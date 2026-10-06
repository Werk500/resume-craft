"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { API_BASE, api } from "@/lib/api";
import type { AddedSkill, Overstatement, Resume, ResumeVersion } from "@/lib/types";
import TextDiff from "@/components/TextDiff";

export default function VersionsPage({ params }: { params: { id: string } }) {
  const resumeId = params.id;

  const [versions, setVersions] = useState<ResumeVersion[]>([]);
  const [resume, setResume] = useState<Resume | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<number[]>([]); // 最多选 2 个对比
  const [diffPair, setDiffPair] = useState<[ResumeVersion, ResumeVersion] | null>(null);
  // 正在逐条确认的版本 + 已做出的处置（key = "SKILL:xxx" / "CLAIM:yyy"）
  const [confirming, setConfirming] = useState<
    { version: ResumeVersion; decisions: Record<string, "ACCEPT" | "REMOVE"> } | null
  >(null);
  const [busy, setBusy] = useState(false);

  function load() {
    setLoading(true);
    Promise.all([
      api<ResumeVersion[]>(`/api/v1/version?resumeId=${resumeId}`),
      api<Resume>(`/api/v1/resume/${resumeId}`),
    ])
      .then(([versionList, resumeData]) => {
        setVersions(versionList);
        setResume(resumeData);
      })
      .catch((e) => setError(e instanceof Error ? e.message : "加载失败"))
      .finally(() => setLoading(false));
  }

  useEffect(load, [resumeId]);

  function toggleSelect(id: number) {
    setError(null);
    setSelected((prev) => {
      if (prev.includes(id)) return prev.filter((x) => x !== id);
      if (prev.length >= 2) {
        setError("最多选择 2 个版本进行对比");
        return prev;
      }
      return [...prev, id];
    });
  }

  function findVersion(id: number): ResumeVersion | null {
    // id = 0 代表“原始简历”，作为最旧的基准
    if (id === 0 && resume) {
      return {
        id: 0,
        resumeId: Number(resumeId),
        versionName: "原始简历",
        targetJob: null,
        optimizedContent: resume.rawText,
        matchScore: null,
        createTime: "",
        // 原始简历没有"确认"概念：没有待确认项，可随时导出/对比
        status: "CONFIRMED",
        confirmedAt: null,
        pendingJson: null,
      };
    }
    return versions.find((v) => v.id === id) ?? null;
  }

  function compare() {
    if (selected.length !== 2) return;
    const a = findVersion(selected[0]);
    const b = findVersion(selected[1]);
    if (!a || !b) return;
    // 约定：原始简历始终作为旧版；两个版本则按创建时间排序
    const [oldV, newV] =
      a.id === 0 ? [a, b] : b.id === 0 ? [b, a] : a.createTime <= b.createTime ? [a, b] : [b, a];
    setDiffPair([oldV, newV]);
  }

  async function handleDelete(id: number) {
    if (!confirm("确认删除该版本？")) return;
    setError(null);
    try {
      await api(`/api/v1/version/${id}`, { method: "DELETE" });
      setSelected((p) => p.filter((x) => x !== id));
      if (diffPair && (diffPair[0].id === id || diffPair[1].id === id)) setDiffPair(null);
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "删除失败");
    }
  }

  /** 提交确认：把所有待确认项连同处置结果发给后端，后端校验完整性后才置为 CONFIRMED */
  async function handleConfirm() {
    if (!confirming) return;
    const { version, decisions } = confirming;
    setBusy(true);
    setError(null);
    try {
      await api(`/api/v1/version/${version.id}/confirm`, {
        method: "POST",
        body: JSON.stringify({
          decisions: Object.entries(decisions).map(([key, action]) => {
            const [type, ...rest] = key.split(":");
            return { type, target: rest.join(":"), action };
          }),
        }),
      });
      setConfirming(null);
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "确认失败");
    } finally {
      setBusy(false);
    }
  }

  /** 导出：后端对"有待确认项且未确认"的版本会返回 400，这里把提示原样显示 */
  async function handleExport(version: ResumeVersion, format: "pdf" | "docx") {
    setError(null);
    try {
      const token = typeof window !== "undefined" ? localStorage.getItem("token") : null;
      const res = await fetch(`${API_BASE}/api/v1/version/${version.id}/export?format=${format}`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });
      if (!res.ok) {
        const text = await res.text().catch(() => "");
        let message = `导出失败 (HTTP ${res.status})`;
        try {
          const parsed = JSON.parse(text);
          if (parsed?.message) message = parsed.message;
        } catch {
          if (text.trim()) message = text.trim();
        }
        throw new Error(message);
      }
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `${version.versionName || `resume-version-${version.id}`}.${format}`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (e) {
      setError(e instanceof Error ? e.message : "导出失败");
    }
  }

  return (
    <main className="mx-auto max-w-5xl p-6">
      <div className="mb-4 flex items-center justify-between">
        <Link href={`/resume/${resumeId}`} className="text-sm text-zinc-900 hover:underline">
          ← 返回简历详情
        </Link>
        <h1 className="text-xl font-bold text-zinc-900">版本历史</h1>
        <span className="w-20" />
      </div>

      {error && (
        <div className="mb-4 rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-600">
          {error}
        </div>
      )}

      {loading && <p className="py-10 text-center text-zinc-400">加载中…</p>}

      {!loading && versions.length === 0 && (
        <div className="rounded-2xl border-2 border-dashed border-zinc-300 bg-white p-16 text-center">
          <p className="text-zinc-500">暂无版本，先在简历详情页做 AI 优化或逐句精修</p>
        </div>
      )}

      {/* 对比操作条 */}
      {selected.length === 2 && (
        <div className="mb-4 flex items-center gap-3 rounded-xl bg-zinc-100 p-3">
          <p className="text-sm text-zinc-900">已选 2 个版本</p>
          <button
            onClick={compare}
            className="rounded-lg bg-zinc-900 px-4 py-1.5 text-sm font-medium text-white hover:bg-zinc-800"
          >
            对比差异
          </button>
          <button
            onClick={() => setSelected([])}
            className="text-sm text-zinc-600 hover:underline"
          >
            清空
          </button>
        </div>
      )}

      {/* 版本列表 */}
      <div className="space-y-2">
        {resume && (
          <div
            key={0}
            className={`flex items-center justify-between rounded-xl border p-4 transition ${
              selected.includes(0) ? "border-brand-400 bg-zinc-100" : "border-amber-200 bg-amber-50/50"
            }`}
          >
            <label className="flex cursor-pointer items-center gap-3">
              <input
                type="checkbox"
                checked={selected.includes(0)}
                onChange={() => toggleSelect(0)}
                className="h-4 w-4"
              />
              <div>
                <p className="font-medium text-zinc-800">原始简历（上传解析）</p>
                <p className="mt-0.5 text-xs text-zinc-400">
                  {resume.fileName} · {resume.rawText?.length ?? 0} 字
                </p>
              </div>
            </label>
            <span className="rounded bg-amber-100 px-2 py-1 text-xs text-amber-600">基准</span>
          </div>
        )}
        {versions.map((v) => {
          const pending = parsePendingItems(v);
          const pendingCount = pending.skills.length + pending.claims.length;
          const needsConfirm = pendingCount > 0 && v.status !== "CONFIRMED";
          return (
            <div
              key={v.id}
              className={`rounded-xl border transition ${
                selected.includes(v.id) ? "border-brand-400 bg-zinc-100" : "border-zinc-200 bg-white"
              }`}
            >
              <div className="flex items-center justify-between p-4">
                <label className="flex cursor-pointer items-center gap-3">
                  <input
                    type="checkbox"
                    checked={selected.includes(v.id)}
                    onChange={() => toggleSelect(v.id)}
                    className="h-4 w-4"
                  />
                  <div>
                    <p className="flex flex-wrap items-center gap-2 font-medium text-zinc-800">
                      {v.versionName}
                      {v.targetJob && (
                        <span className="rounded bg-zinc-100 px-1.5 py-0.5 text-xs text-zinc-500">
                          {v.targetJob}
                        </span>
                      )}
                      {v.status === "CONFIRMED" ? (
                        <span className="rounded bg-emerald-100 px-1.5 py-0.5 text-xs text-emerald-700">
                          ✓ 已确认
                        </span>
                      ) : needsConfirm ? (
                        <span className="rounded bg-amber-100 px-1.5 py-0.5 text-xs text-amber-700">
                          待确认 {pendingCount} 项
                        </span>
                      ) : (
                        <span className="rounded bg-zinc-100 px-1.5 py-0.5 text-xs text-zinc-500">
                          草稿
                        </span>
                      )}
                    </p>
                    <p className="mt-0.5 text-xs text-zinc-400">
                      v{v.id} · {v.createTime?.slice(0, 19).replace("T", " ")} ·{" "}
                      {v.optimizedContent?.length ?? 0} 字
                      {v.matchScore != null && ` · 匹配度 ${Math.round(v.matchScore)}`}
                    </p>
                  </div>
                </label>
                <div className="flex shrink-0 items-center gap-2">
                  {needsConfirm && (
                    <button
                      onClick={() => setConfirming({ version: v, decisions: {} })}
                      className="rounded border border-amber-300 bg-amber-50 px-2 py-1 text-xs font-medium text-amber-700 hover:bg-amber-100"
                    >
                      逐条确认
                    </button>
                  )}
                  <button
                    onClick={() => handleExport(v, "pdf")}
                    disabled={needsConfirm}
                    title={needsConfirm ? "请先确认 AI 的改动再导出" : "导出 PDF"}
                    className="rounded border border-zinc-200 px-2 py-1 text-xs text-zinc-500 hover:bg-zinc-100 disabled:cursor-not-allowed disabled:opacity-40"
                  >
                    导出 PDF
                  </button>
                  <button
                    onClick={() => handleDelete(v.id)}
                    className="rounded border border-zinc-200 px-2 py-1 text-xs text-zinc-400 hover:bg-red-50 hover:text-red-500"
                  >
                    删除
                  </button>
                </div>
              </div>

              {confirming?.version.id === v.id && (
                <VersionConfirmPanel
                  version={v}
                  decisions={confirming.decisions}
                  busy={busy}
                  onDecide={(key, action) =>
                    setConfirming((prev) =>
                      prev ? { ...prev, decisions: { ...prev.decisions, [key]: action } } : prev,
                    )
                  }
                  onQuickAll={() =>
                    setConfirming((prev) => {
                      if (!prev) return prev;
                      const all = parsePendingItems(prev.version);
                      const decisions: Record<string, "ACCEPT" | "REMOVE"> = {};
                      all.skills.forEach((s) => (decisions[`SKILL:${s.skill}`] = "ACCEPT"));
                      all.claims.forEach((c) => (decisions[`CLAIM:${c.claim}`] = "ACCEPT"));
                      return { ...prev, decisions };
                    })
                  }
                  onSubmit={handleConfirm}
                  onCancel={() => setConfirming(null)}
                />
              )}
            </div>
          );
        })}
      </div>

      {/* Diff 结果 */}
      {diffPair && (
        <div className="mt-6">
          <div className="mb-2 flex items-center justify-between">
            <h2 className="text-sm font-semibold text-zinc-700">
              对比：{diffPair[0].versionName}（旧） {diffPair[1].versionName}（新）
            </h2>
            <button onClick={() => setDiffPair(null)} className="text-xs text-zinc-400 hover:text-zinc-600">
              关闭
            </button>
          </div>
          <TextDiff oldText={diffPair[0].optimizedContent} newText={diffPair[1].optimizedContent} />
        </div>
      )}
    </main>
  );
}

