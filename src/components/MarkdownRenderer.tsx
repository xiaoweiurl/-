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
            <p className="mb-2 last:mb-0 leading-relaxed">{children}</p>
          ),
          // 标题
          h1: ({ children }) => (
            <h1 className="text-base font-bold mb-2 mt-3 first:mt-0 text-slate-800">{children}</h1>
          ),
          h2: ({ children }) => (
            <h2 className="text-[14px] font-bold mb-1.5 mt-3 first:mt-0 text-slate-800">{children}</h2>
          ),
          h3: ({ children }) => (
            <h3 className="text-[13px] font-semibold mb-1 mt-2 first:mt-0 text-slate-700">{children}</h3>
          ),
          // 列表
          ul: ({ children }) => (
            <ul className="mb-2 ml-4 list-disc space-y-0.5 text-slate-600">{children}</ul>
          ),
          ol: ({ children }) => (
            <ol className="mb-2 ml-4 list-decimal space-y-0.5 text-slate-600">{children}</ol>
          ),
          li: ({ children }) => (
            <li className="text-[13px] leading-relaxed pl-0.5">{children}</li>
          ),
          // 加粗 / 斜体
          strong: ({ children }) => (
            <strong className="font-semibold text-slate-800">{children}</strong>
          ),
          em: ({ children }) => (
            <em className="italic text-slate-500">{children}</em>
          ),
          // 行内代码
          code: ({ className: codeClassName, children, ...props }) => {
            const isInline = !codeClassName;
            if (isInline) {
              return (
                <code className="bg-slate-100 text-violet-600 px-1 py-0.5 rounded text-[12px] font-mono" {...props}>
                  {children}
                </code>
              );
            }
            // 代码块 - 由 pre 包裹
            return (
              <code className={`${codeClassName || ''} text-[12px] font-mono`} {...props}>
                {children}
              </code>
            );
          },
          // 代码块容器
          pre: ({ children }) => (
            <pre className="bg-slate-800 text-slate-100 rounded-lg p-3 my-2 overflow-x-auto text-[12px] leading-relaxed">
              {children}
            </pre>
          ),
          // 引用
          blockquote: ({ children }) => (
            <blockquote className="border-l-2 border-violet-300 pl-3 py-0.5 my-2 bg-violet-50/50 rounded-r text-slate-500 text-[12px]">
              {children}
            </blockquote>
          ),
          // 分割线
          hr: () => (
            <hr className="my-3 border-slate-200" />
          ),
          // 链接
          a: ({ href, children }) => (
            <a href={href} target="_blank" rel="noopener noreferrer" className="text-violet-500 hover:text-violet-700 underline underline-offset-2">
              {children}
            </a>
          ),
          // 表格
          table: ({ children }) => (
            <div className="my-2 overflow-x-auto rounded-lg border border-slate-200">
              <table className="min-w-full text-[12px]">{children}</table>
            </div>
          ),
          thead: ({ children }) => (
            <thead className="bg-slate-50 border-b border-slate-200">{children}</thead>
          ),
          tbody: ({ children }) => (
            <tbody className="divide-y divide-slate-100">{children}</tbody>
          ),
          tr: ({ children }) => (
            <tr className="hover:bg-slate-50/50">{children}</tr>
          ),
          th: ({ children }) => (
            <th className="px-3 py-1.5 text-left font-semibold text-slate-700 whitespace-nowrap">{children}</th>
          ),
          td: ({ children }) => (
            <td className="px-3 py-1.5 text-slate-600 whitespace-nowrap">{children}</td>
          ),
          // 删除线
          del: ({ children }) => (
            <del className="line-through text-slate-400">{children}</del>
          ),
          // 图片
          img: ({ src, alt }) => (
            <img src={src} alt={alt || ''} className="max-w-full rounded-lg my-2 border border-slate-200" />
          ),
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
}
