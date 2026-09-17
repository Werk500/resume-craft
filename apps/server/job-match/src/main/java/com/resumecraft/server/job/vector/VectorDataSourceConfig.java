package com.resumecraft.server.job.vector;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * pgvector 向量库数据源（PostgreSQL）。
 *
 * <h3>为什么是"第二数据源"</h3>
 * 项目的业务数据在 MySQL（resume_craft），向量数据在 PostgreSQL（resume_craft_vector）。
 * 两者事务边界必须隔离：向量写入失败不能回滚业务事务。
 *
 * <h3>为什么用 JdbcTemplate 而不是第二个 SqlSessionFactory</h3>
 * {@code JobServerApplication} 上的 {@code @MapperScan(basePackages = "com.resumecraft.server")}
 * 会扫描所有 {@code @Mapper} 接口。若 EmbeddingMapper 保持 @Mapper 写法，它会被注册到
 * MySQL 的 SqlSessionFactory 上——查询就会打到错的库。
 * 用独立的 JdbcTemplate 可以彻底绕开这个冲突，且向量读写只有 6 个简单语句，无需 ORM。
 *
 * <h3>为什么不加 @Primary</h3>
 * 主数据源由 Spring Boot 按 {@code spring.datasource.*} 自动装配，MyBatis-Plus 依赖它。
 * 这里刻意不参与自动装配（不标注 @Primary、不覆盖 @ConditionalOnMissingBean），
 * 只是额外暴露一个按名字注入的 JdbcTemplate bean。
 */
@Slf4j
@Configuration
public class VectorDataSourceConfig {

    /** 只注入到向量仓储，不参与 Spring 事务管理 */
    @Bean(name = "vectorJdbcTemplate")
    public JdbcTemplate vectorJdbcTemplate(
            @Value("${app.vector.pg.url:jdbc:postgresql://localhost:5432/resume_craft_vector}") String url,
            @Value("${app.vector.pg.username:postgres}") String username,
            @Value("${app.vector.pg.password:}") String password,
            @Value("${app.vector.pg.driver-class-name:org.postgresql.Driver}") String driverClassName,
            @Value("${app.vector.pg.max-pool-size:4}") int maxPoolSize,
            @Value("${app.vector.pg.connection-timeout-ms:3000}") long connectionTimeoutMs) {

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("pgvector-pool");
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setDriverClassName(driverClassName);
        // 连接池刻意开小：向量读写是低频辅助操作，不值得占用过多连接
        dataSource.setMaximumPoolSize(maxPoolSize);
        dataSource.setConnectionTimeout(connectionTimeoutMs);
        // 关键：初始化失败不要阻断服务启动，否则 PG 没开整个 job-match 就起不来，
        // 降级能力就失去意义了。首次查询失败时由 VectorStore 捕获并回落 AI 近似打分。
        dataSource.setInitializationFailTimeout(-1);

        log.info("pgvector 数据源已配置: url={}, username={}, maxPoolSize={}", url, username, maxPoolSize);
        return new JdbcTemplate(dataSource);
    }
}
