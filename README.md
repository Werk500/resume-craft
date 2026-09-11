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
                     │       │优化/版本 │ │种子库     │ │              │
                     │       └────┬─────┘ └────┬──────┘ └──────┬───────┘
                     │            │            │               │
              ┌──────▼────────────▼────────────▼───────────────▼──────┐
              │        MySQL :3306 (resume_craft) · Redis :6379       │
              │        Nacos :8848 (服务注册 / 发现)                    │
              └───────────────────────────────────────────────────────┘
```

- 服务间调用：OpenFeign + LoadBalancer，`/internal/**` 通过 `X-Internal-Token` 校验
- 数据隔离：业务接口统一从 JWT 解析 `userId`（`AuthContext`），越权统一按“资源不存在”处理
- 缓存：诊断 / 优化 / 匹配结果缓存 Redis，含空值缓存（防穿透）与随机 TTL（防雪崩）

## 技术栈

- **后端**：Java 17 · Spring Boot 3.5 · Spring Cloud Gateway / OpenFeign / LoadBalancer · Nacos · MyBatis-Plus 3.5 · MySQL · Redis · Spring AI 1.1（OpenAI 兼容，默认 DeepSeek）· JWT（jjwt）· springdoc-openapi
- **前端**：Next.js 14（App Router）· TypeScript · Tailwind CSS · Recharts · lucide-react

## 目录结构

```
.
├── apps/
│   ├── server/                 # 后端多模块工程
│   │   ├── common/             # 公共：AI 抽象、鉴权、异常、DTO、线程池
│   │   ├── gateway/            # API 网关 :8080
│   │   ├── auth/               # 认证服务 :8081
│   │   ├── resume/             # 简历服务 :8082（解析/诊断/优化/版本/对话创建）
│   │   ├── job-match/          # 岗位与匹配服务 :8083
│   │   ├── application/        # 投递服务 :8084
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

需要本机可用：MySQL（3306，库名 `resume_craft`）、Redis（6379）、Nacos（8848）。首次启动后端时 `schema.sql` 会自动建表。

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

- 集成测试（Testcontainers 级别的服务间联调）与前端组件测试待补充
- 可观测性与治理待加强：跨服务 traceId、Prometheus 指标、网关限流、AI 调用熔断/重试
- 目前依赖本机中间件启动，Dockerfile / 一键 Compose 全套部署待完善
- 登录方式目前仅用户名密码，未接入第三方登录
