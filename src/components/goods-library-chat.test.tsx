import assert from 'node:assert/strict';
import { test } from 'node:test';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import ChatAnswerBody from './ChatAnswerBody';
import { goodsAnswerLinks } from '@/lib/goods-library-chat';
import { parseChatSse, type ChatSource } from '@/lib/chat-sse';

const MAIN = 'https://files.example/main.jpg?X-Amz-Credential=AKIA%2F20261009%2Fcn-hangzhou%2Fs3%2Faws4_request&X-Amz-Signature=abc+def/ghi=';
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
  '![主图](https://files.example/truncated)',
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

function renderAnswer(content: string, sources?: ChatSource[], goodsLibrary?: unknown) {
  return renderToStaticMarkup(
    <ChatAnswerBody
      content={content}
      sources={sources}
      goodsLibrary={goodsLibrary}
      citeIds={['E1']}
      darkMode
      onCite={() => {}}
    />
  );
}

test('style number answer shows every real photo even when the text already contains the urls', () => {
  const html = renderAnswer(answer, [goodsSource]);

  assert.doesNotMatch(html, /truncated/);
  assert.match(html, /href="\/goods-library\/34"/);
  assert.match(html, /href="\/sampler\/34"/);
  assert.match(html, /data-testid="open-product-detail"/);
  assert.match(html, /data-testid="open-sample-order"/);
  assert.equal((html.match(/data-testid="goods-library-photo"/g) || []).length, 3);
  assert.match(html, /data-slot="主图"/);
  assert.match(html, /data-slot="侧面图"/);
  assert.match(html, /data-slot="细节图"/);
  assert.match(html, /src="https:\/\/files\.example\/main\.jpg\?X-Amz-Credential=AKIA%2F20261009%2Fcn-hangzhou%2Fs3%2Faws4_request&amp;X-Amz-Signature=abc\+def\/ghi="/);
  assert.match(html, /src="https:\/\/files\.example\/side\.jpg\?sig=fixture&amp;X-Amz-Signature=abc"/);
  assert.match(html, /src="https:\/\/files\.example\/detail\.jpg\?sig=one"/);
  assert.doesNotMatch(html, /javascript/);
  assert.doesNotMatch(html, /href="\/goods-library\/34"[^>]*target="_blank"/);
  assert.doesNotMatch(html, /href="\/sampler\/34"[^>]*target="_blank"/);
  assert.match(html, /<button[^>]*data-cite="E1"/);
  assert.equal(goodsAnswerLinks([goodsSource])[0]?.productDetailPath, '/goods-library/34');
});

test('supply-chain sse keeps photos when sources omit image urls', () => {
  const sourceWithoutImages: ChatSource = { ...goodsSource, images: undefined };
  const sse = [
    'event:message',
    `data:${JSON.stringify({ type: 'sources', sources: [sourceWithoutImages, { id: 'E1', title: '数据缺失说明', source: 'supply_chain' }] })}`,
    '',
    'event:message',
    `data:${JSON.stringify({
      type: 'goods_library',
      entries: [{
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
      }],
    })}`,
    '',
    'event:message',
    `data:${JSON.stringify({ type: 'content', content: '针对货号 M19F011。\n\n![主图](https://files.example/truncated)' })}`,
    '',
    'event:message',
    `data:${JSON.stringify({ type: 'done', historyId: 'h1', sources: [sourceWithoutImages] })}`,
    '',
    '',
  ].join('\n');

  const parsed = parseChatSse(sse, true);
  assert.equal(parsed.sources[0]?.images, undefined);
  assert.equal(parsed.isStreaming, false);
  const html = renderAnswer(parsed.content, parsed.sources, parsed.goodsLibrary);
  assert.equal((html.match(/data-testid="goods-library-photo"/g) || []).length, 3);
  assert.match(html, /data-slot="细节图"/);
  assert.doesNotMatch(html, /truncated/);
  assert.match(html, /href="\/goods-library\/34"/);
  assert.match(html, /href="\/sampler\/34"/);
  assert.match(html, /豹纹长裤/);
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
  const html = renderAnswer('看看 M19F011', sources, [{
    source: 'visual_match',
    goodsNo: 'M19F011',
    productDetailPath: '/goods-library/34',
    images: [{ imageUrl: MAIN, slotLabel: '主图' }],
  }]);
  assert.equal(html.includes('goods-library-photo'), false);
  assert.doesNotMatch(html, /href="\/goods-library\/34"/);
});
