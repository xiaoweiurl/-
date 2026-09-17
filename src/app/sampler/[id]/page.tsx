'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useParams } from 'next/navigation';
import { toast } from 'sonner';
import { Toaster } from '@/components/ui/sonner';
import {
  Building2, CreditCard, FileText, Hash, Image as ImageIcon, Loader2,
  PenTool, Save, Upload, User, X, ZoomIn,
} from 'lucide-react';
import { loginHref, samplerFormPath } from '@/lib/auth-redirect';

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

const IMAGE_SLOTS = [
  { slot: 'main', label: '主图' },
  { slot: 'side', label: '侧面图' },
  { slot: 'detail', label: '细节' },
  { slot: 'product', label: '产品图' },
] as const;

const INFO_FIELDS = [
  { key: 'goods_no', label: '货号', icon: CreditCard },
  { key: 'product_name', label: '品名', icon: Hash },
  { key: 'customer', label: '客户', icon: Building2 },
  { key: 'order_no', label: '订单号', icon: FileText },
  { key: 'initiator', label: '发起人', icon: User, required: true },
  { key: 'sampler', label: '打样员', icon: PenTool },
] as const;

type SlotKey = (typeof IMAGE_SLOTS)[number]['slot'];
type InfoKey = (typeof INFO_FIELDS)[number]['key'];

function isGoodsId(id: string | undefined): id is string {
  return !!id && /^\d+$/.test(id);
}

async function api(path: string, init?: RequestInit): Promise<Response> {
  return fetch(path, { credentials: 'include', ...init });
}

