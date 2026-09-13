"use client";

import { useRef, useState } from "react";
import { api } from "@/lib/api";
import type { ResumeVersion } from "@/lib/types";

const FOCUS_OPTIONS = [
  { key: "DATA", label: "侧重数据成果", desc: "突出量化数据与可衡量成果" },
  { key: "METHOD", label: "侧重过程方法", desc: "突出技术方案与实施过程" },
  { key: "IMPACT", label: "侧重项目影响力", desc: "突出业务价值与影响" },
];

/**
 * 逐句精修：选中简历原文段落 → AI 按方向改写 → 采用并保存为新版本
 */
export default function SegmentRewriter({
  resumeId,
  initialText,
}: {
  resumeId: string;
  initialText: string;
}) {
  const [text, setText] = useState(initialText); // 当前展示文本（替换后更新）
  const [selected, setSelected] = useState("");
  const [rewriting, setRewriting] = useState(false);
  const [result, setResult] = useState<string | null>(null);
  const [activeFocus, setActiveFocus] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [msg, setMsg] = useState<{ type: "ok" | "err"; text: string } | null>(null);
  const preRef = useRef<HTMLPreElement>(null);

  /** 捕获选中文本 */
  function captureSelection() {
    const sel = window.getSelection();
    const s = sel ? sel.toString().trim() : "";
    if (s && s.length > 5) {
      setSelected(s);
      setResult(null);
      setMsg(null);
    }
  }

  async function handleRewrite(focusKey: string) {
    if (!selected) return;
    setRewriting(true);
    setResult(null);
    setActiveFocus(focusKey);
    setMsg(null);
    try {
      const rw = await api<string>("/api/v1/optimize/rewrite", {
        method: "POST",
        body: JSON.stringify({ original: selected, focus: focusKey }),
      });
      setResult(rw);
    } catch (e) {
      setMsg({ type: "err", text: e instanceof Error ? e.message : "改写失败" });
    } finally {
      setRewriting(false);
    }
  }

  /** 采用改写：替换原文段 → 保存为新版本 */
  async function handleAdopt() {
    if (!result || !selected) return;
    setSaving(true);
    setMsg(null);
    try {
      const newText = text.replace(selected, result);
      const ver = await api<ResumeVersion>(`/api/v1/optimize/${resumeId}/save`, {
        method: "POST",
        body: JSON.stringify({ content: newText, versionName: `逐句精修-${activeFocus ?? ""}` }),
      });
      setText(newText);
      setSelected("");
      setResult(null);
      setMsg({ type: "ok", text: `已保存为新版本（v${ver.id}），原文已替换显示` });
    } catch (e) {
      setMsg({ type: "err", text: e instanceof Error ? e.message : "保存失败" });
    } finally {
      setSaving(false);
    }
  }

  return (
    <div>
      {/* 原文（可选中） */}
      <pre
        ref={preRef}
        onMouseUp={captureSelection}
        className="mt-2 max-h-96 cursor-text select-text overflow-y-auto whitespace-pre-wrap rounded-xl bg-zinc-50 p-4 text-xs leading-relaxed text-zinc-600"
      >
        {text}
      </pre>

      {/* 选中提示 + 精修入口 */}
      {selected && !result && (
        <div className="mt-3 rounded-xl border border-zinc-200 bg-zinc-100 p-3">
          <div className="flex items-center justify-between">
            <p className="text-xs text-zinc-900">
              已选中 {selected.length} 字：<span className="line-clamp-1">{selected}</span>
            </p>
            <button onClick={() => setSelected("")} className="shrink-0 text-xs text-zinc-400 hover:text-zinc-900">
              清除
            </button>
          </div>
          <div className="mt-2 flex flex-wrap gap-2">
            {FOCUS_OPTIONS.map((f) => (
              <button
                key={f.key}
                onClick={() => handleRewrite(f.key)}
                disabled={rewriting}
                className="rounded-lg bg-white px-3 py-1.5 text-xs text-zinc-600 shadow-card hover:bg-zinc-200 disabled:opacity-50"
                title={f.desc}
              >
                {f.label}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* 改写结果 */}
      {result && (
        <div className="mt-3 rounded-xl border border-green-200 bg-green-50 p-3">
          <div className="flex items-center justify-between">
            <p className="text-xs font-medium text-green-700">AI 改写结果</p>
            <button onClick={() => setResult(null)} className="text-xs text-green-500 hover:text-green-700">
              ✕ 放弃
            </button>
          </div>
          <p className="mt-2 whitespace-pre-wrap text-xs leading-relaxed text-zinc-700">{result}</p>
          <div className="mt-3 flex gap-2">
            <button
              onClick={handleAdopt}
              disabled={saving}
              className="rounded-lg bg-green-600 px-4 py-1.5 text-xs font-medium text-white hover:bg-green-700 disabled:opacity-50"
            >
              {saving ? "保存中…" : "✓ 采用并保存为新版本"}
            </button>
            <button
              onClick={() => activeFocus && handleRewrite(activeFocus)}
              disabled={rewriting}
              className="rounded-lg border border-green-300 px-3 py-1.5 text-xs text-green-700 hover:bg-green-100 disabled:opacity-50"
            >
              {rewriting ? "改写中…" : "↻ 重新改写"}
            </button>
          </div>
        </div>
      )}

      {/* 状态消息 */}
      {msg && (
        <div
          className={`mt-3 rounded-lg px-3 py-2 text-xs ${
            msg.type === "ok" ? "bg-green-50 text-green-700" : "bg-red-50 text-red-600"
          }`}
        >
          {msg.text}
        </div>
      )}

      {rewriting && !result && (
        <p className="mt-2 animate-pulse text-xs text-zinc-400">AI 改写中，约 5~15 秒…</p>
      )}
    </div>
  );
}