/** 解析版本里的待确认项快照；解析失败就当没有——后端有兜底，前端不因此白屏 */
function parsePendingItems(v: ResumeVersion): { skills: AddedSkill[]; claims: Overstatement[] } {
  if (!v.pendingJson) return { skills: [], claims: [] };
  try {
    const raw = JSON.parse(v.pendingJson) as { skills?: AddedSkill[]; claims?: Overstatement[] };
    return { skills: raw.skills ?? [], claims: raw.claims ?? [] };
  } catch {
    return { skills: [], claims: [] };
  }
}

/**
 * 逐条确认面板（Human-in-the-loop 的落地）。
 *
 * <p>为什么必须逐条：AI 改写时可能把"有经验"写成"已落地"，或者补上原文没有的技能词。
 * 这些内容一旦写进简历就会被面试官顺着问下去，所以必须由本人拍板——
 * 全部处置完（保留 / 我删掉）后端才会把版本置为 CONFIRMED，之后才能导出、投递。
 */
function VersionConfirmPanel({
  version,
  decisions,
  busy,
  onDecide,
  onQuickAll,
  onSubmit,
  onCancel,
}: {
  version: ResumeVersion;
  decisions: Record<string, "ACCEPT" | "REMOVE">;
  busy: boolean;
  onDecide: (key: string, action: "ACCEPT" | "REMOVE") => void;
  onQuickAll: () => void;
  onSubmit: () => void;
  onCancel: () => void;
}) {
  const pending = parsePendingItems(version);
  const total = pending.skills.length + pending.claims.length;
  const decided = Object.keys(decisions).length;

  return (
    <div className="border-t border-zinc-200 bg-zinc-50 p-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <p className="text-xs font-medium text-zinc-700">AI 改动确认（{decided}/{total}）</p>
        <p className="text-[11px] text-zinc-400">全部处置后才能确认，确认后该版本才可导出/投递</p>
      </div>

      <div className="mt-3 space-y-2">
        {pending.skills.map((s) => (
          <ConfirmRow
            key={`SKILL:${s.skill}`}
            itemKey={`SKILL:${s.skill}`}
            tag="无依据技能"
            tone="amber"
            label={s.skill}
            hint={s.evidence ? `依据：${s.evidence}` : "原文里找不到对应依据"}
            action={decisions[`SKILL:${s.skill}`]}
            onDecide={onDecide}
          />
        ))}
        {pending.claims.map((c) => (
          <ConfirmRow
            key={`CLAIM:${c.claim}`}
            itemKey={`CLAIM:${c.claim}`}
            tag="疑似夸大"
            tone="rose"
            label={c.claim}
            hint={c.original ? `原文：${c.original}` : c.reason || "比原文说得更重"}
            action={decisions[`CLAIM:${c.claim}`]}
            onDecide={onDecide}
          />
        ))}
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-2">
        <button
          onClick={onQuickAll}
          className="rounded border border-zinc-200 bg-white px-3 py-1.5 text-xs text-zinc-600 hover:bg-zinc-100"
        >
          全部保留
        </button>
        <button
          onClick={onSubmit}
          disabled={decided < total || busy}
          className="rounded bg-zinc-900 px-3 py-1.5 text-xs font-medium text-white hover:bg-zinc-800 disabled:cursor-not-allowed disabled:opacity-40"
        >
          {busy ? "提交中…" : "提交确认"}
        </button>
        <button onClick={onCancel} className="text-xs text-zinc-500 hover:underline">
          取消
        </button>
        {decided < total && (
          <span className="text-[11px] text-zinc-400">还有 {total - decided} 项未处置</span>
        )}
      </div>
    </div>
  );
}

