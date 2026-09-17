/**
 * 语义评分来源标识。
 *
 * 匹配引擎的语义维度支持三种模式，分数口径不同，必须让用户看见：
 *   EMBEDDING     —— pgvector 向量余弦相似度，结果可复现
 *   AI_APPROX     —— 大模型近似打分（向量库不可用时的降级）
 *   RULE_FALLBACK —— 固定分兜底（大模型也不可用）
 *
 * 这个标识同时是"降级可观测"能力的可视化证据：把 VECTOR_ENABLED 关掉后，
 * 页面上会真的从"向量检索"变成"AI 近似"，而不是无感地返回一个数字。
 */

const MODE_META: Record<string, { label: string; className: string; hint: string }> = {
  EMBEDDING: {
    label: "向量检索",
    className: "bg-brand-50 text-brand-700 ring-brand-200",
    hint: "简历与 JD 各自向量化后计算余弦相似度，同一输入多次调用分数完全一致。",
  },
  AI_APPROX: {
    label: "AI 近似",
    className: "bg-amber-50 text-amber-700 ring-amber-200",
    hint: "向量库不可用时的降级路径：由大模型直接估算语义贴合度，分数会有小幅波动。",
  },
  RULE_FALLBACK: {
    label: "固定兜底",
    className: "bg-zinc-100 text-zinc-500 ring-zinc-200",
    hint: "向量与 AI 均不可用时的兜底分值，仅保证接口可用，参考价值有限。",
  },
};

export default function SemanticModeBadge({
  mode,
  reason,
}: {
  mode: string | null | undefined;
  reason?: string | null;
}) {
  if (!mode) return null;
  const meta = MODE_META[mode] ?? {
    label: mode,
    className: "bg-zinc-100 text-zinc-500 ring-zinc-200",
    hint: "",
  };

  const title = reason ? `${meta.hint}\n\n本次归因：${reason}` : meta.hint;

  return (
    <span
      title={title}
      className={`inline-flex cursor-help items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-medium ring-1 ring-inset ${meta.className}`}
    >
      <span aria-hidden className="text-[8px] leading-none">◆</span>
      {meta.label}
    </span>
  );
}
