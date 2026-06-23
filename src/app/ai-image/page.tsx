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

// ========== 模型和分辨率配置 ==========

// Nano Banana 系列的宽高比选项
const NANO_ASPECT_RATIOS = [
  'auto', '1:1', '16:9', '9:16', '4:3', '3:4', '3:2', '2:3',
  '5:4', '4:5', '21:9', '1:4', '4:1', '1:8', '8:1',
];

// Nano Banana 系列的尺寸选项
const NANO_IMAGE_SIZES = ['1K', '2K', '4K'];

// Nano Banana 系列模型
const NANO_MODELS = [
  { id: 'nano-banana-2', name: 'Nano Banana 2', desc: '推荐，均衡质量与速度' },
  { id: 'nano-banana-2-cl', name: 'Nano Banana 2 CL', desc: '2K增强版，更精细' },
  { id: 'nano-banana-2-4k-cl', name: 'Nano Banana 2 4K CL', desc: '4K超清版' },
  { id: 'nano-banana-pro', name: 'Nano Banana Pro', desc: '专业版，更高质量' },
  { id: 'nano-banana-pro-vip', name: 'Nano Banana Pro VIP', desc: '旗舰版，最佳质量' },
  { id: 'nano-banana-pro-4k-vip', name: 'Nano Banana Pro 4K VIP', desc: '4K旗舰版' },
  { id: 'nano-banana', name: 'Nano Banana', desc: '基础版' },
  { id: 'nano-banana-fast', name: 'Nano Banana Fast', desc: '极速版，最快出图' },
];

// GPT Image 2 普通版分辨率
const GPT_RES_STANDARD: Record<string, string> = {
  '1:1': '1024x1024', '16:9': '1672x941', '9:16': '941x1672',
  '4:3': '1443x1090', '3:4': '1090x1443', '3:2': '1536x1024',
  '2:3': '1024x1536', '5:4': '1408x1120', '4:5': '1120x1408',
  '21:9': '1920x832', '9:21': '832x1920', '2:1': '1792x896', '1:2': '896x1792',
};

// GPT Image 2 VIP版分辨率（1K/2K/4K）
const GPT_RES_VIP: Record<string, Record<string, string>> = {
  '1K': {
    '1:1': '1024x1024', '16:9': '1280x720', '9:16': '720x1280',
    '4:3': '1152x864', '3:4': '864x1152', '3:2': '1536x1024',
    '2:3': '1024x1536', '5:4': '1120x896', '4:5': '896x1120',
    '21:9': '1456x624', '9:21': '624x1456', '3:1': '2048x688',
    '1:3': '688x2048', '2:1': '1536x768', '1:2': '768x1536',
  },
  '2K': {
    '1:1': '2048x2048', '16:9': '2048x1152', '9:16': '1152x2048',
    '4:3': '2304x1728', '3:4': '1728x2304', '3:2': '2048x1360',
    '2:3': '1360x2048', '5:4': '2240x1792', '4:5': '1792x2240',
    '21:9': '2912x1248', '9:21': '1248x2912', '3:1': '3840x1280',
    '1:3': '1280x3840', '2:1': '3072x1536', '1:2': '1536x3072',
  },
  '4K': {
    '1:1': '2880x2880', '16:9': '3840x2160', '9:16': '2160x3840',
    '4:3': '3264x2448', '3:4': '2448x3264', '3:2': '3504x2336',
    '2:3': '2336x3504', '5:4': '3200x2560', '4:5': '2560x3200',
    '21:9': '3840x1648', '9:21': '1648x3840', '3:1': '3840x1280',
    '1:3': '1280x3840', '2:1': '3840x1920', '1:2': '1920x3840',
  },
};

type ModelType = 'nano' | 'gpt';

function getModelType(modelId: string): ModelType {
  return modelId.startsWith('gpt-image') ? 'gpt' : 'nano';
}

