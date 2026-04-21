/**
 * 数据库操作工具
 * 用于图片管理系统的数据持久化
 * 
 * 表结构：
 * - users: 用户信息
 * - albums: 相册信息
 * - album_keywords: 相册关键词（用于自动分类）
 * - images: 图片信息
 * - image_tags: 图片标签
 * - image_ai_tags: AI识别标签
 * - notifications: 用户通知
 */

import { Pool } from 'pg';

// 数据库连接池
let pool: Pool | null = null;

/**
 * 图片记录类型（对应 images 表）
 */
export interface ImageRecord {
  id: string;
  title: string;
  description: string | null;
  url: string;
  thumbnail_url: string | null;
  file_key: string | null;
  size: number;
  size_formatted: string | null;
  width: number | null;
  height: number | null;
  resolution: string | null;
  file_type: string;
  album_id: string | null;
  album_name: string | null;
  favorite: boolean;
  ai_confidence: number | null;
  classify_method: string | null;
  created_at: Date;
  updated_at: Date;
  user_id: string | null;
  deleted: boolean;
  deleted_at: Date | null;
  view_count: number;
  // 关联数据
  tags?: string[];
  ai_tags?: string[];
}

/**
 * 相册记录类型（对应 albums 表）
 */
export interface AlbumRecord {
  id: string;
  name: string;
  description: string | null;
  cover_url: string | null;
  image_count: number;
  sort_order: number;
  is_system: boolean;
  created_at: Date;
  updated_at: Date;
  user_id: string | null;
  keywords?: string[];
}

/**
 * 用户记录类型（对应 users 表）
 */
export interface UserRecord {
  id: string;
  username: string;
  email: string;
  avatar_url: string | null;
  role: string;
  membership: string;
  storage_used: number;
  storage_limit: number;
  created_at: Date;
  last_login_at: Date | null;
}

/**
 * 通知记录类型（对应 notifications 表）
 */
export interface NotificationRecord {
  id: string;
  title: string | null;
  content: string | null;
  type: string;
  read: boolean;
  created_at: Date;
  resource_id: string | null;
  user_id: string | null;
}

/**
 * 获取数据库连接池
 */
export function getPool(): Pool {
  if (!pool) {
    const databaseUrl = process.env.PGDATABASE_URL;
    if (!databaseUrl) {
      throw new Error('PGDATABASE_URL environment variable is not set');
    }
    
    pool = new Pool({
      connectionString: databaseUrl,
      ssl: {
        rejectUnauthorized: false,
      },
    });
    
    console.log('[DB] 数据库连接池已创建');
  }
  return pool;
}

/**
 * 检查数据库是否可用
 */
export async function isDatabaseAvailable(): Promise<boolean> {
  try {
    const client = await getPool().connect();
    await client.query('SELECT 1');
    client.release();
    return true;
  } catch (error) {
    console.error('[DB] 数据库连接失败:', error);
    return false;
  }
}

// ==========================================
// 图片相关操作
// ==========================================

/**
 * 获取所有图片
 */
export async function getAllImages(options: {
  page?: number;
  pageSize?: number;
  albumId?: string;
  tag?: string;
  favorite?: boolean;
  includeDeleted?: boolean;
}): Promise<{ images: ImageRecord[]; total: number }> {
  const client = await getPool().connect();
  try {
    const {
      page = 1,
      pageSize = 24,
      albumId,
      tag,
      favorite,
      includeDeleted = false,
    } = options;

    let whereClause = includeDeleted ? '1=1' : 'deleted = FALSE';
    const params: (string | number | boolean)[] = [];
    let paramIndex = 1;

    if (albumId) {
      whereClause += ` AND album_id = $${paramIndex++}`;
      params.push(albumId);
    }

    if (favorite !== undefined) {
      whereClause += ` AND favorite = $${paramIndex++}`;
      params.push(favorite);
    }

    // 获取总数
    const countResult = await client.query(
      `SELECT COUNT(*) as total FROM images WHERE ${whereClause}`,
      params
    );
    const total = parseInt(countResult.rows[0].total, 10);

    // 分页查询图片
    const offset = (page - 1) * pageSize;
    const imagesResult = await client.query(
      `SELECT * FROM images WHERE ${whereClause} ORDER BY created_at DESC LIMIT $${paramIndex++} OFFSET $${paramIndex++}`,
      [...params, pageSize, offset]
    );

    // 获取每张图片的标签
    const images = await Promise.all(
      imagesResult.rows.map(async (row) => {
        const tagsResult = await client.query(
          'SELECT tag FROM image_tags WHERE image_id = $1',
          [row.id]
        );
        return {
          ...row,
          tags: tagsResult.rows.map((r) => r.tag),
        };
      })
    );

    return { images, total };
  } finally {
    client.release();
  }
}

