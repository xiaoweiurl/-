import assert from 'node:assert/strict';
import { test } from 'node:test';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import MarkdownRenderer from './MarkdownRenderer';
import GoodsLibraryChatMedia from './GoodsLibraryChatMedia';
import { goodsAnswerLinks, withoutBrokenSlotImages } from '@/lib/goods-library-chat';
import type { ChatSource } from '@/lib/chat-sse';

const MAIN = 'https://files.example/main.jpg?sig=fixture&X-Amz-Signature=abc';
const SIDE = 'https://files.example/side.jpg?sig=fixture&X-Amz-Signature=abc';
const DETAIL = 'https://files.example/detail.jpg?sig=one';

const goodsSource: ChatSource = {
  id: 'E2',
  source: 'supply_chain',
  title: '商品库文件夹',
  recordId: '34',
  goodsNo: 'M19F011',
  productName: '豹纹长裤',
  sampler: '余凌辉',
  productDetailPath: '/goods-library/34',
  sampleOrderPath: '/sampler/34',
  images: [
    { imageUrl: MAIN, slotLabel: '主图' },
    { imageUrl: SIDE, slotLabel: '侧面图' },
    { imageUrl: DETAIL, slotLabel: '细节图' },
    { imageUrl: 'javascript:alert(1)', slotLabel: '产品图' },
  ],
};

const answer = [
  '当前业务数据库中暂无货号 M19F011 的精确成本 [[E1]]。',
  '',
  '展示槽位：`M19F011`',
  '',
  `![主图](https://files.example/truncated)`,
  '',
  '---',
  '',
  '商品库：[M19F011](/goods-library/34)',
  '',
  '品名：豹纹长裤',
  '',
  '[打样单](/sampler/34)',
  '',
  `![主图](${MAIN})`,
  '',
  `![侧面图](${SIDE})`,
  '',
  `![细节图](${DETAIL})`,
].join('\n');

test('style number answer links the goods number and renders every real photo', () => {
  const html = renderToStaticMarkup(
    <MarkdownRenderer
      content={withoutBrokenSlotImages(answer, [goodsSource])}
      citeIds={['E1']}
      goodsLinks={goodsAnswerLinks([goodsSource])}
      onCite={() => {}}
    />
  );

  assert.doesNotMatch(html, /truncated/);
  assert.match(html, /href="\/goods-library\/34"/);
  assert.match(html, /href="\/sampler\/34"/);
  assert.match(html, /data-testid="open-product-detail"/);
  assert.match(html, /data-testid="open-sample-order"/);
  assert.equal((html.match(/<img\b/g) || []).length, 3);
  assert.match(html, /src="https:\/\/files\.example\/main\.jpg\?sig=fixture&amp;X-Amz-Signature=abc"/);
  assert.match(html, /src="https:\/\/files\.example\/side\.jpg\?sig=fixture&amp;X-Amz-Signature=abc"/);
  assert.match(html, /src="https:\/\/files\.example\/detail\.jpg\?sig=one"/);
  assert.match(html, /alt="主图"/);
  assert.match(html, /alt="侧面图"/);
  assert.match(html, /alt="细节图"/);
  assert.doesNotMatch(html, /href="\/goods-library\/34"[^>]*target="_blank"/);
  assert.doesNotMatch(html, /href="\/sampler\/34"[^>]*target="_blank"/);
  assert.match(html, /<button[^>]*data-cite="E1"/);
});

test('goods library media shows photos that are not already in the answer', () => {
  const html = renderToStaticMarkup(
    <GoodsLibraryChatMedia sources={[goodsSource]} content="暂无货号 M19F011 的成本" />
  );
  assert.equal((html.match(/data-testid="goods-library-photo"/g) || []).length, 3);
  assert.match(html, /data-slot="主图"/);
  assert.match(html, /data-slot="侧面图"/);
  assert.match(html, /data-slot="细节图"/);
  assert.doesNotMatch(html, /javascript/);
  assert.match(html, /href="\/goods-library\/34"/);
  assert.match(html, /href="\/sampler\/34"/);
});

test('photos already written into the answer are not shown twice', () => {
  const html = renderToStaticMarkup(
    <GoodsLibraryChatMedia sources={[goodsSource]} content={answer} />
  );
  assert.doesNotMatch(html, /data-testid="goods-library-photo"/);
  assert.match(html, /href="\/sampler\/34"/);
});

test('another company path and image-search cards are not turned into goods links', () => {
  const sources: ChatSource[] = [
    {
      id: 'visual-1',
      source: 'visual_match',
      title: '商品库文件夹',
      goodsNo: 'M19F011',
      productDetailPath: '/goods-library/34',
      sampleOrderPath: '/sampler/34',
      images: [{ imageUrl: MAIN, slotLabel: '主图' }],
    },
    {
      id: 'E9',
      source: 'supply_chain',
      title: '商品库文件夹',
      goodsNo: 'OTHER',
      productDetailPath: 'https://evil.test/goods-library/8',
      sampleOrderPath: '/sampler/8/edit',
      images: [{ imageUrl: SIDE, slotLabel: '侧面图' }],
    },
  ];
  assert.equal(goodsAnswerLinks(sources).length, 0);
  const html = renderToStaticMarkup(<GoodsLibraryChatMedia sources={sources} content="" />);
  assert.equal(html, '');
  const plain = renderToStaticMarkup(<MarkdownRenderer content="看看 M19F011" />);
  assert.doesNotMatch(plain, /<a /);
});
