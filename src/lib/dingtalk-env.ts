/**
 * DingTalk in-app browser / microapp detection (PC + mobile).
 */

export function isDingTalkUserAgent(ua: string | null | undefined): boolean {
  if (!ua) return false;
  return /DingTalk/i.test(ua);
}

export function isDingTalkEnv(): boolean {
  if (typeof navigator === 'undefined') return false;
  return isDingTalkUserAgent(navigator.userAgent);
}