/**
 * 根据 ID 获取图片
 */
export async function getImageById(id: string): Promise<ImageRecord | null> {
  const client = await getPool().connect();
  try {
    const result = await client.query(
      'SELECT * FROM images WHERE id = $1',
      [id]
    );
    
    if (result.rows.length === 0) {
      return null;
    }

    // 获取标签
    const tagsResult = await client.query(
      'SELECT tag FROM image_tags WHERE image_id = $1',
      [id]
    );

    return {
      ...result.rows[0],
      tags: tagsResult.rows.map((r) => r.tag),
    };
  } finally {
    client.release();
  }
}

/**
 * 插入图片
 */
export async function insertImage(image: {
  id: string;
  url: string;
  title: string;
  size?: number;
  size_formatted?: string;
  resolution?: string;
  file_type?: string;
  album_id?: string | null;
  album_name?: string | null;
  tags?: string[];
  file_key?: string;
  width?: number;
  height?: number;
  user_id?: string;
}): Promise<ImageRecord> {
  const client = await getPool().connect();
  try {
    await client.query('BEGIN');

    const result = await client.query(
      `INSERT INTO images (
        id, url, title, size, size_formatted, resolution, file_type,
        album_id, album_name, file_key, width, height, user_id,
        favorite, deleted, view_count, created_at, updated_at
      ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, FALSE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
      RETURNING *`,
      [
        image.id,
        image.url,
        image.title,
        image.size || 0,
        image.size_formatted || null,
        image.resolution || null,
        image.file_type || 'jpg',
        image.album_id || null,
        image.album_name || null,
        image.file_key || null,
        image.width || null,
        image.height || null,
        image.user_id || 'user-1',
      ]
    );

    // 插入标签
    if (image.tags && image.tags.length > 0) {
      for (const tag of image.tags) {
        await client.query(
          'INSERT INTO image_tags (image_id, tag) VALUES ($1, $2) ON CONFLICT DO NOTHING',
          [image.id, tag]
        );
      }
    }

    await client.query('COMMIT');
    console.log(`[DB] 插入图片: ${image.title}`);
    
    return {
      ...result.rows[0],
      tags: image.tags || [],
    };
  } catch (error) {
    await client.query('ROLLBACK');
    throw error;
  } finally {
    client.release();
  }
}

/**
 * 更新图片
 */
export async function updateImage(
  id: string,
  updates: Partial<{
    title: string;
    description: string;
    album_id: string | null;
    album_name: string | null;
    favorite: boolean;
    deleted: boolean;
    deleted_at: Date | null;
    tags: string[];
  }>
): Promise<ImageRecord | null> {
  const client = await getPool().connect();
  try {
    await client.query('BEGIN');

    // 构建更新语句
    const setClauses: string[] = [];
    const params: (string | boolean | Date | null)[] = [id];
    let paramIndex = 2;

    const simpleFields = ['title', 'description', 'album_id', 'album_name', 'favorite', 'deleted', 'deleted_at'] as const;
    
    for (const field of simpleFields) {
      if (field in updates) {
        setClauses.push(`${field} = $${paramIndex++}`);
        params.push(updates[field] as string | boolean | Date | null);
      }
    }

    if (setClauses.length > 0) {
      setClauses.push('updated_at = CURRENT_TIMESTAMP');
      
      const result = await client.query(
        `UPDATE images SET ${setClauses.join(', ')} WHERE id = $1 RETURNING *`,
        params
      );

      if (result.rows.length === 0) {
        await client.query('ROLLBACK');
        return null;
      }
    }

    // 更新标签
    if (updates.tags !== undefined) {
      // 先删除旧标签
      await client.query('DELETE FROM image_tags WHERE image_id = $1', [id]);
      
      // 插入新标签
      for (const tag of updates.tags) {
        await client.query(
          'INSERT INTO image_tags (image_id, tag) VALUES ($1, $2)',
          [id, tag]
        );
      }
    }

    await client.query('COMMIT');
    console.log(`[DB] 更新图片: ${id}`);

    return getImageById(id);
  } catch (error) {
    await client.query('ROLLBACK');
    throw error;
  } finally {
    client.release();
  }
}

