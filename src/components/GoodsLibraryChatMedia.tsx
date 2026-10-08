'use client';

import React from 'react';
import SearchRecordActions from '@/components/SearchRecordActions';
import { goodsLibraryChatEntries } from '@/lib/goods-library-chat';
import type { ChatSource } from '@/lib/chat-sse';

/**
 * 货号命中的商品库图片。签名 URL 用 img 直接展示。
 * 回答正文里已经有同一张完整图片时不再重复。
 */
export default function GoodsLibraryChatMedia({
  sources,
  content = '',
}: {
  sources?: ChatSource[];
  content?: string;
}) {
  const entries = goodsLibraryChatEntries(sources);
  if (entries.length === 0) return null;
  return (
    <div data-testid="goods-library-chat-media" className="mt-3 space-y-3">
      {entries.map((entry, index) => {
        const pending = entry.images.filter(image => !content.includes(image.imageUrl));
        return (
          <div key={`${entry.productDetailPath || entry.goodsNo}-${index}`}>
            {pending.length > 0 && (
              <div className="grid grid-cols-2 gap-2">
                {pending.map(image => (
                  <figure key={image.imageUrl} className="min-w-0">
                    <img
                      src={image.imageUrl}
                      alt={image.slotLabel}
                      data-testid="goods-library-photo"
                      data-slot={image.slotLabel}
                      className="w-full aspect-square object-cover rounded-xl border border-[#E5E5EA] bg-[#F2F2F7]"
                    />
                    <figcaption className="mt-1 text-[11px] text-[#8E8E93]">{image.slotLabel}</figcaption>
                  </figure>
                ))}
              </div>
            )}
            <SearchRecordActions
              sampleOrderPath={entry.sampleOrderPath}
              productDetailPath={entry.productDetailPath}
            />
          </div>
        );
      })}
    </div>
  );
}
