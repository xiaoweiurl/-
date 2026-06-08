'use client';

import React, { useState, useEffect, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { backendFetch } from '@/lib/backend-proxy';
import {
  Package, Warehouse, ShoppingCart, Factory, Gift,
  Search, Plus, Trash2, Edit2, Upload, ChevronLeft,
  BarChart3, FileSpreadsheet, RefreshCw, ArrowLeft
} from 'lucide-react';

// ====== 类型定义 ======
interface PageData<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

interface Stats {
  quotationCount: number;
  warehouseCount: number;
  purchaseCount: number;
  planCount: number;
  accessoryCount: number;
}

// ====== Tab定义 ======
const TABS = [
  { id: 'quotations', label: '产品报价', icon: Package, color: 'from-violet-500 to-purple-600' },
  { id: 'warehouse', label: '原料入库', icon: Warehouse, color: 'from-blue-500 to-cyan-600' },
  { id: 'purchases', label: '原料采购', icon: ShoppingCart, color: 'from-emerald-500 to-teal-600' },
  { id: 'plans', label: '生产计划', icon: Factory, color: 'from-orange-500 to-amber-600' },
  { id: 'accessories', label: '辅料采购', icon: Gift, color: 'from-pink-500 to-rose-600' },
];

export default function SupplyChainPage() {
  const [activeTab, setActiveTab] = useState('quotations');
  const [data, setData] = useState<PageData<any>>({ items: [], total: 0, page: 1, pageSize: 20, totalPages: 0 });
  const [stats, setStats] = useState<Stats | null>(null);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [showAddForm, setShowAddForm] = useState(false);
  const [editItem, setEditItem] = useState<any>(null);
  const [formData, setFormData] = useState<Record<string, any>>({});
  const router = useRouter();

  // ====== 登录检查 ======
  useEffect(() => {
    const sessionId = localStorage.getItem('session_id');
    if (!sessionId) {
      router.replace('/login');
    }

    // 监听浏览器后退，确保登出后无法通过后退回到页面
    const handlePageShow = (e: PageTransitionEvent) => {
      if (e.persisted) {
        const sid = localStorage.getItem('session_id');
        if (!sid) {
          router.replace('/login');
        }
      }
    };
    window.addEventListener('pageshow', handlePageShow);
    return () => window.removeEventListener('pageshow', handlePageShow);
  }, [router]);

  // ====== 数据获取 ======
  const fetchData = useCallback(async (page = 1) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(page), pageSize: '20' });
      if (keyword) params.set('keyword', keyword);
      const res = await backendFetch(`/supply-chain/${activeTab}?${params}`);
      const result = await res.json();
      setData(result);
    } catch (e) {
      console.error('获取数据失败', e);
    } finally {
      setLoading(false);
    }
  }, [activeTab, keyword]);

  const fetchStats = useCallback(async () => {
    try {
      const res = await backendFetch('/supply-chain/stats');
      setStats(await res.json());
    } catch (e) {
      console.error('获取统计失败', e);
    }
  }, []);

  useEffect(() => { fetchData(1); }, [fetchData]);
  useEffect(() => { fetchStats(); }, [fetchStats]);

  // ====== CRUD操作 ======
  const handleSave = async () => {
    try {
      const url = editItem
        ? `/supply-chain/${activeTab}/${editItem.id}`
        : `/supply-chain/${activeTab}`;
      const method = editItem ? 'PUT' : 'POST';
      await backendFetch(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(formData),
      });
      setShowAddForm(false);
      setEditItem(null);
      setFormData({});
      fetchData(data.page);
      fetchStats();
    } catch (e) {
      console.error('保存失败', e);
    }
  };

  const handleDelete = async (id: number) => {
    if (!confirm('确认删除此条记录？')) return;
    try {
      await backendFetch(`/supply-chain/${activeTab}/${id}`, { method: 'DELETE' });
      fetchData(data.page);
      fetchStats();
    } catch (e) {
      console.error('删除失败', e);
    }
  };

  const handleEdit = (item: any) => {
    setEditItem(item);
    setFormData(item);
    setShowAddForm(true);
  };

  // ====== Excel上传 ======
  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const fd = new FormData();
    fd.append('file', file);
    fd.append('type', activeTab);
    try {
      const res = await backendFetch('/supply-chain/import', { method: 'POST', body: fd });
      const result = await res.json();
      if (result.success) {
        fetchData(1);
        fetchStats();
      }
    } catch (e) {
      console.error('上传失败', e);
    }
    e.target.value = '';
  };

  // ====== 表格列定义 ======
  const getColumns = (): { key: string; label: string; width?: string }[] => {
    switch (activeTab) {
      case 'quotations': return [
        { key: 'productCode', label: '产品编码' },
        { key: 'productionCode', label: '生产编码' },
        { key: 'documentNo', label: '单据编号' },
        { key: 'period', label: '期间' },
        { key: 'customer', label: '客户' },
        { key: 'salesperson', label: '业务员' },
        { key: 'productCategory', label: '产品类别' },
        { key: 'approvalStatus', label: '审批状态' },
        { key: 'salesType', label: '销售类型' },
      ];
      case 'warehouse': return [
        { key: 'productCode', label: '产品编码' },
        { key: 'color', label: '颜色' },
        { key: 'batchNo', label: '批号' },
        { key: 'unit', label: '单位' },
        { key: 'unitPrice', label: '单价' },
      ];
      case 'purchases': return [
        { key: 'materialCode', label: '原料编码' },
        { key: 'unit', label: '单位' },
        { key: 'supplier', label: '供应商' },
        { key: 'batchNo', label: '批号' },
        { key: 'unitPrice', label: '单价' },
      ];
      case 'plans': return [
        { key: 'semiProductCode', label: '半成品编码' },
        { key: 'productCode', label: '产品编码' },
        { key: 'sewingWeight', label: '缝纫克重' },
        { key: 'machineType', label: '机型' },
        { key: 'needleCount', label: '针数' },
        { key: 'seconds', label: '秒数' },
        { key: 'machineCount', label: '机台数' },
        { key: 'singleMachineOutput', label: '单机产量' },
      ];
      case 'accessories': return [
        { key: 'accessoryName', label: '辅料名称' },
        { key: 'accessoryCategory', label: '辅料类别' },
        { key: 'unit', label: '单位' },
        { key: 'supplier', label: '供应商' },
        { key: 'accessoryUnitPrice', label: '单价' },
      ];
      default: return [];
    }
  };

  const getFormFields = (): { key: string; label: string; type?: string }[] => {
    switch (activeTab) {
      case 'quotations': return [
        { key: 'productCode', label: '产品编码' },
        { key: 'productionCode', label: '生产编码' },
        { key: 'documentNo', label: '单据编号' },
        { key: 'period', label: '期间' },
        { key: 'customer', label: '客户' },
        { key: 'salesperson', label: '业务员' },
        { key: 'productCategory', label: '产品类别' },
        { key: 'approvalStatus', label: '审批状态' },
        { key: 'salesType', label: '销售类型' },
        { key: 'rawMaterialName1', label: '原料1名称' },
        { key: 'materialUsage1', label: '原料1用量', type: 'number' },
        { key: 'materialUnitPrice1', label: '原料1单价', type: 'number' },
        { key: 'accessoryName', label: '辅料名称' },
        { key: 'accessoryPrice', label: '辅料价格', type: 'number' },
      ];
      case 'warehouse': return [
        { key: 'productCode', label: '产品编码' },
        { key: 'color', label: '颜色' },
        { key: 'batchNo', label: '批号' },
        { key: 'unit', label: '单位' },
        { key: 'unitPrice', label: '单价', type: 'number' },
      ];
      case 'purchases': return [
        { key: 'materialCode', label: '原料编码' },
        { key: 'unit', label: '单位' },
        { key: 'supplier', label: '供应商' },
        { key: 'batchNo', label: '批号' },
        { key: 'unitPrice', label: '单价', type: 'number' },
      ];
      case 'plans': return [
        { key: 'semiProductCode', label: '半成品编码' },
        { key: 'productCode', label: '产品编码' },
        { key: 'sewingWeight', label: '缝纫克重', type: 'number' },
        { key: 'machineType', label: '机型' },
        { key: 'needleCount', label: '针数' },
        { key: 'seconds', label: '秒数', type: 'number' },
        { key: 'machineCount', label: '机台数', type: 'number' },
        { key: 'singleMachineOutput', label: '单机产量', type: 'number' },
      ];
      case 'accessories': return [
        { key: 'accessoryName', label: '辅料名称' },
        { key: 'accessoryCategory', label: '辅料类别' },
        { key: 'unit', label: '单位' },
        { key: 'supplier', label: '供应商' },
        { key: 'accessoryUnitPrice', label: '单价', type: 'number' },
      ];
      default: return [];
    }
  };

  const currentTab = TABS.find(t => t.id === activeTab) || TABS[0];

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-50">
      {/* 顶部标题 */}
      <div className="bg-white/80 backdrop-blur-md border-b border-slate-200 px-8 py-6">
        <div className="flex items-center gap-3 mb-1">
          <button onClick={() => { localStorage.removeItem('session_id'); localStorage.removeItem('session_expires'); localStorage.removeItem('portal_type'); document.cookie = 'session_id=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT; SameSite=Lax'; router.replace('/login'); }} className="p-2 rounded-xl hover:bg-slate-100 transition-colors" title="退出登录">
            <ArrowLeft className="w-5 h-5 text-slate-500" />
          </button>
          <div className="w-10 h-10 rounded-xl bg-gradient-to-r from-violet-500 to-purple-600 flex items-center justify-center">
            <BarChart3 className="w-5 h-5 text-white" />
          </div>
          <div>
            <h1 className="text-2xl font-bold text-slate-800">供应链 & 工厂管理</h1>
            <p className="text-sm text-slate-500">产品报价、原料管理、生产计划、辅料采购</p>
          </div>
        </div>
      </div>

      {/* 统计卡片 */}
      <div className="px-8 py-4">
        <div className="grid grid-cols-5 gap-4">
          {TABS.map(tab => {
            const countKey = `${tab.id.replace('quotations', 'quotation').replace('warehouse', 'warehouse').replace('purchases', 'purchase').replace('plans', 'plan').replace('accessories', 'accessory')}Count` as keyof Stats;
            return (
              <div
                key={tab.id}
                onClick={() => { setActiveTab(tab.id); setKeyword(''); }}
                className={`cursor-pointer rounded-2xl p-4 border transition-all duration-200 ${
                  activeTab === tab.id
                    ? 'bg-gradient-to-r ' + tab.color + ' text-white shadow-lg scale-[1.02]'
                    : 'bg-white border-slate-200 hover:shadow-md hover:-translate-y-0.5'
                }`}
              >
                <div className="flex items-center gap-3">
                  <tab.icon className={`w-5 h-5 ${activeTab === tab.id ? 'text-white' : 'text-slate-400'}`} />
                  <div>
                    <div className={`text-xs ${activeTab === tab.id ? 'text-white/80' : 'text-slate-500'}`}>{tab.label}</div>
                    <div className="text-2xl font-bold">{stats?.[countKey] ?? 0}</div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* 工具栏 */}
      <div className="px-8 pb-4">
        <div className="flex items-center gap-3">
          {/* 搜索 */}
          <div className="flex-1 relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
            <input
              type="text"
              value={keyword}
              onChange={e => setKeyword(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && fetchData(1)}
              placeholder={`搜索${currentTab.label}...`}
              className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-slate-200 bg-white focus:outline-none focus:ring-2 focus:ring-violet-500/20 focus:border-violet-400 text-sm"
            />
          </div>
          {/* 操作按钮 */}
          <button
            onClick={() => { setEditItem(null); setFormData({}); setShowAddForm(true); }}
            className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-gradient-to-r from-violet-500 to-purple-600 text-white text-sm font-medium hover:shadow-lg transition-all"
          >
            <Plus className="w-4 h-4" /> 新增
          </button>
          <label className="flex items-center gap-2 px-4 py-2.5 rounded-xl border border-slate-200 bg-white text-sm font-medium text-slate-700 hover:bg-slate-50 cursor-pointer transition-all">
            <Upload className="w-4 h-4" /> 导入Excel
            <input type="file" accept=".xlsx,.xls" className="hidden" onChange={handleFileUpload} />
          </label>
          <button
            onClick={() => fetchData(data.page)}
            className="p-2.5 rounded-xl border border-slate-200 bg-white hover:bg-slate-50 transition-all"
          >
            <RefreshCw className={`w-4 h-4 text-slate-500 ${loading ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      {/* 数据表格 */}
      <div className="px-8 pb-8">
        <div className="bg-white rounded-2xl shadow-sm border border-slate-200 overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="bg-gradient-to-r from-slate-50 to-slate-100/50">
                  {getColumns().map(col => (
                    <th key={col.key} className="px-4 py-3 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider whitespace-nowrap">
                      {col.label}
                    </th>
                  ))}
                  <th className="px-4 py-3 text-right text-xs font-semibold text-slate-600 uppercase tracking-wider w-24">操作</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {loading ? (
                  <tr><td colSpan={100} className="px-4 py-12 text-center text-slate-400">
                    <RefreshCw className="w-6 h-6 animate-spin mx-auto mb-2" />加载中...
                  </td></tr>
                ) : data.items.length === 0 ? (
                  <tr><td colSpan={100} className="px-4 py-12 text-center text-slate-400">
                    <FileSpreadsheet className="w-10 h-10 mx-auto mb-2 text-slate-300" />暂无数据
                  </td></tr>
                ) : (
                  data.items.map((item: any, idx: number) => (
                    <tr key={item.id || idx} className="hover:bg-violet-50/30 transition-colors">
                      {getColumns().map(col => (
                        <td key={col.key} className="px-4 py-3 text-sm text-slate-700 whitespace-nowrap max-w-[200px] truncate">
                          {col.key.toLowerCase().includes('price') || col.key.toLowerCase().includes('usage') || col.key.toLowerCase().includes('weight')
                            ? <span className="font-mono text-violet-600">{item[col.key] ?? '-'}</span>
                            : item[col.key] ?? '-'}
                        </td>
                      ))}
                      <td className="px-4 py-3 text-right whitespace-nowrap">
                        <button onClick={() => handleEdit(item)} className="p-1.5 rounded-lg hover:bg-blue-50 text-blue-500 transition-colors">
                          <Edit2 className="w-3.5 h-3.5" />
                        </button>
                        <button onClick={() => handleDelete(item.id)} className="p-1.5 rounded-lg hover:bg-red-50 text-red-500 transition-colors ml-1">
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
          {/* 分页 */}
          {data.totalPages > 1 && (
            <div className="flex items-center justify-between px-4 py-3 border-t border-slate-100 bg-slate-50/50">
              <span className="text-sm text-slate-500">共 {data.total} 条</span>
              <div className="flex items-center gap-1">
                <button
                  onClick={() => fetchData(Math.max(1, data.page - 1))}
                  disabled={data.page <= 1}
                  className="p-1.5 rounded-lg border border-slate-200 bg-white disabled:opacity-40 hover:bg-slate-50 transition-colors"
                >
                  <ChevronLeft className="w-4 h-4" />
                </button>
                <span className="px-3 text-sm text-slate-600">{data.page} / {data.totalPages}</span>
                <button
                  onClick={() => fetchData(Math.min(data.totalPages, data.page + 1))}
                  disabled={data.page >= data.totalPages}
                  className="p-1.5 rounded-lg border border-slate-200 bg-white disabled:opacity-40 hover:bg-slate-50 transition-colors"
                >
                  <ChevronLeft className="w-4 h-4 rotate-180" />
                </button>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* 新增/编辑弹窗 */}
      {showAddForm && (
        <div className="fixed inset-0 z-50 bg-black/40 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl max-h-[80vh] overflow-hidden">
            <div className={`bg-gradient-to-r ${currentTab.color} px-6 py-4`}>
              <h3 className="text-lg font-bold text-white">{editItem ? '编辑' : '新增'}{currentTab.label}</h3>
            </div>
            <div className="p-6 overflow-y-auto max-h-[60vh]">
              <div className="grid grid-cols-2 gap-4">
                {getFormFields().map(field => (
                  <div key={field.key}>
                    <label className="block text-xs font-medium text-slate-600 mb-1">{field.label}</label>
                    <input
                      type={field.type || 'text'}
                      value={formData[field.key] ?? ''}
                      onChange={e => setFormData(prev => ({ ...prev, [field.key]: field.type === 'number' ? (e.target.value ? Number(e.target.value) : '') : e.target.value }))}
                      className="w-full px-3 py-2 rounded-lg border border-slate-200 focus:outline-none focus:ring-2 focus:ring-violet-500/20 focus:border-violet-400 text-sm"
                      step={field.type === 'number' ? '0.0001' : undefined}
                    />
                  </div>
                ))}
              </div>
            </div>
            <div className="flex justify-end gap-3 px-6 py-4 border-t border-slate-100 bg-slate-50/50">
              <button onClick={() => { setShowAddForm(false); setEditItem(null); setFormData({}); }}
                className="px-4 py-2 rounded-xl border border-slate-200 text-sm text-slate-600 hover:bg-slate-100 transition-colors">
                取消
              </button>
              <button onClick={handleSave}
                className={`px-6 py-2 rounded-xl bg-gradient-to-r ${currentTab.color} text-white text-sm font-medium hover:shadow-lg transition-all`}>
                保存
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
