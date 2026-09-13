"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { api } from "@/lib/api";
import type { OcrBlock, Resume } from "@/lib/types";

interface EditableBlock {
  text: string;
  confidence: number | null;
  reason: string | null;
  confirmed: boolean;
}

export default function OcrReviewPage({ params }: { params: { id: string } }) {
  const resumeId = params.id;
  const router = useRouter();

  const [resume, setResume] = useState<Resume | null>(null);
  const [blocks, setBlocks] = useState<EditableBlock[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<Resume>(`/api/v1/resume/${resumeId}`)
      .then((r) => {
        setResume(r);
        setBlocks(toEditableBlocks(r));
      })
      .catch((e) => setError(e instanceof Error ? e.message : "加载失败"))
      .finally(() => setLoading(false));
  }, [resumeId]);

  function updateBlockText(index: number, text: string) {
    setBlocks((prev) => prev.map((b, i) => (i === index ? { ...b, text } : b)));
  }

  function toggleConfirmed(index: number) {
    setBlocks((prev) => prev.map((b, i) => (i === index ? { ...b, confirmed: !b.confirmed } : b)));
  }

  function confirmAll() {
    setBlocks((prev) => prev.map((b) => ({ ...b, confirmed: true })));
  }

  async function handleSubmit() {
    if (saving) return;
    const rawText = blocks
      .map((b) => b.text.trim())
      .filter((t) => t.length > 0)
      .join("\n\n");
    if (!rawText) {
      setError("确认后的内容不能为空，请补充识别文本");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await api<Resume>(`/api/v1/resume/${resumeId}/text`, {
        method: "PUT",
        body: JSON.stringify({ rawText }),
      });
      router.push(`/resume/${resumeId}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : "保存失败");
      setSaving(false);
    }
  }

  const allConfirmed = blocks.length > 0 && blocks.every((b) => b.confirmed);
  const isReview = resume?.ocrStatus === "REVIEW";

  return (
    <main className="mx-auto max-w-4xl p-6">
      <div className="mb-4 flex items-center justify-between">
        <Link href={`/resume/${resumeId}`} className="text-sm text-zinc-900 hover:underline">
          ← 返回简历详情
        </Link>
        <h1 className="text-xl font-bold text-zinc-900">图片识别内容核对</h1>
        <span className="w-24" />
      </div>

      {error && (
        <div className="mb-4 rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-600">
          {error}
        </div>
      )}

      {loading && <p className="py-10 text-center text-zinc-400">加载中…</p>}

      {!loading && resume && !isReview && (
        <div className="rounded-2xl border border-green-200 bg-green-50 p-8 text-center">
          <p className="text-lg font-medium text-green-700">该简历无需人工核对</p>
          <p className="mt-1 text-sm text-green-600">
            识别置信度正常（{formatConfidence(resume.ocrConfidence)}），可直接诊断与匹配
          </p>
          <Link
            href={`/resume/${resumeId}`}
            className="mt-4 inline-block rounded-lg bg-green-600 px-4 py-2 text-sm font-medium text-white hover:bg-green-700"
          >
            返回简历详情
          </Link>
        </div>
      )}

      {!loading && resume && isReview && (
        <>
          <div className="mb-4 rounded-2xl border border-amber-200 bg-amber-50 p-4">
            <p className="text-sm font-medium text-amber-700">
              识别置信度较低（{formatConfidence(resume.ocrConfidence)}）
            </p>
            <p className="mt-1 text-xs leading-relaxed text-amber-600">
              模糊扫描件不会参与自动评分。请逐块核对下面识别出的文字，修正错误后勾选「已核对」，
              全部确认后即可解锁 AI 诊断与岗位匹配。
            </p>
          </div>

          <div className="space-y-3">
            {blocks.map((block, index) => (
              <div
                key={index}
                className={`rounded-2xl border bg-white p-4 shadow-card ${
                  block.confirmed ? "border-green-300" : "border-zinc-200"
                }`}
              >
                <div className="mb-2 flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-medium text-zinc-400">第 {index + 1} 块</span>
                    <ConfidenceBadge confidence={block.confidence} />
                  </div>
                  <label className="flex cursor-pointer items-center gap-1.5 text-xs text-zinc-500">
                    <input
                      type="checkbox"
                      checked={block.confirmed}
                      onChange={() => toggleConfirmed(index)}
                      className="h-3.5 w-3.5"
                    />
                    已核对
                  </label>
                </div>
                {block.reason && (
                  <p className="mb-2 rounded-lg bg-zinc-50 px-2.5 py-1.5 text-xs text-zinc-400">
                    {block.reason}
                  </p>
                )}
                <textarea
                  value={block.text}
                  onChange={(e) => updateBlockText(index, e.target.value)}
                  rows={Math.min(8, Math.max(2, block.text.split("\n").length + 1))}
                  className="w-full resize-y rounded-xl border border-zinc-300 px-3 py-2 text-sm leading-relaxed text-zinc-700 outline-none focus-visible:ring-2 focus-visible:ring-brand-500/25 focus-visible:ring-offset-1 focus:border-zinc-900"
                />
              </div>
            ))}
          </div>

          <div className="mt-5 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-zinc-200 bg-white p-4">
            <button
              onClick={confirmAll}
              className="rounded-lg border border-zinc-200 px-3 py-2 text-xs text-zinc-500 hover:bg-zinc-50"
            >
              全部标记为已核对
            </button>
            <div className="flex items-center gap-3">
              <span className="text-xs text-zinc-400">
                {blocks.filter((b) => b.confirmed).length}/{blocks.length} 块已核对
              </span>
              <button
                onClick={handleSubmit}
                disabled={!allConfirmed || saving}
                className="rounded-lg bg-zinc-900 px-5 py-2 text-sm font-medium text-white hover:bg-zinc-800 disabled:opacity-50"
              >
                {saving ? "保存中…" : "保存确认并解锁评分"}
              </button>
            </div>
          </div>
        </>
      )}
    </main>
  );
}

function ConfidenceBadge({ confidence }: { confidence: number | null }) {
  const value = confidence ?? 0;
  const style =
    value >= 0.7
      ? "bg-green-100 text-green-700"
      : value >= 0.5
        ? "bg-yellow-100 text-yellow-700"
        : "bg-red-100 text-red-600";
  return (
    <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${style}`}>
      置信度 {formatConfidence(confidence)}
    </span>
  );
}

function formatConfidence(confidence: number | null | undefined): string {
  if (confidence == null) return "未知";
  return `${Math.round(confidence * 100)}%`;
}

function toEditableBlocks(resume: Resume): EditableBlock[] {
  const blocks: OcrBlock[] = resume.ocrBlocks?.length
    ? resume.ocrBlocks
    : [
        {
          text: resume.rawText ?? "",
          confidence: resume.ocrConfidence ?? 0.4,
          reason: "未提供分块信息，请整体核对识别文本",
        },
      ];

  return blocks.map((b) => ({
    text: b.text ?? "",
    confidence: b.confidence ?? null,
    reason: b.reason ?? null,
    confirmed: false,
  }));
}
