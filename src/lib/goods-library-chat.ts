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
  productName?: string;
  sampler?: string;
  sampleOrderPath: string | null;
  productDetailPath: string | null;
  images: GoodsLibraryChatImage[];
}

const SLOT_ORDER = ['主图', '侧面图', '细节图', '产品图'];
const SLOT_IMAGE = /!\[(主图|侧面图|细节图|产品图)(图片)?\]\([^)\n]*\)?/g;

/**
 * 对话来源里已经核对过公司的商品库记录。以图搜图卡片不走这里。
 */
export function goodsLibraryChatEntries(sources?: ChatSource[] | null): GoodsLibraryChatEntry[] {
  const entries: GoodsLibraryChatEntry[] = [];
  for (const source of sources || []) {
    const entry = entryFromSource(source);
    if (entry) entries.push(entry);
  }
  return entries;
}

/**
 * `goods_library` SSE 事件里的条目。路径和图片地址再验一次，外链和以图搜图卡片丢弃。
 */
export function normalizeGoodsLibraryEntries(raw: unknown): GoodsLibraryChatEntry[] {
  if (!Array.isArray(raw)) return [];
  const sources: ChatSource[] = raw.map((item) => {
    const row = (item && typeof item === 'object' ? item : {}) as ChatSource;
    return {
      ...row,
      title: '商品库文件夹',
      source: row.source === 'visual_match' ? 'visual_match' : 'supply_chain',
    };
  });
  return goodsLibraryChatEntries(sources);
}

/**
 * 来源事件和独立的商品库事件合并。来源被截掉图片时，仍用商品库事件里的签名地址。
 */
export function resolveGoodsLibraryEntries(
  sources?: ChatSource[] | null,
  explicit?: unknown,
): GoodsLibraryChatEntry[] {
  const merged = new Map<string, ChatSource>();
  const absorb = (entry: GoodsLibraryChatEntry) => {
    const key = entry.productDetailPath || entry.sampleOrderPath || entry.goodsNo || 'goods';
    const prev = merged.get(key);
    const images = [...(prev?.images || []), ...entry.images];
    merged.set(key, {
      title: '商品库文件夹',
      source: 'supply_chain',
      goodsNo: prev?.goodsNo || entry.goodsNo,
      productName: prev?.productName || entry.productName,
      sampler: prev?.sampler || entry.sampler,
      productDetailPath: prev?.productDetailPath || entry.productDetailPath || undefined,
      sampleOrderPath: prev?.sampleOrderPath || entry.sampleOrderPath || undefined,
      images,
    });
  };
  for (const entry of normalizeGoodsLibraryEntries(explicit)) absorb(entry);
  for (const entry of goodsLibraryChatEntries(sources)) absorb(entry);
  return goodsLibraryChatEntries([...merged.values()]);
}

export function goodsAnswerLinks(sources?: ChatSource[] | null, explicit?: unknown): GoodsAnswerLink[] {
  return resolveGoodsLibraryEntries(sources, explicit)
    .filter(entry => entry.goodsNo && entry.productDetailPath)
    .map(entry => ({ goodsNo: entry.goodsNo, productDetailPath: entry.productDetailPath }));
}

/**
 * 回答里已经有商品库照片时，删掉模型或旧附录写的槽位图片语法。
 * 真图由 img 直接渲染，截断的签名地址不再占位。
 */
export function withoutBrokenSlotImages(
  content: string,
  sources?: ChatSource[] | null,
  explicit?: unknown,
): string {
  if (!content) return content;
  const known = resolveGoodsLibraryEntries(sources, explicit).flatMap(entry => entry.images);
  if (known.length === 0) return content;
  return content.replace(SLOT_IMAGE, '');
}

function entryFromSource(source?: ChatSource | null): GoodsLibraryChatEntry | null {
  if (!source || source.source === 'visual_match' || source.title !== '商品库文件夹') return null;
  const links = visibleRecordLinks(source);
  if (!links.productDetailPath?.startsWith('/goods-library/') && !links.sampleOrderPath) return null;
  const productName = (source.productName || '').trim();
  const sampler = (source.sampler || '').trim();
  return {
    goodsNo: (source.goodsNo || '').trim(),
    ...(productName ? { productName } : {}),
    ...(sampler ? { sampler } : {}),
    sampleOrderPath: links.sampleOrderPath,
    productDetailPath: links.productDetailPath,
    images: orderedImages(source.images),
  };
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
