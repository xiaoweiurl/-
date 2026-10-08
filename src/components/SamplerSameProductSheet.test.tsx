import assert from 'node:assert/strict';
import { test } from 'node:test';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import SamplerSameProductSheet from './SamplerSameProductSheet';

test('sampler sheet lists goods number, name, sampler and similarity', () => {
  const html = renderToStaticMarkup(
    <SamplerSameProductSheet
      open
      loading={false}
      error={null}
      results={[{
        scorePercent: 86,
        imageUrl: 'https://example.test/main.jpg',
        goodsNo: 'H100',
        productName: '蕾丝中筒',
        sampler: '张三',
      }]}
      onClose={() => {}}
      onPick={() => {}}
      onFile={() => {}}
    />
  );
  assert.match(html, /data-testid="sampler-same-product-sheet"/);
  assert.match(html, /data-testid="sampler-same-product-hit"/);
  assert.match(html, /data-testid="sampler-search-camera"/);
  assert.match(html, /capture="environment"/);
  assert.match(html, /H100/);
  assert.match(html, /蕾丝中筒/);
  assert.match(html, /打样员 张三/);
  assert.match(html, /86%/);
  assert.doesNotMatch(html, /open-sample-order/);
  assert.doesNotMatch(html, /open-product-detail/);
});

test('sampler sheet opens only confirmed sample order and product detail paths', () => {
  const html = renderToStaticMarkup(
    <SamplerSameProductSheet
      open
      loading={false}
      error={null}
      results={[
        {
          scorePercent: 90,
          goodsNo: 'H100',
          productName: '蕾丝中筒',
          sampler: '张三',
          sampleOrderPath: '/sampler/9',
          productDetailPath: '/goods-library/9',
        },
        {
          scorePercent: 70,
          goodsNo: 'H80',
          productName: '棉袜',
          sampler: '李四',
          sampleOrderPath: 'https://evil.test/sampler/8',
          productDetailPath: '/goods-library/8/edit',
        },
      ]}
      onClose={() => {}}
      onPick={() => {}}
      onFile={() => {}}
    />
  );
  assert.match(html, /href="\/sampler\/9"/);
  assert.match(html, /href="\/goods-library\/9"/);
  assert.doesNotMatch(html, /evil\.test/);
  assert.doesNotMatch(html, /goods-library\/8/);
  assert.equal((html.match(/data-testid="open-sample-order"/g) || []).length, 1);
});

test('closed sampler sheet renders nothing', () => {
  const html = renderToStaticMarkup(
    <SamplerSameProductSheet
      open={false}
      loading={false}
      error={null}
      results={[]}
      onClose={() => {}}
      onPick={() => {}}
      onFile={() => {}}
    />
  );
  assert.equal(html, '');
});
