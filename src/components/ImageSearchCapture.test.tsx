import assert from 'node:assert/strict';
import { test } from 'node:test';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import ImageSearchCapture from './ImageSearchCapture';
import { imageSearchSheetClass } from '@/lib/image-search-sheet';

test('mobile capture offers camera and album inputs', () => {
  const html = renderToStaticMarkup(<ImageSearchCapture preview={null} onPick={() => {}} />);
  assert.match(html, /data-testid="image-search-camera"/);
  assert.match(html, /data-testid="image-search-album"/);
  assert.match(html, /capture="environment"/);
  assert.match(html, /accept="image\/\*"/);
  const cameraAt = html.indexOf('image-search-camera');
  const albumAt = html.indexOf('image-search-album');
  const cameraTag = html.slice(Math.max(0, cameraAt - 80), cameraAt + 180);
  const albumTag = html.slice(Math.max(0, albumAt - 40), albumAt + 220);
  assert.match(cameraTag, /capture="environment"/);
  assert.equal(albumTag.includes('capture='), false);
});

test('dingtalk webview uses a full-screen sheet', () => {
  const dingtalk = imageSearchSheetClass(true);
  const desktop = imageSearchSheetClass(false);
  assert.match(dingtalk, /h-\[100dvh\]/);
  assert.equal(dingtalk.includes('sm:max-w-lg'), false);
  assert.match(desktop, /sm:max-w-lg/);
  assert.match(desktop, /h-\[100dvh\]/);
});
