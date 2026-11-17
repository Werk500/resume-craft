package com.resumecraft.server.gateway.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitConfigTest {

    private static final String SECRET =
            "0123456789abcdef0123456789abcdef";

    private final ClientIpResolver clientIpResolver = new ClientIpResolver(
            "127.0.0.1/32,::1/128,10.0.0.0/8"
    );

    private final RateLimitConfig config =
            new RateLimitConfig(SECRET, clientIpResolver);

    @Test
    @DisplayName("无 JWT 时使用客户端 IP 作为限流 Key")
    void shouldUseClientIpWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/")
                        .remoteAddress(new InetSocketAddress("127.0.0.1", 12345))
                        .header("X-Forwarded-For", "198.51.100.20")
                        .build()
        );

        KeyResolver keyResolver = config.userIpKeyResolver();

        assertThat(keyResolver.resolve(exchange).block())
                .isEqualTo("ip:198.51.100.20");
    }

    @Test
    @DisplayName("有效 JWT 时优先使用 userId 作为限流 Key")
    void shouldUseUserIdWithValidToken() {
        String token = Jwts.builder()
                .subject("42")
                .signWith(Keys.hmacShaKeyFor(
                        SECRET.getBytes(StandardCharsets.UTF_8)
                ))
                .compact();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/")
                        .remoteAddress(new InetSocketAddress("127.0.0.1", 12345))
                        .header("Authorization", "Bearer " + token)
                        .build()
        );

        KeyResolver keyResolver = config.userIpKeyResolver();

        assertThat(keyResolver.resolve(exchange).block())
                .isEqualTo("u:42");
    }
}
