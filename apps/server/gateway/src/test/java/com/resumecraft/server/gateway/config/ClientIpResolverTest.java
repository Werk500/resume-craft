package com.resumecraft.server.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientIpResolverTest {

    private static final String TRUSTED_PROXIES =
            "127.0.0.1/32,::1/128,10.0.0.0/8,"
                    + "172.16.0.0/12,192.168.0.0/16";

    private final ClientIpResolver resolver =
            new ClientIpResolver(TRUSTED_PROXIES);

    @Test
    @DisplayName("直接访问网关时忽略客户端伪造的转发头")
    void shouldIgnoreForwardedHeadersFromUntrustedPeer() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/")
                        .remoteAddress(new InetSocketAddress("203.0.113.20", 12345))
                        .header("X-Forwarded-For", "1.1.1.1")
                        .build()
        );

        assertThat(resolver.resolveClientIp(exchange))
                .isEqualTo("203.0.113.20");
    }

    @Test
    @DisplayName("可信代理转发时从右向左取真实客户端 IP")
    void shouldResolveClientIpBehindTrustedProxy() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/")
                        .remoteAddress(new InetSocketAddress("127.0.0.1", 12345))
                        .header("X-Forwarded-For", "198.51.100.7, 127.0.0.1")
                        .build()
        );

        assertThat(resolver.resolveClientIp(exchange))
                .isEqualTo("198.51.100.7");
    }

    @Test
    @DisplayName("多级可信代理会剥离右侧可信跳数")
    void shouldStripMultipleTrustedProxyHops() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/")
                        .remoteAddress(new InetSocketAddress("10.0.0.2", 12345))
                        .header(
                                "X-Forwarded-For",
                                "198.51.100.8, 10.0.0.3, 10.0.0.2"
                        )
                        .build()
        );

        assertThat(resolver.resolveClientIp(exchange))
                .isEqualTo("198.51.100.8");
    }

    @Test
    @DisplayName("可信代理没有 XFF 时使用 X-Real-IP")
    void shouldUseRealIpAsFallback() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/")
                        .remoteAddress(new InetSocketAddress("127.0.0.1", 12345))
                        .header("X-Real-IP", "198.51.100.9")
                        .build()
        );

        assertThat(resolver.resolveClientIp(exchange))
                .isEqualTo("198.51.100.9");
    }

    @Test
    @DisplayName("代理头非法时回退到 TCP 对端地址")
    void shouldFallbackToPeerWhenHeaderIsInvalid() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/")
                        .remoteAddress(new InetSocketAddress("127.0.0.1", 12345))
                        .header("X-Forwarded-For", "not-an-ip")
                        .build()
        );

        assertThat(resolver.resolveClientIp(exchange))
                .isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("IPv4-mapped IPv6 会归一化为 IPv4")
    void shouldNormalizeIpv4MappedIpv6() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/")
                        .remoteAddress(new InetSocketAddress("127.0.0.1", 12345))
                        .header(
                                "X-Forwarded-For",
                                "::ffff:198.51.100.12, 127.0.0.1"
                        )
                        .build()
        );

        assertThat(resolver.resolveClientIp(exchange))
                .isEqualTo("198.51.100.12");
    }

    @Test
    @DisplayName("非法可信代理 CIDR 在启动配置解析时失败")
    void shouldRejectInvalidTrustedProxyCidr() {
        assertThatThrownBy(() -> new ClientIpResolver("127.0.0.1/33"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid trusted proxy CIDR");
    }
}
