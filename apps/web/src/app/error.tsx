"use client";

export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <main className="mx-auto flex min-h-[60vh] max-w-xl flex-col items-center justify-center px-6 text-center">
      <p className="text-xs font-medium uppercase tracking-[0.2em] text-slate-400">Error</p>
      <h1 className="mt-3 text-2xl font-semibold tracking-tight text-slate-900">页面加载失败</h1>
      <p className="mt-2 max-w-[46ch] text-sm leading-relaxed text-slate-500">
        {error.message || "请检查网络连接后重试。"}
      </p>
      {error.digest && (
        <p className="mt-1 text-xs text-slate-400">错误编号 {error.digest}</p>
      )}
      <div className="mt-6 flex flex-wrap items-center justify-center gap-3">
        <button
          onClick={reset}
          className="rounded-lg bg-slate-900 px-5 py-2.5 text-sm font-medium text-white transition hover:bg-slate-700 active:translate-y-px"
        >
          重试
        </button>
        <a
          href="/"
          className="rounded-lg border border-slate-200 px-5 py-2.5 text-sm font-medium text-slate-600 transition hover:bg-slate-100"
        >
          返回首页
        </a>
      </div>
    </main>
  );
}
