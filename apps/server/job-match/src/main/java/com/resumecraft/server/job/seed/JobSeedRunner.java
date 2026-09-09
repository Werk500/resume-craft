package com.resumecraft.server.job.seed;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecraft.server.job.domain.Job;
import com.resumecraft.server.job.domain.JobMapper;
import com.resumecraft.server.job.service.JobService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;


@Component
@Slf4j
public class JobSeedRunner implements CommandLineRunner {

    @Resource
    private JobMapper jobMapper;
    @Resource
    private ObjectMapper objectMapper;



    /**
     * Spring 启动后自动执行
     * @param args incoming main method arguments
     * @throws Exception
     */
    @Override
    public void run(String... args) throws Exception {


        List<Job> jobs = readSeed();

        //把 sourceUrl 为 null 或空的过滤
        //把每个 Job 对象的 sourceUrl 作为 Map 的 Key
        //使用有序 Map，保持插入顺序
        Map<String, Job> dedup = jobs.stream()
                .filter(j -> j.getSourceUrl() != null && !j.getSourceUrl().isBlank())
                .collect(Collectors.toMap(Job::getSourceUrl, j -> j, (a, b) -> a, LinkedHashMap::new));

        //2.只查一次已存在的URL
        Set<String> existing = jobMapper.selectList(new LambdaQueryWrapper<Job>()
                        .select(Job::getSourceUrl)
                        .in(Job::getSourceUrl, dedup.keySet()))
                .stream().map(Job::getSourceUrl).collect(Collectors.toSet());

        List<Job> toInsert = dedup.values().stream()
                .filter(j -> !existing.contains(j.getSourceUrl()))
                .toList();

        //3.批量插入，每批200条提交一条
        if (!toInsert.isEmpty()) {
//            //底层逻辑是 MyBatis-Plus 的 ServiceImpl.saveBatch：
//            // 它按 batchSize 每 200 条一批切换成 ExecutorType.BATCH 执行并提交
//            jobService.saveBatch(toInsert,200);
            int inserted = jobMapper.insertIgnoreBatch(toInsert);
            log.info("JD seed: total={}, existing={}, inserted={}", dedup.size(), existing.size(), inserted);
        }
    }

    /**
     * 读 job_seed.json
     */
    private List<Job> readSeed() {
        ClassPathResource resource = new ClassPathResource("db/jobs.json");

        try {
            return objectMapper.readValue(
                    resource.getInputStream(),
                    new TypeReference<>() {
                    });
        } catch (IOException e) {
            throw new IllegalStateException("读取种子数据失败", e);
        }

    }


}
