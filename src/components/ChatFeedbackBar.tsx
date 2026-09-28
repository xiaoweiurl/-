'use client';

import { ThumbsDown, ThumbsUp } from 'lucide-react';

export default function ChatFeedbackBar({
  feedback,
  showComment,
  comment,
  onCommentChange,
  onUseful,
  onWrong,
  onSubmitWrong,
}: {
  feedback?: 'useful' | 'wrong';
  showComment: boolean;
  comment: string;
  onCommentChange: (value: string) => void;
  onUseful: () => void;
  onWrong: () => void;
  onSubmitWrong: () => void;
}) {
  return (
    <div data-testid="chat-feedback" className="mt-3 flex flex-wrap items-center gap-2 border-t border-[#e5e5ea] pt-2">
      <button
        type="button"
        data-testid="feedback-useful"
        disabled={!!feedback}
        onClick={(event) => {
          event.preventDefault();
          event.stopPropagation();
          onUseful();
        }}
        className={`inline-flex items-center gap-1 rounded-lg px-2 py-1 text-[11px] border ${
          feedback === 'useful'
            ? 'border-[rgba(52,199,89,0.4)] bg-[rgba(52,199,89,0.12)] text-[#34c759]'
            : 'border-[#e5e5ea] text-[#8e8e93] hover:text-[#1c1c1e]'
        }`}
      >
        <ThumbsUp className="w-3 h-3" /> {feedback === 'useful' ? '已反馈' : '有用'}
      </button>
      <button
        type="button"
        data-testid="feedback-wrong"
        disabled={!!feedback}
        onClick={(event) => {
          event.preventDefault();
          event.stopPropagation();
          onWrong();
        }}
        className={`inline-flex items-center gap-1 rounded-lg px-2 py-1 text-[11px] border ${
          feedback === 'wrong'
            ? 'border-[rgba(255,59,48,0.35)] bg-[rgba(255,59,48,0.08)] text-[#ff3b30]'
            : 'border-[#e5e5ea] text-[#8e8e93] hover:text-[#1c1c1e]'
        }`}
      >
        <ThumbsDown className="w-3 h-3" /> {feedback === 'wrong' ? '已反馈' : '答错了'}
      </button>
      {showComment && !feedback && (
        <span className="inline-flex items-center gap-1">
          <input
            data-testid="feedback-comment"
            value={comment}
            onChange={(event) => onCommentChange(event.target.value)}
            placeholder="备注（可选）"
            className="h-7 w-36 rounded-lg border border-[#e5e5ea] px-2 text-[11px] text-[#1c1c1e]"
          />
          <button
            type="button"
            data-testid="feedback-submit"
            onClick={(event) => {
              event.preventDefault();
              event.stopPropagation();
              onSubmitWrong();
            }}
            className="rounded-lg bg-[#ff3b30] px-2 py-1 text-[11px] text-white"
          >
            提交
          </button>
        </span>
      )}
      {feedback === 'wrong' && (
        <span className="text-[11px] text-[#8e8e93]">已提交，管理员会在待补充里看到</span>
      )}
    </div>
  );
}
