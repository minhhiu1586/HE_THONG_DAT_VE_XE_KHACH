import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  devIndicators: false,
  experimental: { cpus: 2 },
  // Browser gọi /api/... cùng origin frontend; Next.js chuyển tiếp đến Java backend, nơi AuthFilter xử lý request.
  async rewrites() {
    const backend = process.env.BACKEND_URL ?? "http://127.0.0.1:8080";
    return [{ source: "/api/:path*", destination: `${backend}/api/:path*` }];
  },
};
export default nextConfig;
