"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { api } from "@/lib/api";
import type { Resume, ResumeVersion } from "@/lib/types";
import TextDiff from "@/components/TextDiff";

export default function VersionsPage({ params }: { params: { id: string } }) {
  const resumeId = params.id;

  const [versions, setVersions] = useState<ResumeVersion[]>([]);
  const [resume, setResume] = useState<Resume | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<number[]>([]); // 最多选 2 个对比
  const [diffPair, setDiffPair] = useState<[ResumeVersion, ResumeVersion] | null>(null);

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
        {versions.map((v) => (
          <div
            key={v.id}
            className={`flex items-center justify-between rounded-xl border p-4 transition ${
              selected.includes(v.id) ? "border-brand-400 bg-zinc-100" : "border-zinc-200 bg-white"
            }`}
          >
            <label className="flex cursor-pointer items-center gap-3">
              <input
                type="checkbox"
                checked={selected.includes(v.id)}
                onChange={() => toggleSelect(v.id)}
                className="h-4 w-4"
              />
              <div>
                <p className="font-medium text-zinc-800">
                  {v.versionName}
                  {v.targetJob && (
                    <span className="ml-2 rounded bg-zinc-100 px-1.5 py-0.5 text-xs text-zinc-500">
                      {v.targetJob}
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
            <button
              onClick={() => handleDelete(v.id)}
              className="rounded border border-zinc-200 px-2 py-1 text-xs text-zinc-400 hover:bg-red-50 hover:text-red-500"
            >
              删除
            </button>
          </div>
        ))}
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
