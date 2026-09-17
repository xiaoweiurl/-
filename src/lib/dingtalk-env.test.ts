import assert from 'node:assert/strict';
import { test } from 'node:test';
import { isDingTalkEnv, isDingTalkUserAgent } from './dingtalk-env';

test('isDingTalkUserAgent detects mobile and PC DingTalk', () => {
  assert.equal(
    isDingTalkUserAgent(
      'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) DingTalk/7.1.0',
    ),
    true,
  );
  assert.equal(
    isDingTalkUserAgent(
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 DingTalk(6.5.0)',
    ),
    true,
  );
  assert.equal(isDingTalkUserAgent('Mozilla/5.0 (Linux; Android 13) Chrome/120.0.0.0'), false);
  assert.equal(isDingTalkUserAgent(''), false);
  assert.equal(isDingTalkUserAgent(null), false);
});

test('isDingTalkEnv is false in Node without navigator DingTalk UA', () => {
  assert.equal(isDingTalkEnv(), false);
});
