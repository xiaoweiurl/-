/**
 * DingTalk H5 免登：加载 JSAPI → requestAuthCode → 后端换会话。
 *
 * 企业内部应用 H5 必须用 {@code dd.runtime.permission.requestAuthCode({ corpId })}。
 * 无 corpId 时禁止调用 getAuthCode：取到的码会被 getuserinfo 以 40078 拒绝。
 * getAuthCode / dt.getAuthCode 仅在 classic API 不可用且 corpId 已配置时回退。
 */

import { isDingTalkEnv } from './dingtalk-env';

const JSAPI_SRC = 'https://g.alicdn.com/dingding/dingtalk-jsapi/2.15.0/dingtalk.open.js';
const AUTH_TIMEOUT_MS = 12_000;

export const DINGTALK_CORP_ID_REQUIRED =
  '钉钉免登缺少企业 CorpId。请运维配置环境变量 DINGTALK_CORP_ID（开放平台「应用信息」中的 CorpId）后重启后端。未配置时无法调用 requestAuthCode，继续取码会出现 40078「不存在的临时授权码」。';

type JsapiConfig = {
  configured?: boolean;
  corpId?: string;
  agentId?: string;
  timeStamp?: string;
  nonceStr?: string;
  signature?: string;
};

export type DingTalkDdLike = {
  ready?: (cb: () => void) => void;
  config?: (opts: Record<string, unknown>) => void;
  error?: (cb: (err: unknown) => void) => void;
  getAuthCode?: (opts: Record<string, unknown>) => unknown;
  runtime?: {
    permission?: {
      requestAuthCode?: (opts: Record<string, unknown>) => unknown;
    };
  };
};

declare global {
  interface Window {
    dd?: DingTalkDdLike;
    dt?: { getAuthCode?: (opts: Record<string, unknown>) => unknown };
  }
}

const freeLoginInFlight: { current: Promise<{ ok: boolean; error?: string }> | null } = {
  current: null,
};

export async function fetchDingTalkJsapiConfig(pageUrl: string): Promise<JsapiConfig> {
  const res = await fetch(
    `/api/auth/dingtalk/config?url=${encodeURIComponent(pageUrl)}`,
    { credentials: 'include' },
  );
  const payload = await res.json().catch(() => ({}));
  return (payload.data || payload) as JsapiConfig;
}

export async function dingTalkFreeLogin(goodsId: string): Promise<{ ok: boolean; error?: string }> {
  return beginSingleFlight(freeLoginInFlight, () => dingTalkFreeLoginOnce(goodsId));
}

async function dingTalkFreeLoginOnce(goodsId: string): Promise<{ ok: boolean; error?: string }> {
  if (!isDingTalkEnv()) {
    return { ok: false, error: '请在钉钉中打开' };
  }
  try {
    const pageUrl = typeof window !== 'undefined' ? window.location.href.split('#')[0] : '';
    const config = await fetchDingTalkJsapiConfig(pageUrl);
    if (config.configured === false) {
      return { ok: false, error: '钉钉应用未配置，无法免登' };
    }
    const authCode = await requestDingTalkAuthCode(config);
    const res = await fetch('/api/auth/dingtalk', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ authCode, goodsId: Number(goodsId) }),
    });
    const data = await res.json().catch(() => ({}));
    if (res.ok && (data.success === true || data.code === 200)) {
      return { ok: true };
    }
    return {
      ok: false,
      error: data.message || data.error || `免登失败（${res.status}）`,
    };
  } catch (e) {
    return { ok: false, error: e instanceof Error ? e.message : '钉钉免登失败' };
  }
}

export async function requestDingTalkAuthCode(config: JsapiConfig): Promise<string> {
  const corpId = resolveDingTalkCorpId(config.corpId);
  const dd = await loadDingTalkJsapi();
  applyDdConfig(dd, { ...config, corpId });
  return withTimeout(
    waitReadyThen(dd, () => getAuthCodeFromDd(dd, corpId)),
    AUTH_TIMEOUT_MS,
    '获取钉钉授权码超时，请确认已发布应用且 H5 可信域名包含本站',
  );
}

function applyDdConfig(dd: DingTalkDdLike, config: JsapiConfig) {
  if (!config.signature || !dd.config) return;
  dd.config({
    agentId: config.agentId || undefined,
    corpId: config.corpId,
    timeStamp: config.timeStamp,
    nonceStr: config.nonceStr,
    signature: config.signature,
    type: 0,
    jsApiList: ['runtime.permission.requestAuthCode', 'getAuthCode'],
  });
}

function waitReadyThen<T>(dd: DingTalkDdLike, fn: () => Promise<T>): Promise<T> {
  if (typeof dd.ready !== 'function') {
    return fn();
  }
  return new Promise((resolve, reject) => {
    let settled = false;
    dd.error?.((err: unknown) => {
      if (settled) return;
      settled = true;
      reject(new Error(formatJsapiError(err)));
    });
    dd.ready?.(() => {
      if (settled) return;
      fn().then(
        (value) => {
          settled = true;
          resolve(value);
        },
        (err) => {
          settled = true;
          reject(err);
        },
      );
    });
  });
}

