import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  canAccessAccountSettings,
  canResetPasswordOf,
  isAdminOrAbove,
  isSamplerSession,
} from './auth';

test('canAccessAccountSettings allows any full-session user including non-admin', () => {
  assert.equal(canAccessAccountSettings({ role: 'user' }), true);
  assert.equal(canAccessAccountSettings({ role: 'admin' }), true);
  assert.equal(canAccessAccountSettings({ role: 'superadmin' }), true);
  assert.equal(canAccessAccountSettings({ role: 'user', scope: 'full' }), true);
});

test('canAccessAccountSettings denies sampler-scoped DingTalk sessions', () => {
  assert.equal(canAccessAccountSettings({ role: 'sampler', scope: 'sampler' }), false);
  assert.equal(canAccessAccountSettings({ role: 'user', scope: 'sampler' }), false);
  assert.equal(canAccessAccountSettings(null), false);
  assert.equal(canAccessAccountSettings(undefined), false);
});

test('isSamplerSession matches role or scope', () => {
  assert.equal(isSamplerSession({ role: 'sampler' }), true);
  assert.equal(isSamplerSession({ scope: 'sampler' }), true);
  assert.equal(isSamplerSession({ role: 'user' }), false);
});

test('non-admin cannot reset another user password', () => {
  assert.equal(canResetPasswordOf('user', 'u1', 'user', 'u2'), false);
  assert.equal(canResetPasswordOf('sampler', 's1', 'user', 'u2'), false);
  assert.equal(canResetPasswordOf(null, 'u1', 'user', 'u2'), false);
});

test('admin can reset ordinary users but not other admins', () => {
  assert.equal(canResetPasswordOf('admin', 'a1', 'user', 'u2'), true);
  assert.equal(canResetPasswordOf('admin', 'a1', 'admin', 'a2'), false);
  assert.equal(canResetPasswordOf('admin', 'a1', 'superadmin', 'sa1'), false);
  assert.equal(canResetPasswordOf('admin', 'a1', 'admin', 'a1'), true);
});

test('superadmin can reset any user password', () => {
  assert.equal(canResetPasswordOf('superadmin', 'sa1', 'user', 'u2'), true);
  assert.equal(canResetPasswordOf('superadmin', 'sa1', 'admin', 'a1'), true);
  assert.equal(canResetPasswordOf('superadmin', 'sa1', 'superadmin', 'sa2'), true);
});

test('isAdminOrAbove is admin and superadmin only', () => {
  assert.equal(isAdminOrAbove('user'), false);
  assert.equal(isAdminOrAbove('sampler'), false);
  assert.equal(isAdminOrAbove('admin'), true);
  assert.equal(isAdminOrAbove('superadmin'), true);
});
