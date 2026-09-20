'use client';

import { useEffect, useState, useCallback, useSyncExternalStore } from 'react';
import { useParams, useRouter } from 'next/navigation';
import dynamic from 'next/dynamic';
import { Loader2, ChevronLeft, AlertCircle, Download, Undo2, Redo2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import type { ImageEditorInstance } from '@toast-ui/react-image-editor';

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
          <Loader2 className="w-10 h-10 animate-spin text-[#007aff]" />
          <span className="text-[#8e8e93]">加载编辑器...</span>
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
  const isMounted = useSyncExternalStore(() => () => {}, () => true, () => false);
  const [editorInstance, setEditorInstance] = useState<ImageEditorInstance | null>(null);
  const [error, setError] = useState<string | null>(null);
  
  // AI 去水印相关状态
  const [isRemovingWatermark, setIsRemovingWatermark] = useState(false);
  const [, setWatermarkStatus] = useState<string | null>(null);

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
  const _handleRemoveWatermark = useCallback(async () => {
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
      <div className="min-h-screen bg-[#f2f2f7] flex items-center justify-center">
        <div className="flex flex-col items-center gap-4 text-center px-4">
          <AlertCircle className="w-16 h-16 text-[#ff3b30]" />
          <h2 className="text-xl font-semibold text-[#1C1C1E]">加载失败</h2>
          <p className="text-[#8e8e93] max-w-md">{error}</p>
          <Button
            onClick={handleClose}
            variant="outline"
            className="mt-4 border-[#e5e5ea] text-[#3a3a3c] hover:bg-[#ffffff]"
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
      <div className="min-h-screen bg-[#f2f2f7] flex items-center justify-center">
        <div className="flex flex-col items-center gap-3">
          <Loader2 className="w-10 h-10 animate-spin text-[#007aff]" />
          <span className="text-[#8e8e93]">加载图片...</span>
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
    <div className="h-screen flex flex-col bg-[#f2f2f7]">
      {/* 顶部导航栏 */}
      <div className="h-12 bg-[#f2f2f7] border-b border-[#e5e5ea] flex items-center justify-between px-4 shrink-0 z-20">
        <div className="flex items-center gap-3">
          <Button
            variant="ghost"
            size="sm"
            onClick={handleClose}
            className="text-[#3a3a3c] hover:text-[#1C1C1E] hover:bg-[#ffffff]"
          >
            <ChevronLeft className="w-4 h-4 mr-1" />
            返回
          </Button>
          <span className="text-[#8e8e93]">|</span>
          <span className="text-[#1C1C1E] font-medium truncate max-w-[300px]">
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
            className="text-[#007aff] hover:text-white hover:bg-[rgba(0,122,255,0.5)]"
            title="AI 一键去水印"
          >
            {isRemovingWatermark ? (
              <Loader2 className="w-4 h-4 mr-1 animate-spin" />
            ) : (
              <Sparkles className="w-4 h-4 mr-1" />
            )}
            AI去水印
          </Button>
          
          <span className="text-[#8e8e93]">|</span>
          */}
          
          <Button
            variant="ghost"
            size="sm"
            onClick={handleUndo}
            className="text-[#3a3a3c] hover:text-[#1C1C1E] hover:bg-[#ffffff]"
            title="撤销"
          >
            <Undo2 className="w-4 h-4" />
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={handleRedo}
            className="text-[#3a3a3c] hover:text-[#1C1C1E] hover:bg-[#ffffff]"
            title="重做"
          >
            <Redo2 className="w-4 h-4" />
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={handleDownload}
            className="text-[#3a3a3c] hover:text-[#1C1C1E] hover:bg-[#ffffff]"
            title="下载"
          >
            <Download className="w-4 h-4" />
          </Button>
        </div>
      </div>

      {/* AI 去水印状态栏 - 暂未实现，前端入口已注释
      {watermarkStatus && (
        <div className="h-10 bg-[rgba(0,122,255,0.4)] border-b border-[rgba(0,122,255,0.5)] flex items-center justify-center px-4 shrink-0 z-20">
          <div className="flex items-center gap-2">
            {isRemovingWatermark && <Loader2 className="w-4 h-4 animate-spin text-[#007aff]" />}
            <span className="text-[#007aff] text-sm">{watermarkStatus}</span>
          </div>
        </div>
      )}
      */}

      {/* 编辑器区域 */}
      <div className="flex-1 relative" style={{ minHeight: 0 }}>
        {isMounted && (
          <TuiImageEditor
            ref={(ref: { getInstance(): ImageEditorInstance } | null) => {
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
