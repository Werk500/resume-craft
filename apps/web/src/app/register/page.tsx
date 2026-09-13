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

interface FieldErrors {
  username?: string;
  nickname?: string;
  password?: string;
}

export default function RegisterPage() {
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [nickname, setNickname] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  function validate() {
    const next: FieldErrors = {};
    const name = username.trim();
    if (!name) {
      next.username = "请输入用户名";
    } else if (name.length < 3 || name.length > 20) {
      next.username = "用户名需为 3-20 个字符";
    }
    if (nickname.trim().length > 50) {
      next.nickname = "昵称不能超过 50 个字符";
    }
    if (!password) {
      next.password = "请输入密码";
    } else if (password.length < 6 || password.length > 20) {
      next.password = "密码需为 6-20 个字符";
    }
    setFieldErrors(next);
    return Object.keys(next).length === 0;
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (!validate()) return;

    setLoading(true);
    setError(null);
    try {
      const name = username.trim();
      await api("/api/v1/auth/register", {
        method: "POST",
        body: JSON.stringify({ username: name, password, nickname: nickname.trim() || name }),
      });
      // 注册成功后自动登录
      const data = await api<{
        token: string;
        user: { userId: number; username: string; nickname: string | null };
      }>("/api/v1/auth/login", {
        method: "POST",
        body: JSON.stringify({ username: name, password }),
      });
      saveAuth(data.token, data.user);
      router.push("/resumes");
    } catch (err) {
      setError(err instanceof Error ? err.message : "注册失败，请稍后重试");
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthSplitLayout title="注册" subtitle="三分钟建立你的第一份可匹配简历">
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <Field
          id="register-username"
          label="用户名"
          error={fieldErrors.username}
          hint="3-20 个字符，登录后不可修改"
        >
          <input
            id="register-username"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            autoComplete="username"
            aria-invalid={Boolean(fieldErrors.username)}
            aria-describedby={fieldErrors.username ? "register-username-error" : undefined}
            className={INPUT_CLASS}
            placeholder="3-20 个字符"
          />
        </Field>

        <Field id="register-nickname" label="昵称" error={fieldErrors.nickname} hint="可留空，默认使用用户名">
          <input
            id="register-nickname"
            value={nickname}
            onChange={(event) => setNickname(event.target.value)}
            autoComplete="nickname"
            aria-invalid={Boolean(fieldErrors.nickname)}
            aria-describedby={fieldErrors.nickname ? "register-nickname-error" : undefined}
            className={INPUT_CLASS}
            placeholder="默认使用用户名"
          />
        </Field>

        <Field
          id="register-password"
          label="密码"
          error={fieldErrors.password}
          hint="6-20 个字符"
        >
          <div className="relative">
            <input
              id="register-password"
              type={showPassword ? "text" : "password"}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="new-password"
              aria-invalid={Boolean(fieldErrors.password)}
              aria-describedby={fieldErrors.password ? "register-password-error" : undefined}
              className={`${INPUT_CLASS} pr-11`}
              placeholder="6-20 个字符"
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
          {loading ? "注册中…" : "注册并登录"}
        </button>
      </form>

      <p className="mt-6 text-center text-sm text-zinc-500">
        已有账号？
        <Link
          href="/login"
          className="ml-1 font-medium text-zinc-900 underline-offset-4 hover:underline"
        >
          去登录
        </Link>
      </p>
    </AuthSplitLayout>
  );
}
