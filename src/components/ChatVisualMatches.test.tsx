import assert from 'node:assert/strict';
import { test } from 'node:test';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import ChatVisualMatches from './ChatVisualMatches';
import MarkdownRenderer from './MarkdownRenderer';
import ChatFeedbackBar from './ChatFeedbackBar';
import { citeIdsFromSources, mapHistoryChatMessage, parseChatSse, type ChatSource } from '@/lib/chat-sse';

const matches: ChatSource[] = [
  {
    id: 'visual-1',
    source: 'visual_match',
    sourceLabel: '产品',
    title: 'H100 蕾丝中筒',
    goodsNo: 'H100',
    productName: '蕾丝中筒',
    sampler: '张三',
    scorePercent: 86,
    imageUrl: 'https://example.test/goods/9.jpg',
  },
  {
    id: 'visual-2',
    source: 'visual_match',
    sourceLabel: '素材',
    title: '红色蕾丝',
    albumName: '秋冬',
    scorePercent: 71,
    imageUrl: 'https://example.test/lib/1.jpg',
  },
];

const citations: ChatSource[] = [
  { id: 'E1', source: 'supply_chain', recordId: 'erp-tool-analysis', title: '供应链AI工具分析', excerpt: '精准匹配未命中' },
];

test('visual match cards show thumbnail, similarity, source and goods fields', () => {
  const html = renderToStaticMarkup(<ChatVisualMatches matches={matches} />);
  assert.match(html, /data-testid="chat-visual-matches"/);
  assert.equal((html.match(/data-testid="visual-match-card"/g) || []).length, 2);
  assert.match(html, /data-source-label="产品"/);
  assert.match(html, /data-source-label="素材"/);
  assert.match(html, /data-score="86"/);
  assert.match(html, /86%/);
  assert.match(html, /71%/);
  assert.match(html, /H100 蕾丝中筒/);
  assert.match(html, /打样员 张三/);
  assert.match(html, /红色蕾丝/);
  assert.match(html, /秋冬/);
  assert.match(html, /src="https:\/\/example.test\/goods\/9.jpg"/);
  assert.match(html, /type="button"/);
});

test('opening a result shows the larger image', () => {
  const html = renderToStaticMarkup(<ChatVisualMatches matches={matches} initialExpandedId="visual-1" />);
  assert.match(html, /data-testid="visual-match-large"/);
  assert.match(html, /src="https:\/\/example.test\/goods\/9.jpg"/);
  assert.match(html, /aria-label="关闭"/);
});

test('same-product cards show the main image, other thumbs, goods number and sampler', () => {
  const html = renderToStaticMarkup(
    <ChatVisualMatches
      matches={[{
        id: 'visual-1',
        source: 'visual_match',
        cardType: 'product',
        scenario: 'SAME_PRODUCT',
        sourceLabel: '产品',
        goodsNo: 'H100',
        productName: '蕾丝中筒',
        sampler: '张三',
        scorePercent: 86,
        imageUrl: 'https://example.test/goods/main.jpg',
        images: [
          { imageUrl: 'https://example.test/goods/side.jpg', slotLabel: '侧面图' },
          { imageUrl: 'https://example.test/goods/detail.jpg', slotLabel: '细节图' },
        ],
      }]}
    />
  );
  assert.match(html, /data-testid="visual-product-card"/);
  assert.match(html, /data-scenario="SAME_PRODUCT"/);
  assert.match(html, /同款/);
  assert.match(html, /H100 蕾丝中筒/);
  assert.match(html, /打样员 张三/);
  assert.match(html, /src="https:\/\/example.test\/goods\/main.jpg"/);
  assert.equal((html.match(/data-testid="visual-product-thumb"/g) || []).length, 2);
  assert.match(html, /src="https:\/\/example.test\/goods\/side.jpg"/);
  assert.doesNotMatch(html, /data-testid="visual-match-card"/);
});

test('product and reference cards open the sample order and product detail when present', () => {
  const html = renderToStaticMarkup(
    <ChatVisualMatches
      matches={[
        {
          id: 'visual-1',
          source: 'visual_match',
          cardType: 'product',
          goodsNo: 'H100',
          productName: '蕾丝中筒',
          sampler: '张三',
          scorePercent: 86,
          imageUrl: 'https://example.test/goods/main.jpg',
          sampleOrderPath: '/sampler/9',
          productDetailPath: '/goods-library/9',
        },
        {
          id: 'visual-2',
          source: 'visual_match',
          cardType: 'image',
          sourceLabel: '素材',
          title: '红色蕾丝',
          scorePercent: 71,
          imageUrl: 'https://example.test/lib/1.jpg',
          productDetailPath: '/products/p-1',
        },
        {
          id: 'visual-3',
          source: 'visual_match',
          cardType: 'image',
          sourceLabel: '素材',
          title: '没有档案',
          scorePercent: 40,
          imageUrl: 'https://example.test/lib/2.jpg',
          sampleOrderPath: 'javascript:alert(1)',
        },
      ]}
      initialExpandedId="visual-1"
    />
  );
  assert.match(html, /href="\/sampler\/9"/);
  assert.match(html, /href="\/goods-library\/9"/);
  assert.match(html, /href="\/products\/p-1"/);
  assert.doesNotMatch(html, /javascript:/);
  assert.equal((html.match(/data-testid="open-sample-order"/g) || []).length, 2);
});

