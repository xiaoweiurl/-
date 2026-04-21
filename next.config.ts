import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  // outputFileTracingRoot: path.resolve(__dirname, '../../'),  // Uncomment and add 'import path from "path"' if needed
  /* config options here */
  allowedDevOrigins: ['*.dev.coze.site'],
  experimental: {
    serverActions: {
      bodySizeLimit: '512mb', // 支持最大 512MB 的请求体
    },
  },
  images: {
    remotePatterns: [
      // 允许的图片域名白名单
      {
        protocol: 'https',
        hostname: 'lf-coze-web-cdn.coze.cn',
        pathname: '/**',
      },
      {
        protocol: 'https',
        hostname: 'images.unsplash.com',
        pathname: '/**',
      },
      {
        protocol: 'https',
        hostname: 'coze-coding-project.tos.coze.site',
        pathname: '/**',
      },
      {
        protocol: 'https',
        hostname: 'code.coze.cn',
        pathname: '/**',
      },
      // 本地后端服务（仅开发环境）
      ...(process.env.NODE_ENV !== 'production' ? [
        {
          protocol: 'http' as const,
          hostname: 'localhost' as const,
          port: '8080' as const,
          pathname: '/**' as const,
        },
      ] : []),
    ],
    // 图片格式限制
    formats: ['image/avif', 'image/webp'] as const,
    // 设备尺寸
    deviceSizes: [640, 750, 828, 1080, 1200, 1920, 2048],
    imageSizes: [16, 32, 48, 64, 96, 128, 256, 384],
  },
  // 安全头配置
  async headers() {
    return [
      {
        source: '/:path*',
        headers: [
          // X-Frame-Options
          {
            key: 'X-Frame-Options',
            value: 'SAMEORIGIN',
          },
          // X-Content-Type-Options
          {
            key: 'X-Content-Type-Options',
            value: 'nosniff',
          },
          // X-XSS-Protection
          {
            key: 'X-XSS-Protection',
            value: '1; mode=block',
          },
          // Referrer-Policy
          {
            key: 'Referrer-Policy',
            value: 'strict-origin-when-cross-origin',
          },
          // Permissions-Policy
          {
            key: 'Permissions-Policy',
            value: 'camera=(), microphone=(), geolocation=()',
          },
        ],
      },
      // API路由添加额外的安全头
      {
        source: '/api/:path*',
        headers: [
          {
            key: 'X-Content-Type-Options',
            value: 'nosniff',
          },
          {
            key: 'X-RateLimit-Limit',
            value: '100',
          },
          {
            key: 'X-RateLimit-Window',
            value: '60',
          },
        ],
      },
    ];
  },
  // 开发环境关闭严格模式以提高兼容性
  ...(process.env.NODE_ENV !== 'production' && {
    reactStrictMode: false,
  }),
};

export default nextConfig;
