"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { clearAuth, getCurrentUser } from "@/lib/api";

const navLinks = [
  { href: "/create", label: "AI 创建" },
  { href: "/resumes", label: "简历" },
  { href: "/jobs", label: "岗位" },
  { href: "/match", label: "匹配" },
  { href: "/applications", label: "投递" },
];

export default function Nav() {
  const [user, setUser] = useState<ReturnType<typeof getCurrentUser>>(null);

  useEffect(() => {
    setUser(getCurrentUser());
  }, []);

  function handleLogout() {
    clearAuth();
    setUser(null);
    window.location.href = "/";
  }

  return (
    <nav className="sticky top-0 z-10 border-b border-slate-200 bg-white/80 backdrop-blur">
      <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
        <Link href="/" className="text-lg font-bold text-blue-600">
          AI 简历优化
        </Link>

        <div className="flex items-center gap-1 text-sm">
          {navLinks.map((l) => (
            <Link
              key={l.href}
              href={l.href}
              className="rounded-lg px-3 py-1.5 text-slate-600 hover:bg-slate-100 hover:text-slate-900"
            >
              {l.label}
            </Link>
          ))}
        </div>

        <div className="flex items-center gap-3 text-sm">
          {user ? (
            <>
              <span className="text-slate-500">
                👋 {user.nickname || user.username}
              </span>
              <button
                onClick={handleLogout}
                className="rounded-lg border border-slate-200 px-3 py-1.5 text-slate-600 hover:bg-slate-100"
              >
                退出
              </button>
            </>
          ) : (
            <Link
              href="/login"
              className="rounded-lg bg-blue-600 px-4 py-1.5 font-medium text-white hover:bg-blue-700"
            >
              登录
            </Link>
          )}
        </div>
      </div>
    </nav>
  );
}
