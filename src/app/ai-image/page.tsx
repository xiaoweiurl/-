'use client';

import { useState, useCallback, useRef, useEffect } from 'react';
import Sidebar from '@/components/Sidebar';
import { getCurrentBrand } from '@/lib/brand';
import {
  Sparkles,
  Download,
  Loader2,
  Image as ImageIcon,
  ChevronDown,
  X,
  ZoomIn,
  Wand2,
  Ratio,
  Maximize2,
  Trash2,
  Copy,
  Check,
} from 'lucide-react';

// ========== 模型和分辨率配置 ==========

const NANO_ASPECT_RATIOS = [
  'auto', '1:1', '16:9', '9:16', '4:3', '3:4', '3:2', '2:3',
  '5:4', '4:5', '21:9', '1:4', '4:1', '1:8', '8:1',
];

const NANO_IMAGE_SIZES = ['1K', '2K', '4K'];

const NANO_MODELS = [
  { id: 'nano-banana-2', name: 'Nano Banana 2', desc: '推荐', badge: '推荐' },
  { id: 'nano-banana-2-cl', name: 'Nano Banana 2 CL', desc: '2K增强', badge: '' },
  { id: 'nano-banana-2-4k-cl', name: 'Nano Banana 2 4K CL', desc: '4K超清', badge: '4K' },
  { id: 'nano-banana-pro', name: 'Nano Banana Pro', desc: '专业版', badge: '' },
  { id: 'nano-banana-pro-cl', name: 'Nano Banana Pro CL', desc: '专业增强', badge: '' },
  { id: 'nano-banana-pro-vip', name: 'Nano Banana Pro VIP', desc: '旗舰', badge: 'VIP' },
  { id: 'nano-banana-pro-4k-vip', name: 'Nano Banana Pro 4K VIP', desc: '4K旗舰', badge: '4K' },
  { id: 'nano-banana', name: 'Nano Banana', desc: '基础版', badge: '' },
  { id: 'nano-banana-fast', name: 'Nano Banana Fast', desc: '极速', badge: '极速' },
];

const GPT_MODELS = [
  { id: 'gpt-image-2', name: 'GPT Image 2', desc: '标准版', badge: '' },
  { id: 'gpt-image-2-vip', name: 'GPT Image 2 VIP', desc: 'VIP高清', badge: 'VIP' },
];

const GPT_RES_STANDARD: Record<string, string> = {
  '1:1': '1024x1024', '16:9': '1672x941', '9:16': '941x1672',
  '4:3': '1443x1090', '3:4': '1090x1443', '3:2': '1536x1024',
  '2:3': '1024x1536', '5:4': '1408x1120', '4:5': '1120x1408',
  '21:9': '1920x832', '9:21': '832x1920', '2:1': '1792x896', '1:2': '896x1792',
};

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

function ratioToPreview(ratio: string): string {
  if (ratio === 'auto') return '1/1';
  const parts = ratio.split(':').map(Number);
  if (parts.length === 2 && parts[0] > 0 && parts[1] > 0) {
    return `${parts[0]}/${parts[1]}`;
  }
  return '1/1';
}

