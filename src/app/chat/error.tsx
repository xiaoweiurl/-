'use client';

import { useEffect } from 'react';

export default function ChatError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error('[Chat Error]', error);
  }, [error]);

  return (
    <div className="flex h-screen flex-col items-center justify-center bg-white">
      <div className="w-16 h-16 rounded-2xl bg-[#FF3B30]
        flex items-center justify-center mb-6 shadow-lg shadow-[0_2px_12px_rgba(0,0,0,0.04)]">
        <svg className="w-8 h-8 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z" />
        </svg>
      </div>
      <h2 className="text-xl font-semibold text-[#1C1C1E] mb-2">页面加载出错</h2>
      <p className="text-sm text-[#8e8e93] mb-6 text-center max-w-md">
        AI 对话页面遇到了一个错误，请尝试刷新页面。
      </p>
      <div className="flex gap-3">
        <button
          onClick={reset}
          className="px-4 py-2 rounded-xl bg-[#007AFF]
            text-white font-medium
            transition-all duration-200 shadow-sm hover:shadow-md"
        >
          重试
        </button>
        <button
          onClick={() => window.location.href = '/'}
          className="px-4 py-2 rounded-xl border border-[#e5e5ea] text-[#8e8e93]
            hover:bg-[#f2f2f7] transition-all duration-200"
        >
          返回主页
        </button>
      </div>
      {error.message && (
        <p className="mt-4 text-xs text-[#3a3a3c] max-w-md text-center break-all">
          {error.message}
        </p>
      )}
    </div>
  );
}
