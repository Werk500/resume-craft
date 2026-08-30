const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8088";

/** 后端统一响应结构 */
interface ApiEnvelope<T> {
  code: number;
  message: string;
  data: T;
}

/**
 * 统一 API 调用：自动带 JWT token、解析 ApiResponse 信封、抛错带后端 message。
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
