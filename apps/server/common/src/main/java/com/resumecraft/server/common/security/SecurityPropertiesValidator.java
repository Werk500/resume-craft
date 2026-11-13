package com.resumecraft.server.common.security;


import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
public class SecurityPropertiesValidator {

    @Value("${app.jwt.secret:}")
    private String jwtSecret;

    @Value("${app.internal-token:}")
    private String internalToken;

    @Value("${spring.profiles.active:dev}")
    private String activeProfiles;

    @PostConstruct
    public void validate(){
        if (!StringUtils.hasText(jwtSecret)) {
            throw new IllegalStateException("启动失败: JWT Secret 不能为空 (app.jwt.secret)");
        }
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("启动失败: JWT Secret 长度不足，至少需要 32 字节 (当前: "
                    + jwtSecret.getBytes(StandardCharsets.UTF_8).length + " 字节)");
        }
        if (!StringUtils.hasText(internalToken)) {
            throw new IllegalStateException("启动失败: Internal Token 不能为空 (app.internal-token)");
        }
        if (internalToken.length() < 16) {
            throw new IllegalStateException("启动失败: Internal Token 长度不足，至少需要 16 个字符");
        }

        // 判断是否是生产环境
        boolean isProd = activeProfiles != null
                && activeProfiles.toLowerCase(Locale.ROOT).contains("prod");

        if (isProd) {
            // 2.1 生产环境禁止使用默认的 JWT Secret
            if ("dev-only-secret-key-change-before-prod-32bytes".equals(jwtSecret)) {
                throw new IllegalStateException("生产环境禁止使用默认 JWT_SECRET");
            }

            // 2.2 生产环境禁止使用默认的 Internal Token
            if ("dev-internal-token".equals(internalToken)) {
                throw new IllegalStateException("生产环境禁止使用默认 INTERNAL_TOKEN");
            }
        }
    }
}
