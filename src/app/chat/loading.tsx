export default function ChatLoading() {
  return (
    <div className="flex h-screen items-center justify-center bg-gradient-to-br from-slate-50 via-white to-violet-50/30">
      <div className="flex flex-col items-center gap-4">
        <div className="w-12 h-12 rounded-2xl bg-gradient-to-r from-violet-500 to-purple-600
          flex items-center justify-center shadow-lg shadow-violet-200/50 animate-pulse">
          <svg className="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
              d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
          </svg>
        </div>
        <p className="text-sm text-slate-400">加载中...</p>
      </div>
    </div>
  );
}
