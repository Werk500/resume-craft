# AI简历设计与优化软件

一站式 AI 简历工具：简历诊断、AI 优化、目标岗位智能匹配。

## 目录结构

```
.
├── 需求文档.docx / .md     # 需求（活文档，随里程碑更新）
├── 开发计划.md             # 分里程碑开发计划
├── docker-compose.yml      # 本地中间件：PostgreSQL(pgvector)/Redis/MinIO
├── apps/
│   ├── web/                # 前端：Next.js 14 + TypeScript + Tailwind + shadcn/ui
│   └── server/             # 后端：Spring Boot 3.2（模块化单体，按领域分包）
└── docs/                   # 技术决策记录、接口文档
```

## 技术栈

- 前端：Next.js 14 · TypeScript · Tailwind CSS · shadcn/ui · Zustand · Framer Motion · Recharts
- 后端：Spring Boot 3.2 · Spring AI · Spring Security + JWT · Maven
- 数据：PostgreSQL + pgvector · Redis · MinIO
- AI：国产大模型（默认 DeepSeek，开发期 Mock 先行）

## 快速开始

> 详见各里程碑的启动说明，此处随 M0 逐步填充。

### 中间件
```bash
docker compose up -d
```

### 后端
```bash
cd apps/server && mvn spring-boot:run
# 访问 http://localhost:8088
```

### 前端
```bash
cd apps/web && npm install && npm run dev
# 访问 http://localhost:3001
```