export default function AiImagePage() {
  const brand = getCurrentBrand();
  const [activeModel, setActiveModel] = useState('nano-banana-2');
  const [modelType, setModelType] = useState<ModelType>('nano');

  // Nano Banana 参数
  const [nanoAspectRatio, setNanoAspectRatio] = useState('1:1');
  const [nanoImageSize, setNanoImageSize] = useState('1K');

  // GPT Image 参数
  const [gptAspectRatio, setGptAspectRatio] = useState('1:1');
  const [gptImageSize, setGptImageSize] = useState('1K');

  const [prompt, setPrompt] = useState('');
  const [isGenerating, setIsGenerating] = useState(false);
  const [generatedImages, setGeneratedImages] = useState<
    { url: string; prompt: string; model: string; detail: string; timestamp: number }[]
  >([]);
  const [error, setError] = useState('');
  const [previewImage, setPreviewImage] = useState<string | null>(null);
  const [showResDropdown, setShowResDropdown] = useState(false);
  const [showModelDropdown, setShowModelDropdown] = useState(false);

  // 获取 GPT 当前可选的分辨率列表
  const gptAvailableRatios = gptImageSize === 'standard'
    ? Object.keys(GPT_RES_STANDARD)
    : Object.keys(GPT_RES_VIP[gptImageSize] || {});

  const handleModelTypeChange = useCallback((type: ModelType) => {
    setModelType(type);
    setShowModelDropdown(false);
    if (type === 'nano') {
      setActiveModel('nano-banana-2');
    } else {
      setActiveModel('gpt-image-2');
      setGptImageSize('1K');
      setGptAspectRatio('1:1');
    }
  }, []);

  const handleGenerate = useCallback(async () => {
    if (!prompt.trim() || isGenerating) return;

    setIsGenerating(true);
    setError('');

    try {
      // 构建请求参数
      let requestBody: Record<string, unknown>;

      if (modelType === 'nano') {
        requestBody = {
          model: activeModel,
          prompt: prompt.trim(),
          aspectRatio: nanoAspectRatio,
          imageSize: nanoImageSize,
          images: [],
        };
      } else {
        // GPT Image: aspectRatio 直接用像素值
        const aspectRatio = gptImageSize === 'standard'
          ? GPT_RES_STANDARD[gptAspectRatio] || '1024x1024'
          : GPT_RES_VIP[gptImageSize]?.[gptAspectRatio] || '1024x1024';
        requestBody = {
          model: activeModel,
          prompt: prompt.trim(),
          aspectRatio,
          images: [],
        };
      }

      const response = await fetch('/api/ai-image', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(requestBody),
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
        const jsonStr = JSON.stringify(data);
        const urlMatch = jsonStr.match(/https?:\/\/[^\s"']+?\.(png|jpg|jpeg|webp)/i);
        if (urlMatch) {
          imageUrl = urlMatch[0];
        } else {
          console.error('无法解析API响应:', JSON.stringify(data).substring(0, 500));
          throw new Error('无法解析生成结果，请查看控制台');
        }
      }

      const detail = modelType === 'nano'
        ? `${nanoAspectRatio} / ${nanoImageSize}`
        : `${gptAspectRatio} / ${gptImageSize}`;

      setGeneratedImages((prev) => [
        { url: imageUrl, prompt: prompt.trim(), model: activeModel, detail, timestamp: Date.now() },
        ...prev,
      ]);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : '生成失败';
      setError(message);
    } finally {
      setIsGenerating(false);
    }
  }, [prompt, activeModel, modelType, nanoAspectRatio, nanoImageSize, gptAspectRatio, gptImageSize, isGenerating]);

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
      window.open(url, '_blank');
    }
  }, []);

  // 获取当前模型的显示名
  const currentModelName = modelType === 'nano'
    ? NANO_MODELS.find((m) => m.id === activeModel)?.name || activeModel
    : 'GPT Image 2';

  return (
    <div className="flex h-screen bg-slate-50">
      <Sidebar activeItem="ai-image" onItemClick={(id: string) => {
        if (id === 'ai-image') return;
      }} />
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* 顶部栏 */}
        <div className="h-14 bg-white border-b border-slate-200/60 flex items-center px-6 shrink-0">
          <div className="flex items-center gap-2">
            <Sparkles className="w-4 h-4 text-violet-500" />
            <span className="text-sm font-medium text-slate-700">AI 智能生图</span>
          </div>
        </div>

        <div className="flex-1 overflow-auto p-6">
          <div className="max-w-6xl mx-auto space-y-6">
            {/* 设置区域 */}
            <div className="bg-white rounded-2xl shadow-sm border border-slate-200/60 p-6">
              {/* 标题 */}
              <div className="flex items-center gap-3 mb-5">
                <div className={`w-10 h-10 rounded-xl bg-gradient-to-br from-violet-500 to-purple-600 flex items-center justify-center`}>
                  <Sparkles className="w-5 h-5 text-white" />
                </div>
                <div>
                  <h2 className="text-lg font-semibold text-slate-800">AI 智能生图</h2>
                  <p className="text-sm text-slate-500">选择模型和参数，输入描述生成图片</p>
                </div>
              </div>

              {/* 模型大类切换 */}
              <div className="mb-5">
                <label className="text-sm font-medium text-slate-700 mb-2 block">模型系列</label>
                <div className="flex gap-3">
                  {(['nano', 'gpt'] as ModelType[]).map((type) => (
                    <button
                      key={type}
                      onClick={() => handleModelTypeChange(type)}
                      className={`flex-1 px-4 py-3 rounded-xl border-2 transition-all duration-200 text-left ${
                        modelType === type
                          ? 'border-violet-500 bg-violet-50 shadow-sm'
                          : 'border-slate-200 bg-white hover:border-slate-300'
                      }`}
                    >
                      <div className="flex items-center gap-2">
                        <ImageIcon className={`w-4 h-4 ${modelType === type ? 'text-violet-600' : 'text-slate-400'}`} />
                        <span className={`font-medium text-sm ${modelType === type ? 'text-violet-700' : 'text-slate-700'}`}>
                          {type === 'nano' ? 'Nano Banana' : 'GPT Image 2'}
                        </span>
                      </div>
                      <p className="text-xs text-slate-500 mt-1">
                        {type === 'nano' ? '多种模型可选，支持比例+尺寸' : '高质量生图，支持多分辨率'}
                      </p>
                    </button>
                  ))}
                </div>
              </div>

              {/* 子模型选择（Nano Banana 系列） */}
              {modelType === 'nano' && (
                <div className="mb-5">
                  <label className="text-sm font-medium text-slate-700 mb-2 block">选择模型</label>
                  <div className="relative">
                    <button
                      onClick={() => setShowModelDropdown(!showModelDropdown)}
                      className="w-full flex items-center justify-between px-4 py-2.5 rounded-xl border border-slate-200 bg-white hover:border-slate-300 transition-colors"
                    >
                      <div className="flex items-center gap-2">
                        <Settings2 className="w-4 h-4 text-slate-400" />
                        <span className="text-sm text-slate-700 font-medium">{currentModelName}</span>
                        <span className="text-xs text-slate-400">
                          {NANO_MODELS.find((m) => m.id === activeModel)?.desc}
                        </span>
                      </div>
                      <ChevronDown className={`w-4 h-4 text-slate-400 transition-transform ${showModelDropdown ? 'rotate-180' : ''}`} />
                    </button>
                    {showModelDropdown && (
                      <div className="absolute z-20 w-full mt-1 bg-white border border-slate-200 rounded-xl shadow-lg max-h-64 overflow-y-auto">
                        {NANO_MODELS.map((m) => (
                          <button
                            key={m.id}
                            onClick={() => {
                              setActiveModel(m.id);
                              setShowModelDropdown(false);
                            }}
                            className={`w-full flex items-center justify-between px-4 py-2.5 text-sm hover:bg-violet-50 transition-colors ${
                              activeModel === m.id ? 'bg-violet-50 text-violet-700 font-medium' : 'text-slate-700'
                            }`}
                          >
                            <div>
                              <span>{m.name}</span>
                              <span className="text-xs text-slate-400 ml-2">{m.desc}</span>
                            </div>
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              )}

              {/* 参数配置区：根据模型类型不同 */}
              {modelType === 'nano' ? (
                <>
                  {/* Nano Banana: 比例 + 尺寸 */}
                  <div className="mb-5">
                    <label className="text-sm font-medium text-slate-700 mb-2 block">图片比例</label>
                    <div className="grid grid-cols-5 sm:grid-cols-8 gap-2">
                      {NANO_ASPECT_RATIOS.map((ratio) => (
                        <button
                          key={ratio}
                          onClick={() => setNanoAspectRatio(ratio)}
                          className={`px-3 py-2 rounded-lg text-xs font-medium transition-all duration-200 ${
                            nanoAspectRatio === ratio
                              ? 'bg-violet-600 text-white shadow-sm'
                              : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                          }`}
                        >
                          {ratio}
                        </button>
                      ))}
                    </div>
                  </div>
                  <div className="mb-5">
                    <label className="text-sm font-medium text-slate-700 mb-2 block">图片尺寸</label>
                    <div className="flex gap-2">
                      {NANO_IMAGE_SIZES.map((size) => (
                        <button
                          key={size}
                          onClick={() => setNanoImageSize(size)}
                          className={`px-4 py-2 rounded-lg text-sm font-medium transition-all duration-200 ${
                            nanoImageSize === size
                              ? 'bg-violet-600 text-white shadow-sm'
                              : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                          }`}
                        >
                          {size}
                        </button>
                      ))}
                    </div>
                  </div>
                </>
              ) : (
                <>
                  {/* GPT Image 2: 尺寸档位 + 比例 */}
                  <div className="mb-5">
                    <label className="text-sm font-medium text-slate-700 mb-2 block">分辨率档位</label>
                    <div className="flex gap-2">
                      {['standard', '1K', '2K', '4K'].map((tier) => (
                        <button
                          key={tier}
                          onClick={() => {
                            setGptImageSize(tier);
                            const availableRatios = tier === 'standard'
                              ? Object.keys(GPT_RES_STANDARD)
                              : Object.keys(GPT_RES_VIP[tier] || {});
                            if (!availableRatios.includes(gptAspectRatio)) {
                              setGptAspectRatio(availableRatios[0] || '1:1');
                            }
                          }}
                          className={`px-4 py-2 rounded-lg text-sm font-medium transition-all duration-200 ${
                            gptImageSize === tier
                              ? 'bg-violet-600 text-white shadow-sm'
                              : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                          }`}
                        >
                          {tier === 'standard' ? '标准' : tier}
                        </button>
                      ))}
                    </div>
                  </div>
                  <div className="mb-5">
                    <label className="text-sm font-medium text-slate-700 mb-2 block">图片比例</label>
                    <div className="grid grid-cols-5 sm:grid-cols-8 gap-2">
                      {gptAvailableRatios.map((ratio) => {
                        const pixelValue = gptImageSize === 'standard'
                          ? GPT_RES_STANDARD[ratio]
                          : GPT_RES_VIP[gptImageSize]?.[ratio];
                        return (
                          <button
                            key={ratio}
                            onClick={() => setGptAspectRatio(ratio)}
                            className={`px-3 py-2 rounded-lg text-xs font-medium transition-all duration-200 ${
                              gptAspectRatio === ratio
                                ? 'bg-violet-600 text-white shadow-sm'
                                : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                            }`}
                            title={pixelValue}
                          >
                            <span>{ratio}</span>
                            <span className="block text-[10px] opacity-60">{pixelValue}</span>
                          </button>
                        );
                      })}
                    </div>
                  </div>
                </>
              )}

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
                <span className="text-xs text-slate-400">
                  {currentModelName} · {modelType === 'nano' ? `${nanoAspectRatio} / ${nanoImageSize}` : `${gptAspectRatio} / ${gptImageSize === 'standard' ? '标准' : gptImageSize}`}
                </span>
                {prompt.trim() && (
                  <span className="text-xs text-slate-400 ml-auto">Ctrl+Enter 快捷生成</span>
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
                              {NANO_MODELS.find((m) => m.id === img.model)?.name || img.model === 'gpt-image-2' ? 'GPT Image 2' : img.model}
                            </span>
                            <span className="text-xs text-slate-400">{img.detail}</span>
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
