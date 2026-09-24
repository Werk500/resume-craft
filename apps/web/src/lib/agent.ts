import { API_BASE, clearAuth } from "./api";

/**
 * 求职助手 Agent 对话。
 *
 * 两条通道：
 * - 同步 {@link askAgent}：POST /api/v1/agent/chat，一次性返回完整答案（保留用于排查与对比）
 * - 流式 {@link streamAgentChat}：POST /api/v1/agent/chat/stream，SSE 逐帧推送
 *
 * 注意两者响应形态不同：同步通道走 common 的 GlobalExceptionHandler，
 * 被包成 { code, message, data } 信封；流式通道是裸 SSE 帧（见 AgentStreamEvent）。
 */

export interface AgentReply {
  sessionId: string;
  answer: string;
}

/**
 * SSE 事件帧契约，与后端 AgentController#chatStream 一一对应。
 *
 * 为什么是「带 type 的 JSON 帧」而不是纯文本流：
 * Agent 和普通 chatbot 不同——它要先调工具再组织回答。
 * 前端需要区分「正在调工具」和「正在输出文字」两种等待态，
 * 纯文本流区分不了，只能靠前端定时轮播文案假装阶段（改之前就是假进度条）。
 */
export type AgentStreamEvent =
  | { type: "start"; sessionId?: string }
  | { type: "tool"; name: string }
  | { type: "delta"; text: string }
  | { type: "done"; sessionId?: string; answer?: string }
  | { type: "error"; message: string };

/** 工具方法名 → 展示文案。后端推的是 Java 方法名，界面需要翻译成人话。 */
const AGENT_TOOL_LABELS: Record<string, string> = {
  searchJobs: "正在搜索岗位…",
  calculateMatch: "正在调用匹配引擎算分…",
};

export function agentToolLabel(name: string): string {
  return AGENT_TOOL_LABELS[name] ?? `正在调用工具 ${name}…`;
}

export async function askAgent(message: string, sessionId?: string): Promise<AgentReply> {
  let token: string | null = null;
  if (typeof window !== "undefined") {
    token = localStorage.getItem("token");
  }

  const response = await fetch(`${API_BASE}/api/v1/agent/chat`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ message, sessionId }),
  });

  if (isAuthFailure(response.status)) {
    requireLogin();
  }

  const json = await response.json().catch(() => null);

  if (!response.ok || !json || json.code !== 200) {
    throw new Error(json?.message || `请求失败 (HTTP ${response.status})`);
  }

  return json.data as AgentReply;
}

/**
 * 流式对话（SSE）。
 *
 * 后端契约：POST /api/v1/agent/chat/stream
 * 请求体：{ message: string; sessionId?: string }
 * 响应：text/event-stream，每帧形如 `data:{"type":"delta","text":"你"}\n\n`
 *
 * <h3>为什么用 fetch + ReadableStream，而不是 EventSource</h3>
 * EventSource 只支持 GET、且不能自定义请求头；
 * 我们的接口是 POST（要带消息体）并且需要 Authorization: Bearer，
 * 所以手动读 body 流，自己按 SSE 规范分帧。
 *
 * @param onEvent 每解析出一帧就回调一次，调用方据此驱动 UI
 * @param signal  可选中断信号（用户点「停止」时取消）
 * @returns 最终 sessionId 与累积正文
 */
export async function streamAgentChat(
  message: string,
  sessionId: string | undefined,
  onEvent: (event: AgentStreamEvent) => void,
  signal?: AbortSignal,
): Promise<{ sessionId: string; text: string }> {
  let token: string | null = null;
  if (typeof window !== "undefined") {
    token = localStorage.getItem("token");
  }

  const response = await fetch(`${API_BASE}/api/v1/agent/chat/stream`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ message, sessionId }),
    signal,
  });

  if (isAuthFailure(response.status)) {
    requireLogin();
  }
  if (!response.ok) {
    // 流式接口不套 ApiResponse 信封，出错时可能直接返回文本，尽力取一下
    const detail = await response.text().catch(() => "");
    throw new Error(detail.trim() || `请求失败 (HTTP ${response.status})`);
  }
  if (!response.body) {
    throw new Error("当前浏览器不支持流式响应");
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8");

  let buffer = "";
  /** SSE 一帧可以有多行 data:，按规范用 \n 拼接 */
  let dataLines: string[] = [];
  let text = "";
  let resolvedSessionId = sessionId ?? "";
  let serverError: string | null = null;

  /** 空行代表一帧结束，此时才解析 */
  function dispatch() {
    if (dataLines.length === 0) return;
    const payload = dataLines.join("\n");
    dataLines = [];

    if (!payload || payload === "[DONE]") return;

    let event: AgentStreamEvent;
    try {
      event = JSON.parse(payload) as AgentStreamEvent;
    } catch {
      // 兜底：不是 JSON 就当成纯文本增量，异常帧也不至于丢内容
      event = { type: "delta", text: payload };
    }

    if (event.type === "delta") {
      // 空串通常是保活心跳，不要塞进正文
      if (!event.text) return;
      text += event.text;
    } else if (event.type === "start" || event.type === "done") {
      if (event.sessionId) resolvedSessionId = event.sessionId;
    } else if (event.type === "error") {
      serverError = event.message || "对话失败";
    }

    onEvent(event);
  }

  while (true) {
    const { done, value } = await reader.read();
    buffer += decoder.decode(value ?? new Uint8Array(), { stream: !done });

    const lines = buffer.split(/\r?\n/);
    // 最后一段可能被网络截断，留到下一轮拼接
    buffer = lines.pop() ?? "";

    for (const line of lines) {
      if (line === "") {
        dispatch();
      } else if (line.startsWith(":")) {
        continue; // SSE 注释行，常用于心跳
      } else if (line.startsWith("data:")) {
        // 规范要求去掉 "data:" 后的一个空格
        dataLines.push(line.slice(5).replace(/^ /, ""));
      }
    }

    if (done) break;
  }

  // 后端若没补最后一个空行，这里兜底收尾
  dispatch();

  if (serverError) throw new Error(serverError);
  return { sessionId: resolvedSessionId, text };
}

/** 网关对「未登录」和「token 失效」都返回 401/403，二者都按登录失效处理 */
function isAuthFailure(status: number): boolean {
  return status === 401 || status === 403;
}

/** 清理登录态、跳登录页，并中断当前调用链 */
function requireLogin(): never {
  if (typeof window !== "undefined" && !window.location.pathname.startsWith("/login")) {
    clearAuth();
    window.location.href = "/login";
  }
  throw new Error("登录已过期，请重新登录");
}

/**
 * 对话入口的快捷指令。
 *
 * 刻意覆盖两类意图，便于演示 Agent 的两种能力：
 * 1) 需要先搜岗位再追问（多步编排）
 * 2) 直接给定 ID 算匹配（工具调用）
 */
export const AGENT_QUICK_PROMPTS = [
  "帮我找字节的后端岗位",
  "有哪些适合我的 Java 岗位？",
  "这个岗位需要什么技术栈？",
  "帮我看看简历和岗位的匹配度",
];
