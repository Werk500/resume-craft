package com.resumecraft.server.resume.domain;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 简历优化版本 Mapper（复用 resume_version 表）。
 */
@Mapper
public interface ResumeVersionMapper extends BaseMapper<ResumeVersion> {
}
