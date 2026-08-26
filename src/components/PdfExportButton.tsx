'use client';

import React, { useState } from 'react';
import { createRoot } from 'react-dom/client';
import { FileDown, Loader2 } from 'lucide-react';
import MarkdownRenderer from './MarkdownRenderer';

interface PdfExportButtonProps {
  /** Markdown 富文本内容 */
  content: string;
  /** 文档标题（缺省时从内容首个标题提取） */
  title?: string;
  className?: string;
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
          {/* 正文（浅色 Markdown 渲染） */}
          <MarkdownRenderer content={content} darkMode={false} />
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
