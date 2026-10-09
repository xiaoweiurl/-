import assert from 'node:assert/strict';
import fs from 'node:fs';
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

function renderAnswer(content: string, sources?: ChatSource[], goodsLibrary?: unknown, returnTo?: string) {
  return renderToStaticMarkup(
    <ChatAnswerBody
      content={content}
      sources={sources}
      goodsLibrary={goodsLibrary}
      citeIds={['E1']}
      darkMode
      returnTo={returnTo}
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

test('chat links carry the page they should return to', () => {
  const supply = renderAnswer(answer, [goodsSource], undefined, '/supply-chain?tab=chat');
  assert.match(supply, /href="\/goods-library\/34\?from=%2Fsupply-chain%3Ftab%3Dchat"/);
  assert.match(supply, /href="\/sampler\/34\?from=%2Fsupply-chain%3Ftab%3Dchat"/);
  assert.doesNotMatch(supply, /href="\/goods-library\/34"/);
  assert.doesNotMatch(supply, /target="_blank"/);

  const designer = renderAnswer('展示槽位：`M19F011`', [{
    ...goodsSource,
    images: [{ imageUrl: MAIN, slotLabel: '主图' }],
  }], undefined, '/chat');
  assert.match(designer, /href="\/goods-library\/34\?from=%2Fchat"[^>]*>M19F011<\/a>/);
  assert.match(designer, /href="\/sampler\/34\?from=%2Fchat"/);

  const evil = renderAnswer(answer, [goodsSource], undefined, 'https://evil.test/phish');
  assert.match(evil, /href="\/goods-library\/34"/);
  assert.doesNotMatch(evil, /evil\.test/);
});

test('bare style number links the chip and shows only the filled main photo', () => {
  const content = [
    '当前业务数据库中暂无货号 M19F011 的精确生产排产明细。',
    '',
    '展示槽位：`M19F011`',
  ].join('\n');
  const source: ChatSource = {
    ...goodsSource,
    images: [{ imageUrl: MAIN, slotLabel: '主图' }],
  };
  const html = renderAnswer(content, [source]);

  assert.match(html, /href="\/goods-library\/34"[^>]*>M19F011<\/a>/);
  assert.match(html, /href="\/sampler\/34"/);
  assert.match(html, /打开商品详情/);
  assert.match(html, /打开打样单/);
  assert.equal((html.match(/<code\b/g) || []).length, 0);
  assert.equal((html.match(/data-testid="goods-library-photo"/g) || []).length, 1);
  assert.match(html, /data-slot="主图"/);
  assert.doesNotMatch(html, /data-slot="侧面图"/);
  assert.doesNotMatch(html, /data-slot="细节图"/);
  assert.doesNotMatch(html, /data-slot="产品图"/);
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

test('answer markdown keeps paragraph, heading, list, and table spacing', () => {
  const content = [
    '针对货号 M19F011 推进利润目标。当前需先核对已录入的基础信息。',
    '',
    '## 二、50%利润目标倒推测算框架',
    '',
    '- 直接材料成本按用量和损耗核算',
    '- 制造加工成本按机台费率核算',
    '',
    '| 成本维度 | 核算公式 | 管控动作 |',
    '| --- | --- | --- |',
    '| 直接材料成本 | 面辅料采购单价 × 单件用量 × (1+损耗率) | 录入该货号完整BOM，并核对物料规格 |',
  ].join('\n');
  const html = renderAnswer(content, [goodsSource]);

  assert.match(html, /data-testid="goods-library-photo"/);
  assert.match(html, /href="\/goods-library\/34"/);
  assert.match(html, /href="\/sampler\/34"/);
  assert.match(html, /<p class="[^"]*text-\[15px\]\/\[2\.1\]/);
  assert.match(html, /class="md-heading\b/);
  const cells = html.match(/<t[dh] class="[^"]*"/g) || [];
  assert.ok(cells.length >= 2, html);
  for (const cell of cells) {
    assert.match(cell, /\bpy-3\b/);
    assert.match(cell, /px-\[18px\]/);
    assert.match(cell, /\bwhitespace-normal\b/);
    assert.match(cell, /text-\[14px\]\/\[2\]/);
  }
  assert.doesNotMatch(html, /whitespace-nowrap/);

  const css = fs.readFileSync(new URL('../app/globals.css', import.meta.url), 'utf8');
  const bodyRule = css.match(/\.markdown-body \{[^}]*\}/)?.[0] || '';
  assert.match(bodyRule, /display:\s*flex/);
  assert.match(bodyRule, /gap:\s*16px/);
  assert.match(bodyRule, /line-height:\s*2\.1/);
  const listRule = css.match(/\.markdown-body ul,\s*\.markdown-body ol \{[^}]*\}/)?.[0] || '';
  assert.match(listRule, /gap:\s*6px/);
  const cellRule = css.match(/\.markdown-body td, \.markdown-body th \{[^}]*\}/)?.[0] || '';
  assert.match(cellRule, /padding:\s*12px 18px/);
  assert.match(cellRule, /line-height:\s*2/);
  assert.match(cellRule, /white-space:\s*normal/);
});
