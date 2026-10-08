/**
 * 窄屏和钉钉内置浏览器都用整屏结果，避免被顶栏或安全区裁掉。
 */
export function imageSearchSheetClass(dingtalk: boolean): string {
  if (dingtalk) {
    return 'bg-white w-full h-[100dvh] max-h-[100dvh] rounded-none shadow-lg flex flex-col';
  }
  return 'bg-white w-full h-[100dvh] sm:h-auto sm:max-h-[85vh] sm:max-w-lg sm:rounded-2xl shadow-lg flex flex-col';
}
