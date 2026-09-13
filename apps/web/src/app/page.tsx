import Link from "next/link";
import {
  ArrowRight,
  Briefcase,
  FileSearch,
  GitCompare,
  MessagesSquare,
  ScanText,
  Send,
  Sparkles,
  Target,
  UploadCloud,
} from "lucide-react";

/** 八个环节，按真实使用流程排序；span 控制 Bento 的非对称网格 */
const steps = [
  {
    no: "01",
    icon: UploadCloud,
    title: "上传简历",
    desc: "支持 PDF / Word / 图片，自动解析全文与联系方式",
    href: "/upload",
    span: "lg:col-span-4",
  },
  {
    no: "02",
    icon: ScanText,
    title: "识别核对",
    desc: "图片走 OCR 并给出分块置信度，低置信度逐块确认后才参与评分",
    href: "/resumes",
    span: "lg:col-span-2",
  },
  {
    no: "03",
    icon: FileSearch,
    title: "AI 诊断",
    desc: "信息完整度 / 表达质量 / 岗位匹配度三维评分",
    href: "/resumes",
    span: "lg:col-span-2",
  },
  {
    no: "04",
    icon: Sparkles,
    title: "优化与逐句精修",
    desc: "保留事实的整份改写，以及 DATA / METHOD / IMPACT 三方向精修",
    href: "/resumes",
    span: "lg:col-span-4",
  },
  {
    no: "05",
    icon: Briefcase,
    title: "选择目标岗位",
    desc: "真实校招 JD 种子库，按公司或关键词检索",
    href: "/jobs",
    span: "lg:col-span-2",
  },
  {
    no: "06",
    icon: Target,
    title: "可解释匹配",
    desc: "关键词 40% + 语义 40% + 硬性条件 20%，输出命中与缺失明细",
    href: "/match",
    span: "lg:col-span-4",
  },
  {
    no: "07",
    icon: GitCompare,
    title: "定向优化与提升报告",
    desc: "按目标 JD 改写并重新匹配，给出优化前后分差与改动原因",
    href: "/match",
    span: "lg:col-span-3",
  },
  {
    no: "08",
    icon: Send,
    title: "版本对比与投递跟踪",
    desc: "行级 Diff 对比任意版本，看板式跟踪投递状态",
    href: "/applications",
    span: "lg:col-span-3",
  },
];

/** Hero 右侧的流程轨道 */
const flow = [
  { label: "上传解析", hint: "PDF / Word / 图片，联系人自动提取" },
  { label: "诊断与优化", hint: "三维评分 + 逐句精修" },
  { label: "岗位匹配", hint: "40 / 40 / 20 可解释评分" },
  { label: "投递跟踪", hint: "版本对比 + 状态流转" },
];

const secondary = [
  { icon: MessagesSquare, label: "AI 对话创建简历", href: "/create" },
  { icon: ScanText, label: "OCR 人工核对", href: "/resumes" },
  { icon: GitCompare, label: "版本 Diff 对比", href: "/resumes" },
];

export default function Home() {
  return (
    <main className="mx-auto max-w-6xl px-6 py-12 sm:py-16">
      {/* Hero：左对齐，右侧流程轨道 */}
      <section className="grid gap-10 lg:grid-cols-[1.05fr_0.95fr] lg:items-center">
        <div>
          <p className="text-xs font-medium uppercase tracking-[0.18em] text-zinc-500">
            校招简历工作台
          </p>
          <h1 className="mt-4 text-4xl font-semibold leading-[1.08] tracking-tight text-zinc-900 md:text-5xl">
            把简历打磨到
            <br />
            能拿到面试
          </h1>
          <p className="mt-5 max-w-[46ch] text-base leading-relaxed text-zinc-600">
            从上传解析到岗位匹配、投递跟踪，八个环节串成一条可解释、可复现的流程。
          </p>

          <div className="mt-7 flex flex-wrap items-center gap-3">
            <Link
              href="/upload"
              className="group inline-flex items-center gap-2 rounded-xl bg-zinc-900 px-5 py-3 text-sm font-medium text-white transition hover:bg-zinc-800 active:tranzinc-y-px"
            >
              上传简历
              <ArrowRight className="h-4 w-4 transition-transform group-hover:tranzinc-x-0.5" />
            </Link>
            <Link
              href="/match"
              className="inline-flex items-center gap-2 rounded-xl border border-zinc-300 bg-white px-5 py-3 text-sm font-medium text-zinc-700 transition hover:border-zinc-400 hover:bg-zinc-50"
            >
              看看匹配引擎
            </Link>
          </div>

          <div className="mt-8 flex flex-wrap gap-x-5 gap-y-2">
            {secondary.map((item) => (
              <Link
                key={item.label}
                href={item.href}
                className="inline-flex items-center gap-1.5 text-sm text-zinc-500 transition hover:text-zinc-900"
              >
                <item.icon className="h-4 w-4 text-zinc-400" strokeWidth={1.75} />
                {item.label}
              </Link>
            ))}
          </div>
        </div>

        <ol className="divide-y divide-zinc-100 rounded-2xl border border-zinc-200 bg-white px-5">
          {flow.map((item, index) => (
            <li key={item.label} className="flex items-center gap-4 py-4">
              <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full border border-zinc-200 font-mono text-[11px] text-zinc-500">
                {index + 1}
              </span>
              <div className="min-w-0">
                <p className="text-sm font-medium text-zinc-900">{item.label}</p>
                <p className="truncate text-xs text-zinc-500">{item.hint}</p>
              </div>
            </li>
          ))}
        </ol>
      </section>

      {/* 流程型 Bento */}
      <section className="mt-16">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h2 className="text-xl font-semibold tracking-tight text-zinc-900">
              八个环节，一条流程
            </h2>
            <p className="mt-1 text-sm text-zinc-500">
              点进任意环节，都能看到它在整条链路里的位置与产物。
            </p>
          </div>
          <Link
            href="/resumes"
            className="text-sm font-medium text-zinc-600 transition hover:text-zinc-900"
          >
            查看我的简历 →
          </Link>
        </div>

        <div className="mt-6 grid gap-4 lg:grid-cols-6">
          {steps.map((step) => (
            <Link
              key={step.no}
              href={step.href}
              className={`group flex flex-col rounded-2xl border border-zinc-200 bg-white p-6 transition duration-200 hover:-tranzinc-y-0.5 hover:border-zinc-300 hover:shadow-card active:tranzinc-y-0 ${step.span}`}
            >
              <div className="flex items-center justify-between">
                <span className="font-mono text-xs text-brand-600">{step.no}</span>
                <step.icon
                  className="h-5 w-5 text-zinc-400 transition group-hover:text-brand-600"
                  strokeWidth={1.75}
                />
              </div>
              <h3 className="mt-4 text-base font-semibold text-zinc-900">{step.title}</h3>
              <p className="mt-1.5 text-sm leading-relaxed text-zinc-500">{step.desc}</p>
              <span className="mt-auto inline-flex items-center gap-1 pt-5 text-xs font-medium text-zinc-500 transition group-hover:text-zinc-900">
                进入环节
                <ArrowRight className="h-3.5 w-3.5 transition-transform group-hover:tranzinc-x-0.5" />
              </span>
            </Link>
          ))}
        </div>
      </section>
    </main>
  );
}
