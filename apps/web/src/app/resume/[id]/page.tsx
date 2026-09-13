"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { api } from "@/lib/api";
import type { Resume, DiagnosisResponse, ResumeVersion } from "@/lib/types";
import SegmentRewriter from "@/components/SegmentRewriter";

export default function ResumeDetailPage({ params }: { params: { id: string } }) {
  const resumeId = params.id;

  const [resume, setResume] = useState<Resume | null>(null);
  const [diagnosis, setDiagnosis] = useState<DiagnosisResponse | null>(null);
  const [diagnosing, setDiagnosing] = useState(false);
  const [optimized, setOptimized] = useState<ResumeVersion | null>(null);
  const [optimizing, setOptimizing] = useState(false);
  const [targetJob, setTargetJob] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<Resume>(`/api/v1/resume/${resumeId}`)
      .then(setResume)
      .catch((e) => setError(e instanceof Error ? e.message : "加载失败"));
  }, [resumeId]);

  async function handleDiagnose() {
    if (resume?.ocrStatus === "REVIEW") {
      setError("图片识别置信度较低，请先核对识别内容再诊断");
      return;
    }
    setDiagnosing(true);
    setError(null);
    try {
      setDiagnosis(await api<DiagnosisResponse>(`/api/v1/diagnose/${resumeId}`));
    } catch (e) {
      setError(e instanceof Error ? e.message : "诊断失败");
    } finally {
      setDiagnosing(false);
    }
  }

  async function handleOptimize() {
    if (resume?.ocrStatus === "REVIEW") {
      setError("图片识别置信度较低，请先核对识别内容再优化");
      return;
    }
    setOptimizing(true);
    setError(null);
    try {
      const q = targetJob.trim() ? `?targetJob=${encodeURIComponent(targetJob.trim())}` : "";
      setOptimized(await api<ResumeVersion>(`/api/v1/optimize/${resumeId}${q}`, { method: "POST" }));
    } catch (e) {
      setError(e instanceof Error ? e.message : "优化失败");
    } finally {
      setOptimizing(false);
    }
  }

  function downloadMarkdown() {
    if (!optimized) return;
    const blob = new Blob([optimized.optimizedContent], { type: "text/markdown;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${resume?.fileName || "resume"}-优化版.md`;
    a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <main className="mx-auto max-w-5xl p-6">
      <div className="mb-4 flex items-center justify-between">
        <Link href="/resumes" className="text-sm text-blue-600 hover:underline">
          ← 返回简历列表
        </Link>
        <Link
          href={`/resume/${resumeId}/versions`}
          className="rounded-lg bg-slate-100 px-3 py-1.5 text-sm font-medium text-slate-600 hover:bg-slate-200"
        >
          📚 版本历史
        </Link>
      </div>

      {error && (
        <div className="mb-4 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-600">
          {error}
        </div>
      )}

      {resume?.ocrStatus === "REVIEW" && (
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-amber-200 bg-amber-50 p-4">
          <div>
            <p className="text-sm font-medium text-amber-700">
              ⚠️ 图片识别置信度较低，已暂停 AI 诊断与优化
            </p>
            <p className="mt-0.5 text-xs text-amber-600">
              模糊扫描件不会参与自动评分，请先人工核对识别内容。
            </p>
          </div>
          <Link
            href={`/resume/${resumeId}/review`}
            className="rounded-lg bg-amber-500 px-4 py-2 text-sm font-medium text-white hover:bg-amber-600"
          >
            去核对识别内容
          </Link>
        </div>
      )}

      {resume && (
        <div className="grid gap-4 lg:grid-cols-2">
          {/* 左列：原始简历 */}
          <div className="space-y-4">
            <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
              <h1 className="text-lg font-bold text-slate-900">{resume.fileName}</h1>
              <p className="mt-1 text-xs text-slate-400">
                {resume.fileType.toUpperCase()} · 上传于 {resume.createTime?.slice(0, 10)}
              </p>
              <div className="mt-4 grid grid-cols-2 gap-3 text-sm">
                <div>
                  <span className="text-xs text-slate-400">姓名</span>
                  <p className="font-medium text-slate-700">{resume.parsedName || "未识别"}</p>
                </div>
                <div>
                  <span className="text-xs text-slate-400">邮箱</span>
                  <p className="font-medium text-slate-700">{resume.parsedEmail || "未识别"}</p>
                </div>
                <div>
                  <span className="text-xs text-slate-400">电话</span>
                  <p className="font-medium text-slate-700">{resume.parsedPhone || "未识别"}</p>
                </div>
              </div>
              <p className="mt-4 text-xs text-slate-400">
                💡 鼠标选中原文中的一段经历，可进行 AI 逐句精修
              </p>
              <SegmentRewriter resumeId={resumeId} initialText={resume.rawText} />
            </div>
          </div>

          {/* 右列：AI 诊断 + 优化 */}
          <div className="space-y-4">
            {/* 诊断卡片 */}
            <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
              <h2 className="font-semibold text-slate-800">🤖 AI 简历诊断</h2>
              {!diagnosis && !diagnosing && (
                <button
                  onClick={handleDiagnose}
                  disabled={resume?.ocrStatus === "REVIEW"}
                  className="mt-4 w-full rounded-lg bg-blue-600 py-2.5 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
                >
                  开始诊断
                </button>
              )}
              {diagnosing && <p className="mt-4 animate-pulse text-sm text-slate-500">AI 分析中，约 10~30 秒…</p>}
              {diagnosis && (
                <div className="mt-4">
                  <div className="flex items-center gap-3">
                    <span className="text-4xl font-bold text-slate-900">{diagnosis.totalScore}</span>
                    <div className="text-sm">
                      <span className="text-slate-400">综合评分</span>
                      <ScoreBar value={diagnosis.completeness.score} label={diagnosis.completeness.label} color={diagnosis.completeness.color} />
                      <ScoreBar value={diagnosis.expression.score} label={diagnosis.expression.label} color={diagnosis.expression.color} />
                      <ScoreBar value={diagnosis.matchScore.score} label={diagnosis.matchScore.label} color={diagnosis.matchScore.color} />
                    </div>
                  </div>
                  <ul className="mt-4 space-y-2">
                    {diagnosis.suggestions.map((s, i) => (
                      <li key={i} className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-600">
                        {s}
                      </li>
                    ))}
                  </ul>
                  <button
                    onClick={handleDiagnose}
                    disabled={resume?.ocrStatus === "REVIEW"}
                    className="mt-3 text-xs text-blue-600 hover:underline"
                  >
                    重新诊断
                  </button>
                </div>
              )}
            </div>

            {/* 优化卡片 */}
            <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
              <h2 className="font-semibold text-slate-800">✨ AI 一键优化</h2>
              <div className="mt-4 flex gap-2">
                <input
                  value={targetJob}
                  onChange={(e) => setTargetJob(e.target.value)}
                  placeholder="目标岗位（可空 = 通用优化）"
                  className="flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-brand-500/25 focus-visible:ring-offset-1 focus:border-blue-500"
                />
                <button
                  onClick={handleOptimize}
                  disabled={optimizing || resume?.ocrStatus === "REVIEW"}
                  className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
                >
                  {optimizing ? "优化中…" : "优化"}
                </button>
              </div>

              {optimized && (
                <div className="mt-4">
                  <div className="mb-2 flex items-center justify-between">
                    <span className="text-xs text-slate-400">
                      {optimized.versionName}
                      {optimized.targetJob ? ` · 目标岗位：${optimized.targetJob}` : ""}
                    </span>
                    <div className="flex gap-2 text-xs">
                      <button
                        onClick={() => navigator.clipboard.writeText(optimized.optimizedContent)}
                        className="rounded border border-slate-200 px-2 py-1 text-slate-500 hover:bg-slate-50"
                      >
                        复制
                      </button>
                      <button
                        onClick={downloadMarkdown}
                        className="rounded border border-slate-200 px-2 py-1 text-slate-500 hover:bg-slate-50"
                      >
                        下载
                      </button>
                    </div>
                  </div>
                  <pre className="max-h-96 overflow-y-auto whitespace-pre-wrap rounded-xl border border-blue-100 bg-blue-50/50 p-4 text-xs leading-relaxed text-slate-700">
                    {optimized.optimizedContent}
                  </pre>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </main>
  );
}

function ScoreBar({ value, label, color }: { value: number; label: string; color: string }) {
  const colorClass =
    color === "green" ? "bg-green-500" : color === "yellow" ? "bg-yellow-400" : "bg-red-400";
  return (
    <div className="mt-1 flex items-center gap-2">
      <span className="w-20 shrink-0 text-xs text-slate-500">{label}</span>
      <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-slate-100">
        <div className={`h-full ${colorClass}`} style={{ width: `${Math.min(value, 100)}%` }} />
      </div>
      <span className="w-8 text-right text-xs text-slate-500">{Math.round(value)}</span>
    </div>
  );
}
