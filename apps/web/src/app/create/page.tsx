"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { api } from "@/lib/api";
import { extractBuiltResume, streamResumeChat, type ChatMessage } from "@/lib/chat";
import {
  DEFAULT_TEMPLATE_ID,
  RESUME_TEMPLATES,
  buildTemplateInstruction,
  getTemplate,
} from "@/lib/resumeTemplates";
import type { Resume } from "@/lib/types";

const GREETING: ChatMessage = {
  role: "assistant",
  content:
    "你好，我是你的简历助手 \n\n我们先从基本信息开始：你的姓名、电话 / 邮箱、学校、专业和毕业年份是？你也可以一次性把信息都发给我，例如：“我叫张三，北京邮电大学软件工程本科，2027 届……”\n\n我会一个问题一个问题收集，信息足够时自动为你生成完整简历。",
};

const CHECKLIST = [
  "基本信息：姓名 / 电话 / 邮箱 / 毕业年份",
  "教育背景：学校 / 专业 / 学历 / 时间",
  "专业技能：语言 / 框架 / 工具",
  "项目经历：项目名 / 时间 / 职责 / 技术栈",
  "补充经历：实习 / 竞赛 / 校园经历（可选）",
];

const QUICK_ACTIONS = [
  "我是2027届软件工程本科生",
  "我掌握 Java 和 Spring Boot",
  "我有校园项目经历",
  "信息够了，直接生成简历",
];