/**
 * H5 微应用取码：优先 classic requestAuthCode({ corpId })。
 * 无 corpId 时直接失败，绝不调用 getAuthCode（避免 40078）。
 */
export function getAuthCodeFromDd(
  dd: DingTalkDdLike,
  corpId: string | null | undefined,
  dtGetAuthCode?: ((opts: Record<string, unknown>) => unknown) | undefined,
): Promise<string> {
  const resolvedCorpId = resolveDingTalkCorpId(corpId);
  const opts: Record<string, unknown> = { corpId: resolvedCorpId };

  return new Promise((resolve, reject) => {
    const onSuccess = (res: { code?: string; authCode?: string } | null | undefined) => {
      const code = res?.code || res?.authCode;
      if (code) resolve(code);
      else reject(new Error('钉钉未返回授权码'));
    };
    const onFail = (err: unknown) => reject(new Error(formatJsapiError(err)));

    const classic = dd.runtime?.permission?.requestAuthCode;
    if (typeof classic === 'function') {
      settleMaybePromise(classic({ ...opts, onSuccess, onFail }), onSuccess, onFail);
      return;
    }
    const dtGet =
      dtGetAuthCode ??
      (typeof window !== 'undefined' ? window.dt?.getAuthCode : undefined);
    if (typeof dtGet === 'function') {
      settleMaybePromise(dtGet({ ...opts, onSuccess, onFail }), onSuccess, onFail);
      return;
    }
    if (typeof dd.getAuthCode === 'function') {
      settleMaybePromise(dd.getAuthCode({ ...opts, onSuccess, onFail }), onSuccess, onFail);
      return;
    }
    reject(new Error('当前页面无法调用钉钉免登 JSAPI，请在钉钉内打开并确认应用已发布'));
  });
}

export function resolveDingTalkCorpId(corpId: string | null | undefined): string {
  const id = (corpId || '').trim();
  if (!id) {
    throw new Error(DINGTALK_CORP_ID_REQUIRED);
  }
  return id;
}

/**
 * Share one in-flight promise so authCode is not requested/consumed twice.
 * Compatible with React refs (`useRef<Promise<T> | null>(null)`).
 */
export function beginSingleFlight<T>(
  holder: { current: Promise<T> | null },
  start: () => Promise<T>,
): Promise<T> {
  if (holder.current) return holder.current;
  const run = start();
  holder.current = run;
  void run.finally(() => {
    if (holder.current === run) holder.current = null;
  });
  return run;
}

function settleMaybePromise(
  ret: unknown,
  onSuccess: (res: { code?: string; authCode?: string }) => void,
  onFail: (err: unknown) => void,
) {
  if (ret && typeof (ret as Promise<unknown>).then === 'function') {
    (ret as Promise<{ code?: string; authCode?: string }>).then(onSuccess, onFail);
  }
}

export function loadDingTalkJsapi(): Promise<DingTalkDdLike> {
  if (typeof window === 'undefined') {
    return Promise.reject(new Error('非浏览器环境'));
  }
  if (window.dd) {
    return Promise.resolve(window.dd);
  }
  return new Promise((resolve, reject) => {
    const existing = document.querySelector(`script[src="${JSAPI_SRC}"]`);
    if (existing) {
      existing.addEventListener('load', () => {
        if (window.dd) resolve(window.dd);
        else reject(new Error('钉钉 JSAPI 加载失败'));
      });
      existing.addEventListener('error', () => reject(new Error('钉钉 JSAPI 脚本加载失败')));
      return;
    }
    const script = document.createElement('script');
    script.src = JSAPI_SRC;
    script.async = true;
    script.onload = () => {
      if (window.dd) resolve(window.dd);
      else reject(new Error('钉钉 JSAPI 加载失败'));
    };
    script.onerror = () => reject(new Error('钉钉 JSAPI 脚本加载失败'));
    document.head.appendChild(script);
  });
}

function formatJsapiError(err: unknown): string {
  if (err == null) return '钉钉 JSAPI 调用失败';
  if (typeof err === 'string') return err;
  if (typeof err === 'object') {
    const o = err as { errorMessage?: string; message?: string; errorCode?: string };
    return o.errorMessage || o.message || o.errorCode || JSON.stringify(err);
  }
  return String(err);
}

function withTimeout<T>(promise: Promise<T>, ms: number, message: string): Promise<T> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(message)), ms);
    promise.then(
      (value) => {
        clearTimeout(timer);
        resolve(value);
      },
      (err) => {
        clearTimeout(timer);
        reject(err);
      },
    );
  });
}
