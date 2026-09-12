package com.resumecraft.server.gateway.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import org.springframework.http.HttpHeaders;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

//请求进入网关
//   ↓
//RequestRateLimiter 调用 userOrIpKeyResolver
//   ↓
//读 Authorization 头
//   ↓
//有 Bearer token？
//        ├─ 是 → 验签 + 解析
//        │        ├─ 成功 → key = "u:12345"
//        │        └─ 失败 → 走 IP 兜底
//        └─ 否 → 走 IP 兜底
//                ↓
//          读 RemoteAddress
//                ↓
//          key = "ip:192.168.1.100"
//        ↓
//用 key 去 Redis 找令牌桶
//   ↓
//有令牌 → 放行
//没令牌 → 429
@Configuration
public class RateLimitConfig {


    private final SecretKey secretKey;

    public  RateLimitConfig(@Value("${app.jwt.secret}")  String secret) {
        // HS256 要求密钥 >= 32 字节，与 common 的 JwtUtil 保持一致
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

//    //按客户端IP限流
//    @Bean
//    public KeyResolver ipKeyResolver() {
//        //Mono.just(x) 表示"一个立即返回 x 的异步流"。
//        return exchange -> Mono.just(
//                Objects.requireNonNull(exchange.getRequest()//拿请求
//                        .getRemoteAddress()//拿远端地址
//                        .getAddress()//拿IP地址
//                        .getHostAddress()
//                ));
    /**
     * 限流维度：
     *  - 已登录（Authorization: Bearer <JWT> 且验签通过）→ 按用户 userId
     *  - 未登录 / token 无效 → 退化为按客户端 IP
     */

    @Bean
    public KeyResolver userIpKeyResolver() {

        //Mono.fromSupplier(supplier) 表示"惰性执行",不立刻执行 supplier,等到有人订阅时才执行
        return exchange -> Mono.fromSupplier(() ->{
            Long userId = parseUserId(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            if (userId != null) {
                return "u:" +userId;
            }
            InetSocketAddress addr = exchange.getRequest().getRemoteAddress();
            String ip = (addr != null && addr.getAddress() != null)
                    ? addr.getAddress().getHostAddress() : "unknown";
            return "ip:" +ip;
        });
    }

    private Long parseUserId(String authHeader) {
        if(authHeader == null || !authHeader.startsWith("Bearer ")){
            return null;}
        try {
            var claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(authHeader.substring(7))
                    .getPayload();
            return Long.valueOf(claims.getSubject());   // auth 服务把 userId 放在 subject
        } catch (Exception e) {
            return null;   // 过期/伪造 token → 走 IP 兜底（鉴权仍由业务服务负责）
        }
    }
}
