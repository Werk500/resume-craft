"use client";

import { useEffect, useState } from "react";
import {
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
import Link from "next/link";
import { API_BASE } from "@/lib/api";

const features = [
  {
    icon: UploadCloud,
    title: "简历上传解析",
    desc: "支持 PDF / Word / 图片，图片走 OCR 识别并给出分块置信度",
    path: "/upload",
  },
  {
    icon: ScanText,
    title: "OCR 人工核对",
    desc: "低置信度扫描件逐块确认后才参与评分，避免模糊内容污染诊断结果",
    path: "/resumes",
  },
  {
    icon: MessagesSquare,
    title: "AI 对话创建",
    desc: "问答式从零生成 Markdown 简历，保存后可继续诊断与优化",
    path: "/create",
  },
  {
    icon: FileSearch,
    title: "AI 简历诊断",
    desc: "信息完整度 / 表达质量 / 岗位匹配度三维评分与改进建议",
    path: "/resumes",
  },
  {
    icon: Sparkles,
    title: "优化与逐句精修",
    desc: "保留事实的整份改写，以及 DATA / METHOD / IMPACT 三方向精修",
    path: "/resumes",
  },
  {
    icon: Target,
    title: "可解释岗位匹配",
    desc: "关键词 40% + 语义 40% + 硬性条件 20%，输出命中与缺失明细",
    path: "/match",
  },
  {
    icon: GitCompare,
    title: "版本对比与提升报告",
    desc: "任意两个版本行级 Diff，定向优化前后匹配分对比与改动原因",
    path: "/resumes",
  },
  {
    icon: Briefcase,
    title: "校招岗位库",
    desc: "真实校招 JD 种子库，按公司或关键词检索目标岗位",
    path: "/jobs",
  },
  {
    icon: Send,
    title: "投递跟踪",
    desc: "看板式状态流转（待跟进 / 面试中 / 已拒绝等）与统计",
    path: "/applications",
  },
];

export default function Home() {
  const [backend, setBackend] = useState<string>("连接中...");
  const [backendOk, setBackendOk] = useState<boolean>(false);

  useEffect(() => {
    let cancelled = false;

    async function checkBackend(attempt = 1): Promise<void> {
      try {
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), 5000);
        const res = await fetch(`${API_BASE}/actuator/health`, { signal: controller.signal });
        clearTimeout(timer);
        const data = (await res.json()) as { status?: string };
        if (cancelled) return;
        const up = data?.status === "UP";
        setBackend(up ? "后端运行中" : `后端状态 ${data?.status ?? "未知"}`);
        setBackendOk(up);
      } catch {
        if (cancelled) return;
        if (attempt < 3) {
          setTimeout(() => void checkBackend(attempt + 1), 1500 * attempt);
          return;
        }
        setBackend("后端未连接（请确认网关 8080 已启动）");
        setBackendOk(false);
      }
    }

    void checkBackend();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <main className="mx-auto max-w-5xl p-6 sm:p-8">
      <header className="mt-10 mb-10 text-center">
        <h1 className="text-4xl font-bold tracking-tight text-slate-900">
          AI 简历设计与优化
        </h1>
        <p className="mt-3 text-lg text-slate-600">
          一站式 AI 简历工具：智能诊断 · 一键优化 · 岗位匹配
        </p>
        <div className="mt-4 inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-1 text-sm text-slate-500">
          <span className={`h-2 w-2 rounded-full ${backendOk ? "bg-green-500" : "bg-amber-500"}`} />
          {backend}
        </div>
      </header>

      <section className="mb-8 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h2 className="text-lg font-semibold text-slate-800">功能全景</h2>
        <p className="mt-2 text-sm text-slate-500">
          从简历上传到 AI 诊断、优化、岗位匹配、投递跟踪，全流程打通，基于 Spring AI 多模型架构。
        </p>
      </section>

      <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {features.map((f) => (
          <Link
            key={f.title}
            href={f.path}
            className="group flex flex-col rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition duration-200 hover:-translate-y-0.5 hover:border-slate-300 hover:shadow-md active:translate-y-0"
          >
            <f.icon
              className="h-7 w-7 text-brand-600 transition duration-200 group-hover:scale-105"
              strokeWidth={1.75}
            />
            <h3 className="mt-3 font-semibold text-slate-800">{f.title}</h3>
            <p className="mt-1 text-sm leading-relaxed text-slate-500">{f.desc}</p>
            <span className="mt-auto pt-4 text-xs font-medium text-brand-600">
              开始使用
              <span className="ml-1 inline-block transition-transform duration-200 group-hover:translate-x-0.5">
                →
              </span>
            </span>
          </Link>
        ))}
      </section>
    </main>
  );
}
