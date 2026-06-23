'use client';

import { useState, useCallback } from 'react';
import Sidebar from '@/components/Sidebar';
import { getCurrentBrand } from '@/lib/brand';
import {
  Sparkles,
  Download,
  Loader2,
  Image as ImageIcon,
  Settings2,
  ChevronDown,
  X,
  ZoomIn,
} from 'lucide-react';

// 模型配置
interface ModelConfig {
  id: string;
  name: string;
  description: string;
  resolutions: Record<string, string[]>;
}

const MODELS: ModelConfig[] = [
  {
    id: 'nano-banana-2',
    name: 'Nano Banana 2',
    description: '快速生图，适合一般需求',
    resolutions: {
      '1K': ['1024x1024', '1280x720', '720x1280'],
      '2K': ['2048x2048', '2560x1440', '1440x2560'],
    },
  },
  {
    id: 'gpt-image-2',
    name: 'GPT Image 2',
    description: '高质量生图，支持更多分辨率',
    resolutions: {
      '1K': [
        '1024x1024', '1280x720', '720x1280',
        '1152x864', '864x1152', '1536x1024',
        '1024x1536', '1120x896', '896x1120',
        '1672x941', '941x1672', '1443x1090',
        '1090x1443', '1408x1120', '1120x1408',
      ],
      '2K': [
        '2048x2048', '2048x1152', '1152x2048',
        '2304x1728', '1728x2304', '2048x1360',
        '1360x2048', '2240x1792', '1792x2240',
        '2912x1248', '1248x2912', '1920x832',
        '832x1920', '1792x896', '896x1792',
      ],
      '4K': [
        '2880x2880', '3840x2160', '2160x3840',
        '3264x2448', '2448x3264', '3504x2336',
        '2336x3504', '3200x2560', '2560x3200',
        '3840x1648', '1648x3840', '3840x1280',
        '1280x3840', '3072x1536', '1536x3072',
      ],
    },
  },
];

type ModelId = string;
type ResolutionTier = string;

// 宽高比标签
function getAspectRatioLabel(resolution: string): string {
  const [w, h] = resolution.split('x').map(Number);
  if (w === h) return '1:1';
  const gcd = (a: number, b: number): number => (b === 0 ? a : gcd(b, a % b));
  const g = gcd(w, h);
  return `${w / g}:${h / g}`;
}

