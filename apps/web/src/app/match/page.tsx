"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { Resume, Job, MatchResult, TargetedOptimizeResponse } from "@/lib/types";
import ImprovementReport from "@/components/ImprovementReport";

export default function MatchPage() {
  const [resumes, setResumes] = useState<Resume[]>([]);
  const [jobs, setJobs] = useState<Job[]>([]);
  const [resumeId, setResumeId] = useState<string>("");
  const [jobId, setJobId] = useState<string>("");
  const [result, setResult] = useState<MatchResult | null>(null);
  const [report, setReport] = useState<{
    before: MatchResult;
    after: MatchResult;
    targeted: TargetedOptimizeResponse;
  } | null>(null);
  const [matching, setMatching] = useState(false);
  const [optimizing, setOptimizing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([api<Resume[]>("/api/v1/resume"), api<Job[]>("/api/v1/job")])
      .then(([r, j]) => {
        setResumes(r);
        setJobs(j);
        if (r.length > 0) setResumeId(String(r[0].id));
        if (j.length > 0) setJobId(String(j[0].id));
      })
      .catch((e) => setError(e instanceof Error ? e.message : "加载失败"));
  }, []);

  async function handleMatch() {
    if (!resumeId || !jobId) {
      setError("请选择简历和岗位");
      return;
    }
    setMatching(true);
    setError(null);
    setResult(null);
    setReport(null);
    try {
      const matched = await api<MatchResult>("/api/v1/match/body", {
        method: "POST",
        body: JSON.stringify({
          resumeId: Number(resumeId),
          jobId: Number(jobId),
          forceRefresh: true,
        }),
      });
      setResult(matched);
    } catch (e) {
      setError(e instanceof Error ? e.message : "匹配失败");
    } finally {
      setMatching(false);
    }
  }

  async function handleTargetedOptimize() {
    if (!resumeId || !jobId) {
      setError("请选择简历和岗位");
      return;
    }
    setOptimizing(true);
    setError(null);
    setResult(null);
    setReport(null);
    try {
      // 1. 优化前：简历原文对 JD 匹配
      const before = await api<MatchResult>("/api/v1/match/body", {
        method: "POST",
        body: JSON.stringify({
          resumeId: Number(resumeId),
          jobId: Number(jobId),
          forceRefresh: true,
        }),
      });

      // 2. 定向优化并保存版本
      const targeted = await api<TargetedOptimizeResponse>(
        `/api/v1/optimize/${resumeId}/targeted?jobId=${jobId}`,
        { method: "POST" },
      );

      // 3. 优化后：对保存的版本重新匹配（forceRefresh 跳过缓存，保证可复现对比）
      const after = await api<MatchResult>("/api/v1/match/body", {
        method: "POST",
        body: JSON.stringify({
          resumeId: Number(resumeId),
          jobId: Number(jobId),
          versionId: targeted.versionId,
          forceRefresh: true,
        }),
      });

      setReport({ before, after, targeted });
    } catch (e) {
      setError(e instanceof Error ? e.message : "定向优化失败");
    } finally {
      setOptimizing(false);
    }
  }

  return (
    <main className="mx-auto max-w-3xl p-6">
      <header className="mb-6">
        <h1 className="text-2xl font-bold text-slate-900">AI 人岗匹配</h1>
        <p className="mt-1 text-sm text-slate-500">选择简历与目标岗位，AI 分析匹配度</p>
      </header>

      {error && (
        <div className="mb-4 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-600">
          {error}
        </div>
      )}

      {/* 选择区 */}
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-700">选择简历</label>
            <select
              value={resumeId}
              onChange={(e) => setResumeId(e.target.value)}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500"
            >
              {resumes.length === 0 && <option value="">暂无简历，请先上传</option>}
              {resumes.map((r) => (
                <option key={r.id} value={r.id}>
                  #{r.id} {r.fileName} {r.parsedName ? `(${r.parsedName})` : ""}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-700">选择岗位</label>
            <select
              value={jobId}
              onChange={(e) => setJobId(e.target.value)}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500"
            >
              {jobs.length === 0 && <option value="">暂无岗位，请先录入</option>}
              {jobs.map((j) => (
                <option key={j.id} value={j.id}>
                  #{j.id} {j.title} · {j.company}
                </option>
              ))}
            </select>
          </div>
        </div>
        <div className="mt-4 grid gap-2 sm:grid-cols-2">
          <button
            onClick={handleMatch}
            disabled={matching || optimizing || resumes.length === 0 || jobs.length === 0}
            className="rounded-lg bg-slate-100 py-2.5 text-sm font-medium text-slate-700 hover:bg-slate-200 disabled:opacity-50"
          >
            {matching ? "AI 匹配分析中，约 20~40 秒…" : "仅看匹配度"}
          </button>
          <button
            onClick={handleTargetedOptimize}
            disabled={matching || optimizing || resumes.length === 0 || jobs.length === 0}
            className="rounded-lg bg-emerald-600 py-2.5 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
          >
            {optimizing ? "定向优化 + 重新匹配中…" : "🎯 定向优化并对比提升"}
          </button>
        </div>
      </div>

      {/* 结果区 */}
      {result && (
        <div className="mt-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <div className="flex items-center gap-6">
            <div className="text-center">
              <p className="text-5xl font-bold text-blue-600">
                {Math.round(result.overallScore ?? 0)}
              </p>
              <p className="mt-1 text-xs text-slate-400">综合匹配度</p>
            </div>
            <div className="flex-1 space-y-2">
              <MatchBar label="关键词覆盖" value={result.keywordCoverage} />
              <MatchBar label="语义匹配" value={result.semanticSimilarity} />
              <MatchBar label="硬性条件" value={result.hardRequirementScore} />
            </div>
          </div>
          {result.matchExplanation && (
            <div className="mt-5 rounded-xl bg-slate-50 p-4">
              <p className="mb-1 text-xs font-medium text-slate-400">AI 归因分析</p>
              <p className="text-sm leading-relaxed text-slate-600">{result.matchExplanation}</p>
            </div>
          )}
        </div>
      )}

      {/* 定向优化提升报告 */}
      {report && (
        <div className="mt-6">
          <ImprovementReport
            resumeId={resumeId}
            before={report.before}
            after={report.after}
            targeted={report.targeted}
          />
        </div>
      )}
    </main>
  );
}

function MatchBar({ label, value }: { label: string; value: number | null }) {
  const v = Math.min(value ?? 0, 100);
  const color = v >= 70 ? "bg-green-500" : v >= 40 ? "bg-yellow-400" : "bg-red-400";
  return (
    <div className="flex items-center gap-2">
      <span className="w-20 shrink-0 text-xs text-slate-500">{label}</span>
      <div className="h-2 flex-1 overflow-hidden rounded-full bg-slate-100">
        <div className={`h-full ${color}`} style={{ width: `${v}%` }} />
      </div>
      <span className="w-8 text-right text-xs text-slate-500">{Math.round(v)}</span>
    </div>
  );
}
