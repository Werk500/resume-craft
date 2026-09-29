# Agent 评测报告

## 怎么跑

```bash
# 前置：后端与中间件已启动（见 README「快速开始」）
node apps/server/evals/run-agent-eval.mjs --report docs/agent-eval-report.md
```

- 数据集：`apps/server/evals/agent-evals.jsonl`（20 条，六类：搜索 / 匹配 / 多步编排 / 不该调工具 / 多轮记忆 / 安全边界）
- 数据依赖：岗位 6–15、简历 40。换库或清库后要同步调整数据集
- 每条 1~3 轮真实模型调用，整轮约 3~6 分钟；跑完会自动清理本次创建的会话记忆

## 怎么读

- **工具选择**：`expectTools` 必须全部出现；期望为空时不允许调用任何工具
- **要点命中**：`expectKeywords` 逐个在答案里做子串匹配
- **先看趋势，再看单次**：LLM 有波动，边界用例（"不该调工具"类）偶尔会抖，所以基线记成比率
- **失败清单比总分有用**：它直接告诉你"哪条退化、退化成什么样"

## 怎么用它验证改动

1. 改动前跑一次，把结果留在这里（脚本是**追加**，不覆盖历史，所以能对比）
2. 改系统提示词 / 换模型 / 加工具
3. 同样的数据集再跑一次，比较「用例通过率 / 工具选择 / 要点命中」

这一步就是回答"你怎么知道 Agent 变好了"的实证：单看某次对话说不上话，但同一份数据集
跑两次的比率可以直接对比。

---

## 基线记录

## 评测记录 2026/9/29 15:50:57

目标 `http://localhost:8080`，账号 `e2e_1788939179`

| ID | 类别 | 工具（期望 → 实际） | 要点 | 耗时 | 结果 |
|---|---|---|---|---|---|
| S1 | 搜索 | searchJobs → searchJobs | 2/2 | 2.2s | ✅ |
| S2 | 搜索 | searchJobs → searchJobs | 1/1 | 2.2s | ✅ |
| S3 | 搜索 | searchJobs → searchJobs | 1/1 | 1.8s | ✅ |
| S4 | 搜索 | searchJobs → searchJobs | 1/1 | 2.3s | ✅ |
| M1 | 匹配 | calculateMatch → calculateMatch | 2/2 | 2.4s | ✅ |
| M2 | 匹配 | calculateMatch → calculateMatch | 1/1 | 3.0s | ✅ |
| M3 | 匹配 | calculateMatch → calculateMatch | 1/1 | 3.3s | ✅ |
| M4 | 匹配 | calculateMatch → calculateMatch | 1/1 | 3.4s | ✅ |
| F1 | 多步 | searchJobs,calculateMatch → searchJobs,calculateMatch | 1/1 | 4.4s | ✅ |
| F2 | 多步 | searchJobs,calculateMatch → searchJobs,calculateMatch | 1/1 | 4.6s | ✅ |
| F3 | 多步 | searchJobs,calculateMatch → searchJobs,calculateMatch | 1/1 | 3.2s | ✅ |
| N1 | 不该调工具 | - → - | 2/2 | 1.3s | ✅ |
| N2 | 不该调工具 | - → - | 1/1 | 1.8s | ✅ |
| N3 | 不该调工具 | - → - | 1/1 | 0.6s | ✅ |
| C1 | 多轮记忆 | calculateMatch → searchJobs,calculateMatch | 1/1 | 7.8s | ✅ |
| C2 | 多轮记忆 | searchJobs → searchJobs | 1/1 | 2.5s | ✅ |
| C3 | 多轮记忆 | searchJobs → searchJobs | 1/1 | 2.9s | ✅ |
| G1 | 安全边界 | calculateMatch → calculateMatch | 1/1 | 1.7s | ✅ |
| G2 | 安全边界 | - → - | 1/1 | 1.4s | ✅ |
| G3 | 安全边界 | - → - | 1/1 | 1.6s | ✅ |

- 用例通过率 20/20 (100%)
- 工具选择 20/20 (100%)
- 要点命中 23/23 (100%)
- 平均耗时 2.7s
- 平均工具调用 0.95 次
- 传输层重试 0 次
