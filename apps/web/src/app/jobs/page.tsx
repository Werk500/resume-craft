"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { Job } from "@/lib/types";

export default function JobsPage() {
  const [jobs, setJobs] = useState<Job[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ company: "", title: "", department: "", location: "", salaryRange: "", description: "", requirements: "" });
  const [saving, setSaving] = useState(false);

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

  return (
    <main className="mx-auto max-w-4xl p-6">
      <header className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">岗位管理</h1>
          <p className="mt-1 text-sm text-slate-500">录入 JD，用于 AI 人岗匹配</p>
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
              <span className="rounded-full bg-blue-50 px-2 py-0.5 text-xs text-blue-600">ID: {j.id}</span>
            </div>
            {j.description && (
              <p className="mt-3 line-clamp-2 text-sm text-slate-500">{j.description}</p>
            )}
            {j.requirements && (
              <p className="mt-2 line-clamp-2 text-xs text-slate-400">要求：{j.requirements}</p>
            )}
          </div>
        ))}
      </div>
    </main>
  );
}
