import type { Metadata } from "next";
import { JetBrains_Mono, Outfit } from "next/font/google";
import "./globals.css";
import Nav from "@/components/Nav";

const outfit = Outfit({
  subsets: ["latin"],
  variable: "--font-outfit",
  display: "swap",
});

const jetbrainsMono = JetBrains_Mono({
  subsets: ["latin"],
  variable: "--font-mono",
  display: "swap",
});

export const metadata: Metadata = {
  title: {
    default: "AI 简历设计与优化",
    template: "%s · AI 简历优化",
  },
  description:
    "上传解析、AI 诊断、一键优化、可解释岗位匹配与投递跟踪，一站式校招简历工具。",
  openGraph: {
    title: "AI 简历设计与优化",
    description: "可解释的岗位匹配与定向优化，让每一份简历都有据可依。",
    type: "website",
  },
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="zh-CN">
      <body
        className={`${outfit.variable} ${jetbrainsMono.variable} min-h-screen bg-[#f4f5f6] font-sans text-zinc-900 antialiased`}
      >
        <a
          href="#main"
          className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-lg focus:bg-zinc-900 focus:px-4 focus:py-2 focus:text-sm focus:text-white"
        >
          跳到主要内容
        </a>
        <Nav />
        <div id="main">{children}</div>
      </body>
    </html>
  );
}
