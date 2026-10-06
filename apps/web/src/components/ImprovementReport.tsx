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

      {/* 下一步：由后端按覆盖率 + 待确认项用代码判定，放在最上面先给结论 */}
      {targeted.nextStep && (
        <NextStepCard
          nextStep={targeted.nextStep}
          advice={targeted.advice}
          gaps={targeted.gaps}
          resumeId={resumeId}
        />
      )}

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
  const claims = targeted.pendingClaims ?? [];
  const steps = targeted.steps ?? [];
  // 降级执行时没有分数轨迹（打分服务不可用），只显示一条说明
  const hasTrace = !targeted.degraded && (targeted.baselineCoverage != null || iterations.length > 0);

  if (!hasTrace && pending.length === 0 && claims.length === 0 && !targeted.degraded && steps.length === 0) {
    return null;
  }

  const gain =
    targeted.baselineCoverage != null && targeted.finalCoverage != null
      ? Math.round((targeted.finalCoverage - targeted.baselineCoverage) * 10) / 10
      : null;

  return (
    <>
      {targeted.degraded && (
        <section className="mt-5 rounded-xl border border-amber-300 bg-amber-50 p-3">
          <p className="text-xs font-medium text-amber-800">⚠ 本次为降级结果</p>
          <p className="mt-1 text-[11px] leading-relaxed text-amber-700">
            打分服务暂时不可用，所以只做了一次改写：没有跑「打分 → 复算 → 保留最优」的闭环，
            也没有分数对比与事实核查。等打分服务恢复后重新优化一次即可。
          </p>
        </section>
      )}

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

      {claims.length > 0 && (
        <section className="mt-3 rounded-xl border border-rose-300 bg-rose-50 p-3">
          <p className="text-xs font-medium text-rose-800">
            ⚠ 疑似夸大：{claims.length} 处表述比原文更「满」
          </p>
          <p className="mt-1 text-[11px] leading-relaxed text-rose-700">
            AI 改写时把经历说得更重了（例如把「参与」写成「主导」、「有经验」写成「已落地」）。
            这类问题面试时最容易被追问穿，建议按原文措辞改回去再投递。
          </p>
          <ul className="mt-2 space-y-2">
            {claims.map((c, i) => (
              <li key={i} className="rounded-lg border border-rose-200 bg-white p-2">
                <p className="text-xs font-medium text-rose-700">改写稿：「{c.claim}」</p>
                {c.original && (
                  <p className="mt-0.5 text-[11px] text-zinc-500">
                    原文：{c.original.length > 80 ? `${c.original.slice(0, 80)}…` : c.original}
                  </p>
                )}
                {c.reason && <p className="mt-0.5 text-[11px] text-zinc-400">{c.reason}</p>}
              </li>
            ))}
          </ul>
        </section>
      )}

      {steps.length > 0 && <PipelineSteps steps={steps} />}
    </>
  );
}

/**
 * 优化流水线的节点耗时条。
 *
 * <p>这条图是这次优化的"工程说明书"：灰色是纯代码节点（打分、组装），蓝色是调用模型的节点。
 * 一眼能看出"哪一步花钱、哪一步必须花钱"——能确定的部分全部留在代码里，
 * 模型只负责改写和提线索。
 */
