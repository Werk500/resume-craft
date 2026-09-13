"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { api, saveAuth } from "@/lib/api";

export default function RegisterPage() {
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [nickname, setNickname] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      await api("/api/v1/auth/register", {
        method: "POST",
        body: JSON.stringify({ username, password, nickname: nickname || username }),
      });
      // 注册成功后自动登录
      const data = await api<{ token: string; user: { userId: number; username: string; nickname: string | null } }>(
        "/api/v1/auth/login",
        { method: "POST", body: JSON.stringify({ username, password }) }
      );
      saveAuth(data.token, data.user);
      router.push("/resumes");
    } catch (err) {
      setError(err instanceof Error ? err.message : "注册失败");
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="flex min-h-[80vh] items-center justify-center p-6">
      <div className="w-full max-w-sm">
        <div className="rounded-2xl border border-zinc-200 bg-white p-8 shadow-card">
          <h1 className="text-2xl font-bold text-zinc-900">注册</h1>
          <p className="mt-1 text-sm text-zinc-500">创建账号，开启 AI 简历之旅</p>

          <form onSubmit={handleSubmit} className="mt-6 space-y-4">
            <div>
              <label htmlFor="register-username" className="mb-1 block text-sm font-medium text-zinc-700">用户名</label>
              <input
                id="register-username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
                minLength={3}
                maxLength={20}
                className="w-full rounded-lg border border-zinc-300 px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-brand-500/25 focus-visible:ring-offset-1 focus:border-zinc-900 focus:ring-2 focus:ring-zinc-200"
                placeholder="3-20 个字符"
              />
            </div>
            <div>
              <label htmlFor="register-nickname" className="mb-1 block text-sm font-medium text-zinc-700">昵称（可选）</label>
              <input
                id="register-nickname"
                value={nickname}
                onChange={(e) => setNickname(e.target.value)}
                maxLength={50}
                className="w-full rounded-lg border border-zinc-300 px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-brand-500/25 focus-visible:ring-offset-1 focus:border-zinc-900 focus:ring-2 focus:ring-zinc-200"
                placeholder="默认使用用户名"
              />
            </div>
            <div>
              <label htmlFor="register-password" className="mb-1 block text-sm font-medium text-zinc-700">密码</label>
              <input
                id="register-password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                minLength={6}
                maxLength={20}
                className="w-full rounded-lg border border-zinc-300 px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-brand-500/25 focus-visible:ring-offset-1 focus:border-zinc-900 focus:ring-2 focus:ring-zinc-200"
                placeholder="6-20 个字符"
              />
            </div>

            {error && (
              <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-600">
                {error}
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="w-full rounded-lg bg-zinc-900 py-2.5 font-medium text-white hover:bg-zinc-800 disabled:opacity-50"
            >
              {loading ? "注册中…" : "注册"}
            </button>
          </form>

          <p className="mt-4 text-center text-sm text-zinc-500">
            已有账号？{" "}
            <Link href="/login" className="text-brand-700 hover:underline">
              去登录
            </Link>
          </p>
        </div>
      </div>
    </main>
  );
}
