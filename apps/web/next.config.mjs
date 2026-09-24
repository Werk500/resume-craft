/** @type {import('next').NextConfig} */
// 同源代理目标：本地开发指向宿主机网关；容器内由 GATEWAY_URL 指定（如 http://gateway:8080）
const GATEWAY_URL = process.env.GATEWAY_URL || "http://localhost:8080";

const nextConfig = {
  reactStrictMode: true,
  // 关闭 Next 自带的响应压缩。
  // 原因：Next 的压缩中间件对 /api 反向代理的响应也会压缩，而 zlib 会先把
  // SSE 流缓冲满再吐出去 —— 浏览器只收到一个整块，打字机效果直接失效
  // （实测：压缩开启时整条 4.7KB 的流一次性到达，工具阶段事件也全部丢失）。
  // 本地开发不需要这点压缩收益；线上由反向代理（nginx）负责压缩。
  compress: false,
  // distDir 由 NEXT_DIST_DIR 决定，默认 .next。
  // 注意：dev server 与生产构建必须用**不同**目录，否则产物互相覆盖，
  // 页面会报 Cannot find module .../middleware-manifest.json。
  // 本地约定：dev 用 NEXT_DIST_DIR=.next-build；验证生产构建时换个目录
  // （例如 NEXT_DIST_DIR=.next-verify），或者构建前先停掉 dev server。
  // 另：用 cmd 设置该变量时请写 set "NEXT_DIST_DIR=.next-build"，
  // 写成 set NEXT_DIST_DIR=.next-build && ... 会把末尾空格算进变量值，
  // Next 就会在 .next-build 后面多带一个空格建目录。
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
