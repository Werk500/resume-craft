"use client";

import type { MatchResult } from "@/lib/types";
import SemanticModeBadge from "@/components/SemanticModeBadge";

/**
 * 匹配分卡片：总分 + 三维明细 + 语义评分来源 + 硬性封顶提示 + 关键词命中。
 * 匹配页与提升报告共用，保证同一份结果在不同位置呈现一致。
 */
export default function MatchScoreCard({
  result,
  title,
}: {
  result: MatchResult;
  title?: string;
}) {
  const overall = Math.round(result.overallScore ?? 0);
  // 与服务端 MatchEngine 的封顶规则保持一致：硬性不满足 → 总分上限 40
  const capped = result.hardRequirementPassed === false && overall <= 40;
  const semantic = result.dimensionDetails?.semantic;
  const failedItems = result.dimensionDetails?.hardRequirement?.failedItems ?? [];

  return (
    <div className="rounded-2xl border border-zinc-200 bg-white p-6 shadow-card">
      {title && <h2 className="mb-4 text-base font-bold text-zinc-900">{title}</h2>}

      <div className="flex flex-wrap items-center gap-x-8 gap-y-4">
        <div className="text-center">
          <p className={`text-5xl font-bold ${scoreTone(overall)}`}>{overall}</p>
          <p className="mt-1 text-xs text-zinc-400">综合匹配度</p>
        </div>
        <div className="min-w-[220px] flex-1 space-y-2.5">
          <MatchBar label="关键词覆盖" value={result.keywordCoverage} />
          <MatchBar
            label="语义匹配"
            value={result.semanticSimilarity}
            badge={<SemanticModeBadge mode={semantic?.mode} reason={semantic?.reason} />}
          />
          <MatchBar
            label="硬性条件"
            value={result.hardRequirementScore}
            badge={
              result.hardRequirementPassed === false ? (
                <span className="inline-flex items-center gap-1 rounded-full bg-red-50 px-2 py-0.5 text-[11px] font-medium text-red-600 ring-1 ring-inset ring-red-200">
                  <span aria-hidden className="text-[8px] leading-none">▲</span>
                  总分封顶 40
                </span>
              ) : null
            }
          />
        </div>
      </div>

      {failedItems.length > 0 && (
        <div className="mt-4 rounded-xl border border-red-100 bg-red-50/60 p-3">
          <p className="text-xs font-medium text-red-700">硬性条件未满足</p>
          <ul className="mt-1.5 space-y-1">
            {failedItems.map((item) => (
              <li key={item} className="flex gap-2 text-sm text-red-600/90">
                <span className="text-red-400">•</span>
                <span>{item}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {capped && (
        <p className="mt-2 text-xs text-zinc-400">
          综合分已按规则封顶——即使其他维度得分更高，硬性条件不满足时总分不超过 40。
        </p>
      )}

      {semantic?.reason && (
        <p className="mt-3 text-xs leading-relaxed text-zinc-400">语义评分归因：{semantic.reason}</p>
      )}

      {(result.keywordHits?.length ?? 0) > 0 && (
        <div className="mt-5">
          <p className="text-xs font-medium text-zinc-400">
            关键词命中
            <span className="ml-1.5 text-zinc-300">
              {result.keywordHits!.filter((h) => h.hit).length}/{result.keywordHits!.length}
            </span>
          </p>
          <div className="mt-2 flex flex-wrap gap-1.5">
            {result.keywordHits!.map((hit) => (
              <span
                key={hit.keyword}
                className={`rounded-full px-2.5 py-1 text-xs ${
                  hit.hit
                    ? "bg-brand-50 text-brand-700"
                    : "bg-zinc-100 text-zinc-500 line-through decoration-zinc-300"
                }`}
              >
                {hit.keyword}
              </span>
            ))}
          </div>
        </div>
      )}

      {result.matchExplanation && (
        <div className="mt-5 rounded-xl bg-zinc-50 p-4">
          <p className="mb-1 text-xs font-medium text-zinc-400">AI 归因分析</p>
          <p className="text-sm leading-relaxed text-zinc-600">{result.matchExplanation}</p>
        </div>
      )}
    </div>
  );
}

function MatchBar({
  label,
  value,
  badge,
}: {
  label: string;
  value: number | null;
  badge?: React.ReactNode;
}) {
  const v = Math.min(Math.max(value ?? 0, 0), 100);
  return (
    <div className="flex items-center gap-3">
      <span className="flex w-28 shrink-0 items-center gap-1.5 text-xs text-zinc-500">
        {label}
        {badge}
      </span>
      <div className="relative h-px flex-1 bg-zinc-200">
        <span className="absolute -top-[3px] h-[7px] w-px bg-zinc-300" style={{ left: "50%" }} />
        <span
          className="absolute -top-[4px] h-[9px] w-[2px] rounded bg-zinc-900"
          style={{ left: `calc(${v}% - 1px)` }}
        />
      </div>
      <span className={`w-9 text-right text-sm font-medium ${scoreTone(v)}`}>{Math.round(v)}</span>
    </div>
  );
}

function scoreTone(value: number): string {
  return value >= 70 ? "text-brand-700" : value >= 40 ? "text-amber-600" : "text-red-500";
}
