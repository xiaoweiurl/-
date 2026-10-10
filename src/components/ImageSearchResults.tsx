'use client';

import React from 'react';
import SearchRecordActions from './SearchRecordActions';

interface GoodsBrief {
  id: number;
  folderName?: string;
  goodsNo?: string;
  productName?: string;
  sampler?: string;
}

interface SearchThumb {
  imageUrl?: string;
  slotLabel?: string;
}

export interface ImageSearchResultHit {
  scorePercent: number;
  source: string;
  sourceId: string;
  slot?: string;
  slotLabel?: string;
  title?: string;
  imageUrl?: string | null;
  albumName?: string;
  productId?: string;
  cardType?: string;
  scenario?: string;
  images?: SearchThumb[];
  goods?: GoodsBrief | null;
  relatedGoods?: GoodsBrief[];
  sampleOrderPath?: string | null;
  productDetailPath?: string | null;
}

function goodsLine(goods: GoodsBrief): string {
  return [goods.goodsNo, goods.productName].filter(Boolean).join(' ') || goods.folderName || '打样记录';
}

/** 商品卡上的货号以 goods 为准。素材只有唯一一条关联时才显示，多条对不上就不猜货号。 */
function confirmedLink(hit: ImageSearchResultHit): GoodsBrief | undefined {
  if (hit.goods) return hit.goods;
  const related = (hit.relatedGoods || []).filter(item => item && (item.goodsNo || item.productName || item.id));
  if (related.length === 1) return related[0];
  return undefined;
}

/**
 * 弹层结果。同款是一张商品卡（主图 + 小图 + 货号），素材是一张图一条。
 */
export default function ImageSearchResults({
  results,
  scenario = null,
}: {
  results: ImageSearchResultHit[];
  scenario?: string | null;
}) {
  return (
    <div className="space-y-3" data-testid="image-search-results" data-scenario={scenario || ''}>
      {results.map((hit) => {
        const linked = confirmedLink(hit);
        const product = hit.cardType === 'product';
        const thumbs = (hit.images || []).filter(thumb => thumb.imageUrl).slice(0, 4);
        return (
          <div
            key={`${hit.cardType || 'image'}-${hit.source}-${hit.sourceId}-${hit.slot || ''}-${hit.slotLabel || ''}`}
            data-testid={product ? 'search-product-card' : 'search-image-card'}
            data-scenario={hit.scenario || scenario || ''}
            className="flex gap-3 rounded-2xl border border-[#E5E5EA] p-3 min-h-16"
          >
            <div className="w-20 h-20 rounded-xl bg-[#F2F2F7] overflow-hidden shrink-0">
              {hit.imageUrl ? <img src={hit.imageUrl} alt="" className="w-full h-full object-cover" /> : null}
            </div>
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <span className="text-[15px] font-semibold text-[#007AFF]">{hit.scorePercent}%</span>
                <span className="text-[11px] px-1.5 py-0.5 rounded-md bg-[#F2F2F7] text-[#3A3A3C]">
                  {product ? '同款' : hit.source === 'goods' ? '打样' : '素材'}
                </span>
                {hit.slotLabel && hit.source === 'goods' && !product && (
                  <span className="text-[11px] text-[#8E8E93]">{hit.slotLabel}</span>
                )}
              </div>
              <p className="text-[13px] text-[#1C1C1E] truncate">{hit.title || (linked ? goodsLine(linked) : '未命名')}</p>
              {hit.albumName && !product && <p className="text-[12px] text-[#8E8E93] truncate">{hit.albumName}</p>}
              {linked && (
                <p className="flex items-center min-h-11 text-[13px] text-[#007AFF] truncate">
                  {goodsLine(linked)}
                  {linked.sampler ? ` · 打样员 ${linked.sampler}` : ''}
                </p>
              )}
              {hit.productId && !linked && (
                <p className="text-[12px] text-[#8E8E93] truncate">款号 {hit.productId}</p>
              )}
              <SearchRecordActions
                sampleOrderPath={hit.sampleOrderPath}
                productDetailPath={hit.productDetailPath}
              />
              {product && thumbs.length > 0 && (
                <div className="mt-1.5 flex gap-1">
                  {thumbs.map((thumb, index) => (
                    <img
                      key={`${thumb.imageUrl}-${index}`}
                      src={thumb.imageUrl}
                      alt={thumb.slotLabel || '同款其他图片'}
                      data-testid="search-product-thumb"
                      className="w-8 h-8 rounded-md object-cover bg-[#F2F2F7]"
                    />
                  ))}
                </div>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}
