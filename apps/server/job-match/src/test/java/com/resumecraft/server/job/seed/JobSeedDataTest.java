package com.resumecraft.server.job.seed;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecraft.server.job.domain.Job;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验 classpath:db/jobs.json 种子数据的完整性与合法性。
 *
 * 目标：防止种子文件被误改坏（字段缺失、URL 重复、条数归零等），
 * 让问题在 CI 阶段就暴露，而不是等到 JobSeedRunner 启动时才炸。
 *
 * 不启动 Spring，纯文件 + Jackson 校验，秒级完成。
 */
public class JobSeedDataTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static List<Job> JOBS;

    @BeforeAll
    static void loadSeed() throws IOException {
        // 从 classpath 读取 db/jobs.json
        try (InputStream in = JobSeedDataTest.class
                .getClassLoader()
                .getResourceAsStream("db/jobs.json")) {

            assertThat(in)
                    .as("classpath 下必须存在 db/jobs.json")
                    .isNotNull();

            JOBS = MAPPER.readValue(in, new TypeReference<List<Job>>() {});
        }
    }

    @Test
    @DisplayName("db/jobs.json 是合法 JSON，能解析成 List<Job>")
    void shouldBeValidJsonAndParsable() {
        // 能走到这里说明 @BeforeAll 已经解析成功
        assertThat(JOBS)
                .as("解析结果不应为 null")
                .isNotEmpty();
    }

    @Test
    @DisplayName("种子数据条数应大于 0")
    void shouldHaveAtLeastOneJob() {
        assertThat(JOBS)
                .as("种子文件不能为空数组")
                .isNotEmpty();
    }

    @Test
    @DisplayName("每条 sourceUrl 都非空")
    void everyJobShouldHaveNonBlankSourceUrl() {
        List<Job> bad = JOBS.stream()
                .filter(j -> j.getSourceUrl() == null || j.getSourceUrl().isBlank())
                .toList();

        assertThat(bad)
                .as("存在 sourceUrl 为空的记录: %s", bad)
                .isEmpty();
    }

    @Test
    @DisplayName("sourceUrl 全局唯一，不允许重复")
    void sourceUrlShouldBeUnique() {
        List<String> urls = JOBS.stream()
                .map(Job::getSourceUrl)
                .toList();

        Set<String> unique = new HashSet<>(urls);

        assertThat(urls)
                .as("存在重复 sourceUrl，请检查种子文件")
                .doesNotHaveDuplicates();

    }
}
