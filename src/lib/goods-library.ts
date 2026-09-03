/**
 * 商品库数据操作模块
 *
 * 商品库 = 文件夹式商品管理：
 * - 第一层信息：发起人/打样员/品名/货号/客户/订单号（均可空）
 * - 第二层图片：主图/侧面图/细节/产品图（OSS 存储，key 落库）
 * - 备注信息：卖点/竞品/功能/对应人群/使用场景（均可空）
 * - 文件夹名 = 货号 + 品名；文件夹封面 = 主图
 */

import { getPool } from '@/lib/db';
import { S3Storage } from 'coze-coding-dev-sdk';

// ==========================================
// 类型定义
// ==========================================

export interface GoodsFolder {
  id: number;
  folder_name: string;
  initiator: string | null;
  sampler: string | null;
  product_name: string | null;
  goods_no: string | null;
  customer: string | null;
  order_no: string | null;
  main_image_key: string | null;
  side_image_key: string | null;
  detail_image_key: string | null;
  product_image_key: string | null;
  selling_points: string | null;
  competitors: string | null;
  features: string | null;
  target_audience: string | null;
  usage_scenarios: string | null;
  user_id: string | null;
  created_at: Date;
  updated_at: Date;
}

/** 图片槽位（第二层表格的四种图片） */
export const IMAGE_SLOTS = ['main', 'side', 'detail', 'product'] as const;
export type ImageSlot = (typeof IMAGE_SLOTS)[number];

export const SLOT_LABELS: Record<ImageSlot, string> = {
  main: '主图',
  side: '侧面图',
  detail: '细节',
  product: '产品图',
};

const SLOT_COLUMN: Record<ImageSlot, keyof GoodsFolder> = {
  main: 'main_image_key',
  side: 'side_image_key',
  detail: 'detail_image_key',
  product: 'product_image_key',
};

// ==========================================
// OSS 存储
// ==========================================

const storage = new S3Storage({
  endpointUrl: process.env.COZE_BUCKET_ENDPOINT_URL,
  bucketName: process.env.COZE_BUCKET_NAME,
  region: 'cn-beijing',
});

/**
 * 生成文件夹名称：货号 + 品名（任一为空则只用另一个，都为空给默认名）
 */
export function buildFolderName(goodsNo?: string | null, productName?: string | null): string {
  const no = (goodsNo || '').trim();
  const name = (productName || '').trim();
  if (no && name) return `${no}${name}`;
  if (no) return no;
  if (name) return name;
  return '未命名商品';
}

/**
 * OSS 对象键安全化：S3 key 仅允许字母/数字/._-/，中文等字符替换为 '_'
 * 数据库中的 folder_name 保留原始中文名用于展示
 */
function sanitizeForKey(s: string): string {
  return s.replace(/[^a-zA-Z0-9._-]/g, '_').substring(0, 120) || 'unnamed';
}

/** 上传商品图片到 OSS，目录结构 goods-library/{文件夹名}/{槽位}.{ext} */
export async function uploadGoodsImage(
  folderName: string,
  slot: ImageSlot,
  fileContent: Buffer,
  ext: string,
  contentType: string,
): Promise<string> {
  const safeExt = ext.replace(/[^a-zA-Z0-9]/g, '').substring(0, 10) || 'jpg';
  const fileName = `goods-library/${sanitizeForKey(folderName)}/${slot}.${safeExt}`;
  // uploadFile 返回带 UUID 前缀的实际 key，必须持久化返回值
  return storage.uploadFile({ fileContent, fileName, contentType });
}

/** 删除 OSS 图片（失败静默，不阻断主流程） */
export async function deleteGoodsImage(key: string | null): Promise<void> {
  if (!key) return;
  try {
    await storage.deleteFile({ fileKey: key });
  } catch (e) {
    console.warn('[商品库] 删除 OSS 图片失败(忽略):', key, e);
  }
}

/** 生成图片访问签名 URL（1 天有效） */
export async function signImageUrl(key: string | null): Promise<string | null> {
  if (!key) return null;
  try {
    return await storage.generatePresignedUrl({ key, expireTime: 86400 });
  } catch (e) {
    console.warn('[商品库] 生成签名URL失败:', key, e);
    return null;
  }
}

// ==========================================
// 数据库操作
// ==========================================

/** 图片 key 字段 → 签名 URL 字段的附加转换 */
async function withSignedUrls(row: GoodsFolder) {
  const [main, side, detail, product] = await Promise.all([
    signImageUrl(row.main_image_key),
    signImageUrl(row.side_image_key),
    signImageUrl(row.detail_image_key),
    signImageUrl(row.product_image_key),
  ]);
  return {
    ...row,
    main_image_url: main,
    side_image_url: side,
    detail_image_url: detail,
    product_image_url: product,
  };
}

