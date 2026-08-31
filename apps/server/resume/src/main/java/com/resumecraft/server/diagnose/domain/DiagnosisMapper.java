package com.resumecraft.server.diagnose.domain;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

/**
 * 诊断报告 Mapper。
 */
@Mapper
public interface DiagnosisMapper extends BaseMapper<Diagnosis> {

    @Select("SELECT * FROM diagnosis WHERE resume_id = #{resumeId}")
    Optional<Diagnosis> findByResumeId(Long resumeId);
}