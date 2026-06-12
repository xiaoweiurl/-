'use client';

import React, { useEffect, useState, useCallback } from 'react';
import dynamic from 'next/dynamic';
import { X, Loader2, Download, RotateCcw, RotateCw, Maximize, Minimize } from 'lucide-react';

import { Button } from '@/components/ui/button';
import type { ImageItem } from './ImageCard';

// 导入 TUI Image Editor 样式
import 'tui-image-editor/dist/tui-image-editor.css';

interface ImageEditorProps {
  image: ImageItem;
  onClose: () => void;
  onSave?: (editedImage: string) => void;
}

// 动态导入 TUI Image Editor
const TuiImageEditor = dynamic(
  () => import('@toast-ui/react-image-editor'),
  { 
    ssr: false,
    loading: () => (
      <div className="flex items-center justify-center h-full bg-slate-900">
        <div className="flex flex-col items-center gap-3">
          <Loader2 className="w-10 h-10 animate-spin text-violet-600" />
          <span className="text-slate-400">加载编辑器...</span>
        </div>
      </div>
    )
  }
);

// 全局编辑器实例
let editorInstance: any = null;

export default function ImageEditor({ image, onClose, onSave }: ImageEditorProps) {
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);

  // 获取完整的图片 URL（将 localhost:8080 改写为代理路径）
  const getFullImageUrl = useCallback((url: string): string => {
    // 已经是代理路径，直接返回
    if (url.startsWith('/api/proxy/')) return url;
    // 如果是包含 localhost:8080 的完整URL，改写为代理路径
    if (url.includes('localhost:8080')) {
      const pathWithoutHost = url.replace(/^https?:\/\/localhost:8080/, '');
      const pathWithoutApiPrefix = pathWithoutHost.replace(/^\/api/, '');
      return `/api/proxy${pathWithoutApiPrefix}`;
    }
    
    // 其他完整URL（非localhost，如CDN等）
    if (url.startsWith('http://') || url.startsWith('https://')) {
      return url;
    }
    
    // 相对路径，通过代理访问
    if (url.startsWith('/')) {
      return `/api/proxy${url}`;
    }
    
    return url;
  }, []);

  // 编辑器加载完成后隐藏加载状态
  useEffect(() => {
    const timer = setTimeout(() => {
      setIsLoading(false);
    }, 1500);
    return () => clearTimeout(timer);
  }, []);

  // 获取编辑器实例
  const getEditor = useCallback(() => {
    return editorInstance;
  }, []);

  // 处理下载
  const handleDownload = useCallback(() => {
    const editor = getEditor();
    if (!editor) return;
    
    setIsSaving(true);
    try {
      const dataURL = editor.toDataURL({
        format: 'jpeg',
        quality: 0.92,
      });
      
      const link = document.createElement('a');
      link.download = `edited_${image.title || 'image'}.jpg`;
      link.href = dataURL;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      
      onSave?.(dataURL);
    } catch (error) {
      console.error('保存图片失败:', error);
    } finally {
      setIsSaving(false);
    }
  }, [image.title, onSave, getEditor]);

  // 处理旋转
  const handleRotate = useCallback((angle: number) => {
    const editor = getEditor();
    if (!editor) return;
    editor.rotate(angle);
  }, [getEditor]);

  // 处理翻转
  const handleFlip = useCallback((type: 'flipX' | 'flipY') => {
    const editor = getEditor();
    if (!editor) return;
    editor[type]();
  }, [getEditor]);

  // 处理重置
  const handleReset = useCallback(() => {
    const editor = getEditor();
    if (!editor) return;
    const fullUrl = getFullImageUrl(image.url);
    editor.loadImageFromURL(fullUrl, image.title || 'image');
  }, [image.url, image.title, getEditor, getFullImageUrl]);

  // 处理放大/缩小
  const handleZoom = useCallback((ratio: number) => {
    const editor = getEditor();
    if (!editor) return;
    editor.setZoomRatio(ratio);
  }, [getEditor]);

  // 编辑器配置
  const fullImageUrl = getFullImageUrl(image.url);
  const editorOptions = {
    includeUI: {
      loadImage: {
        path: fullImageUrl,
        name: image.title || 'image',
      },
      theme: {
        'common.background': '#0f172a',
        'common.border': '#1e293b',
        'header.background': '#1e293b',
        'header.border': '#334155',
        'menu.normalIcon.path': '#94a3b8',
        'menu.normalIcon.name': '#94a3b8',
        'menu.disabledIcon.path': '#475569',
        'menu.disabledIcon.name': '#475569',
        'menu.hoverIcon.path': '#f8fafc',
        'menu.hoverIcon.name': '#f8fafc',
        'menu.activeIcon.path': '#8b5cf6',
        'menu.activeIcon.name': '#8b5cf6',
        'submenu.background': '#1e293b',
        'submenu.partition.color': '#334155',
        'submenu.normalLabel.color': '#94a3b8',
        'submenu.normalLabel.path': '#94a3b8',
        'submenu.normalLabel.name': '#94a3b8',
        'submenu.activeLabel.color': '#f8fafc',
        'submenu.activeLabel.path': '#f8fafc',
        'submenu.activeLabel.name': '#f8fafc',
        'checkbox.background': '#334155',
        'checkbox.border': '#475569',
        'checkbox.disabledBackground': '#1e293b',
        'checkbox.disabledBorder': '#334155',
        'range.pointer.color': '#8b5cf6',
        'range.bar.color': '#334155',
        'range.subbar.color': '#8b5cf6',
        'range.value.color': '#f8fafc',
        'range.value.fontWeight': 'normal',
        'range.value.fontSize': '12px',
        'colorpicker.button.border': '#475569',
        'colorpicker.title.color': '#f8fafc',
      },
      menu: ['crop', 'flip', 'rotate', 'draw', 'shape', 'icon', 'text', 'mask', 'filter'],
      initMenu: 'filter',
      uiSize: {
        width: '100%',
        height: '100%',
      },
      menuBarPosition: 'bottom',
    },
    cssMaxWidth: 1200,
    cssMaxHeight: 800,
    usageStatistics: false,
    selectionStyle: {
      cornerSize: 20,
      rotatingPointOffset: 70,
    },
  };

  return (
    <div className="fixed inset-0 z-[100] flex flex-col bg-slate-950">
      {/* 顶部工具栏 */}
      <div className="h-14 bg-slate-900 border-b border-slate-800 flex items-center justify-between px-4 shrink-0">
        <div className="flex items-center gap-3">
          <Button
            variant="ghost"
            size="sm"
            onClick={onClose}
            className="text-slate-400 hover:text-white hover:bg-slate-800"
          >
            <X className="w-4 h-4 mr-1" />
            关闭
          </Button>
          <span className="text-slate-600">|</span>
          <span className="text-white font-medium truncate max-w-[300px]">
            {image.title}
          </span>
        </div>

        {/* 快捷工具 */}
        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => handleRotate(-90)}
            className="text-slate-400 hover:text-white hover:bg-slate-800"
            title="向左旋转"
          >
            <RotateCcw className="w-4 h-4" />
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => handleRotate(90)}
            className="text-slate-400 hover:text-white hover:bg-slate-800"
            title="向右旋转"
          >
            <RotateCw className="w-4 h-4" />
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => handleFlip('flipX')}
            className="text-slate-400 hover:text-white hover:bg-slate-800"
            title="水平翻转"
          >
            <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M12 3v18M7 7l-5 5 5 5M17 7l5 5-5 5" />
            </svg>
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => handleFlip('flipY')}
            className="text-slate-400 hover:text-white hover:bg-slate-800"
            title="垂直翻转"
          >
            <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M3 12h18M7 7l5-5 5 5M7 17l5 5 5-5" />
            </svg>
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => handleZoom(1.5)}
            className="text-slate-400 hover:text-white hover:bg-slate-800"
            title="放大"
          >
            <Maximize className="w-4 h-4" />
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => handleZoom(0.5)}
            className="text-slate-400 hover:text-white hover:bg-slate-800"
            title="缩小"
          >
            <Minimize className="w-4 h-4" />
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={handleReset}
            className="text-slate-400 hover:text-white hover:bg-slate-800"
            title="重置"
          >
            <RotateCcw className="w-4 h-4" />
          </Button>
          
          <div className="w-px h-6 bg-slate-700 mx-2" />
          
          <Button
            size="sm"
            onClick={handleDownload}
            disabled={isSaving}
            className="bg-violet-600 hover:bg-violet-700 text-white"
          >
            {isSaving ? (
              <Loader2 className="w-4 h-4 mr-1 animate-spin" />
            ) : (
              <Download className="w-4 h-4 mr-1" />
            )}
            {isSaving ? '保存中...' : '下载'}
          </Button>
        </div>
      </div>

      {/* 编辑器区域 */}
      <div className="flex-1 relative overflow-hidden">
        {typeof window !== 'undefined' && (
          // @ts-ignore - 类型声明不完美，但功能正常
          <TuiImageEditor
            {...editorOptions}
          />
        )}
        
        {/* 加载遮罩 */}
        {isLoading && (
          <div className="absolute inset-0 bg-slate-900 flex items-center justify-center z-10">
            <div className="flex flex-col items-center gap-3">
              <Loader2 className="w-10 h-10 animate-spin text-violet-600" />
              <span className="text-slate-400">加载编辑器...</span>
            </div>
          </div>
        )}
      </div>

      {/* 底部提示 */}
      <div className="h-8 bg-slate-900 border-t border-slate-800 flex items-center justify-center text-xs text-slate-500">
        使用底部工具栏进行裁剪、旋转、滤镜、绘图、文字、形状等编辑操作
      </div>
    </div>
  );
}