/** 商品库列表（按创建时间倒序，附主图签名 URL 作为文件夹封面） */
export async function listGoodsFolders(keyword?: string) {
  const client = await getPool().connect();
  try {
    const params: string[] = [];
    let where = '';
    if (keyword && keyword.trim()) {
      params.push(`%${keyword.trim()}%`);
      where = `WHERE folder_name ILIKE $1 OR goods_no ILIKE $1 OR product_name ILIKE $1 OR customer ILIKE $1 OR order_no ILIKE $1`;
    }
    const result = await client.query(
      `SELECT * FROM goods_library ${where} ORDER BY created_at DESC LIMIT 500`,
      params,
    );
    return Promise.all((result.rows as GoodsFolder[]).map(withSignedUrls));
  } finally {
    client.release();
  }
}

/** 商品详情（附全部图片签名 URL） */
export async function getGoodsFolder(id: number) {
  const client = await getPool().connect();
  try {
    const result = await client.query('SELECT * FROM goods_library WHERE id = $1', [id]);
    if (result.rows.length === 0) return null;
    return withSignedUrls(result.rows[0] as GoodsFolder);
  } finally {
    client.release();
  }
}

export interface GoodsFolderInput {
  initiator?: string | null;
  sampler?: string | null;
  product_name?: string | null;
  goods_no?: string | null;
  customer?: string | null;
  order_no?: string | null;
  selling_points?: string | null;
  competitors?: string | null;
  features?: string | null;
  target_audience?: string | null;
  usage_scenarios?: string | null;
}

/** 创建商品文件夹（自动按 货号+品名 生成文件夹名） */
export async function createGoodsFolder(input: GoodsFolderInput, userId: string | null) {
  const folderName = buildFolderName(input.goods_no, input.product_name);
  const client = await getPool().connect();
  try {
    const result = await client.query(
      `INSERT INTO goods_library
        (folder_name, initiator, sampler, product_name, goods_no, customer, order_no,
         selling_points, competitors, features, target_audience, usage_scenarios, user_id)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13)
       RETURNING *`,
      [
        folderName,
        input.initiator || null,
        input.sampler || null,
        input.product_name || null,
        input.goods_no || null,
        input.customer || null,
        input.order_no || null,
        input.selling_points || null,
        input.competitors || null,
        input.features || null,
        input.target_audience || null,
        input.usage_scenarios || null,
        userId,
      ],
    );
    return result.rows[0] as GoodsFolder;
  } finally {
    client.release();
  }
}

/** 更新商品文件夹信息（货号/品名变化时重算文件夹名） */
export async function updateGoodsFolder(id: number, input: GoodsFolderInput) {
  const client = await getPool().connect();
  try {
    // 先取当前值，合并后重算 folder_name
    const current = await client.query('SELECT * FROM goods_library WHERE id = $1', [id]);
    if (current.rows.length === 0) return null;
    const cur = current.rows[0] as GoodsFolder;

    const merged = {
      initiator: input.initiator !== undefined ? input.initiator : cur.initiator,
      sampler: input.sampler !== undefined ? input.sampler : cur.sampler,
      product_name: input.product_name !== undefined ? input.product_name : cur.product_name,
      goods_no: input.goods_no !== undefined ? input.goods_no : cur.goods_no,
      customer: input.customer !== undefined ? input.customer : cur.customer,
      order_no: input.order_no !== undefined ? input.order_no : cur.order_no,
      selling_points: input.selling_points !== undefined ? input.selling_points : cur.selling_points,
      competitors: input.competitors !== undefined ? input.competitors : cur.competitors,
      features: input.features !== undefined ? input.features : cur.features,
      target_audience: input.target_audience !== undefined ? input.target_audience : cur.target_audience,
      usage_scenarios: input.usage_scenarios !== undefined ? input.usage_scenarios : cur.usage_scenarios,
    };
    const folderName = buildFolderName(merged.goods_no, merged.product_name);

    const result = await client.query(
      `UPDATE goods_library SET
        folder_name=$1, initiator=$2, sampler=$3, product_name=$4, goods_no=$5, customer=$6, order_no=$7,
        selling_points=$8, competitors=$9, features=$10, target_audience=$11, usage_scenarios=$12,
        updated_at=now()
       WHERE id=$13 RETURNING *`,
      [
        folderName, merged.initiator, merged.sampler, merged.product_name, merged.goods_no,
        merged.customer, merged.order_no, merged.selling_points, merged.competitors,
        merged.features, merged.target_audience, merged.usage_scenarios, id,
      ],
    );
    return result.rows[0] as GoodsFolder;
  } finally {
    client.release();
  }
}

/** 更新某个图片槽位的 OSS key */
export async function updateGoodsImageKey(id: number, slot: ImageSlot, key: string | null) {
  const column = SLOT_COLUMN[slot];
  const client = await getPool().connect();
  try {
    const result = await client.query(
      `UPDATE goods_library SET ${column} = $1, updated_at = now() WHERE id = $2 RETURNING *`,
      [key, id],
    );
    return result.rows.length > 0 ? (result.rows[0] as GoodsFolder) : null;
  } finally {
    client.release();
  }
}

/** 删除商品文件夹（返回记录以便调用方清理 OSS 图片） */
export async function deleteGoodsFolder(id: number) {
  const client = await getPool().connect();
  try {
    const result = await client.query('DELETE FROM goods_library WHERE id = $1 RETURNING *', [id]);
    return result.rows.length > 0 ? (result.rows[0] as GoodsFolder) : null;
  } finally {
    client.release();
  }
}
