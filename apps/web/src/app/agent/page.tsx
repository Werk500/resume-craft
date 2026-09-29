"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import {
  AGENT_QUICK_PROMPTS,
  agentToolLabel,
  clearAgentSession,
  fetchAgentSessionMessages,
  loadAgentSessions,
  removeAgentSession,
  renameAgentSession,
  streamAgentChat,
  touchAgentSession,
} from "@/lib/agent";
import type { AgentSession, AgentStreamEvent } from "@/lib/agent";
import MarkdownLite from "@/components/MarkdownLite";

interface Message {
  role: "user" | "assistant";
  content: string;
  /** 该条回复是否由 Agent 调用工具产出（用于展示标记） */
  usedTools?: boolean;
}

const GREETING =
  "我是求职助手，可以帮你做这些事：\n\n" +
  "- **找岗位**：告诉我公司或技术方向，我从岗位库里搜\n" +
  "- **算匹配度**：给我简历 ID 和岗位，我调用匹配引擎算分并解释差距\n" +
  "- **多轮追问**：接着问「那杭州的呢」，我记得我们聊到哪\n\n" +
  "同一个会话我会记住上下文。**所有分数都来自匹配引擎，我不会编造。**\n\n" +
  "先说说你想找什么样的岗位？";

export default function AgentPage() {
  const [messages, setMessages] = useState<Message[]>([
    { role: "assistant", content: GREETING },
  ]);
  const [input, setInput] = useState("");
  const [sessionId, setSessionId] = useState<string | undefined>(undefined);
  const [pending, setPending] = useState(false);
  /** 后端正在执行的工具名；null 表示还没进入工具阶段 */
  const [activeTool, setActiveTool] = useState<string | null>(null);
  /** 已收到的正文增量，边收边渲染（打字机） */
  const [streamingText, setStreamingText] = useState("");
  const [error, setError] = useState<string | null>(null);
  /** 历史会话列表（标题/时间存 localStorage，消息本体在后端 Redis） */
  const [sessions, setSessions] = useState<AgentSession[]>([]);
  /** 正在重命名的会话 id */
  const [editingId, setEditingId] = useState<string | null>(null);
  /** 正在等待删除确认的会话 id */
  const [confirmId, setConfirmId] = useState<string | null>(null);
  const [loadingHistory, setLoadingHistory] = useState(false);
  /** 历史接口不可用（或记忆已过期）时的提示 */
  const [historyMissing, setHistoryMissing] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);
  /** 正文累加器：用 state 累加会在闭包里读到旧值 */
  const textRef = useRef("");
  /** 本轮是否真的调用过工具，用于给最终消息打标 */
  const usedToolsRef = useRef(false);
  /** 当前请求的中断句柄 */
  const abortRef = useRef<AbortController | null>(null);
  /** 本轮用户问的第一句话，用来给新会话起标题 */
  const firstMessageRef = useRef("");

  // 首次进入恢复最近一次会话：消息在后端 Redis 里，按需拉取
  useEffect(() => {
    const list = loadAgentSessions();
    setSessions(list);
    if (list.length > 0) {
      void openSession(list[0].sessionId);
    }
    // 只在挂载时执行一次
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [messages, pending, streamingText, activeTool]);

  /** 把已收到的正文落成一条正式消息 */
  function commitAssistant(raw: string) {
    const content = raw.trim();
    if (content) {
      setMessages((prev) => [
        ...prev,
        { role: "assistant", content, usedTools: usedToolsRef.current },
      ]);
    }
    textRef.current = "";
    setStreamingText("");
  }

  /**
   * 处理一帧 SSE 事件。
   * 与后端 AgentStreamEvent 契约一一对应：
   * start → 记住 sessionId；tool → 展示真实工具名；
   * delta → 追加正文；error/done 由 promise 的拒绝/完成表达。
   */
  function handleStreamEvent(event: AgentStreamEvent) {
    if (event.type === "start") {
      if (event.sessionId) {
        setSessionId(event.sessionId);
        // 新会话在第一轮 start 帧拿到 memoryId，此时落到本地列表
        setSessions(touchAgentSession(event.sessionId, firstMessageRef.current));
      }
    } else if (event.type === "tool") {
      // 后端已在「第一个工具执行前」把工具轮的 token 拦掉了（那是模型决定调
      // 什么工具时的自言自语，不是给用户看的答案）。这里再清一次是兜底：
      // 多次工具调用的场景下，第二轮的前言仍可能先于第二个 tool 帧到达，
      // 不清掉就会和最终答案粘成一句话。
      textRef.current = "";
      setStreamingText("");
      usedToolsRef.current = true;
      setActiveTool(event.name);
    } else if (event.type === "delta") {
      textRef.current += event.text;
      setStreamingText(textRef.current);
    }
  }

  async function handleSend(text?: string) {
    const content = (text ?? input).trim();
    if (!content || pending) return;

    setMessages((prev) => [...prev, { role: "user", content }]);
    firstMessageRef.current = content;
    setInput("");
    setError(null);
    setPending(true);
    setActiveTool(null);
    setStreamingText("");
    textRef.current = "";
    usedToolsRef.current = false;

    const controller = new AbortController();
    abortRef.current = controller;

    try {
      const result = await streamAgentChat(content, sessionId, handleStreamEvent, controller.signal);
      if (result.sessionId) setSessionId(result.sessionId);
      commitAssistant(result.text || textRef.current);
      if (!result.text.trim()) {
        setError("Agent 没有返回内容，请再问一次");
      }
    } catch (e) {
      // 用户主动停止：保留已生成的部分，不当作错误
      const aborted = e instanceof DOMException && e.name === "AbortError";
      commitAssistant(textRef.current);
      if (!aborted) {
        setError(e instanceof Error ? e.message : "对话失败");
      }
    } finally {
      abortRef.current = null;
      setActiveTool(null);
      setStreamingText("");
      textRef.current = "";
      setPending(false);
    }
  }

  function handleStop() {
    abortRef.current?.abort();
  }

  /** 开新会话：旧会话保留在列表里（记忆还在 Redis），只是从这里开始一段新的 */
  function newSession() {
    if (pending) return;
    setSessionId(undefined);
    setMessages([{ role: "assistant", content: GREETING }]);
    setError(null);
    setStreamingText("");
    setHistoryMissing(false);
    textRef.current = "";
  }

  /** 切换到某个历史会话：消息从后端拉，前端不存副本 */
  async function openSession(id: string) {
    if (pending || id === sessionId) return;
    setSessionId(id);
    setEditingId(null);
    setConfirmId(null);
    setError(null);
    setStreamingText("");
    setHistoryMissing(false);
    textRef.current = "";
    setLoadingHistory(true);
    try {
      const history = await fetchAgentSessionMessages(id);
      setMessages(history.length > 0 ? history : [{ role: "assistant", content: GREETING }]);
    } catch {
      // 历史接口不可用或记忆已过期：不把用户卡住，提示一下即可（继续提问仍会接上后端上下文）
      setHistoryMissing(true);
      setMessages([{ role: "assistant", content: GREETING }]);
    } finally {
      setLoadingHistory(false);
    }
  }

  function saveTitle(id: string, title: string) {
    setSessions(renameAgentSession(id, title));
    setEditingId(null);
  }

  /** 删除会话：本地列表与后端记忆一起清掉 */
  function deleteSession(id: string) {
    if (pending) return;
    setSessions(removeAgentSession(id));
    setConfirmId(null);
    void clearAgentSession(id).catch(() => {});
    if (id === sessionId) newSession();
  }

  const idle = messages.length <= 1;

  return (
    <main className="mx-auto max-w-6xl p-6">
      <header className="mb-5 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-zinc-900">求职助手</h1>
          <p className="mt-1 max-w-[62ch] text-sm leading-relaxed text-zinc-500">
            用自然语言描述需求，Agent 会自主调用岗位搜索与匹配引擎，并把结果解释给你
          </p>
        </div>
        <button
          onClick={newSession}
          disabled={pending}
          className="rounded-lg border border-zinc-200 px-3 py-1.5 text-sm text-zinc-600 transition hover:bg-zinc-100 disabled:opacity-50"
        >
          新会话
        </button>
      </header>

      {error && (
        <div className="mb-4 rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-600">
          {error}
        </div>
      )}

      <div className="grid gap-5 lg:grid-cols-[280px_1fr]">
        {/* 左侧：历史会话 + 能力说明与边界 */}
        <aside className="space-y-4">
          <div className="rounded-2xl border border-zinc-200 bg-white p-4 shadow-card">
            <div className="flex items-center justify-between gap-2">
              <p className="text-sm font-semibold text-zinc-800">历史会话</p>
              <button
                onClick={newSession}
                disabled={pending}
                className="rounded-lg border border-zinc-200 px-2 py-0.5 text-[11px] text-zinc-500 transition hover:bg-zinc-100 disabled:opacity-50"
              >
                + 新建
              </button>
            </div>

            {sessions.length === 0 ? (
              <p className="mt-3 text-xs leading-relaxed text-zinc-400">
                还没有历史会话。提问后会自动记录，会话记忆保存在服务端（24 小时）。
              </p>
            ) : (
              <ul className="mt-3 space-y-1">
                {sessions.map((s) => {
                  const active = s.sessionId === sessionId;
                  return (
                    <li
                      key={s.sessionId}
                      className={
                        "rounded-lg px-2 py-1.5 transition " +
                        (active ? "bg-zinc-100 ring-1 ring-inset ring-zinc-200" : "hover:bg-zinc-50")
                      }
                    >
                      {editingId === s.sessionId ? (
                        <input
                          autoFocus
                          defaultValue={s.title}
                          aria-label="会话标题"
                          onBlur={(e) => saveTitle(s.sessionId, e.target.value)}
                          onKeyDown={(e) => {
                            const value = (e.target as HTMLInputElement).value;
                            if (e.key === "Enter") saveTitle(s.sessionId, value);
                            if (e.key === "Escape") setEditingId(null);
                          }}
                          className="w-full rounded border border-zinc-300 px-1.5 py-1 text-xs outline-none focus:border-zinc-900"
                        />
                      ) : (
                        <div className="flex items-center gap-1">
                          <button
                            onClick={() => void openSession(s.sessionId)}
                            title={s.title}
                            className="min-w-0 flex-1 text-left"
                          >
                            <span
                              className={
                                "block truncate text-xs " +
                                (active ? "font-medium text-zinc-900" : "text-zinc-600")
                              }
                            >
                              {s.title}
                            </span>
                            <span className="mt-0.5 block text-[10px] text-zinc-400">
                              {formatSessionTime(s.updatedAt)}
                            </span>
                          </button>

                          {confirmId === s.sessionId ? (
                            <span className="flex shrink-0 items-center gap-1">
                              <button
                                onClick={() => deleteSession(s.sessionId)}
                                className="rounded bg-red-50 px-1.5 py-0.5 text-[10px] text-red-600 ring-1 ring-inset ring-red-200"
                              >
                                删除
                              </button>
                              <button
                                onClick={() => setConfirmId(null)}
                                className="rounded px-1 py-0.5 text-[10px] text-zinc-500 hover:bg-zinc-100"
                              >
                                取消
                              </button>
                            </span>
                          ) : (
                            <span className="flex shrink-0 items-center">
                              <button
                                onClick={() => setEditingId(s.sessionId)}
                                aria-label="重命名"
                                className="rounded px-1 text-[11px] text-zinc-300 transition hover:bg-zinc-100 hover:text-zinc-600"
                              >
                                ✎
                              </button>
                              <button
                                onClick={() => setConfirmId(s.sessionId)}
                                aria-label="删除会话"
                                className="rounded px-1 text-[11px] text-zinc-300 transition hover:bg-zinc-100 hover:text-red-500"
                              >
                                ✕
                              </button>
                            </span>
                          )}
                        </div>
                      )}
                    </li>
                  );
                })}
              </ul>
            )}
          </div>

          <div className="rounded-2xl border border-zinc-200 bg-white p-4 shadow-card">
            <p className="text-sm font-semibold text-zinc-800">它能做什么</p>
            <ul className="mt-3 space-y-2 text-xs leading-relaxed text-zinc-500">
              <li className="flex gap-2">
                <span className="shrink-0 text-zinc-400">•</span>
                <span>按公司 / 技术方向搜索岗位</span>
              </li>
              <li className="flex gap-2">
                <span className="shrink-0 text-zinc-400">•</span>
                <span>调用匹配引擎算分，并解释缺失的关键词</span>
              </li>
              <li className="flex gap-2">
                <span className="shrink-0 text-zinc-400">•</span>
                <span>结合上下文回答追问</span>
              </li>
            </ul>
          </div>

          <div className="rounded-2xl border border-amber-200 bg-amber-50/60 p-4">
            <p className="text-sm font-semibold text-amber-800">能力边界</p>
            <p className="mt-2 text-xs leading-relaxed text-amber-700">
              Agent 只做<strong className="font-semibold">编排与解释</strong>，
              匹配分由可解释的规则引擎算出，不经过大模型。
              简历内容的实际修改需要到
              <Link href="/resumes" className="mx-1 underline hover:text-amber-900">
                简历
              </Link>
              或
              <Link href="/match" className="mx-1 underline hover:text-amber-900">
                匹配
              </Link>
              页面操作。
            </p>
          </div>

          <div className="rounded-2xl border border-zinc-200 bg-white p-4 shadow-card">
            <p className="text-sm font-semibold text-zinc-800">提示</p>
            <p className="mt-2 text-xs leading-relaxed text-zinc-500">
              算匹配度时把简历 ID 一起告诉它，例如
              <span className="mt-1 block rounded bg-zinc-100 px-2 py-1 font-mono text-[11px] text-zinc-700">
                我的简历 ID 是 42，帮我看看字节那个岗位
              </span>
            </p>
          </div>
        </aside>

        {/* 右侧：对话区 */}
        <section className="flex flex-col overflow-hidden rounded-2xl border border-zinc-200 bg-white shadow-card">
          <div className="h-[560px] space-y-4 overflow-y-auto p-5">
            {historyMissing && (
              <div className="rounded-xl border border-amber-200 bg-amber-50/70 px-3 py-2 text-xs leading-relaxed text-amber-700">
                历史消息暂时读不到（会话记忆可能已过期）。继续提问仍会接着服务端的上下文。
              </div>
            )}

            {messages.map((m, i) => (
              <MessageBubble key={i} message={m} />
            ))}

            {loadingHistory && (
              <div className="flex justify-start">
                <div className="rounded-2xl border border-zinc-200 bg-white px-4 py-2.5 text-sm text-zinc-500 shadow-card">
                  正在加载历史消息…
                </div>
              </div>
            )}

            {pending && (
              <div className="flex justify-start">
                {streamingText ? (
                  /* 阶段二：模型正在吐字，直接渲染正文 + 光标 */
                  <div className="max-w-[88%] min-w-0 rounded-2xl border border-zinc-200 bg-white px-4 py-3 shadow-card">
                    {activeTool && <ToolBadge />}
                    <MarkdownLite text={streamingText} />
                    <span
                      aria-hidden
                      className="ml-0.5 inline-block h-4 w-[2px] translate-y-[3px] animate-pulse rounded-full bg-zinc-400"
                    />
                  </div>
                ) : (
                  /* 阶段一：还没吐字，展示真实的后端工具阶段 */
                  <div className="flex items-center gap-2 rounded-2xl border border-zinc-200 bg-white px-4 py-2.5 text-sm text-zinc-500 shadow-card">
                    <span className="flex gap-1" aria-hidden>
                      <Dot delay="0ms" />
                      <Dot delay="160ms" />
                      <Dot delay="320ms" />
                    </span>
                    {activeTool ? agentToolLabel(activeTool) : "正在思考…"}
                  </div>
                )}
              </div>
            )}

            {idle && !pending && (
              <div className="flex flex-wrap gap-2 pt-1">
                {AGENT_QUICK_PROMPTS.map((p) => (
                  <button
                    key={p}
                    onClick={() => handleSend(p)}
                    className="rounded-full border border-zinc-200 bg-white px-3 py-1.5 text-xs text-zinc-600 transition hover:border-zinc-300 hover:bg-zinc-50"
                  >
                    {p}
                  </button>
                ))}
              </div>
            )}

            <div ref={bottomRef} />
          </div>

          <div className="border-t border-zinc-200 p-4">
            <div className="flex items-end gap-2">
              <textarea
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={(e) => {
                  // Enter 发送，Shift+Enter 换行
                  if (e.key === "Enter" && !e.shiftKey) {
                    e.preventDefault();
                    handleSend();
                  }
                }}
                rows={2}
                placeholder="说说你想找什么样的岗位，或把简历 ID 告诉它…"
                aria-label="对话输入"
                className="min-h-[52px] flex-1 resize-none rounded-xl border border-zinc-300 px-3 py-2.5 text-sm outline-none transition focus:border-zinc-900 focus-visible:ring-2 focus-visible:ring-brand-500/25 focus-visible:ring-offset-1"
              />
              {pending ? (
                <button
                  onClick={handleStop}
                  className="shrink-0 rounded-xl border border-zinc-300 px-5 py-2.5 text-sm font-medium text-zinc-600 transition hover:bg-zinc-100"
                >
                  停止
                </button>
              ) : (
                <button
                  onClick={() => handleSend()}
                  disabled={!input.trim()}
                  className="shrink-0 rounded-xl bg-zinc-900 px-5 py-2.5 text-sm font-medium text-white transition hover:bg-zinc-800 disabled:opacity-50"
                >
                  发送
                </button>
              )}
            </div>
            <p className="mt-2 text-[11px] text-zinc-400">
              Enter 发送 · Shift + Enter 换行 · 回复流式生成，可随时停止
            </p>
          </div>
        </section>
      </div>
    </main>
  );
}

