import assert from 'node:assert/strict';
import { test } from 'node:test';
import { shouldSkipBffForwardHeader } from './bff-forward-headers';

test('strips browser Origin so Java CorsFilter is not tripped by FRP public host', () => {
  assert.equal(shouldSkipBffForwardHeader('Origin'), true);
  assert.equal(shouldSkipBffForwardHeader('origin'), true);
  assert.equal(shouldSkipBffForwardHeader('Referer'), true);
});

test('keeps session and content headers', () => {
  assert.equal(shouldSkipBffForwardHeader('Cookie'), false);
  assert.equal(shouldSkipBffForwardHeader('X-Session-Id'), false);
  assert.equal(shouldSkipBffForwardHeader('Authorization'), false);
  assert.equal(shouldSkipBffForwardHeader('Content-Type'), false);
  assert.equal(shouldSkipBffForwardHeader('Accept'), false);
});

test('strips hop-by-hop and fetch metadata', () => {
  assert.equal(shouldSkipBffForwardHeader('Host'), true);
  assert.equal(shouldSkipBffForwardHeader('X-Forwarded-Host'), true);
  assert.equal(shouldSkipBffForwardHeader('sec-fetch-site'), true);
  assert.equal(shouldSkipBffForwardHeader('sec-ch-ua'), true);
  assert.equal(shouldSkipBffForwardHeader('x-middleware-foo'), true);
});
