import type { ChatSource, VisualThumb } from '@/lib/chat-sse';
import { visibleRecordLinks } from '@/lib/image-search-links';

export interface GoodsAnswerLink {
  goodsNo: string;
  productDetailPath?: string | null;
}

export interface GoodsLibraryChatImage {
  imageUrl: string;
  slotLabel: string;
}

export interface GoodsLibraryChatEntry {
  goodsNo: string;
  sampleOrderPath: string | null;
  productDetailPath: string | null;
  images: GoodsLibraryChatImage[];
}

const SLOT_ORDER = ['主图', '侧面图', '细节图', '产品图'];

/**
 * 对话来源里已经核对过公司的商品库记录。以图搜图卡片不走这里。
 */
export function goodsLibraryChatEntries(sources?: ChatSource[] | null): GoodsLibraryChatEntry[] {
  const entries: GoodsLibraryChatEntry[] = [];
  for (const source of sources || []) {
    if (!source || source.source === 'visual_match' || source.title !== '商品库文件夹') continue;
    const links = visibleRecordLinks(source);
    if (!links.productDetailPath?.startsWith('/goods-library/') && !links.sampleOrderPath) continue;
    entries.push({
      goodsNo: (source.goodsNo || '').trim(),
      sampleOrderPath: links.sampleOrderPath,
      productDetailPath: links.productDetailPath,
      images: orderedImages(source.images),
    });
  }
  return entries;
}

export function goodsAnswerLinks(sources?: ChatSource[] | null): GoodsAnswerLink[] {
  return goodsLibraryChatEntries(sources)
    .filter(entry => entry.goodsNo && entry.productDetailPath)
    .map(entry => ({ goodsNo: entry.goodsNo, productDetailPath: entry.productDetailPath }));
}

/**
 * 去掉模型复述的槽位图片。完整签名 URL 保留，截断地址删掉，避免裂图盖住真图。
 */
export function withoutBrokenSlotImages(content: string, sources?: ChatSource[] | null): string {
  if (!content) return content;
  const known = new Set(goodsLibraryChatEntries(sources).flatMap(entry => entry.images.map(image => image.imageUrl)));
  if (known.size === 0) return content;
  const slotImage = /!\[(主图|侧面图|细节图|产品图)(图片)?\]\(([^)\n]*)\)?/g;
  return content.replace(slotImage, (all, _slot: string, _suffix: string, url: string) => {
    const raw = String(url || '');
    const decoded = raw.replace(/%28/g, '(').replace(/%29/g, ')');
    return known.has(raw) || known.has(decoded) ? all : '';
  });
}

function orderedImages(images?: VisualThumb[]): GoodsLibraryChatImage[] {
  const bySlot = new Map<string, GoodsLibraryChatImage>();
  const extras: GoodsLibraryChatImage[] = [];
  for (const image of images || []) {
    const imageUrl = (image?.imageUrl || '').trim();
    if (!/^https?:\/\//i.test(imageUrl)) continue;
    const slotLabel = (image.slotLabel || '').trim() || '商品图片';
    const item = { imageUrl, slotLabel };
    if (SLOT_ORDER.includes(slotLabel)) {
      if (!bySlot.has(slotLabel)) bySlot.set(slotLabel, item);
    } else {
      extras.push(item);
    }
  }
  return [...SLOT_ORDER.flatMap(slot => {
    const item = bySlot.get(slot);
    return item ? [item] : [];
  }), ...extras];
}
