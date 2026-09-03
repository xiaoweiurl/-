'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import { useRouter, useParams } from 'next/navigation';
import {
  ArrowLeft, Loader2, Trash2, Upload, X, ZoomIn,
  User, PenTool, Hash, CreditCard, Building2, FileText, Save,
  Image as ImageIcon, Sparkles, Swords, Cog, Users, MapPin,
} from 'lucide-react';

interface GoodsDetail {
  id: number;
  folder_name: string;
  initiator: string | null;
  sampler: string | null;
  product_name: string | null;
  goods_no: string | null;
  customer: string | null;
  order_no: string | null;
  selling_points: string | null;
  competitors: string | null;
  features: string | null;
  target_audience: string | null;
  usage_scenarios: string | null;
  main_image_url: string | null;
  side_image_url: string | null;
  detail_image_url: string | null;
  product_image_url: string | null;
}

/** 第二层图片槽位（均可空） */
const IMAGE_SLOTS = [
  { slot: 'main', label: '主图' },
  { slot: 'side', label: '侧面图' },
  { slot: 'detail', label: '细节' },
  { slot: 'product', label: '产品图' },
] as const;

/** 第一层信息字段（均可空） */
const INFO_FIELDS = [
  { key: 'initiator', label: '发起人', icon: User },
  { key: 'sampler', label: '打样员', icon: PenTool },
  { key: 'product_name', label: '品名', icon: Hash },
  { key: 'goods_no', label: '货号', icon: CreditCard },
  { key: 'customer', label: '客户', icon: Building2 },
  { key: 'order_no', label: '订单号', icon: FileText },
] as const;

/** 备注字段（均可空） */
const REMARK_FIELDS = [
  { key: 'selling_points', label: '卖点', icon: Sparkles },
  { key: 'competitors', label: '竞品', icon: Swords },
  { key: 'features', label: '功能', icon: Cog },
  { key: 'target_audience', label: '对应人群', icon: Users },
  { key: 'usage_scenarios', label: '使用场景', icon: MapPin },
] as const;

type SlotKey = (typeof IMAGE_SLOTS)[number]['slot'];