function ConfirmRow({
  itemKey,
  tag,
  tone,
  label,
  hint,
  action,
  onDecide,
}: {
  itemKey: string;
  tag: string;
  tone: "amber" | "rose";
  label: string;
  hint: string;
  action?: "ACCEPT" | "REMOVE";
  onDecide: (key: string, action: "ACCEPT" | "REMOVE") => void;
}) {
  const tagClass =
    tone === "amber" ? "bg-amber-100 text-amber-700" : "bg-rose-100 text-rose-700";
  return (
    <div className="flex flex-wrap items-start justify-between gap-2 rounded-lg border border-zinc-200 bg-white p-2.5">
      <div className="min-w-0 flex-1">
        <p className="flex items-center gap-2 text-xs font-medium text-zinc-700">
          <span className={`rounded px-1.5 py-0.5 text-[10px] ${tagClass}`}>{tag}</span>
          <span className="truncate">{label}</span>
        </p>
        <p className="mt-0.5 text-[11px] leading-relaxed text-zinc-400">
          {hint.length > 120 ? `${hint.slice(0, 120)}…` : hint}
        </p>
      </div>
      <div className="flex shrink-0 items-center gap-1.5">
        <button
          onClick={() => onDecide(itemKey, "ACCEPT")}
          className={`rounded border px-2 py-1 text-xs ${
            action === "ACCEPT"
              ? "border-emerald-400 bg-emerald-50 text-emerald-700"
              : "border-zinc-200 text-zinc-500 hover:bg-zinc-50"
          }`}
        >
          保留
        </button>
        <button
          onClick={() => onDecide(itemKey, "REMOVE")}
          className={`rounded border px-2 py-1 text-xs ${
            action === "REMOVE"
              ? "border-rose-400 bg-rose-50 text-rose-700"
              : "border-zinc-200 text-zinc-500 hover:bg-zinc-50"
          }`}
        >
          我删掉
        </button>
      </div>
    </div>
  );
}
