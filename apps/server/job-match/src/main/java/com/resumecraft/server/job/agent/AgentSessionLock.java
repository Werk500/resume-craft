package com.resumecraft.server.job.agent;

import com.resumecraft.server.common.cache.CacheKeys;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class AgentSessionLock {

    /** 锁的兜底过期时间：客户端断流时可能收不到回调，不能让锁永久占着 */
    private static final Duration TTL = Duration.ofSeconds(60);
    private static final String SKIP = "SKIP";


    private static final RedisScript<Long> UNLOCK = RedisScript.of(
            new ClassPathResource("scripts/agent-session-lock-unlock.lua"), Long.class);
    private static final RedisScript<Long> RENEW = RedisScript.of(
            new ClassPathResource("scripts/agent-session-lock-renew.lua"), Long.class);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 启动时把脚本读一遍：路径写错、文件没打进 jar 这类问题要在启动时就炸，
     * 而不是等到线上第一次释放锁才发现。
     */
    @PostConstruct
    void warmUp() {
        log.info("会话锁脚本已加载: unlock={} renew={}",
                UNLOCK.getSha1(), RENEW.getSha1());
    }

    /** 拿到锁返回 token；会话正忙返回 null；Redis 不可用返回 SKIP（fail-open） */
    public String tryLock(String memoryId) {
        try {
            String token = UUID.randomUUID().toString();
            Boolean ok = stringRedisTemplate.opsForValue()
                    .setIfAbsent(CacheKeys.agentSessionLock(memoryId), token, TTL);
            return Boolean.TRUE.equals(ok) ? token : null;
        } catch (Exception e) {
            log.warn("会话锁不可用，放行本轮: memoryId={}, err={}", memoryId, e.getMessage());
            return SKIP;
        }
    }

    public boolean renew(String memoryId, String token) {
        if (SKIP.equals(token)) return true;
        try {
            Long r = stringRedisTemplate.execute(RENEW,
                    List.of(CacheKeys.agentSessionLock(memoryId)),
                    token, String.valueOf(TTL.toMillis()));
            return r != null && r > 0;      // ← execute 可能返回 null，直接 r > 0 会 NPE
        } catch (Exception e) {             // ← 它将来会被后台任务周期调用，异常不能抛出去
            log.warn("会话锁续期失败: memoryId={}, err={}", memoryId, e.getMessage());
            return false;
        }
    }


    public void unlock(String memoryId,String token) {
        if(SKIP.equals(token)) {
            return;
        }
        try {
            stringRedisTemplate.execute(UNLOCK, List.of(CacheKeys.agentSessionLock(memoryId)), token);
        } catch (Exception e) {
            log.warn("会话锁释放失败，等待 TTL 自动过期: memoryId={}", memoryId);
        }
    }


}
