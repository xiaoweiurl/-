'use client';

import React from 'react';
import { Camera, ImagePlus } from 'lucide-react';

/**
 * 拍照和相册分开。拍照带 capture，方便手机和钉钉直接调起后置摄像头。
 */
export default function ImageSearchCapture({
  preview,
  onPick,
}: {
  preview: string | null;
  onPick: (file: File | null) => void;
}) {
  return (
    <div className="space-y-2" data-testid="image-search-capture">
      <div className="w-full min-h-28 rounded-2xl border border-dashed border-[#E5E5EA] bg-[#F2F2F7] flex items-center justify-center overflow-hidden">
        {preview ? (
          <img src={preview} alt="待检索图片" className="max-h-40 w-full object-contain" />
        ) : (
          <span className="text-[13px] text-[#8E8E93] px-4 text-center">拍一张款式图，或从相册选</span>
        )}
      </div>
      <div className="grid grid-cols-2 gap-2">
        <label className="min-h-12 rounded-xl bg-[#007AFF] text-white text-[15px] font-medium flex items-center justify-center gap-1.5 active:scale-[0.98] transition-transform">
          <Camera className="w-4 h-4" />
          拍照
          <input
            data-testid="image-search-camera"
            type="file"
            accept="image/*"
            capture="environment"
            className="sr-only"
            onChange={(event) => onPick(event.target.files?.[0] || null)}
          />
        </label>
        <label className="min-h-12 rounded-xl bg-[#F2F2F7] text-[#1C1C1E] text-[15px] font-medium flex items-center justify-center gap-1.5 active:scale-[0.98] transition-transform">
          <ImagePlus className="w-4 h-4" />
          相册
          <input
            data-testid="image-search-album"
            type="file"
            accept="image/jpeg,image/png,image/webp,image/gif,image/bmp,image/*"
            className="sr-only"
            onChange={(event) => onPick(event.target.files?.[0] || null)}
          />
        </label>
      </div>
    </div>
  );
}
