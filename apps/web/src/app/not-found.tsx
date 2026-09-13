import Link from "next/link";

export default function NotFound() {
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
        404
      </span>
      <h1 className="mt-5 text-3xl font-semibold tracking-tight text-zinc-900">
        这个页面不存在
      </h1>
      <p className="mt-2 max-w-[44ch] text-sm leading-relaxed text-zinc-500">
        链接可能已失效，或者地址输入有误。
      </p>
      <div className="mt-7 flex flex-wrap items-center justify-center gap-3">
        <Link
          href="/"
          className="rounded-xl bg-zinc-900 px-5 py-2.5 text-sm font-medium text-white transition hover:bg-zinc-800 active:translate-y-px"
        >
          返回首页
        </Link>
        <Link
          href="/resumes"
          className="rounded-xl border border-zinc-300 bg-white px-5 py-2.5 text-sm font-medium text-zinc-700 transition hover:bg-zinc-50"
        >
          查看我的简历
        </Link>
      </div>
    </main>
  );
}
