'use client';

import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { backendFetch } from '@/lib/backend-proxy';
import {
  ArrowLeft, Package, TrendingUp, DollarSign, Factory, BarChart3,
  Search, Upload, Download, Plus, Edit3, Trash2, ChevronLeft,
  ChevronRight, RefreshCw, Layers, Calculator, Target, Zap,
  ChevronDown, X, Check, AlertCircle, Info
} from 'lucide-react';

// ============ 类型定义 ============
interface CostBreakdown {
  materialCost: number;
  accessoryCost: number;
  processingCost: number;
  totalCost: number;
  details: {
    name: string;
    usage: number;
    unitPrice: number;
    subtotal: number;
    source: string;
  }[];
}

interface SmartQuote {
  productCode: string;
  productionCode: string;
  customer: string;
  costBreakdown: CostBreakdown;
  suggestedPrice: number;
  profitRate: number;
  profitAmount: number;
  machineType: string;
  singleMachineOutput: number;
  dailyCapacity: number;
}

interface SupplierComparison {
  materialCode: string;
  suppliers: {
    supplier: string;
    batchNo: string;
    unitPrice: number;
    priceRank: number;
  }[];
  bestSupplier: string;
  bestPrice: number;
  savings: number;
}

interface ProductSpec {
  productCode: string;
  productionCode: string;
  customer: string;
  period: string;
  salesperson: string;
  approvalStatus: string;
}

// ============ 格式化工具 ============
const fmt = (n: number) => n.toFixed(4);
const fmtY = (n: number) => `¥${n.toFixed(4)}`;
const fmtP = (n: number) => `${(n * 100).toFixed(1)}%`;

