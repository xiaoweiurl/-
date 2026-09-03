'use client';

import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { toast } from 'sonner';
import { Toaster } from '@/components/ui/sonner';
import {
  FolderOpen, Plus, Search, Loader2, Trash2, ArrowLeft,
  User, PenTool, Hash, CreditCard, Building2, FileText, ImagePlus, X,
} from 'lucide-react';

interface GoodsFolder {
  id: number;
  folder_name: string;
  initiator: string | null;
  sampler: string | null;
  product_name: string | null;
  goods_no: string | null;
  customer: string | null;
  order_no: string | null;
  main_image_url: string | null;
  created_at: string;
}

const EMPTY_FORM = {
  initiator: '', sampler: '', product_name: '',
  goods_no: '', customer: '', order_no: '', remark: '',
};

/** 第一层表格字段（均可空） */
const FORM_FIELDS = [
  { key: 'initiator', label: '发起人', icon: User },
  { key: 'sampler', label: '打样员', icon: PenTool },
  { key: 'product_name', label: '品名', icon: Hash },
  { key: 'goods_no', label: '货号', icon: CreditCard },
  { key: 'customer', label: '客户', icon: Building2 },
  { key: 'order_no', label: '订单号', icon: FileText },
] as const;

/** 图片槽位（均可空，创建时可一次性上传，图片以槽位名命名） */
const IMAGE_SLOTS = [
  { key: 'main', label: '主图', field: 'mainImage' },
  { key: 'side', label: '侧面图', field: 'sideImage' },
  { key: 'detail', label: '细节', field: 'detailImage' },
  { key: 'product', label: '产品图', field: 'productImage' },
] as const;

type SlotKey = (typeof IMAGE_SLOTS)[number]['key'];
const EMPTY_SLOT_FILES: Record<SlotKey, File | null> = { main: null, side: null, detail: null, product: null };

