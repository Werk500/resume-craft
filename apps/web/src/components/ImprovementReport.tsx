"use client";

import Link from "next/link";
import type { KeywordHit, MatchResult, TargetedOptimizeResponse } from "@/lib/types";
import SemanticModeBadge from "@/components/SemanticModeBadge";

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
    <div className="rounded-2xl border border-emerald-200 bg-white p-6 shadow-card">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-lg font-bold text-zinc-900">定向优化 · 提升报告</h2>
          <p className="mt-0.5 text-xs text-zinc-400">
            同一 JD 下，优化前简历 vs 优化版本（已保存为版本 #{targeted.versionId}）
          </p>
        </div>
        <Link
          href={`/resume/${resumeId}/versions`}
          className="shrink-0 rounded-lg bg-zinc-100 px-3 py-1.5 text-xs font-medium text-zinc-600 hover:bg-zinc-200"
        >
          查看 Diff
        </Link>
      </div>

      {/* 总分对比 */}
      <div className="mt-5 flex flex-wrap items-end gap-4">
        <ScoreBlock label="优化前" value={beforeScore} tone="slate" />
        <div className="pb-2 text-2xl text-zinc-300">→</div>
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
          <div key={d.label} className="rounded-xl bg-zinc-50 p-3">
            <p className="text-xs text-zinc-400">{d.label}</p>
            <div className="mt-2 flex items-baseline gap-2">
              <span className="text-xl font-bold text-zinc-700">{round(d.before)}</span>
              <span className="text-xs text-zinc-400">→</span>
              <span className="text-xl font-bold text-zinc-900">{round(d.after)}</span>
              <span className={`text-xs font-medium ${(round(d.after) - round(d.before)) >= 0 ? "text-emerald-500" : "text-amber-500"}`}>
                {(round(d.after) - round(d.before)) >= 0 ? "+" : ""}
                {round(d.after) - round(d.before)}
              </span>
            </div>
          </div>
        ))}
      </div>

      {/* 语义评分来源：前后若走了不同的评分路径必须显式标注，
          否则"语义分变化"可能来自口径切换而非内容优化 */}
      {(before.dimensionDetails?.semantic?.mode || after.dimensionDetails?.semantic?.mode) && (
        <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1.5 rounded-xl bg-zinc-50 px-3 py-2">
          <span className="text-xs text-zinc-400">语义评分来源</span>
          <span className="flex items-center gap-1.5 text-xs text-zinc-500">
            优化前
            <SemanticModeBadge
              mode={before.dimensionDetails?.semantic?.mode}
              reason={before.dimensionDetails?.semantic?.reason}
            />
          </span>
          <span className="flex items-center gap-1.5 text-xs text-zinc-500">
            优化后
            <SemanticModeBadge
              mode={after.dimensionDetails?.semantic?.mode}
              reason={after.dimensionDetails?.semantic?.reason}
            />
          </span>
        </div>
      )}

      {/* 优化轨迹：每轮的覆盖率都由规则引擎复算，涨了才保留、没涨就回滚 */}
      <OptimizeTrace targeted={targeted} />

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
          <section className="rounded-xl border border-zinc-200 bg-zinc-100 p-3">
            <p className="text-xs font-medium text-zinc-900">本次改动（提升原因）</p>
            <ul className="mt-2 space-y-1.5">
              {targeted.changes!.map((c, i) => (
                <li key={i} className="flex gap-2 text-sm text-zinc-600">
                  <span className="text-zinc-400">•</span>
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
                <li key={i} className="flex gap-2 text-sm text-zinc-600">
                  <span className="text-amber-500">△</span>
                  <span>{g}</span>
                </li>
              ))}
            </ul>
          </section>
        )}
      </div>

      {after.matchExplanation && (
        <div className="mt-4 rounded-xl bg-zinc-50 p-4">
          <p className="mb-1 text-xs font-medium text-zinc-400">优化后归因分析</p>
          <p className="text-sm leading-relaxed text-zinc-600">{after.matchExplanation}</p>
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
  const color = tone === "blue" ? "text-zinc-900" : "text-zinc-700";
  return (
    <div className="text-center">
      <p className="text-xs text-zinc-400">{label}</p>
      <p className={`text-5xl font-bold ${color}`}>{value}</p>
    </div>
  );
}

/**
 * 优化闭环的可视化：基线 → 每轮 → 最终。
 *
 * <p>为什么值得单独展示：这套"改一版就复算一次分"的闭环是后端最想让人看见的设计——
 * 分数来自规则引擎（不调模型、可复现），涨了才保留、没涨就回滚。
 * 另外把 pendingSkills（模型写了但原文找不到依据的技能）单独标成待确认，
 * 而不是假装简历完美——这是"不造假"的最后一环。
 */
function OptimizeTrace({ targeted }: { targeted: TargetedOptimizeResponse }) {
  const iterations = targeted.iterations ?? [];
  const pending = targeted.pendingSkills ?? [];
  const hasTrace = targeted.baselineCoverage != null || iterations.length > 0;

  if (!hasTrace && pending.length === 0) {
    return null;
  }

  const gain =
    targeted.baselineCoverage != null && targeted.finalCoverage != null
      ? Math.round((targeted.finalCoverage - targeted.baselineCoverage) * 10) / 10
      : null;

  return (
    <>
      {hasTrace && (
        <section className="mt-5 rounded-xl border border-blue-100 bg-blue-50/50 p-3">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <p className="text-xs font-medium text-blue-700">AI 优化轨迹</p>
            {targeted.baselineCoverage != null && targeted.finalCoverage != null && (
              <p className="text-xs text-blue-700">
                关键词覆盖率 {pct(targeted.baselineCoverage)}% → {pct(targeted.finalCoverage)}%
                {gain != null && (
                  <span className="ml-1 font-semibold">
                    ({gain >= 0 ? "+" : ""}
                    {gain})
                  </span>
                )}
              </p>
            )}
          </div>

          <div className="mt-3 space-y-2">
            {iterations.map((it) => (
              <div key={it.round} className="flex items-center gap-3">
                <span className="w-11 shrink-0 text-xs text-zinc-500">第 {it.round} 轮</span>
                <div className="h-2.5 flex-1 overflow-hidden rounded-full bg-zinc-200/80">
                  <div
                    className={`h-full rounded-full ${it.kept ? "bg-emerald-500" : "bg-zinc-400"}`}
                    style={{ width: `${Math.max(3, Math.min(100, pct(it.keywordCoverage)))}%` }}
                  />
                </div>
                <span className="w-14 shrink-0 text-right text-xs font-semibold text-zinc-700">
                  {pct(it.keywordCoverage)}%
                </span>
                <span
                  className={`w-20 shrink-0 text-xs ${it.kept ? "text-emerald-600" : "text-zinc-400"}`}
                >
                  {it.kept ? "已保留" : "已回滚"}
                  {it.gain != null ? ` ${it.gain >= 0 ? "+" : ""}${it.gain}` : ""}
                </span>
                <span className="w-24 shrink-0 truncate text-xs text-zinc-400">
                  {(it.addedKeywords?.length ?? 0) > 0
                    ? `补 ${it.addedKeywords!.length} 个词`
                    : "无新增词"}
                </span>
              </div>
            ))}
          </div>

          <p className="mt-2 text-[11px] leading-relaxed text-blue-600/80">
            每轮覆盖率都由后端的规则引擎重新计算（不调用模型、结果可复现）；只有分数真的上涨才会保留，
            否则回滚到上一版并提前结束，避免「越改越差」。
          </p>
        </section>
      )}

      {pending.length > 0 && (
        <section className="mt-3 rounded-xl border border-amber-300 bg-amber-50 p-3">
          <p className="text-xs font-medium text-amber-800">
            ⚠ 待你确认：{pending.length} 个词在简历原文里找不到依据
          </p>
          <p className="mt-1 text-[11px] leading-relaxed text-amber-700">
            AI 改写时把它们写进了简历，代码回原文核对时没找到出处。如果确实没有相关经历，
            建议删掉再投递——面试官会顺着简历问下去。
          </p>
          <ul className="mt-2 flex flex-wrap gap-1.5">
            {pending.map((s) => (
              <li
                key={s.skill}
                title={s.evidence ? `模型给的依据：${s.evidence}` : "模型未能给出原文依据"}
                className="rounded-full border border-amber-300 bg-white px-2.5 py-1 text-xs text-amber-700"
              >
                {s.skill}
              </li>
            ))}
          </ul>
        </section>
      )}
    </>
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

/** 覆盖率保留一位小数，避免 38.900000000000006 这类浮点毛刺 */
function pct(value: number | null | undefined): number {
  return Math.round((value ?? 0) * 10) / 10;
}
