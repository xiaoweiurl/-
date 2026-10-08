'use client';

import React, { useState } from 'react';
import { X } from 'lucide-react';
import { isProductCard, type ChatSource, type VisualFilter, type VisualThumb } from '@/lib/chat-sse';
import ImageSearchFilterChips from './ImageSearchFilterChips';
import SearchRecordActions from './SearchRecordActions';

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
  const [hiddenFilters, setHiddenFilters] = useState<string[]>([]);
  const expanded = cards.find(match => (match.id || '') === expandedId) || null;
  const filters = visibleFilters(matches, hiddenFilters);
  const notice = matches.find(match => match.filterNotice)?.filterNotice || '';

  if (cards.length === 0) {
    if (!matches.some(match => match.empty) && filters.length === 0 && !notice) return null;
    return (
      <div className="mb-2.5 space-y-2">
        <ImageSearchFilterChips
          filters={filters}
          onRemove={(id) => setHiddenFilters(current => current.includes(id) ? current : [...current, id])}
        />
        {notice ? <p className="text-[12px] leading-5 text-[#C93400]">{notice}</p> : null}
        {matches.some(match => match.empty) && (
          <p data-testid="visual-match-empty" className="text-[12px] leading-5 text-[#8E8E93]">
            没有找到足够相似的图片
          </p>
        )}
      </div>
    );
  }

  return (
    <div data-testid="chat-visual-matches" className="mb-2.5">
      <ImageSearchFilterChips
        filters={filters}
        onRemove={(id) => setHiddenFilters(current => current.includes(id) ? current : [...current, id])}
      />
      {notice ? <p className="mb-1.5 text-[12px] leading-5 text-[#C93400]">{notice}</p> : null}
      <p className="mb-1.5 text-[11px] text-[#8E8E93]">{sectionLabel(cards)}</p>
      {cards.some(isProductCard) && (
        <div className="space-y-2 mb-2">
          {cards.filter(isProductCard).map((hit, index) => (
            <ProductCard key={hit.id || `product-${index}`} hit={hit} onOpen={setExpandedId} />
          ))}
        </div>
      )}
      {cards.some(hit => !isProductCard(hit)) && (
      <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
        {cards.filter(hit => !isProductCard(hit)).map((hit, index) => {
          const percent = typeof hit.scorePercent === 'number' ? hit.scorePercent : 0;
          const label = hit.sourceLabel || '素材';
          const title = cardTitle(hit);
          const sub = hit.sampler ? `打样员 ${hit.sampler}` : (hit.albumName || hit.slotLabel || '');
          const key = hit.id || String(index);
          return (
            <div
              key={key}
              className="rounded-xl border border-[#E5E5EA] bg-white overflow-hidden"
            >
              <button
                type="button"
                data-testid="visual-match-card"
                data-source-label={label}
                data-score={percent}
                data-scenario={hit.scenario || ''}
                aria-label={`查看大图 ${title} 相似度 ${percent}%`}
                onClick={() => setExpandedId(key)}
                className="w-full min-h-11 text-left active:scale-[0.98] transition-transform"
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
              <SearchRecordActions
                sampleOrderPath={hit.sampleOrderPath}
                productDetailPath={hit.productDetailPath}
                className="px-2"
              />
            </div>
          );
        })}
      </div>
      )}
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
            <SearchRecordActions
              sampleOrderPath={expanded.sampleOrderPath}
              productDetailPath={expanded.productDetailPath}
              className="px-3"
            />
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

function ProductCard({ hit, onOpen }: { hit: ChatSource; onOpen: (id: string) => void }) {
  const percent = typeof hit.scorePercent === 'number' ? hit.scorePercent : 0;
  const title = cardTitle(hit);
  const key = hit.id || title;
  const thumbs = (hit.images || []).filter(thumb => thumb?.imageUrl).slice(0, 4);
  return (
    <div className="rounded-xl border border-[#E5E5EA] bg-white p-2">
      <button
        type="button"
        data-testid="visual-product-card"
        data-scenario={hit.scenario || 'SAME_PRODUCT'}
        data-score={percent}
        aria-label={`查看同款 ${title} 相似度 ${percent}%`}
        onClick={() => onOpen(key)}
        className="w-full min-h-11 text-left flex gap-2 active:scale-[0.99] transition-transform"
      >
        <div className="w-16 h-16 rounded-lg bg-[#F2F2F7] overflow-hidden shrink-0">
          {hit.imageUrl ? <img src={hit.imageUrl} alt={title} className="w-full h-full object-cover" loading="lazy" /> : null}
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex items-center justify-between gap-2">
            <span className="text-[15px] font-semibold text-[#007AFF]">{percent}%</span>
            <span className="text-[10px] px-1.5 py-0.5 rounded-md bg-[#F2F2F7] text-[#3A3A3C]">同款</span>
          </div>
          <p className="text-[13px] text-[#1C1C1E] truncate">{title}</p>
          {hit.sampler ? <p className="text-[12px] text-[#8E8E93] truncate">打样员 {hit.sampler}</p> : null}
          {thumbs.length > 0 && (
            <div className="mt-1 flex gap-1">
              {thumbs.map((thumb, index) => (
                <Thumb key={`${thumb.imageUrl}-${index}`} thumb={thumb} />
              ))}
            </div>
          )}
        </div>
      </button>
      <SearchRecordActions
        sampleOrderPath={hit.sampleOrderPath}
        productDetailPath={hit.productDetailPath}
        className="pl-[4.5rem]"
      />
    </div>
  );
}

function Thumb({ thumb }: { thumb: VisualThumb }) {
  return (
    <img
      src={thumb.imageUrl}
      alt={thumb.slotLabel || '同款其他图片'}
      data-testid="visual-product-thumb"
      className="w-8 h-8 rounded-md object-cover bg-[#F2F2F7]"
      loading="lazy"
    />
  );
}

function visibleFilters(matches: ChatSource[], hidden: string[]): VisualFilter[] {
  const found = matches.find(match => Array.isArray(match.filters) && match.filters.length > 0);
  return (found?.filters || []).filter(filter => filter?.id && !hidden.includes(filter.id));
}

function sectionLabel(cards: ChatSource[]): string {
  const hasProduct = cards.some(isProductCard);
  const hasImage = cards.some(hit => !isProductCard(hit));
  if (hasProduct && hasImage) return '同款在前，参考图在后';
  if (hasProduct) return '同款';
  return '相似图片';
}

function cardTitle(hit: ChatSource): string {
  const named = [hit.goodsNo, hit.productName].filter(Boolean).join(' ');
  if (named) return named;
  if (hit.title && hit.title.trim()) return hit.title.trim();
  return '未命名';
}
