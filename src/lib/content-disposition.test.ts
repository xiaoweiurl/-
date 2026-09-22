import assert from 'node:assert/strict';
import { test } from 'node:test';
import { attachmentContentDisposition } from './content-disposition';

test('ascii filename stays in filename=', () => {
  assert.equal(
    attachmentContentDisposition('cover.jpg'),
    'attachment; filename="cover.jpg"; filename*=UTF-8\'\'cover.jpg'
  );
});

test('chinese filename uses filename* and ascii fallback', () => {
  const name = '松野湃主图.jpg';
  const header = attachmentContentDisposition(name);
  assert.equal(
    header,
    `attachment; filename="_____.jpg"; filename*=UTF-8''${encodeURIComponent(name)}`
  );
  const asciiPart = header.slice(0, header.indexOf('filename*'));
  for (const ch of asciiPart) {
    assert.ok(ch.charCodeAt(0) <= 255);
  }
});
