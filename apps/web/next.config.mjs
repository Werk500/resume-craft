/** @type {import('next').NextConfig} */
// 同源代理目标：本地开发指向宿主机网关；容器内由 GATEWAY_URL 指定（如 http://gateway:8080）
const GATEWAY_URL = process.env.GATEWAY_URL || "http://localhost:8080";

const nextConfig = {
  reactStrictMode: true,
  // 本地验证生产构建时可用 NEXT_DIST_DIR=.next-build 输出到独立目录，
  // 避免 next build 覆盖 dev server 正在使用的 .next（会导致 chunk 404/500）
  distDir: process.env.NEXT_DIST_DIR || ".next",
  // 同源代理：浏览器只访问 3001，由 Next 转发到网关 8080
  // 好处：不依赖 CORS 配置，内嵌浏览器 / 局域网访问也能正常工作
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${GATEWAY_URL}/api/:path*` },
      { source: "/actuator/:path*", destination: `${GATEWAY_URL}/actuator/:path*` },
    ];
  },
};

export default nextConfig;
