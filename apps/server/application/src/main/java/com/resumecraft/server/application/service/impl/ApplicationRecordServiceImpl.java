package com.resumecraft.server.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.resumecraft.server.application.domain.ApplicationRecord;
import com.resumecraft.server.application.domain.ApplicationRecordMapper;
import com.resumecraft.server.application.gateway.ResumeVersionGateway;
import com.resumecraft.server.application.service.ApplicationRecordService;
import com.resumecraft.server.common.dto.ResumeVersionDTO;
import com.resumecraft.server.common.security.AuthContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class ApplicationRecordServiceImpl implements ApplicationRecordService {

    private static final Set<String> ALLOWED_STATUSES = Set.of(
            "pending", "interviewing", "rejected", "no_response", "accepted"
    );

    @Resource
    private ApplicationRecordMapper applicationRecordMapper;

    @Resource
    private ResumeVersionGateway resumeVersionGateway;

    /**
     * 创建投递记录
     * @param record
     * @return
     */
    @Override
    public ApplicationRecord create(ApplicationRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("投递记录不能为空");
        }

        Long userId = AuthContext.getUserId();
        validateResumeVersion(record.getResumeVersionId(), userId);

        // 防止客户端伪造主键或覆盖归属。
        record.setId(null);
        record.setUserId(userId);
        record.setCreateTime(null);
        record.setStatus(normalizeStatus(record.getStatus()));
        applicationRecordMapper.insert(record);
        return record;
    }

    /**
     *  查询所有投递记录（按ID倒序）
     * @return
     */
    @Override
    public List<ApplicationRecord> findall() {

        Long userId = AuthContext.getUserId();

        LambdaQueryWrapper<ApplicationRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApplicationRecord::getUserId, userId)  // ← 添加用户过滤
                .orderByDesc(ApplicationRecord::getId);
        return applicationRecordMapper.selectList(wrapper);
    }

    /**
     * 根据ID查询投递记录
     * @param id
     * @return
     */
    @Override
    public ApplicationRecord findById(Long id) {

        Long userId = AuthContext.getUserId();
        LambdaQueryWrapper<ApplicationRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApplicationRecord::getUserId, userId).eq(ApplicationRecord::getId, id);

        ApplicationRecord record = applicationRecordMapper.selectOne(wrapper);
        if (record == null) {
            throw new IllegalArgumentException("投递记录不存在: id=" + id);
        }
        return record;

    }

    /**
     *  更新投递记录
     * @param id
     * @param record
     * @return
     */
    @Override
    @Transactional
    public ApplicationRecord updateStatus(Long id, String status) {
        findById(id);
        ApplicationRecord patch = new ApplicationRecord();
        patch.setId(id);
        patch.setStatus(normalizeStatus(status));
        applicationRecordMapper.updateById(patch);
        return findById(id);
    }

    /**
     * 删除记录
     * @param id
     * @return
     */
    @Override
    public void delete(Long id) {
        // 1. 先确认记录存在
        findById(id);
        // 2. 删除
        applicationRecordMapper.deleteById(id);

        log.info("删除投递记录成功: id={}", id);
    }


    /**
     * 获取投递统计
     * @return
     */
    @Override
    public Map<String, Long> stats() {

        //1.获取当前用户ID
        Long userId = AuthContext.getUserId();
        log.info("获取投递统计: userId={}", userId);

        //查询当前用户的所有投递记录
        LambdaQueryWrapper<ApplicationRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApplicationRecord::getUserId, userId);
        List<ApplicationRecord> records = applicationRecordMapper.selectList(wrapper);

        // 保证五个状态键永远存在（没有就返回 0）
        Map<String, Long> result = new LinkedHashMap<>();
        for (String status : List.of("pending", "interviewing", "rejected", "no_response", "accepted")) {
            result.put(status, 0L);
        }

        // 按真实状态字符串统计
        for (ApplicationRecord record : records) {
            if (record.getStatus() != null) {
                result.merge(record.getStatus(), 1L, Long::sum);
            }
        }

        result.put("total", (long) records.size());
        return result;

    }

    /**
     * Human-in-the-loop 的强制约束放在后端：版本必须属于当前用户，
     * 且不能包含未确认的 AI 改动。Feign 调用发生在数据库事务之外。
     */
    private void validateResumeVersion(Long versionId, Long userId) {
        if (versionId == null || versionId <= 0) {
            throw new IllegalArgumentException("请选择已确认的简历版本");
        }

        ResumeVersionDTO version = resumeVersionGateway.getVersion(versionId);
        if (version == null || !userId.equals(version.getUserId())) {
            // 不区分“不存在”和“无权限”，避免泄露其他用户资源是否存在。
            throw new IllegalArgumentException("简历版本不存在或无权使用");
        }

        boolean confirmed = "CONFIRMED".equalsIgnoreCase(version.getStatus());
        boolean legacyWithoutPending = !StringUtils.hasText(version.getPendingJson());
        if (!confirmed && !legacyWithoutPending) {
            throw new IllegalArgumentException("该版本还有 AI 改动未确认，请确认后再投递");
        }
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            throw new IllegalArgumentException("投递状态不能为空");
        }

        String normalized = status.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("投递状态不合法: " + status);
        }
        return normalized;
    }
}
