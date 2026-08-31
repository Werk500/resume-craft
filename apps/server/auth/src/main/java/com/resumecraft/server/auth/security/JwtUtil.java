package com.resumecraft.server.auth.security;


import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
@Slf4j
public class JwtUtil {

    /** 签名密钥，从配置读取（.env → JWT_SECRET），禁止硬编码 */
    private final SecretKey secretKey;

    @Value("${app.jwt.expire-seconds:86400}")
    private Long expireSeconds;

    public JwtUtil(@Value("${app.jwt.secret}") String secret) {
        // HS256 要求密钥 ≥ 32 字节
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 JWT Token
     * @param userId 用户ID（作为 subject）
     * @param username 用户名（作为 claim）
     * @return JWT 字符串
     */
    public String generateToken(String userId, String username) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expireSeconds * 1000);

        return Jwts.builder()
                .subject(userId)
                .claim("username", username)
                .issuedAt(new Date())
                .expiration(expiration)
                .signWith(secretKey)  // 0.12.x 版本自动选择 HS256
                .compact();
    }

    /**
     * 解析 Token 获取用户ID
     * @param token JWT 字符串
     * @return 用户ID，解析失败返回 null
     */
    public  Long parseUserId(String token) {
        try {
            var claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token).getPayload();

            return Long.valueOf(claims.getSubject());
        } catch (Exception e) {
            log.warn("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }


    public  boolean validateToken(String token) {
        return token != null && parseUserId(token) != null;
    }
}
