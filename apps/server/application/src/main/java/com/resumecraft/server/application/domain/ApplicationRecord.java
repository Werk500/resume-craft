package com.resumecraft.server.application.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 投递记录实体。
 */
@Data
@TableName("application_record")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApplicationRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归属用户（数据隔离） */
    private Long userId;

    private Long resumeVersionId;

    private Long jobId;

    private LocalDate appliedAt;

    private String channel;

    private String status;

    private String notes;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}