test('reference cards stay one image each', () => {
  const html = renderToStaticMarkup(
    <ChatVisualMatches
      matches={[{
        id: 'visual-2',
        source: 'visual_match',
        cardType: 'image',
        scenario: 'SIMILAR_REFERENCE',
        sourceLabel: '素材',
        title: '红色蕾丝',
        albumName: '秋冬',
        scorePercent: 71,
        imageUrl: 'https://example.test/lib/1.jpg',
      }]}
    />
  );
  assert.match(html, /data-testid="visual-match-card"/);
  assert.match(html, /data-scenario="SIMILAR_REFERENCE"/);
  assert.match(html, /红色蕾丝/);
  assert.match(html, /秋冬/);
  assert.doesNotMatch(html, /data-testid="visual-product-card"/);
});

test('empty search renders a no-match note', () => {
  const html = renderToStaticMarkup(
    <ChatVisualMatches matches={[{ id: 'visual-empty', source: 'visual_match', empty: true, title: '未找到相似图片' }]} />
  );
  assert.match(html, /data-testid="visual-match-empty"/);
  assert.match(html, /没有找到足够相似的图片/);
  assert.doesNotMatch(html, /data-testid="visual-match-card"/);
});

test('history and citations keep E1 while visual cards stay out of cite buttons', () => {
  const sse = [
    'event:message',
    'data:{"type":"sources","sources":[{"id":"E1","recordId":"erp-tool-analysis","source":"supply_chain","title":"供应链AI工具分析","excerpt":"精准匹配未命中"},{"id":"visual-1","source":"visual_match","sourceLabel":"产品","title":"H100 蕾丝中筒","goodsNo":"H100","productName":"蕾丝中筒","sampler":"张三","scorePercent":86,"imageUrl":"https://example.test/goods/9.jpg"}]}',
    '',
    'event:message',
    'data:{"type":"content","content":"最接近的是 H100 [[E1]]。"}',
    '',
    'event:message',
    'data:{"type":"done","historyId":"hist-visual","sources":[{"id":"E1","recordId":"erp-tool-analysis","source":"supply_chain","title":"供应链AI工具分析"},{"id":"visual-1","source":"visual_match","sourceLabel":"产品","goodsNo":"H100","productName":"蕾丝中筒","sampler":"张三","scorePercent":86,"imageUrl":"https://example.test/goods/9.jpg"}]}',
    '',
  ].join('\n');
  const parsed = parseChatSse(sse, true);
  assert.deepEqual(citeIdsFromSources(parsed.sources), ['E1']);
  assert.equal(parsed.sources.find(source => source.source === 'visual_match')?.goodsNo, 'H100');

  const mapped = mapHistoryChatMessage({
    id: 'hist-visual',
    role: 'assistant',
    content: parsed.content,
    sources: parsed.sources,
  });
  assert.equal(mapped.sources.find(source => source.source === 'visual_match')?.imageUrl, 'https://example.test/goods/9.jpg');

  const html = renderToStaticMarkup(
    <>
      <ChatVisualMatches matches={mapped.sources.filter(source => source.source === 'visual_match')} />
      <MarkdownRenderer content={mapped.content} citeIds={citeIdsFromSources(mapped.sources)} onCite={() => {}} />
      <ChatFeedbackBar
        showComment={false}
        comment=""
        onCommentChange={() => {}}
        onUseful={() => {}}
        onWrong={() => {}}
        onSubmitWrong={() => {}}
      />
    </>
  );
  assert.match(html, /data-testid="visual-match-card"/);
  assert.match(html, /H100/);
  const cite = html.match(/<button\b[^>]*data-cite="E1"[^>]*>/);
  assert.ok(cite, html);
  assert.doesNotMatch(html, /data-cite="visual-1"/);
  assert.match(html, /data-testid="chat-feedback"/);
  assert.deepEqual(citeIdsFromSources([...citations, ...matches]), ['E1']);
});
