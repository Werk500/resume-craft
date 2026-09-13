import Link from "next/link";

export default function NotFound() {
  return (
    <main className="mx-auto flex min-h-[60vh] max-w-xl flex-col items-center justify-center px-6 text-center">
      <p className="text-5xl font-semibold tracking-tight text-slate-300">404</p>
      <h1 className="mt-3 text-xl font-semibold text-slate-900">没有找到这个页面</h1>
      <p className="mt-2 max-w-[46ch] text-sm leading-relaxed text-slate-500">
        链接可能已失效，或者你输入了错误的地址。
      </p>
      <div className="mt-6 flex flex-wrap items-center justify-center gap-3">
        <Link
          href="/"
          className="rounded-lg bg-slate-900 px-5 py-2.5 text-sm font-medium text-white transition hover:bg-slate-700"
        >
          返回首页
        </Link>
        <Link
          href="/resumes"
          className="rounded-lg border border-slate-200 px-5 py-2.5 text-sm font-medium text-slate-600 transition hover:bg-slate-100"
        >
          查看我的简历
        </Link>
      </div>
    </main>
  );
}
