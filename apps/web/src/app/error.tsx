"use client";

export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <main className="relative mx-auto flex min-h-[70vh] max-w-xl flex-col items-center justify-center px-6 text-center">
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 -z-10"
        style={{
          backgroundImage: "radial-gradient(circle at 1px 1px, #d4d4d8 1px, transparent 0)",
          backgroundSize: "22px 22px",
          opacity: 0.45,
        }}
      />
      <span className="rounded-full border border-zinc-200 bg-white px-3 py-1 font-mono text-xs text-zinc-500">
        Error
      </span>
      <h1 className="mt-5 text-3xl font-semibold tracking-tight text-zinc-900">
        页面加载失败
      </h1>
      <p className="mt-2 max-w-[44ch] text-sm leading-relaxed text-zinc-500">
        {error.message || "请检查网络连接后重试。"}
      </p>
      {error.digest && (
        <p className="mt-1 font-mono text-xs text-zinc-400">错误编号 {error.digest}</p>
      )}
      <div className="mt-7 flex flex-wrap items-center justify-center gap-3">
        <button
          onClick={reset}
          className="rounded-xl bg-zinc-900 px-5 py-2.5 text-sm font-medium text-white transition hover:bg-zinc-800 active:translate-y-px"
        >
          重试
        </button>
        <a
          href="/"
          className="rounded-xl border border-zinc-300 bg-white px-5 py-2.5 text-sm font-medium text-zinc-700 transition hover:bg-zinc-50"
        >
          返回首页
        </a>
      </div>
    </main>
  );
}
