import assert from 'node:assert/strict';
import { test } from 'node:test';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import MarkdownRenderer from './MarkdownRenderer';
import ChatFeedbackBar from './ChatFeedbackBar';

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
