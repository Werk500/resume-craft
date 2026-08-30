import type { Metadata } from "next";
import "./globals.css";
import Nav from "@/components/Nav";

export const metadata: Metadata = {
  title: "AI简历设计与优化",
  description: "一站式 AI 简历工具：智能诊断、一键优化、岗位匹配",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="zh-CN">
      <body className="min-h-screen bg-gradient-to-b from-slate-50 to-slate-100">
        <Nav />
        {children}
      </body>
    </html>
  );
}
