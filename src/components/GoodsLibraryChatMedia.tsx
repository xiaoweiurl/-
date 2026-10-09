'use client';

import React from 'react';
import SearchRecordActions from '@/components/SearchRecordActions';
import { resolveGoodsLibraryEntries, type GoodsLibraryChatEntry } from '@/lib/goods-library-chat';
import { withDetailReturn } from '@/lib/detail-return';
import type { ChatSource } from '@/lib/chat-sse';

/**
 * 货号命中的商品库图片。每一张真实槽位用 img 直接展示，不走 markdown。
 * 回答正文里即使已经写过同一条签名地址或截断的「主图」语法，这里仍然出图。
 */
export default function GoodsLibraryChatMedia({
  sources,
  entries,
  returnTo,
}: {
  sources?: ChatSource[];
  entries?: GoodsLibraryChatEntry[] | unknown;
  returnTo?: string | null;
}) {
  const resolved = resolveGoodsLibraryEntries(sources, entries);
  if (resolved.length === 0) return null;
  return (
    <div data-testid="goods-library-chat-media" className="mb-6 space-y-3">
      {resolved.map((entry, index) => (
        <div key={`${entry.productDetailPath || entry.goodsNo}-${index}`}>
          {(entry.goodsNo || entry.productName) && (
            <p className="mb-1.5 text-[12px] leading-5 text-[#3A3A3C]">
              {entry.productDetailPath ? (
                <a
                  href={withDetailReturn(entry.productDetailPath, returnTo) || entry.productDetailPath}
                  data-testid="open-product-detail"
                  className="text-[#007AFF] underline underline-offset-2"
                >
                  {entry.goodsNo || '商品库'}
                </a>
              ) : (
                entry.goodsNo
              )}
              {entry.productName ? ` ${entry.productName}` : ''}
              {entry.sampler ? ` · 打样员 ${entry.sampler}` : ''}
            </p>
          )}
          {entry.images.length > 0 && (
            <div className="grid grid-cols-2 gap-2">
              {entry.images.map(image => (
                <figure key={`${image.slotLabel}-${image.imageUrl}`} className="min-w-0">
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
            returnTo={returnTo}
          />
        </div>
      ))}
    </div>
  );
}
