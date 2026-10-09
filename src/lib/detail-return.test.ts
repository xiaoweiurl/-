import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  CHAT_PAGE_RETURN,
  detailBackAction,
  detailReturnPath,
  SUPPLY_CHAIN_CHAT_RETURN,
  withDetailReturn,
} from './detail-return';

const origin = 'http://localhost:5000';

test('withDetailReturn keeps chat and supply-chain on goods and sampler links', () => {
  assert.equal(
    withDetailReturn('/goods-library/34', CHAT_PAGE_RETURN),
    '/goods-library/34?from=%2Fchat',
  );
  assert.equal(
    withDetailReturn('/sampler/34', SUPPLY_CHAIN_CHAT_RETURN),
    '/sampler/34?from=%2Fsupply-chain%3Ftab%3Dchat',
  );
  assert.equal(withDetailReturn('/goods-library/34', null), '/goods-library/34');
  assert.equal(withDetailReturn('/goods-library/34', 'https://evil.test/phish'), '/goods-library/34');
  assert.equal(withDetailReturn('/goods-library/34', '//evil.test'), '/goods-library/34');
  assert.equal(withDetailReturn('/goods-library/34', '/chat/../../admin'), '/goods-library/34');
});

test('detailReturnPath rejects open redirects', () => {
  assert.equal(detailReturnPath('%2Fchat'), '/chat');
  assert.equal(detailReturnPath(SUPPLY_CHAIN_CHAT_RETURN), SUPPLY_CHAIN_CHAT_RETURN);
  assert.equal(detailReturnPath('https://evil.test'), null);
  assert.equal(detailReturnPath('//evil.test'), null);
  assert.equal(detailReturnPath('/login'), null);
  assert.equal(detailReturnPath('/api/goods-library/1'), null);
  assert.equal(detailReturnPath('/chat/../../etc'), null);
});

test('full navigation from supply-chain or designer chat goes back in history', () => {
  assert.deepEqual(detailBackAction({
    search: '?from=%2Fsupply-chain%3Ftab%3Dchat',
    pathname: '/goods-library/34',
    referrer: `${origin}/supply-chain?tab=chat`,
    origin,
    historyLength: 2,
    fallback: '/goods-library',
  }), { type: 'back' });
  assert.deepEqual(detailBackAction({
    search: '?from=%2Fchat',
    pathname: '/goods-library/34',
    referrer: `${origin}/chat`,
    origin,
    historyLength: 2,
    fallback: '/goods-library',
  }), { type: 'back' });
});

test('a chat link does not return to the goods list when that list is the previous document', () => {
  assert.deepEqual(detailBackAction({
    search: '?from=%2Fchat',
    pathname: '/goods-library/34',
    referrer: `${origin}/goods-library`,
    origin,
    historyLength: 3,
    fallback: '/goods-library',
  }), { type: 'push', href: CHAT_PAGE_RETURN });
  assert.deepEqual(detailBackAction({
    search: '?from=%2Fsupply-chain%3Ftab%3Dchat',
    pathname: '/sampler/34',
    referrer: `${origin}/goods-library`,
    origin,
    historyLength: 3,
    fallback: null,
  }), { type: 'push', href: SUPPLY_CHAIN_CHAT_RETURN });
});

test('opening detail from the goods list still returns through history', () => {
  assert.deepEqual(detailBackAction({
    search: '',
    pathname: '/goods-library/34',
    referrer: '',
    origin,
    historyLength: 2,
    fallback: '/goods-library',
  }), { type: 'back' });
});

test('a chat link opened in a new tab uses from instead of the goods list', () => {
  assert.deepEqual(detailBackAction({
    search: '?from=%2Fsupply-chain%3Ftab%3Dchat',
    pathname: '/goods-library/34',
    referrer: '',
    origin,
    historyLength: 1,
    fallback: '/goods-library',
  }), { type: 'push', href: SUPPLY_CHAIN_CHAT_RETURN });

  assert.deepEqual(detailBackAction({
    search: '?from=%2Fchat',
    pathname: '/sampler/34',
    referrer: 'https://mail.example/notice',
    origin,
    historyLength: 2,
    fallback: null,
  }), { type: 'push', href: CHAT_PAGE_RETURN });
});

test('a direct visit with no chat return opens the goods list', () => {
  assert.deepEqual(detailBackAction({
    search: '',
    pathname: '/goods-library/34',
    referrer: '',
    origin,
    historyLength: 1,
    fallback: '/goods-library',
  }), { type: 'push', href: '/goods-library' });
});

test('cross-origin arrival stays in the app on the goods list', () => {
  assert.deepEqual(detailBackAction({
    search: '',
    pathname: '/goods-library/34',
    referrer: 'https://mail.example/notice',
    origin,
    historyLength: 2,
    fallback: '/goods-library',
  }), { type: 'push', href: '/goods-library' });
});

test('sampler opened from chat can go back, and a DingTalk landing does not dump to the list', () => {
  assert.deepEqual(detailBackAction({
    search: '?from=%2Fchat',
    pathname: '/sampler/34',
    referrer: `${origin}/chat`,
    origin,
    historyLength: 2,
    fallback: null,
  }), { type: 'back' });

  assert.deepEqual(detailBackAction({
    search: '',
    pathname: '/sampler/34',
    referrer: 'https://applink.dingtalk.com/',
    origin,
    historyLength: 2,
    fallback: null,
  }), { type: 'stay' });

  assert.equal(detailReturnPath('javascript:alert(1)'), null);
});
