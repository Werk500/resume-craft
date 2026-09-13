/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
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
