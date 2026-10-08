'use client';

import React, { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Loader2, ScanSearch, X } from 'lucide-react';
import ImageSearchResults, { type ImageSearchResultHit } from './ImageSearchResults';

type SearchHit = ImageSearchResultHit;

interface SearchPayload {
  tookMs?: number;
  mode?: string;
  scenario?: string;
  results?: SearchHit[];
}

const SCOPES = [
  { value: 'all', label: '全部' },
  { value: 'library', label: '素材' },
  { value: 'goods', label: '打样' },
] as const;

function scenarioLabel(scenario: string | null): string {
  if (scenario === 'SAME_PRODUCT') return '同款 · ';
  if (scenario === 'SIMILAR_REFERENCE') return '参考图 · ';
  if (scenario === 'MIXED') return '同款在前 · ';
  return '';
}

/**
 * 设计师素材页顶栏和工厂页（/supply-chain，含 mode=factory 对话）共用。
 * 商品库和打样表单 /sampler 不放入口。功能关闭时不渲染。
 * 结果里仍可带出关联的历史打样或商品，仅作参考。
 */
export default function ImageSearchEntry({ className = '' }: { className?: string }) {
  const [enabled, setEnabled] = useState(false);
  const [open, setOpen] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [text, setText] = useState('');
  const [scope, setScope] = useState<(typeof SCOPES)[number]['value']>('all');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [tookMs, setTookMs] = useState<number | null>(null);
  const [scenario, setScenario] = useState<string | null>(null);
  const [results, setResults] = useState<SearchHit[]>([]);
  const [mounted, setMounted] = useState(false);
  const inputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    setMounted(true);
  }, []);

  useEffect(() => {
    if (!open) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false);
        setError(null);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  useEffect(() => {
    let cancelled = false;
    fetch('/api/image-search/status', { credentials: 'include' })
      .then(async (res) => {
        if (!res.ok) return null;
        return res.json();
      })
      .then((body) => {
        if (!cancelled && body?.success && body?.data?.enabled === true) {
          setEnabled(true);
        }
      })
      .catch(() => {
        /* 后端不可用时不显示入口 */
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    return () => {
      if (preview) URL.revokeObjectURL(preview);
    };
  }, [preview]);

  if (!enabled) return null;

  const close = () => {
    setOpen(false);
    setError(null);
  };

  const onFile = (next: File | null) => {
    if (preview) URL.revokeObjectURL(preview);
    setFile(next);
    setPreview(next ? URL.createObjectURL(next) : null);
    setResults([]);
    setError(null);
  };

  const search = async () => {
    if (!file && !text.trim()) {
      setError('请上传图片，或输入中文描述');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const body = new FormData();
      if (file) body.append('file', file);
      if (text.trim()) body.append('text', text.trim());
      body.append('scope', scope);
      const res = await fetch('/api/image-search/query', {
        method: 'POST',
        body,
        credentials: 'include',
      });
      const data = await res.json();
      if (!res.ok || !data.success) {
        setResults([]);
        setError(data.message || '检索失败');
        return;
      }
      const payload = (data.data || {}) as SearchPayload;
      setResults(payload.results || []);
      setScenario(payload.scenario || null);
      setTookMs(payload.tookMs ?? null);
      if (!payload.results || payload.results.length === 0) {
        setError('没有找到相似图片。确认已经打开功能并完成回填。');
      }
    } catch {
      setError('检索失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className={`inline-flex items-center gap-1.5 min-h-9 px-3 py-1.5 rounded-xl text-[12px] font-medium text-[#007AFF] bg-[#007AFF]/10 hover:bg-[#007AFF]/16 transition-colors ${className}`}
      >
        <ScanSearch className="w-3.5 h-3.5" />
        <span>以图搜图</span>
      </button>

      {/* 顶栏 backdrop-filter 会成为 position:fixed 的包含块，弹层挂到 body 才相对视口定位 */}
      {open && mounted && createPortal(
        <div className="fixed inset-0 z-[80] bg-black/40 flex items-end sm:items-center justify-center p-0 sm:p-4 overscroll-contain">
          <div className="bg-white w-full h-[100dvh] sm:h-auto sm:max-h-[85vh] sm:max-w-lg sm:rounded-2xl shadow-lg flex flex-col">
            <div className="flex items-center justify-between px-4 h-14 border-b border-[#E5E5EA] shrink-0">
              <h2 className="text-[16px] font-semibold text-[#1C1C1E]">以图搜图</h2>
              <button type="button" onClick={close} className="min-w-11 min-h-11 flex items-center justify-center text-[#8E8E93]" aria-label="关闭">
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto px-4 py-4 space-y-4">
              <button
                type="button"
                onClick={() => inputRef.current?.click()}
                className="w-full min-h-28 rounded-2xl border border-dashed border-[#E5E5EA] bg-[#F2F2F7] flex items-center justify-center overflow-hidden"
              >
                {preview ? (
                  <img src={preview} alt="待检索图片" className="max-h-40 w-full object-contain" />
                ) : (
                  <span className="text-[13px] text-[#8E8E93] px-4 text-center">上传一张款式图或素材图</span>
                )}
              </button>
              <input
                ref={inputRef}
                type="file"
                accept="image/jpeg,image/png,image/webp,image/gif,image/bmp"
                className="hidden"
                onChange={(event) => onFile(event.target.files?.[0] || null)}
              />

              <input
                value={text}
                onChange={(event) => setText(event.target.value)}
                placeholder="可选：用中文描述，例如红色蕾丝"
                className="w-full min-h-11 px-3 rounded-xl bg-[#F2F2F7] text-[15px] text-[#1C1C1E] placeholder:text-[#8E8E93] focus:outline-none focus:ring-2 focus:ring-[#007AFF]/30"
              />

              <div className="flex gap-2">
                {SCOPES.map((item) => (
                  <button
                    key={item.value}
                    type="button"
                    onClick={() => setScope(item.value)}
                    className={`min-h-9 px-3 rounded-xl text-[13px] ${
                      scope === item.value ? 'bg-[#007AFF] text-white' : 'bg-[#F2F2F7] text-[#3A3A3C]'
                    }`}
                  >
                    {item.label}
                  </button>
                ))}
              </div>

              <button
                type="button"
                onClick={search}
                disabled={loading}
                className="w-full min-h-11 rounded-xl bg-[#007AFF] text-white text-[15px] font-medium disabled:opacity-60 flex items-center justify-center gap-2"
              >
                {loading && <Loader2 className="w-4 h-4 animate-spin" />}
                搜索相似款
              </button>

              {error && <p className="text-[13px] text-[#FF3B30]">{error}</p>}
              {tookMs != null && results.length > 0 && (
                <p className="text-[12px] text-[#8E8E93]">
                  {scenarioLabel(scenario)}{results.length} 条 · {tookMs} ms
                </p>
              )}

              <ImageSearchResults results={results} scenario={scenario} />
            </div>
          </div>
        </div>,
        document.body,
      )}
    </>
  );
}
