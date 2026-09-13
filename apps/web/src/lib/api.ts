// 默认同源（经 next.config.mjs 的 rewrites 转发到网关 8080）；
// 如需直连网关，可在 .env.local 设置 NEXT_PUBLIC_API_BASE=http://localhost:8080
const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? "";

/** 后端统一响应结构 */
interface ApiEnvelope<T> {
  code: number;
  message: string;
  data: T;
}

/** 401 时清理登录态并跳登录页 */
function redirectToLogin() {
  if (typeof window !== "undefined" && !window.location.pathname.startsWith("/login")) {
    clearAuth();
    window.location.href = "/login";
  }
}

/**
 * 统一 API 调用：自动带 JWT token、解析 ApiResponse 信封、抛错带后端 message。
 * 收到 401（token 失效/未登录）时自动清登录态并跳转登录页。
 */
export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  let token: string | null = null;
  if (typeof window !== "undefined") {
    token = localStorage.getItem("token");
  }

  const headers: Record<string, string> = {};
  if (options.body) headers["Content-Type"] = "application/json";
  if (token) headers["Authorization"] = `Bearer ${token}`;

  const res = await fetch(`${API_BASE}${path}`, { ...options, headers });

  if (res.status === 401) {
    // token 失效或未登录 → 清理并跳转
    redirectToLogin();
    throw new Error("登录已过期，请重新登录");
  }

  const json = (await res.json().catch(() => null)) as ApiEnvelope<T> | null;

  if (!res.ok || !json || json.code !== 200) {
    throw new Error(json?.message || `请求失败 (HTTP ${res.status})`);
  }
  return json.data;
}

/** 登录后保存 token + 用户信息 */
export function saveAuth(token: string, user: { userId: number; username: string; nickname: string | null }) {
  localStorage.setItem("token", token);
  localStorage.setItem("user", JSON.stringify(user));
}

export function clearAuth() {
  localStorage.removeItem("token");
  localStorage.removeItem("user");
}

export function getCurrentUser(): { userId: number; username: string; nickname: string | null } | null {
  if (typeof window === "undefined") return null;
  const raw = localStorage.getItem("user");
  return raw ? JSON.parse(raw) : null;
}

export { API_BASE };
