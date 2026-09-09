"use client";

import Link from "next/link";
import type { KeywordHit, MatchResult, TargetedOptimizeResponse } from "@/lib/types";

interface ImprovementReportProps {
  resumeId: string;
  before: MatchResult;
  after: MatchResult;
  targeted: TargetedOptimizeResponse;
}

export default function ImprovementReport({
  resumeId,
  before,
  after,
  targeted,
}: ImprovementReportProps) {
  const beforeScore = round(before.overallScore);
  const afterScore = round(after.overallScore);
  const delta = afterScore - beforeScore;
  const newlyHitKeywords = findNewlyHitKeywords(before.keywordHits, before.missingKeywords, after);
  const dimensions = [
    { label: "关键词覆盖", before: before.keywordCoverage, after: after.keywordCoverage },
    { label: "语义匹配", before: before.semanticSimilarity, after: after.semanticSimilarity },
    { label: "硬性条件", before: before.hardRequirementScore, after: after.hardRequirementScore },
  ];

  return (
    <div className="rounded-2xl border border-emerald-200 bg-white p-6 shadow-sm">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-lg font-bold text-slate-900">✨ 定向优化 · 提升报告</h2>
          <p className="mt-0.5 text-xs text-slate-400">
            同一 JD 下，优化前简历 vs 优化版本（已保存为版本 #{targeted.versionId}）
          </p>
        </div>
        <Link
          href={`/resume/${resumeId}/versions`}
          className="shrink-0 rounded-lg bg-slate-100 px-3 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-200"
        >
          查看 Diff
        </Link>
      </div>

      {/* 总分对比 */}
      <div className="mt-5 flex flex-wrap items-end gap-4">
        <ScoreBlock label="优化前" value={beforeScore} tone="slate" />
        <div className="pb-2 text-2xl text-slate-300">→</div>
        <ScoreBlock label="优化后" value={afterScore} tone="blue" />
        <div
          className={`mb-2 rounded-full px-3 py-1 text-sm font-bold ${
            delta >= 0 ? "bg-emerald-100 text-emerald-600" : "bg-amber-100 text-amber-600"
          }`}
        >
          {delta >= 0 ? "▲" : "▼"} {Math.abs(delta)}
        </div>
      </div>

      {/* 三维对比 */}
      <div className="mt-5 grid gap-3 sm:grid-cols-3">
        {dimensions.map((d) => (
          <div key={d.label} className="rounded-xl bg-slate-50 p-3">
            <p className="text-xs text-slate-400">{d.label}</p>
            <div className="mt-2 flex items-baseline gap-2">
              <span className="text-xl font-bold text-slate-700">{round(d.before)}</span>
              <span className="text-xs text-slate-400">→</span>
              <span className="text-xl font-bold text-blue-600">{round(d.after)}</span>
              <span className={`text-xs font-medium ${(round(d.after) - round(d.before)) >= 0 ? "text-emerald-500" : "text-amber-500"}`}>
                {(round(d.after) - round(d.before)) >= 0 ? "+" : ""}
                {round(d.after) - round(d.before)}
              </span>
            </div>
          </div>
        ))}
      </div>

      {/* 提升原因 */}
      <div className="mt-5 space-y-3">
        {newlyHitKeywords.length > 0 && (
          <section className="rounded-xl border border-green-100 bg-green-50/60 p-3">
            <p className="text-xs font-medium text-green-700">新增命中的核心关键词</p>
            <div className="mt-2 flex flex-wrap gap-1.5">
              {newlyHitKeywords.map((k) => (
                <span
                  key={k}
                  className="rounded-full bg-green-100 px-2.5 py-1 text-xs font-medium text-green-700"
                >
                  ✓ {k}
                </span>
              ))}
            </div>
          </section>
        )}

        {(targeted.changes?.length ?? 0) > 0 && (
          <section className="rounded-xl border border-blue-100 bg-blue-50/60 p-3">
            <p className="text-xs font-medium text-blue-700">本次改动（提升原因）</p>
            <ul className="mt-2 space-y-1.5">
              {targeted.changes!.map((c, i) => (
                <li key={i} className="flex gap-2 text-sm text-slate-600">
                  <span className="text-blue-400">•</span>
                  <span>{c}</span>
                </li>
              ))}
            </ul>
          </section>
        )}

        {(targeted.gaps?.length ?? 0) > 0 && (
          <section className="rounded-xl border border-amber-200 bg-amber-50/60 p-3">
            <p className="text-xs font-medium text-amber-700">仍需补强的缺口（建议下一步）</p>
            <ul className="mt-2 space-y-1.5">
              {targeted.gaps!.map((g, i) => (
                <li key={i} className="flex gap-2 text-sm text-slate-600">
                  <span className="text-amber-500">△</span>
                  <span>{g}</span>
                </li>
              ))}
            </ul>
          </section>
        )}
      </div>

      {after.matchExplanation && (
        <div className="mt-4 rounded-xl bg-slate-50 p-4">
          <p className="mb-1 text-xs font-medium text-slate-400">优化后归因分析</p>
          <p className="text-sm leading-relaxed text-slate-600">{after.matchExplanation}</p>
        </div>
      )}
    </div>
  );
}

function ScoreBlock({
  label,
  value,
  tone,
}: {
  label: string;
  value: number;
  tone: "slate" | "blue";
}) {
  const color = tone === "blue" ? "text-blue-600" : "text-slate-700";
  return (
    <div className="text-center">
      <p className="text-xs text-slate-400">{label}</p>
      <p className={`text-5xl font-bold ${color}`}>{value}</p>
    </div>
  );
}

function findNewlyHitKeywords(
  beforeHits: KeywordHit[] | null,
  beforeMissing: string[] | null,
  after: MatchResult,
): string[] {
  const beforeHitSet = new Set(
    (beforeHits ?? []).filter((h) => h.hit).map((h) => h.keyword),
  );
  const missing = new Set(beforeMissing ?? []);

  return (after.keywordHits ?? [])
    .filter((h) => h.hit && (!beforeHitSet.has(h.keyword) || missing.has(h.keyword)))
    .map((h) => h.keyword);
}

function round(value: number | null | undefined): number {
  return Math.round(value ?? 0);
}
