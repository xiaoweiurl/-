'use client';

import React from 'react';
import { Camera, ImagePlus, Loader2, X } from 'lucide-react';

export interface SamplerSameProductHit {
  scorePercent: number;
  imageUrl?: string | null;
  goodsNo?: string;
  productName?: string;
  sampler?: string;
}

/**
 * 打样表单上的轻量同款结果。只展示货号、品名、打样员和相似度。
 */
export default function SamplerSameProductSheet({
  open,
  loading,
  error,
  results,
  onClose,
  onPick,
  onFile,
}: {
  open: boolean;
  loading: boolean;
  error: string | null;
  results: SamplerSameProductHit[];
  onClose: () => void;
  onPick: (hit: SamplerSameProductHit) => void;
  onFile: (file: File | null) => void;
}) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-[80] bg-black/40 flex items-end" data-testid="sampler-same-product-sheet">
      <div className="bg-white w-full max-h-[88dvh] rounded-t-2xl shadow-lg flex flex-col pb-[max(0.75rem,env(safe-area-inset-bottom))]">
        <div className="flex items-center justify-between px-4 min-h-14 border-b border-[#E5E5EA]">
          <h2 className="text-[16px] font-semibold text-[#1C1C1E]">拍照查同款</h2>
          <button type="button" onClick={onClose} className="min-w-11 min-h-11 flex items-center justify-center text-[#8E8E93]" aria-label="关闭">
            <X className="w-5 h-5" />
          </button>
        </div>
        <div className="px-4 py-3 grid grid-cols-2 gap-2">
          <label className="min-h-12 rounded-xl bg-[#007AFF] text-white text-[15px] font-medium flex items-center justify-center gap-1.5">
            <Camera className="w-4 h-4" />
            拍照
            <input
              data-testid="sampler-search-camera"
              type="file"
              accept="image/*"
              capture="environment"
              className="sr-only"
              onChange={(event) => onFile(event.target.files?.[0] || null)}
            />
          </label>
          <label className="min-h-12 rounded-xl bg-[#F2F2F7] text-[#1C1C1E] text-[15px] font-medium flex items-center justify-center gap-1.5">
            <ImagePlus className="w-4 h-4" />
            相册
            <input
              data-testid="sampler-search-album"
              type="file"
              accept="image/*"
              className="sr-only"
              onChange={(event) => onFile(event.target.files?.[0] || null)}
            />
          </label>
        </div>
        <div className="flex-1 overflow-y-auto px-4 pb-2 space-y-2">
          {loading && (
            <p className="min-h-12 flex items-center justify-center gap-2 text-[14px] text-[#8E8E93]">
              <Loader2 className="w-4 h-4 animate-spin" />
              正在查找同款
            </p>
          )}
          {error && <p className="text-[13px] text-[#FF3B30]">{error}</p>}
          {results.map((hit, index) => (
            <button
              key={`${hit.goodsNo || 'goods'}-${index}`}
              type="button"
              data-testid="sampler-same-product-hit"
              onClick={() => onPick(hit)}
              className="w-full min-h-16 flex items-center gap-3 rounded-2xl border border-[#E5E5EA] p-2 text-left active:scale-[0.99] transition-transform"
            >
              <div className="w-16 h-16 rounded-xl bg-[#F2F2F7] overflow-hidden shrink-0">
                {hit.imageUrl ? <img src={hit.imageUrl} alt="" className="w-full h-full object-cover" /> : null}
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <span className="text-[15px] font-semibold text-[#007AFF]">{hit.scorePercent}%</span>
                  <span className="text-[15px] font-medium text-[#1C1C1E] truncate">{hit.goodsNo || '未填货号'}</span>
                </div>
                <p className="text-[13px] text-[#3A3A3C] truncate">{hit.productName || '未填品名'}</p>
                <p className="text-[12px] text-[#8E8E93] truncate">打样员 {hit.sampler || '未填写'}</p>
              </div>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
