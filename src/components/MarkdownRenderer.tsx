'use client';

import React from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

interface MarkdownRendererProps {
  content: string;
  className?: string;
  darkMode?: boolean;
}

/**
 * LLM 输出预处理：将模型自发使用的 HTML 标签转换为等价 Markdown。
 * react-markdown 默认不渲染原始 HTML（标签连同内部文本被整体丢弃），
 * 导致 <b>货号</b>、<font color=...>品名</font> 等内容渲染为空白。
 * 代码块/行内代码内的内容不处理，避免破坏代码示例。
 */
function preprocessLlmHtml(content: string): string {
  if (!content || !/<[a-zA-Z]/.test(content)) return content;
  const segments = content.split(/(```[\s\S]*?```|`[^`\n]*`)/g);
  return segments
    .map((seg, i) => {
      if (i % 2 === 1) return seg; // 代码段原样保留
      let s = seg;
      s = s.replace(/<(b|strong)>([\s\S]*?)<\/\1>/gi, '**$2**');
      s = s.replace(/<(i|em)>([\s\S]*?)<\/\1>/gi, '*$2*');
      s = s.replace(/<font[^>]*>([\s\S]*?)<\/font>/gi, '**$1**');
      s = s.replace(/<span[^>]*>([\s\S]*?)<\/span>/gi, '$1');
      s = s.replace(/<mark[^>]*>([\s\S]*?)<\/mark>/gi, '**$1**');
      s = s.replace(/<u>([\s\S]*?)<\/u>/gi, '$1');
      s = s.replace(/<s>([\s\S]*?)<\/s>/gi, '~~$1~~');
      s = s.replace(/<br\s*\/?>/gi, '  \n');
      s = s.replace(/<\/(div|p|section|article)>/gi, '\n');
      s = s.replace(/<(div|p|section|article)[^>]*>/gi, '\n');
      // 其余未知标签剥离，保留内部文本
      s = s.replace(/<\/?[a-zA-Z][^>]*>/g, '');
      return s;
    })
    .join('');
}

export default function MarkdownRenderer({ content, className = '', darkMode = false }: MarkdownRendererProps) {
  // Color helpers
  const t = (light: string, dark: string) => darkMode ? dark : light;
  const processedContent = React.useMemo(() => preprocessLlmHtml(content), [content]);

  return (
    <div className={`markdown-body ${className}`}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          // 段落
          p: ({ children }) => (
            <p className={`mb-3 last:mb-0 leading-[1.8] text-[13px] ${t('text-[#1c1c1e]', 'text-[#1c1c1e]')}`}>{children}</p>
          ),
          // 标题 - 简洁装饰线
          h1: ({ children }) => (
            <div className="mb-3 mt-5 first:mt-0">
              <h1 className={`text-[15px] font-bold mb-1.5 ${t('text-[#1C1C1E]', 'text-[#1C1C1E]')}`}>{children}</h1>
              <div className={`h-[2px] w-10 rounded-full ${t('bg-[rgba(118,118,128,0.12)]', 'bg-[#007aff]')}`} />
            </div>
          ),
          h2: ({ children }) => (
            <div className="mb-2.5 mt-4 first:mt-0">
              <div className="flex items-center gap-2.5 mb-1">
                <div className={`w-[3px] h-4 rounded-full shrink-0 ${t('bg-[rgba(0,0,0,0.08)]', 'bg-[#007aff]')}`} />
                <h2 className={`text-[14px] font-bold ${t('text-[#1C1C1E]', 'text-[#1C1C1E]')}`}>{children}</h2>
              </div>
            </div>
          ),
          h3: ({ children }) => (
            <div className="mb-2 mt-3 first:mt-0">
              <h3 className={`text-[13px] font-semibold flex items-center gap-2 ${t('text-[#1c1c1e]', 'text-[#1c1c1e]')}`}>
                <span className={`inline-block w-1.5 h-1.5 rounded-sm shrink-0 ${t('bg-[rgba(0,0,0,0.1)]', 'bg-[#007aff]')}`} />
                {children}
              </h3>
            </div>
          ),
          // 无序列表
          ul: ({ children }) => (
            <ul className="mb-3 ml-1 space-y-1.5 [&>li]:flex [&>li]:items-start [&>li]:gap-2">
              {children}
            </ul>
          ),
          ol: ({ children }) => (
            <ol className="mb-3 ml-1 space-y-1.5 list-none counter-reset-list">
              {children}
            </ol>
          ),
          li: ({ children, node: _node }) => {
            const childArray = React.Children.toArray(children);
            const hasSubList = childArray.some(
              (c) => React.isValidElement(c) && (c.type === 'ul' || c.type === 'ol')
            );
            const textChildren = hasSubList
              ? childArray.filter((c) => !(React.isValidElement(c) && (c.type === 'ul' || c.type === 'ol')))
              : children;

            return (
              <li className={`text-[13px] leading-[1.8] flex items-start gap-2.5 ${t('text-[#1c1c1e]', 'text-[#1c1c1e]')}`}>
                <span className={`inline-block w-[5px] h-[5px] rounded-full shrink-0 mt-[8px] ${t('bg-[rgba(0,0,0,0.12)]', 'bg-[#007aff]')}`} />
                <span className="flex-1 min-w-0">
                  {textChildren}
                  {hasSubList && (
                    <div className="mt-1 ml-0">
                      {childArray.filter((c) => React.isValidElement(c) && (c.type === 'ul' || c.type === 'ol'))}
                    </div>
                  )}
                </span>
              </li>
            );
          },
          // 加粗
          strong: ({ children }) => (
            <strong className={`font-semibold ${t('text-[#1C1C1E]', 'text-[#1C1C1E]')}`}>{children}</strong>
          ),
          // 斜体
          em: ({ children }) => (
            <em className={`italic ${t('text-[#8e8e93]', 'text-[#8e8e93]')}`}>{children}</em>
          ),
          // 行内代码
          code: ({ className: codeClassName, children, ...props }) => {
            const isInline = !codeClassName;
            if (isInline) {
              return (
                <code className={`${t('bg-[rgba(118,118,128,0.08)] text-[#8e8e93] border-[rgba(229,229,234,0.6)]', 'bg-[rgba(118,118,128,0.12)] text-[#007aff] border-[rgba(229,229,234,0.5)]')} px-1.5 py-0.5 rounded-md text-[11.5px] font-mono border`} {...props}>
                  {children}
                </code>
              );
            }
            return (
              <code className={`${codeClassName || ''} text-[12px] font-mono leading-relaxed`} {...props}>
                {children}
              </code>
            );
          },
          // 代码块容器
          pre: ({ children }) => (
            <div className="relative group my-3">
              <div className={`absolute top-0 left-0 right-0 h-8 rounded-t-lg flex items-center px-3 ${t('bg-[#ffffff]', 'bg-[#ffffff]')}`}>
                <div className="flex items-center gap-1.5">
                  <span className="w-2.5 h-2.5 rounded-full bg-[rgba(255,59,48,0.8)]" />
                  <span className="w-2.5 h-2.5 rounded-full bg-[rgba(255,149,0,0.8)]" />
                  <span className="w-2.5 h-2.5 rounded-full bg-[rgba(52,199,89,0.8)]" />
                </div>
              </div>
              <pre className={`bg-[#f2f2f7] text-[#1c1c1e] rounded-lg pt-9 pb-3 px-4 overflow-x-auto text-[12px] leading-[1.7] ${t('border-[rgba(229,229,234,0.5)]', 'border-[rgba(229,229,234,0.5)]')} border`}>
                {children}
              </pre>
            </div>
          ),
          // 引用
          blockquote: ({ children }) => (
            <blockquote className={`my-3 pl-4 py-2 relative rounded-r-lg ${t('bg-[rgba(242,242,247,0.6)]', 'bg-[rgba(0,0,0,0.015)]')}`}>
              <div className={`absolute left-0 top-0 bottom-0 w-[3px] rounded-full ${t('bg-[rgba(0,0,0,0.12)]', 'bg-[#007aff]')}`} />
              <div className={`text-[12.5px] leading-[1.7] ${t('text-[#3a3a3c]', 'text-[#3a3a3c]')}`}>{children}</div>
            </blockquote>
          ),
          // 分割线
          hr: () => (
            <div className="my-4 flex items-center gap-2">
              <div className={`flex-1 h-px bg-gradient-to-r from-transparent ${t('via-[#ffffff]', 'via-[#ffffff]')} to-transparent`} />
              <div className={`w-1 h-1 rounded-full ${t('bg-[rgba(0,0,0,0.1)]', 'bg-[rgba(0,0,0,0.1)]')}`} />
              <div className={`flex-1 h-px bg-gradient-to-r from-transparent ${t('via-[#ffffff]', 'via-[#ffffff]')} to-transparent`} />
            </div>
          ),
          // 链接
          a: ({ href, children }) => (
            <a
              href={href}
              target="_blank"
              rel="noopener noreferrer"
              className={`inline-flex items-center gap-0.5 underline underline-offset-2 transition-colors ${t('text-[#007aff] hover:text-[#007aff] decoration-blue-300/50 hover:decoration-blue-500', 'text-[#007aff] hover:text-[#007aff] decoration-blue-400/50 hover:decoration-blue-300')}`}
            >
              {children}
              <svg className="w-3 h-3 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
              </svg>
            </a>
          ),
          // 表格
          table: ({ children }) => (
            <div className={`my-3 overflow-x-auto rounded-xl border shadow-sm ${t('border-[rgba(229,229,234,0.8)]', 'border-[rgba(229,229,234,0.5)]')}`}>
              <table className="min-w-full text-[12px]">{children}</table>
            </div>
          ),
          thead: ({ children }) => (
            <thead className={`${t('bg-[#f2f2f7] border-[rgba(229,229,234,0.8)]', 'bg-[rgba(118,118,128,0.12)] border-[rgba(229,229,234,0.5)]')} border-b`}>{children}</thead>
          ),
          tbody: ({ children }) => (
            <tbody className={`${t('divide-[rgba(229,229,234,0.8)]', 'divide-[rgba(229,229,234,0.5)]')} divide-y`}>{children}</tbody>
          ),
          tr: ({ children }) => (
            <tr className={`${t('hover:bg-[rgba(242,242,247,0.8)]', 'hover:bg-[rgba(0,0,0,0.015)]')} transition-colors`}>{children}</tr>
          ),
          th: ({ children }) => (
            <th className={`px-4 py-2 text-left font-semibold whitespace-nowrap text-[12px] ${t('text-[#1c1c1e]', 'text-[#1c1c1e]')}`}>
              {children}
            </th>
          ),
          td: ({ children }) => (
            <td className={`px-4 py-2 whitespace-nowrap text-[12px] ${t('text-[#3a3a3c]', 'text-[#3a3a3c]')}`}>{children}</td>
          ),
          // 删除线
          del: ({ children }) => (
            <del className={`line-through ${t('text-[#8e8e93]', 'text-[#8e8e93]')}`}>{children}</del>
          ),
          // 图片
          img: ({ src, alt }) => (
            <div className="my-3">
              <img
                src={src}
                alt={alt || ''}
                className={`max-w-full rounded-xl border shadow-md hover:shadow-lg transition-shadow ${t('border-[rgba(229,229,234,0.6)]', 'border-[rgba(229,229,234,0.5)]')}`}
              />
            </div>
          ),
        }}
      >
        {processedContent}
      </ReactMarkdown>
    </div>
  );
}
