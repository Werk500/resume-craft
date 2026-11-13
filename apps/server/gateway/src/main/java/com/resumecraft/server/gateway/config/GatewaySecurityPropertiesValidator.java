package com.resumecraft.server.gateway.config;


import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
public class GatewaySecurityPropertiesValidator {

    @Value("${app.jwt.secret:}")
    private String jwtSecret;

    @Value("${spring.profiles.active:dev}")
    private String activeProfiles;

    @PostConstruct
    public void validate(){
        if (!StringUtils.hasText(jwtSecret)) {
            throw new IllegalStateException("启动失败: JWT Secret 不能为空 (app.jwt.secret)");
        }

        int secretBytesLength = jwtSecret.getBytes(StandardCharsets.UTF_8).length;
        if (secretBytesLength < 32) {
            throw new IllegalStateException("【安全校验失败】网关启动终止: app.jwt.secret 长度不足，至少需要 32 字节（当前为 "
                    + secretBytesLength + " 字节）");
        }

        String profiles = activeProfiles == null
                ? ""
                : activeProfiles.toLowerCase(Locale.ROOT);

        if (profiles.contains("prod")) {

            String defaultDevSecret = "dev-only-secret-key-change-before-prod-32bytes";

            if (defaultDevSecret.equals(jwtSecret)) {
                throw new IllegalStateException("【安全校验失败】网关启动终止: 生产环境禁止使用默认的 JWT Secret，请立即更换！");
            }
        }

    }
}
