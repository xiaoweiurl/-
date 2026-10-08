import assert from 'node:assert/strict';
import { test } from 'node:test';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import ImageSearchFilterChips from './ImageSearchFilterChips';
import ChatVisualMatches from './ChatVisualMatches';

test('filter chips render labels and a remove control', () => {
  const html = renderToStaticMarkup(
    <ImageSearchFilterChips
      filters={[
        { id: 'time', label: '2025年' },
        { id: 'sampler', label: '打样员 张三' },
        { id: 'attr:红色', label: '红色', relaxed: true },
      ]}
    />
  );
  assert.match(html, /data-testid="image-search-filter-chips"/);
  assert.equal((html.match(/data-testid="image-search-filter-chip"/g) || []).length, 3);
  assert.match(html, /2025年/);
  assert.match(html, /打样员 张三/);
  assert.match(html, /data-filter-id="attr:红色"/);
  assert.match(html, /data-relaxed="true"/);
  assert.match(html, /移除2025年/);
});

test('chat visual matches show the parsed filters as removable chips', () => {
  const html = renderToStaticMarkup(
    <ChatVisualMatches
      matches={[{
        id: 'visual-1',
        source: 'visual_match',
        sourceLabel: '产品',
        title: 'H100',
        goodsNo: 'H100',
        scorePercent: 80,
        imageUrl: 'https://example.test/a.jpg',
        cardType: 'product',
        filters: [{ id: 'time', label: '2025年', applied: true }],
        filterNotice: '「打样员 张三」把符合条件的图片都筛掉了，下面是去掉这个条件后的结果。',
      }]}
    />
  );
  assert.match(html, /data-testid="image-search-filter-chip"/);
  assert.match(html, /2025年/);
  assert.match(html, /去掉这个条件后的结果/);
});
