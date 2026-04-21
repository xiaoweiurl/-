'use client';

import React from 'react';

interface HighlightedTextProps {
  text: string;
  query: string;
  className?: string;
  highlightClassName?: string;
}

export default function HighlightedText({
  text,
  query,
  className = '',
  highlightClassName = 'bg-yellow-200 text-yellow-900 px-0.5 rounded',
}: HighlightedTextProps) {
  // 如果没有查询或查询为空，直接返回原文
  if (!query || query.trim() === '') {
    return <span className={className}>{text}</span>;
  }

  // 转义正则特殊字符
  const escapeRegExp = (string: string) => {
    return string.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  };

  try {
    // 创建正则表达式，不区分大小写
    const regex = new RegExp(`(${escapeRegExp(query.trim())})`, 'gi');
    const parts = text.split(regex);

    return (
      <span className={className}>
        {parts.map((part, index) => {
          // 检查这部分是否匹配查询（不区分大小写）
          if (part.toLowerCase() === query.trim().toLowerCase()) {
            return (
              <mark key={index} className={highlightClassName}>
                {part}
              </mark>
            );
          }
          return part;
        })}
      </span>
    );
  } catch (error) {
    // 如果正则表达式创建失败（例如用户输入特殊字符），返回原文
    console.error('高亮文本失败:', error);
    return <span className={className}>{text}</span>;
  }
}

// 多关键词高亮组件
interface MultiHighlightedTextProps {
  text: string;
  queries: string[];
  className?: string;
  highlightClassName?: string;
}

export function MultiHighlightedText({
  text,
  queries,
  className = '',
  highlightClassName = 'bg-yellow-200 text-yellow-900 px-0.5 rounded',
}: MultiHighlightedTextProps) {
  // 过滤掉空查询
  const validQueries = queries.filter(q => q && q.trim() !== '');
  
  if (validQueries.length === 0) {
    return <span className={className}>{text}</span>;
  }

  try {
    // 为每个查询创建正则
    const regexParts = validQueries.map(q => `(${q.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`);
    const regex = new RegExp(regexParts.join('|'), 'gi');
    
    // 找到所有匹配的位置
    const matches: { start: number; end: number; text: string }[] = [];
    let match;
    
    while ((match = regex.exec(text)) !== null) {
      matches.push({
        start: match.index,
        end: match.index + match[0].length,
        text: match[0],
      });
    }

    // 如果没有匹配，返回原文
    if (matches.length === 0) {
      return <span className={className}>{text}</span>;
    }

    // 构建结果
    const parts: React.ReactNode[] = [];
    let lastIndex = 0;

    matches.forEach((m, i) => {
      // 添加匹配前的文本
      if (m.start > lastIndex) {
        parts.push(text.slice(lastIndex, m.start));
      }
      // 添加高亮匹配
      parts.push(
        <mark key={i} className={highlightClassName}>
          {m.text}
        </mark>
      );
      lastIndex = m.end;
    });

    // 添加最后一部分文本
    if (lastIndex < text.length) {
      parts.push(text.slice(lastIndex));
    }

    return <span className={className}>{parts}</span>;
  } catch (error) {
    console.error('多关键词高亮失败:', error);
    return <span className={className}>{text}</span>;
  }
}

// 高亮标签中的关键词
interface HighlightedTagsProps {
  tags: string[];
  query: string;
  className?: string;
}

export function HighlightedTags({
  tags,
  query,
  className = '',
}: HighlightedTagsProps) {
  if (!tags || tags.length === 0) {
    return null;
  }

  return (
    <div className={className}>
      {tags.map((tag, index) => (
        <HighlightedText
          key={index}
          text={tag}
          query={query}
          className="inline-block px-2 py-0.5 mr-1 mb-1 text-xs rounded-full bg-slate-100 text-slate-600"
          highlightClassName="bg-yellow-200 text-yellow-900 px-0.5 rounded"
        />
      ))}
    </div>
  );
}
