"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Send } from "lucide-react";
import { api } from "@/lib/api";
import ListSkeleton from "@/components/ListSkeleton";
import PageHeader from "@/components/PageHeader";
import {
  APP_STATUS,
  type ApplicationRecord,
  type AppStatus,
  type Job,
  type Resume,
  type ResumeVersion,
} from "@/lib/types";

const STATUS_COLORS: Record<string, string> = {
  pending: "bg-zinc-100 text-zinc-600",
  interviewing: "bg-zinc-200 text-zinc-900",
  rejected: "bg-red-100 text-red-600",
  no_response: "bg-amber-100 text-amber-700",
  accepted: "bg-green-100 text-green-700",
};

type VersionOption = ResumeVersion & { resumeName: string };

function localToday() {
  const now = new Date();
  const offset = now.getTimezoneOffset() * 60_000;
  return new Date(now.getTime() - offset).toISOString().slice(0, 10);
}

export default function ApplicationsPage() {
  const [apps, setApps] = useState<ApplicationRecord[]>([]);
  const [jobs, setJobs] = useState<Job[]>([]);
  const [versionOptions, setVersionOptions] = useState<VersionOption[]>([]);
  const [loading, setLoading] = useState(true);
  const [optionsLoading, setOptionsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({
    resumeVersionId: "",
    jobId: "",
    appliedAt: localToday(),
    channel: "",
    status: "pending" as AppStatus,
    notes: "",
  });
  const [saving, setSaving] = useState(false);

  function load() {
    setLoading(true);
    api<ApplicationRecord[]>("/api/v1/application")
      .then(setApps)
      .catch((e) => setError(e instanceof Error ? e.message : "加载失败"))
      .finally(() => setLoading(false));
  }

  function loadOptions() {
    setOptionsLoading(true);
    Promise.all([
      api<Job[]>("/api/v1/job"),
      api<Resume[]>("/api/v1/resume"),
    ])
      .then(async ([jobList, resumes]) => {
        const versionGroups = await Promise.all(
          resumes.map(async (resume) => {
            const versions = await api<ResumeVersion[]>(
              `/api/v1/version?resumeId=${resume.id}`,
            );
            return versions
              .filter((version) => version.status === "CONFIRMED" || !version.pendingJson)
              .map((version) => ({ ...version, resumeName: resume.fileName }));
          }),
        );
        setJobs(jobList);
        setVersionOptions(versionGroups.flat());
      })
      .catch((e) => setError(e instanceof Error ? e.message : "加载投递选项失败"))
      .finally(() => setOptionsLoading(false));
  }

  useEffect(() => {
    load();
    loadOptions();
  }, []);

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await api("/api/v1/application", {
        method: "POST",
        body: JSON.stringify({
          resumeVersionId: form.resumeVersionId ? Number(form.resumeVersionId) : null,
          jobId: form.jobId ? Number(form.jobId) : null,
          appliedAt: form.appliedAt || null,
          channel: form.channel || null,
          status: form.status,
          notes: form.notes || null,
        }),
      });
      setShowForm(false);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "创建失败");
    } finally {
      setSaving(false);
    }
  }

  async function handleStatusChange(id: number, status: string) {
    setError(null);
    try {
      await api(`/api/v1/application/${id}`, { method: "PUT", body: JSON.stringify({ status }) });
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "更新失败");
    }
  }

  async function handleDelete(id: number) {
    if (!confirm("确认删除这条投递记录？")) return;
    setError(null);
    try {
      await api(`/api/v1/application/${id}`, { method: "DELETE" });
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "删除失败");
    }
  }

  return (
    <main className="mx-auto max-w-4xl p-6">
      <PageHeader
        title="投递管理"
        description="跟踪每一次投递与面试进度，按状态看板式流转"
        action={
          <button
            onClick={() => setShowForm(!showForm)}
            className="rounded-xl bg-zinc-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-zinc-800"
          >
            {showForm ? "取消" : "新增投递"}
          </button>
        }
      />

      {error && (
        <div className="mb-4 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-600">
          {error}
        </div>
      )}

      {showForm && (
        <form onSubmit={handleCreate} className="mb-6 rounded-2xl border border-zinc-200 bg-white p-6 shadow-card">
          <h2 className="mb-4 font-semibold text-zinc-800">新增投递记录</h2>
          <div className="grid gap-3 sm:grid-cols-2">
            <input
              type="date"
              aria-label="投递日期"
              required
              value={form.appliedAt}
              onChange={(e) => setForm({ ...form, appliedAt: e.target.value })}
              className="rounded-lg border border-zinc-300 px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-brand-500/25 focus-visible:ring-offset-1 focus:border-zinc-900"
            />
            <input
              aria-label="投递渠道"
              value={form.channel}
              onChange={(e) => setForm({ ...form, channel: e.target.value })}
              placeholder="投递渠道（如 BOSS直聘）"
              className="rounded-lg border border-zinc-300 px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-brand-500/25 focus-visible:ring-offset-1 focus:border-zinc-900"
            />
            <select
              aria-label="优化版本"
              required
              disabled={optionsLoading}
              value={form.resumeVersionId}
              onChange={(e) => setForm({ ...form, resumeVersionId: e.target.value })}
              className="rounded-lg border border-zinc-300 px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-brand-500/25 focus-visible:ring-offset-1 focus:border-zinc-900"
            >
              <option value="">
                {optionsLoading ? "正在加载版本…" : "请选择已确认版本"}
              </option>
              {versionOptions.map((version) => (
                <option key={version.id} value={version.id}>
                  {version.versionName} · {version.resumeName}
                </option>
              ))}
            </select>
            <select
              aria-label="目标岗位"
              required
              disabled={optionsLoading}
              value={form.jobId}
              onChange={(e) => setForm({ ...form, jobId: e.target.value })}
              className="rounded-lg border border-zinc-300 px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-brand-500/25 focus-visible:ring-offset-1 focus:border-zinc-900"
            >
              <option value="">
                {optionsLoading ? "正在加载岗位…" : "请选择目标岗位"}
              </option>
              {jobs.map((job) => (
                <option key={job.id} value={job.id}>
                  {job.company} · {job.title}
                  {job.location ? ` · ${job.location}` : ""}
                </option>
              ))}
            </select>
            <select
              aria-label="投递状态"
              value={form.status}
              onChange={(e) => setForm({ ...form, status: e.target.value as AppStatus })}
              className="rounded-lg border border-zinc-300 px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-brand-500/25 focus-visible:ring-offset-1 focus:border-zinc-900"
            >
              {Object.entries(APP_STATUS).map(([k, v]) => (
                <option key={k} value={k}>{v}</option>
              ))}
            </select>
            <input
              aria-label="备注"
              value={form.notes}
              onChange={(e) => setForm({ ...form, notes: e.target.value })}
              placeholder="备注"
              className="rounded-lg border border-zinc-300 px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-brand-500/25 focus-visible:ring-offset-1 focus:border-zinc-900"
            />
          </div>
          {!optionsLoading && versionOptions.length === 0 && (
            <p className="mt-3 text-xs text-amber-600">
              当前没有已确认的简历版本，请先完成定向优化和人工确认。
            </p>
          )}
          <button type="submit" disabled={saving || optionsLoading} className="mt-4 rounded-lg bg-zinc-900 px-6 py-2 text-sm font-medium text-white hover:bg-zinc-800 disabled:opacity-50">
            {saving ? "保存中…" : "保存"}
          </button>
        </form>
      )}

      {loading && <ListSkeleton rows={3} />}

      {!loading && apps.length === 0 && (
        <div className="rounded-2xl border border-dashed border-zinc-300 bg-white p-14 text-center">
          <Send className="mx-auto h-7 w-7 text-zinc-400" strokeWidth={1.75} />
          <h2 className="mt-4 text-base font-semibold text-zinc-900">还没有投递记录</h2>
          <p className="mx-auto mt-1.5 max-w-[42ch] text-sm leading-relaxed text-zinc-500">
            先选一个目标岗位做匹配与定向优化，投递之后在这里跟踪每一家的进度。
          </p>
          <div className="mt-6 flex flex-wrap items-center justify-center gap-3">
            <Link
              href="/match"
              className="rounded-xl bg-zinc-900 px-5 py-2.5 text-sm font-medium text-white transition hover:bg-zinc-800"
            >
              去匹配岗位
            </Link>
            <Link
              href="/jobs"
              className="rounded-xl border border-zinc-300 bg-white px-5 py-2.5 text-sm font-medium text-zinc-700 transition hover:bg-zinc-50"
            >
              浏览岗位库
            </Link>
          </div>
        </div>
      )}

      <div className="space-y-3">
        {apps.map((a) => (
          <div key={a.id} className="rounded-2xl border border-zinc-200 bg-white p-5 shadow-card">
            <div className="flex items-center justify-between">
              <div>
                <p className="font-medium text-zinc-800">
                  #{a.id}
                  {a.channel && <span className="ml-2 text-sm text-zinc-500">{a.channel}</span>}
                  {a.jobId && (
                    <span className="ml-2 text-xs text-zinc-400">
                      {jobs.find((job) => job.id === a.jobId)?.title || `岗位 #${a.jobId}`}
                    </span>
                  )}
                  {a.resumeVersionId && (
                    <span className="ml-2 text-xs text-zinc-400">
                      {versionOptions.find((version) => version.id === a.resumeVersionId)?.versionName
                        || `版本 #${a.resumeVersionId}`}
                    </span>
                  )}
                </p>
                <p className="mt-1 text-xs text-zinc-400">
                  {a.appliedAt || "未填日期"} {a.notes ? `· ${a.notes}` : ""}
                </p>
              </div>
              <div className="flex items-center gap-2">
                <select
                  value={a.status}
                  onChange={(e) => handleStatusChange(a.id, e.target.value)}
                  className={`rounded-full px-3 py-1 text-xs font-medium outline-none focus-visible:ring-2 focus-visible:ring-brand-500/25 focus-visible:ring-offset-1 ${STATUS_COLORS[a.status] || STATUS_COLORS.pending}`}
                >
                  {Object.entries(APP_STATUS).map(([k, v]) => (
                    <option key={k} value={k}>{v}</option>
                  ))}
                </select>
                <button
                  onClick={() => handleDelete(a.id)}
                  className="rounded border border-zinc-200 px-2 py-1 text-xs text-zinc-400 hover:bg-red-50 hover:text-red-500"
                >
                  删除
                </button>
              </div>
            </div>
          </div>
        ))}
      </div>
    </main>
  );
}
