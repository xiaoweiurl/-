'use client';

import { useEffect, useState } from 'react';
import { Download, Loader2 } from 'lucide-react';
import { backendFetch } from '@/lib/backend-proxy';

interface GapItem {
  id: string;
  question: string;
  answer?: string;
  comment?: string;
  createdAt?: string;
}

export default function KnowledgeGapList() {
  const [items, setItems] = useState<GapItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [exporting, setExporting] = useState(false);

  useEffect(() => {
    backendFetch('/chat/knowledge-gaps')
      .then(async res => {
        const data = await res.json();
        if (res.status === 403) {
          setError(data.error || '只有管理员可以查看待补充知识');
          return;
        }
        if (data.success) {
          setItems(data.items || []);
        } else {
          setError(data.error || '加载失败');
        }
      })
      .catch(() => setError('加载失败'))
      .finally(() => setLoading(false));
  }, []);

  const exportJsonl = async () => {
    setExporting(true);
    try {
      const res = await backendFetch('/chat/knowledge-gaps/export');
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        setError((data as { error?: string }).error || '导出失败');
        return;
      }
      const text = await res.text();
      const blob = new Blob([text], { type: 'application/x-ndjson;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'knowledge-gaps-eval.jsonl';
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      setError('导出失败');
    } finally {
      setExporting(false);
    }
  };

  if (loading) {
    return (
      <div className="flex-1 flex items-center justify-center text-[#8e8e93]">
        <Loader2 className="w-5 h-5 animate-spin" />
      </div>
    );
  }

  if (error) {
    return <div className="flex-1 p-8 text-sm text-[#ff3b30]">{error}</div>;
  }

  return (
    <div className="flex-1 overflow-y-auto p-6">
      <div className="mb-4 flex items-center justify-between gap-3 max-w-3xl">
        <p className="text-sm text-[#8e8e93]">
          这些是用户标成答错的问题。补上对应文档后，下次同类问题就能引用到原文。
        </p>
        <button
          type="button"
          onClick={exportJsonl}
          disabled={exporting || items.length === 0}
          className="inline-flex shrink-0 items-center gap-1 rounded-lg border border-[#e5e5ea] bg-white px-3 py-1.5 text-[12px] text-[#1c1c1e] disabled:opacity-40"
        >
          <Download className="w-3.5 h-3.5" />
          {exporting ? '导出中' : '导出评测题'}
        </button>
      </div>
      {items.length === 0 ? (
        <div className="flex items-center justify-center text-sm text-[#8e8e93] py-16">
          还没有待补充的问题。对话里点「答错了」后会出现在这里。
        </div>
      ) : (
        <div className="space-y-3 max-w-3xl">
          {items.map(item => (
            <article key={item.id} className="rounded-xl border border-[#e5e5ea] bg-white p-4">
              <div className="text-[11px] text-[#ff9500] font-medium mb-1">待补充</div>
              <h2 className="text-sm font-medium text-[#1c1c1e]">{item.question}</h2>
              {item.answer && (
                <p className="mt-2 text-[13px] text-[#3a3a3c] whitespace-pre-wrap line-clamp-4">{item.answer}</p>
              )}
              {item.comment && (
                <p className="mt-2 text-[12px] text-[#8e8e93]">备注：{item.comment}</p>
              )}
              {item.createdAt && (
                <p className="mt-2 text-[11px] text-[#8e8e93]">{item.createdAt.replace('T', ' ').slice(0, 16)}</p>
              )}
            </article>
          ))}
        </div>
      )}
    </div>
  );
}
