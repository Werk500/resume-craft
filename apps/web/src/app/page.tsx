"use client";

import { useEffect, useState } from "react";
import { FileSearch, Sparkles, Target, UploadCloud, Briefcase, Send } from "lucide-react";
import Link from "next/link";
import { API_BASE } from "@/lib/api";

const features = [
  { icon: UploadCloud, title: "简历上传", desc: "拖拽上传 PDF / Word，自动解析全文+联系信息", path: "/upload", done: true },
  { icon: FileSearch, title: "简历诊断", desc: "AI 多维评分，可视化体检报告与改进建议", path: "/resumes", done: true },
  { icon: Sparkles, title: "智能优化", desc: "AI 一键改写、STAR 法则、前后对比下载", path: "/resumes", done: true },
  { icon: Target, title: "岗位匹配", desc: "JD 解析、AI 匹配度分析、可解释归因", path: "/match", done: true },
  { icon: Briefcase, title: "岗位管理", desc: "录入目标公司 JD，随时对比匹配", path: "/jobs", done: true },
  { icon: Send, title: "投递管理", desc: "投递记录、面试进度、状态流转跟踪", path: "/applications", done: true },
];

export default function Home() {
  const [backend, setBackend] = useState<string>("连接中...");
  const [backendOk, setBackendOk] = useState<boolean>(false);

  useEffect(() => {
    fetch(`${API_BASE}/actuator/health`)
      .then((r) => r.json())
      .then((d) => {
        setBackend(`后端 ${d.status}`);
        setBackendOk(d.status === "UP");
      })
      .catch(() => setBackend("后端未连接"));
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
          <div key={f.title} className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm transition hover:shadow-md">
            <f.icon className="mb-3 h-8 w-8 text-blue-600" />
            <h3 className="font-semibold text-slate-800">{f.title}</h3>
            <p className="mt-1 text-sm text-slate-500">{f.desc}</p>
            {f.done ? (
              f.path && (
                <Link href={f.path} className="mt-3 inline-block rounded-full bg-blue-100 px-3 py-1 text-xs font-medium text-blue-700 hover:bg-blue-200">
                  开始使用 →
                </Link>
              )
            ) : (
              <span className="mt-3 inline-block rounded-full bg-slate-100 px-3 py-1 text-xs text-slate-500">建设中</span>
            )}
          </div>
        ))}
      </section>
    </main>
  );
}
