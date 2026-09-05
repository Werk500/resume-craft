"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { Job, JdAnalysis } from "@/lib/types";
import JobRadar from "@/components/JobRadar";

export default function JobsPage() {
  const [jobs, setJobs] = useState<Job[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ company: "", title: "", department: "", location: "", salaryRange: "", description: "", requirements: "" });
  const [saving, setSaving] = useState(false);
  // JD 解析：每岗位一份结果 + 谁在加载
  const [analysis, setAnalysis] = useState<Record<number, JdAnalysis>>({});
  const [analyzingId, setAnalyzingId] = useState<number | null>(null);
  const [expandedId, setExpandedId] = useState<number | null>(null);

  function load() {
    setLoading(true);
    api<Job[]>("/api/v1/job")
      .then(setJobs)
      .catch((e) => setError(e instanceof Error ? e.message : "加载失败"))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await api("/api/v1/job", { method: "POST", body: JSON.stringify(form) });
      setShowForm(false);
      setForm({ company: "", title: "", department: "", location: "", salaryRange: "", description: "", requirements: "" });
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "创建失败");
    } finally {
      setSaving(false);
    }
  }

  /** 展开/收起 JD 解析；未解析过则先调 AI */
  async function toggleAnalyze(job: Job) {
    if (expandedId === job.id) {
      setExpandedId(null);
      return;
    }
    setExpandedId(job.id);
    if (!analysis[job.id]) {
      setAnalyzingId(job.id);
      setError(null);
      try {
        const result = await api<JdAnalysis>(`/api/v1/job/${job.id}/analyze`);
        setAnalysis((prev) => ({ ...prev, [job.id]: result }));
      } catch (e) {
        setError(e instanceof Error ? e.message : "JD 解析失败");
      } finally {
        setAnalyzingId(null);
      }
    }
  }

  return (
    <main className="mx-auto max-w-4xl p-6">
      <header className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">岗位管理</h1>
          <p className="mt-1 text-sm text-slate-500">录入 JD，AI 解析岗位要求 + 人岗匹配</p>
        </div>
        <button
          onClick={() => setShowForm(!showForm)}
          className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          {showForm ? "取消" : "+ 录入岗位"}
        </button>
      </header>

      {error && (
        <div className="mb-4 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-600">
          {error}
        </div>
      )}

      {showForm && (
        <form onSubmit={handleCreate} className="mb-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="mb-4 font-semibold text-slate-800">录入新岗位</h2>
          <div className="grid gap-3 sm:grid-cols-2">
            <input required value={form.company} onChange={(e) => setForm({ ...form, company: e.target.value })} placeholder="公司名称 *" className="rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500" />
            <input required value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder="岗位名称 *" className="rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500" />
            <input value={form.department} onChange={(e) => setForm({ ...form, department: e.target.value })} placeholder="部门" className="rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500" />
            <input value={form.location} onChange={(e) => setForm({ ...form, location: e.target.value })} placeholder="工作地点" className="rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500" />
            <input value={form.salaryRange} onChange={(e) => setForm({ ...form, salaryRange: e.target.value })} placeholder="薪资范围" className="rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500" />
          </div>
          <textarea value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} placeholder="岗位职责描述（JD 全文）" rows={4} className="mt-3 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500" />
          <textarea value={form.requirements} onChange={(e) => setForm({ ...form, requirements: e.target.value })} placeholder="任职要求" rows={3} className="mt-3 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500" />
          <button type="submit" disabled={saving} className="mt-4 rounded-lg bg-blue-600 px-6 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50">
            {saving ? "保存中…" : "保存"}
          </button>
        </form>
      )}

      {loading && <p className="py-10 text-center text-slate-400">加载中…</p>}

      {!loading && jobs.length === 0 && (
        <div className="rounded-2xl border-2 border-dashed border-slate-300 bg-white p-16 text-center">
          <p className="text-slate-500">还没有岗位，录入第一份 JD 开始匹配</p>
        </div>
      )}

      <div className="space-y-3">
        {jobs.map((j) => (
          <div key={j.id} className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="flex items-start justify-between">
              <div>
                <p className="font-semibold text-slate-800">
                  {j.title} <span className="ml-1 text-sm font-normal text-slate-400">{j.company}</span>
                </p>
                <p className="mt-1 text-xs text-slate-500">
                  {[j.department, j.location, j.salaryRange].filter(Boolean).join(" · ") || "—"}
                </p>
              </div>
              <button
                onClick={() => toggleAnalyze(j)}
                className="rounded-full bg-blue-50 px-3 py-1 text-xs font-medium text-blue-700 hover:bg-blue-100"
              >
                {expandedId === j.id ? "收起解析" : "🤖 JD 解析"}
              </button>
            </div>
            {j.description && (
              <p className="mt-3 line-clamp-2 text-sm text-slate-500">{j.description}</p>
            )}
            {j.requirements && (
              <p className="mt-2 line-clamp-2 text-xs text-slate-400">要求：{j.requirements}</p>
            )}

            {expandedId === j.id && (
              <div className="mt-4 border-t border-slate-100 pt-4">
                {analyzingId === j.id ? (
                  <p className="animate-pulse py-6 text-center text-sm text-slate-400">
                    AI 解析 JD 中，约 10~20 秒…
                  </p>
                ) : analysis[j.id] ? (
                  <AnalysisPanel data={analysis[j.id]} />
                ) : null}
              </div>
            )}
          </div>
        ))}
      </div>
    </main>
  );
}

/** JD 解析结果展示：总结 + 雷达 + 硬性/加分/隐性要求 + 技能 */
function AnalysisPanel({ data }: { data: JdAnalysis }) {
  return (
    <div className="space-y-4">
      {data.summary && (
        <p className="rounded-lg bg-blue-50 px-3 py-2 text-sm text-slate-600">
          📋 {data.summary}
        </p>
      )}

      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <JobRadar data={data.radar} />
        </div>
        <div className="space-y-3">
          <TagGroup title="🔴 硬性要求" items={data.hardRequirements} color="bg-red-50 text-red-600" />
          <TagGroup title="🟢 加分项" items={data.bonusPoints} color="bg-green-50 text-green-600" />
          <TagGroup title="⚪ 隐性要求（AI 推断）" items={data.hiddenRequirements} color="bg-slate-100 text-slate-500" />
          {data.skills?.length > 0 && (
            <div>
              <p className="mb-1 text-xs font-medium text-slate-400">技能清单</p>
              <div className="flex flex-wrap gap-1">
                {data.skills.map((s) => (
                  <span key={s} className="rounded bg-blue-50 px-1.5 py-0.5 text-xs text-blue-600">
                    {s}
                  </span>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function TagGroup({ title, items, color }: { title: string; items: string[] | null; color: string }) {
  if (!items || items.length === 0) return null;
  return (
    <div>
      <p className="mb-1 text-xs font-medium text-slate-400">{title}</p>
      <div className="flex flex-wrap gap-1">
        {items.map((t) => (
          <span key={t} className={`rounded px-1.5 py-0.5 text-xs ${color}`}>
            {t}
          </span>
        ))}
      </div>
    </div>
  );
}
