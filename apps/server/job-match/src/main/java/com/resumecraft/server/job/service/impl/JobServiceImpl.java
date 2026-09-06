package com.resumecraft.server.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecraft.server.ai.AiService;
import com.resumecraft.server.ai.impl.PromptTemplates;
import com.resumecraft.server.job.domain.JdAnalysis;
import com.resumecraft.server.job.dto.JobPageResult;
import com.resumecraft.server.job.service.JobService;
import com.resumecraft.server.job.domain.Job;
import com.resumecraft.server.job.domain.JobMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class JobServiceImpl implements JobService {

    @Resource
    private JobMapper jobMapper;

    @Resource
    private AiService aiService;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String CACHE_KEY_PREFIX = "analyze:";
    private static final String NULL_VALUE = "NULL";
    private static final long NULL_EXPIRE_MINUTES = 5;

    /**
     * 最大每页大小
     */
    private static final int MAX_SIZE = 100;

    /**
     * 默认每页大小
     */
    private static final int DEFAULT_SIZE = 20;

    /**
     * 创建职位
     */
    @Override
    public Job createJob(Job job) {
        jobMapper.insert(job);
        return job;
    }

    /**
     * 查询所有职位
     * @return
     */
    @Override
    public List<Job> findAll() {
        LambdaQueryWrapper<Job> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(Job::getId);
        return jobMapper.selectList(queryWrapper);
    }

    @Override
    public Job findById(Long id) {
        Job job = jobMapper.selectById(id);
        if (job == null) {
            throw new IllegalArgumentException("岗位不存在：id=" + id);  // GlobalExceptionHandler 自动转 400
        }
        return job;
    }


    /**
     * AI分析岗位
     * @param jobId
     * @return
     */
    @Override
    public JdAnalysis analyzeJob(Long jobId) {

        log.info("开始 JD 分析: jobId={}", jobId);

        //1.构建换成key
        String cacheKey = CACHE_KEY_PREFIX + jobId;

        // 2. 检查缓存（Redis 异常时降级跳过缓存，不阻塞主流程）
        String cachedValue = null;
        try {
            cachedValue = stringRedisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            log.warn("缓存读取失败，跳过缓存, cacheKey={}", cacheKey, e);
        }

        // 3. 检查空值缓存
        if (NULL_VALUE.equals(cachedValue)) {
            log.warn("命中空值缓存, jobId={}", jobId);
            throw new RuntimeException("岗位不存在");
        }

        // 4. 检查正常缓存
        if (cachedValue != null) {
            try {
                JdAnalysis cachedResult = objectMapper.readValue(cachedValue, JdAnalysis.class);
                log.info("命中缓存: jobId={}", jobId);
                return cachedResult;
            } catch (Exception e) {
                log.warn("缓存反序列化失败，将重新生成: {}", e.getMessage());
                try {
                    stringRedisTemplate.delete(cacheKey);
                } catch (Exception ex) {
                    log.warn("删除损坏缓存失败: {}", ex.getMessage());
                }
            }
        }

        // 5. 查询岗位
        Job job;
        try {
            job = jobMapper.selectById(jobId);
            if (job == null) {
                // 岗位不存在，缓存空值（防穿透）
                try {
                    stringRedisTemplate.opsForValue().set(
                            cacheKey,
                            NULL_VALUE,
                            NULL_EXPIRE_MINUTES,
                            TimeUnit.MINUTES
                    );
                } catch (Exception e) {
                    log.warn("空值缓存写入失败: {}", e.getMessage());
                }
                throw new IllegalArgumentException("岗位不存在: jobId=" + jobId);
            }
        } catch (IllegalArgumentException e) {
            throw e; // 业务异常直接抛出
        } catch (Exception e) {
            // 系统异常（数据库连接失败等），不缓存空值
            log.error("查询岗位失败, jobId={}", jobId, e);
            throw new RuntimeException("系统异常，请稍后重试");
        }

        log.info("开始 AI 分析岗位: jobId={}, title={}", jobId, job.getTitle());

        // 6. 拼 prompt 并调用 AI
        String userPrompt = PromptTemplates.jdAnalyzeUser(
                job.getTitle(),
                job.getDescription(),
                job.getRequirements()
        );

        String aiResponse = aiService.chat(PromptTemplates.JD_ANALYZE_SYSTEM, userPrompt);
        log.info("AI 响应: {}", aiResponse);

        // 7. 解析 AI 返回的 JSON
        JdAnalysis analysis = parseJdAnalysis(aiResponse);
        log.info("JD 分析解析成功: jobId={}, 技能数={}, 雷达维度={}",
                jobId,
                analysis.getSkills() != null ? analysis.getSkills().size() : 0,
                analysis.getRadar() != null ? analysis.getRadar().size() : 0);

        // 8. 写入缓存（有效期 1小时 + 随机偏移防雪崩）
        try {
            String resultJson = objectMapper.writeValueAsString(analysis);

            long baseSeconds = TimeUnit.HOURS.toSeconds(1);
            long randomOffset = ThreadLocalRandom.current().nextLong(0, 600); // 0-600秒 = 0-10分钟
            long expireSeconds = baseSeconds + randomOffset;

            stringRedisTemplate.opsForValue().set(cacheKey, resultJson, expireSeconds, TimeUnit.SECONDS);
            log.info("JD 分析结果已缓存: jobId={}, 过期时间={}秒", jobId, expireSeconds);
        } catch (Exception e) {
            log.warn("缓存写入失败: {}", e.getMessage());
            // 缓存失败不影响主流程
        }

        return analysis;

    }

    /**
     * 分页搜索职位
     * @param company
     * @param keyword
     * @param page
     * @param size
     * @return
     */
    @Override
    public JobPageResult search(String company, String keyword, int page, int size) {

        // 1. 参数处理
        int currentPage = Math.max(1, page);
        int pageSize = Math.min(size>0?size:DEFAULT_SIZE,MAX_SIZE);

        log.info("职位搜索: company={}, keyword={}, page={}, size={}", company, keyword, currentPage, pageSize);

        //2.构建查询条件
        LambdaQueryWrapper<Job> wrapper = new LambdaQueryWrapper<>();

        // company 非空 → LIKE %company%
        if (StringUtils.isNotBlank(company)) {
            wrapper.like(Job::getCompany, company.trim());
        }
        // keyword 非空 → 匹配 title OR description OR requirements
        if (StringUtils.isNotBlank(keyword)) {
            String trim = keyword.trim();
            wrapper.and(w->w
                    .like(Job::getTitle, trim)
                    .or()
                    .like(Job::getDescription, trim)
                    .or()
            .like(Job::getRequirements, trim));
        }

        //按create_time倒序
        wrapper.orderByDesc(Job::getCreateTime);

        //3.分页查询（MyBatis-Plus Page 的 current 从 1 开始，直接传当前页）
        Page<Job> pageResult = jobMapper.selectPage(new Page<>(currentPage, pageSize), wrapper);


        //4.转换为DTO
        return JobPageResult.builder()
                .list(pageResult.getRecords())
                .total(pageResult.getTotal())
                .page(currentPage)
                .size(pageSize)
                .build();

    }

    /**
     * 解析 AI 返回的 JSON 为 JdAnalysis 对象
     */
    private JdAnalysis parseJdAnalysis(String aiResponse) {
        try {
            // 清理 Markdown 代码块标记
            String jsonStr = aiResponse
                    .replaceAll("```json\\s*", "")
                    .replaceAll("```\\s*", "")
                    .trim();

            JsonNode json = objectMapper.readTree(jsonStr);

            // 解析雷达图维度
            List<JdAnalysis.RadarDimension> radar = new ArrayList<>();
            JsonNode radarNode = json.get("radar");
            if (radarNode != null && radarNode.isArray()) {
                for (JsonNode item : radarNode) {
                    String name = getString(item, "name");
                    Integer score = getInteger(item, "score");
                    if (name != null && score != null) {
                        radar.add(new JdAnalysis.RadarDimension(name, score));
                    }
                }
            }

            // 构建 JdAnalysis 对象
            return JdAnalysis.builder()
                    .hardRequirements(getStringList(json, "hardRequirements"))
                    .bonusPoints(getStringList(json, "bonusPoints"))
                    .hiddenRequirements(getStringList(json, "hiddenRequirements"))
                    .skills(getStringList(json, "skills"))
                    .radar(radar)
                    .summary(getString(json, "summary"))
                    .build();

        } catch (Exception e) {
            log.error("解析 AI 响应 JSON 失败，原始响应: {}", aiResponse, e);
            throw new RuntimeException("AI 响应格式异常，无法解析 JD 分析结果", e);
        }
    }

    /**
     * 从 JSON 节点获取字符串列表
     */
    private List<String> getStringList(JsonNode json, String key) {
        List<String> list = new ArrayList<>();
        JsonNode node = json.get(key);
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                if (item != null && !item.isNull()) {
                    list.add(item.asText());
                }
            }
        }
        return list;
    }

    /**
     * 从 JSON 节点获取字符串
     */
    private String getString(JsonNode json, String key) {
        JsonNode node = json.get(key);
        return (node != null && !node.isNull()) ? node.asText() : null;
    }

    /**
     * 从 JSON 节点获取整数
     */
    private Integer getInteger(JsonNode json, String key) {
        JsonNode node = json.get(key);
        return (node != null && !node.isNull()) ? node.asInt() : null;
    }

}
