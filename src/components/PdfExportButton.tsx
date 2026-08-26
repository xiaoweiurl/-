'use client';

import React, { useState } from 'react';
import { createRoot } from 'react-dom/client';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { FileDown, Loader2 } from 'lucide-react';

interface PdfExportButtonProps {
  /** Markdown 富文本内容 */
  content: string;
  /** 文档标题（缺省时从内容首个标题提取） */
  title?: string;
  className?: string;
}

/**
 * PDF 打印版式：全部使用内联 hex/rgb 颜色。
 * 不能用 Tailwind 类——Tailwind 4 的颜色编译为 oklch() 函数，
 * html2canvas 解析 computed style 时会报 "unsupported color function lab/oklch"。
 */
const S: Record<string, React.CSSProperties> = {
  p: { margin: '0 0 10px', lineHeight: 1.8, fontSize: '13px', color: '#334155' },
  h1: { fontSize: '17px', fontWeight: 700, color: '#0f172a', margin: '18px 0 8px', paddingBottom: '6px', borderBottom: '2px solid #2563eb' },
  h2: { fontSize: '15px', fontWeight: 700, color: '#0f172a', margin: '16px 0 8px', paddingLeft: '8px', borderLeft: '4px solid #2563eb' },
  h3: { fontSize: '13.5px', fontWeight: 600, color: '#1e293b', margin: '12px 0 6px' },
  h4: { fontSize: '13px', fontWeight: 600, color: '#334155', margin: '10px 0 4px' },
  ul: { margin: '0 0 10px', paddingLeft: '20px', listStyle: 'disc', fontSize: '13px', color: '#334155', lineHeight: 1.8 },
  ol: { margin: '0 0 10px', paddingLeft: '22px', listStyle: 'decimal', fontSize: '13px', color: '#334155', lineHeight: 1.8 },
  li: { marginBottom: '4px' },
  strong: { fontWeight: 700, color: '#0f172a' },
  blockquote: { margin: '0 0 10px', padding: '8px 12px', borderLeft: '4px solid #cbd5e1', background: '#f8fafc', color: '#475569', fontSize: '12.5px', lineHeight: 1.7 },
  code: { background: '#f1f5f9', color: '#be185d', padding: '1px 5px', borderRadius: '4px', fontSize: '12px', fontFamily: 'Menlo, Consolas, monospace' },
  pre: { background: '#0f172a', color: '#e2e8f0', padding: '12px 14px', borderRadius: '8px', fontSize: '11.5px', lineHeight: 1.6, overflow: 'hidden', marginBottom: '10px', fontFamily: 'Menlo, Consolas, monospace' },
  table: { width: '100%', borderCollapse: 'collapse', fontSize: '11px', margin: '8px 0 14px' },
  th: { border: '1px solid #cbd5e1', background: '#f1f5f9', padding: '5px 8px', textAlign: 'left', fontWeight: 600, color: '#0f172a' },
  td: { border: '1px solid #e2e8f0', padding: '5px 8px', color: '#334155', lineHeight: 1.5 },
  hr: { border: 'none', borderTop: '1px solid #e2e8f0', margin: '14px 0' },
  a: { color: '#2563eb', textDecoration: 'underline' },
};

/** PDF 专用 Markdown 渲染器（零 Tailwind，纯内联样式，确保 html2canvas 可解析） */
function PdfMarkdown({ content }: { content: string }) {
  return (
    <ReactMarkdown
      remarkPlugins={[remarkGfm]}
      components={{
        p: ({ children }) => <p style={S.p}>{children}</p>,
        h1: ({ children }) => <h1 style={S.h1}>{children}</h1>,
        h2: ({ children }) => <h2 style={S.h2}>{children}</h2>,
        h3: ({ children }) => <h3 style={S.h3}>{children}</h3>,
        h4: ({ children }) => <h4 style={S.h4}>{children}</h4>,
        ul: ({ children }) => <ul style={S.ul}>{children}</ul>,
        ol: ({ children }) => <ol style={S.ol}>{children}</ol>,
        li: ({ children }) => <li style={S.li}>{children}</li>,
        strong: ({ children }) => <strong style={S.strong}>{children}</strong>,
        blockquote: ({ children }) => <blockquote style={S.blockquote}>{children}</blockquote>,
        code: ({ children, className }) =>
          className ? (
            <code style={{ display: 'block', background: 'none', color: 'inherit', padding: 0 }}>{children}</code>
          ) : (
            <code style={S.code}>{children}</code>
          ),
        pre: ({ children }) => <pre style={S.pre}>{children}</pre>,
        table: ({ children }) => <table style={S.table}>{children}</table>,
        thead: ({ children }) => <thead>{children}</thead>,
        tbody: ({ children }) => <tbody>{children}</tbody>,
        tr: ({ children }) => <tr>{children}</tr>,
        th: ({ children }) => <th style={S.th}>{children}</th>,
        td: ({ children }) => <td style={S.td}>{children}</td>,
        hr: () => <hr style={S.hr} />,
        a: ({ children, href }) => <a href={href} style={S.a}>{children}</a>,
      }}
    >
      {content}
    </ReactMarkdown>
  );
}

