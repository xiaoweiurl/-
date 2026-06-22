import type { Metadata } from 'next';
import { Inspector } from 'react-dev-inspector';
import './globals.css';
import { ClientProviders } from '@/components/ClientProviders';

export const metadata: Metadata = {
  title: {
    default: '企业数智中台系统 | 智能知识管理平台',
    template: '%s | 企业数智中台系统',
  },
  description:
    '一款精美的知识库管理系统，支持知识上传、分类、筛选、批量操作等功能，现代化极简设计，高效管理您的知识资源。',
  keywords: [
    '知识管理',
    '知识库系统',
    '知识分类',
    '知识上传',
    '批量操作',
    '知识预览',
    '收藏夹',
    '知识筛选',
  ],
  authors: [{ name: 'Digital Knowledge Base Team' }],
  generator: 'Next.js',
  // icons: {
  //   icon: '',
  // },
  openGraph: {
    title: '企业数智中台系统 | 智能知识管理平台',
    description:
      '一款精美的知识库管理系统，支持知识上传、分类、筛选、批量操作等功能。',
    siteName: '企业数智中台系统',
    locale: 'zh_CN',
    type: 'website',
    // images: [
    //   {
    //     url: '',
    //     width: 1200,
    //     height: 630,
    //     alt: '扣子编程 - 你的 AI 工程师',
    //   },
    // ],
  },
  // twitter: {
  //   card: 'summary_large_image',
  //   title: 'Coze Code | Your AI Engineer is Here',
  //   description:
  //     'Build and deploy full-stack applications through AI conversation. No env setup, just flow.',
  //   // images: [''],
  // },
  robots: {
    index: true,
    follow: true,
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const isDev = process.env.COZE_PROJECT_ENV === 'DEV';

  return (
    <html lang="zh-CN" style={{ colorScheme: 'light' }}>
      <body className={`antialiased`}>
        <ClientProviders>
          {isDev && <Inspector />}
          {children}
        </ClientProviders>
      </body>
    </html>
  );
}
