package com.resumecraft.server.optimize.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.resumecraft.server.common.security.AuthContext;
import com.resumecraft.server.mq.AfterCommitExecutor;
import com.resumecraft.server.optimize.cache.OptimizeCacheEvictor;
import com.resumecraft.server.optimize.service.ResumeVersionService;
import com.resumecraft.server.resume.domain.ResumeMapper;
import com.resumecraft.server.resume.domain.ResumeVersion;
import com.resumecraft.server.resume.domain.ResumeVersionMapper;
import com.resumecraft.server.resume.gateway.JobMatchServiceGateway;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class ResumeVersionServiceImpl implements ResumeVersionService {
    @Resource
    private ResumeVersionMapper resumeVersionMapper;
    @Resource
    private OptimizeCacheEvictor optimizeCacheEvictor;

    @Resource
    private JobMatchServiceGateway jobMatchServiceGateway;

    @Resource
    private AfterCommitExecutor afterCommitExecutor;

    @Override
    public List<ResumeVersion> list(Long resumeId) {
        Long currentUserId = AuthContext.getUserId();

        LambdaQueryWrapper<ResumeVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ResumeVersion::getResumeId, resumeId)
                .eq(ResumeVersion::getUserId, currentUserId)
                .orderByDesc(ResumeVersion::getId);  // ID倒序，最新的在前

        return resumeVersionMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Void delete(Long id) {

        ResumeVersion version = findOwnedVersion(id);

        Long resumeId = version.getResumeId();
        Long versionId = version.getId();


        int rows = resumeVersionMapper.deleteById(id);
        if (rows > 0) {
            log.info("删除版本成功, versionId: {}, resumeId: {}", id, version.getResumeId());
        } else {
            log.error("删除版本失败, versionId: {}", id);
            throw new RuntimeException("删除版本失败");

        }

        afterCommitExecutor.execute(() -> {
            optimizeCacheEvictor.evictByResumeId(resumeId);
            jobMatchServiceGateway.deleteVersionVector(
                    resumeId,
                    versionId
            );
        });

        log.info(
                "删除版本成功: versionId={}, resumeId={}",
                versionId,
                resumeId
        );
        return null;

    }

    private ResumeVersion findOwnedVersion(Long versionId) {
        Long currentUserId = AuthContext.getUserId();
        ResumeVersion version = resumeVersionMapper.selectById(versionId);

        if (version == null || !currentUserId.equals(version.getUserId())) {
            throw new IllegalArgumentException("版本不存在");
        }

        return version;
    }
}
