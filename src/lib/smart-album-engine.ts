import type { ImageItem } from '@/components/ImageCard';
import type { SmartAlbum, MatchingConfig, MatchMode } from './api/types';

/**
 * 智能相册匹配引擎
 * 基于 matchingConfig 对图片进行自动分类匹配
 * 与原有分类逻辑保持一致
 */

/**
 * 判断文本是否匹配关键词（根据匹配模式）
 */
export function matchesKeyword(
  text: string,
  keyword: string,
  mode: MatchMode,
  caseSensitive = false
): boolean {
  const sourceText = caseSensitive ? text : text.toLowerCase();
  const targetKeyword = caseSensitive ? keyword : keyword.toLowerCase();

  switch (mode) {
    case 'contains':
      return sourceText.includes(targetKeyword);

    case 'exact':
      return sourceText === targetKeyword;

    case 'startsWith':
      return sourceText.startsWith(targetKeyword);

    case 'endsWith':
      return sourceText.endsWith(targetKeyword);

    case 'regex':
      try {
        const regex = new RegExp(targetKeyword, caseSensitive ? '' : 'i');
        return regex.test(text);
      } catch {
        return false;
      }

    case 'fuzzy':
      // 模糊匹配：包含关键词或关键词包含文本
      return sourceText.includes(targetKeyword) || targetKeyword.includes(sourceText);

    default:
      return sourceText.includes(targetKeyword);
  }
}

/**
 * 检查文本是否匹配相册配置（包括同义词）
 */
export function matchesAlbumConfig(
  text: string,
  albumName: string,
  config: MatchingConfig
): boolean {
  const { mode, synonyms, caseSensitive = false } = config;

  // 首先检查主关键词
  if (matchesKeyword(text, albumName, mode, caseSensitive)) {
    return true;
  }

  // 检查同义词
  if (synonyms && synonyms.length > 0) {
    // 找到当前相册的同义词组
    const synonymGroup = synonyms.find(
      s => s.targetKeyword.toLowerCase() === albumName.toLowerCase()
    );

    if (synonymGroup) {
      // 检查是否匹配任意同义词
      return synonymGroup.keywords.some(keyword =>
        matchesKeyword(text, keyword, mode, caseSensitive)
      );
    }
  }

  return false;
}

/**
 * 根据智能相册配置筛选图片
 */
export function filterImagesBySmartAlbum(
  images: ImageItem[],
  album: Pick<SmartAlbum, 'id' | 'name' | 'matchingConfig' | 'isSystem'>
): ImageItem[] {
  // 特殊处理系统预置相册
  if (album.isSystem) {
    switch (album.id) {
      case 'smart-recent':
        // 最近30天
        const thirtyDaysAgo = new Date();
        thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);
        return images.filter(img => {
          const imgDate = new Date(img.createdAt || Date.now());
          return imgDate >= thirtyDaysAgo;
        });

      case 'smart-favorites':
        // 收藏的图片
        return images.filter(img => img.favorite);

      default:
        return images;
    }
  }

  // 普通智能相册：基于 matchingConfig 匹配文件名/标题
  return images.filter(img => {
    const textToMatch = img.title || '';
    return matchesAlbumConfig(textToMatch, album.name, album.matchingConfig);
  });
}

/**
 * 获取智能相册中的图片数量
 */
export function getSmartAlbumImageCount(
  images: ImageItem[],
  album: Pick<SmartAlbum, 'id' | 'name' | 'matchingConfig' | 'isSystem'>
): number {
  return filterImagesBySmartAlbum(images, album).length;
}

/**
 * 为图片自动匹配智能相册
 * 返回匹配到的相册ID列表
 */
export function matchImageToSmartAlbums(
  image: ImageItem,
  albums: SmartAlbum[]
): string[] {
  return albums
    .filter(album => {
      // 系统相册不参与自动匹配
      if (album.isSystem) return false;

      const textToMatch = image.title || '';
      return matchesAlbumConfig(textToMatch, album.name, album.matchingConfig);
    })
    .map(album => album.id);
}

/**
 * 生成匹配配置的描述文本
 */
export function generateMatchingConfigDescription(config: MatchingConfig): string {
  const { mode, synonyms } = config;

  const modeDescriptions: Record<MatchMode, string> = {
    contains: '文件名包含相册名称',
    exact: '文件名与相册名称完全一致',
    startsWith: '文件名以相册名称开头',
    endsWith: '文件名以相册名称结尾',
    regex: '使用正则表达式匹配',
    fuzzy: '模糊匹配',
  };

  let description = modeDescriptions[mode] || modeDescriptions.contains;

  if (synonyms && synonyms.length > 0) {
    const synonymCount = synonyms.reduce((sum, s) => sum + s.keywords.length, 0);
    description += `，包含 ${synonymCount} 个同义词`;
  }

  return description;
}

/**
 * 验证匹配配置是否有效
 */
export function validateMatchingConfig(config: MatchingConfig): boolean {
  if (!config.mode) return false;

  const validModes: MatchMode[] = ['contains', 'exact', 'startsWith', 'endsWith', 'regex', 'fuzzy'];
  if (!validModes.includes(config.mode)) return false;

  // 验证正则表达式
  if (config.mode === 'regex') {
    try {
      new RegExp('test');
    } catch {
      return false;
    }
  }

  return true;
}
