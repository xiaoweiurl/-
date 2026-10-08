'use client';

import React from 'react';
import { visibleRecordLinks, type RecordLinkInput } from '@/lib/image-search-links';

/**
 * 从检索卡片继续打开已有打样单或商品详情。没有核对过的路径时不渲染。
 */
export default function SearchRecordActions({
  sampleOrderPath,
  productDetailPath,
  className = '',
}: RecordLinkInput & { className?: string }) {
  const links = visibleRecordLinks({ sampleOrderPath, productDetailPath });
  if (!links.sampleOrderPath && !links.productDetailPath) return null;
  return (
    <div className={`flex flex-wrap items-center gap-x-3 ${className}`} data-testid="search-record-actions">
      {links.sampleOrderPath && (
        <a
          href={links.sampleOrderPath}
          data-testid="open-sample-order"
          aria-label="打开打样单"
          onClick={(event) => event.stopPropagation()}
          className="inline-flex items-center min-h-11 text-[13px] text-[#007AFF]"
        >
          打样单
        </a>
      )}
      {links.productDetailPath && (
        <a
          href={links.productDetailPath}
          data-testid="open-product-detail"
          aria-label="打开商品详情"
          onClick={(event) => event.stopPropagation()}
          className="inline-flex items-center min-h-11 text-[13px] text-[#007AFF]"
        >
          商品详情
        </a>
      )}
    </div>
  );
}