export default function GoodsDetailPage() {
  const router = useRouter();
  const params = useParams();
  const id = params?.id as string;

  const [detail, setDetail] = useState<GoodsDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [infoForm, setInfoForm] = useState<Record<string, string>>({});
  const [remarkForm, setRemarkForm] = useState<Record<string, string>>({});
  const [savingInfo, setSavingInfo] = useState(false);
  const [savingRemark, setSavingRemark] = useState(false);
  const [uploadingSlot, setUploadingSlot] = useState<SlotKey | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const fileInputs = useRef<Record<string, HTMLInputElement | null>>({});

  const fetchDetail = useCallback(async () => {
    try {
      const res = await fetch(`/api/goods-library/${id}`);
      const data = await res.json();
      if (data.success) {
        const d = data.data as GoodsDetail;
        setDetail(d);
        setInfoForm({
          initiator: d.initiator || '', sampler: d.sampler || '',
          product_name: d.product_name || '', goods_no: d.goods_no || '',
          customer: d.customer || '', order_no: d.order_no || '',
        });
        setRemarkForm({
          selling_points: d.selling_points || '', competitors: d.competitors || '',
          features: d.features || '', target_audience: d.target_audience || '',
          usage_scenarios: d.usage_scenarios || '',
        });
      } else {
        alert(data.message || '商品不存在');
        router.push('/goods-library');
      }
    } catch {
      alert('加载失败');
    } finally {
      setLoading(false);
    }
  }, [id, router]);

  useEffect(() => {
    if (id) fetchDetail();
  }, [id, fetchDetail]);

  const saveFields = async (fields: Record<string, string>, setSaving: (v: boolean) => void) => {
    setSaving(true);
    try {
      const res = await fetch(`/api/goods-library/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(fields),
      });
      const data = await res.json();
      if (data.success) {
        await fetchDetail();
      } else {
        alert(data.message || '保存失败');
      }
    } catch {
      alert('保存失败，请重试');
    } finally {
      setSaving(false);
    }
  };

  const handleUpload = async (slot: SlotKey, file: File) => {
    setUploadingSlot(slot);
    try {
      const formData = new FormData();
      formData.append('slot', slot);
      formData.append('file', file);
      const res = await fetch(`/api/goods-library/${id}/images`, {
        method: 'POST',
        body: formData,
      });
      const data = await res.json();
      if (data.success) {
        await fetchDetail();
      } else {
        alert(data.message || '上传失败');
      }
    } catch {
      alert('上传失败，请重试');
    } finally {
      setUploadingSlot(null);
      const input = fileInputs.current[slot];
      if (input) input.value = '';
    }
  };

  const handleRemoveImage = async (slot: SlotKey) => {
    if (!confirm('确定删除这张图片吗？')) return;
    setUploadingSlot(slot);
    try {
      const res = await fetch(`/api/goods-library/${id}/images?slot=${slot}`, { method: 'DELETE' });
      const data = await res.json();
      if (data.success) {
        await fetchDetail();
      } else {
        alert(data.message || '删除失败');
      }
    } catch {
      alert('删除失败，请重试');
    } finally {
      setUploadingSlot(null);
    }
  };

  const handleDelete = async () => {
    if (!confirm('确定删除该商品文件夹吗？其中的图片将一并删除。')) return;
    try {
      const res = await fetch(`/api/goods-library/${id}`, { method: 'DELETE' });
      const data = await res.json();
      if (data.success) {
        router.push('/goods-library');
      } else {
        alert(data.message || '删除失败');
      }
    } catch {
      alert('删除失败，请重试');
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-[#0f172a] flex items-center justify-center text-slate-500">
        <Loader2 className="w-6 h-6 animate-spin mr-2" /> 加载中...
      </div>
    );
  }

  if (!detail) return null;

  const imageUrlOf = (slot: SlotKey) => detail[`${slot}_image_url` as keyof GoodsDetail] as string | null;

  return (
    <div className="min-h-screen bg-[#0f172a] text-slate-100">
      {/* 顶栏 */}
      <div className="sticky top-0 z-20 backdrop-blur-xl bg-[#0f172a]/80 border-b border-blue-500/15">
        <div className="max-w-6xl mx-auto px-6 h-16 flex items-center gap-4">
          <button
            onClick={() => router.push('/goods-library')}
            className="p-2 rounded-lg hover:bg-slate-700/50 text-slate-400 hover:text-slate-200 transition-colors"
            title="返回商品库"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>
          <div className="min-w-0">
            <h1 className="text-lg font-semibold truncate">{detail.folder_name}</h1>
            <p className="text-[11px] text-slate-500">商品文件夹 · OSS 目录 goods-library/{detail.folder_name}</p>
          </div>
          <div className="flex-1" />
          <button
            onClick={handleDelete}
            className="flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-sm text-red-400 border border-red-500/30 hover:bg-red-500/10 transition-colors"
          >
            <Trash2 className="w-4 h-4" /> 删除商品
          </button>
        </div>
      </div>

      <div className="max-w-6xl mx-auto px-6 py-8 space-y-6">
        {/* ===== 第二层：商品图片（主图/侧面图/细节/产品图，均可空） ===== */}
        <div className="bg-slate-800/50 rounded-xl border border-slate-700/50 p-5">
          <h2 className="text-sm font-semibold text-slate-200 flex items-center gap-2 mb-4">
            <ImageIcon className="w-4 h-4 text-blue-400" />
            商品图片
            <span className="text-[10px] font-normal text-slate-500">上传至阿里云 OSS，按槽位命名</span>
          </h2>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            {IMAGE_SLOTS.map(({ slot, label }) => {
              const url = imageUrlOf(slot);
              const uploading = uploadingSlot === slot;
              return (
                <div key={slot} className="group relative">
                  <div className="aspect-square rounded-xl border-2 border-dashed border-slate-700/60 bg-slate-900/50 overflow-hidden hover:border-blue-500/40 transition-colors">
                    {url ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img src={url} alt={label} className="w-full h-full object-cover" />
                    ) : (
                      <button
                        onClick={() => fileInputs.current[slot]?.click()}
                        disabled={uploading}
                        className="w-full h-full flex flex-col items-center justify-center gap-2 text-slate-500 hover:text-blue-400 transition-colors"
                      >
                        {uploading
                          ? <Loader2 className="w-6 h-6 animate-spin" />
                          : <Upload className="w-6 h-6" />}
                        <span className="text-xs">{uploading ? '上传中...' : `上传${label}`}</span>
                      </button>
                    )}
                  </div>

                  {/* 槽位标签 */}
                  <div className="absolute bottom-2 left-2 px-2 py-0.5 rounded-md bg-slate-900/85 text-[11px] text-slate-300 border border-slate-700/50">
                    {label}
                  </div>

                  {/* 已有图片的操作按钮 */}
                  {url && !uploading && (
                    <div className="absolute top-2 right-2 flex gap-1.5 opacity-0 group-hover:opacity-100 transition-opacity">
                      <button
                        onClick={() => setPreviewUrl(url)}
                        className="p-1.5 rounded-lg bg-slate-900/85 text-slate-300 hover:text-blue-400 transition-colors"
                        title="预览"
                      >
                        <ZoomIn className="w-3.5 h-3.5" />
                      </button>
                      <button
                        onClick={() => fileInputs.current[slot]?.click()}
                        className="p-1.5 rounded-lg bg-slate-900/85 text-slate-300 hover:text-cyan-400 transition-colors"
                        title="替换"
                      >
                        <Upload className="w-3.5 h-3.5" />
                      </button>
                      <button
                        onClick={() => handleRemoveImage(slot)}
                        className="p-1.5 rounded-lg bg-slate-900/85 text-slate-300 hover:text-red-400 transition-colors"
                        title="删除"
                      >
                        <X className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  )}
                  {uploading && url && (
                    <div className="absolute inset-0 flex items-center justify-center bg-slate-900/60 rounded-xl">
                      <Loader2 className="w-6 h-6 animate-spin text-blue-400" />
                    </div>
                  )}

                  <input
                    ref={el => { fileInputs.current[slot] = el; }}
                    type="file"
                    accept="image/*"
                    className="hidden"
                    onChange={e => {
                      const f = e.target.files?.[0];
                      if (f) handleUpload(slot, f);
                    }}
                  />
                </div>
              );
            })}
          </div>
        </div>

        {/* ===== 第一层：商品信息（均可空） ===== */}
        <div className="bg-slate-800/50 rounded-xl border border-slate-700/50 p-5">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-sm font-semibold text-slate-200 flex items-center gap-2">
              <FileText className="w-4 h-4 text-cyan-400" />
              商品信息
              <span className="text-[10px] font-normal text-slate-500">货号或品名变更后文件夹自动重命名</span>
            </h2>
            <button
              onClick={() => saveFields(infoForm, setSavingInfo)}
              disabled={savingInfo}
              className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg bg-gradient-to-r from-blue-600 to-cyan-500 text-white text-xs font-medium disabled:opacity-50 transition-all"
            >
              {savingInfo ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Save className="w-3.5 h-3.5" />}
              保存信息
            </button>
          </div>
          <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
            {INFO_FIELDS.map(({ key, label, icon: Icon }) => (
              <div key={key}>
                <label className="flex items-center gap-1.5 text-xs text-slate-400 mb-1.5">
                  <Icon className="w-3.5 h-3.5" />
                  {label}
                </label>
                <input
                  value={infoForm[key] || ''}
                  onChange={e => setInfoForm(prev => ({ ...prev, [key]: e.target.value }))}
                  placeholder={`请输入${label}（可空）`}
                  className="w-full px-3 py-2 rounded-lg bg-slate-900/60 border border-slate-700/50 text-sm text-slate-200 placeholder:text-slate-600 focus:outline-none focus:border-blue-500/50 focus:ring-1 focus:ring-blue-500/30 transition-all"
                />
              </div>
            ))}
          </div>
        </div>

        {/* ===== 备注（卖点/竞品/功能/对应人群/使用场景，均可空） ===== */}
        <div className="bg-slate-800/50 rounded-xl border border-slate-700/50 p-5">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-sm font-semibold text-slate-200 flex items-center gap-2">
              <Sparkles className="w-4 h-4 text-amber-400" />
              备注
            </h2>
            <button
              onClick={() => saveFields(remarkForm, setSavingRemark)}
              disabled={savingRemark}
              className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg bg-gradient-to-r from-blue-600 to-cyan-500 text-white text-xs font-medium disabled:opacity-50 transition-all"
            >
              {savingRemark ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Save className="w-3.5 h-3.5" />}
              保存备注
            </button>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {REMARK_FIELDS.map(({ key, label, icon: Icon }) => (
              <div key={key} className={key === 'usage_scenarios' ? 'md:col-span-2' : ''}>
                <label className="flex items-center gap-1.5 text-xs text-slate-400 mb-1.5">
                  <Icon className="w-3.5 h-3.5" />
                  {label}
                </label>
                <textarea
                  value={remarkForm[key] || ''}
                  onChange={e => setRemarkForm(prev => ({ ...prev, [key]: e.target.value }))}
                  placeholder={`请输入${label}（可空）`}
                  rows={2}
                  className="w-full px-3 py-2 rounded-lg bg-slate-900/60 border border-slate-700/50 text-sm text-slate-200 placeholder:text-slate-600 focus:outline-none focus:border-blue-500/50 focus:ring-1 focus:ring-blue-500/30 transition-all resize-none"
                />
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* 图片预览弹层 */}
      {previewUrl && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-8"
          onClick={() => setPreviewUrl(null)}
        >
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={previewUrl} alt="预览" className="max-w-full max-h-full object-contain rounded-lg shadow-2xl" />
          <button
            className="absolute top-6 right-6 p-2 rounded-lg bg-slate-900/80 text-slate-300 hover:text-white transition-colors"
            onClick={() => setPreviewUrl(null)}
          >
            <X className="w-5 h-5" />
          </button>
        </div>
      )}
    </div>
  );
}