function PipelineSteps({ steps }: { steps: NonNullable<TargetedOptimizeResponse["steps"]> }) {
  const aiSteps = steps.filter((s) => s.ai);
  const aiMs = aiSteps.reduce((sum, s) => sum + (s.durationMs ?? 0), 0);
  const totalMs = steps.reduce((sum, s) => sum + (s.durationMs ?? 0), 0);
  const maxMs = Math.max(1, ...steps.map((s) => s.durationMs ?? 0));

  return (
    <section className="mt-3 rounded-xl border border-zinc-200 bg-zinc-50 p-3">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <p className="text-xs font-medium text-zinc-700">
          优化流水线：{steps.length} 个节点，其中 {aiSteps.length} 个调用模型
        </p>
        <p className="text-[11px] text-zinc-400">
          AI 节点 {aiMs}ms / 全部 {totalMs}ms
        </p>
      </div>
      <div className="mt-2 space-y-1.5">
        {steps.map((s) => (
          <div key={s.name} className="flex items-center gap-2.5" title={s.detail ?? undefined}>
            <span
              className={`w-11 shrink-0 rounded px-1 py-0.5 text-center text-[10px] font-medium ${
                s.ai ? "bg-blue-100 text-blue-700" : "bg-zinc-200 text-zinc-600"
              }`}
            >
              {s.ai ? "AI" : "code"}
            </span>
            <span className="w-28 shrink-0 truncate text-xs text-zinc-600">{s.name}</span>
            <div className="h-2 flex-1 overflow-hidden rounded-full bg-zinc-200/80">
              <div
                className={`h-full rounded-full ${
                  s.status === "DEGRADED"
                    ? "bg-amber-400"
                    : s.ai
                      ? "bg-blue-500"
                      : "bg-zinc-400"
                }`}
                style={{ width: `${Math.max(2, Math.min(100, ((s.durationMs ?? 0) / maxMs) * 100))}%` }}
              />
            </div>
            <span className="w-14 shrink-0 text-right text-xs text-zinc-500">{s.durationMs ?? 0}ms</span>
            <span className="w-16 shrink-0 text-[11px] text-amber-600">
              {s.status && s.status !== "OK" ? s.status : ""}
            </span>
          </div>
        ))}
      </div>
      <p className="mt-2 text-[11px] leading-relaxed text-zinc-400">
        灰色为纯代码节点，蓝色为调用大模型的节点：打分、复算、保留最优、事实核查的判定全部由代码完成，
        模型只负责改写与提线索。
      </p>
    </section>
  );
}

/**
 * 下一步建议卡片。
 *
 * <p>这段结论是后端用代码算的（判据：关键词覆盖率 + 待确认项数量），不是模型生成的——
 * 所以它可复现、可解释，也不会为了"看起来有帮助"而瞎给建议。
 * 放在报告最上面，用户先看到"我该干什么"，再看下面的过程与证据。
 */
const NEXT_STEP_META: Record<
  NonNullable<TargetedOptimizeResponse["nextStep"]>,
  { label: string; cls: string; tag: string }
> = {
  READY: { label: "可以投递", tag: "✓", cls: "border-emerald-300 bg-emerald-50 text-emerald-800" },
  NEEDS_CONFIRM: { label: "先确认 AI 改动", tag: "!", cls: "border-amber-300 bg-amber-50 text-amber-800" },
  NEEDS_KEYWORDS: { label: "先补关键词", tag: "→", cls: "border-blue-300 bg-blue-50 text-blue-800" },
  NOT_MATCHED: { label: "建议换岗位", tag: "×", cls: "border-zinc-300 bg-zinc-100 text-zinc-700" },
  DEGRADED: { label: "降级运行", tag: "!", cls: "border-amber-300 bg-amber-50 text-amber-800" },
};

function NextStepCard({
  nextStep,
  advice,
  gaps,
  resumeId,
}: {
  nextStep: NonNullable<TargetedOptimizeResponse["nextStep"]>;
  advice: string | null;
  gaps: string[] | null;
  resumeId: string;
}) {
  const meta = NEXT_STEP_META[nextStep] ?? {
    label: nextStep,
    tag: "·",
    cls: "border-zinc-300 bg-zinc-100 text-zinc-700",
  };

  return (
    <section className={`mt-4 rounded-xl border p-4 ${meta.cls}`}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="flex items-center gap-2 text-sm font-semibold">
          <span className="flex h-5 w-5 items-center justify-center rounded-full bg-white/70 text-xs">
            {meta.tag}
          </span>
          下一步：{meta.label}
        </p>
        {nextStep === "NEEDS_CONFIRM" && (
          <Link
            href={`/resume/${resumeId}/versions`}
            className="rounded-lg bg-white/80 px-3 py-1 text-xs font-medium hover:bg-white"
          >
            去版本页逐条确认 →
          </Link>
        )}
      </div>
      {advice && <p className="mt-1.5 text-xs leading-relaxed opacity-90">{advice}</p>}
      {nextStep === "NEEDS_KEYWORDS" && (gaps?.length ?? 0) > 0 && (
        <div className="mt-2 flex flex-wrap gap-1.5">
          {gaps!.slice(0, 8).map((g) => (
            <span key={g} className="rounded-full bg-white/70 px-2 py-0.5 text-[11px]">
              {g}
            </span>
          ))}
          {gaps!.length > 8 && <span className="text-[11px] opacity-70">…共 {gaps!.length} 个</span>}
        </div>
      )}
    </section>
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
