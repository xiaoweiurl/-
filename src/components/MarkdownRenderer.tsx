'use client';

import React from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

interface MarkdownRendererProps {
  content: string;
  className?: string;
  darkMode?: boolean;
}

export default function MarkdownRenderer({ content, className = '', darkMode = false }: MarkdownRendererProps) {
  // Color helpers
  const t = (light: string, dark: string) => darkMode ? dark : light;

  return (
    <div className={`markdown-body ${className}`}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          // 段落
          p: ({ children }) => (
            <p className={`mb-3 last:mb-0 leading-[1.8] text-[13px] ${t('text-slate-700', 'text-slate-200')}`}>{children}</p>
          ),
          // 标题 - 简洁装饰线
          h1: ({ children }) => (
            <div className="mb-3 mt-5 first:mt-0">
              <h1 className={`text-[15px] font-bold mb-1.5 ${t('text-slate-800', 'text-white')}`}>{children}</h1>
              <div className={`h-[2px] w-10 rounded-full ${t('bg-slate-700', 'bg-blue-500')}`} />
            </div>
          ),
          h2: ({ children }) => (
            <div className="mb-2.5 mt-4 first:mt-0">
              <div className="flex items-center gap-2.5 mb-1">
                <div className={`w-[3px] h-4 rounded-full shrink-0 ${t('bg-slate-600', 'bg-blue-400')}`} />
                <h2 className={`text-[14px] font-bold ${t('text-slate-800', 'text-white')}`}>{children}</h2>
              </div>
            </div>
          ),
          h3: ({ children }) => (
            <div className="mb-2 mt-3 first:mt-0">
              <h3 className={`text-[13px] font-semibold flex items-center gap-2 ${t('text-slate-700', 'text-slate-200')}`}>
                <span className={`inline-block w-1.5 h-1.5 rounded-sm shrink-0 ${t('bg-slate-500', 'bg-cyan-400')}`} />
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
          li: ({ children, node }) => {
            const childArray = React.Children.toArray(children);
            const hasSubList = childArray.some(
              (c) => React.isValidElement(c) && (c.type === 'ul' || c.type === 'ol')
            );
            const textChildren = hasSubList
              ? childArray.filter((c) => !(React.isValidElement(c) && (c.type === 'ul' || c.type === 'ol')))
              : children;

            return (
              <li className={`text-[13px] leading-[1.8] flex items-start gap-2.5 ${t('text-slate-700', 'text-slate-200')}`}>
                <span className={`inline-block w-[5px] h-[5px] rounded-full shrink-0 mt-[8px] ${t('bg-slate-400', 'bg-blue-400')}`} />
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
            <strong className={`font-semibold ${t('text-slate-900', 'text-white')}`}>{children}</strong>
          ),
          // 斜体
          em: ({ children }) => (
            <em className={`italic ${t('text-slate-500', 'text-slate-400')}`}>{children}</em>
          ),
          // 行内代码
          code: ({ className: codeClassName, children, ...props }) => {
            const isInline = !codeClassName;
            if (isInline) {
              return (
                <code className={`${t('bg-slate-100 text-slate-700 border-slate-200/60', 'bg-slate-700/50 text-blue-300 border-slate-600/50')} px-1.5 py-0.5 rounded-md text-[11.5px] font-mono border`} {...props}>
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
              <div className={`absolute top-0 left-0 right-0 h-8 rounded-t-lg flex items-center px-3 ${t('bg-slate-800', 'bg-slate-800')}`}>
                <div className="flex items-center gap-1.5">
                  <span className="w-2.5 h-2.5 rounded-full bg-red-400/80" />
                  <span className="w-2.5 h-2.5 rounded-full bg-amber-400/80" />
                  <span className="w-2.5 h-2.5 rounded-full bg-green-400/80" />
                </div>
              </div>
              <pre className={`bg-slate-900 text-slate-100 rounded-lg pt-9 pb-3 px-4 overflow-x-auto text-[12px] leading-[1.7] ${t('border-slate-700/50', 'border-slate-600/50')} border`}>
                {children}
              </pre>
            </div>
          ),
          // 引用
          blockquote: ({ children }) => (
            <blockquote className={`my-3 pl-4 py-2 relative rounded-r-lg ${t('bg-slate-50/60', 'bg-slate-700/30')}`}>
              <div className={`absolute left-0 top-0 bottom-0 w-[3px] rounded-full ${t('bg-slate-400', 'bg-blue-400')}`} />
              <div className={`text-[12.5px] leading-[1.7] ${t('text-slate-500', 'text-slate-300')}`}>{children}</div>
            </blockquote>
          ),
          // 分割线
          hr: () => (
            <div className="my-4 flex items-center gap-2">
              <div className={`flex-1 h-px bg-gradient-to-r from-transparent ${t('via-slate-200', 'via-slate-600')} to-transparent`} />
              <div className={`w-1 h-1 rounded-full ${t('bg-slate-300', 'bg-slate-500')}`} />
              <div className={`flex-1 h-px bg-gradient-to-r from-transparent ${t('via-slate-200', 'via-slate-600')} to-transparent`} />
            </div>
          ),
          // 链接
          a: ({ href, children }) => (
            <a
              href={href}
              target="_blank"
              rel="noopener noreferrer"
              className={`inline-flex items-center gap-0.5 underline underline-offset-2 transition-colors ${t('text-blue-600 hover:text-blue-800 decoration-blue-300/50 hover:decoration-blue-500', 'text-blue-400 hover:text-blue-300 decoration-blue-400/50 hover:decoration-blue-300')}`}
            >
              {children}
              <svg className="w-3 h-3 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
              </svg>
            </a>
          ),
          // 表格
          table: ({ children }) => (
            <div className={`my-3 overflow-x-auto rounded-xl border shadow-sm ${t('border-slate-200/80', 'border-slate-600/50')}`}>
              <table className="min-w-full text-[12px]">{children}</table>
            </div>
          ),
          thead: ({ children }) => (
            <thead className={`${t('bg-slate-50 border-slate-200/80', 'bg-slate-700/50 border-slate-600/50')} border-b`}>{children}</thead>
          ),
          tbody: ({ children }) => (
            <tbody className={`${t('divide-slate-100/80', 'divide-slate-700/50')} divide-y`}>{children}</tbody>
          ),
          tr: ({ children }) => (
            <tr className={`${t('hover:bg-slate-50/80', 'hover:bg-slate-700/30')} transition-colors`}>{children}</tr>
          ),
          th: ({ children }) => (
            <th className={`px-4 py-2 text-left font-semibold whitespace-nowrap text-[12px] ${t('text-slate-700', 'text-slate-200')}`}>
              {children}
            </th>
          ),
          td: ({ children }) => (
            <td className={`px-4 py-2 whitespace-nowrap text-[12px] ${t('text-slate-600', 'text-slate-300')}`}>{children}</td>
          ),
          // 删除线
          del: ({ children }) => (
            <del className={`line-through ${t('text-slate-400', 'text-slate-500')}`}>{children}</del>
          ),
          // 图片
          img: ({ src, alt }) => (
            <div className="my-3">
              <img
                src={src}
                alt={alt || ''}
                className={`max-w-full rounded-xl border shadow-md hover:shadow-lg transition-shadow ${t('border-slate-200/60', 'border-slate-600/50')}`}
              />
            </div>
          ),
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
}
