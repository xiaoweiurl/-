import assert from 'node:assert/strict';
import { test } from 'node:test';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import ImageSearchResults from './ImageSearchResults';

test('modal renders a product card before reference images', () => {
  const html = renderToStaticMarkup(
    <ImageSearchResults
      scenario="MIXED"
      results={[
        {
          scorePercent: 81,
          source: 'goods',
          sourceId: '9',
          cardType: 'product',
          scenario: 'MIXED',
          title: 'H100 蕾丝中筒',
          imageUrl: 'https://example.test/main.jpg',
          goods: { id: 9, goodsNo: 'H100', productName: '蕾丝中筒', sampler: '张三' },
          sampleOrderPath: '/sampler/9',
          productDetailPath: '/goods-library/9',
          images: [{ imageUrl: 'https://example.test/side.jpg', slotLabel: '侧面图' }],
        },
        {
          scorePercent: 64,
          source: 'library',
          sourceId: 'lib-1',
          cardType: 'image',
          scenario: 'MIXED',
          title: '红色蕾丝',
          albumName: '秋冬',
          imageUrl: 'https://example.test/lib.jpg',
        },
      ]}
    />
  );
  assert.match(html, /data-testid="search-product-card"/);
  assert.match(html, /data-testid="search-image-card"/);
  assert.match(html, /同款/);
  assert.match(html, /素材/);
  assert.match(html, /H100 蕾丝中筒/);
  assert.match(html, /打样员 张三/);
  assert.match(html, /href="\/sampler\/9"/);
  assert.match(html, /href="\/goods-library\/9"/);
  assert.match(html, /打开打样单/);
  assert.match(html, /打开商品详情/);
  assert.equal((html.match(/data-testid="open-sample-order"/g) || []).length, 1);
  assert.equal((html.match(/data-testid="open-product-detail"/g) || []).length, 1);
  assert.match(html, /data-testid="search-product-thumb"/);
  assert.match(html, /秋冬/);
  const productAt = html.indexOf('search-product-card');
  const imageAt = html.indexOf('search-image-card');
  assert.ok(productAt >= 0 && imageAt > productAt);
});

test('a result without a confirmed record has no sample or product link', () => {
  const html = renderToStaticMarkup(
    <ImageSearchResults
      results={[
        {
          scorePercent: 40,
          source: 'library',
          sourceId: 'lib-2',
          title: '没有档案',
          productId: 'missing',
          sampleOrderPath: 'https://evil.test/sampler/1',
          productDetailPath: '/goods-library/9/edit',
        },
        {
          scorePercent: 55,
          source: 'library',
          sourceId: 'lib-3',
          cardType: 'image',
          title: '只有商品详情',
          productDetailPath: '/products/p-1',
        },
      ]}
    />
  );
  assert.doesNotMatch(html, /open-sample-order/);
  assert.doesNotMatch(html, /evil\.test/);
  assert.doesNotMatch(html, /goods-library\/9/);
  assert.match(html, /href="\/products\/p-1"/);
  assert.equal((html.match(/data-testid="open-product-detail"/g) || []).length, 1);
});
