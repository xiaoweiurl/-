'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Lock } from 'lucide-react';

export default function SwaggerPage() {
  const router = useRouter();
  const [accessDenied, setAccessDenied] = useState(false);
  const [checking, setChecking] = useState(true);

  // 权限检查：仅管理员可访问
  useEffect(() => {
    const checkAdmin = async () => {
      try {
        const sessionId = localStorage.getItem('session_id');
        if (!sessionId) { router.push('/login'); return; }
        const { default: { backendFetch } } = await import('@/lib/backend-proxy');
        const res = await backendFetch('/auth/session', { headers: { 'X-Session-Id': sessionId } });
        const result = await res.json();
        if (result.code === 200 && result.data?.role === 'admin') {
          setAccessDenied(false);
        } else {
          setAccessDenied(true);
        }
      } catch {
        setAccessDenied(true);
      } finally {
        setChecking(false);
      }
    };
    checkAdmin();
  }, [router]);

  useEffect(() => {
    if (accessDenied || checking) return;
    // 动态加载 Swagger UI CSS 和 JS
    const loadSwaggerUI = () => {
      // 加载 CSS
      const cssLink = document.createElement('link');
      cssLink.rel = 'stylesheet';
      cssLink.href = 'https://unpkg.com/swagger-ui-dist@5.9.0/swagger-ui.css';
      document.head.appendChild(cssLink);

      // 加载 JS
      const script = document.createElement('script');
      script.src = 'https://unpkg.com/swagger-ui-dist@5.9.0/swagger-ui-bundle.js';
      script.onload = () => {
        // Swagger UI 加载完成后初始化
        const script2 = document.createElement('script');
        script2.src = 'https://unpkg.com/swagger-ui-dist@5.9.0/swagger-ui-standalone-preset.js';
        script2.onload = () => {
          // 初始化 Swagger UI
          if (typeof window !== 'undefined' && (window as any).SwaggerUIBundle) {
            (window as any).SwaggerUIBundle({
              url: '/api/swagger',
              dom_id: '#swagger-ui',
              deepLinking: true,
              presets: [
                (window as any).SwaggerUIBundle.presets.apis,
                (window as any).SwaggerUIStandalonePreset
              ],
              layout: 'StandaloneLayout',
              // 启用 credentials 以发送 cookies
              withCredentials: true,
              // 请求拦截器：在每个请求中添加 sessionId
              requestInterceptor: (req: any) => {
                // 从 localStorage 获取 sessionId
                const sessionId = localStorage.getItem('session_id');
                if (sessionId) {
                  req.headers['X-Session-Id'] = sessionId;
                }
                return req;
              },
            });
          }
        };
        document.body.appendChild(script2);
      };
      document.body.appendChild(script);
    };

    loadSwaggerUI();
  }, [accessDenied, checking]);

  if (checking) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center">
        <div className="text-slate-400">加载中...</div>
      </div>
    );
  }

  if (accessDenied) {
    return (
      <div className="min-h-screen bg-slate-50 flex flex-col items-center justify-center gap-4">
        <Lock className="w-16 h-16 text-slate-300" />
        <h2 className="text-xl font-semibold text-slate-600">无访问权限</h2>
        <p className="text-slate-400">仅管理员可访问此页面</p>
        <button
          onClick={() => router.push('/')}
          className="px-4 py-2 bg-violet-600 text-white rounded-lg hover:bg-violet-700 transition-colors"
        >
          返回首页
        </button>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="max-w-7xl mx-auto p-6">
        <div className="mb-6">
          <h1 className="text-3xl font-bold text-slate-800">数字知识库 API 文档</h1>
          <p className="text-slate-600 mt-2">
            基于 OpenAPI 3.0 规范的 RESTful API 文档
          </p>
        </div>
        <div className="bg-white rounded-xl shadow-lg p-4" style={{ minHeight: 'calc(100vh - 200px)' }}>
          <div id="swagger-ui" />
        </div>
      </div>
    </div>
  );
}