export default function AiImagePage() {
  const brand = getCurrentBrand();
  const [activeModel, setActiveModel] = useState<ModelId>('nano-banana-2');
  const [resolutionTier, setResolutionTier] = useState<ResolutionTier>('1K');
  const [selectedResolution, setSelectedResolution] = useState('1024x1024');
  const [prompt, setPrompt] = useState('');
  const [isGenerating, setIsGenerating] = useState(false);
  const [generatedImages, setGeneratedImages] = useState<
    { url: string; prompt: string; model: string; resolution: string; timestamp: number }[]
  >([]);
  const [error, setError] = useState('');
  const [previewImage, setPreviewImage] = useState<string | null>(null);
  const [showResDropdown, setShowResDropdown] = useState(false);

  const currentModel = MODELS.find((m) => m.id === activeModel) || MODELS[0];
  const availableResolutions: string[] = currentModel.resolutions[resolutionTier] || [];

  const handleModelChange = useCallback((modelId: string) => {
    setActiveModel(modelId);
    const model = MODELS.find((m) => m.id === modelId) || MODELS[0];
    // 重置分辨率为该模型1K的默认值
    const firstRes = model.resolutions['1K']?.[0] || '1024x1024';
    setResolutionTier('1K');
    setSelectedResolution(firstRes);
  }, []);

  const handleGenerate = useCallback(async () => {
    if (!prompt.trim() || isGenerating) return;

    setIsGenerating(true);
    setError('');

    try {
      const response = await fetch('/api/ai-image', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          model: activeModel,
          prompt: prompt.trim(),
          aspectRatio: selectedResolution,
          images: [],
        }),
      });

      const data = await response.json();

      if (!response.ok) {
        throw new Error(data.error || data.message || '生成失败');
      }

      // 解析返回的图片URL
      let imageUrl = '';
      if (data.data?.url) {
        imageUrl = data.data.url;
      } else if (data.data?.image_url) {
        imageUrl = data.data.image_url;
      } else if (data.url) {
        imageUrl = data.url;
      } else if (data.image_url) {
        imageUrl = data.image_url;
      } else if (data.data?.b64_json) {
        imageUrl = `data:image/png;base64,${data.data.b64_json}`;
      } else if (data.b64_json) {
        imageUrl = `data:image/png;base64,${data.b64_json}`;
      } else if (Array.isArray(data.data) && data.data[0]?.url) {
        imageUrl = data.data[0].url;
      } else if (Array.isArray(data.images) && data.images[0]?.url) {
        imageUrl = data.images[0].url;
      } else {
        // 尝试直接从响应中查找URL
        const jsonStr = JSON.stringify(data);
        const urlMatch = jsonStr.match(/https?:\/\/[^\s"']+?\.(png|jpg|jpeg|webp)/i);
        if (urlMatch) {
          imageUrl = urlMatch[0];
        } else {
          console.error('无法解析API响应:', JSON.stringify(data).substring(0, 500));
          throw new Error('无法解析生成结果，请查看控制台');
        }
      }

      setGeneratedImages((prev) => [
        {
          url: imageUrl,
          prompt: prompt.trim(),
          model: activeModel,
          resolution: selectedResolution,
          timestamp: Date.now(),
        },
        ...prev,
      ]);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : '生成失败';
      setError(message);
    } finally {
      setIsGenerating(false);
    }
  }, [prompt, activeModel, selectedResolution, isGenerating]);

  const handleDownload = useCallback(async (url: string, index: number) => {
    try {
      const response = await fetch(url);
      const blob = await response.blob();
      const blobUrl = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = blobUrl;
      link.download = `ai-image-${index + 1}.png`;
      link.click();
      window.URL.revokeObjectURL(blobUrl);
    } catch {
      // 降级：直接打开链接
      window.open(url, '_blank');
    }
  }, []);

  return (
    <div className="flex h-screen bg-slate-50">
      <Sidebar activeItem="ai-image" onItemClick={(id: string) => {
        if (id === 'ai-image') return;
        // 其他导航由主页处理
      }} />
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* 自定义顶部栏 */}
        <div className="h-14 bg-white border-b border-slate-200/60 flex items-center px-6 shrink-0">
          <div className="flex items-center gap-2">
            <Sparkles className="w-4 h-4 text-violet-500" />
            <span className="text-sm font-medium text-slate-700">AI 生图</span>
          </div>
        </div>

        <div className="flex-1 overflow-auto p-6">
          <div className="max-w-6xl mx-auto space-y-6">
            {/* 顶部模型选择 + 设置区 */}
            <div className="bg-white rounded-2xl shadow-sm border border-slate-200/60 p-6">
              <div className="flex items-center justify-between mb-5">
                <div className="flex items-center gap-3">
                  <div className={`w-10 h-10 rounded-xl bg-gradient-to-br from-violet-500 to-purple-600 flex items-center justify-center`}>
                    <Sparkles className="w-5 h-5 text-white" />
                  </div>
                  <div>
                    <h2 className="text-lg font-semibold text-slate-800">AI 智能生图</h2>
                    <p className="text-sm text-slate-500">选择模型和分辨率，输入描述生成图片</p>
                  </div>
                </div>
              </div>

              {/* 模型切换 */}
              <div className="mb-5">
                <label className="text-sm font-medium text-slate-700 mb-2 block">选择模型</label>
                <div className="flex gap-3">
                  {MODELS.map((model) => (
                    <button
                      key={model.id}
                      onClick={() => handleModelChange(model.id)}
                      className={`flex-1 px-4 py-3 rounded-xl border-2 transition-all duration-200 text-left ${
                        activeModel === model.id
                          ? `border-violet-500 bg-violet-50 shadow-sm`
                          : 'border-slate-200 bg-white hover:border-slate-300'
                      }`}
                    >
                      <div className="flex items-center gap-2">
                        <ImageIcon className={`w-4 h-4 ${activeModel === model.id ? 'text-violet-600' : 'text-slate-400'}`} />
                        <span className={`font-medium text-sm ${activeModel === model.id ? 'text-violet-700' : 'text-slate-700'}`}>
                          {model.name}
                        </span>
                      </div>
                      <p className="text-xs text-slate-500 mt-1">{model.description}</p>
                    </button>
                  ))}
                </div>
              </div>

              {/* 分辨率选择 */}
              <div className="mb-5">
                <label className="text-sm font-medium text-slate-700 mb-2 block">分辨率</label>
                <div className="flex gap-2 mb-3">
                  {(['1K', '2K', '4K'] as string[]).map((tier) => {
                    const hasRes = currentModel.resolutions[tier] && currentModel.resolutions[tier].length > 0;
                    return (
                      <button
                        key={tier}
                        onClick={() => {
                          if (hasRes) {
                            setResolutionTier(tier);
                            const firstRes = currentModel.resolutions[tier]?.[0] || selectedResolution;
                            setSelectedResolution(firstRes);
                          }
                        }}
                        disabled={!hasRes}
                        className={`px-4 py-2 rounded-lg text-sm font-medium transition-all duration-200 ${
                          resolutionTier === tier
                            ? 'bg-violet-600 text-white shadow-sm'
                            : hasRes
                              ? 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                              : 'bg-slate-50 text-slate-300 cursor-not-allowed'
                        }`}
                      >
                        {tier}
                      </button>
                    );
                  })}
                </div>

                {/* 具体分辨率选择 */}
                <div className="relative">
                  <button
                    onClick={() => setShowResDropdown(!showResDropdown)}
                    className="w-full flex items-center justify-between px-4 py-2.5 rounded-xl border border-slate-200 bg-white hover:border-slate-300 transition-colors"
                  >
                    <div className="flex items-center gap-2">
                      <Settings2 className="w-4 h-4 text-slate-400" />
                      <span className="text-sm text-slate-700">{selectedResolution}</span>
                      <span className="text-xs text-slate-400">({getAspectRatioLabel(selectedResolution)})</span>
                    </div>
                    <ChevronDown className={`w-4 h-4 text-slate-400 transition-transform ${showResDropdown ? 'rotate-180' : ''}`} />
                  </button>

                  {showResDropdown && (
                    <div className="absolute z-20 w-full mt-1 bg-white border border-slate-200 rounded-xl shadow-lg max-h-64 overflow-y-auto">
                      {availableResolutions.map((res) => (
                        <button
                          key={res}
                          onClick={() => {
                            setSelectedResolution(res);
                            setShowResDropdown(false);
                          }}
                          className={`w-full flex items-center justify-between px-4 py-2.5 text-sm hover:bg-violet-50 transition-colors ${
                            selectedResolution === res ? 'bg-violet-50 text-violet-700 font-medium' : 'text-slate-700'
                          }`}
                        >
                          <span>{res}</span>
                          <span className="text-xs text-slate-400">{getAspectRatioLabel(res)}</span>
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              </div>

              {/* 提示词输入 */}
              <div className="mb-4">
                <label className="text-sm font-medium text-slate-700 mb-2 block">提示词</label>
                <textarea
                  value={prompt}
                  onChange={(e) => setPrompt(e.target.value)}
                  placeholder="描述你想要生成的图片，越详细效果越好..."
                  className="w-full h-28 px-4 py-3 rounded-xl border border-slate-200 bg-white focus:outline-none focus:ring-2 focus:ring-violet-500/20 focus:border-violet-400 resize-none text-sm text-slate-700 placeholder:text-slate-400"
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
                      handleGenerate();
                    }
                  }}
                />
              </div>

              {/* 生成按钮 */}
              <div className="flex items-center gap-3">
                <button
                  onClick={handleGenerate}
                  disabled={isGenerating || !prompt.trim()}
                  className={`px-6 py-2.5 rounded-xl font-medium text-sm text-white transition-all duration-200 flex items-center gap-2 ${
                    isGenerating || !prompt.trim()
                      ? 'bg-slate-300 cursor-not-allowed'
                      : `bg-gradient-to-r from-violet-500 to-purple-600 hover:shadow-lg hover:-translate-y-0.5 active:translate-y-0`
                  }`}
                >
                  {isGenerating ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" />
                      生成中...
                    </>
                  ) : (
                    <>
                      <Sparkles className="w-4 h-4" />
                      生成图片
                    </>
                  )}
                </button>
                {prompt.trim() && (
                  <span className="text-xs text-slate-400">Ctrl+Enter 快捷生成</span>
                )}
              </div>

              {/* 错误提示 */}
              {error && (
                <div className="mt-4 px-4 py-3 rounded-xl bg-red-50 border border-red-200 flex items-center justify-between">
                  <span className="text-sm text-red-600">{error}</span>
                  <button onClick={() => setError('')} className="text-red-400 hover:text-red-600">
                    <X className="w-4 h-4" />
                  </button>
                </div>
              )}
            </div>

            {/* 生成结果 */}
            {generatedImages.length > 0 && (
              <div className="bg-white rounded-2xl shadow-sm border border-slate-200/60 p-6">
                <div className="flex items-center justify-between mb-4">
                  <h3 className="text-base font-semibold text-slate-800">生成结果 ({generatedImages.length})</h3>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                  {generatedImages.map((img, index) => (
                    <div
                      key={img.timestamp}
                      className="group relative rounded-xl overflow-hidden border border-slate-200 bg-slate-50 hover:shadow-lg transition-all duration-300"
                    >
                      <div className="aspect-square relative cursor-pointer" onClick={() => setPreviewImage(img.url)}>
                        <img
                          src={img.url}
                          alt={img.prompt}
                          className="w-full h-full object-cover"
                        />
                        <div className="absolute inset-0 bg-black/0 group-hover:bg-black/20 transition-all duration-200 flex items-center justify-center">
                          <ZoomIn className="w-8 h-8 text-white opacity-0 group-hover:opacity-100 transition-opacity duration-200" />
                        </div>
                      </div>
                      <div className="p-3">
                        <p className="text-xs text-slate-600 line-clamp-2 mb-2">{img.prompt}</p>
                        <div className="flex items-center justify-between">
                          <div className="flex items-center gap-2">
                            <span className="text-xs px-2 py-0.5 rounded-full bg-violet-50 text-violet-600 font-medium">
                              {MODELS.find((m) => m.id === img.model)?.name || img.model}
                            </span>
                            <span className="text-xs text-slate-400">{img.resolution}</span>
                          </div>
                          <button
                            onClick={() => handleDownload(img.url, index)}
                            className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-400 hover:text-violet-600 transition-colors"
                          >
                            <Download className="w-4 h-4" />
                          </button>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* 图片预览弹窗 */}
      {previewImage && (
        <div
          className="fixed inset-0 z-50 bg-black/70 flex items-center justify-center p-8"
          onClick={() => setPreviewImage(null)}
        >
          <div className="relative max-w-[90vw] max-h-[90vh]">
            <img
              src={previewImage}
              alt="Preview"
              className="max-w-full max-h-[85vh] object-contain rounded-lg shadow-2xl"
            />
            <button
              onClick={() => setPreviewImage(null)}
              className="absolute -top-3 -right-3 w-8 h-8 rounded-full bg-white shadow-lg flex items-center justify-center text-slate-600 hover:text-red-500 transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
