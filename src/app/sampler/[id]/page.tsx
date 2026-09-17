'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useParams } from 'next/navigation';
import { toast } from 'sonner';
import { Toaster } from '@/components/ui/sonner';
import { Camera, Loader2, X } from 'lucide-react';
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
  { slot: 'side', label: '侧面' },
  { slot: 'detail', label: '细节' },
  { slot: 'product', label: '产品' },
] as const;

const FILL_FIELDS = [
  { key: 'goods_no', label: '货号', placeholder: '填写货号' },
  { key: 'product_name', label: '品名', placeholder: '填写品名' },
  { key: 'customer', label: '客户', placeholder: '选填' },
  { key: 'order_no', label: '订单号', placeholder: '选填' },
] as const;

type SlotKey = (typeof IMAGE_SLOTS)[number]['slot'];
type FillKey = (typeof FILL_FIELDS)[number]['key'];

function isGoodsId(id: string | undefined): id is string {
  return !!id && /^\d+$/.test(id);
}

function displayName(d: GoodsDetail): string {
  const name = (d.folder_name || '').trim();
  if (name && name !== '未命名商品') return name;
  const no = (d.goods_no || '').trim();
  const product = (d.product_name || '').trim();
  return [no, product].filter(Boolean).join(' ') || '未命名商品';
}

async function api(path: string, init?: RequestInit): Promise<Response> {
  return fetch(path, { credentials: 'include', ...init });
}

