/**
 * HTTP 头只能是 Latin-1。中文文件名必须用 RFC 5987 的 filename*。
 */
export function attachmentContentDisposition(filename: string, fallback = 'download'): string {
  const raw = (filename || fallback).replace(/[\r\n"]/g, '').trim() || fallback;
  const ascii = raw.replace(/[^\x20-\x7E]/g, '_') || fallback;
  const encoded = encodeURIComponent(raw).replace(/['()*]/g, (ch) =>
    `%${ch.charCodeAt(0).toString(16).toUpperCase()}`
  );
  return `attachment; filename="${ascii}"; filename*=UTF-8''${encoded}`;
}
