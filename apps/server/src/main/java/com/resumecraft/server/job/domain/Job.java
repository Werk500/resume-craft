package com.resumecraft.server.job.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 岗位信息实体。
 */
@Data
@TableName("job")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Job {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String company;

    private String title;

    private String department;

    private String location;

    private String salaryRange;

    private String description;

    private String requirements;

    private String sourceUrl;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}