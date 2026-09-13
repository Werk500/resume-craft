import Link from "next/link";
import { GitCompare, ScanText, Target } from "lucide-react";

const points = [
  { icon: Target, title: "可解释匹配", desc: "关键词 40% + 语义 40% + 硬性条件 20%" },
  { icon: ScanText, title: "OCR 人工核对", desc: "低置信度扫描件确认后才参与评分" },
  { icon: GitCompare, title: "版本对比", desc: "行级 Diff 与定向优化提升报告" },
];

interface AuthSplitLayoutProps {
  title: string;
  subtitle?: string;
  children: React.ReactNode;
}

/** 登录 / 注册的统一分屏布局：左侧品牌区（仅宽屏），右侧表单 */
export default function AuthSplitLayout({ title, subtitle, children }: AuthSplitLayoutProps) {
  return (
    <main className="grid min-h-[calc(100dvh-57px)] lg:grid-cols-[1.05fr_1fr]">
      <section className="relative hidden flex-col justify-between overflow-hidden bg-zinc-950 p-12 text-white lg:flex">
        <div
          aria-hidden
          className="pointer-events-none absolute inset-0"
          style={{
            backgroundImage: "radial-gradient(circle at 1px 1px, #ffffff 1px, transparent 0)",
            backgroundSize: "24px 24px",
            opacity: 0.14,
          }}
        />

        <Link
          href="/"
          className="relative flex items-center gap-2 text-sm font-semibold tracking-tight"
        >
          <span className="h-5 w-5 rounded-md bg-white" aria-hidden />
          AI 简历优化
        </Link>

        <div className="relative">
          <h2 className="text-4xl font-semibold leading-[1.1] tracking-tight">
            把简历打磨到
            <br />
            能拿到面试
          </h2>
          <ul className="mt-9 space-y-4">
            {points.map((point) => (
              <li key={point.title} className="flex gap-3">
                <point.icon className="mt-0.5 h-4 w-4 shrink-0 text-brand-400" strokeWidth={1.75} />
                <div>
                  <p className="text-sm font-medium">{point.title}</p>
                  <p className="mt-0.5 text-xs text-zinc-400">{point.desc}</p>
                </div>
              </li>
            ))}
          </ul>
        </div>

        <p className="relative text-xs text-zinc-500">AI 简历设计与优化 · 校招简历工作台</p>
      </section>

      <section className="flex items-center justify-center px-6 py-12">
        <div className="w-full max-w-sm">
          <h1 className="text-2xl font-semibold tracking-tight text-zinc-900">{title}</h1>
          {subtitle && <p className="mt-1.5 text-sm text-zinc-500">{subtitle}</p>}
          <div className="mt-7">{children}</div>
        </div>
      </section>
    </main>
  );
}
