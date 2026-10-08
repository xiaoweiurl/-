import assert from 'node:assert/strict';
import { test } from 'node:test';
import { visibleRecordLinks } from './image-search-links';

test('known sample order and product detail paths are kept', () => {
  const links = visibleRecordLinks({
    sampleOrderPath: '/sampler/9',
    productDetailPath: '/goods-library/9',
  });
  assert.equal(links.sampleOrderPath, '/sampler/9');
  assert.equal(links.productDetailPath, '/goods-library/9');
  assert.equal(visibleRecordLinks({ productDetailPath: '/products/p-1' }).productDetailPath, '/products/p-1');
});

test('missing or unsafe paths are not links', () => {
  const links = visibleRecordLinks({
    sampleOrderPath: 'https://evil.test/sampler/9',
    productDetailPath: '/goods-library/9/edit',
  });
  assert.equal(links.sampleOrderPath, null);
  assert.equal(links.productDetailPath, null);
  assert.equal(visibleRecordLinks({ sampleOrderPath: '/sampler/0' }).sampleOrderPath, null);
  assert.equal(visibleRecordLinks({ productDetailPath: '/products/../admin' }).productDetailPath, null);
  assert.equal(visibleRecordLinks({}).sampleOrderPath, null);
  assert.equal(visibleRecordLinks(null).productDetailPath, null);
});