function MessageBubble({ message }: { message: Message }) {
  const isUser = message.role === "user";

  if (isUser) {
    return (
      <div className="flex justify-end">
        <div className="max-w-[82%] whitespace-pre-wrap rounded-2xl bg-zinc-900 px-4 py-2.5 text-sm leading-relaxed text-white shadow-card">
          {message.content}
        </div>
      </div>
    );
  }

  return (
    <div className="flex justify-start">
      <div className="max-w-[88%] min-w-0 rounded-2xl border border-zinc-200 bg-white px-4 py-3 shadow-card">
        {message.usedTools && <ToolBadge />}
        <MarkdownLite text={message.content} />
      </div>
    </div>
  );
}

/** 「已调用工具」标记：流式过程中和落成正式消息后都会出现 */
function ToolBadge() {
  return (
    <span className="mb-2 inline-flex items-center gap-1 rounded-full bg-brand-50 px-2 py-0.5 text-[11px] font-medium text-brand-700 ring-1 ring-inset ring-brand-200">
      <span aria-hidden className="text-[8px] leading-none">
        ◆
      </span>
      已调用工具
    </span>
  );
}

function Dot({ delay }: { delay: string }) {
  return (
    <span
      className="h-1.5 w-1.5 animate-bounce rounded-full bg-zinc-400"
      style={{ animationDelay: delay }}
    />
  );
}

/** 会话列表里的短时间，如 09-29 16:20 —— 够用且不占宽度 */
function formatSessionTime(ts: number): string {
  const d = new Date(ts);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
