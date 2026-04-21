'use client';

import React from 'react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { Sparkles, Info } from 'lucide-react';
import type { MatchingConfig, MatchMode } from '@/lib/api/types';

// 匹配模式选项
const MATCH_MODE_OPTIONS: { value: MatchMode; label: string; description: string }[] = [
  {
    value: 'contains',
    label: '包含匹配',
    description: '文件名包含相册名称即可匹配，如"T恤"匹配"夏季T恤新款"',
  },
  {
    value: 'exact',
    label: '精确匹配',
    description: '文件名必须与相册名称完全一致',
  },
  {
    value: 'startsWith',
    label: '开头匹配',
    description: '文件名以相册名称开头，如"T恤"匹配"T恤-男款"',
  },
  {
    value: 'endsWith',
    label: '结尾匹配',
    description: '文件名以相册名称结尾，如"T恤"匹配"男款T恤"',
  },
  {
    value: 'regex',
    label: '正则匹配',
    description: '使用正则表达式进行高级匹配',
  },
  {
    value: 'fuzzy',
    label: '模糊匹配',
    description: '支持同义词匹配，如"T恤"和"tshirt"视为相同',
  },
];

interface SmartAlbumEditorProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: (data: {
    name: string;
    description: string;
    matchingConfig: MatchingConfig;
  }) => void;
  initialData?: {
    name: string;
    description?: string;
    matchingConfig?: MatchingConfig;
  };
  mode?: 'create' | 'edit';
}

export default function SmartAlbumEditor({
  open,
  onOpenChange,
  onConfirm,
  initialData,
  mode = 'create'
}: SmartAlbumEditorProps) {
  const [name, setName] = React.useState(initialData?.name || '');
  const [description, setDescription] = React.useState(initialData?.description || '');
  const [matchMode, setMatchMode] = React.useState<MatchMode>(
    initialData?.matchingConfig?.mode || 'contains'
  );
  const [synonyms, setSynonyms] = React.useState(
    initialData?.matchingConfig?.synonyms?.[0]?.keywords.join(', ') || ''
  );
  const [isSubmitting, setIsSubmitting] = React.useState(false);

  // 重置表单
  React.useEffect(() => {
    if (open) {
      setName(initialData?.name || '');
      setDescription(initialData?.description || '');
      setMatchMode(initialData?.matchingConfig?.mode || 'contains');
      setSynonyms(initialData?.matchingConfig?.synonyms?.[0]?.keywords.join(', ') || '');
    }
  }, [open, initialData]);

  const handleSubmit = async () => {
    if (!name.trim()) {
      return;
    }

    setIsSubmitting(true);
    try {
      // 构建匹配配置
      const matchingConfig: MatchingConfig = {
        mode: matchMode,
      };

      // 如果是 fuzzy 模式且有同义词，添加同义词配置
      if (matchMode === 'fuzzy' && synonyms.trim()) {
        const synonymList = synonyms.split(/[,，、\s]+/).filter(k => k.trim());
        if (synonymList.length > 0) {
          matchingConfig.synonyms = [{
            keywords: synonymList,
            targetKeyword: name.trim(),
          }];
        }
      }

      await onConfirm({
        name: name.trim(),
        description: description.trim(),
        matchingConfig,
      });
      onOpenChange(false);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Sparkles className="w-5 h-5 text-violet-600" />
            {mode === 'create' ? '创建智能相册' : '编辑智能相册'}
          </DialogTitle>
          <DialogDescription>
            智能相册会根据文件名自动匹配归类图片，上传新图片时会自动归入匹配的相册
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-6 py-4">
          {/* 相册名称 */}
          <div className="space-y-2">
            <Label htmlFor="name">
              相册名称 <span className="text-red-500">*</span>
            </Label>
            <Input
              id="name"
              value={name}
              onChange={e => setName(e.target.value)}
              placeholder="如：T恤、冲锋衣、抓绒衣等"
              maxLength={100}
            />
            <p className="text-xs text-slate-500">
              相册名称将用于匹配文件名，建议使用简洁明确的分类名称
            </p>
          </div>

          {/* 相册描述 */}
          <div className="space-y-2">
            <Label htmlFor="description">描述</Label>
            <Textarea
              id="description"
              value={description}
              onChange={e => setDescription(e.target.value)}
              placeholder="简要描述这个相册的用途（可选）"
              rows={2}
              maxLength={500}
            />
          </div>

          {/* 匹配模式 */}
          <div className="space-y-3">
            <Label>匹配模式</Label>
            <div className="space-y-2">
              {MATCH_MODE_OPTIONS.map((option) => (
                <label
                  key={option.value}
                  className={`flex items-start gap-3 p-3 rounded-lg border cursor-pointer transition-colors ${
                    matchMode === option.value
                      ? 'border-violet-500 bg-violet-50'
                      : 'border-slate-200 hover:border-violet-300 hover:bg-slate-50'
                  }`}
                >
                  <input
                    type="radio"
                    name="matchMode"
                    value={option.value}
                    checked={matchMode === option.value}
                    onChange={e => setMatchMode(e.target.value as MatchMode)}
                    className="mt-1"
                  />
                  <div className="flex-1">
                    <div className="font-medium text-sm">{option.label}</div>
                    <div className="text-xs text-slate-500 mt-0.5">
                      {option.description}
                    </div>
                  </div>
                </label>
              ))}
            </div>
          </div>

          {/* 同义词配置（仅在 fuzzy 模式显示） */}
          {matchMode === 'fuzzy' && (
            <div className="space-y-2">
              <Label htmlFor="synonyms">
                同义词 <span className="text-slate-400 font-normal">(可选)</span>
              </Label>
              <Input
                id="synonyms"
                value={synonyms}
                onChange={e => setSynonyms(e.target.value)}
                placeholder="如：tshirt, T-shirt, tee"
                maxLength={500}
              />
              <p className="text-xs text-slate-500">
                多个同义词用逗号分隔，文件名包含任意同义词都会匹配到此相册
              </p>
            </div>
          )}

          {/* 正则提示 */}
          {matchMode === 'regex' && (
            <div className="p-3 bg-amber-50 border border-amber-200 rounded-lg">
              <div className="flex items-start gap-2">
                <Info className="w-4 h-4 text-amber-600 mt-0.5 flex-shrink-0" />
                <div className="text-xs text-amber-800">
                  <p className="font-medium mb-1">正则表达式示例：</p>
                  <p>• <code>.*T恤.*</code> - 包含"T恤"的任意文件名</p>
                  <p>• <code>^T恤.*</code> - 以"T恤"开头的文件名</p>
                  <p>• <code>.*T恤$</code> - 以"T恤"结尾的文件名</p>
                </div>
              </div>
            </div>
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button
            onClick={handleSubmit}
            disabled={!name.trim() || isSubmitting}
            className="bg-gradient-to-r from-violet-500 to-purple-600 hover:from-violet-600 hover:to-purple-700"
          >
            {isSubmitting ? '保存中...' : mode === 'create' ? '创建' : '保存'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
