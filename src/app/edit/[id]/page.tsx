'use client';

import React, { useEffect, useState, useCallback } from 'react';
import { useParams, useRouter } from 'next/navigation';
import dynamic from 'next/dynamic';
import { Loader2, ChevronLeft, AlertCircle, Download, Undo2, Redo2, Eraser, X, Sparkles } from 'lucide-react';
import { Button } from '@/components/ui/button';

// 导入 TUI Image Editor 样式
import 'tui-image-editor/dist/tui-image-editor.css';

// 动态导入 TUI Image Editor
const TuiImageEditor = dynamic(
  () => import('@toast-ui/react-image-editor'),
  { 
    ssr: false,
    loading: () => (
      <div className="flex items-center justify-center h-full bg-white">
        <div className="flex flex-col items-center gap-3">
          <Loader2 className="w-10 h-10 animate-spin text-violet-600" />
          <span className="text-gray-500">加载编辑器...</span>
        </div>
      </div>
    )
  }
);

export default function EditPage() {
  const params = useParams();
  const router = useRouter();
  const imageId = params.id as string;
  
  const [image, setImage] = useState<{ url: string; title: string; originalUrl?: string } | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isMounted, setIsMounted] = useState(false);
  const [editorInstance, setEditorInstance] = useState<any>(null);
  const [error, setError] = useState<string | null>(null);
  
  // AI 去水印相关状态
  const [isRemovingWatermark, setIsRemovingWatermark] = useState(false);
  const [watermarkStatus, setWatermarkStatus] = useState<string | null>(null);

  // 确保只在客户端渲染
  useEffect(() => {
    setIsMounted(true);
  }, []);

  // 加载图片信息
  useEffect(() => {
    const loadImage = async () => {
      try {
        setIsLoading(true);
        setError(null);
        
        const response = await fetch(`/api/images/${imageId}`, {
          credentials: 'include',
        });
        const result = await response.json();
        
        const isSuccess = result.success === true || result.code === 200;
        const data = result.data;
        
        if (isSuccess && data && data.url) {
          const proxyUrl = `/api/images/${imageId}/file`;
          const originalUrl = data.thumbnailUrl || data.url;
          
          const img = new Image();
          img.onload = () => {
            setImage({
              url: proxyUrl,
              title: data.title || '未命名图片',
              originalUrl,
            });
            setIsLoading(false);
          };
          img.onerror = () => {
            console.error('图片预加载失败:', proxyUrl);
            setError('图片无法加载，请检查图片地址是否可访问');
            setIsLoading(false);
          };
          img.src = proxyUrl;
        } else {
          setError(result.error || result.message || '图片加载失败');
          setIsLoading(false);
        }
      } catch (err) {
        console.error('加载图片失败:', err);
        setError('网络错误，请稍后重试');
        setIsLoading(false);
      }
    };

    if (imageId) {
      loadImage();
    }
  }, [imageId]);

  // 返回上一页
  const handleClose = useCallback(() => {
    router.back();
  }, [router]);

  // 下载编辑后的图片
  const handleDownload = useCallback(() => {
    if (!editorInstance) return;
    const dataURL = editorInstance.toDataURL();
    const link = document.createElement('a');
    link.href = dataURL;
    link.download = `edited-${image?.title || 'image'}.png`;
    link.click();
  }, [editorInstance, image]);

  // 撤销
  const handleUndo = useCallback(() => {
    if (!editorInstance) return;
    editorInstance.undo();
  }, [editorInstance]);

  // 重做
  const handleRedo = useCallback(() => {
    if (!editorInstance) return;
    editorInstance.redo();
  }, [editorInstance]);

  // AI 一键去水印
  const handleRemoveWatermark = useCallback(async () => {
    if (!image || isRemovingWatermark) return;
    
    setIsRemovingWatermark(true);
    setWatermarkStatus('正在调用 AI 去除水印...');
    
    try {
      const response = await fetch('/api/images/watermark-remove', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
          imageId,
          imageUrl: image.originalUrl || undefined,
        }),
      });
      
      const result = await response.json();
      
      if (result.success && result.data?.imageUrl) {
        setWatermarkStatus('AI 处理完成，正在加载结果...');
        
        // 将去水印后的图片加载到编辑器
        if (editorInstance) {
          try {
            await editorInstance.loadImageFromURL(result.data.imageUrl, image.title || 'image');
            setWatermarkStatus('去水印完成！');
          } catch {
            setWatermarkStatus('图片已处理，请下载查看');
          }
        } else {
          setWatermarkStatus(null);
        }
        setIsRemovingWatermark(false);
        setTimeout(() => setWatermarkStatus(null), 3000);
      } else {
        setWatermarkStatus(null);
        setIsRemovingWatermark(false);
        alert(result.error || 'AI 去水印失败，请稍后重试');
      }
    } catch (err) {
      console.error('去水印失败:', err);
      setWatermarkStatus(null);
      setIsRemovingWatermark(false);
      alert('网络错误，请稍后重试');
    }
  }, [image, editorInstance, isRemovingWatermark, imageId]);

  // 错误状态
  if (error) {
    return (
      <div className="min-h-screen bg-slate-950 flex items-center justify-center">
        <div className="flex flex-col items-center gap-4 text-center px-4">
          <AlertCircle className="w-16 h-16 text-red-500" />
          <h2 className="text-xl font-semibold text-white">加载失败</h2>
          <p className="text-slate-400 max-w-md">{error}</p>
          <Button
            onClick={handleClose}
            variant="outline"
            className="mt-4 border-slate-700 text-slate-300 hover:bg-slate-800"
          >
            <ChevronLeft className="w-4 h-4 mr-2" />
            返回
          </Button>
        </div>
      </div>
    );
  }

  // 加载状态
  if (isLoading || !image) {
    return (
      <div className="min-h-screen bg-slate-950 flex items-center justify-center">
        <div className="flex flex-col items-center gap-3">
          <Loader2 className="w-10 h-10 animate-spin text-violet-600" />
          <span className="text-slate-400">加载图片...</span>
        </div>
      </div>
    );
  }

  // 编辑器配置 - 使用默认白色主题
  const editorOptions = {
    includeUI: {
      loadImage: {
        path: image.url,
        name: image.title,
      },
      theme: {},
      menu: ['crop', 'flip', 'rotate', 'draw', 'shape', 'icon', 'text', 'mask', 'filter'],
      initMenu: 'crop',
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
    <div className="h-screen flex flex-col bg-slate-900">
      {/* 顶部导航栏 */}
      <div className="h-12 bg-slate-900 border-b border-slate-700 flex items-center justify-between px-4 shrink-0 z-20">
        <div className="flex items-center gap-3">
          <Button
            variant="ghost"
            size="sm"
            onClick={handleClose}
            className="text-slate-300 hover:text-white hover:bg-slate-800"
          >
            <ChevronLeft className="w-4 h-4 mr-1" />
            返回
          </Button>
          <span className="text-slate-600">|</span>
          <span className="text-white font-medium truncate max-w-[300px]">
            {image.title}
          </span>
        </div>
        
        <div className="flex items-center gap-2">
          {/* AI 一键去水印 - 暂未实现，前端入口已注释
          <Button
            variant="ghost"
            size="sm"
            onClick={handleRemoveWatermark}
            disabled={isRemovingWatermark}
            className="text-violet-300 hover:text-white hover:bg-violet-800/50"
            title="AI 一键去水印"
          >
            {isRemovingWatermark ? (
              <Loader2 className="w-4 h-4 mr-1 animate-spin" />
            ) : (
              <Sparkles className="w-4 h-4 mr-1" />
            )}
            AI去水印
          </Button>
          
          <span className="text-slate-700">|</span>
          */}
          
          <Button
            variant="ghost"
            size="sm"
            onClick={handleUndo}
            className="text-slate-300 hover:text-white hover:bg-slate-800"
            title="撤销"
          >
            <Undo2 className="w-4 h-4" />
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={handleRedo}
            className="text-slate-300 hover:text-white hover:bg-slate-800"
            title="重做"
          >
            <Redo2 className="w-4 h-4" />
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={handleDownload}
            className="text-slate-300 hover:text-white hover:bg-slate-800"
            title="下载"
          >
            <Download className="w-4 h-4" />
          </Button>
        </div>
      </div>

      {/* AI 去水印状态栏 - 暂未实现，前端入口已注释
      {watermarkStatus && (
        <div className="h-10 bg-violet-900/40 border-b border-violet-700/50 flex items-center justify-center px-4 shrink-0 z-20">
          <div className="flex items-center gap-2">
            {isRemovingWatermark && <Loader2 className="w-4 h-4 animate-spin text-violet-300" />}
            <span className="text-violet-200 text-sm">{watermarkStatus}</span>
          </div>
        </div>
      )}
      */}

      {/* 编辑器区域 */}
      <div className="flex-1 relative" style={{ minHeight: 0 }}>
        {isMounted && (
          // @ts-ignore
          <TuiImageEditor
            ref={(ref: any) => {
              if (ref && !editorInstance) {
                setEditorInstance(ref.getInstance());
              }
            }}
            {...editorOptions}
          />
        )}
      </div>
    </div>
  );
}
