import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  isSafeReturnPath,
  loginHref,
  parseReturnPath,
  samplerFormPath,
} from './auth-redirect';

test('samplerFormPath is /sampler/{id}', () => {
  assert.equal(samplerFormPath(77), '/sampler/77');
  assert.equal(samplerFormPath('9'), '/sampler/9');
});

test('isSafeReturnPath allows sampler and goods detail', () => {
  assert.equal(isSafeReturnPath('/sampler/9'), true);
  assert.equal(isSafeReturnPath('/sampler/9?x=1'), true);
  assert.equal(isSafeReturnPath('/goods-library/12'), true);
});

test('isSafeReturnPath rejects open redirects and login loops', () => {
  assert.equal(isSafeReturnPath('https://evil.example/phish'), false);
  assert.equal(isSafeReturnPath('//evil.example'), false);
  assert.equal(isSafeReturnPath('/login'), false);
  assert.equal(isSafeReturnPath('/login?returnUrl=/sampler/1'), false);
  assert.equal(isSafeReturnPath(''), false);
  assert.equal(isSafeReturnPath('/\\evil'), false);
});

test('parseReturnPath decodes query values', () => {
  assert.equal(parseReturnPath('%2Fsampler%2F9'), '/sampler/9');
  assert.equal(parseReturnPath('/sampler/9'), '/sampler/9');
  assert.equal(parseReturnPath('https://evil.example'), null);
});

test('loginHref encodes returnUrl', () => {
  assert.equal(loginHref('/sampler/9'), '/login?returnUrl=%2Fsampler%2F9');
  assert.equal(loginHref('/login'), '/login');
  assert.equal(loginHref('https://evil.example'), '/login');
});