/**
 * 批量更新图片标签
 */
export async function updateImageTags(imageIds: string[], tags: string[]): Promise<number> {
  const client = await getPool().connect();
  try {
    await client.query('BEGIN');

    for (const imageId of imageIds) {
      // 删除旧标签
      await client.query('DELETE FROM image_tags WHERE image_id = $1', [imageId]);
      
      // 插入新标签
      for (const tag of tags) {
        await client.query(
          'INSERT INTO image_tags (image_id, tag) VALUES ($1, $2)',
          [imageId, tag]
        );
      }
    }

    await client.query('COMMIT');
    console.log(`[DB] 更新 ${imageIds.length} 张图片的标签`);
    return imageIds.length;
  } catch (error) {
    await client.query('ROLLBACK');
    throw error;
  } finally {
    client.release();
  }
}

/**
 * 标记图片为已删除（软删除）
 */
export async function softDeleteImages(imageIds: string[]): Promise<number> {
  const client = await getPool().connect();
  try {
    const result = await client.query(
      `UPDATE images SET deleted = TRUE, deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP 
       WHERE id = ANY($1) AND deleted = FALSE`,
      [imageIds]
    );

    console.log(`[DB] 软删除 ${result.rowCount} 张图片`);
    return result.rowCount || 0;
  } finally {
    client.release();
  }
}

/**
 * 恢复已删除的图片
 */
export async function restoreImages(imageIds: string[]): Promise<number> {
  const client = await getPool().connect();
  try {
    const result = await client.query(
      `UPDATE images SET deleted = FALSE, deleted_at = NULL, updated_at = CURRENT_TIMESTAMP 
       WHERE id = ANY($1) AND deleted = TRUE`,
      [imageIds]
    );

    console.log(`[DB] 恢复 ${result.rowCount} 张图片`);
    return result.rowCount || 0;
  } finally {
    client.release();
  }
}

/**
 * 永久删除图片
 */
export async function permanentlyDeleteImages(imageIds: string[]): Promise<number> {
  const client = await getPool().connect();
  try {
    const result = await client.query(
      'DELETE FROM images WHERE id = ANY($1)',
      [imageIds]
    );

    console.log(`[DB] 永久删除 ${result.rowCount} 张图片`);
    return result.rowCount || 0;
  } finally {
    client.release();
  }
}

/**
 * 切换收藏状态
 */
export async function toggleFavorite(id: string): Promise<ImageRecord | null> {
  const client = await getPool().connect();
  try {
    const result = await client.query(
      `UPDATE images SET favorite = NOT favorite, updated_at = CURRENT_TIMESTAMP 
       WHERE id = $1 AND deleted = FALSE 
       RETURNING *`,
      [id]
    );

    if (result.rows.length === 0) {
      return null;
    }

    console.log(`[DB] 切换收藏: ${id}`);
    
    return {
      ...result.rows[0],
      tags: [],
    };
  } finally {
    client.release();
  }
}

/**
 * 获取所有标签
 */
export async function getAllTags(): Promise<string[]> {
  const client = await getPool().connect();
  try {
    const result = await client.query(
      'SELECT DISTINCT tag FROM image_tags WHERE image_id IN (SELECT id FROM images WHERE deleted = FALSE) ORDER BY tag'
    );
    return result.rows.map((row) => row.tag);
  } finally {
    client.release();
  }
}

/**
 * 移动图片到相册
 */
