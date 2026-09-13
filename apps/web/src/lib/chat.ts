import { API_BASE } from "./api";

export type ChatRole = "user" | "assistant";

export interface ChatMessage {
  role: ChatRole;
  content: string;
}

/**
 * AI 对话式创建简历（SSE 流式）。
 *
 * 后端契约：POST /api/v1/chat/resume/stream
 * 请求体：{ messages: [{role, content}], targetJob?: string }
 * 响应：text/event-stream，每行 data: {"delta":"..."}
 */
export async function streamResumeChat(
  messages: ChatMessage[],
  onDelta: (delta: string) => void,
  targetJob?: string,
  templateId?: string,
): Promise<string> {
  let token: string | null = null;
  if (typeof window !== "undefined") {
    token = localStorage.getItem("token");
  }

  const response = await fetch(`${API_BASE}/api/v1/chat/resume/stream`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({
      messages,
      targetJob: targetJob?.trim() || undefined,
      templateId: templateId || undefined,
    }),
  });

  if (response.status === 401) {
    if (typeof window !== "undefined" && !window.location.pathname.startsWith("/login")) {
      localStorage.removeItem("token");
      window.location.href = "/login";
    }
    throw new Error("登录已过期，请重新登录");
  }
  if (!response.ok) {
    const json = await response.json().catch(() => null);
    throw new Error(json?.message || `请求失败 (HTTP ${response.status})`);
  }
  if (!response.body) {
    throw new Error("浏览器不支持流式响应");
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";
  let fullText = "";

  while (true) {
    const { done, value } = await reader.read();
    buffer += decoder.decode(value ?? new Uint8Array(), { stream: !done });

    const lines = buffer.split(/\r?\n/);
    buffer = lines.pop() ?? "";

    for (const rawLine of lines) {
      const line = rawLine.trim();
      if (!line.startsWith("data:")) continue;

      const payload = line.slice(5).trim();
      if (!payload) continue;

      if (payload === "[DONE]") break;

      // 后端约定事件体为 JSON；解析失败则按纯文本兜底
      let delta = payload;
      try {
        const parsed = JSON.parse(payload);
        if (typeof parsed?.delta === "string") {
          delta = parsed.delta;
        } else if (parsed?.error) {
          throw new Error(parsed.error);
        }
      } catch (e) {
        if (e instanceof Error && e.message !== payload) throw e;
      }

      fullText += delta;
      onDelta(delta);
    }
  }

  return fullText;
}

/** 从 AI 回复中提取 [BUILD] 标记后的 Markdown（没标记返回 null） */
export function extractBuiltResume(text: string): string | null {
  const marker = "[BUILD]";
  const index = text.indexOf(marker);
  if (index < 0) return null;
  const rest = text.slice(index + marker.length).trim();
  return rest.length > 0 ? rest : null;
}
