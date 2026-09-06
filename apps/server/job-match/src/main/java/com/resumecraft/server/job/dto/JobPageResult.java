package com.resumecraft.server.job.dto;

import com.resumecraft.server.job.domain.Job;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobPageResult {

    /** 当前页的岗位列表 */
    private List<Job> list;

    /** 符合条件的岗位总数（不是本页条数） */
    private long total;

    /** 当前页码，从 1 开始 */
    private long page;

    /** 每页条数 */
    private long size;
}