export async function moveImagesToAlbum(imageIds: string[], albumId: string, albumName: string): Promise<number> {
  const client = await getPool().connect();
  try {
    const result = await client.query(
      `UPDATE images SET album_id = $1, album_name = $2, updated_at = CURRENT_TIMESTAMP 
       WHERE id = ANY($3) AND deleted = FALSE`,
      [albumId, albumName, imageIds]
    );

    console.log(`[DB] 移动 ${result.rowCount} 张图片到相册: ${albumName}`);
    return result.rowCount || 0;
  } finally {
    client.release();
  }
}

// ==========================================
// 相册相关操作
// ==========================================

/**
 * 获取所有相册
 */
export async function getAllAlbums(): Promise<AlbumRecord[]> {
  const client = await getPool().connect();
  try {
    const result = await client.query(
      'SELECT * FROM albums ORDER BY sort_order'
    );

    // 获取每个相册的关键词
    const albums = await Promise.all(
      result.rows.map(async (row) => {
        const keywordsResult = await client.query(
          'SELECT keyword FROM album_keywords WHERE album_id = $1',
          [row.id]
        );
        return {
          ...row,
          keywords: keywordsResult.rows.map((r) => r.keyword),
        };
      })
    );

    return albums;
  } finally {
    client.release();
  }
}

/**
 * 更新相册图片数量
 */
export async function updateAlbumImageCount(albumId: string): Promise<void> {
  const client = await getPool().connect();
  try {
    await client.query(
      `UPDATE albums SET image_count = (
        SELECT COUNT(*) FROM images WHERE album_id = $1 AND deleted = FALSE
      ), updated_at = CURRENT_TIMESTAMP WHERE id = $1`,
      [albumId]
    );
  } finally {
    client.release();
  }
}

// ==========================================
// 用户相关操作
// ==========================================

/**
 * 获取用户信息
 */
export async function getUserById(id: string): Promise<UserRecord | null> {
  const client = await getPool().connect();
  try {
    const result = await client.query(
      'SELECT * FROM users WHERE id = $1',
      [id]
    );
    return result.rows.length > 0 ? result.rows[0] : null;
  } finally {
    client.release();
  }
}

/**
 * 更新用户存储使用量
 */
export async function updateUserStorage(userId: string, additionalBytes: number): Promise<void> {
  const client = await getPool().connect();
  try {
    await client.query(
      'UPDATE users SET storage_used = storage_used + $1 WHERE id = $2',
      [additionalBytes, userId]
    );
  } finally {
    client.release();
  }
}

// ==========================================
// 通知相关操作
// ==========================================

/**
 * 获取用户通知
 */
export async function getUserNotifications(userId: string, limit: number = 10): Promise<NotificationRecord[]> {
  const client = await getPool().connect();
  try {
    const result = await client.query(
      'SELECT * FROM notifications WHERE user_id = $1 ORDER BY created_at DESC LIMIT $2',
      [userId, limit]
    );
    return result.rows;
  } finally {
    client.release();
  }
}

/**
 * 获取未读通知数量
 */
export async function getUnreadNotificationCount(userId: string): Promise<number> {
  const client = await getPool().connect();
  try {
    const result = await client.query(
      'SELECT COUNT(*) as count FROM notifications WHERE user_id = $1 AND read = FALSE',
      [userId]
    );
    return parseInt(result.rows[0].count, 10);
  } finally {
    client.release();
  }
}

/**
 * 标记通知为已读
 */
export async function markNotificationAsRead(notificationId: string): Promise<void> {
  const client = await getPool().connect();
  try {
    await client.query(
      'UPDATE notifications SET read = TRUE WHERE id = $1',
      [notificationId]
    );
  } finally {
    client.release();
  }
}

/**
 * 创建通知
 */
export async function createNotification(notification: {
  id: string;
  title: string;
  content: string;
  type: string;
  userId: string;
  resourceId?: string;
}): Promise<NotificationRecord> {
  const client = await getPool().connect();
  try {
    const result = await client.query(
      `INSERT INTO notifications (id, title, content, type, user_id, resource_id, read, created_at)
       VALUES ($1, $2, $3, $4, $5, $6, FALSE, CURRENT_TIMESTAMP)
       RETURNING *`,
      [notification.id, notification.title, notification.content, notification.type, notification.userId, notification.resourceId || null]
    );
    return result.rows[0];
  } finally {
    client.release();
  }
}
