#!/usr/bin/env node
/**
 * Agent 评测跑批。
 *
 * <h3>为什么是 E2E 脚本而不是 JUnit</h3>
 * Agent 的工具会真的打数据库与匹配引擎，用 mock 只能测"桩数据下的表现"，
 * 回答不了"当前这套提示词 + 工具 + 模型端到端行不行"。所以这里直接打运行中
 * 服务的 SSE 接口：从 tool 帧统计工具选择，从 delta 帧拼出答案判断要点命中。
 * 好处是不需要 Spring 上下文、不依赖 Nacos/DB 连接细节，node 直接跑。
 *
 * <h3>用法</h3>
 *   node apps/server/evals/run-agent-eval.mjs
 *   node apps/server/evals/run-agent-eval.mjs --report docs/agent-eval-report.md
 *
 * 环境变量：BASE_URL / EVAL_USER / EVAL_PASS（默认本机 8080 与 e2e 账号）
 *
 * 注意：LLM 评测天生有波动（同一份数据跑两次可能差 1~2 条边界用例），
 * 所以基线要记成"比率"而不是"全绿"。
 */

import { appendFileSync, readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const BASE_URL = process.env.BASE_URL ?? "http://localhost:8080";
const USER = process.env.EVAL_USER ?? "e2e_1788939179";
const PASS = process.env.EVAL_PASS ?? "E2e@2026";

const here = dirname(fileURLToPath(import.meta.url));
const reportIdx = process.argv.indexOf("--report");
const reportPath = reportIdx >= 0 ? resolve(process.argv[reportIdx + 1]) : null;
const datasetIdx = process.argv.indexOf("--dataset");
const datasetPath = datasetIdx >= 0 ? resolve(process.argv[datasetIdx + 1]) : join(here, "agent-evals.jsonl");

/** 终端里 CJK 占两列，用它对齐表格 */
const width = (s) => [...s].reduce((n, c) => n + (c.codePointAt(0) > 0x2e80 ? 2 : 1), 0);
const pad = (s, w) => s + " ".repeat(Math.max(0, w - width(s)));

function loadCases() {
  return readFileSync(datasetPath, "utf8")
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter((l) => l && !l.startsWith("//"))
    .map((l, i) => {
      try {
        return JSON.parse(l);
      } catch (e) {
        throw new Error(`数据集第 ${i + 1} 行不是合法 JSON：${e.message}`);
      }
    });
}

async function login() {
  const res = await fetch(`${BASE_URL}/api/v1/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: USER, password: PASS }),
  });
  const json = await res.json().catch(() => null);
  if (!res.ok || !json || json.code !== 200) {
    throw new Error(`登录失败：HTTP ${res.status} ${json?.message ?? ""}`);
  }
  return json.data.token;
}

/** 单轮对话：解析 SSE，收集工具名与正文 */
async function ask(token, message, sessionId) {
  const startedAt = Date.now();
  const res = await fetch(`${BASE_URL}/api/v1/agent/chat/stream`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({ message, sessionId }),
  });
  if (!res.ok || !res.body) throw new Error(`流式请求失败：HTTP ${res.status}`);

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let dataLines = [];
  let answer = "";
  let resolvedSessionId = sessionId ?? "";
  const tools = [];

  const dispatch = () => {
    if (dataLines.length === 0) return;
    const payload = dataLines.join("\n");
    dataLines = [];
    if (!payload || payload === "[DONE]") return;
    let event;
    try {
      event = JSON.parse(payload);
    } catch {
      return;
    }
    if (event.type === "start" && event.sessionId) resolvedSessionId = event.sessionId;
    if (event.type === "tool" && event.name) tools.push(event.name);
    if (event.type === "delta" && event.text) answer += event.text;
  };

  while (true) {
    const { done, value } = await reader.read();
    buffer += decoder.decode(value ?? new Uint8Array(), { stream: !done });
    const lines = buffer.split(/\r?\n/);
    buffer = lines.pop() ?? "";
    for (const line of lines) {
      if (line === "") dispatch();
      else if (line.startsWith("data:")) dataLines.push(line.slice(5).replace(/^ /, ""));
    }
    if (done) break;
  }
  dispatch();

  return { answer, tools, sessionId: resolvedSessionId, elapsedMs: Date.now() - startedAt };
}

/**
 * 带一次重试的提问。
 *
 * 只对"传输层失败"重试（连接被重置、流被 terminated）——这类失败来自网络或上游抖动，
 * 与模型行为无关，计进基线会污染数字。模型给出的答案不合格属于评测结论，不重试。
 */
async function askWithRetry(token, message, sessionId, onRetry) {
  try {
    return await ask(token, message, sessionId);
  } catch (e) {
    if (!isTransportError(e)) throw e;
    onRetry();
    await new Promise((r) => setTimeout(r, 1500));
    return await ask(token, message, sessionId);
  }
}

function isTransportError(e) {
  const msg = String(e?.message ?? "");
  return /terminated|ECONNRESET|socket hang up|fetch failed|other side closed/i.test(msg);
}

async function clearSession(token, sessionId) {
  try {
    await fetch(`${BASE_URL}/api/v1/agent/session/${encodeURIComponent(sessionId)}`, {
      method: "DELETE",
      headers: { Authorization: `Bearer ${token}` },
    });
  } catch {
    // 清理失败不影响评测结论
  }
}

function scoreCase(testCase, actualTools, answer) {
  const toolsCovered = testCase.expectTools.every((t) => actualTools.includes(t));
  const noUnexpectedTools = testCase.expectTools.length > 0 || actualTools.length === 0;
  const hits = testCase.expectKeywords.filter((k) => answer.includes(k));
  const keywordsOk = hits.length === testCase.expectKeywords.length;
  // 负向断言：答案里不能出现这些内容（用于注入防护这类"应当拒绝"的用例）
  const rejected = (testCase.rejectKeywords ?? []).filter((k) => answer.includes(k));
  const rejectOk = rejected.length === 0;
  return {
    toolsOk: toolsCovered && noUnexpectedTools,
    hits,
    keywordsOk,
    rejected,
    rejectOk,
    pass: toolsCovered && noUnexpectedTools && keywordsOk && rejectOk,
  };
}

function reason(row) {
  if (row.error) return `请求失败：${row.error}`;
  const parts = [];
  if (!row.toolsOk) {
    parts.push(
      row.c.expectTools.length === 0
        ? `不该调工具，实际调了 ${row.actual.join(",") || "-"}`
        : `期望工具 ${row.c.expectTools.join(",")}，实际 ${row.actual.join(",") || "-"}`,
    );
  }
  if (!row.keywordsOk) {
    const missed = row.c.expectKeywords.filter((k) => !row.hits.includes(k));
    parts.push(`答案里没提到：${missed.join("、")}`);
  }
  if (!row.rejectOk) {
    parts.push(`答案里出现了不该出现的内容：${row.rejected.join("、")}`);
  }
  return parts.join("；");
}

async function main() {
  const cases = loadCases();
  const token = await login();
  console.log(`用例 ${cases.length} 条 | 账号 ${USER} | 目标 ${BASE_URL}`);
  console.log("跑批中（每条 1~3 轮真实模型调用，约 2~4 分钟）…\n");

  const rows = [];
  const createdSessions = [];
  let transportRetries = 0;

  for (const testCase of cases) {
    let sessionId;
    let elapsedMs = 0;
    const tools = new Set();
    let answer = "";
    let error = "";

    try {
      for (const turn of testCase.turns) {
        const r = await askWithRetry(token, turn, sessionId, () => {
          transportRetries++;
        });
        sessionId = r.sessionId || sessionId;
        elapsedMs += r.elapsedMs;
        r.tools.forEach((t) => tools.add(t));
        answer = r.answer;
      }
      if (sessionId) createdSessions.push(sessionId);
    } catch (e) {
      error = e.message;
    }

    const actualTools = [...tools];
    const score = scoreCase(testCase, actualTools, answer);
    const row = { c: testCase, actual: actualTools, elapsedMs, answer, error, ...score };
    rows.push(row);
    console.log(
      `  ${score.pass ? "PASS" : "FAIL"}  ${pad(testCase.id, 4)}${pad(testCase.category, 14)}` +
        `工具[${actualTools.join(",") || "-"}]`,
    );
  }

  const passed = rows.filter((r) => r.pass).length;
  const toolsPassed = rows.filter((r) => r.toolsOk).length;
  const kwTotal = rows.reduce((n, r) => n + r.c.expectKeywords.length, 0);
  const kwHit = rows.reduce((n, r) => n + r.hits.length, 0);
  const avgMs = Math.round(rows.reduce((n, r) => n + r.elapsedMs, 0) / rows.length / 100) / 10;
  const avgTools = (rows.reduce((n, r) => n + r.actual.length, 0) / rows.length).toFixed(2);
  const failed = rows.filter((r) => !r.pass);
  const rate = (n, d) => `${n}/${d} (${Math.round((n / d) * 100)}%)`;

  const summary = [
    `用例通过率 ${rate(passed, rows.length)}`,
    `工具选择 ${rate(toolsPassed, rows.length)}`,
    `要点命中 ${rate(kwHit, kwTotal)}`,
    `平均耗时 ${avgMs}s`,
    `平均工具调用 ${avgTools} 次`,
    `传输层重试 ${transportRetries} 次`,
  ];

  console.log("\n================ 汇总 ================");
  summary.forEach((s) => console.log("  " + s));
  if (failed.length > 0) {
    console.log("\n失败清单：");
    failed.forEach((r) => {
      console.log(`  ${r.c.id}（${r.c.category}）：${reason(r)}`);
      // 光看"失败了"没法定位，附一段答案原文（这是失败清单最大的价值）
      const snippet = (r.answer || "").replace(/\s+/g, " ").slice(0, 120);
      if (snippet) console.log(`      答案片段：${snippet}`);
    });
  }

  // 清理本次评测创建的会话（顺带验证清会话接口）
  for (const sid of createdSessions) await clearSession(token, sid);
  console.log(`\n已清理 ${createdSessions.length} 个评测会话的记忆。`);

  if (reportPath) {
    const stamp = new Date().toLocaleString("zh-CN", { timeZone: "Asia/Shanghai" });
    const lines = [];
    lines.push(`\n## 评测记录 ${stamp}\n`);
    lines.push(`目标 \`${BASE_URL}\`，账号 \`${USER}\`\n`);
    lines.push("| ID | 类别 | 工具（期望 → 实际） | 要点 | 耗时 | 结果 |");
    lines.push("|---|---|---|---|---|---|");
    for (const r of rows) {
      lines.push(
        `| ${r.c.id} | ${r.c.category} | ${r.c.expectTools.join(",") || "-"} → ${r.actual.join(",") || "-"} | ` +
          `${r.hits.length}/${r.c.expectKeywords.length} | ${(r.elapsedMs / 1000).toFixed(1)}s | ${r.pass ? "✅" : "❌"} |`,
      );
    }
    lines.push("");
    summary.forEach((s) => lines.push(`- ${s}`));
    if (failed.length > 0) {
      lines.push("");
      lines.push("失败明细：");
      failed.forEach((r) => lines.push(`- \`${r.c.id}\`（${r.c.category}）：${reason(r)}`));
    }
    appendFileSync(reportPath, lines.join("\n") + "\n");
    console.log(`报告已追加到 ${reportPath}`);
  }
}

main().catch((e) => {
  console.error("评测失败：" + e.message);
  process.exit(1);
});