/** 从 Markdown 内容提取首个标题作为文档名 */
function extractTitle(content: string): string {
  for (const line of content.split('\n')) {
    const m = line.match(/^#{1,3}\s*(.+)$/);
    if (m) return m[1].replace(/[【】[\]*_#]/g, '').trim().slice(0, 40);
  }
  const first = content.trim().split('\n')[0] || '报告';
  return first.replace(/[【】[\]*_#]/g, '').trim().slice(0, 20) || '报告';
}

/** 文件名安全化 */
function safeFilename(name: string): string {
  return name.replace(/[\\/:*?"<>|]/g, '_').slice(0, 60) || '报告';
}

/**
 * PDF 导出按钮：将 Markdown 富文本渲染为浅色打印版式，用 html2pdf.js 导出 A4 PDF。
 * 屏外渲染（left:-12000px），不干扰页面；完成后自动清理 DOM。
 */
export default function PdfExportButton({ content, title, className = '' }: PdfExportButtonProps) {
  const [exporting, setExporting] = useState(false);

  const handleExport = async () => {
    if (exporting || !content) return;
    setExporting(true);
    const host = document.createElement('div');
    host.style.cssText = 'position:fixed;left:-12000px;top:0;width:760px;background:#ffffff;z-index:-1;';
    document.body.appendChild(host);
    let root: ReturnType<typeof createRoot> | null = null;
    try {
      const html2pdf = (await import('html2pdf.js')).default;
      const docTitle = (title || extractTitle(content)).trim();
      const dateStr = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' });

      root = createRoot(host);
      root.render(
        <div style={{ padding: '36px 40px', background: '#ffffff', color: '#1e293b', fontFamily: '-apple-system, "PingFang SC", "Microsoft YaHei", sans-serif' }}>
          {/* 文档头 */}
          <div style={{ borderBottom: '3px solid #2563eb', paddingBottom: '14px', marginBottom: '20px' }}>
            <div style={{ fontSize: '22px', fontWeight: 700, color: '#0f172a', lineHeight: 1.4 }}>{docTitle}</div>
            <div style={{ fontSize: '11px', color: '#64748b', marginTop: '6px', display: 'flex', justifyContent: 'space-between' }}>
              <span>盈云产品智能中台 · AI 业务助手生成</span>
              <span>{dateStr}</span>
            </div>
          </div>
          {/* 正文（纯内联样式 Markdown 渲染，避免 oklch 颜色函数） */}
          <PdfMarkdown content={content} />
          {/* 文档尾 */}
          <div style={{ borderTop: '1px solid #e2e8f0', marginTop: '24px', paddingTop: '10px', fontSize: '10px', color: '#94a3b8', display: 'flex', justifyContent: 'space-between' }}>
            <span>本报告由 AI 基于内部数据库与知识库生成，数据结论以来源标注为准</span>
            <span>盈云产品智能中台</span>
          </div>
        </div>
      );
      // 等待 React 渲染与字体/表格布局稳定
      await new Promise(r => setTimeout(r, 400));

      await html2pdf()
        .set({
          margin: [10, 10, 12, 10],
          filename: `${safeFilename(docTitle)}.pdf`,
          image: { type: 'jpeg', quality: 0.95 },
          html2canvas: { scale: 2, useCORS: true, backgroundColor: '#ffffff', logging: false },
          jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' },
          pagebreak: { mode: ['css', 'legacy'] },
        })
        .from(host)
        .save();
    } catch (e) {
      console.error('PDF 导出失败:', e);
    } finally {
      try { root?.unmount(); } catch { /* ignore */ }
      host.remove();
      setExporting(false);
    }
  };

  return (
    <button
      onClick={handleExport}
      disabled={exporting}
      className={`p-1 rounded-md text-slate-400 hover:text-slate-200 hover:bg-slate-700/50 transition-all disabled:opacity-50 ${className}`}
      title="导出 PDF"
    >
      {exporting ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <FileDown className="w-3.5 h-3.5" />}
    </button>
  );
}
