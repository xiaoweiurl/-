import assert from 'node:assert/strict';
import { test } from 'node:test';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import MarkdownRenderer from './MarkdownRenderer';
import ChatFeedbackBar from './ChatFeedbackBar';
import { mapHistoryChatMessage, parseChatSse } from '@/lib/chat-sse';

const sample = [
  '段落里的引用 [[E1]]。',
  '',
  '- 列表 **粗体 [[B1]]**',
  '',
  '| 说明 |',
  '| --- |',
  '| 单元格 [[K3]] 和没有来源的 [[Z9]] |',
  '',
  '模型如果写成链接 [E1](cite:E1) 也不能跳转。',
].join('\n');

test('citations in paragraphs, lists, bold, and table cells are buttons, not links', () => {
  const html = renderToStaticMarkup(
    <MarkdownRenderer
      content={sample}
      citeIds={['E1', 'B1', 'K3']}
      onCite={() => {}}
    />
  );

  for (const id of ['E1', 'B1', 'K3']) {
    const button = html.match(new RegExp(`<button\\b[^>]*data-cite="${id}"[^>]*>`));
    assert.ok(button, `missing cite button ${id}: ${html}`);
    assert.match(button[0], /type="button"/);
    assert.doesNotMatch(html, new RegExp(`<a\\b[^>]*data-cite="${id}"`));
    assert.doesNotMatch(html, new RegExp(`href="[^"]*${id}`));
  }
  const missing = html.match(/<span\b[^>]*data-cite="Z9"[^>]*>/);
  assert.ok(missing, html);
  assert.match(missing[0], /data-cite-missing="true"/);
  assert.doesNotMatch(html, /<button\b[^>]*data-cite="Z9"/);
  assert.equal((html.match(/<a /g) || []).length, 0);
});

test('empty cite href does not become a homepage link', () => {
  const html = renderToStaticMarkup(
    <MarkdownRenderer content={'跳转 [E1](/) 以及 [B1](cite:B1)'} citeIds={['E1']} onCite={() => {}} />
  );
  assert.match(html, /<button[^>]*data-cite="E1"/);
  assert.match(html, /<span[^>]*data-cite="B1"[^>]*data-cite-missing="true"/);
  assert.doesNotMatch(html, /<a /);
});

test('finished assistant answers expose useful and wrong feedback actions', () => {
  const html = renderToStaticMarkup(
    <ChatFeedbackBar
      showComment={false}
      comment=""
      onCommentChange={() => {}}
      onUseful={() => {}}
      onWrong={() => {}}
      onSubmitWrong={() => {}}
    />
  );
  assert.match(html, /data-testid="chat-feedback"/);
  assert.match(html, /有用/);
  assert.match(html, /答错了/);
  assert.match(html, /<button\b[^>]*data-testid="feedback-useful"[^>]*type="button"|<button\b[^>]*type="button"[^>]*data-testid="feedback-useful"/);
  assert.match(html, /<button\b[^>]*data-testid="feedback-wrong"[^>]*type="button"|<button\b[^>]*type="button"[^>]*data-testid="feedback-wrong"/);
  assert.doesNotMatch(html, /<a /);
});

test('wrong feedback opens an optional comment and then shows submitted state', () => {
  const asking = renderToStaticMarkup(
    <ChatFeedbackBar
      showComment
      comment=""
      onCommentChange={() => {}}
      onUseful={() => {}}
      onWrong={() => {}}
      onSubmitWrong={() => {}}
    />
  );
  assert.match(asking, /data-testid="feedback-comment"/);
  assert.match(asking, /备注（可选）/);

  const submitted = renderToStaticMarkup(
    <ChatFeedbackBar
      feedback="wrong"
      showComment={false}
      comment="货号不对"
      onCommentChange={() => {}}
      onUseful={() => {}}
      onWrong={() => {}}
      onSubmitWrong={() => {}}
    />
  );
  assert.match(submitted, /已反馈/);
  assert.match(submitted, /已提交，管理员会在待补充里看到/);
});

const toolMissSse = [
  'event:conversation',
  'data:conv-factory-1',
  '',
  'event:message',
  'data:{"type":"sources","sources":[{"id":"E1","recordId":"erp-tool-analysis","source":"supply_chain","title":"供应链AI工具分析","excerpt":"精准匹配未命中","score":0},{"id":"B1","recordId":"doc-b","source":"salesperson_kb","title":"业务员资料","excerpt":"EXAMPLE","score":0.4},{"id":"K3","recordId":"doc-k","source":"knowledge","title":"知识库","excerpt":"EXAMPLE","score":0.2}]}',
  '',
  'event:message',
  'data:{"type":"content","content":"货号未命中 [[E1]]，资料见 [[B1]][[K3]]。"}',
  '',
  'event:message',
  'data:{"type":"done","historyId":"hist-e1","conversationId":"conv-factory-1","sources":[{"id":"E1","recordId":"erp-tool-analysis","source":"supply_chain","title":"供应链AI工具分析","excerpt":"精准匹配未命中","score":0},{"id":"B1","recordId":"doc-b","source":"salesperson_kb","title":"业务员资料","excerpt":"EXAMPLE","score":0.4},{"id":"K3","recordId":"doc-k","source":"knowledge","title":"知识库","excerpt":"EXAMPLE","score":0.2}]}',
  '',
].join('\n');