export default function SupplyChainPage() {
  const router = useRouter();
  const [activeTab, setActiveTab] = useState('dashboard');
  const [loading, setLoading] = useState(true);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // 数据状态
  const [smartQuotes, setSmartQuotes] = useState<SmartQuote[]>([]);
  const [supplierComparisons, setSupplierComparisons] = useState<SupplierComparison[]>([]);
  const [productSpecs, setProductSpecs] = useState<ProductSpec[]>([]);
  const [targetProfitRate, setTargetProfitRate] = useState(0.3);
  const [processingCostPerUnit, setProcessingCostPerUnit] = useState(0.05);
  const [searchKeyword, setSearchKeyword] = useState('');

  // 供应链数据
  const [quotations, setQuotations] = useState<any[]>([]);
  const [warehouse, setWarehouse] = useState<any[]>([]);
  const [purchases, setPurchases] = useState<any[]>([]);
  const [plans, setPlans] = useState<any[]>([]);
  const [accessories, setAccessories] = useState<any[]>([]);

  // 分页
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [totalItems, setTotalItems] = useState(0);
  const pageSize = 20;

  // 登录检查 + pageshow
  useEffect(() => {
    const sessionId = typeof window !== 'undefined' ? localStorage.getItem('session_id') : null;
    const sessionExpires = typeof window !== 'undefined' ? localStorage.getItem('session_expires') : null;
    if (!sessionId || (sessionExpires && Number(sessionExpires) < Date.now())) {
      window.location.href = '/login';
      return;
    }

    const handlePageShow = (e: PageTransitionEvent) => {
      if (e.persisted) {
        const sid = localStorage.getItem('session_id');
        const expires = localStorage.getItem('session_expires');
        if (!sid || (expires && Number(expires) < Date.now())) {
          window.location.href = '/login';
        }
      }
    };
    window.addEventListener('pageshow', handlePageShow);
    return () => window.removeEventListener('pageshow', handlePageShow);
  }, []);

  // 加载智能报价数据
  const fetchSmartQuotes = useCallback(async () => {
    try {
      const res = await backendFetch('/supply-chain/smart-quotes?profitRate=' + targetProfitRate + '&processingCost=' + processingCostPerUnit);
      const data = await res.json();
      if (data.success) setSmartQuotes(data.data || []);
    } catch (e) { console.error('获取智能报价失败', e); }
  }, [targetProfitRate, processingCostPerUnit]);

  // 加载供应商对比
  const fetchSupplierComparisons = useCallback(async () => {
    try {
      const res = await backendFetch('/supply-chain/supplier-comparison');
      const data = await res.json();
      if (data.success) setSupplierComparisons(data.data || []);
    } catch (e) { console.error('获取供应商对比失败', e); }
  }, []);

  // 加载产品规格
  const fetchProductSpecs = useCallback(async () => {
    try {
      const res = await backendFetch('/supply-chain/quotations?page=1&pageSize=100');
      const data = await res.json();
      if (data.success) {
        setProductSpecs((data.data?.content || []).map((q: any) => ({
          productCode: q.productCode,
          productionCode: q.productionCode,
          customer: q.customer,
          period: q.period,
          salesperson: q.salesperson,
          approvalStatus: q.approvalStatus,
        })));
      }
    } catch (e) { console.error('获取产品规格失败', e); }
  }, []);

  // 加载各模块分页数据
  const fetchModuleData = useCallback(async (module: string, page: number) => {
    try {
      const kw = searchKeyword ? `&keyword=${encodeURIComponent(searchKeyword)}` : '';
      const res = await backendFetch(`/supply-chain/${module}?page=${page}&pageSize=${pageSize}${kw}`);
      const data = await res.json();
      if (data.success) {
        const content = data.data?.content || [];
        const total = data.data?.totalElements || 0;
        setTotalItems(total);
        setTotalPages(Math.ceil(total / pageSize) || 1);
        switch (module) {
          case 'quotations': setQuotations(content); break;
          case 'warehouse': setWarehouse(content); break;
          case 'purchases': setPurchases(content); break;
          case 'plans': setPlans(content); break;
          case 'accessories': setAccessories(content); break;
        }
      }
    } catch (e) { console.error('获取数据失败', e); }
  }, [searchKeyword]);

  // 初始化加载
  useEffect(() => {
    const init = async () => {
      setLoading(true);
      await Promise.all([fetchSmartQuotes(), fetchSupplierComparisons(), fetchProductSpecs()]);
      setLoading(false);
    };
    init();
  }, [fetchSmartQuotes, fetchSupplierComparisons, fetchProductSpecs]);

  // 参数变化时重新加载智能报价
  useEffect(() => {
    fetchSmartQuotes();
  }, [targetProfitRate, processingCostPerUnit]);

  // Tab切换时加载数据
  useEffect(() => {
    const moduleMap: Record<string, string> = {
      'quotations': 'quotations',
      'warehouse': 'warehouse',
      'purchases': 'purchases',
      'plans': 'plans',
      'accessories': 'accessories',
    };
    if (moduleMap[activeTab]) {
      fetchModuleData(moduleMap[activeTab], 1);
      setCurrentPage(1);
    }
  }, [activeTab, fetchModuleData]);

  // Excel上传
  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const moduleMap: Record<string, string> = {
      'quotations': 'quotation',
      'warehouse': 'warehouse',
      'purchases': 'purchase',
      'plans': 'plan',
      'accessories': 'accessory',
    };
    const type = moduleMap[activeTab] || 'quotation';
    const formData = new FormData();
    formData.append('file', file);
    try {
      const res = await backendFetch(`/supply-chain/import?type=${type}`, {
        method: 'POST',
        body: formData,
      });
      const data = await res.json();
      if (data.success) {
        alert(`成功导入 ${data.data?.count || 0} 条数据`);
        fetchModuleData(activeTab, currentPage);
      } else {
        alert('导入失败: ' + (data.error || '未知错误'));
      }
    } catch (e) {
      alert('导入失败');
    }
    e.target.value = '';
  };

  // 删除记录
  const handleDelete = async (module: string, id: number) => {
    if (!confirm('确认删除此条记录？')) return;
    try {
      const res = await backendFetch(`/supply-chain/${module}/${id}`, { method: 'DELETE' });
      const data = await res.json();
      if (data.success) {
        fetchModuleData(module, currentPage);
      }
    } catch (e) { alert('删除失败'); }
  };

  // Tab配置
  const tabs = [
    { id: 'dashboard', label: '智能仪表盘', icon: BarChart3 },
    { id: 'smart-quote', label: '智能报价', icon: Calculator },
    { id: 'cost-analysis', label: '成本分析', icon: Target },
    { id: 'supplier', label: '供应商对比', icon: TrendingUp },
    { id: 'quotations', label: '产品报价', icon: Package },
    { id: 'warehouse', label: '原料入库', icon: Layers },
    { id: 'purchases', label: '原料采购', icon: DollarSign },
    { id: 'plans', label: '生产计划', icon: Factory },
    { id: 'accessories', label: '辅料采购', icon: Zap },
  ];

  // ============ 仪表盘统计 ============
  const dashboardStats = [
    { label: '产品报价单', value: smartQuotes.length, icon: Package, color: 'from-violet-500 to-purple-600' },
    { label: '原料种类', value: new Set(purchases.map(p => p.materialCode)).size || supplierComparisons.length, icon: Layers, color: 'from-blue-500 to-cyan-600' },
    { label: '供应商数量', value: new Set([...purchases.map(p => p.supplier), ...accessories.map(a => a.supplier)]).size, icon: TrendingUp, color: 'from-amber-500 to-orange-600' },
    { label: '平均利润率', value: smartQuotes.length ? fmtP(smartQuotes.reduce((s, q) => s + q.profitRate, 0) / smartQuotes.length) : '-', icon: Target, color: 'from-emerald-500 to-green-600' },
  ];

  // ============ 渲染 ============
  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100">
      {/* 顶部导航 */}
      <header className="sticky top-0 z-50 bg-white/80 backdrop-blur-xl border-b border-slate-200/60 shadow-sm">
        <div className="max-w-[1800px] mx-auto px-6 h-16 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <button onClick={() => window.location.href = '/login'} className="p-2 hover:bg-slate-100 rounded-xl transition-colors">
              <ArrowLeft className="w-5 h-5 text-slate-600" />
            </button>
            <div className="h-6 w-px bg-slate-200" />
            <Factory className="w-6 h-6 text-amber-600" />
            <h1 className="text-lg font-bold bg-gradient-to-r from-amber-600 to-orange-600 bg-clip-text text-transparent">
              盈云产品智能中台 · 供应链管理
            </h1>
          </div>
          <div className="flex items-center gap-3">
            <span className="text-sm text-slate-500">目标利润率</span>
            <input
              type="number" min={0} max={1} step={0.05}
              value={targetProfitRate}
              onChange={e => setTargetProfitRate(Number(e.target.value))}
              className="w-20 px-2 py-1 text-sm border rounded-lg text-center"
            />
            <span className="text-sm text-slate-500">加工费/条</span>
            <input
              type="number" min={0} step={0.01}
              value={processingCostPerUnit}
              onChange={e => setProcessingCostPerUnit(Number(e.target.value))}
              className="w-24 px-2 py-1 text-sm border rounded-lg text-center"
            />
          </div>
        </div>
      </header>

      <div className="max-w-[1800px] mx-auto px-6 py-6 flex gap-6">
        {/* 左侧Tab栏 */}
        <nav className="w-52 flex-shrink-0">
          <div className="bg-white rounded-2xl shadow-sm border border-slate-200/60 p-2 sticky top-24">
            {tabs.map(tab => {
              const Icon = tab.icon;
              const isActive = activeTab === tab.id;
              return (
                <button
                  key={tab.id}
                  onClick={() => setActiveTab(tab.id)}
                  className={`w-full flex items-center gap-2.5 px-3 py-2.5 rounded-xl text-sm font-medium transition-all duration-200 mb-0.5 ${
                    isActive
                      ? 'bg-gradient-to-r from-amber-500 to-orange-500 text-white shadow-md shadow-amber-200'
                      : 'text-slate-600 hover:bg-slate-50'
                  }`}
                >
                  <Icon className="w-4 h-4" />
                  {tab.label}
                </button>
              );
            })}
          </div>
        </nav>

        {/* 主内容区 */}
        <main className="flex-1 min-w-0">
          {loading ? (
            <div className="flex items-center justify-center h-64">
              <RefreshCw className="w-8 h-8 text-amber-500 animate-spin" />
            </div>
          ) : (
            <>
              {/* ===== 智能仪表盘 ===== */}
              {activeTab === 'dashboard' && (
                <div className="space-y-6">
                  {/* 统计卡片 */}
                  <div className="grid grid-cols-4 gap-4">
                    {dashboardStats.map((stat, i) => {
                      const Icon = stat.icon;
                      return (
                        <div key={i} className="bg-white rounded-2xl p-5 shadow-sm border border-slate-200/60">
                          <div className="flex items-center justify-between mb-3">
                            <span className="text-sm text-slate-500">{stat.label}</span>
                            <div className={`w-9 h-9 rounded-xl bg-gradient-to-br ${stat.color} flex items-center justify-center`}>
                              <Icon className="w-4 h-4 text-white" />
                            </div>
                          </div>
                          <div className="text-2xl font-bold text-slate-800">{stat.value}</div>
                        </div>
                      );
                    })}
                  </div>

                  {/* 报价概览 */}
                  <div className="bg-white rounded-2xl shadow-sm border border-slate-200/60 p-6">
                    <h2 className="text-lg font-bold text-slate-800 mb-4 flex items-center gap-2">
                      <Calculator className="w-5 h-5 text-amber-500" />
                      产品智能报价概览
                    </h2>
                    <div className="overflow-x-auto">
                      <table className="w-full text-sm">
                        <thead>
                          <tr className="border-b border-slate-100">
                            <th className="text-left py-3 px-3 text-slate-500 font-medium">产品编码</th>
                            <th className="text-left py-3 px-3 text-slate-500 font-medium">客户</th>
                            <th className="text-right py-3 px-3 text-slate-500 font-medium">原料成本</th>
                            <th className="text-right py-3 px-3 text-slate-500 font-medium">辅料成本</th>
                            <th className="text-right py-3 px-3 text-slate-500 font-medium">加工费</th>
                            <th className="text-right py-3 px-3 text-slate-500 font-medium">总成本</th>
                            <th className="text-right py-3 px-3 text-slate-500 font-medium">建议报价</th>
                            <th className="text-right py-3 px-3 text-slate-500 font-medium">利润率</th>
                            <th className="text-right py-3 px-3 text-slate-500 font-medium">日产能</th>
                          </tr>
                        </thead>
                        <tbody>
                          {smartQuotes.map((q, i) => (
                            <tr key={i} className="border-b border-slate-50 hover:bg-amber-50/30 transition-colors">
                              <td className="py-3 px-3 font-semibold text-slate-800">{q.productCode}</td>
                              <td className="py-3 px-3 text-slate-600">{q.customer}</td>
                              <td className="py-3 px-3 text-right text-slate-600">{fmtY(q.costBreakdown.materialCost)}</td>
                              <td className="py-3 px-3 text-right text-slate-600">{fmtY(q.costBreakdown.accessoryCost)}</td>
                              <td className="py-3 px-3 text-right text-slate-600">{fmtY(q.costBreakdown.processingCost)}</td>
                              <td className="py-3 px-3 text-right font-bold text-slate-800">{fmtY(q.costBreakdown.totalCost)}</td>
                              <td className="py-3 px-3 text-right font-bold text-amber-600">{fmtY(q.suggestedPrice)}</td>
                              <td className="py-3 px-3 text-right">
                                <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${
                                  q.profitRate >= targetProfitRate
                                    ? 'bg-emerald-50 text-emerald-700'
                                    : 'bg-red-50 text-red-700'
                                }`}>
                                  {fmtP(q.profitRate)}
                                </span>
                              </td>
                              <td className="py-3 px-3 text-right text-slate-600">{q.dailyCapacity} 条/天</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>

                  {/* 供应商推荐 */}
                  <div className="bg-white rounded-2xl shadow-sm border border-slate-200/60 p-6">
                    <h2 className="text-lg font-bold text-slate-800 mb-4 flex items-center gap-2">
                      <TrendingUp className="w-5 h-5 text-blue-500" />
                      供应商最优推荐
                    </h2>
                    <div className="grid grid-cols-2 gap-4">
                      {supplierComparisons.map((sc, i) => (
                        <div key={i} className="border border-slate-200 rounded-xl p-4 hover:shadow-md transition-shadow">
                          <div className="flex items-center justify-between mb-3">
                            <span className="font-semibold text-slate-800">{sc.materialCode}</span>
                            <span className="px-2 py-0.5 bg-emerald-50 text-emerald-700 rounded-full text-xs font-medium">
                              节省 {fmtY(sc.savings)}/单位
                            </span>
                          </div>
                          <div className="space-y-1.5">
                            {sc.suppliers.map((s, j) => (
                              <div key={j} className={`flex items-center justify-between text-sm px-2 py-1.5 rounded-lg ${
                                s.supplier === sc.bestSupplier ? 'bg-amber-50 border border-amber-200' : ''
                              }`}>
                                <div className="flex items-center gap-2">
                                  {s.supplier === sc.bestSupplier && <Check className="w-3.5 h-3.5 text-amber-600" />}
                                  <span className={s.supplier === sc.bestSupplier ? 'font-medium text-slate-800' : 'text-slate-500'}>
                                    {s.supplier}
                                  </span>
                                </div>
                                <div className="flex items-center gap-2">
                                  <span className="text-slate-600">批次: {s.batchNo}</span>
                                  <span className={`font-medium ${s.priceRank === 1 ? 'text-emerald-600' : 'text-slate-600'}`}>
                                    ¥{fmt(s.unitPrice)}
                                  </span>
                                </div>
                              </div>
                            ))}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              )}

              {/* ===== 智能报价 ===== */}
              {activeTab === 'smart-quote' && (
                <div className="space-y-6">
                  <div className="bg-gradient-to-r from-amber-500 to-orange-500 rounded-2xl p-6 text-white">
                    <h2 className="text-xl font-bold mb-1">智能报价引擎</h2>
                    <p className="text-amber-100 text-sm">基于原料采购价、辅料成本和生产计划，自动计算最优报价</p>
                    <div className="flex items-center gap-6 mt-4">
                      <div>
                        <div className="text-amber-200 text-xs">当前利润率目标</div>
                        <div className="text-2xl font-bold">{fmtP(targetProfitRate)}</div>
                      </div>
                      <div>
                        <div className="text-amber-200 text-xs">加工费/条</div>
                        <div className="text-2xl font-bold">¥{processingCostPerUnit}</div>
                      </div>
                      <div>
                        <div className="text-amber-200 text-xs">产品数量</div>
                        <div className="text-2xl font-bold">{smartQuotes.length}</div>
                      </div>
                    </div>
                  </div>

                  {smartQuotes.map((q, i) => (
                    <div key={i} className="bg-white rounded-2xl shadow-sm border border-slate-200/60 p-6">
                      <div className="flex items-center justify-between mb-4">
                        <div>
                          <h3 className="text-lg font-bold text-slate-800">{q.productCode} <span className="text-slate-400 font-normal">({q.productionCode})</span></h3>
                          <p className="text-sm text-slate-500">客户: {q.customer} | 机型: {q.machineType}</p>
                        </div>
                        <div className="text-right">
                          <div className="text-sm text-slate-500">建议报价</div>
                          <div className="text-2xl font-bold text-amber-600">¥{q.suggestedPrice.toFixed(4)}</div>
                        </div>
                      </div>

                      <div className="grid grid-cols-3 gap-4 mb-4">
                        <div className="bg-blue-50 rounded-xl p-3">
                          <div className="text-xs text-blue-600 mb-1">原料成本</div>
                          <div className="text-lg font-bold text-blue-800">{fmtY(q.costBreakdown.materialCost)}</div>
                        </div>
                        <div className="bg-purple-50 rounded-xl p-3">
                          <div className="text-xs text-purple-600 mb-1">辅料成本</div>
                          <div className="text-lg font-bold text-purple-800">{fmtY(q.costBreakdown.accessoryCost)}</div>
                        </div>
                        <div className="bg-emerald-50 rounded-xl p-3">
                          <div className="text-xs text-emerald-600 mb-1">加工费</div>
                          <div className="text-lg font-bold text-emerald-800">{fmtY(q.costBreakdown.processingCost)}</div>
                        </div>
                      </div>

                      <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                          <thead>
                            <tr className="border-b border-slate-100">
                              <th className="text-left py-2 px-2 text-slate-500 font-medium">原料名称</th>
                              <th className="text-right py-2 px-2 text-slate-500 font-medium">用量</th>
                              <th className="text-right py-2 px-2 text-slate-500 font-medium">单价</th>
                              <th className="text-right py-2 px-2 text-slate-500 font-medium">小计</th>
                              <th className="text-left py-2 px-2 text-slate-500 font-medium">供应商</th>
                            </tr>
                          </thead>
                          <tbody>
                            {q.costBreakdown.details.map((d, j) => (
                              <tr key={j} className="border-b border-slate-50">
                                <td className="py-2 px-2 text-slate-700">{d.name}</td>
                                <td className="py-2 px-2 text-right text-slate-600">{d.usage}</td>
                                <td className="py-2 px-2 text-right text-slate-600">¥{fmt(d.unitPrice)}</td>
                                <td className="py-2 px-2 text-right font-medium text-slate-800">¥{fmt(d.subtotal)}</td>
                                <td className="py-2 px-2 text-slate-500">{d.source}</td>
                              </tr>
                            ))}
                          </tbody>
                          <tfoot>
                            <tr className="bg-slate-50 font-bold">
                              <td className="py-2 px-2 text-slate-800">合计</td>
                              <td /><td /><td />
                              <td className="py-2 px-2 text-right text-amber-600 text-base">{fmtY(q.costBreakdown.totalCost)}</td>
                              <td />
                            </tr>
                          </tfoot>
                        </table>
                      </div>

                      <div className="flex items-center justify-between mt-4 pt-4 border-t border-slate-100">
                        <div className="flex items-center gap-4">
                          <span className="text-sm text-slate-500">利润率: <span className={`font-bold ${q.profitRate >= targetProfitRate ? 'text-emerald-600' : 'text-red-600'}`}>{fmtP(q.profitRate)}</span></span>
                          <span className="text-sm text-slate-500">利润额: <span className="font-bold text-slate-800">{fmtY(q.profitAmount)}</span></span>
                          <span className="text-sm text-slate-500">日产能: <span className="font-bold text-slate-800">{q.dailyCapacity} 条</span></span>
                        </div>
                        <button className="px-4 py-2 bg-gradient-to-r from-amber-500 to-orange-500 text-white rounded-xl text-sm font-medium hover:shadow-lg transition-shadow">
                          生成报价单
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {/* ===== 成本分析 ===== */}
              {activeTab === 'cost-analysis' && (
                <div className="space-y-6">
                  <div className="bg-white rounded-2xl shadow-sm border border-slate-200/60 p-6">
                    <h2 className="text-lg font-bold text-slate-800 mb-4">各产品成本构成对比</h2>
                    <div className="space-y-4">
                      {smartQuotes.map((q, i) => {
                        const total = q.costBreakdown.totalCost;
                        const matPct = total > 0 ? (q.costBreakdown.materialCost / total * 100) : 0;
                        const accPct = total > 0 ? (q.costBreakdown.accessoryCost / total * 100) : 0;
                        const proPct = total > 0 ? (q.costBreakdown.processingCost / total * 100) : 0;
                        return (
                          <div key={i} className="border border-slate-200 rounded-xl p-4">
                            <div className="flex items-center justify-between mb-2">
                              <span className="font-semibold text-slate-800">{q.productCode}</span>
                              <span className="text-sm text-slate-500">总成本 {fmtY(total)}</span>
                            </div>
                            <div className="flex h-8 rounded-lg overflow-hidden">
                              <div className="bg-blue-500 flex items-center justify-center text-white text-xs font-medium" style={{width: `${matPct}%`}}>
                                {matPct >= 10 ? `原料 ${matPct.toFixed(0)}%` : ''}
                              </div>
                              <div className="bg-purple-500 flex items-center justify-center text-white text-xs font-medium" style={{width: `${accPct}%`}}>
                                {accPct >= 10 ? `辅料 ${accPct.toFixed(0)}%` : ''}
                              </div>
                              <div className="bg-emerald-500 flex items-center justify-center text-white text-xs font-medium" style={{width: `${proPct}%`}}>
                                {proPct >= 10 ? `加工 ${proPct.toFixed(0)}%` : ''}
                              </div>
                            </div>
                            <div className="flex items-center gap-4 mt-2 text-xs text-slate-500">
                              <span className="flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-blue-500" /> 原料 {fmtY(q.costBreakdown.materialCost)}</span>
                              <span className="flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-purple-500" /> 辅料 {fmtY(q.costBreakdown.accessoryCost)}</span>
                              <span className="flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-emerald-500" /> 加工 {fmtY(q.costBreakdown.processingCost)}</span>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>

                  {/* 利润率雷达 */}
                  <div className="bg-white rounded-2xl shadow-sm border border-slate-200/60 p-6">
                    <h2 className="text-lg font-bold text-slate-800 mb-4">利润率与目标对比</h2>
                    <div className="space-y-3">
                      {smartQuotes.map((q, i) => (
                        <div key={i} className="flex items-center gap-4">
                          <span className="w-24 text-sm font-medium text-slate-700">{q.productCode}</span>
                          <div className="flex-1 bg-slate-100 rounded-full h-6 relative overflow-hidden">
                            <div
                              className={`h-full rounded-full transition-all duration-500 ${
                                q.profitRate >= targetProfitRate ? 'bg-gradient-to-r from-emerald-400 to-emerald-500' : 'bg-gradient-to-r from-red-400 to-red-500'
                              }`}
                              style={{width: `${Math.min(q.profitRate / 0.5 * 100, 100)}%`}}
                            />
                            <div className="absolute top-0 left-0 h-full border-r-2 border-dashed border-amber-500" style={{width: `${targetProfitRate / 0.5 * 100}%`}} />
                          </div>
                          <span className={`text-sm font-bold w-16 text-right ${q.profitRate >= targetProfitRate ? 'text-emerald-600' : 'text-red-600'}`}>
                            {fmtP(q.profitRate)}
                          </span>
                        </div>
                      ))}
                      <div className="flex items-center gap-2 text-xs text-slate-400 mt-2">
                        <div className="w-3 h-0.5 border-t-2 border-dashed border-amber-500" /> 目标线 ({fmtP(targetProfitRate)})
                      </div>
                    </div>
                  </div>
                </div>
              )}

              {/* ===== 供应商对比 ===== */}
              {activeTab === 'supplier' && (
                <div className="space-y-6">
                  {supplierComparisons.map((sc, i) => (
                    <div key={i} className="bg-white rounded-2xl shadow-sm border border-slate-200/60 p-6">
                      <div className="flex items-center justify-between mb-4">
                        <h3 className="text-lg font-bold text-slate-800">{sc.materialCode}</h3>
                        <div className="flex items-center gap-2">
                          <span className="px-3 py-1 bg-emerald-50 text-emerald-700 rounded-lg text-sm font-medium">
                            最优: {sc.bestSupplier} ¥{fmt(sc.bestPrice)}
                          </span>
                          <span className="px-3 py-1 bg-blue-50 text-blue-700 rounded-lg text-sm font-medium">
                            可节省 {fmtY(sc.savings)}/单位
                          </span>
                        </div>
                      </div>
                      <div className="grid grid-cols-3 gap-4">
                        {sc.suppliers.map((s, j) => (
                          <div key={j} className={`border rounded-xl p-4 ${s.supplier === sc.bestSupplier ? 'border-amber-300 bg-amber-50/50' : 'border-slate-200'}`}>
                            <div className="flex items-center justify-between mb-2">
                              <span className="font-medium text-slate-800">{s.supplier}</span>
                              {s.priceRank === 1 && <span className="text-xs px-2 py-0.5 bg-amber-100 text-amber-700 rounded-full">最优</span>}
                            </div>
                            <div className="text-2xl font-bold text-slate-800 mb-1">¥{fmt(s.unitPrice)}</div>
                            <div className="text-xs text-slate-500">批次: {s.batchNo} | 排名: 第{s.priceRank}</div>
                            <div className="mt-2 bg-slate-100 rounded-full h-2 overflow-hidden">
                              <div
                                className="h-full bg-gradient-to-r from-amber-400 to-orange-500 rounded-full"
                                style={{width: `${(sc.bestPrice / s.unitPrice) * 100}%`}}
                              />
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {/* ===== 通用数据表格 ===== */}
              {['quotations', 'warehouse', 'purchases', 'plans', 'accessories'].includes(activeTab) && (
                <div className="space-y-4">
                  {/* 工具栏 */}
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <div className="relative">
                        <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                        <input
                          type="text"
                          placeholder="搜索..."
                          value={searchKeyword}
                          onChange={e => setSearchKeyword(e.target.value)}
                          onKeyDown={e => e.key === 'Enter' && fetchModuleData(activeTab, 1)}
                          className="pl-9 pr-4 py-2 border border-slate-200 rounded-xl text-sm w-64 focus:outline-none focus:ring-2 focus:ring-amber-300"
                        />
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => fileInputRef.current?.click()}
                        className="flex items-center gap-1.5 px-4 py-2 bg-gradient-to-r from-amber-500 to-orange-500 text-white rounded-xl text-sm font-medium hover:shadow-lg transition-shadow"
                      >
                        <Upload className="w-4 h-4" /> 导入Excel
                      </button>
                      <input ref={fileInputRef} type="file" accept=".xlsx,.xls" onChange={handleFileUpload} className="hidden" />
                    </div>
                  </div>

                  {/* 数据表 */}
                  <div className="bg-white rounded-2xl shadow-sm border border-slate-200/60 overflow-hidden">
                    <div className="overflow-x-auto">
                      <DataTable
                        module={activeTab}
                        data={
                          activeTab === 'quotations' ? quotations :
                          activeTab === 'warehouse' ? warehouse :
                          activeTab === 'purchases' ? purchases :
                          activeTab === 'plans' ? plans : accessories
                        }
                        onDelete={(id) => handleDelete(activeTab, id)}
                      />
                    </div>
                    {/* 分页 */}
                    <div className="flex items-center justify-between px-6 py-4 border-t border-slate-100">
                      <span className="text-sm text-slate-500">共 {totalItems} 条</span>
                      <div className="flex items-center gap-2">
                        <button
                          onClick={() => { setCurrentPage(Math.max(1, currentPage - 1)); fetchModuleData(activeTab, Math.max(1, currentPage - 1)); }}
                          disabled={currentPage <= 1}
                          className="p-1.5 rounded-lg hover:bg-slate-100 disabled:opacity-30"
                        >
                          <ChevronLeft className="w-4 h-4" />
                        </button>
                        <span className="text-sm text-slate-600">{currentPage} / {totalPages}</span>
                        <button
                          onClick={() => { setCurrentPage(Math.min(totalPages, currentPage + 1)); fetchModuleData(activeTab, Math.min(totalPages, currentPage + 1)); }}
                          disabled={currentPage >= totalPages}
                          className="p-1.5 rounded-lg hover:bg-slate-100 disabled:opacity-30"
                        >
                          <ChevronRight className="w-4 h-4" />
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              )}
            </>
          )}
        </main>
      </div>
    </div>
  );
}

// ============ 数据表格组件 ============
function DataTable({ module, data, onDelete }: { module: string; data: any[]; onDelete: (id: number) => void }) {
  const columns: Record<string, { key: string; label: string }[]> = {
    quotations: [
      { key: 'productCode', label: '产品编码' },
      { key: 'productionCode', label: '生产编码' },
      { key: 'customer', label: '客户' },
      { key: 'salesperson', label: '业务员' },
      { key: 'approvalStatus', label: '审批状态' },
      { key: 'rawMaterialName1', label: '原料1' },
      { key: 'materialUsage1', label: '用量1' },
      { key: 'materialUnitPrice1', label: '单价1' },
      { key: 'rawMaterialName2', label: '原料2' },
      { key: 'materialUsage2', label: '用量2' },
      { key: 'accessoryName', label: '辅料' },
      { key: 'accessoryPrice', label: '辅料价' },
    ],
    warehouse: [
      { key: 'productCode', label: '原料编码' },
      { key: 'color', label: '颜色' },
      { key: 'batchNo', label: '批次号' },
      { key: 'unit', label: '单位' },
      { key: 'unitPrice', label: '单价' },
    ],
    purchases: [
      { key: 'materialCode', label: '原料编码' },
      { key: 'unit', label: '单位' },
      { key: 'supplier', label: '供应商' },
      { key: 'batchNo', label: '批次号' },
      { key: 'unitPrice', label: '单价' },
    ],
    plans: [
      { key: 'semiProductCode', label: '半成品编码' },
      { key: 'productCode', label: '产品编码' },
      { key: 'sewingWeight', label: '缝头重量' },
      { key: 'machineType', label: '机型' },
      { key: 'needleCount', label: '针数' },
      { key: 'seconds', label: '秒数' },
      { key: 'machineCount', label: '机台数' },
      { key: 'singleMachineOutput', label: '单机产量' },
    ],
    accessories: [
      { key: 'accessoryName', label: '辅料名称' },
      { key: 'accessoryCategory', label: '辅料类别' },
      { key: 'unit', label: '单位' },
      { key: 'supplier', label: '供应商' },
      { key: 'accessoryUnitPrice', label: '单价' },
    ],
  };

  const cols = columns[module] || [];

  if (!data.length) {
    return (
      <div className="py-12 text-center text-slate-400">
        <Package className="w-10 h-10 mx-auto mb-2 opacity-40" />
        <p>暂无数据，请导入Excel</p>
      </div>
    );
  }

  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="bg-slate-50">
          {cols.map(col => (
            <th key={col.key} className="text-left py-3 px-4 text-slate-500 font-medium whitespace-nowrap">{col.label}</th>
          ))}
          <th className="text-center py-3 px-4 text-slate-500 font-medium w-20">操作</th>
        </tr>
      </thead>
      <tbody>
        {data.map((row, i) => (
          <tr key={row.id || i} className="border-b border-slate-50 hover:bg-amber-50/30 transition-colors">
            {cols.map(col => (
              <td key={col.key} className="py-2.5 px-4 text-slate-700 whitespace-nowrap max-w-[200px] truncate">
                {row[col.key] ?? '-'}
              </td>
            ))}
            <td className="py-2.5 px-4 text-center">
              <button onClick={() => onDelete(row.id)} className="p-1 text-slate-400 hover:text-red-500 transition-colors">
                <Trash2 className="w-4 h-4" />
              </button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