export default function GoodsLibraryPage() {
  const router = useRouter();
  const [folders, setFolders] = useState<GoodsFolder[]>([]);
  const [loading, setLoading] = useState(true);
  const [keyword, setKeyword] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [slotFiles, setSlotFiles] = useState<Record<SlotKey, File | null>>(EMPTY_SLOT_FILES);
  const [creating, setCreating] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const fileInputs = useRef<Record<string, HTMLInputElement | null>>({});

  // 本地图片预览 URL（随文件选择生成，变更时释放旧 URL）
  const slotPreviews = useMemo(() => {
    const map: Partial<Record<SlotKey, string>> = {};
    for (const { key } of IMAGE_SLOTS) {
      const f = slotFiles[key];
      if (f) map[key] = URL.createObjectURL(f);
    }
    return map;
  }, [slotFiles]);

  useEffect(() => {
    return () => {
      Object.values(slotPreviews).forEach(url => url && URL.revokeObjectURL(url));
    };
  }, [slotPreviews]);

  const fetchFolders = useCallback(async (kw?: string) => {
    try {
      const url = kw ? `/api/goods-library?keyword=${encodeURIComponent(kw)}` : '/api/goods-library';
      const res = await fetch(url);
      const data = await res.json();
      if (data.success) setFolders(data.data || []);
    } catch (e) {
      console.error('加载商品库失败:', e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchFolders();
  }, [fetchFolders]);

  const handleSearch = () => {
    setLoading(true);
    fetchFolders(keyword);
  };

  const handleCreate = async () => {
    setCreating(true);
    try {
      // 一次性提交：文本字段 + 四类图片（multipart）
      const fd = new FormData();
      Object.entries(form).forEach(([k, v]) => fd.append(k, v));
      for (const { key, field } of IMAGE_SLOTS) {
        const f = slotFiles[key];
        if (f) fd.append(field, f);
      }
      const res = await fetch('/api/goods-library', { method: 'POST', body: fd });
      const data = await res.json();
      if (data.success) {
        setShowCreate(false);
        setForm(EMPTY_FORM);
        setSlotFiles(EMPTY_SLOT_FILES);
        toast.success('商品文件夹创建成功');
        // 创建成功直接进入该商品文件夹，继续完善图片与备注
        router.push(`/goods-library/${data.data.id}`);
      } else {
        toast.error(data.message || '创建失败');
      }
    } catch (e) {
      toast.error('创建失败，请重试');
    } finally {
      setCreating(false);
    }
  };

  const handleDelete = async (e: React.MouseEvent, id: number) => {
    e.stopPropagation();
    if (!confirm('确定删除该商品文件夹吗？其中的图片将一并删除。')) return;
    setDeletingId(id);
    try {
      const res = await fetch(`/api/goods-library/${id}`, { method: 'DELETE' });
      const data = await res.json();
      if (data.success) {
        setFolders(prev => prev.filter(f => f.id !== id));
        toast.success('已删除');
      } else {
        toast.error(data.message || '删除失败');
      }
    } catch {
      toast.error('删除失败，请重试');
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <div className="min-h-screen bg-[#0f172a] text-slate-100">
      {/* 顶栏 */}
      <div className="sticky top-0 z-20 backdrop-blur-xl bg-[#0f172a]/80 border-b border-blue-500/15">
        <div className="max-w-7xl mx-auto px-6 h-16 flex items-center gap-4">
          <button
            onClick={() => router.push('/')}
            className="p-2 rounded-lg hover:bg-slate-700/50 text-slate-400 hover:text-slate-200 transition-colors"
            title="返回首页"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-blue-600 to-cyan-500 flex items-center justify-center shadow-[0_0_12px_rgba(59,130,246,0.3)]">
              <FolderOpen className="w-4 h-4 text-white" />
            </div>
            <h1 className="text-lg font-semibold">商品库</h1>
            <span className="text-xs text-slate-500">{folders.length} 个商品文件夹</span>
          </div>

          <div className="flex-1" />

          {/* 搜索 */}
          <div className="relative">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
            <input
              value={keyword}
              onChange={e => setKeyword(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && handleSearch()}
              placeholder="搜索货号 / 品名 / 客户 / 订单号"
              className="w-64 pl-9 pr-3 py-2 rounded-lg bg-slate-800/60 border border-slate-700/50 text-sm text-slate-200 placeholder:text-slate-500 focus:outline-none focus:border-blue-500/50 focus:ring-1 focus:ring-blue-500/30 transition-all"
            />
          </div>

          <button
            onClick={() => setShowCreate(true)}
            className="flex items-center gap-1.5 px-4 py-2 rounded-lg bg-gradient-to-r from-blue-600 to-cyan-500 text-white text-sm font-medium shadow-[0_0_15px_rgba(59,130,246,0.25)] hover:shadow-[0_0_20px_rgba(59,130,246,0.4)] transition-all"
          >
            <Plus className="w-4 h-4" />
            新建商品
          </button>
        </div>
      </div>

      {/* 文件夹网格 */}
      <div className="max-w-7xl mx-auto px-6 py-8">
        {loading ? (
          <div className="flex items-center justify-center py-32 text-slate-500">
            <Loader2 className="w-6 h-6 animate-spin mr-2" /> 加载中...
          </div>
        ) : folders.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-32 text-slate-500">
            <FolderOpen className="w-16 h-16 mb-4 text-slate-600" />
            <p className="text-sm">暂无商品文件夹</p>
            <p className="text-xs mt-1 text-slate-600">点击右上角「新建商品」创建第一个商品文件夹</p>
          </div>
        ) : (
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-5">
            {folders.map(folder => (
              <div
                key={folder.id}
                onClick={() => router.push(`/goods-library/${folder.id}`)}
                className="group relative bg-slate-800/50 rounded-xl border border-slate-700/50 overflow-hidden cursor-pointer hover:border-blue-500/40 hover:shadow-[0_0_20px_rgba(59,130,246,0.15)] hover:-translate-y-1 transition-all duration-300"
              >
                {/* 文件夹封面 = 主图 */}
                <div className="aspect-[4/3] bg-slate-900/60 flex items-center justify-center overflow-hidden">
                  {folder.main_image_url ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img
                      src={folder.main_image_url}
                      alt={folder.folder_name}
                      className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                    />
                  ) : (
                    <FolderOpen className="w-14 h-14 text-slate-600 group-hover:text-blue-400/60 transition-colors" />
                  )}
                </div>

                {/* 文件夹信息 */}
                <div className="p-3 border-t border-slate-700/40">
                  <div className="text-sm font-medium text-slate-200 truncate" title={folder.folder_name}>
                    {folder.folder_name}
                  </div>
                  <div className="text-[11px] text-slate-500 mt-1 truncate">
                    {folder.customer ? `客户：${folder.customer}` : ' '}
                  </div>
                  <div className="text-[11px] text-slate-600 truncate">
                    {folder.order_no ? `订单：${folder.order_no}` : ' '}
                  </div>
                </div>

                {/* 删除按钮（悬停显示） */}
                <button
                  onClick={e => handleDelete(e, folder.id)}
                  disabled={deletingId === folder.id}
                  className="absolute top-2 right-2 p-1.5 rounded-lg bg-slate-900/80 text-slate-400 hover:text-red-400 hover:bg-slate-900 opacity-0 group-hover:opacity-100 transition-all"
                  title="删除商品文件夹"
                >
                  {deletingId === folder.id
                    ? <Loader2 className="w-4 h-4 animate-spin" />
                    : <Trash2 className="w-4 h-4" />}
                </button>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* 新建商品对话框（第一层表格信息，均允许为空） */}
      {showCreate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm" onClick={() => setShowCreate(false)}>
          <div
            className="w-full max-w-lg bg-slate-800 rounded-2xl border border-slate-700/60 shadow-2xl p-6"
            onClick={e => e.stopPropagation()}
          >
            <h2 className="text-base font-semibold mb-1">新建商品文件夹</h2>
            <p className="text-xs text-slate-500 mb-5">
              填写商品信息（均允许为空），文件夹名称将自动生成为「货号 + 品名」
            </p>

            <div className="grid grid-cols-2 gap-4">
              {FORM_FIELDS.map(({ key, label, icon: Icon }) => (
                <div key={key}>
                  <label className="flex items-center gap-1.5 text-xs text-slate-400 mb-1.5">
                    <Icon className="w-3.5 h-3.5" />
                    {label}
                  </label>
                  <input
                    value={form[key]}
                    onChange={e => setForm(prev => ({ ...prev, [key]: e.target.value }))}
                    placeholder={`请输入${label}（可空）`}
                    className="w-full px-3 py-2 rounded-lg bg-slate-900/60 border border-slate-700/50 text-sm text-slate-200 placeholder:text-slate-600 focus:outline-none focus:border-blue-500/50 focus:ring-1 focus:ring-blue-500/30 transition-all"
                  />
                </div>
              ))}
            </div>

            {/* 商品图片（均可空，创建时一次性上传） */}
            <div className="mt-5">
              <div className="text-xs text-slate-400 mb-2">商品图片（均可空，将分别以 主图/侧面图/细节/产品图 命名）</div>
              <div className="grid grid-cols-4 gap-3">
                {IMAGE_SLOTS.map(({ key, label }) => (
                  <div key={key}>
                    <input
                      ref={el => { fileInputs.current[key] = el; }}
                      type="file"
                      accept="image/*"
                      className="hidden"
                      onChange={e => {
                        const f = e.target.files?.[0];
                        if (f) setSlotFiles(prev => ({ ...prev, [key]: f }));
                        e.target.value = '';
                      }}
                    />
                    {slotFiles[key] && slotPreviews[key] ? (
                      <div className="relative group aspect-square rounded-lg overflow-hidden border border-blue-500/30">
                        {/* eslint-disable-next-line @next/next/no-img-element */}
                        <img src={slotPreviews[key]} alt={label} className="w-full h-full object-cover" />
                        <button
                          onClick={() => setSlotFiles(prev => ({ ...prev, [key]: null }))}
                          className="absolute top-1 right-1 p-1 rounded-md bg-slate-900/80 text-slate-400 hover:text-red-400 opacity-0 group-hover:opacity-100 transition-all"
                          title="移除"
                        >
                          <X className="w-3.5 h-3.5" />
                        </button>
                        <div className="absolute bottom-0 inset-x-0 bg-slate-900/70 text-[10px] text-center text-slate-300 py-0.5">
                          {label}
                        </div>
                      </div>
                    ) : (
                      <button
                        onClick={() => fileInputs.current[key]?.click()}
                        className="w-full aspect-square rounded-lg border border-dashed border-slate-600 hover:border-blue-500/50 hover:bg-blue-500/5 flex flex-col items-center justify-center gap-1 text-slate-500 hover:text-blue-400 transition-all"
                      >
                        <ImagePlus className="w-5 h-5" />
                        <span className="text-[11px]">{label}</span>
                      </button>
                    )}
                  </div>
                ))}
              </div>
            </div>

            {/* 备注（可空，可填写卖点、竞品、功能、对应人群、使用场景等） */}
            <div className="mt-5">
              <div className="text-xs text-slate-400 mb-2">备注（可空，可填写卖点、竞品、功能、对应人群、使用场景等）</div>
              <textarea
                value={form.remark}
                onChange={e => setForm(prev => ({ ...prev, remark: e.target.value }))}
                rows={3}
                placeholder={'卖点：\n竞品：\n功能：\n对应人群：\n使用场景：'}
                className="w-full px-3 py-2 rounded-lg bg-slate-800/60 border border-slate-700/60 text-sm text-slate-200 placeholder:text-slate-600 focus:outline-none focus:border-blue-500/50 resize-none"
              />
            </div>

            {/* 文件夹名预览 */}
            <div className="mt-4 px-3 py-2 rounded-lg bg-blue-500/5 border border-blue-500/20 text-xs text-slate-400">
              文件夹名称：
              <span className="text-blue-300 font-medium">
                {(() => {
                  const no = form.goods_no.trim();
                  const name = form.product_name.trim();
                  if (no && name) return `${no}${name}`;
                  if (no) return no;
                  if (name) return name;
                  return '未命名商品';
                })()}
              </span>
            </div>

            <div className="flex justify-end gap-3 mt-6">
              <button
                onClick={() => setShowCreate(false)}
                className="px-4 py-2 rounded-lg text-sm text-slate-400 hover:text-slate-200 hover:bg-slate-700/50 transition-colors"
              >
                取消
              </button>
              <button
                onClick={handleCreate}
                disabled={creating}
                className="flex items-center gap-1.5 px-5 py-2 rounded-lg bg-gradient-to-r from-blue-600 to-cyan-500 text-white text-sm font-medium shadow-[0_0_15px_rgba(59,130,246,0.25)] hover:shadow-[0_0_20px_rgba(59,130,246,0.4)] disabled:opacity-50 transition-all"
              >
                {creating && <Loader2 className="w-4 h-4 animate-spin" />}
                创建商品文件夹
              </button>
            </div>
          </div>
        </div>
      )}

      <Toaster position="top-center" richColors closeButton />
    </div>
  );
}
