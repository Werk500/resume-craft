package com.resumecraft.server.auth.security;


import com.resumecraft.server.common.exception.ServiceUnavailableException;
import com.resumecraft.server.common.exception.TooManyRequestsException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;

import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Component
@Slf4j
public class LoginAttemptGuard {

    private static final int PAIR_LIMIT = 5;
    private static final int USER_LIMIT = 20;
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final String KEY_PREFIX = "resume-craft:auth:login:";
    private static final String PAIR_KEY_PREFIX = KEY_PREFIX + "pair:";
    private static final String USER_KEY_PREFIX = KEY_PREFIX + "user:";

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private static final RedisScript<Long> LOGIN_ATTEMPT_SCRIPT =
            RedisScript.of(
                    new ClassPathResource("scripts/login_attempt.lua"),
                    Long.class
            );

    public void checkAndRecord(String username, String clientIp){
        String pairKey = pairKey(username, clientIp);
        String userKey = userKey(username);
        long pairCount = increment(pairKey);
        long userCount = increment(userKey);

        if (pairCount > PAIR_LIMIT || userCount > USER_LIMIT){
            throw new TooManyRequestsException("登录尝试过于频繁，请稍后再试");
        }

    }

    public void reset(String username, String clientIp){
        try {
            stringRedisTemplate.delete(List.of(
                    pairKey(username, clientIp),
                    userKey(username)
            ));
        } catch (RuntimeException e) {
            // 计数最多保留 10 分钟，不应让已经成功的登录失败
            log.warn(
                    "登录成功后清理失败计数失败: username={}",
                    username,
                    e
            );
        }
    }

    private String pairKey(String username, String clientIp) {
        return PAIR_KEY_PREFIX + clientIp + ":" + normalizeUsername(username);
    }

    private String userKey(String username) {
        return USER_KEY_PREFIX + normalizeUsername(username);
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private long increment(String key){

        try {
            Long count = stringRedisTemplate.execute(
                    LOGIN_ATTEMPT_SCRIPT,
                    Collections.singletonList(key),
                    String.valueOf(WINDOW.toSeconds())
            );

            if (count == null) {
                throw new IllegalStateException("Redis 登录限流计数失败");
            }

            return count;

        } catch (TooManyRequestsException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ServiceUnavailableException(
                    "登录服务暂时不可用，请稍后重试"
            );
        }
    }

}
