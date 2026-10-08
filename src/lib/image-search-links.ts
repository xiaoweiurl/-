/**
 * 只接受后端核对过的站内路径。查询串、外链和路径穿越都不渲染。
 */

const GOODS_ID = /^[1-9]\d{0,18}$/;
const PRODUCT_ID = /^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$/;

export interface RecordLinkInput {
  sampleOrderPath?: string | null;
  productDetailPath?: string | null;
}

export interface VisibleRecordLinks {
  sampleOrderPath: string | null;
  productDetailPath: string | null;
}

export function visibleRecordLinks(input: RecordLinkInput | null | undefined): VisibleRecordLinks {
  return {
    sampleOrderPath: sampleOrderHref(input?.sampleOrderPath),
    productDetailPath: productDetailHref(input?.productDetailPath),
  };
}

export function sampleOrderHref(path?: string | null): string | null {
  return matchPath(path, '/sampler/') ? path!.trim() : null;
}

export function productDetailHref(path?: string | null): string | null {
  const value = path?.trim();
  if (!value) return null;
  if (matchPath(value, '/goods-library/')) return value;
  if (value.startsWith('/products/')) {
    const id = value.slice('/products/'.length);
    return PRODUCT_ID.test(id) ? value : null;
  }
  return null;
}

function matchPath(path: string | null | undefined, prefix: string): boolean {
  const value = path?.trim();
  if (!value || !value.startsWith(prefix)) return false;
  return GOODS_ID.test(value.slice(prefix.length));
}