test('factory tool-miss SSE keeps E1 clickable and shows feedback after the stream ends', () => {
  const parsed = parseChatSse(toolMissSse, true);
  assert.equal(parsed.isStreaming, false);
  assert.equal(parsed.historyId, 'hist-e1');
  assert.equal(parsed.conversationId, 'conv-factory-1');
  assert.deepEqual(parsed.sources.map(s => s.id), ['E1', 'B1', 'K3']);
  assert.equal(parsed.sources[0].recordId, 'erp-tool-analysis');

  const html = renderToStaticMarkup(
    <>
      <MarkdownRenderer
        content={parsed.content}
        citeIds={parsed.sources.map(s => s.id).filter((id): id is string => !!id)}
        onCite={() => {}}
      />
      {!parsed.isStreaming && parsed.content && (
        <ChatFeedbackBar
          showComment={false}
          comment=""
          onCommentChange={() => {}}
          onUseful={() => {}}
          onWrong={() => {}}
          onSubmitWrong={() => {}}
        />
      )}
    </>
  );
  for (const id of ['E1', 'B1', 'K3']) {
    const button = html.match(new RegExp(`<button\\b[^>]*data-cite="${id}"[^>]*>`));
    assert.ok(button, html);
    assert.doesNotMatch(button[0], /data-cite-missing/);
  }
  assert.match(html, /data-testid="chat-feedback"/);
  assert.match(html, /有用/);
  assert.match(html, /答错了/);
});

test('done frame alone restores E1 when the earlier sources event was dropped', () => {
  const onlyDone = [
    'event:message',
    'data:{"type":"content","content":"未命中 [[E1]]"}',
    '',
    'event:message',
    'data:{"type":"done","historyId":"hist-2","sources":[{"id":"E1","recordId":"erp-tool-analysis","source":"supply_chain","title":"供应链AI工具分析","excerpt":"x","score":0}]}',
  ].join('\n');
  const parsed = parseChatSse(onlyDone, true);
  assert.equal(parsed.historyId, 'hist-2');
  assert.equal(parsed.sources[0]?.id, 'E1');
  assert.equal(parsed.isStreaming, false);
});

test('multiline data frames and a final frame without a blank line still parse', () => {
  const multiline = [
    'event:message',
    'data:{"type":"done","historyId":"hist-3","sources":[{"id":"E1",',
    'data:"recordId":"erp-tool-analysis","source":"supply_chain","title":"供应链AI工具分析"}]}',
  ].join('\n');
  const parsed = parseChatSse(multiline, true);
  assert.equal(parsed.historyId, 'hist-3');
  assert.equal(parsed.sources[0]?.id, 'E1');
  assert.equal(parsed.sources[0]?.recordId, 'erp-tool-analysis');
});

test('reader completion without a done event still ends streaming', () => {
  const partial = 'event:message\ndata:{"type":"content","content":"还在输出 [[E1]]"}\n\n';
  const parsed = parseChatSse(partial, true);
  assert.equal(parsed.sawTerminal, false);
  assert.equal(parsed.isStreaming, false);
  assert.match(parsed.content, /E1/);
});

test('history messages keep sources_json ids so feedback can render', () => {
  const mapped = mapHistoryChatMessage({
    id: 'hist-old',
    role: 'assistant',
    content: '历史回答 [[E1]]',
    sources: [{ id: 'E1', recordId: 'erp-tool-analysis', source: 'supply_chain', title: '供应链AI工具分析' }],
  });
  assert.equal(mapped.historyId, 'hist-old');
  assert.equal(mapped.isStreaming, false);
  assert.equal(mapped.sources[0]?.id, 'E1');
  const html = renderToStaticMarkup(
    <>
      <MarkdownRenderer content={mapped.content} citeIds={['E1']} onCite={() => {}} />
      <ChatFeedbackBar showComment={false} comment="" onCommentChange={() => {}} onUseful={() => {}} onWrong={() => {}} onSubmitWrong={() => {}} />
    </>
  );
  assert.match(html, /<button[^>]*data-cite="E1"/);
  assert.match(html, /data-testid="feedback-useful"/);
});
