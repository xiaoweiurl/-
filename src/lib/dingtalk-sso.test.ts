import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  DINGTALK_CORP_ID_REQUIRED,
  beginSingleFlight,
  getAuthCodeFromDd,
  readSamplerTicketFromSearch,
  recoverSamplerAuth,
  requestDingTalkAuthCode,
  resolveDingTalkCorpId,
  type DingTalkDdLike,
} from './dingtalk-sso';

test('resolveDingTalkCorpId requires non-blank corpId', () => {
  assert.equal(resolveDingTalkCorpId(' dingabc '), 'dingabc');
  assert.throws(() => resolveDingTalkCorpId(''), (err: Error) => {
    assert.equal(err.message, DINGTALK_CORP_ID_REQUIRED);
    assert.match(err.message, /DINGTALK_CORP_ID/);
    return true;
  });
  assert.throws(() => resolveDingTalkCorpId('   '));
  assert.throws(() => resolveDingTalkCorpId(undefined));
  assert.throws(() => resolveDingTalkCorpId(null));
});

test('requestDingTalkAuthCode fails fast without corpId (no JSAPI / getAuthCode)', async () => {
  await assert.rejects(
    () => requestDingTalkAuthCode({ configured: true, corpId: '' }),
    (err: Error) => {
      assert.equal(err.message, DINGTALK_CORP_ID_REQUIRED);
      return true;
    },
  );
  await assert.rejects(
    () => requestDingTalkAuthCode({ configured: true }),
    /DINGTALK_CORP_ID/,
  );
});

test('requestDingTalkAuthCode with corpId reaches JSAPI load, not the corpId error', async () => {
  await assert.rejects(
    () => requestDingTalkAuthCode({ configured: true, corpId: 'dingabc' }),
    /非浏览器环境/,
  );
});

test('getAuthCodeFromDd prefers classic requestAuthCode over getAuthCode', async () => {
  const calls: string[] = [];
  const dd: DingTalkDdLike = {
    getAuthCode: (opts) => {
      calls.push('getAuthCode');
      (opts.onSuccess as (res: { code: string }) => void)({ code: 'wrong-v2' });
    },
    runtime: {
      permission: {
        requestAuthCode: (opts) => {
          calls.push('requestAuthCode');
          (opts.onSuccess as (res: { code: string }) => void)({ code: 'classic-code' });
        },
      },
    },
  };
  const dtGet = (opts: Record<string, unknown>) => {
    calls.push('dt.getAuthCode');
    (opts.onSuccess as (res: { code: string }) => void)({ code: 'wrong-dt' });
  };
  const code = await getAuthCodeFromDd(dd, 'dingcorp', dtGet);
  assert.equal(code, 'classic-code');
  assert.deepEqual(calls, ['requestAuthCode']);
});

test('getAuthCodeFromDd always passes corpId to requestAuthCode', async () => {
  let received: Record<string, unknown> | undefined;
  const dd: DingTalkDdLike = {
    runtime: {
      permission: {
        requestAuthCode: (opts) => {
          received = opts;
          (opts.onSuccess as (res: { code: string }) => void)({ code: 'ok' });
        },
      },
    },
  };
  await getAuthCodeFromDd(dd, ' dingcorp ');
  assert.equal(received?.corpId, 'dingcorp');
  assert.equal('clientId' in (received ?? {}), false);
});

test('getAuthCodeFromDd passes clientId to requestAuthCode when present', async () => {
  let received: Record<string, unknown> | undefined;
  const dd: DingTalkDdLike = {
    runtime: {
      permission: {
        requestAuthCode: (opts) => {
          received = opts;
          (opts.onSuccess as (res: { code: string }) => void)({ code: 'ok' });
        },
      },
    },
  };
  await getAuthCodeFromDd(dd, 'dingcorp', undefined, ' ding-app-key ');
  assert.equal(received?.corpId, 'dingcorp');
  assert.equal(received?.clientId, 'ding-app-key');
});

test('getAuthCodeFromDd omits blank clientId', async () => {
  let received: Record<string, unknown> | undefined;
  const dd: DingTalkDdLike = {
    runtime: {
      permission: {
        requestAuthCode: (opts) => {
          received = opts;
          (opts.onSuccess as (res: { code: string }) => void)({ code: 'ok' });
        },
      },
    },
  };
  await getAuthCodeFromDd(dd, 'dingcorp', undefined, '  ');
  assert.equal(received?.corpId, 'dingcorp');
  assert.equal('clientId' in (received ?? {}), false);
});

test('getAuthCodeFromDd still fails fast without corpId even if clientId present', async () => {
  let called = false;
  const dd: DingTalkDdLike = {
    runtime: {
      permission: {
        requestAuthCode: () => {
          called = true;
        },
      },
    },
  };
  await assert.rejects(
    () => getAuthCodeFromDd(dd, '', undefined, 'ding-app-key'),
    /DINGTALK_CORP_ID/,
  );
  assert.equal(called, false);
});

