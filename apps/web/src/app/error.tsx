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
      <p className="text-xs font-medium uppercase tracking-[0.2em] text-zinc-400">Error</p>
      <h1 className="mt-3 text-2xl font-semibold tracking-tight text-zinc-900">页面加载失败</h1>
      <p className="mt-2 max-w-[46ch] text-sm leading-relaxed text-zinc-500">
        {error.message || "请检查网络连接后重试。"}
      </p>
      {error.digest && (
        <p className="mt-1 text-xs text-zinc-400">错误编号 {error.digest}</p>
      )}
      <div className="mt-6 flex flex-wrap items-center justify-center gap-3">
        <button
          onClick={reset}
          className="rounded-lg bg-zinc-900 px-5 py-2.5 text-sm font-medium text-white transition hover:bg-zinc-700 active:tranzinc-y-px"
        >
          重试
        </button>
        <a
          href="/"
          className="rounded-lg border border-zinc-200 px-5 py-2.5 text-sm font-medium text-zinc-600 transition hover:bg-zinc-100"
        >
          返回首页
        </a>
      </div>
    </main>
  );
}