export default function SamplerFormPage() {
  const params = useParams();
  const id = typeof params?.id === 'string' ? params.id : '';

  const [detail, setDetail] = useState<GoodsDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(
    isGoodsId(id) ? null : '无效的商品编号',
  );
  const [form, setForm] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [uploadingSlot, setUploadingSlot] = useState<SlotKey | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const fileInputs = useRef<Record<string, HTMLInputElement | null>>({});

  const applyDetail = useCallback((d: GoodsDetail) => {
    setDetail(d);
    setForm({
      initiator: d.initiator || '',
      sampler: d.sampler || '',
      product_name: d.product_name || '',
      goods_no: d.goods_no || '',
      customer: d.customer || '',
      order_no: d.order_no || '',
      remark: d.remark || '',
    });
    setLoadError(null);
  }, []);

  const fetchDetail = useCallback(async () => {
    if (!isGoodsId(id)) {
      setLoading(false);
      setLoadError('无效的商品编号');
      return;
    }
    setLoading(true);
    setLoadError(null);
    try {
      const res = await api(`/api/goods-library/${id}`);
      if (res.status === 401) {
        window.location.href = loginHref(samplerFormPath(id));
        return;
      }
      const data = await res.json().catch(() => ({}));
      if (res.ok && data.success && data.data) {
        applyDetail(data.data as GoodsDetail);
      } else {
        setDetail(null);
        setLoadError(data.message || '商品不存在或无法加载');
      }
    } catch {
      setDetail(null);
      setLoadError('加载失败，请检查网络后重试');
    } finally {
      setLoading(false);
    }
  }, [id, applyDetail]);

  useEffect(() => {
    void fetchDetail();
  }, [fetchDetail]);

  const handleSave = async () => {
    if (!isGoodsId(id)) return;
    if (!(form.initiator || '').trim()) {
      toast.error('发起人不能为空');
      return;
    }
    setSaving(true);
    try {
      const body: Record<string, string> = {};
      for (const field of INFO_FIELDS) {
        body[field.key] = form[field.key] || '';
      }
      body.remark = form.remark || '';
      const res = await api(`/api/goods-library/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
      if (res.status === 401) {
        window.location.href = loginHref(samplerFormPath(id));
        return;
      }
      const data = await res.json().catch(() => ({}));
      if (res.ok && data.success) {
        if (data.data) applyDetail(data.data as GoodsDetail);
        else await fetchDetail();
        toast.success('已保存到商品库');
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
    if (!isGoodsId(id)) return;
    setUploadingSlot(slot);
    try {
      const formData = new FormData();
      formData.append('slot', slot);
      formData.append('file', file);
      const res = await api(`/api/goods-library/${id}/images`, {
        method: 'POST',
        body: formData,
      });
      if (res.status === 401) {
        window.location.href = loginHref(samplerFormPath(id));
        return;
      }
      const data = await res.json().catch(() => ({}));
      if (res.ok && data.success) {
        await fetchDetail();
        toast.success('图片已上传');
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
    if (!isGoodsId(id) || !confirm('确定删除这张图片吗？')) return;
    setUploadingSlot(slot);
    try {
      const res = await api(`/api/goods-library/${id}/images?slot=${slot}`, { method: 'DELETE' });
      if (res.status === 401) {
        window.location.href = loginHref(samplerFormPath(id));
        return;
      }
      const data = await res.json().catch(() => ({}));
      if (res.ok && data.success) {
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

  const imageUrlOf = (slot: SlotKey) =>
    detail ? (detail[`${slot}_image_url` as keyof GoodsDetail] as string | null) : null;

  const title = detail?.folder_name || (isGoodsId(id) ? `商品 #${id}` : '打样任务');

  return (
    <div className="min-h-[100dvh] bg-[#F2F2F7] text-[#1C1C1E] overflow-x-hidden touch-manipulation pb-[max(5.5rem,calc(env(safe-area-inset-bottom)+4.5rem))]">
      <header className="sticky top-0 z-20 border-b border-[#E5E5EA] bg-white/90 backdrop-blur-xl pt-[env(safe-area-inset-top)]">
        <div className="max-w-lg mx-auto px-4 min-h-14 flex flex-col justify-center py-2">
          <p className="text-[11px] font-medium text-[#007AFF]">打样任务</p>
          <h1 className="text-base font-semibold truncate">{title}</h1>
        </div>
      </header>

      {loading && (
        <div className="flex items-center justify-center gap-2 py-24 text-[#8E8E93]">
          <Loader2 className="w-5 h-5 animate-spin" />
          <span className="text-sm">加载中...</span>
        </div>
      )}

      {!loading && loadError && (
        <div className="max-w-lg mx-auto px-4 py-16 text-center">
          <ImageIcon className="w-10 h-10 text-[#8E8E93] mx-auto mb-3" />
          <p className="text-sm text-[#3A3A3C] mb-4">{loadError}</p>
          {isGoodsId(id) && (
            <button
              onClick={() => void fetchDetail()}
              className="min-h-11 px-5 rounded-xl bg-[#007AFF] text-white text-sm font-medium"
            >
              重新加载
            </button>
          )}
        </div>
      )}

      {!loading && detail && (
        <main className="max-w-lg mx-auto px-4 py-4 space-y-4">
          <section className="bg-white rounded-2xl border border-[#E5E5EA] p-4">
            <h2 className="text-sm font-semibold mb-3 flex items-center gap-2">
              <FileText className="w-4 h-4 text-[#007AFF]" />
              商品信息
            </h2>
            <div className="space-y-3">
              {INFO_FIELDS.map(({ key, label, icon: Icon, ...rest }) => {
                const required = 'required' in rest && rest.required;
                return (
                  <label key={key} className="block">
                    <span className="flex items-center gap-1.5 text-xs text-[#8E8E93] mb-1.5">
                      <Icon className="w-3.5 h-3.5" />
                      {label}
                      {required && <span className="text-[#FF3B30]">*</span>}
                    </span>
                    <input
                      value={form[key as InfoKey] || ''}
                      onChange={e => setForm(prev => ({ ...prev, [key]: e.target.value }))}
                      placeholder={required ? `请输入${label}` : `${label}（可空）`}
                      autoComplete="off"
                      className="w-full min-h-11 px-3 rounded-xl bg-[#F2F2F7] border border-[#E5E5EA] text-base text-[#1C1C1E] placeholder:text-[#8E8E93] focus:outline-none focus:border-[#007AFF] focus:ring-1 focus:ring-[rgba(0,122,255,0.3)]"
                    />
                  </label>
                );
              })}
              <label className="block">
                <span className="flex items-center gap-1.5 text-xs text-[#8E8E93] mb-1.5">
                  备注（卖点 / 竞品 / 使用场景）
                </span>
                <textarea
                  value={form.remark || ''}
                  onChange={e => setForm(prev => ({ ...prev, remark: e.target.value }))}
                  rows={5}
                  placeholder="可填写卖点、竞品、功能、对应人群、使用场景"
                  className="w-full min-h-28 px-3 py-2.5 rounded-xl bg-[#F2F2F7] border border-[#E5E5EA] text-base text-[#1C1C1E] placeholder:text-[#8E8E93] focus:outline-none focus:border-[#007AFF] focus:ring-1 focus:ring-[rgba(0,122,255,0.3)] resize-none"
                />
              </label>
            </div>
          </section>

          <section className="bg-white rounded-2xl border border-[#E5E5EA] p-4">
            <h2 className="text-sm font-semibold mb-3 flex items-center gap-2">
              <ImageIcon className="w-4 h-4 text-[#007AFF]" />
              商品图片
            </h2>
            <div className="grid grid-cols-2 gap-3">
              {IMAGE_SLOTS.map(({ slot, label }) => {
                const url = imageUrlOf(slot);
                const uploading = uploadingSlot === slot;
                return (
                  <div key={slot} className="relative">
                    <div className="aspect-square rounded-xl border border-dashed border-[#E5E5EA] bg-[#F2F2F7] overflow-hidden">
                      {url ? (
                        // eslint-disable-next-line @next/next/no-img-element
                        <img
                          src={url}
                          alt={label}
                          className="w-full h-full object-cover"
                          onClick={() => setPreviewUrl(url)}
                        />
                      ) : (
                        <button
                          type="button"
                          onClick={() => fileInputs.current[slot]?.click()}
                          disabled={uploading}
                          className="w-full h-full flex flex-col items-center justify-center gap-1.5 text-[#8E8E93]"
                        >
                          {uploading
                            ? <Loader2 className="w-6 h-6 animate-spin" />
                            : <Upload className="w-6 h-6" />}
                          <span className="text-xs">{uploading ? '上传中' : `上传${label}`}</span>
                        </button>
                      )}
                    </div>
                    <div className="absolute bottom-2 left-2 px-1.5 py-0.5 rounded-md bg-white/90 text-[11px] text-[#3A3A3C]">
                      {label}
                    </div>
                    {url && !uploading && (
                      <div className="absolute top-2 right-2 flex gap-1">
                        <button
                          type="button"
                          onClick={() => setPreviewUrl(url)}
                          className="min-w-9 min-h-9 rounded-lg bg-white/90 flex items-center justify-center"
                          aria-label="预览"
                        >
                          <ZoomIn className="w-4 h-4" />
                        </button>
                        <button
                          type="button"
                          onClick={() => fileInputs.current[slot]?.click()}
                          className="min-w-9 min-h-9 rounded-lg bg-white/90 flex items-center justify-center"
                          aria-label="替换"
                        >
                          <Upload className="w-4 h-4" />
                        </button>
                        <button
                          type="button"
                          onClick={() => void handleRemoveImage(slot)}
                          className="min-w-9 min-h-9 rounded-lg bg-white/90 flex items-center justify-center text-[#FF3B30]"
                          aria-label="删除"
                        >
                          <X className="w-4 h-4" />
                        </button>
                      </div>
                    )}
                    <input
                      ref={el => { fileInputs.current[slot] = el; }}
                      type="file"
                      accept="image/*"
                      className="hidden"
                      onChange={e => {
                        const f = e.target.files?.[0];
                        if (f) void handleUpload(slot, f);
                      }}
                    />
                  </div>
                );
              })}
            </div>
          </section>
        </main>
      )}

      {detail && (
        <div className="fixed bottom-0 inset-x-0 z-20 border-t border-[#E5E5EA] bg-white/95 backdrop-blur-xl pb-[env(safe-area-inset-bottom)]">
          <div className="max-w-lg mx-auto px-4 py-3">
            <button
              type="button"
              onClick={() => void handleSave()}
              disabled={saving}
              className="w-full min-h-11 rounded-xl bg-[#007AFF] text-white text-base font-medium flex items-center justify-center gap-2 disabled:opacity-50 active:scale-[0.98] transition-all"
            >
              {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
              保存
            </button>
          </div>
        </div>
      )}

      {previewUrl && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
          onClick={() => setPreviewUrl(null)}
        >
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={previewUrl} alt="预览" className="max-w-full max-h-full object-contain rounded-xl" />
        </div>
      )}

      <Toaster position="top-center" richColors closeButton />
    </div>
  );
}
