/**
 * 下载工具函数
 * 支持批量下载图片到本地
 */

/**
 * 下载单个文件到本地
 */
export const downloadSingleFile = async (
  imageUrl: string,
  fileName?: string
): Promise<boolean> => {
  try {
    const response = await fetch(imageUrl);
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    const blob = await response.blob();
    const url = window.URL.createObjectURL(blob);

    const link = document.createElement('a');
    link.href = url;
    link.download = fileName || getImageFileName(imageUrl);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);

    // 延迟释放 URL 对象
    setTimeout(() => {
      window.URL.revokeObjectURL(url);
    }, 100);

    return true;
  } catch (error) {
    console.error('下载文件失败:', error);
    return false;
  }
};

/**
 * 从 URL 中提取文件名
 */
const getImageFileName = (url: string): string => {
  try {
    const urlObj = new URL(url);
    const pathname = urlObj.pathname;
    const fileName = pathname.split('/').pop() || 'image.jpg';

    // 如果文件名没有扩展名，添加 .jpg
    if (!fileName.includes('.')) {
      return `${fileName}.jpg`;
    }

    return fileName;
  } catch {
    const parts = url.split('/');
    const fileName = parts[parts.length - 1] || 'image.jpg';

    if (!fileName.includes('.')) {
      return `${fileName}.jpg`;
    }

    return fileName;
  }
};

/**
 * 批量下载文件
 */
export const batchDownload = async (
  items: Array<{
    url: string;
    name?: string;
  }>,
  onProgress?: (current: number, total: number, fileName: string) => void
): Promise<{ success: number; failed: number }> => {
  let successCount = 0;
  let failCount = 0;

  for (let i = 0; i < items.length; i++) {
    const { url, name } = items[i];
    const fileName = name || getImageFileName(url);

    const success = await downloadSingleFile(url, fileName);
    if (success) {
      successCount++;
    } else {
      failCount++;
    }

    // 更新进度
    if (onProgress) {
      onProgress(i + 1, items.length, fileName);
    }

    // 延迟一小段时间，避免浏览器阻止多个下载
    if (i < items.length - 1) {
      await new Promise(resolve => setTimeout(resolve, 500));
    }
  }

  return { success: successCount, failed: failCount };
};

/**
 * 下载商品的所有图片（主图和详情图）
 */
export const downloadProductImages = async (
  images: Array<{
    id: string;
    url: string;
    title?: string;
    fileType?: string;
    isMainImage?: boolean;
  }>,
  onProgress?: (current: number, total: number, fileName: string) => void
): Promise<{ success: number; failed: number }> => {
  if (images.length === 0) {
    return { success: 0, failed: 0 };
  }

  // 构建下载列表，添加文件名标识（主图/详情图）
  const downloadItems = images.map((img, index) => {
    const baseFileName = img.title || `image-${img.id}`;
    const fileType = img.fileType || 'jpg';

    // 如果是详情图，添加序号
    let fileName = baseFileName;
    if (!img.isMainImage) {
      fileName = `${baseFileName}_detail${index}`;
    }

    // 添加扩展名
    if (!fileName.includes(`.${fileType}`)) {
      fileName = `${fileName}.${fileType}`;
    }

    return {
      url: img.url,
      name: fileName,
    };
  });

  return batchDownload(downloadItems, onProgress);
};
