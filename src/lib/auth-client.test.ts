import assert from 'node:assert/strict';
import { test } from 'node:test';
import { isPublicAuthPage } from './auth-client';

test('isPublicAuthPage covers login/register/forgot/share', () => {
  assert.equal(isPublicAuthPage('/login'), true);
  assert.equal(isPublicAuthPage('/login/'), true);
  assert.equal(isPublicAuthPage('/register'), true);
  assert.equal(isPublicAuthPage('/forgot-password'), true);
  assert.equal(isPublicAuthPage('/share/abc123'), true);
});

test('isPublicAuthPage rejects app pages that need a session', () => {
  assert.equal(isPublicAuthPage('/'), false);
  assert.equal(isPublicAuthPage('/knowledge'), false);
  assert.equal(isPublicAuthPage('/user-settings'), false);
  assert.equal(isPublicAuthPage('/chat'), false);
});