const fieldClass =
  'w-full min-h-12 bg-transparent text-[17px] leading-snug text-[#1C1C1E] placeholder:text-[#C7C7CC] focus:outline-none';

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
    if (!isGoodsId(id) || !detail) return;
    const initiator = (detail.initiator || '').trim();
    if (!initiator) {
      toast.error('该商品缺少发起人，请联系管理员');
      return;
    }
    setSaving(true);
    try {
      const body: Record<string, string> = {
        initiator,
        sampler: detail.sampler || '',
        goods_no: form.goods_no || '',
        product_name: form.product_name || '',
        customer: form.customer || '',
        order_no: form.order_no || '',
        remark: form.remark || '',
      };
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
        toast.success('已保存');
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
    if (!isGoodsId(id) || !confirm('删除这张图片？')) return;
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

  const col = 'w-full max-w-md mx-auto px-[max(1.25rem,env(safe-area-inset-left))] pr-[max(1.25rem,env(safe-area-inset-right))]';
  const frost = {
    background: 'rgba(242,242,247,0.88)',
    backdropFilter: 'blur(20px)',
    WebkitBackdropFilter: 'blur(20px)',
  } as const;

  return (
    <div className="min-h-[100dvh] w-full max-w-[100vw] bg-[#F2F2F7] text-[#1C1C1E] overflow-x-hidden touch-manipulation pb-[max(7.5rem,calc(env(safe-area-inset-bottom)+6.25rem))]">
      <header className="sticky top-0 z-20 w-full pt-[env(safe-area-inset-top)]" style={frost}>
        <div className={`${col} py-3`}>
          <p className="text-[13px] font-medium text-[#007AFF] tracking-wide">打样任务</p>
          <h1 className="mt-0.5 text-[22px] font-bold leading-tight tracking-tight truncate">
            {detail ? displayName(detail) : isGoodsId(id) ? '加载中' : '打样任务'}
          </h1>
          {detail && (
            <p className="mt-1 text-[13px] text-[#8E8E93] truncate">
              {[
                detail.initiator && `发起 ${detail.initiator}`,
                detail.sampler && `打样 ${detail.sampler}`,
              ].filter(Boolean).join(' · ') || '请完善货号与品名'}
            </p>
          )}
        </div>
      </header>

      {loading && (
        <div className="flex items-center justify-center gap-2 py-28 text-[#8E8E93]">
          <Loader2 className="w-5 h-5 animate-spin" />
          <span className="text-[15px]">加载中</span>
        </div>
      )}

      {!loading && loadError && (
        <div className={`${col} py-24 text-center`}>
          <p className="text-[15px] text-[#3A3A3C] mb-5">{loadError}</p>
          {isGoodsId(id) && (
            <button
              type="button"
              onClick={() => void fetchDetail()}
              className="min-h-12 px-6 rounded-xl bg-[#007AFF] text-white text-[17px] font-medium active:scale-[0.98] transition-transform"
            >
              重新加载
            </button>
          )}
        </div>
      )}

      {!loading && detail && (
        <main className={`${col} pt-2 space-y-5`}>
          <section className="bg-white rounded-2xl shadow-[0_2px_12px_rgba(0,0,0,0.04)] overflow-hidden">
            {FILL_FIELDS.map(({ key, label, placeholder }, index) => (
              <label
                key={key}
                className={`flex items-center gap-3 min-h-14 px-4 ${index > 0 ? 'border-t border-[#E5E5EA]' : ''}`}
              >
                <span className="w-[4.5rem] shrink-0 text-[15px] text-[#1C1C1E]">{label}</span>
                <input
                  value={form[key as FillKey] || ''}
                  onChange={e => setForm(prev => ({ ...prev, [key]: e.target.value }))}
                  placeholder={placeholder}
                  autoComplete="off"
                  enterKeyHint="next"
                  className={fieldClass}
                />
              </label>
            ))}
          </section>

          <section className="bg-white rounded-2xl shadow-[0_2px_12px_rgba(0,0,0,0.04)] px-4 py-3">
            <label className="block">
              <span className="block text-[13px] text-[#8E8E93] mb-1.5">备注</span>
              <textarea
                value={form.remark || ''}
                onChange={e => setForm(prev => ({ ...prev, remark: e.target.value }))}
                rows={4}
                placeholder="卖点、竞品、使用场景"
                className="w-full min-h-[6.5rem] bg-transparent text-[17px] leading-relaxed text-[#1C1C1E] placeholder:text-[#C7C7CC] focus:outline-none resize-none"
              />
            </label>
          </section>

          <section>
            <p className="px-1 mb-2 text-[13px] text-[#8E8E93]">图片</p>
            <div className="grid grid-cols-2 gap-3">
              {IMAGE_SLOTS.map(({ slot, label }) => {
                const url = imageUrlOf(slot);
                const uploading = uploadingSlot === slot;
                return (
                  <div key={slot}>
                    <button
                      type="button"
                      onClick={() => {
                        if (uploading) return;
                        if (url) setPreviewUrl(url);
                        else fileInputs.current[slot]?.click();
                      }}
                      className="relative w-full aspect-[4/3] rounded-2xl bg-white shadow-[0_2px_12px_rgba(0,0,0,0.04)] overflow-hidden active:scale-[0.99] transition-transform"
                    >
                      {url ? (
                        // eslint-disable-next-line @next/next/no-img-element
                        <img src={url} alt={label} className="w-full h-full object-cover" />
                      ) : (
                        <span className="flex flex-col items-center justify-center gap-1.5 h-full text-[#8E8E93]">
                          {uploading
                            ? <Loader2 className="w-6 h-6 animate-spin text-[#007AFF]" />
                            : <Camera className="w-7 h-7" strokeWidth={1.5} />}
                          <span className="text-[13px]">{uploading ? '上传中' : '添加'}</span>
                        </span>
                      )}
                    </button>
                    <div className="mt-1.5 flex items-center justify-between min-h-8 px-0.5">
                      <span className="text-[13px] text-[#8E8E93]">{label}</span>
                      {url && !uploading && (
                        <span className="flex items-center gap-3">
                          <button
                            type="button"
                            onClick={() => fileInputs.current[slot]?.click()}
                            className="min-h-8 text-[13px] text-[#007AFF]"
                          >
                            更换
                          </button>
                          <button
                            type="button"
                            onClick={() => void handleRemoveImage(slot)}
                            className="min-h-8 text-[13px] text-[#FF3B30]"
                          >
                            删除
                          </button>
                        </span>
                      )}
                    </div>
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
        <div className="fixed bottom-0 inset-x-0 z-20 pt-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]" style={frost}>
          <div className={col}>
            <button
              type="button"
              onClick={() => void handleSave()}
              disabled={saving}
              className="w-full min-h-12 rounded-xl bg-[#007AFF] text-white text-[17px] font-semibold flex items-center justify-center disabled:opacity-50 active:scale-[0.98] transition-transform"
            >
              {saving ? <Loader2 className="w-5 h-5 animate-spin" /> : '保存'}
            </button>
          </div>
        </div>
      )}

      {previewUrl && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-5"
          onClick={() => setPreviewUrl(null)}
        >
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={previewUrl} alt="" className="max-w-full max-h-full object-contain rounded-2xl" />
          <button
            type="button"
            className="absolute top-[max(1rem,env(safe-area-inset-top))] right-[max(1rem,env(safe-area-inset-right))] min-w-11 min-h-11 rounded-full bg-white/90 flex items-center justify-center"
            onClick={() => setPreviewUrl(null)}
            aria-label="关闭"
          >
            <X className="w-5 h-5" />
          </button>
        </div>
      )}

      <Toaster position="bottom-center" richColors offset={{ bottom: 96 }} mobileOffset={{ bottom: 96 }} />
    </div>
  );
}

