package com.resumecraft.server.common.security;


import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;


@Slf4j
@Service
public class TokenRevocationService {

    private static final String KEY_PREFIX =
            "resume-craft:auth:revoked:";

    private final StringRedisTemplate redisTemplate;

    public TokenRevocationService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void revoke(JwtUtil.ParsedToken token) {

        if(token == null|| !StringUtils.hasText(token.tokenId())) {
            return;
        }

        Duration ttl = Duration.between(Instant.now(), token.expiresAt());

        try {
            if(!ttl.isZero() && !ttl.isNegative()) {
                redisTemplate.opsForValue()
                        .set(KEY_PREFIX+token.tokenId(),
                                "1",ttl);
            }
        } catch (RuntimeException e) {
            log.error("写入 JWT 吊销名单失败: jti={}", token.tokenId(), e);
            throw new IllegalStateException("退出登录失败，请稍后重试", e);
        }
    }

    public boolean isRevoked(String tokenId){

        if (!StringUtils.hasText(tokenId)) {
            return true;
        }

        try {
            return Boolean.TRUE.equals(
                    redisTemplate.hasKey(KEY_PREFIX + tokenId)
            );
        } catch (RuntimeException e) {
            log.error("查询 JWT 吊销状态失败: jti={}", tokenId, e);
            return true;
        }
    }
}
