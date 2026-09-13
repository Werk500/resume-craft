"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { usePathname } from "next/navigation";
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
  const pathname = usePathname();

  useEffect(() => {
    setUser(getCurrentUser());
  }, []);

  function isActive(href: string) {
    if (href === "/") return pathname === "/";
    return pathname === href || pathname.startsWith(`${href}/`);
  }

  function handleLogout() {
    clearAuth();
    setUser(null);
    window.location.href = "/";
  }

  return (
    <nav className="sticky top-0 z-10 border-b border-zinc-200 bg-white/85 backdrop-blur">
      <div className="mx-auto flex max-w-6xl items-center justify-between gap-3 px-4 py-3">
        <Link
          href="/"
          className="flex shrink-0 items-center gap-2 text-lg font-semibold tracking-tight text-zinc-900"
        >
          <span className="h-5 w-5 rounded-md bg-zinc-900" aria-hidden />
          AI 简历优化
        </Link>

        <div className="flex flex-1 items-center gap-1 overflow-x-auto text-sm">
          {navLinks.map((l) => (
            <Link
              key={l.href}
              href={l.href}
              aria-current={isActive(l.href) ? "page" : undefined}
              className={`shrink-0 rounded-lg px-3 py-1.5 transition ${
                isActive(l.href)
                  ? "bg-zinc-900 font-medium text-white"
                  : "text-zinc-600 hover:bg-zinc-200/70 hover:text-zinc-900"
              }`}
            >
              {l.label}
            </Link>
          ))}
        </div>

        <div className="flex shrink-0 items-center gap-3 text-sm">
          {user ? (
            <>
              <span className="hidden text-zinc-500 sm:inline">
                {user.nickname || user.username}
              </span>
              <button
                onClick={handleLogout}
                className="rounded-lg border border-zinc-200 px-3 py-1.5 text-zinc-600 transition hover:bg-zinc-100"
              >
                退出
              </button>
            </>
          ) : (
            <Link
              href="/login"
              className="rounded-lg bg-zinc-900 px-4 py-1.5 font-medium text-white transition hover:bg-zinc-800"
            >
              登录
            </Link>
          )}
        </div>
      </div>
    </nav>
  );
}
