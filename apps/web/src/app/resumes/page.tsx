"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { api } from "@/lib/api";
import type { Resume } from "@/lib/types";

export default function ResumesPage() {
  const [resumes, setResumes] = useState<Resume[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<Resume[]>("/api/v1/resume")
      .then(setResumes)
      .catch((e) => setError(e instanceof Error ? e.message : "加载失败"))
      .finally(() => setLoading(false));
  }, []);

  return (
    <main className="mx-auto max-w-4xl p-6">
      <header className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">我的简历</h1>
          <p className="mt-1 text-sm text-slate-500">上传解析、AI 诊断、一键优化</p>
        </div>
        <Link
          href="/upload"
          className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          + 上传简历
        </Link>
      </header>

      {loading && <p className="py-10 text-center text-slate-400">加载中…</p>}
      {error && (
        <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-600">
          {error}
        </div>
      )}

      {!loading && !error && resumes.length === 0 && (
        <div className="rounded-2xl border-2 border-dashed border-slate-300 bg-white p-16 text-center">
          <p className="text-slate-500">还没有简历，点击右上角上传第一份简历</p>
        </div>
      )}

      <div className="space-y-3">
        {resumes.map((r) => (
          <Link
            key={r.id}
            href={`/resume/${r.id}`}
            className="flex items-center justify-between rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition hover:border-blue-300 hover:shadow-md"
          >
            <div className="flex items-center gap-4">
              <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-blue-100 text-lg">
                📄
              </div>
              <div>
                <p className="font-medium text-slate-800">{r.fileName}</p>
                <p className="mt-0.5 text-xs text-slate-500">
                  {r.fileType.toUpperCase()} ·{" "}
                  {r.parsedName ? `${r.parsedName} · ` : ""}
                  {r.parsedEmail || "未识别邮箱"}
                </p>
              </div>
            </div>
            <span className="text-xs text-slate-400">
              {r.createTime ? r.createTime.slice(0, 10) : ""} →
            </span>
          </Link>
        ))}
      </div>
    </main>
  );
}
