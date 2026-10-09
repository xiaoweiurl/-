'use client';

import React from 'react';
import { visibleRecordLinks, type RecordLinkInput } from '@/lib/image-search-links';
import { withDetailReturn } from '@/lib/detail-return';

/**
 * 从检索卡片继续打开已有打样单或商品详情。没有核对过的路径时不渲染。
 */
export default function SearchRecordActions({
  sampleOrderPath,
  productDetailPath,
  className = '',
  returnTo,
}: RecordLinkInput & { className?: string; returnTo?: string | null }) {
  const links = visibleRecordLinks({ sampleOrderPath, productDetailPath });
  const sampleHref = withDetailReturn(links.sampleOrderPath, returnTo);
  const detailHref = withDetailReturn(links.productDetailPath, returnTo);
  if (!sampleHref && !detailHref) return null;
  return (
    <div className={`flex flex-wrap items-center gap-x-3 ${className}`} data-testid="search-record-actions">
      {sampleHref && (
        <a
          href={sampleHref}
          data-testid="open-sample-order"
          aria-label="打开打样单"
          onClick={(event) => event.stopPropagation()}
          className="inline-flex items-center min-h-11 text-[13px] text-[#007AFF]"
        >
          打样单
        </a>
      )}
      {detailHref && (
        <a
          href={detailHref}
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
