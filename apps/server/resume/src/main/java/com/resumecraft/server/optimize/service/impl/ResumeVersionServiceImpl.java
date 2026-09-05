package com.resumecraft.server.optimize.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.resumecraft.server.common.security.AuthContext;
import com.resumecraft.server.optimize.service.ResumeVersionService;
import com.resumecraft.server.resume.domain.ResumeMapper;
import com.resumecraft.server.resume.domain.ResumeVersion;
import com.resumecraft.server.resume.domain.ResumeVersionMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class ResumeVersionServiceImpl implements ResumeVersionService {
    @Resource
    private ResumeVersionMapper resumeVersionMapper;

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
    public Void delete(Long id) {
        ResumeVersion version = resumeVersionMapper.selectById(id);
        Long currentUserId = AuthContext.getUserId();
        //1.查询版本是否存在
        if (version == null) {
            log.warn("版本不存在, versionId: {}", id);
            throw new IllegalArgumentException("版本不存在");
        }

        //2.校验归属权
        if (!currentUserId.equals(version.getUserId())) {
            log.warn("越权删除, versionId: {}, 当前用户: {}, 版本所属用户: {}",
                    id, currentUserId, version.getUserId());
            throw new IllegalArgumentException("无权删除该版本");
        }

        int rows = resumeVersionMapper.deleteById(id);
        if (rows > 0) {
            log.info("删除版本成功, versionId: {}, resumeId: {}", id, version.getResumeId());
        } else {
            log.error("删除版本失败, versionId: {}", id);
            throw new RuntimeException("删除版本失败");

        }
        return null;

    }
}
