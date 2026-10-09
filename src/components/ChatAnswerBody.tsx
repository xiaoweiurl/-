'use client';

import React from 'react';
import MarkdownRenderer from '@/components/MarkdownRenderer';
import GoodsLibraryChatMedia from '@/components/GoodsLibraryChatMedia';
import { goodsAnswerLinks, withoutBrokenSlotImages } from '@/lib/goods-library-chat';
import type { ChatSource } from '@/lib/chat-sse';

/**
 * 供应链和设计师对话共用的回答正文。
 * 商品库照片放在文字前面，用 img 渲染；货号和打样单仍是站内链接。
 */
export default function ChatAnswerBody({
  content,
  sources,
  goodsLibrary,
  darkMode = false,
  citeIds,
  onCite,
}: {
  content: string;
  sources?: ChatSource[];
  goodsLibrary?: unknown;
  darkMode?: boolean;
  citeIds?: string[];
  onCite?: (id: string) => void;
}) {
  return (
    <>
      <GoodsLibraryChatMedia sources={sources} entries={goodsLibrary} />
      <MarkdownRenderer
        content={withoutBrokenSlotImages(content || '', sources, goodsLibrary)}
        darkMode={darkMode}
        citeIds={citeIds}
        goodsLinks={goodsAnswerLinks(sources, goodsLibrary)}
        onCite={onCite}
      />
    </>
  );
}
