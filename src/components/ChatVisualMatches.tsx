'use client';

import React, { useState } from 'react';
import { X } from 'lucide-react';
import type { ChatSource } from '@/lib/chat-sse';

/**
 * 对话里的相似图。缩略图、相似度、素材/产品，点开看大图。
 */
export default function ChatVisualMatches({
  matches,
  initialExpandedId = null,
}: {
  matches: ChatSource[];
  initialExpandedId?: string | null;
}) {
  const cards = matches.filter(match => !match.empty);
  const [expandedId, setExpandedId] = useState<string | null>(initialExpandedId);
  const expanded = cards.find(match => (match.id || '') === expandedId) || null;

  if (cards.length === 0) {
    if (!matches.some(match => match.empty)) return null;
    return (
      <p data-testid="visual-match-empty" className="mb-2.5 text-[12px] leading-5 text-[#8E8E93]">
        没有找到足够相似的图片
      </p>
    );
  }

  return (
    <div data-testid="chat-visual-matches" className="mb-2.5">
      <p className="mb-1.5 text-[11px] text-[#8E8E93]">相似图片</p>
      <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
        {cards.map((hit, index) => {
          const percent = typeof hit.scorePercent === 'number' ? hit.scorePercent : 0;
          const label = hit.sourceLabel || '素材';
          const title = cardTitle(hit);
          const sub = hit.sampler ? `打样员 ${hit.sampler}` : (hit.albumName || hit.slotLabel || '');
          const key = hit.id || String(index);
          return (
            <button
              key={key}
              type="button"
              data-testid="visual-match-card"
              data-source-label={label}
              data-score={percent}
              aria-label={`查看大图 ${title} 相似度 ${percent}%`}
              onClick={() => setExpandedId(key)}
              className="min-h-11 text-left rounded-xl border border-[#E5E5EA] bg-white overflow-hidden active:scale-[0.98] transition-transform"
            >
              <div className="aspect-square bg-[#F2F2F7]">
                {hit.imageUrl ? (
                  <img src={hit.imageUrl} alt={title} className="w-full h-full object-cover" loading="lazy" />
                ) : null}
              </div>
              <div className="px-2 py-1.5">
                <div className="flex items-center justify-between gap-1">
                  <span className="text-[13px] font-semibold text-[#007AFF]">{percent}%</span>
                  <span className="text-[10px] px-1.5 py-0.5 rounded-md bg-[#F2F2F7] text-[#3A3A3C]">{label}</span>
                </div>
                <p className="text-[12px] text-[#1C1C1E] truncate">{title}</p>
                {sub ? <p className="text-[11px] text-[#8E8E93] truncate">{sub}</p> : null}
              </div>
            </button>
          );
        })}
      </div>
      {expanded && (
        <div
          className="fixed inset-0 z-[80] bg-black/40 flex items-end sm:items-center justify-center p-3"
          onClick={() => setExpandedId(null)}
        >
          <div
            data-testid="visual-match-large"
            className="w-full max-w-lg max-h-[88vh] rounded-2xl bg-white overflow-hidden shadow-2xl"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="flex items-center justify-between gap-3 px-3 min-h-11 border-b border-[#E5E5EA]">
              <div className="min-w-0">
                <p className="text-[13px] font-medium text-[#1C1C1E] truncate">{cardTitle(expanded)}</p>
                <p className="text-[11px] text-[#8E8E93]">
                  {typeof expanded.scorePercent === 'number' ? `${expanded.scorePercent}%` : ''}
                  {expanded.sourceLabel ? ` · ${expanded.sourceLabel}` : ''}
                </p>
              </div>
              <button
                type="button"
                onClick={() => setExpandedId(null)}
                className="shrink-0 w-9 h-9 flex items-center justify-center text-[#8E8E93]"
                aria-label="关闭"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
            {expanded.imageUrl ? (
              <img
                src={expanded.imageUrl}
                alt={cardTitle(expanded)}
                className="w-full max-h-[70vh] object-contain bg-[#F2F2F7]"
              />
            ) : null}
          </div>
        </div>
      )}
    </div>
  );
}

function cardTitle(hit: ChatSource): string {
  const named = [hit.goodsNo, hit.productName].filter(Boolean).join(' ');
  if (named) return named;
  if (hit.title && hit.title.trim()) return hit.title.trim();
  return '未命名';
}
