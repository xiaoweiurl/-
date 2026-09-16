import type { NextConfig } from 'next';
import { parseAllowedDevOrigins } from './src/lib/next-runtime';

const nextConfig: NextConfig = {
  // outputFileTracingRoot: path.resolve(__dirname, '../../'),  // Uncomment and add 'import path from "path"' if needed
  /* config options here */
  // HMR is permanently off (custom server uses next({ dev: false })). This list
  // only matters if Next is ever started in development by mistake.
  allowedDevOrigins: parseAllowedDevOrigins(),
  // Never show the Next.js dev overlay (reconnect/reload UI).
  devIndicators: false,
  serverExternalPackages: ['pg'],
  // 生产环境性能优化
  compress: true,           // Gzip压缩响应
  poweredByHeader: false,   // 移除X-Powered-By头
  // 输出模式优化
  ...(process.env.NODE_ENV === 'production' ? {
    output: 'standalone' as const,  // 减少Docker镜像体积
  } : {}),
  experimental: {
    serverActions: {
      bodySizeLimit: '512mb', // 支持最大 512MB 的请求体
    },
  },
  // HTTP缓存优化（静态资源）
  onDemandEntries: {
    // 页面在内存中缓存时间(ms)
    maxInactiveAge: 60 * 1000,
    // 同时保持的页面数
    pagesBufferLength: 5,
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
      // 映射域名（外网访问时的图片域名，支持所有子域名）
      {
        protocol: 'http',
        hostname: '**',
        pathname: '/**',
      },
      {
        protocol: 'https',
        hostname: '**',
        pathname: '/**',
      },
    ],
    // 图片格式限制
    formats: ['image/avif', 'image/webp'] as const,
    // 设备尺寸
    deviceSizes: [640, 750, 828, 1080, 1200, 1920, 2048],
    imageSizes: [16, 32, 48, 64, 96, 128, 256, 384],
  },
  // 图片代理通过 API Route 实现（/api/uploads/[[...path]]/route.ts）
  async headers() {
    return [
      // 静态资源缓存（JS/CSS/图片）
      {
        source: '/_next/static/:path*',
        headers: [
          {
            key: 'Cache-Control',
            value: 'public, max-age=31536000, immutable',
          },
        ],
      },
      // 上传的图片缓存
      {
        source: '/api/uploads/:path*',
        headers: [
          {
            key: 'Cache-Control',
            value: 'public, max-age=86400, stale-while-revalidate=3600',
          },
        ],
      },
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
      // SSE 流式路由禁止缓冲
      {
        source: '/api/chat/:path*',
        headers: [
          {
            key: 'Cache-Control',
            value: 'no-cache, no-transform',
          },
          {
            key: 'X-Accel-Buffering',
            value: 'no',
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
