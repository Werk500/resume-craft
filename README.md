# AI 简历设计与优化软件（resume-craft）

![CI](https://github.com/Werk500/resume-craft/actions/workflows/ci.yml/badge.svg)

面向校招场景的一站式 AI 简历工具：**上传解析 → AI 诊断 → 一键优化 → 岗位匹配 → 定向优化 → 版本管理 → 投递跟踪**，全流程闭环，匹配结果可解释、可复现。

## 功能概览

| 模块 | 说明 |
|---|---|
| 简历解析 | PDF（PDFBox）、Word（POI）、图片（视觉模型 OCR）；OCR 输出分块置信度，低置信度进入人工作业流 |
| AI 诊断 | 信息完整度 / 表达质量 / 岗位匹配度三维评分 + 改进建议，支持 SSE 流式输出 |
| 一键优化 | 保留事实的整份改写；逐句精修（DATA / METHOD / IMPACT 三个方向） |
| 版本管理 | 任意两个版本并排 Diff（LCS 行级对齐），支持导出 PDF / DOCX |
| 岗位库 | 秋招 JD 种子库（真实校招岗位，`sourceUrl` 唯一索引 + `INSERT IGNORE` 幂等导入） |
| 匹配引擎 | 40/40/20 可解释评分：关键词覆盖 + 语义相似 + 硬性条件；返回命中/缺失关键词与维度明细 |
| 语义向量检索 | 简历与 JD 各自 embedding（百炼 text-embedding-v3，1024 维），PostgreSQL + pgvector 存向量，余弦相似度打分；同一输入结果完全可复现 |
| 异步向量生成 | Kafka 事件驱动：创建/更新简历后异步生成向量，接口无需等待 embedding；重试耗尽的消息进死信队列 |
| 定向优化 | 针对目标 JD 改写并重新匹配，输出「优化前 → 优化后」提升报告与改动原因 |
| 对话创建 | SSE 流式问答从 0 生成 Markdown 简历，可直接保存为简历继续诊断 / 匹配 |
| 投递管理 | 看板式状态流转（待跟进 / 面试中 / 已拒绝 / 无回应 / 已录用）+ 按状态统计 |

## 架构

```
                     ┌────────────────────────────┐
 Browser  :3001 ───▶ │  web (Next.js 14 App Router)│
                     └──────────────┬─────────────┘
                                    │ /api/v1/**
                     ┌──────────────▼─────────────┐
                     │  gateway :8080 (Spring Cloud │
                     │  Gateway + CORS + JWT 透传)  │
                     └───┬───────┬────────┬────────┘
                         │       │        │
              ┌──────────▼─┐ ┌───▼──────┐ ┌▼──────────┐ ┌──────────────┐
              │ auth :8081 │ │resume    │ │job-match  │ │application   │
              │ JWT/注册登录│ │:8082     │ │:8083      │ │:8084         │
              └──────┬─────┘ │解析/诊断/ │ │岗位/匹配/ │ │投递记录       │
                     │       │优化/版本 │ │向量检索   │ │              │
                     │       └────┬─────┘ └────┬──────┘ └──────┬───────┘
                     │            │            │               │
                     │            │  ①发消息    │  ②消费        │
                     │            ▼            ▼               │
                     │      ┌──────────────────────┐            │
                     │      │  Kafka :9092         │            │
                     │      │  embedding-task (3P) │            │
                     │      │  + DLQ 死信队列       │            │
                     │      └──────────────────────┘            │
                     │                                          │
              ┌──────▼──────────────────────────────────────────▼─────┐
              │  MySQL :3306 (resume_craft)   Redis :6379              │
              │  PostgreSQL :5432 (pgvector 向量库)                     │
              │  Nacos :8848 (服务注册 / 发现)                          │
              └───────────────────────────────────────────────────────┘
```

### 关键设计

- **服务间调用**：OpenFeign + LoadBalancer，`/internal/**` 通过 `X-Internal-Token` 校验
- **数据隔离**：业务接口统一从 JWT 解析 `userId`（`AuthContext`），越权统一按"资源不存在"处理
- **缓存**：诊断 / 优化 / 匹配结果缓存 Redis，含空值缓存（防穿透）与随机 TTL（防雪崩）
- **语义评分三级降级**：向量检索（`EMBEDDING`）→ 大模型近似打分（`AI_APPROX`）→ 固定分兜底
  （`RULE_FALLBACK`），评分来源通过 `dimensionDetails.semantic.mode` 上报前端
- **异步向量生成**：简历创建/更新后发 Kafka 消息，消费端生成向量写入 pgvector。
  消息在**事务提交后**发送，避免消费者读到未提交数据；消费端靠 `contentHash` + 唯一约束实现幂等；
  重试耗尽转入死信 topic，避免毒消息阻塞分区
- **降级不影响主流程**：Kafka 不可用时上传照常成功，匹配路径会同步补生成向量

### 消息队列为何不覆盖匹配接口

匹配是用户主动等待、需要立即看到结果的操作，改成异步会让体验从"点一下出结果"退化为"提交任务等通知"。
Kafka 只用在不影响用户感知的后台任务上（向量预生成）。

## 技术栈

- **后端**：Java 17 · Spring Boot 3.5 · Spring Cloud Gateway / OpenFeign / LoadBalancer · Nacos · MyBatis-Plus 3.5 · MySQL · Redis · PostgreSQL + pgvector（向量检索）· Kafka（异步消息）· Spring AI 1.1（OpenAI 兼容，默认 DeepSeek；embedding 用百炼 text-embedding-v3）· JWT（jjwt）· springdoc-openapi · Micrometer + Prometheus
- **前端**：Next.js 14（App Router）· TypeScript · Tailwind CSS · Recharts · lucide-react

## 目录结构

```
.
├── apps/
│   ├── server/                 # 后端多模块工程
│   │   ├── common/             # 公共：AI/Embedding 抽象、Kafka topic 与消息体、
│   │   │                       #       鉴权、异常、DTO、线程池
│   │   ├── gateway/            # API 网关 :8080
│   │   ├── auth/               # 认证服务 :8081
│   │   ├── resume/             # 简历服务 :8082（解析/诊断/优化/版本/对话创建，
│   │   │                       #       向量任务生产者）
│   │   ├── job-match/          # 岗位与匹配服务 :8083（含向量库读写与
│   │   │                       #       向量任务消费者）
│   │   ├── application/        # 投递服务 :8084
│   │   ├── db/                 # pgvector 建表脚本（PostgreSQL）
│   │   ├── start-services.ps1  # 一键构建 + 启动 + 健康检查
│   │   ├── stop-services.ps1   # 停止全部服务
│   │   ├── demo.ps1            # 一键演示完整闭环（诊断/匹配/优化/投递）
│   │   ├── reset-demo.ps1      # 重置演示账号数据 + 清理缓存
│   │   ├── smoke-test.ps1      # 冒烟测试
│   │   └── verify-interfaces.ps1
│   └── web/                    # 前端 Next.js 应用 :3001
├── docs/                       # 测试样例
└── docker-compose.yml          # 中间件（MySQL / Redis / Nacos）
```

## 快速开始

### 1. 启动中间件

需要本机可用：

| 中间件 | 端口 | 用途 | 是否必需 |
|---|---|---|---|
| MySQL | 3306 | 业务库 `resume_craft`，首次启动自动建表 | 必需 |
| Redis | 6379 | 缓存、限流、分布式锁 | 必需 |
| Nacos | 8848 | 服务注册发现 | 必需 |
| PostgreSQL | 5432 | 向量库 `resume_craft_vector`（pgvector） | 可选* |
| Kafka | 9092 | 异步向量生成 | 可选* |

\* 未部署时系统自动降级：无 PostgreSQL 时语义评分回落大模型近似打分；无 Kafka 时向量在匹配请求内同步生成。**功能仍可用，只是失去可复现性与异步加速。**

**向量库初始化**（部署 PostgreSQL 时执行一次）：

```bash
psql -U postgres -c "CREATE DATABASE resume_craft_vector ENCODING 'UTF8';"
psql -U postgres -d resume_craft_vector -c "CREATE EXTENSION IF NOT EXISTS vector;"
psql -U postgres -d resume_craft_vector -f apps/server/db/pgvector_schema.sql
```

**Kafka 启动**（KRaft 模式，无需 ZooKeeper）：

```powershell
$KAFKA_HOME = "D:\JAVA001\kafka\kafka_2.13-4.3.1"
$env:KAFKA_HEAP_OPTS = "-Xmx512M -Xms256M"
& "$KAFKA_HOME\bin\windows\kafka-server-start.bat" "$KAFKA_HOME\config\server.properties"
```

topic（`embedding-task` 3 分区 + `embedding-task-dlq`）由应用启动时自动创建，无需手动建。

### 2. 配置环境变量

```bash
cp .env.example .env
# 编辑 .env，填入 AI_API_KEY（不填则用 Mock AI，可用规则/固定数据联调）
```

| 变量 | 说明 | 默认 |
|---|---|---|
| `AI_API_KEY` | 大模型 API Key（DeepSeek 兼容） | 无（Mock） |
| `AI_BASE_URL` | 模型服务地址 | `https://api.deepseek.com` |
| `AI_MODEL` | 模型名 | `deepseek-chat` |
| `EMBEDDING_API_KEY` | 向量化模型 API Key（阿里云百炼）。留空则语义评分降级为 AI 近似 | 无（降级） |
| `EMBEDDING_BASE_URL` | 向量化服务地址 | `https://dashscope.aliyuncs.com/compatible-mode` |
| `EMBEDDING_MODEL` | 向量化模型名 | `text-embedding-v3` |
| `EMBEDDING_DIMENSIONS` | 向量维度（须与建表时的 `VECTOR(n)` 一致） | `1024` |
| `PG_URL` | pgvector 连接串 | `jdbc:postgresql://localhost:5432/resume_craft_vector` |
| `PG_USER` / `PG_PASSWORD` | pgvector 账号 | `postgres` / 空 |
| `KAFKA_SERVERS` | Kafka 地址 | `localhost:9092` |
| `VECTOR_ENABLED` | 向量检索开关（`false` 时强制走 AI 近似，用于验证降级） | `true` |
| `JWT_SECRET` | JWT 签名密钥（≥32 字节） | 开发默认值 |
| `INTERNAL_TOKEN` | 服务间内部调用令牌 | `dev-internal-token` |

### 3. 启动后端

```powershell
cd apps/server
pwsh ./start-services.ps1          # 构建 + 停旧进程 + 启动 5 个服务 + 健康检查
pwsh ./start-services.ps1 -SkipBuild   # 跳过构建（jar 已存在时）
```

启动完成后：

| 服务 | 地址 | 接口文档 |
|---|---|---|
| Gateway | http://localhost:8080 | — |
| auth | http://localhost:8081 | http://localhost:8081/swagger-ui.html |
| resume | http://localhost:8082 | http://localhost:8082/swagger-ui.html |
| job-match | http://localhost:8083 | http://localhost:8083/swagger-ui.html |
| application | http://localhost:8084 | http://localhost:8084/swagger-ui.html |

### 4. 启动前端

```bash
cd apps/web
npm install
npm run dev        # http://localhost:3001（API 默认经网关 8080）
```

### 5. 停止服务

```powershell
cd apps/server
pwsh ./stop-services.ps1 -Force
```

### 方式 B：Docker 部署（可选）

> 前提：Docker Desktop 已启动。后端为多阶段构建（Maven → JRE），前端为 Next.js 多阶段构建（`npm ci` → `next build` → `next start`），首次构建需下载依赖，耗时较长。

**B1：应用容器化 + 宿主机中间件（推荐，本机已有 MySQL / Redis / Nacos / PostgreSQL / Kafka）**

```bash
# 先停掉本机直接运行的后端，避免 8080 端口冲突
pwsh apps/server/stop-services.ps1 -Force

docker compose -f docker-compose.app.yml up -d --build
```

- 前端：http://localhost:3001 ，网关：http://localhost:8080
- 容器通过 `host.docker.internal` 访问宿主机中间件（compose 已配置 `extra_hosts`）
- 需先在本机启动 PostgreSQL（5432）与 Kafka（9092）；若未启动，向量检索与异步任务自动降级
- 停止：`docker compose -f docker-compose.app.yml down`

**B2：全套容器化（含 MySQL / Redis / Nacos / PostgreSQL+pgvector / Kafka，适合全新环境）**

```bash
docker compose -f docker-compose.full.yml up -d --build
```

- 中间件只在容器网络内暴露，**不映射宿主机端口**，避免与本机已安装的服务冲突
- pgvector 建表脚本通过 `docker-entrypoint-initdb.d` 在容器首次初始化时自动执行
- 需要能正常拉取 `nacos/nacos-server` 镜像；内网受限时用 B1
- 停止：`docker compose -f docker-compose.full.yml down`；连数据一起清理：`down -v`

AI Key 从根目录 `.env` 读取（`AI_API_KEY` 等）；未配置时使用占位值，AI 相关功能不可用。

## 演示路径（校招闭环）

1. 注册 / 登录 → 上传简历（PDF / Word / 图片）
2. 图片简历若识别置信度低 → 进入「人工核对」页逐块确认，确认前禁止自动评分
3. 简历诊断 → 查看三维评分与建议；一键优化 / 逐句精修
4. 岗位库选择目标 JD → 匹配（可解释：命中/缺失关键词、维度明细）
5. 定向优化 → 查看「优化前 → 优化后」提升报告与改动原因
6. 版本历史并排 Diff → 导出 PDF / DOCX → 记录投递并跟踪状态

## 测试

后端单元测试（纯 Mockito，无需 MySQL/Redis/Nacos）：

```powershell
cd apps/server
mvn -B test
```

覆盖内容：匹配引擎 40/40/20 公式与硬性条件封顶、AI 失败降级、Feign 跨服务错误透传、OCR 置信度阈值判定、JD 种子数据契约（共 21 个用例）。

CI（GitHub Actions）在每次 push / PR 时执行后端 `mvn -B test` 与前端 `npm ci && npm run build`。

## 一键演示

后端已启动时：

```powershell
cd apps/server
pwsh ./demo.ps1
```

脚本会自动完成：注册/登录演示账号 → 创建简历 → AI 诊断 → 选择岗位 → 匹配 → 定向优化 → 优化前后提升对比 → 记录投递并统计。

| 参数 | 说明 |
|---|---|
| `-StartServices` | 自动启动后端（跳过构建） |
| `-KeepData` | 演示结束后保留演示简历（默认级联清理） |

重置演示数据与缓存：

```powershell
pwsh ./reset-demo.ps1
```

## 已知限制与后续规划

- **集成测试待补充**：当前 21 个单元测试用 Mockito 隔离，尚无 Testcontainers 级别的跨服务联调测试
- **前端组件测试缺失**：仅有 `tsc` 类型检查与 `next build` 构建校验
- **向量阈值需按业务校准**：余弦相似度到百分制的映射区间（`0.30~0.90`）目前基于经验值，理想做法是收集真实简历-JD 对做标注校准
- **岗位数据来自种子库**：未接入实时爬取（合规与反爬成本考虑）
- **登录方式单一**：仅用户名密码，未接入第三方登录
- **本机开发依赖较多中间件**：MySQL / Redis / Nacos / PostgreSQL / Kafka；其中 PostgreSQL 与 Kafka 为可选，缺失时自动降级

## 技术亮点速览

面向面试的架构决策记录，每条都对应可验证的实现：

| 亮点 | 实现要点 | 可验证性 |
|---|---|---|
| **可解释匹配** | 40/40/20 加权，输出关键词命中/缺失、语义归因、维度明细 | 响应含 `dimensionDetails` |
| **评分可复现** | 语义分从"大模型打分"改为"向量余弦相似度" | 同一输入多次调用分数完全一致 |
| **三级降级** | 向量检索 → AI 近似 → 固定分，来源经 `semantic.mode` 上报 | 关闭 `VECTOR_ENABLED` 可现场验证 |
| **异步向量生成** | Kafka 事件驱动，接口不再等待 embedding | 接口 16~23ms 返回，向量约 2s 后生成 |
| **事务边界正确** | 消息在 `afterCommit` 后发送，避免消费者读到未提交数据 | `AfterCommitExecutor` |
| **消费幂等** | `contentHash` 短路 + 唯一约束双保险 | 重复投递不产生重复行 |
| **异步与删除的竞态处理** | 消费前校验 + 写入后复检，发现简历已删则回滚向量 | 创建后 0/100ms 删除均无孤儿数据 |
| **故障降级** | Kafka 不可用时上传照常成功，匹配路径同步兜底生成向量 | 停 Kafka 后上传仍返回 200 |
| **全链路可观测** | traceId 网关生成 → MDC 日志 → Feign 透传 → 响应头回写 | 响应头含 `X-Trace-Id` |
| **网关限流** | Redis 令牌桶，按 JWT userId 分桶、未登录回落 IP | 可压测验证 |