export default function AiImagePage() {
  const brand = getCurrentBrand();
  const [activeModel, setActiveModel] = useState('nano-banana-2');
  const [modelType, setModelType] = useState<ModelType>('nano');

  const [nanoAspectRatio, setNanoAspectRatio] = useState('1:1');
  const [nanoImageSize, setNanoImageSize] = useState('1K');

  const [gptAspectRatio, setGptAspectRatio] = useState('1:1');
  const [gptImageSize, setGptImageSize] = useState<string>('1K');
  const [gptStandardMode, setGptStandardMode] = useState<'ratio' | 'pixel'>('pixel');

  const [prompt, setPrompt] = useState('');
  const [isGenerating, setIsGenerating] = useState(false);
  const [generatedImages, setGeneratedImages] = useState<
    { url: string; prompt: string; model: string; detail: string; timestamp: number }[]
  >([]);
  const [error, setError] = useState('');
  const [previewImage, setPreviewImage] = useState<string | null>(null);
  const [showModelDropdown, setShowModelDropdown] = useState(false);
  const [copiedIdx, setCopiedIdx] = useState<number | null>(null);

  const dropdownRef = useRef<HTMLDivElement>(null);

  const isGptVip = activeModel === 'gpt-image-2-vip';

  const gptAvailableRatios = isGptVip
    ? Object.keys(GPT_RES_VIP[gptImageSize] || {})
    : gptStandardMode === 'pixel'
      ? Object.keys(GPT_RES_STANDARD)
      : NANO_ASPECT_RATIOS.filter(r => r !== 'auto');

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setShowModelDropdown(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const handleModelTypeChange = useCallback((type: ModelType) => {
    setModelType(type);
    setShowModelDropdown(false);
    if (type === 'nano') {
      setActiveModel('nano-banana-2');
    } else {
      setActiveModel('gpt-image-2');
      setGptImageSize('1K');
      setGptAspectRatio('1:1');
      setGptStandardMode('pixel');
    }
  }, []);

  const handleGptModelChange = useCallback((modelId: string) => {
    setActiveModel(modelId);
    setShowModelDropdown(false);
    if (modelId === 'gpt-image-2-vip') {
      setGptImageSize('1K');
      setGptAspectRatio('1:1');
    } else {
      setGptStandardMode('pixel');
    }
  }, []);

  const handleGenerate = useCallback(async () => {
    if (!prompt.trim() || isGenerating) return;
    setIsGenerating(true);
    setError('');

    try {
      let requestBody: Record<string, unknown>;

      if (modelType === 'nano') {
        requestBody = {
          model: activeModel,
          prompt: prompt.trim(),
          aspectRatio: nanoAspectRatio,
          imageSize: nanoImageSize,
          images: [],
          replyType: 'json',
        };
      } else {
        let aspectRatio: string;
        if (isGptVip) {
          aspectRatio = GPT_RES_VIP[gptImageSize]?.[gptAspectRatio] || '1024x1024';
        } else {
          if (gptStandardMode === 'ratio') {
            aspectRatio = gptAspectRatio;
          } else {
            aspectRatio = GPT_RES_STANDARD[gptAspectRatio] || '1024x1024';
          }
        }
        requestBody = {
          model: activeModel,
          prompt: prompt.trim(),
          aspectRatio,
          images: [],
          replyType: 'json',
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

      let imageUrl = '';
      if (data.data?.url) imageUrl = data.data.url;
      else if (data.data?.image_url) imageUrl = data.data.image_url;
      else if (data.url) imageUrl = data.url;
      else if (data.image_url) imageUrl = data.image_url;
      else if (data.data?.b64_json) imageUrl = `data:image/png;base64,${data.data.b64_json}`;
      else if (data.b64_json) imageUrl = `data:image/png;base64,${data.b64_json}`;
      else if (Array.isArray(data.data) && data.data[0]?.url) imageUrl = data.data[0].url;
      else if (Array.isArray(data.images) && data.images[0]?.url) imageUrl = data.images[0].url;
      else {
        const jsonStr = JSON.stringify(data);
        const urlMatch = jsonStr.match(/https?:\/\/[^\s"']+?\.(png|jpg|jpeg|webp)/i);
        if (urlMatch) imageUrl = urlMatch[0];
        else throw new Error('无法解析生成结果');
      }

      const detail = modelType === 'nano'
        ? `${nanoAspectRatio} / ${nanoImageSize}`
        : isGptVip
          ? `${gptAspectRatio} / ${gptImageSize}`
          : gptStandardMode === 'ratio'
            ? `${gptAspectRatio}（比例格式）`
            : `${gptAspectRatio} / ${GPT_RES_STANDARD[gptAspectRatio] || ''}`;

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
  }, [prompt, activeModel, modelType, nanoAspectRatio, nanoImageSize, gptAspectRatio, gptImageSize, gptStandardMode, isGptVip, isGenerating]);

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

  const handleCopyPrompt = useCallback((text: string, idx: number) => {
    navigator.clipboard.writeText(text);
    setCopiedIdx(idx);
    setTimeout(() => setCopiedIdx(null), 1500);
  }, []);

  const handleDeleteImage = useCallback((timestamp: number) => {
    setGeneratedImages((prev) => prev.filter((img) => img.timestamp !== timestamp));
  }, []);

  const currentModelName = modelType === 'nano'
    ? NANO_MODELS.find((m) => m.id === activeModel)?.name || activeModel
    : GPT_MODELS.find((m) => m.id === activeModel)?.name || activeModel;

  const isBaonasi = brand.name === '宝娜斯';
  const accent = isBaonasi ? 'rose' : 'violet';
  const accentBg = isBaonasi ? 'from-rose-500 to-pink-600' : 'from-violet-500 to-purple-600';
  const accentText = isBaonasi ? 'text-rose-600' : 'text-violet-600';
  const accentBgLight = isBaonasi ? 'bg-rose-50' : 'bg-violet-50';
  const accentBgLighter = isBaonasi ? 'bg-rose-50/50' : 'bg-violet-50/50';
  const accentBorder = isBaonasi ? 'border-rose-400' : 'border-violet-400';
  const accentBorderLight = isBaonasi ? 'border-rose-200' : 'border-violet-200';
  const accentBgBtn = isBaonasi ? 'bg-rose-600' : 'bg-violet-600';
  const accentRing = isBaonasi ? 'focus:ring-rose-500/20 focus:border-rose-400' : 'focus:ring-violet-500/20 focus:border-violet-400';

  // 获取当前选中比例的像素值
  const getCurrentPixelValue = () => {
    if (modelType === 'nano') return null;
    if (isGptVip) return GPT_RES_VIP[gptImageSize]?.[gptAspectRatio]?.replace('x', '×');
    if (gptStandardMode === 'pixel') return GPT_RES_STANDARD[gptAspectRatio]?.replace('x', '×');
    return null;
  };

  return (
    <div className="flex h-screen bg-slate-50">
      <Sidebar activeItem="ai-image" onItemClick={(id: string) => {
        if (id === 'ai-image') return;
      }} />

      <div className="flex-1 flex flex-col overflow-hidden">
        {/* 顶部栏 */}
        <div className="h-14 bg-white border-b border-slate-200/60 flex items-center px-6 shrink-0">
          <div className="flex items-center gap-3">
            <div className={`w-8 h-8 rounded-lg bg-gradient-to-br ${accentBg} flex items-center justify-center`}>
              <Wand2 className="w-4 h-4 text-white" />
            </div>
            <span className="text-base font-semibold text-slate-800">{brand.name} AI 智能生图</span>
            <span className="text-sm text-slate-400 font-normal ml-1">AI Image Generation</span>
          </div>
        </div>

        <div className="flex-1 overflow-auto">
          <div className="max-w-7xl mx-auto p-6">
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">

              {/* ====== 左侧：设置面板 ====== */}
              <div className="lg:col-span-5 space-y-5">

                {/* 模型系列选择 */}
                <div className="bg-white rounded-2xl shadow-sm border border-slate-200/60 p-6">
                  <div className="flex items-center gap-2.5 mb-5">
                    <Sparkles className={`w-5 h-5 ${accentText}`} />
                    <span className="text-base font-semibold text-slate-800">模型选择</span>
                  </div>

                  {/* 两大系列 Tab */}
                  <div className={`flex p-1.5 rounded-xl ${accentBgLighter} mb-5`}>
                    {([
                      { type: 'nano' as ModelType, label: 'Nano Banana', icon: '🍌' },
                      { type: 'gpt' as ModelType, label: 'GPT Image 2', icon: '🎨' },
                    ]).map((item) => (
                      <button
                        key={item.type}
                        onClick={() => handleModelTypeChange(item.type)}
                        className={`flex-1 flex items-center justify-center gap-2 px-4 py-3 rounded-lg text-sm font-medium transition-all duration-200 ${
                          modelType === item.type
                            ? `bg-white shadow-sm ${accentText} border ${accentBorderLight}`
                            : 'text-slate-500 hover:text-slate-700'
                        }`}
                      >
                        <span className="text-base">{item.icon}</span>
                        <span className="text-sm">{item.label}</span>
                      </button>
                    ))}
                  </div>

                  {/* 子模型选择 */}
                  {modelType === 'nano' ? (
                    <div ref={dropdownRef} className="relative">
                      <button
                        onClick={() => setShowModelDropdown(!showModelDropdown)}
                        className={`w-full flex items-center justify-between px-5 py-3.5 rounded-xl border-2 transition-all duration-200 ${
                          showModelDropdown ? accentBorderLight : 'border-slate-200'
                        } bg-white hover:shadow-sm`}
                      >
                        <div className="flex items-center gap-3">
                          <span className="text-xl">🍌</span>
                          <div className="text-left">
                            <div className="text-sm font-medium text-slate-800">{currentModelName}</div>
                            <div className="text-xs text-slate-400 mt-0.5">{NANO_MODELS.find((m) => m.id === activeModel)?.desc}</div>
                          </div>
                        </div>
                        <div className="flex items-center gap-2">
                          {NANO_MODELS.find((m) => m.id === activeModel)?.badge && (
                            <span className={`text-xs px-2 py-0.5 rounded-md font-bold ${
                              NANO_MODELS.find((m) => m.id === activeModel)?.badge === '推荐' ? 'bg-emerald-100 text-emerald-700' :
                              NANO_MODELS.find((m) => m.id === activeModel)?.badge === 'VIP' ? 'bg-amber-100 text-amber-700' :
                              NANO_MODELS.find((m) => m.id === activeModel)?.badge === '4K' ? 'bg-blue-100 text-blue-700' :
                              NANO_MODELS.find((m) => m.id === activeModel)?.badge === '极速' ? 'bg-orange-100 text-orange-700' :
                              'bg-slate-100 text-slate-600'
                            }`}>{NANO_MODELS.find((m) => m.id === activeModel)?.badge}</span>
                          )}
                          <ChevronDown className={`w-4 h-4 text-slate-400 transition-transform duration-200 ${showModelDropdown ? 'rotate-180' : ''}`} />
                        </div>
                      </button>
                      {showModelDropdown && (
                        <div className="absolute z-20 w-full mt-2 bg-white border border-slate-200 rounded-xl shadow-xl overflow-hidden">
                          <div className="max-h-80 overflow-y-auto p-2">
                            {NANO_MODELS.map((m) => (
                              <button
                                key={m.id}
                                onClick={() => {
                                  setActiveModel(m.id);
                                  setShowModelDropdown(false);
                                }}
                                className={`w-full flex items-center gap-3 px-4 py-3.5 rounded-lg text-left transition-all duration-150 ${
                                  activeModel === m.id
                                    ? `${accentBgLight} ${accentText}`
                                    : 'hover:bg-slate-50'
                                }`}
                              >
                                <span className="text-lg">🍌</span>
                                <div className="flex-1 min-w-0">
                                  <div className="text-sm font-medium">{m.name}</div>
                                  <div className="text-xs text-slate-400 mt-0.5">{m.desc}</div>
                                </div>
                                {m.badge && (
                                  <span className={`text-xs px-2 py-0.5 rounded-md font-bold ${
                                    m.badge === '推荐' ? 'bg-emerald-100 text-emerald-700' :
                                    m.badge === 'VIP' ? 'bg-amber-100 text-amber-700' :
                                    m.badge === '4K' ? 'bg-blue-100 text-blue-700' :
                                    m.badge === '极速' ? 'bg-orange-100 text-orange-700' :
                                    'bg-slate-100 text-slate-600'
                                  }`}>{m.badge}</span>
                                )}
                              </button>
                            ))}
                          </div>
                        </div>
                      )}
                    </div>
                  ) : (
                    <div className="space-y-3">
                      {GPT_MODELS.map((m) => (
                        <button
                          key={m.id}
                          onClick={() => handleGptModelChange(m.id)}
                          className={`w-full flex items-center gap-3 px-5 py-4 rounded-xl border-2 transition-all duration-200 text-left ${
                            activeModel === m.id
                              ? `${accentBorder} ${accentBgLight} shadow-sm`
                              : 'border-slate-200 bg-white hover:border-slate-300'
                          }`}
                        >
                          <span className="text-xl">🎨</span>
                          <div className="flex-1">
                            <div className="text-sm font-medium">{m.name}</div>
                            <div className="text-xs text-slate-400 mt-0.5">{m.desc}</div>
                          </div>
                          {m.badge && (
                            <span className="text-xs px-2 py-0.5 rounded-md font-bold bg-amber-100 text-amber-700">{m.badge}</span>
                          )}
                        </button>
                      ))}
                    </div>
                  )}
                </div>

                {/* 参数配置 */}
                <div className="bg-white rounded-2xl shadow-sm border border-slate-200/60 p-6">
                  <div className="flex items-center gap-2.5 mb-5">
                    <Ratio className={`w-5 h-5 ${accentText}`} />
                    <span className="text-base font-semibold text-slate-800">参数配置</span>
                  </div>

                  {modelType === 'nano' ? (
                    <>
                      {/* Nano Banana: 尺寸 */}
                      <div className="mb-5">
                        <label className="text-sm font-medium text-slate-600 mb-3 block">图片尺寸</label>
                        <div className="flex gap-3">
                          {NANO_IMAGE_SIZES.map((size) => (
                            <button
                              key={size}
                              onClick={() => setNanoImageSize(size)}
                              className={`flex-1 py-3 rounded-xl text-sm font-semibold transition-all duration-200 ${
                                nanoImageSize === size
                                  ? `${accentBgBtn} text-white shadow-md`
                                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                              }`}
                            >
                              {size}
                            </button>
                          ))}
                        </div>
                      </div>
                      {/* Nano Banana: 比例 */}
                      <div>
                        <label className="text-sm font-medium text-slate-600 mb-3 block">图片比例</label>
                        <div className="grid grid-cols-5 gap-2">
                          {NANO_ASPECT_RATIOS.map((ratio) => (
                            <button
                              key={ratio}
                              onClick={() => setNanoAspectRatio(ratio)}
                              className={`group flex flex-col items-center gap-1.5 py-2.5 px-1 rounded-lg transition-all duration-200 ${
                                nanoAspectRatio === ratio
                                  ? `${accentBgBtn} text-white shadow-md`
                                  : 'bg-slate-50 text-slate-600 hover:bg-slate-100 border border-slate-200/60'
                              }`}
                            >
                              <div
                                className={`rounded-sm transition-all duration-200 ${
                                  nanoAspectRatio === ratio ? 'bg-white/80' : 'bg-slate-300 group-hover:bg-slate-200'
                                }`}
                                style={ratio !== 'auto' ? { aspectRatio: ratioToPreview(ratio), maxWidth: 18, maxHeight: 18, width: 18, height: 'auto' } : { width: 14, height: 14 }}
                              />
                              <span className="text-xs font-medium leading-none">{ratio}</span>
                            </button>
                          ))}
                        </div>
                      </div>
                    </>
                  ) : isGptVip ? (
                    <>
                      {/* GPT VIP: 档位 */}
                      <div className="mb-5">
                        <label className="text-sm font-medium text-slate-600 mb-3 block">分辨率档位</label>
                        <div className="flex gap-3">
                          {['1K', '2K', '4K'].map((tier) => (
                            <button
                              key={tier}
                              onClick={() => {
                                setGptImageSize(tier);
                                const availableRatios = Object.keys(GPT_RES_VIP[tier] || {});
                                if (!availableRatios.includes(gptAspectRatio)) {
                                  setGptAspectRatio(availableRatios[0] || '1:1');
                                }
                              }}
                              className={`flex-1 py-3 rounded-xl text-sm font-semibold transition-all duration-200 ${
                                gptImageSize === tier
                                  ? `${accentBgBtn} text-white shadow-md`
                                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                              }`}
                            >
                              {tier}
                            </button>
                          ))}
                        </div>
                      </div>
                      {/* GPT VIP: 比例 */}
                      <div>
                        <label className="text-sm font-medium text-slate-600 mb-3 block">图片比例</label>
                        <div className="grid grid-cols-5 gap-2">
                          {Object.keys(GPT_RES_VIP[gptImageSize] || {}).map((ratio) => {
                            const pixelValue = GPT_RES_VIP[gptImageSize]?.[ratio];
                            return (
                              <button
                                key={ratio}
                                onClick={() => setGptAspectRatio(ratio)}
                                className={`group flex flex-col items-center gap-1 py-2.5 px-1 rounded-lg transition-all duration-200 ${
                                  gptAspectRatio === ratio
                                    ? `${accentBgBtn} text-white shadow-md`
                                    : 'bg-slate-50 text-slate-600 hover:bg-slate-100 border border-slate-200/60'
                                }`}
                              >
                                <div
                                  className={`rounded-sm transition-all ${
                                    gptAspectRatio === ratio ? 'bg-white/80' : 'bg-slate-300 group-hover:bg-slate-200'
                                  }`}
                                  style={{ aspectRatio: ratioToPreview(ratio), maxWidth: 18, maxHeight: 18, width: 18, height: 'auto' }}
                                />
                                <span className="text-xs font-medium leading-none">{ratio}</span>
                                <span className={`text-[10px] leading-none ${gptAspectRatio === ratio ? 'text-white/70' : 'text-slate-400'}`}>
                                  {pixelValue?.replace('x', '×')}
                                </span>
                              </button>
                            );
                          })}
                        </div>
                      </div>
                    </>
                  ) : (
                    <>
                      {/* GPT 标准版 */}
                      <div className="mb-5">
                        <label className="text-sm font-medium text-slate-600 mb-3 block">格式</label>
                        <div className={`flex p-1.5 rounded-xl ${accentBgLighter}`}>
                          {[
                            { mode: 'pixel' as const, label: '像素格式' },
                            { mode: 'ratio' as const, label: '比例格式' },
                          ].map((opt) => (
                            <button
                              key={opt.mode}
                              onClick={() => {
                                setGptStandardMode(opt.mode);
                                setGptAspectRatio(opt.mode === 'pixel' ? '1:1' : '16:9');
                              }}
                              className={`flex-1 py-2.5 rounded-lg text-sm font-medium transition-all duration-200 ${
                                gptStandardMode === opt.mode
                                  ? `bg-white shadow-sm ${accentText} border ${accentBorderLight}`
                                  : 'text-slate-500'
                              }`}
                            >
                              {opt.label}
                            </button>
                          ))}
                        </div>
                      </div>
                      <div>
                        <label className="text-sm font-medium text-slate-600 mb-3 block">图片比例</label>
                        <div className="grid grid-cols-5 gap-2">
                          {gptAvailableRatios.map((ratio) => {
                            const pixelValue = gptStandardMode === 'pixel' ? GPT_RES_STANDARD[ratio] : undefined;
                            return (
                              <button
                                key={ratio}
                                onClick={() => setGptAspectRatio(ratio)}
                                className={`group flex flex-col items-center gap-1 py-2.5 px-1 rounded-lg transition-all duration-200 ${
                                  gptAspectRatio === ratio
                                    ? `${accentBgBtn} text-white shadow-md`
                                    : 'bg-slate-50 text-slate-600 hover:bg-slate-100 border border-slate-200/60'
                                }`}
                              >
                                <div
                                  className={`rounded-sm transition-all ${
                                    gptAspectRatio === ratio ? 'bg-white/80' : 'bg-slate-300 group-hover:bg-slate-200'
                                  }`}
                                  style={{ aspectRatio: ratioToPreview(ratio), maxWidth: 18, maxHeight: 18, width: 18, height: 'auto' }}
                                />
                                <span className="text-xs font-medium leading-none">{ratio}</span>
                                {pixelValue && (
                                  <span className={`text-[10px] leading-none ${gptAspectRatio === ratio ? 'text-white/70' : 'text-slate-400'}`}>
                                    {pixelValue.replace('x', '×')}
                                  </span>
                                )}
                              </button>
                            );
                          })}
                        </div>
                      </div>
                    </>
                  )}
                </div>

                {/* 提示词输入 + 生成 */}
                <div className="bg-white rounded-2xl shadow-sm border border-slate-200/60 p-6">
                  <div className="flex items-center justify-between mb-4">
                    <div className="flex items-center gap-2.5">
                      <Wand2 className={`w-5 h-5 ${accentText}`} />
                      <span className="text-base font-semibold text-slate-800">提示词</span>
                    </div>
                    <span className="text-xs text-slate-400 font-medium">Ctrl + Enter 快捷生成</span>
                  </div>
                  <div className="relative">
                    <textarea
                      value={prompt}
                      onChange={(e) => setPrompt(e.target.value)}
                      placeholder="描述你想要生成的图片，越详细效果越好..."
                      className={`w-full h-36 px-4 py-3.5 rounded-xl border border-slate-200 bg-slate-50/50 focus:outline-none focus:ring-2 focus:bg-white ${accentRing} resize-none text-sm text-slate-700 placeholder:text-slate-400 leading-relaxed transition-all duration-200`}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
                          handleGenerate();
                        }
                      }}
                    />
                    <div className="absolute bottom-3.5 left-4 right-4 flex items-center justify-between">
                      <span className="text-xs text-slate-300">{prompt.length} 字</span>
                    </div>
                  </div>

                  <button
                    onClick={handleGenerate}
                    disabled={isGenerating || !prompt.trim()}
                    className={`w-full mt-4 py-3.5 rounded-xl font-semibold text-sm text-white transition-all duration-300 flex items-center justify-center gap-2 ${
                      isGenerating || !prompt.trim()
                        ? 'bg-slate-300 cursor-not-allowed'
                        : `bg-gradient-to-r ${accentBg} hover:shadow-lg hover:-translate-y-0.5 active:translate-y-0`
                    }`}
                  >
                    {isGenerating ? (
                      <>
                        <Loader2 className="w-4 h-4 animate-spin" />
                        AI 正在创作中...
                      </>
                    ) : (
                      <>
                        <Sparkles className="w-4 h-4" />
                        开始生成
                      </>
                    )}
                  </button>

                  {/* 参数摘要 */}
                  <div className="mt-4 flex items-center gap-2 flex-wrap">
                    <span className={`text-xs px-2.5 py-1 rounded-md ${accentBgLight} ${accentText} font-medium`}>
                      {currentModelName}
                    </span>
                    <span className="text-xs px-2.5 py-1 rounded-md bg-slate-100 text-slate-500 font-medium">
                      {modelType === 'nano'
                        ? `${nanoAspectRatio} · ${nanoImageSize}`
                        : isGptVip
                          ? `${gptAspectRatio} · ${gptImageSize}`
                          : gptStandardMode === 'ratio'
                            ? `${gptAspectRatio}（比例格式）`
                            : `${gptAspectRatio} · ${GPT_RES_STANDARD[gptAspectRatio]?.replace('x', '×')}`
                      }
                    </span>
                    {getCurrentPixelValue() && (
                      <span className="text-xs px-2.5 py-1 rounded-md bg-slate-100 text-slate-500 font-medium">
                        {getCurrentPixelValue()}
                      </span>
                    )}
                  </div>
                </div>

                {/* 错误提示 */}
                {error && (
                  <div className="px-5 py-4 rounded-xl bg-red-50 border border-red-200 flex items-center justify-between">
                    <span className="text-sm text-red-600">{error}</span>
                    <button onClick={() => setError('')} className="text-red-400 hover:text-red-600 transition-colors">
                      <X className="w-4 h-4" />
                    </button>
                  </div>
                )}
              </div>

              {/* ====== 右侧：生成结果 ====== */}
              <div className="lg:col-span-7">
                <div className="bg-white rounded-2xl shadow-sm border border-slate-200/60 p-6 min-h-[calc(100vh-7.5rem)]">
                  <div className="flex items-center justify-between mb-6">
                    <div className="flex items-center gap-2.5">
                      <ImageIcon className={`w-5 h-5 ${accentText}`} />
                      <span className="text-base font-semibold text-slate-800">生成结果</span>
                      {generatedImages.length > 0 && (
                        <span className={`text-xs px-2 py-0.5 rounded-md ${accentBgLight} ${accentText} font-bold`}>
                          {generatedImages.length}
                        </span>
                      )}
                    </div>
                    {generatedImages.length > 0 && (
                      <button
                        onClick={() => setGeneratedImages([])}
                        className="text-sm text-slate-400 hover:text-red-500 transition-colors flex items-center gap-1.5"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                        清空全部
                      </button>
                    )}
                  </div>

                  {generatedImages.length === 0 ? (
                    <div className="flex flex-col items-center justify-center py-32 text-slate-300">
                      <div className={`w-24 h-24 rounded-2xl ${accentBgLighter} flex items-center justify-center mb-5`}>
                        <ImageIcon className="w-10 h-10 text-slate-300" />
                      </div>
                      <p className="text-base font-medium text-slate-400 mb-2">还没有生成图片</p>
                      <p className="text-sm text-slate-300">输入提示词，选择模型和参数，开始创作</p>
                    </div>
                  ) : (
                    <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4">
                      {generatedImages.map((img, index) => (
                        <div
                          key={img.timestamp}
                          className="group relative rounded-xl overflow-hidden border border-slate-200/80 bg-white hover:shadow-xl hover:shadow-slate-200/50 transition-all duration-300 hover:-translate-y-0.5"
                        >
                          <div
                            className="aspect-square relative cursor-pointer overflow-hidden"
                            onClick={() => setPreviewImage(img.url)}
                          >
                            <img
                              src={img.url}
                              alt={img.prompt}
                              className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
                            />
                            <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-all duration-300" />
                            <div className="absolute bottom-0 left-0 right-0 p-3 opacity-0 group-hover:opacity-100 transition-all duration-300">
                              <p className="text-xs text-white/90 line-clamp-2">{img.prompt}</p>
                            </div>
                            {/* 操作浮层 */}
                            <div className="absolute top-2.5 right-2.5 flex gap-2 opacity-0 group-hover:opacity-100 transition-all duration-300">
                              <button
                                onClick={(e) => { e.stopPropagation(); setPreviewImage(img.url); }}
                                className="w-8 h-8 rounded-lg bg-black/40 backdrop-blur-sm flex items-center justify-center text-white/90 hover:bg-black/60 transition-colors"
                              >
                                <Maximize2 className="w-4 h-4" />
                              </button>
                              <button
                                onClick={(e) => { e.stopPropagation(); handleDownload(img.url, index); }}
                                className="w-8 h-8 rounded-lg bg-black/40 backdrop-blur-sm flex items-center justify-center text-white/90 hover:bg-black/60 transition-colors"
                              >
                                <Download className="w-4 h-4" />
                              </button>
                            </div>
                          </div>
                          {/* 底部信息 */}
                          <div className="px-3.5 py-3 flex items-center justify-between">
                            <div className="flex items-center gap-1.5 min-w-0">
                              <span className={`text-xs px-2 py-0.5 rounded-md ${accentBgLight} ${accentText} font-medium shrink-0`}>
                                {NANO_MODELS.find((m) => m.id === img.model)?.name
                                  || GPT_MODELS.find((m) => m.id === img.model)?.name
                                  || img.model}
                              </span>
                              <span className="text-xs text-slate-400 truncate">{img.detail}</span>
                            </div>
                            <div className="flex items-center gap-1">
                              <button
                                onClick={() => handleCopyPrompt(img.prompt, index)}
                                className="p-1.5 rounded-md hover:bg-slate-100 text-slate-400 hover:text-slate-600 transition-colors"
                                title="复制提示词"
                              >
                                {copiedIdx === index ? <Check className="w-3.5 h-3.5 text-emerald-500" /> : <Copy className="w-3.5 h-3.5" />}
                              </button>
                              <button
                                onClick={() => handleDeleteImage(img.timestamp)}
                                className="p-1.5 rounded-md hover:bg-red-50 text-slate-400 hover:text-red-500 transition-colors"
                                title="删除"
                              >
                                <Trash2 className="w-3.5 h-3.5" />
                              </button>
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* 图片预览弹窗 */}
      {previewImage && (
        <div
          className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-8"
          onClick={() => setPreviewImage(null)}
        >
          <div className="relative max-w-[90vw] max-h-[90vh] animate-in fade-in zoom-in-95 duration-200">
            <img
              src={previewImage}
              alt="Preview"
              className="max-w-full max-h-[85vh] object-contain rounded-2xl shadow-2xl"
            />
            <button
              onClick={() => setPreviewImage(null)}
              className="absolute -top-3 -right-3 w-9 h-9 rounded-full bg-white shadow-lg flex items-center justify-center text-slate-500 hover:text-red-500 hover:bg-red-50 transition-all duration-200"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