export default function CreateResumePage() {
  const router = useRouter();
  const [messages, setMessages] = useState<ChatMessage[]>([GREETING]);
  const [input, setInput] = useState("");
  const [targetJob, setTargetJob] = useState("");
  const [templateId, setTemplateId] = useState(DEFAULT_TEMPLATE_ID);
  const [streaming, setStreaming] = useState(false);
  const [saving, setSaving] = useState(false);
  const [built, setBuilt] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [messages, built]);

  async function handleSend(text?: string) {
    const content = (text ?? input).trim();
    if (!content || streaming) return;

    const history: ChatMessage[] = [...messages, { role: "user", content }];
    setMessages(history);
    setInput("");
    setError(null);
    setBuilt(null);
    setStreaming(true);

    let assistantText = "";
    try {
      // 过渡兜底：把模板结构约束附加到最后一条 user 消息的发送副本（界面不展示），
      // 后端支持 templateId 后由服务端 Prompt 注入，两者一致、不冲突。
      const apiHistory: ChatMessage[] = history.map((message, index) =>
        index === history.length - 1
          ? { ...message, content: `${message.content}\n${buildTemplateInstruction(templateId)}` }
          : message,
      );
      const full = await streamResumeChat(
        apiHistory,
        (delta) => {
          assistantText += delta;
          setMessages([...history, { role: "assistant", content: assistantText }]);
        },
        targetJob || undefined,
        templateId,
      );
      assistantText = full;

      const finalMessages: ChatMessage[] = [
        ...history,
        { role: "assistant", content: assistantText },
      ];
      setMessages(finalMessages);

      const markdown = extractBuiltResume(assistantText);
      if (markdown) setBuilt(markdown);
    } catch (e) {
      const message = e instanceof Error ? e.message : "对话失败";
      setError(message);
      if (assistantText) {
        setMessages([
          ...history,
          { role: "assistant", content: `${assistantText}\n\n${message}` },
        ]);
      }
    } finally {
      setStreaming(false);
    }
  }

  async function handleSave() {
    if (!built || saving) return;
    setSaving(true);
    setError(null);
    try {
      const stamp = new Date().toISOString().slice(0, 19).replace(/[:T]/g, "-");
      const saved = await api<Resume>("/api/v1/resume/from-text", {
        method: "POST",
        body: JSON.stringify({
          fileName: `AI对话创建-${stamp}.md`,
          rawText: built,
        }),
      });
      router.push(`/resume/${saved.id}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : "保存失败");
      setSaving(false);
    }
  }

  function resetChat() {
    if (streaming) return;
    setMessages([GREETING]);
    setBuilt(null);
    setError(null);
  }

  const hasBuilt = !!built;

  return (
    <main className="mx-auto max-w-6xl p-6">
      <header className="mb-5 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-zinc-900">AI 对话创建简历</h1>
          <p className="mt-1 text-sm text-zinc-500">
            用对话回答几个问题，AI 自动生成可继续诊断 / 优化的 Markdown 简历
          </p>
        </div>
        <Link
          href="/resumes"
          className="rounded-lg bg-zinc-100 px-3 py-1.5 text-sm text-zinc-600 hover:bg-zinc-200"
        >
          返回简历列表
        </Link>
      </header>

      {error && (
        <div className="mb-4 rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-600">
          {error}
        </div>
      )}

      <div className="grid gap-5 lg:grid-cols-[290px_1fr]">
        {/* 左侧引导 */}
        <aside className="space-y-4">
          <div className="rounded-2xl border border-zinc-200 bg-white p-4 shadow-card">
            <p className="text-sm font-semibold text-zinc-800">目标岗位（可选）</p>
            <input
              value={targetJob}
              onChange={(e) => setTargetJob(e.target.value)}
              placeholder="如：Java 后端开发工程师"
              disabled={streaming}
              className="mt-2 w-full rounded-lg border border-zinc-300 px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-brand-500/25 focus-visible:ring-offset-1 focus:border-zinc-900"
            />
            <p className="mt-1.5 text-xs text-zinc-400">填写后 AI 会针对岗位方向优化简历</p>
          </div>

          <div className="rounded-2xl border border-zinc-200 bg-white p-4 shadow-card">
            <p className="text-sm font-semibold text-zinc-800">简历模板</p>
            <div className="mt-3 space-y-2">
              {RESUME_TEMPLATES.map((template) => {
                const active = template.id === templateId;
                return (
                  <button
                    key={template.id}
                    type="button"
                    onClick={() => setTemplateId(template.id)}
                    disabled={streaming}
                    aria-pressed={active}
                    className={`w-full rounded-xl border p-3 text-left transition disabled:opacity-60 ${
                      active
                        ? "border-zinc-900 bg-zinc-50"
                        : "border-zinc-200 hover:border-zinc-300"
                    }`}
                  >
                    <p className="text-sm font-medium text-zinc-900">{template.name}</p>
                    <p className="mt-0.5 text-xs text-zinc-500">{template.tagline}</p>
                    {active && (
                      <p className="mt-2 text-[11px] leading-relaxed text-zinc-400">
                        {template.sections.join(" / ")}
                      </p>
                    )}
                  </button>
                );
              })}
            </div>
            <p className="mt-3 rounded-lg bg-zinc-50 p-2.5 text-xs leading-relaxed text-zinc-400">
              生成时按所选模板的章节顺序输出，信息不足的章节会保留标题并标注待补充。
            </p>
          </div>

          <div className="rounded-2xl border border-zinc-200 bg-white p-4 shadow-card">
            <p className="text-sm font-semibold text-zinc-800">建议覆盖的信息</p>
            <ul className="mt-3 space-y-2">
              {CHECKLIST.map((item) => (
                <li key={item} className="flex gap-2 text-xs leading-relaxed text-zinc-500">
                  <span className="mt-0.5 text-emerald-500">✓</span>
                  <span>{item}</span>
                </li>
              ))}
            </ul>
            <p className="mt-3 rounded-lg bg-zinc-50 p-2.5 text-xs leading-relaxed text-zinc-400">
              不用一次答全，AI 会一步一步追问；觉得够了点“直接生成简历”也可以。
            </p>
          </div>

          {messages.length > 1 && (
            <button
              onClick={resetChat}
              disabled={streaming}
              className="w-full rounded-lg border border-zinc-200 py-2 text-xs text-zinc-500 hover:bg-zinc-50 disabled:opacity-50"
            >
              清空并重新开始
            </button>
          )}
        </aside>

        {/* 右侧对话区 */}
        <section className="overflow-hidden rounded-2xl border border-zinc-200 bg-white shadow-card">
          <div className="h-[560px] space-y-4 overflow-y-auto p-5">
            {messages.map((m, i) => (
              <MessageBubble key={i} message={m} streaming={streaming && i === messages.length - 1} />
            ))}
            {!hasBuilt && messages.length <= 1 && (
              <div className="flex flex-wrap gap-2 pt-1">
                {QUICK_ACTIONS.map((q) => (
                  <button
                    key={q}
                    onClick={() => handleSend(q)}
                    disabled={streaming}
                    className="rounded-full border border-zinc-200 bg-white px-3 py-1.5 text-xs text-zinc-500 hover:border-brand-300 hover:text-zinc-900 disabled:opacity-50"
                  >
                    {q}
                  </button>
                ))}
              </div>
            )}
            <div ref={bottomRef} />
          </div>

          <div className="border-t border-zinc-100 p-3">
            {hasBuilt ? (
              <div className="flex items-center gap-3">
                <span className="flex-1 text-sm text-emerald-600">
                  简历已生成，可保存后继续诊断 / 优化
                </span>
                <button
                  onClick={resetChat}
                  className="rounded-lg border border-zinc-200 px-4 py-2 text-sm text-zinc-500 hover:bg-zinc-50"
                >
                  新建对话
                </button>
                <button
                  onClick={handleSave}
                  disabled={saving}
                  className="rounded-lg bg-emerald-600 px-5 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
                >
                  {saving ? "保存中…" : "保存为简历"}
                </button>
              </div>
            ) : (
              <div className="flex items-end gap-2">
                <textarea
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" && !e.shiftKey) {
                      e.preventDefault();
                      handleSend();
                    }
                  }}
                  rows={2}
                  placeholder={streaming ? "AI 正在回复…" : "直接告诉 AI 你的信息…"}
                  disabled={streaming}
                  className="min-h-[52px] flex-1 resize-none rounded-xl border border-zinc-300 px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-brand-500/25 focus-visible:ring-offset-1 focus:border-zinc-900 disabled:bg-zinc-50"
                />
                <button
                  onClick={() => handleSend()}
                  disabled={streaming || !input.trim()}
                  className="rounded-xl bg-zinc-900 px-5 py-2.5 text-sm font-medium text-white hover:bg-zinc-800 disabled:opacity-50"
                >
                  {streaming ? "生成中…" : "发送"}
                </button>
              </div>
            )}
          </div>
        </section>
      </div>

      {/* 生成的 Markdown 预览 */}
      {built && (
        <div className="mt-5 rounded-2xl border border-emerald-200 bg-white p-5 shadow-card">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-sm font-semibold text-zinc-800">
              生成的简历（Markdown 预览）
              <span className="ml-2 font-normal text-zinc-400">
                模板：{getTemplate(templateId).name}
              </span>
            </h2>
            <button
              onClick={() => setBuilt(null)}
              className="text-xs text-zinc-400 hover:text-zinc-600"
            >
              收起预览
            </button>
          </div>
          <pre className="max-h-96 overflow-auto whitespace-pre-wrap rounded-xl bg-zinc-50 p-4 text-xs leading-relaxed text-zinc-700">
            {built}
          </pre>
        </div>
      )}
    </main>
  );
}

function MessageBubble({
  message,
  streaming,
}: {
  message: ChatMessage;
  streaming: boolean;
}) {
  const isUser = message.role === "user";
  return (
    <div className={`flex ${isUser ? "justify-end" : "justify-start"}`}>
      <div
        className={`max-w-[82%] whitespace-pre-wrap rounded-2xl px-4 py-2.5 text-sm leading-relaxed shadow-card ${
          isUser
            ? "bg-zinc-900 text-white"
            : "border border-zinc-200 bg-white text-zinc-700"
        }`}
      >
        {message.content}
        {streaming && <span className="ml-0.5 animate-pulse">▍</span>}
      </div>
    </div>
  );
}
