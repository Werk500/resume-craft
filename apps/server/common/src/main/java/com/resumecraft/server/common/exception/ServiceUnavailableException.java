package com.resumecraft.server.common.exception;

/**
 * 下游服务不可用（熔断降级触发）。
 *
 * <h3>为什么需要单独的异常类型</h3>
 * 全局异常处理器对 {@code Exception} 有兜底分支，返回统一的"服务器内部错误"，
 * 目的是不把内部异常细节泄露给前端。但熔断降级是<b>预期内的可恢复状态</b>，
 * 用户需要看到明确提示（如"简历服务暂时不可用，请稍后重试"）才能知道该重试。
 *
 * <p>若直接抛 {@code RuntimeException}，消息会被兜底分支吞掉，
 * 前端只能收到泛化的"服务器内部错误"，无法区分"参数错了"和"服务暂时抖动"。
 * 因此单独定义本异常，由全局处理器优先匹配并透传 message。
 *
 * <h3>HTTP 语义</h3>
 * 映射为 <b>503 Service Unavailable</b>：服务暂时不可用，稍后重试可能成功。
 * 与 500（服务内部错误）区分开，便于前端和监控识别。
 */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
