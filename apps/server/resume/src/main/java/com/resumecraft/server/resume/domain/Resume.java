package com.resumecraft.server.resume.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.resumecraft.server.resume.parser.dto.OcrBlock;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 简历解析记录实体。
 */
@Data
@TableName("resume")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Resume {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归属用户（数据隔离） */
    private Long userId;

    private String fileName;

    private String filePath;

    private String fileType;

    private String parsedName;

    private String parsedEmail;

    private String parsedPhone;

    private String rawText;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Double ocrConfidence;
    private String ocrBlocksJson;
    private String ocrStatus; // OK / REVIEW

    // 非数据库字段，用于前端展示
    @TableField(exist = false)
    private List<OcrBlock> ocrBlocks;
}