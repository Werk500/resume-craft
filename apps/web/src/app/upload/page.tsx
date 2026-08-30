"use client";

import { useState, useRef } from "react";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8088";

interface ResumeResponse {
  id: number;
  fileName: string;
  fileType: string;
  parsedName: string | null;
  parsedEmail: string | null;
  parsedPhone: string | null;
  rawText: string;
  createdAt: string;
}

export default function UploadPage() {
  const [dragging, setDragging] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [result, setResult] = useState<ResumeResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  async function handleUpload(file: File) {
    setUploading(true);
    setError(null);
    setResult(null);
    try {
      const formData = new FormData();
      formData.append("file", file);
      const res = await fetch(`${API_BASE}/api/v1/resume/upload`, {
        method: "POST",
        body: formData,
      });
      const json = await res.json();
      if (!res.ok || json.code !== 200) {
        throw new Error(json.message || "上传失败");
      }
      setResult(json.data as ResumeResponse);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "未知错误");
    } finally {
      setUploading(false);
    }
  }

  // Drag & Drop handlers
  function onDragOver(e: React.DragEvent) {
    e.preventDefault();
    setDragging(true);
  }
  function onDragLeave() {
    setDragging(false);
  }
  function onDrop(e: React.DragEvent) {
    e.preventDefault();
    setDragging(false);
    const f = e.dataTransfer.files?.[0];
    if (f) handleUpload(f);
  }

  function onFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const f = e.target.files?.[0];
    if (f) handleUpload(f);
  }

  return (
    <main className="min-h-screen bg-gradient-to-b from-slate-50 to-slate-100 p-6 sm:p-10">
      <div className="mx-auto max-w-2xl">
        {/* Header */}
        <header className="mb-8 text-center">
          <h1 className="text-3xl font-bold tracking-tight text-slate-900">上传简历</h1>
          <p className="mt-2 text-sm text-slate-500">支持 PDF、Word（暂不支持图片 OCR）</p>
        </header>

        {/* Upload Area */}
        <section
          className={`rounded-2xl border-2 border-dashed p-12 text-center transition
            ${dragging ? "border-blue-400 bg-blue-50" : "border-slate-300 bg-white"}
          `}
          onDragOver={onDragOver}
          onDragLeave={onDragLeave}
          onDrop={onDrop}
          onClick={() => fileInputRef.current?.click()}
        >
          <input
            ref={fileInputRef}
            type="file"
            accept=".pdf,.docx,.doc,.png,.jpg,.jpeg"
            onChange={onFileChange}
            className="hidden"
          />
          {uploading ? (
            <div className="animate-pulse space-y-3">
              <div className="mx-auto h-8 w-8 rounded-full bg-blue-200" />
              <p className="text-slate-600">正在解析简历…</p>
            </div>
          ) : (
            <>
              <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-blue-100">
                <svg className="h-8 w-8 text-blue-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
                </svg>
              </div>
              <p className="text-lg font-medium text-slate-700">拖拽文件到此处，或点击选择</p>
              <p className="mt-1 text-xs text-slate-400">PDF / Word / 图片，最大 10MB</p>
            </>
          )}
        </section>

        {/* Error */}
        {error && (
          <div className="mt-4 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
            ⚠️ {error}
          </div>
        )}

        {/* Result Card */}
        {result && (
          <div className="mt-6 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
            <div className="bg-gradient-to-r from-blue-500 to-blue-600 px-6 py-4">
              <h2 className="text-lg font-semibold text-white">📄 解析成功</h2>
              <p className="text-xs text-blue-100">id={result.id} · {result.fileType.toUpperCase()}</p>
              <a
                href={`/resume/${result.id}`}
                className="mt-3 inline-block rounded-full bg-white/20 px-4 py-1.5 text-sm font-medium text-white hover:bg-white/30"
              >
                🤖 去 AI 诊断 & 优化 →
              </a>
            </div>

            {/* Meta info grid */}
            <div className="grid grid-cols-2 gap-4 border-b border-slate-100 p-6">
              <MetaBlock label="文件名" value={result.fileName} />
              <MetaBlock label="文件类型" value={result.fileType.toUpperCase()} />
              <MetaBlock label="邮箱" value={result.parsedEmail ?? "未识别"} highlight={!!result.parsedEmail} />
              <MetaBlock label="电话" value={result.parsedPhone ?? "未识别"} highlight={!!result.parsedPhone} />
            </div>

            {/* Raw text preview */}
            <div className="px-6 pb-6 pt-2">
              <label className="mb-2 block text-sm font-medium text-slate-700">原文内容预览</label>
              <pre className="whitespace-pre-wrap rounded-xl bg-slate-50 p-4 text-sm text-slate-700 leading-relaxed">
                {result.rawText || "(空文本)"}
              </pre>
            </div>
          </div>
        )}

        {/* Back link */}
        <div className="mt-8 text-center">
          <a href="/" className="text-sm text-blue-600 hover:underline">← 返回首页</a>
        </div>
      </div>
    </main>
  );
}

function MetaBlock({ label, value, highlight }: { label: string; value: string; highlight?: boolean }) {
  return (
    <div>
      <span className="text-xs text-slate-400">{label}</span>
      <p className={`mt-1 text-sm font-medium ${highlight ? "text-green-700" : "text-slate-600"}`}>{value}</p>
    </div>
  );
}