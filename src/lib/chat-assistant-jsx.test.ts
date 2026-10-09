import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { test } from 'node:test';
import ts from 'typescript';

const PAGES = [
  'src/app/chat/page.tsx',
  'src/app/supply-chain/page.tsx',
];

function parse(rel: string): ts.SourceFile {
  const file = path.join(process.cwd(), rel);
  const text = fs.readFileSync(file, 'utf8');
  return ts.createSourceFile(file, text, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
}

function tagName(node: ts.JsxSelfClosingElement | ts.JsxOpeningElement): string | undefined {
  return ts.isIdentifier(node.tagName) ? node.tagName.text : undefined;
}

function usesTag(sf: ts.SourceFile, name: string): boolean {
  let found = false;
  const visit = (node: ts.Node) => {
    if ((ts.isJsxSelfClosingElement(node) || ts.isJsxOpeningElement(node)) && tagName(node) === name) {
      found = true;
    }
    ts.forEachChild(node, visit);
  };
  visit(sf);
  return found;
}

function directChildNames(children: ts.NodeArray<ts.JsxChild>): string[] {
  const names: string[] = [];
  for (const child of children) {
    if (ts.isJsxSelfClosingElement(child)) {
      const name = tagName(child);
      if (name) names.push(name);
    } else if (ts.isJsxElement(child)) {
      const name = tagName(child.openingElement);
      if (name) names.push(name);
    }
  }
  return names;
}

test('ChatAnswerBody keeps markdown and goods photos in one fragment', () => {
  const sf = parse('src/components/ChatAnswerBody.tsx');
  const diagnostics = sf.parseDiagnostics ?? [];
  assert.deepEqual(
    diagnostics.map((d) => ts.flattenDiagnosticMessageText(d.messageText, '\n')),
    [],
  );
  let found = false;
  const visit = (node: ts.Node) => {
    if (ts.isJsxFragment(node)) {
      const names = directChildNames(node.children);
      if (names.includes('GoodsLibraryChatMedia') && names.includes('MarkdownRenderer')) found = true;
    }
    ts.forEachChild(node, visit);
  };
  visit(sf);
  assert.equal(found, true);
});

for (const rel of PAGES) {
  test(`${rel} renders the shared answer body`, () => {
    const sf = parse(rel);
    const diagnostics = sf.parseDiagnostics ?? [];
    assert.deepEqual(
      diagnostics.map((d) => ts.flattenDiagnosticMessageText(d.messageText, '\n')),
      [],
    );
    assert.equal(usesTag(sf, 'ChatAnswerBody'), true);
  });
}
