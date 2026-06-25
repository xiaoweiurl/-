'use client';

import React from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

interface MarkdownRendererProps {
  content: string;
  className?: string;
}

export default function MarkdownRenderer({ content, className = '' }: MarkdownRendererProps) {
  return (
    <div className={`markdown-body ${className}`}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          // 段落
          p: ({ children }) => (
            <p className="mb-3 last:mb-0 leading-[1.8] text-[13px]">{children}</p>
          ),
          // 标题 - 渐变色装饰线
          h1: ({ children }) => (
            <div className="mb-3 mt-5 first:mt-0">
              <h1 className="text-[15px] font-bold text-slate-800 mb-1">{children}</h1>
              <div className="h-[2px] w-12 rounded-full bg-gradient-to-r from-violet-500 to-purple-400" />
            </div>
          ),
          h2: ({ children }) => (
            <div className="mb-2.5 mt-4 first:mt-0">
              <div className="flex items-center gap-2 mb-1">
                <div className="w-1 h-4 rounded-full bg-gradient-to-b from-violet-500 to-purple-400 shrink-0" />
                <h2 className="text-[14px] font-bold text-slate-800">{children}</h2>
              </div>
            </div>
          ),
          h3: ({ children }) => (
            <div className="mb-2 mt-3 first:mt-0">
              <h3 className="text-[13px] font-semibold text-slate-700 flex items-center gap-1.5">
                <span className="inline-block w-1.5 h-1.5 rounded-full bg-violet-400 shrink-0" />
                {children}
              </h3>
            </div>
          ),
          // 无序列表 - 自定义圆点
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
            // 判断是否有子列表
            const childArray = React.Children.toArray(children);
            const hasSubList = childArray.some(
              (c) => React.isValidElement(c) && (c.type === 'ul' || c.type === 'ol')
            );
            const textChildren = hasSubList
              ? childArray.filter((c) => !(React.isValidElement(c) && (c.type === 'ul' || c.type === 'ol')))
              : children;

            return (
              <li className="text-[13px] leading-[1.8] flex items-start gap-2">
                <span className="inline-block w-1.5 h-1.5 rounded-full bg-gradient-to-br from-violet-400 to-purple-500 shrink-0 mt-[9px]" />
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
          // 加粗 - 渐变高亮效果
          strong: ({ children }) => (
            <strong className="font-semibold text-slate-800 bg-gradient-to-r from-violet-50/80 to-purple-50/80 px-1 py-0.5 rounded">
              {children}
            </strong>
          ),
          // 斜体
          em: ({ children }) => (
            <em className="italic text-slate-500">{children}</em>
          ),
          // 行内代码
          code: ({ className: codeClassName, children, ...props }) => {
            const isInline = !codeClassName;
            if (isInline) {
              return (
                <code className="bg-violet-50 text-violet-700 px-1.5 py-0.5 rounded-md text-[11.5px] font-mono border border-violet-100/60" {...props}>
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
          // 代码块容器 - 带复制提示
          pre: ({ children }) => (
            <div className="relative group my-3">
              <div className="absolute top-0 left-0 right-0 h-8 bg-slate-800 rounded-t-lg flex items-center px-3">
                <div className="flex items-center gap-1.5">
                  <span className="w-2.5 h-2.5 rounded-full bg-red-400/80" />
                  <span className="w-2.5 h-2.5 rounded-full bg-amber-400/80" />
                  <span className="w-2.5 h-2.5 rounded-full bg-green-400/80" />
                </div>
              </div>
              <pre className="bg-slate-900 text-slate-100 rounded-lg pt-9 pb-3 px-4 overflow-x-auto text-[12px] leading-[1.7] border border-slate-700/50">
                {children}
              </pre>
            </div>
          ),
          // 引用 - 渐变边线
          blockquote: ({ children }) => (
            <blockquote className="my-3 pl-4 py-2 border-l-[3px] border-l-gradient from-violet-400 to-purple-400 bg-gradient-to-r from-violet-50/40 to-purple-50/20 rounded-r-lg relative">
              <div className="absolute left-0 top-0 bottom-0 w-[3px] rounded-full bg-gradient-to-b from-violet-400 to-purple-400" />
              <div className="text-[12.5px] text-slate-500 leading-[1.7]">{children}</div>
            </blockquote>
          ),
          // 分割线 - 渐变效果
          hr: () => (
            <div className="my-4 flex items-center gap-2">
              <div className="flex-1 h-px bg-gradient-to-r from-transparent via-slate-200 to-transparent" />
              <div className="w-1 h-1 rounded-full bg-violet-300" />
              <div className="flex-1 h-px bg-gradient-to-r from-transparent via-slate-200 to-transparent" />
            </div>
          ),
          // 链接 - 带图标
          a: ({ href, children }) => (
            <a
              href={href}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-0.5 text-violet-500 hover:text-violet-700 underline underline-offset-2 decoration-violet-300/60 hover:decoration-violet-500 transition-colors"
            >
              {children}
              <svg className="w-3 h-3 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
              </svg>
            </a>
          ),
          // 表格 - 现代风格
          table: ({ children }) => (
            <div className="my-3 overflow-x-auto rounded-xl border border-slate-200/80 shadow-sm">
              <table className="min-w-full text-[12px]">{children}</table>
            </div>
          ),
          thead: ({ children }) => (
            <thead className="bg-gradient-to-r from-slate-50 to-violet-50/30 border-b border-slate-200/80">
              {children}
            </thead>
          ),
          tbody: ({ children }) => (
            <tbody className="divide-y divide-slate-100/80">{children}</tbody>
          ),
          tr: ({ children }) => (
            <tr className="hover:bg-violet-50/30 transition-colors">{children}</tr>
          ),
          th: ({ children }) => (
            <th className="px-4 py-2 text-left font-semibold text-slate-700 whitespace-nowrap text-[12px]">
              {children}
            </th>
          ),
          td: ({ children }) => (
            <td className="px-4 py-2 text-slate-600 whitespace-nowrap text-[12px]">{children}</td>
          ),
          // 删除线
          del: ({ children }) => (
            <del className="line-through text-slate-400">{children}</del>
          ),
          // 图片 - 带阴影和圆角
          img: ({ src, alt }) => (
            <div className="my-3">
              <img
                src={src}
                alt={alt || ''}
                className="max-w-full rounded-xl border border-slate-200/60 shadow-md hover:shadow-lg transition-shadow"
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
