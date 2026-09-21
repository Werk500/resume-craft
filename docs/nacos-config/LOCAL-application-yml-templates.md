# Nacos 配置中心改造：本地 application.yml 瘦身模板

业务配置已全部迁移到 Nacos，本地只保留「连上配置中心之前必须知道」的引导配置。

| 服务 | 端口 | application.name | 引用的 Nacos 配置 |
|---|---|---|---|
| auth-service | 8081 | `auth-service` | `auth-service.yml` + `common.yml` |
| resume-service | 8082 | `resume-service` | `resume-service.yml` + `common.yml` |
| job-match-service | 8083 | `job-match-service` | `job-match-service.yml` + `common.yml` |
| application-service | 8084 | `application-service` | `application-service.yml` + `common.yml` |
| gateway | 8080 | `resume-craft-gateway` | `gateway.yml` + `common.yml` |

---

## 通用模板（改 port 与 name 即可）

```yaml
server:
  port: 8082          # 按服务修改

spring:
  application:
    name: resume-service     # 按服务修改：必须与 Nacos 的 Data ID 前缀一致
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  config:
    import:
      # optional 前缀：Nacos 不可用时服务照常启动，配置中心不应成为单点故障
      - optional:nacos:${spring.application.name}.yml
      - optional:nacos:common.yml?group=COMMON
  cloud:
    nacos:
      # server-addr 必须在根级：discovery 与 config 共用
      # 只写在 discovery 下会导致 config 模块的 serverAddr 为 null，拉取配置失败
      server-addr: ${NACOS_ADDR:localhost:8848}
      discovery:
        enabled: true
      config:
        file-extension: yml
        refresh-enabled: true
```

---

## gateway 的差异

网关是 WebFlux，**路由配置必须留在本地**（路由是代码级契约，改动应走发布流程）。
只把 Redis、限流参数、JWT secret 放到 Nacos。

```yaml
server:
  port: 8080

spring:
  application:
    name: resume-craft-gateway
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  config:
    import:
      - optional:nacos:${spring.application.name}.yml
      - optional:nacos:common.yml?group=COMMON
  cloud:
    nacos:
      server-addr: ${NACOS_ADDR:localhost:8848}
      discovery:
        enabled: true
      config:
        file-extension: yml
        refresh-enabled: true
    # ---------- 以下保留在本地 ----------
    gateway:
      server:
        webflux:
          globalcors:
            add-to-simple-url-handler-mapping: true
            cors-configurations:
              '[/**]':
                allowed-origin-patterns:
                  - "http://localhost:3001"
                  - "http://127.0.0.1:3001"
                allowed-methods:
                  - GET
                  - POST
                  - PUT
                  - DELETE
                  - OPTIONS
                allowed-headers: "*"
                allow-credentials: true
                max-age: 3600
          routes:
            - id: auth-service
              uri: lb://auth-service
              predicates:
                - Path=/api/v1/auth/**,/api/v1/oauth2/**
              filters:
                - StripPrefix=0
            - id: resume-service
              uri: lb://resume-service
              predicates:
                - Path=/api/v1/resume/**,/api/v1/diagnose/**,/api/v1/optimize/**,/api/v1/version/**,/api/v1/chat/**
              filters:
                - StripPrefix=0
            - id: job-match-service
              uri: lb://job-match-service
              predicates:
                - Path=/api/v1/job/**,/api/v1/match/**
              filters:
                - StripPrefix=0
            - id: application-service
              uri: lb://application-service
              predicates:
                - Path=/api/v1/application/**
              filters:
                - StripPrefix=0
            - id: actuator-health
              uri: lb://auth-service
              predicates:
                - Path=/actuator/health
              filters:
                - StripPrefix=0

management:
  endpoints:
    web:
      exposure:
        include: health,info,gateway,prometheus
  endpoint:
    health:
      show-details: always
```

---

## 迁移时最容易漏的两处

1. **`server-addr` 的位置**：必须在 `spring.cloud.nacos` 根级。
   放在 `spring.cloud.nacos.discovery` 下时，`NacosConfigProperties.serverAddr` 会是 `null`，
   日志表现为 `Error getting properties from nacos: ... serverAddr='null'`。

2. **Nacos 上的配置内容保持纯 ASCII**：带中文注释会触发
   `YAMLException: MalformedInputException: Input length = 1`。
   中文说明写在项目文档里（即本目录的 `.md` 文件），不要发到 Nacos。

---

## 验证清单（每个服务改造后都要跑）

```powershell
# 1. 配置加载：日志中应出现 success 且无 error
Select-String -Path apps/server/logs/<service>.log -Pattern "Load config|Error getting"

# 2. 配置生效：info 端点应返回标记值
Invoke-RestMethod http://localhost:<port>/actuator/info | ConvertTo-Json

# 3. 动态刷新：改 Nacos 中 common.yml 的 info.configSource 并发布，
#    2~3 秒后重新查询，值应更新且服务未重启

# 4. 核心功能不回归：调用该服务的主要接口确认正常
```
