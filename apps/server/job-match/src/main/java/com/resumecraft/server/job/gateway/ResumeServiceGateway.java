package com.resumecraft.server.job.gateway;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecraft.server.common.dto.ResumeBriefDTO;
import com.resumecraft.server.common.dto.ResumeVersionDTO;
import com.resumecraft.server.common.feign.ResumeClient;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * resume-service 调用的统一网关。
 *
 * <h3>为什么单独抽一个 Bean 而不是直接在 Feign 客户端加 @CircuitBreaker</h3>
 * 同一个 {@code getResume} 调用在不同业务场景下需要<b>不同的降级策略</b>：
 * <ul>
 *   <li>匹配流程：不能拿到简历就无法算分，必须<b>快速失败并明确报错</b></li>
 *   <li>Kafka 消费校验：简历已删是正常情况，应<b>跳过而不重试</b></li>
 * </ul>
 * Feign 客户端只能有一个 fallback，无法表达这种差异；
 * 而 {@code @CircuitBreaker} 也不能加在本类私有方法上（Spring AOP 不代理私有方法），
 * 因此这里做成独立 Bean，把"调用 + 熔断 + 异常语义化"收在一处。
 */
@Slf4j
@Component
public class ResumeServiceGateway {
    @Resource
    private ResumeClient resumeClient;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 取简历正文（匹配场景）。
     *
     * <p>降级：熔断打开时返回 null，由调用方判定为"不可用"并抛出用户可读错误。
     */
    @CircuitBreaker(name = "resumeService",fallbackMethod = "getResumeFallback")
    public ResumeBriefDTO getResume(Long resumeId){
        try {
            return resumeClient.getResume(resumeId);
        } catch (FeignException e) {
            throw translate(e);
        }
    }
    /**
     * 判断简历是否可用（Kafka 消费校验场景）。
     *
     * <p>与 getResume 的区别：这里把异常收敛成布尔值，
     * 因为消费端只需要知道"能不能继续生成向量"，不需要区分具体原因。
     */
    @CircuitBreaker(name = "resumeService", fallbackMethod = "isAvailableFallback")
    public boolean isAvailable(Long resumeId){
        try {
            resumeClient.getResume(resumeId);
            return true;
        } catch (FeignException e) {
            // 4xx = 简历不存在或 REVIEW 待核对 → 不可用（永久性，消费端应跳过）
            return false;
        }
    }

    /**
     * 取优化版本内容（匹配场景）。
     * 降级：熔断打开时返回 null，调用方判定为"不可用"。
     */
    @CircuitBreaker(name = "resumeService", fallbackMethod = "getVersionFallback")
    public ResumeVersionDTO getVersion(Long versionId) {
        try {
            return resumeClient.getVersion(versionId);
        } catch (FeignException e) {
            throw translate(e);
        }
    }


    // ------------------------------------------------------------ fallback

    /**
     * 判断是否为业务拒绝（4xx 翻译后的 IllegalArgumentException）。
     *
     * <p><b>为什么 fallback 里要重新抛出去</b>：
     * Resilience4j 的 {@code ignoreExceptions} 只影响<b>失败率统计</b>
     * （4xx 不会被计入，因此不会误触发熔断），<b>但依然会调用 fallback</b>。
     * 如果 fallback 一律返回 null，调用方会把"简历不存在（400）"误判成
     * "简历服务不可用（503）"——用户看到错误的提示，HTTP 状态码也从 4xx 变成 5xx。
     *
     * <p>因此这里把业务拒绝原样抛出，只有真正的服务故障才降级。
     */
    private boolean isBusinessRejection(Throwable t) {
        return t instanceof IllegalArgumentException;
    }

    public ResumeBriefDTO getResumeFallback(Long resumeId, Throwable t) {
        if (isBusinessRejection(t)) {
            // 业务拒绝不是服务故障，原样抛出交给 GlobalExceptionHandler 映射为 400
            throw (IllegalArgumentException) t;
        }
        log.warn("获取简历失败（熔断降级）: resumeId={}, err={}", resumeId, t.getMessage());
        return null;
    }

    public ResumeVersionDTO getVersionFallback(Long versionId, Throwable t) {
        if (isBusinessRejection(t)) {
            throw (IllegalArgumentException) t;
        }
        log.warn("获取简历版本失败（熔断降级）: versionId={}, err={}", versionId, t.getMessage());
        return null;
    }

    public boolean isAvailableFallback(Long resumeId, Throwable t) {
        // 熔断打开时无法确认简历状态。返回 false 会让消费端"跳过并 ACK"，
        // 消息不会进死信；而向量生成本身是可选优化，跳过的代价仅是下次匹配时同步兜底。
        // 这是有意的取舍：宁可少生成一次向量，也不要让消息在熔断期间被反复重试打爆死信队列。
        log.warn("校验简历可用性失败（熔断降级），按不可用处理: resumeId={}, err={}",
                resumeId, t.getMessage());
        return false;
    }


    /**
     * 把 Feign 异常翻译成业务语义异常：
     * 4xx（简历不存在 / REVIEW）→ IllegalArgumentException（全局处理器映射为 400）
     * 5xx → RuntimeException（映射为 500）
     *
     * <p>注意：这里的 4xx 不应当被计入熔断失败率——它是业务拒绝，不是服务故障。
     * 通过 application.yml 的 {@code ignore-exceptions} 配置排除。
     */
    private RuntimeException translate(FeignException e) {
        if (e.status() >= 400 && e.status() < 500) {
            return new IllegalArgumentException(extractMessage(e));
        }
        return new RuntimeException("简历服务暂时不可用，请稍后重试", e);
    }

    /**
     * e.contentUTF8()：把异常里带的原始响应字节，按 UTF-8 解码成字符串。
     * objectMapper.readTree(...)：Jackson 的方法，把 JSON 字符串解析成一棵树（JsonNode）。
     * path("message") 在字段不存在时返回一个"缺失节点"（MissingNode），不会 NPE，链式调用很安全。
     * .asText(null)：把这个节点转成字符串。参数 null 是"默认值"
     * @param e
     * @return
     */
    private String extractMessage(FeignException e) {
        try {
            JsonNode node = objectMapper.readTree(e.contentUTF8());
            String msg = node.path("message").asText(null);
            if (msg != null && !msg.isBlank()) return msg;
        } catch (Exception ignored) {
        }
        return "简历当前状态不允许匹配，请先核对解析内容";
    }
}
