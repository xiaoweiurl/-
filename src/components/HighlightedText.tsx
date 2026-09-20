'use client';

import React from 'react';

interface HighlightedTextProps {
  text: string;
  query: string;
  className?: string;
  highlightClassName?: string;
}

function escapeRegExp(string: string) {
  return string.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function splitHighlightedParts(text: string, query: string): string[] | null {
  try {
    const regex = new RegExp(`(${escapeRegExp(query.trim())})`, 'gi');
    return text.split(regex);
  } catch (error) {
    console.error('高亮文本失败:', error);
    return null;
  }
}

export default function HighlightedText({
  text,
  query,
  className = '',
  highlightClassName = 'bg-[#ff9500] text-[#ff9500] px-0.5 rounded',
}: HighlightedTextProps) {
  if (!query || query.trim() === '') {
    return <span className={className}>{text}</span>;
  }

  const parts = splitHighlightedParts(text, query);
  if (!parts) {
    return <span className={className}>{text}</span>;
  }

  const needle = query.trim().toLowerCase();
  return (
    <span className={className}>
      {parts.map((part, index) => {
        if (part.toLowerCase() === needle) {
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
}

interface MultiHighlightedTextProps {
  text: string;
  queries: string[];
  className?: string;
  highlightClassName?: string;
}

function collectMultiHighlightParts(
  text: string,
  validQueries: string[],
  highlightClassName: string,
): React.ReactNode[] | null {
  try {
    const regexParts = validQueries.map(q => `(${q.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`);
    const regex = new RegExp(regexParts.join('|'), 'gi');

    const matches: { start: number; end: number; text: string }[] = [];
    let match = regex.exec(text);
    while (match !== null) {
      matches.push({
        start: match.index,
        end: match.index + match[0].length,
        text: match[0],
      });
      match = regex.exec(text);
    }

    if (matches.length === 0) {
      return [text];
    }

    const parts: React.ReactNode[] = [];
    let lastIndex = 0;

    matches.forEach((m, i) => {
      if (m.start > lastIndex) {
        parts.push(text.slice(lastIndex, m.start));
      }
      parts.push(
        <mark key={i} className={highlightClassName}>
          {m.text}
        </mark>
      );
      lastIndex = m.end;
    });

    if (lastIndex < text.length) {
      parts.push(text.slice(lastIndex));
    }
    return parts;
  } catch (error) {
    console.error('多关键词高亮失败:', error);
    return null;
  }
}

export function MultiHighlightedText({
  text,
  queries,
  className = '',
  highlightClassName = 'bg-[#ff9500] text-[#ff9500] px-0.5 rounded',
}: MultiHighlightedTextProps) {
  const validQueries = queries.filter(q => q && q.trim() !== '');

  if (validQueries.length === 0) {
    return <span className={className}>{text}</span>;
  }

  const parts = collectMultiHighlightParts(text, validQueries, highlightClassName);
  if (!parts) {
    return <span className={className}>{text}</span>;
  }

  return <span className={className}>{parts}</span>;
}

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
          className="inline-block px-2 py-0.5 mr-1 mb-1 text-xs rounded-full bg-[rgba(118,118,128,0.08)] text-[#8e8e93]"
          highlightClassName="bg-[#ff9500] text-[#ff9500] px-0.5 rounded"
        />
      ))}
    </div>
  );
}