test('getAuthCodeFromDd does not call getAuthCode when corpId is missing', async () => {
  let called = false;
  const dd: DingTalkDdLike = {
    getAuthCode: () => {
      called = true;
    },
    runtime: {
      permission: {
        requestAuthCode: () => {
          called = true;
        },
      },
    },
  };
  await assert.rejects(() => getAuthCodeFromDd(dd, ''), /DINGTALK_CORP_ID/);
  await assert.rejects(() => getAuthCodeFromDd(dd, '  '), /DINGTALK_CORP_ID/);
  assert.equal(called, false);
});

test('getAuthCodeFromDd falls back to getAuthCode only when classic missing and corpId present', async () => {
  const dd: DingTalkDdLike = {
    getAuthCode: (opts) => {
      (opts.onSuccess as (res: { authCode: string }) => void)({ authCode: 'v2-code' });
    },
  };
  const code = await getAuthCodeFromDd(dd, 'dingcorp');
  assert.equal(code, 'v2-code');
});

test('getAuthCodeFromDd falls back to dt.getAuthCode when classic missing', async () => {
  const dd: DingTalkDdLike = {};
  const dtGet = (opts: Record<string, unknown>) => {
    (opts.onSuccess as (res: { code: string }) => void)({ code: 'dt-code' });
  };
  const code = await getAuthCodeFromDd(dd, 'dingcorp', dtGet);
  assert.equal(code, 'dt-code');
});

test('beginSingleFlight shares one in-flight promise (authCode not double-consumed)', async () => {
  const holder: { current: Promise<number> | null } = { current: null };
  let starts = 0;
  const start = () => {
    starts += 1;
    return new Promise<number>((resolve) => {
      setTimeout(() => resolve(42), 15);
    });
  };
  const a = beginSingleFlight(holder, start);
  const b = beginSingleFlight(holder, start);
  assert.equal(a, b);
  assert.equal(await a, 42);
  assert.equal(await b, 42);
  assert.equal(starts, 1);
  const c = beginSingleFlight(holder, start);
  assert.equal(await c, 42);
  assert.equal(starts, 2);
});

test('readSamplerTicketFromSearch reads ticket query', () => {
  assert.equal(readSamplerTicketFromSearch('?ticket=v1.abc.sig'), 'v1.abc.sig');
  assert.equal(readSamplerTicketFromSearch('ticket=v1.abc.sig&x=1'), 'v1.abc.sig');
  assert.equal(readSamplerTicketFromSearch('?id=9'), null);
  assert.equal(readSamplerTicketFromSearch(''), null);
  assert.equal(readSamplerTicketFromSearch('?ticket=%20'), null);
});

test('recoverSamplerAuth prefers ticket over JSAPI', async () => {
  let jsapiCalls = 0;
  const result = await recoverSamplerAuth({
    goodsId: '9',
    ticket: 'v1.payload.sig',
    isDingTalk: true,
    redeemTicket: async (ticket, goodsId) => {
      assert.equal(ticket, 'v1.payload.sig');
      assert.equal(goodsId, '9');
      return { ok: true };
    },
    freeLogin: async () => {
      jsapiCalls += 1;
      return { ok: true };
    },
  });
  assert.deepEqual(result, { ok: true, via: 'ticket' });
  assert.equal(jsapiCalls, 0);
});

test('recoverSamplerAuth falls back to JSAPI when ticket fails in DingTalk', async () => {
  const result = await recoverSamplerAuth({
    goodsId: '9',
    ticket: 'v1.bad',
    isDingTalk: true,
    redeemTicket: async () => ({ ok: false, error: '过期' }),
    freeLogin: async () => ({ ok: true }),
  });
  assert.deepEqual(result, { ok: true, via: 'jsapi' });
});

test('recoverSamplerAuth does not call JSAPI outside DingTalk when ticket fails', async () => {
  let jsapiCalls = 0;
  const result = await recoverSamplerAuth({
    goodsId: '9',
    ticket: 'v1.bad',
    isDingTalk: false,
    redeemTicket: async () => ({ ok: false, error: '无效凭证' }),
    freeLogin: async () => {
      jsapiCalls += 1;
      return { ok: true };
    },
  });
  assert.equal(result.ok, false);
  assert.equal(result.error, '无效凭证');
  assert.equal(jsapiCalls, 0);
});

test('recoverSamplerAuth uses JSAPI when no ticket in DingTalk', async () => {
  let redeemCalls = 0;
  const result = await recoverSamplerAuth({
    goodsId: '9',
    ticket: '  ',
    isDingTalk: true,
    redeemTicket: async () => {
      redeemCalls += 1;
      return { ok: true };
    },
    freeLogin: async (goodsId) => {
      assert.equal(goodsId, '9');
      return { ok: true };
    },
  });
  assert.deepEqual(result, { ok: true, via: 'jsapi' });
  assert.equal(redeemCalls, 0);
});

test('recoverSamplerAuth without ticket outside DingTalk asks for password login', async () => {
  const result = await recoverSamplerAuth({
    goodsId: '9',
    isDingTalk: false,
    redeemTicket: async () => ({ ok: true }),
    freeLogin: async () => ({ ok: true }),
  });
  assert.equal(result.ok, false);
  assert.match(result.error || '', /账号登录/);
});
