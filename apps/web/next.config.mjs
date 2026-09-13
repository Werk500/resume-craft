/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // 本地验证生产构建时可用 NEXT_DIST_DIR=.next-build 输出到独立目录，
  // 避免 next build 覆盖 dev server 正在使用的 .next（会导致 chunk 404/500）
  distDir: process.env.NEXT_DIST_DIR || ".next",
  // 同源代理：浏览器只访问 3001，由 Next 转发到网关 8080
  // 好处：不依赖 CORS 配置，内嵌浏览器 / 局域网访问也能正常工作
  async rewrites() {
    return [
      { source: "/api/:path*", destination: "http://localhost:8080/api/:path*" },
      { source: "/actuator/:path*", destination: "http://localhost:8080/actuator/:path*" },
    ];
  },
};

export default nextConfig;
