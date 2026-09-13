"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Eye, EyeOff } from "lucide-react";
import { api, saveAuth } from "@/lib/api";
import AuthSplitLayout from "@/components/AuthSplitLayout";
import Field from "@/components/Field";

const INPUT_CLASS =
  "w-full rounded-xl border border-zinc-300 bg-white px-3.5 py-2.5 text-sm text-zinc-900 outline-none transition placeholder:text-zinc-400 focus:border-zinc-900 focus-visible:ring-2 focus-visible:ring-zinc-900/10";

export default function LoginPage() {
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<{ username?: string; password?: string }>({});
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  function validate() {
    const next: { username?: string; password?: string } = {};
    if (!username.trim()) next.username = "请输入用户名";
    if (!password) next.password = "请输入密码";
    setFieldErrors(next);
    return Object.keys(next).length === 0;
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (!validate()) return;

    setLoading(true);
    setError(null);
    try {
      const data = await api<{
        token: string;
        user: { userId: number; username: string; nickname: string | null };
      }>("/api/v1/auth/login", {
        method: "POST",
        body: JSON.stringify({ username: username.trim(), password }),
      });
      saveAuth(data.token, data.user);
      router.push("/resumes");
    } catch (err) {
      setError(err instanceof Error ? err.message : "登录失败，请稍后重试");
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthSplitLayout title="登录" subtitle="继续你的简历优化">
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <Field id="login-username" label="用户名" error={fieldErrors.username}>
          <input
            id="login-username"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            autoComplete="username"
            aria-invalid={Boolean(fieldErrors.username)}
            aria-describedby={fieldErrors.username ? "login-username-error" : undefined}
            className={INPUT_CLASS}
            placeholder="请输入用户名"
          />
        </Field>

        <Field id="login-password" label="密码" error={fieldErrors.password}>
          <div className="relative">
            <input
              id="login-password"
              type={showPassword ? "text" : "password"}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="current-password"
              aria-invalid={Boolean(fieldErrors.password)}
              aria-describedby={fieldErrors.password ? "login-password-error" : undefined}
              className={`${INPUT_CLASS} pr-11`}
              placeholder="请输入密码"
            />
            <button
              type="button"
              onClick={() => setShowPassword((value) => !value)}
              aria-label={showPassword ? "隐藏密码" : "显示密码"}
              className="absolute right-1.5 top-1/2 -translate-y-1/2 rounded-lg p-2 text-zinc-400 transition hover:text-zinc-700"
            >
              {showPassword ? (
                <EyeOff className="h-4 w-4" strokeWidth={1.75} />
              ) : (
                <Eye className="h-4 w-4" strokeWidth={1.75} />
              )}
            </button>
          </div>
        </Field>

        {error && (
          <div
            role="alert"
            className="rounded-xl border border-red-200 bg-red-50 px-3.5 py-2.5 text-sm text-red-600"
          >
            {error}
          </div>
        )}

        <button
          type="submit"
          disabled={loading}
          className="w-full rounded-xl bg-zinc-900 py-2.5 text-sm font-medium text-white transition hover:bg-zinc-800 active:translate-y-px disabled:opacity-60"
        >
          {loading ? "登录中…" : "登录"}
        </button>
      </form>

      <p className="mt-6 text-center text-sm text-zinc-500">
        还没有账号？
        <Link
          href="/register"
          className="ml-1 font-medium text-zinc-900 underline-offset-4 hover:underline"
        >
          去注册
        </Link>
      </p>
    </AuthSplitLayout>
  );
}
