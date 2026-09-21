package com.resumecraft.server.job.config;

import feign.FeignException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 验证熔断器忽略的业务异常类型可被 Spring 解析。
 *
 * <h3>背景</h3>
 * 联通熔断时踩过一个坑：{@code ignore-exceptions} 最初配成
 * {@code feign.FeignException$FeignClientException}，但网关内部已经把 FeignException
 * 翻译成了 {@code IllegalArgumentException}，切面根本看不到 Feign 异常，
 * 导致 4xx 业务拒绝被计入失败率，连续请求不存在的资源即可误触发熔断。
 *
 * <p>修正后改为忽略 {@code java.lang.IllegalArgumentException}。本测试锁定这两点：
 * <ol>
 *   <li>真正被忽略的类名能被 Spring 解析（配置生效的前提）；</li>
 *   <li>Feign 的 4xx 异常层级关系符合预期，便于理解"为什么切面看不到它"。</li>
 * </ol>
 *
 * <p>不依赖 Spring 容器与中间件，毫秒级完成。
 */
class IgnoreExceptionClassNameTest {

    /** application.yml 中实际配置的被忽略类型 */
    private static final String IGNORED_CLASS_NAME = "java.lang.IllegalArgumentException";

    /** 曾经配错的类型，保留用于说明为什么那时匹配不上 */
    private static final String FEIGN_CLIENT_EXCEPTION = "feign.FeignException$FeignClientException";

    @Test
    @DisplayName("ignore-exceptions 配置的类名可被 Spring 解析")
    void shouldResolveConfiguredIgnoreClass() {
        // resolveClassName 是 Spring Boot 配置绑定实际走的路径，且不抛受检异常
        Class<?> resolved = ClassUtils.resolveClassName(IGNORED_CLASS_NAME,
                IgnoreExceptionClassNameTest.class.getClassLoader());

        assertThat(resolved).isEqualTo(IllegalArgumentException.class);
        // 必须是 Throwable 的子类，否则 resilience4j 无法用它做异常判定
        assertThat(Throwable.class).isAssignableFrom(resolved);
    }

    @Test
    @DisplayName("Feign 内部类名本身可解析（当初的报错只是 IDE 误报）")
    void shouldResolveFeignInternalClass() {
        assertThatCode(() -> ClassUtils.resolveClassName(FEIGN_CLIENT_EXCEPTION,
                IgnoreExceptionClassNameTest.class.getClassLoader()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("FeignClientException 是 4xx 异常的父类 —— 但切面看不到它")
    void shouldCoverAllClientErrors() {
        // 层级关系成立：BadRequest / NotFound 等都继承 FeignClientException。
        // 但网关在方法内部把 FeignException 翻译成了 IllegalArgumentException，
        // 切面在方法之外，观察到的是翻译后的类型，所以按 Feign 类型配置无效。
        assertThat(FeignException.FeignClientException.class)
                .isAssignableFrom(FeignException.BadRequest.class);
        assertThat(FeignException.FeignClientException.class)
                .isAssignableFrom(FeignException.NotFound.class);
        assertThat(FeignException.FeignClientException.class)
                .isAssignableFrom(FeignException.Unauthorized.class);
    }
}
