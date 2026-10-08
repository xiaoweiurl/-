/** 上传前把长边压到 1280，并转成 JPEG，方便手机和钉钉里拍照检索。 */
export const IMAGE_SEARCH_MAX_EDGE = 1280;
export const IMAGE_SEARCH_JPEG_QUALITY = 0.82;

export async function prepareSearchImage(
  file: File,
  maxEdge = IMAGE_SEARCH_MAX_EDGE,
  quality = IMAGE_SEARCH_JPEG_QUALITY,
): Promise<File> {
  if (typeof document === 'undefined' || typeof createImageBitmap !== 'function') {
    return file;
  }
  try {
    const bitmap = await createImageBitmap(file);
    const longest = Math.max(bitmap.width, bitmap.height);
    const scale = longest > maxEdge ? maxEdge / longest : 1;
    const width = Math.max(1, Math.round(bitmap.width * scale));
    const height = Math.max(1, Math.round(bitmap.height * scale));
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext('2d');
    if (!context) {
      bitmap.close();
      return file;
    }
    context.drawImage(bitmap, 0, 0, width, height);
    bitmap.close();
    const blob = await new Promise<Blob | null>((resolve) => {
      canvas.toBlob(resolve, 'image/jpeg', quality);
    });
    if (!blob) return file;
    const base = file.name.replace(/\.[^.]+$/, '') || 'photo';
    return new File([blob], `${base}.jpg`, { type: 'image/jpeg', lastModified: Date.now() });
  } catch {
    return file;
  }
}
