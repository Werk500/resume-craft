package com.resumecraft.server.optimize.cache;


import com.resumecraft.server.common.cache.CacheKeys;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;


/**
 * 优化缓存清理器
 * <p>
 * 专门用于在简历内容变更或删除时，渐进式清理相关的 Redis 缓存。
 * 核心设计：坚决不使用 KEYS 命令（防止阻塞 Redis 主线程），而是使用 SCAN 游标遍历 + 分批 DEL。
 */
@Slf4j
@Component
public class OptimizeCacheEvictor {

    // 每次让 Redis 遍历的槽位数（降低单次阻塞时间，提高吞吐）
    private static final int SCAN_COUNT = 500;
    // 每攒够多少个 Key 就发一次 DEL 命令（防止内存中 List 过大导致 Java OOM）
    private static final int DELETE_BATCH_SIZE = 500;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 清理指定简历关联的所有优化缓存
     *
     * @param resumeId 简历ID
     */
    public void evictByResumeId(Long resumeId) {
        if (resumeId == null) {
            return;
        }

        // 1. 清理普通优化缓存
        evictByPattern(CacheKeys.optimizePattern(resumeId));
        // 2. 清理针对特定岗位的定向优化缓存
        evictByPattern(CacheKeys.targetedOptimizePattern(resumeId));

    }

    /**
     * 核心方法：按模式（Pattern）扫描并清理缓存
     *
     * @param pattern 匹配模式（如：optimize:resume:123:*）
     */
    private void evictByPattern(String pattern) {
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern).count(SCAN_COUNT).build();

        List<String> batch = new ArrayList<>(DELETE_BATCH_SIZE);

        // 使用 try-with-resources 确保底层 Redis 连接（Cursor）绝对会被关闭，防止连接泄漏
        try(Cursor<String> cursor = stringRedisTemplate.scan(options)){
            while (cursor.hasNext()) {
                batch.add(cursor.next());

                if (batch.size() >= DELETE_BATCH_SIZE) {
                    deleteBatch(batch);
                }
            }
        }catch (Exception e) {
            log.warn(
                    "SCAN 清理优化缓存失败: pattern={}",
                    pattern,
                    e
            );
        } finally {
            // 删除不足一批的剩余 Key
            deleteBatch(batch);
        }

    }

    /**
     * 批量删除缓存
     * <p>
     * 说明：如果 batch 被传进来时是空的，直接返回；
     * 如果删除失败，只记录日志（静默降级，依赖缓存 TTL 兜底），不打断主流程。
     *
     * @param keys 待删除的 Key 集合
     */
    private void deleteBatch(List<String> keys) {
        if (keys.isEmpty()) {
            return;
        }

        try {
            stringRedisTemplate.delete(keys);
        } catch (Exception e) {
            log.warn(
                    "批量删除优化缓存失败: size={}",
                    keys.size(),
                    e
            );
        } finally {
            keys.clear();
        }
    }
}
