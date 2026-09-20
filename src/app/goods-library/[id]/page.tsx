'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { toast } from 'sonner';
import { Toaster } from '@/components/ui/sonner';
import {
  ArrowLeft, Loader2, Trash2, Upload, X, ZoomIn,
  User, PenTool, Hash, CreditCard, Building2, FileText, Save,
  Image as ImageIcon, Sparkles,
} from 'lucide-react';
import { loginHref } from '@/lib/auth-redirect';

interface GoodsDetail {
  id: number;
  folder_name: string;
  initiator: string | null;
  sampler: string | null;
  product_name: string | null;
  goods_no: string | null;
  customer: string | null;
  order_no: string | null;
  remark: string | null;
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

/** 第一层信息字段（发起人必填，其余可空） */
const INFO_FIELDS = [
  { key: 'initiator', label: '发起人', icon: User, required: true },
  { key: 'sampler', label: '打样员', icon: PenTool, required: false },
  { key: 'product_name', label: '品名', icon: Hash, required: false },
  { key: 'goods_no', label: '货号', icon: CreditCard, required: false },
  { key: 'customer', label: '客户', icon: Building2, required: false },
  { key: 'order_no', label: '订单号', icon: FileText, required: false },
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
      if (res.status === 401) {
        window.location.href = loginHref(`/goods-library/${id}`);
        return;
      }
      const data = await res.json();
      if (data.success) {
        const d = data.data as GoodsDetail;
        setDetail(d);
        setInfoForm({
          initiator: d.initiator || '', sampler: d.sampler || '',
          product_name: d.product_name || '', goods_no: d.goods_no || '',
          customer: d.customer || '', order_no: d.order_no || '',
        });
        setRemarkForm({ remark: d.remark || '' });
      } else {
        toast.error(data.message || '商品不存在');
      }
    } catch {
      toast.error('加载失败');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    if (id) fetchDetail();
  }, [id, fetchDetail]);

  const saveFields = async (fields: Record<string, string>, setSaving: (v: boolean) => void) => {
    // 发起人为必填字段，保存信息时不允许清空
    if ('initiator' in fields && !(fields.initiator || '').trim()) {
      toast.error('发起人不能为空');
      return;
    }
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
        toast.success('保存成功');
      } else {
        toast.error(data.message || '保存失败');
      }
    } catch {
      toast.error('保存失败，请重试');
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
        toast.success('上传成功');
      } else {
        toast.error(data.message || '上传失败');
      }
    } catch {
      toast.error('上传失败，请重试');
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
        toast.success('图片已删除');
      } else {
        toast.error(data.message || '删除失败');
      }
    } catch {
      toast.error('删除失败，请重试');
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
        toast.success('商品文件夹已删除');
        router.push('/goods-library');
      } else {
        toast.error(data.message || '删除失败');
      }
    } catch {
      toast.error('删除失败，请重试');
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-white flex items-center justify-center text-[#8e8e93]">
        <Loader2 className="w-6 h-6 animate-spin mr-2" /> 加载中...
      </div>
    );
  }

  if (!detail) {
    return (
      <div className="min-h-screen bg-white flex flex-col items-center justify-center gap-4 text-[#8e8e93]">
        <ImageIcon className="w-10 h-10 text-[#8e8e93]" />
        <p className="text-sm">商品文件夹不存在或已被删除</p>
        <button
          onClick={() => router.push('/goods-library')}
          className="min-h-11 px-5 py-2.5 rounded-lg bg-[rgba(0,122,255,0.2)] border border-[rgba(0,122,255,0.3)] text-[#007aff] text-sm hover:bg-[rgba(0,122,255,0.3)] transition-colors"
        >
          返回商品库
        </button>
      </div>
    );
  }

  const imageUrlOf = (slot: SlotKey) => detail[`${slot}_image_url` as keyof GoodsDetail] as string | null;

  return (
    <div className="min-h-[100dvh] bg-white text-[#1c1c1e] overflow-x-hidden pb-[max(1.25rem,env(safe-area-inset-bottom))] touch-manipulation">
      {/* 顶栏：手机端压缩文案、加大点击区，避免横向溢出 */}
      <div className="sticky top-0 z-20 backdrop-blur-xl bg-white/80 border-b border-[rgba(0,122,255,0.15)] pt-[env(safe-area-inset-top)]">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 min-h-14 sm:h-16 py-2 flex items-center gap-2 sm:gap-4">
          <button
            onClick={() => router.push('/goods-library')}
            className="shrink-0 min-w-11 min-h-11 flex items-center justify-center rounded-lg hover:bg-[rgba(118,118,128,0.12)] text-[#8e8e93] hover:text-[#1c1c1e] transition-colors"
            title="返回商品库"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>
          <div className="min-w-0 flex-1">
            <h1 className="text-base sm:text-lg font-semibold truncate">{detail.folder_name}</h1>
            <p className="hidden sm:block text-[11px] text-[#8e8e93] truncate">
              商品文件夹 · OSS 目录 goods-library/{detail.folder_name}
            </p>
          </div>
          <button
            onClick={handleDelete}
            className="shrink-0 min-h-11 flex items-center gap-1.5 px-2.5 sm:px-3.5 rounded-lg text-sm text-[#ff3b30] border border-[rgba(255,59,48,0.3)] hover:bg-[rgba(255,59,48,0.1)] transition-colors"
          >
            <Trash2 className="w-4 h-4" />
            <span className="hidden sm:inline">删除商品</span>
          </button>
        </div>
      </div>

      <div className="max-w-6xl mx-auto px-4 sm:px-6 py-4 sm:py-8 space-y-4 sm:space-y-6">
        {/* ===== 第二层：商品图片（主图/侧面图/细节/产品图，均可空） ===== */}
        <div className="bg-white rounded-xl border border-[rgba(229,229,234,0.5)] p-4 sm:p-5">
          <h2 className="text-sm font-semibold text-[#1c1c1e] flex flex-wrap items-center gap-2 mb-4">
            <ImageIcon className="w-4 h-4 text-[#007aff]" />
            商品图片
            <span className="text-[10px] font-normal text-[#8e8e93]">上传至阿里云 OSS，按槽位命名</span>
          </h2>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3 sm:gap-4">
            {IMAGE_SLOTS.map(({ slot, label }) => {
              const url = imageUrlOf(slot);
              const uploading = uploadingSlot === slot;
              return (
                <div key={slot} className="group relative">
                  <div className="aspect-square rounded-xl border-2 border-dashed border-[rgba(229,229,234,0.6)] bg-[rgba(242,242,247,0.5)] overflow-hidden hover:border-[rgba(0,122,255,0.4)] transition-colors">
                    {url ? (
                      <img
                        src={url}
                        alt={label}
                        className="w-full h-full object-cover"
                        onClick={() => setPreviewUrl(url)}
                      />
                    ) : (
                      <button
                        onClick={() => fileInputs.current[slot]?.click()}
                        disabled={uploading}
                        className="w-full h-full flex flex-col items-center justify-center gap-2 text-[#8e8e93] hover:text-[#007aff] transition-colors"
                      >
                        {uploading
                          ? <Loader2 className="w-6 h-6 animate-spin" />
                          : <Upload className="w-6 h-6" />}
                        <span className="text-xs">{uploading ? '上传中...' : `上传${label}`}</span>
                      </button>
                    )}
                  </div>

                  {/* 槽位标签 */}
                  <div className="absolute bottom-2 left-2 px-2 py-0.5 rounded-md bg-[rgba(242,242,247,0.85)] text-[11px] text-[#3a3a3c] border border-[rgba(229,229,234,0.5)]">
                    {label}
                  </div>

                  {/* 已有图片的操作：手机无 hover，始终显示；桌面悬停显示 */}
                  {url && !uploading && (
                    <div className="absolute top-2 right-2 flex gap-1.5 opacity-100 sm:opacity-0 sm:group-hover:opacity-100 transition-opacity">
                      <button
                        onClick={() => setPreviewUrl(url)}
                        className="min-w-9 min-h-9 p-2 rounded-lg bg-[rgba(242,242,247,0.92)] text-[#3a3a3c] hover:text-[#007aff] transition-colors"
                        title="预览"
                      >
                        <ZoomIn className="w-4 h-4" />
                      </button>
                      <button
                        onClick={() => fileInputs.current[slot]?.click()}
                        className="min-w-9 min-h-9 p-2 rounded-lg bg-[rgba(242,242,247,0.92)] text-[#3a3a3c] hover:text-[#007aff] transition-colors"
                        title="替换"
                      >
                        <Upload className="w-4 h-4" />
                      </button>
                      <button
                        onClick={() => handleRemoveImage(slot)}
                        className="min-w-9 min-h-9 p-2 rounded-lg bg-[rgba(242,242,247,0.92)] text-[#3a3a3c] hover:text-[#ff3b30] transition-colors"
                        title="删除"
                      >
                        <X className="w-4 h-4" />
                      </button>
                    </div>
                  )}
                  {uploading && url && (
                    <div className="absolute inset-0 flex items-center justify-center bg-[rgba(242,242,247,0.6)] rounded-xl">
                      <Loader2 className="w-6 h-6 animate-spin text-[#007aff]" />
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

        {/* ===== 第一层：商品信息（钉钉工作通知落地表单） ===== */}
        <div id="goods-info" className="bg-white rounded-xl border border-[rgba(229,229,234,0.5)] p-4 sm:p-5">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between mb-4">
            <h2 className="text-sm font-semibold text-[#1c1c1e] flex flex-wrap items-center gap-2">
              <FileText className="w-4 h-4 text-[#007aff]" />
              商品信息
              <span className="text-[10px] font-normal text-[#8e8e93]">货号或品名变更后文件夹自动重命名</span>
            </h2>
            <button
              onClick={() => saveFields(infoForm, setSavingInfo)}
              disabled={savingInfo}
              className="w-full sm:w-auto min-h-11 flex items-center justify-center gap-1.5 px-4 py-2.5 rounded-lg bg-[#007AFF] text-white text-sm font-medium disabled:opacity-50 transition-all"
            >
              {savingInfo ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
              保存信息
            </button>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3 sm:gap-4">
            {INFO_FIELDS.map(({ key, label, icon: Icon, required }) => (
              <div key={key} id={key === 'sampler' ? 'sampler-field' : undefined}>
                <label className="flex items-center gap-1.5 text-xs text-[#8e8e93] mb-1.5">
                  <Icon className="w-3.5 h-3.5" />
                  {label}
                  {required && <span className="text-[#ff3b30]">*</span>}
                </label>
                <input
                  value={infoForm[key] || ''}
                  onChange={e => setInfoForm(prev => ({ ...prev, [key]: e.target.value }))}
                  placeholder={required ? `请输入${label}（必填）` : `请输入${label}（可空）`}
                  autoComplete="off"
                  className="w-full min-h-11 px-3 py-2.5 rounded-lg bg-[rgba(242,242,247,0.6)] border border-[rgba(229,229,234,0.5)] text-base sm:text-sm text-[#1c1c1e] placeholder:text-[#8e8e93] focus:outline-none focus:border-[rgba(0,122,255,0.5)] focus:ring-1 focus:ring-[rgba(0,122,255,0.3)] transition-all"
                />
              </div>
            ))}
          </div>
        </div>

        {/* ===== 备注（卖点/竞品/功能/对应人群/使用场景，均可空） ===== */}
        <div className="bg-white rounded-xl border border-[rgba(229,229,234,0.5)] p-4 sm:p-5">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between mb-4">
            <h2 className="text-sm font-semibold text-[#1c1c1e] flex items-center gap-2">
              <Sparkles className="w-4 h-4 text-[#ff9500]" />
              备注
            </h2>
            <button
              onClick={() => saveFields(remarkForm, setSavingRemark)}
              disabled={savingRemark}
              className="w-full sm:w-auto min-h-11 flex items-center justify-center gap-1.5 px-4 py-2.5 rounded-lg bg-[#007AFF] text-white text-sm font-medium disabled:opacity-50 transition-all"
            >
              {savingRemark ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
              保存备注
            </button>
          </div>
          <textarea
            value={remarkForm.remark || ''}
            onChange={e => setRemarkForm({ remark: e.target.value })}
            placeholder={'可填写卖点、竞品、功能、对应人群、使用场景等（可空）\n\n卖点：\n竞品：\n功能：\n对应人群：\n使用场景：'}
            rows={6}
            className="w-full min-h-32 px-3 py-2.5 rounded-lg bg-[rgba(242,242,247,0.6)] border border-[rgba(229,229,234,0.5)] text-base sm:text-sm text-[#1c1c1e] placeholder:text-[#8e8e93] focus:outline-none focus:border-[rgba(0,122,255,0.5)] focus:ring-1 focus:ring-[rgba(0,122,255,0.3)] transition-all resize-none"
          />
        </div>
      </div>

      {/* 图片预览弹层 */}
      {previewUrl && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4 sm:p-8"
          onClick={() => setPreviewUrl(null)}
        >
          <img src={previewUrl} alt="预览" className="max-w-full max-h-full object-contain rounded-lg shadow-2xl" />
          <button
            className="absolute top-4 right-4 min-w-11 min-h-11 flex items-center justify-center rounded-lg bg-[rgba(242,242,247,0.92)] text-[#3a3a3c] hover:text-[#1C1C1E] transition-colors"
            onClick={() => setPreviewUrl(null)}
          >
            <X className="w-5 h-5" />
          </button>
        </div>
      )}

      <Toaster position="top-center" richColors closeButton />
    </div>
  );
}
