'use client';

import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useRouter } from 'next/navigation';
import {
  Package, TrendingUp, DollarSign, Factory,
  Search, Upload, Download, Plus, Edit3, Trash2, ChevronLeft,
  ChevronRight, RefreshCw, Calculator, Target, Zap,
  ChevronDown, Check, AlertCircle, Info, Warehouse,
  ShoppingBag, BoxIcon, Cog, BarChart3, Sparkles
} from 'lucide-react';

// ============ 类型定义 ============
interface QuotationItem {
  id: number; productCode: string; productionCode: string; documentNo: string;
  period: string; customer: string; salesperson: string; productCategory: string;
  frontQuotationNo: string; approvalStatus: string; salesType: string;
  rawMaterialName1: string; materialUsage1: number; materialUnitPrice1: number;
  rawMaterialName2: string; materialUsage2: number; materialUnitPrice2: number;
  rawMaterialName3: string; materialUsage3: number; materialUnitPrice3: number;
  rawMaterialName4: string; materialUsage4: number; materialUnitPrice4: number;
  rawMaterialName5: string; materialUsage5: number; materialUnitPrice5: number;
  rawMaterialName6: string; materialUsage6: number; materialUnitPrice6: number;
  accessoryName: string; accessoryPrice: number;
}

interface WarehouseItem {
  id: number; productCode: string; color: string; batchNo: string;
  unit: string; unitPrice: number;
}

interface PurchaseItem {
  id: number; materialCode: string; unit: string; supplier: string;
  batchNo: string; unitPrice: number;
}

interface PlanItem {
  id: number; semiProductCode: string; productCode: string;
  sewingWeight: number; machineType: string; needleCount: string;
  seconds: number; machineCount: number; singleMachineOutput: number;
}

interface AccessoryItem {
  id: number; accessoryName: string; accessoryCategory: string;
  unit: string; supplier: string; accessoryUnitPrice: number;
}

interface SmartQuoteResult {
  productCode: string; productionCode: string; customer: string;
  materials: { name: string; usage: number; purchasePrice: number; cost: number; bestSupplier: string }[];
  accessoryCost: number; accessoryName: string;
  totalMaterialCost: number; processingCostPerUnit: number;
  totalCostPerUnit: number; suggestedPrice: number; profitRate: number;
  dailyCapacity: number; sewingWeight: number;
}

interface SupplierCompare {
  materialCode: string; suppliers: { supplier: string; unitPrice: number }[];
  bestPrice: number; bestSupplier: string; worstPrice: number; savingRate: number;
}

type TabKey = 'dashboard' | 'smart-quote' | 'quotation' | 'warehouse' | 'purchase' | 'plan' | 'accessory';

const TABS: { key: TabKey; label: string; icon: React.ReactNode }[] = [
  { key: 'dashboard', label: '智能仪表盘', icon: <BarChart3 className="w-4 h-4" /> },
  { key: 'smart-quote', label: '智能报价', icon: <Sparkles className="w-4 h-4" /> },
  { key: 'quotation', label: '产品报价', icon: <FileText className="w-4 h-4" /> },
  { key: 'warehouse', label: '原料入库', icon: <Warehouse className="w-4 h-4" /> },
  { key: 'purchase', label: '原料采购', icon: <ShoppingBag className="w-4 h-4" /> },
  { key: 'plan', label: '生产计划', icon: <Cog className="w-4 h-4" /> },
  { key: 'accessory', label: '辅料采购', icon: <BoxIcon className="w-4 h-4" /> },
];

// ============ 工具函数 ============
function formatMoney(val: number | string | null): string {
  if (val == null) return '-';
  const n = typeof val === 'string' ? parseFloat(val) : val;
  if (isNaN(n)) return '-';
  return n.toFixed(4);
}

function formatMoneyShort(val: number | string | null): string {
  if (val == null) return '-';
  const n = typeof val === 'string' ? parseFloat(val) : val;
  if (isNaN(n)) return '-';
  if (n >= 1) return n.toFixed(2);
  return n.toFixed(4);
}

function FileText(props: React.SVGProps<SVGSVGElement> & { className?: string }) {
  return (
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...props}>
      <path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><line x1="10" y1="9" x2="8" y2="9"/>
    </svg>
  );
}

// ============ 主组件 ============
export default function SupplyChainPage() {
  const router = useRouter();
  const [activeTab, setActiveTab] = useState<TabKey>('dashboard');
  const [loading, setLoading] = useState(false);

  // 数据
  const [quotations, setQuotations] = useState<QuotationItem[]>([]);
  const [warehouse, setWarehouse] = useState<WarehouseItem[]>([]);
  const [purchases, setPurchases] = useState<PurchaseItem[]>([]);
  const [plans, setPlans] = useState<PlanItem[]>([]);
  const [accessories, setAccessories] = useState<AccessoryItem[]>([]);
  const [smartQuotes, setSmartQuotes] = useState<SmartQuoteResult[]>([]);
  const [supplierCompares, setSupplierCompares] = useState<SupplierCompare[]>([]);
  const [stats, setStats] = useState({ productCount: 0, materialCount: 0, supplierCount: 0, avgProfitRate: 0 });

  // 报价参数
  const [targetProfitRate, setTargetProfitRate] = useState(30);
  const [processingCost, setProcessingCost] = useState(0.05);

  // 分页搜索
  const [searchKeyword, setSearchKeyword] = useState('');
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [totalItems, setTotalItems] = useState(0);
  const pageSize = 20;

  // 认证检查
  useEffect(() => {
    const sessionId = localStorage.getItem('session_id');
    if (!sessionId) {
      window.location.href = '/login';
    }
  }, []);

  // 自动获取后端 API 基础地址（根据运行环境自动推导）
  const getApiBase = useCallback(() => {
    if (typeof window === 'undefined') return '/api';
    const hostname = window.location.hostname;
    const protocol = window.location.protocol;
    if (hostname === 'localhost' || hostname === '127.0.0.1') {
      return 'http://localhost:8080/api';
    }
    return `${protocol}//${hostname}/api`;
  }, []);

  // 安全的 fetch + json 解析，401 时跳转登录
  const safeFetch = useCallback(async (url: string) => {
    try {
      const sessionId = localStorage.getItem('session_id');
      const headers: Record<string, string> = { 'Content-Type': 'application/json' };
      if (sessionId) headers['X-Session-Id'] = sessionId;
      const res = await fetch(`${getApiBase()}/supply-chain${url}`, { headers });
      if (res.status === 401) {
        localStorage.removeItem('session_id');
        localStorage.removeItem('portal_type');
        localStorage.removeItem('session_expires');
        window.location.href = '/login';
        return null;
      }
      if (!res.ok) return null;
      return await res.json();
    } catch {
      return null;
    }
  }, []);

  // 加载所有数据
  const loadAllData = useCallback(async () => {
    setLoading(true);
    try {
      const [qRes, wRes, pRes, plRes, aRes, sqRes, scRes, stRes] = await Promise.all([
        safeFetch('/quotations?page=1&pageSize=100'),
        safeFetch('/warehouse?page=1&pageSize=100'),
        safeFetch('/purchases?page=1&pageSize=100'),
        safeFetch('/plans?page=1&pageSize=100'),
        safeFetch('/accessories?page=1&pageSize=100'),
        safeFetch(`/smart-quote/product-list?targetProfitRate=${targetProfitRate / 100}&processingCost=${processingCost}`),
        safeFetch('/smart-quote/supplier-comparison'),
        safeFetch(`/stats?targetProfitRate=${targetProfitRate / 100}&processingCost=${processingCost}`),
      ]);
      // 后端 pageResult 返回 {items: [...], total: N}
      // 后端 smart-quotes 返回 {items: [...]}
      // 后端 supplier-comparison 返回 [...]
      // 后端 stats 返回 {productCount, materialCount, ...}
      const extractItems = (res: any) => res?.items || res?.data?.items || res?.data?.content || res?.content || [];
      const extractData = (res: any) => res?.data || res || [];

      setQuotations(extractItems(qRes));
      setWarehouse(extractItems(wRes));
      setPurchases(extractItems(pRes));
      setPlans(extractItems(plRes));
      setAccessories(extractItems(aRes));
      // smart-quote/product-list 返回 {products: [...]}
      // supplier-comparison 返回 {comparison: {materialCode: [...]}}
      setSmartQuotes((sqRes?.products || []).map((p: any) => ({
        ...p,
        materials: (p.materialDetails || p.materials || []).map((m: any) => ({
          ...m,
          purchasePrice: m.purchasePrice ?? m.unitPrice,
          bestSupplier: m.bestSupplier ?? m.supplier ?? '',
        })),
        totalMaterialCost: p.totalMaterialCost ?? p.materialCost,
        processingCostPerUnit: p.processingCostPerUnit ?? p.processingCost,
        totalCostPerUnit: p.totalCostPerUnit ?? p.totalCost,
      })));
      // 供应商对比数据后端返回 {comparison: {materialCode: [{supplier, unitPrice},...]}}
      const comparisonMap = scRes?.comparison || {};
      const scList: SupplierCompare[] = Object.entries(comparisonMap).map(([code, suppliers]: [string, any]) => {
        const sups = (suppliers || []).sort((a: any, b: any) => (a.unitPrice || 0) - (b.unitPrice || 0));
        const best = sups[0];
        const worst = sups[sups.length - 1];
        return {
          materialCode: code,
          suppliers: sups,
          bestPrice: best?.unitPrice || 0,
          bestSupplier: best?.supplier || '',
          worstPrice: worst?.unitPrice || 0,
          savingRate: worst?.unitPrice ? ((worst.unitPrice - (best?.unitPrice || 0)) / worst.unitPrice) : 0,
        };
      });
      setSupplierCompares(scList);
      const s = extractData(stRes);
      setStats({ productCount: s?.productCount || 0, materialCount: s?.materialCount || 0, supplierCount: s?.supplierCount || 0, avgProfitRate: s?.avgProfitRate || 0 });
    } catch (e) {
      console.error('加载数据失败', e);
    } finally {
      setLoading(false);
    }
  }, [targetProfitRate, processingCost]);

  useEffect(() => { loadAllData(); }, [loadAllData]);

  // 删除
  const handleDelete = async (type: string, id: number) => {
    if (!confirm('确定删除此条数据？')) return;
    try {
      const sessionId = localStorage.getItem('session_id');
      const headers: Record<string, string> = {};
      if (sessionId) headers['X-Session-Id'] = sessionId;
      await fetch(`${getApiBase()}/supply-chain/${type}/${id}`, { method: 'DELETE', headers });
      loadAllData();
    } catch { alert('删除失败'); }
  };

  // Excel导入
  const fileInputRef = useRef<HTMLInputElement>(null);
  const handleImport = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const formData = new FormData();
    formData.append('file', file);
    formData.append('type', activeTab === 'quotation' ? 'quotation' : activeTab === 'warehouse' ? 'warehouse' : activeTab === 'purchase' ? 'purchase' : activeTab === 'plan' ? 'plan' : 'accessory');
    try {
      const sessionId = localStorage.getItem('session_id');
      const headers: Record<string, string> = {};
      if (sessionId) headers['X-Session-Id'] = sessionId;
      const res = await fetch(`${getApiBase()}/supply-chain/import`, { method: 'POST', headers, body: formData });
      const result = await res.json();
      if (result.code === 200) { alert(`成功导入 ${result.data} 条数据`); loadAllData(); }
      else alert('导入失败: ' + (result.message || '未知错误'));
    } catch { alert('导入失败'); }
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  // ============ 退出登录 ============
  const handleLogout = () => {
    localStorage.removeItem('session_id');
    localStorage.removeItem('session_expires');
    localStorage.removeItem('portal_type');
    window.location.href = '/login';
  };

  // ============ 渲染：智能仪表盘 ============
  const renderDashboard = () => {
    const profitRateColor = stats.avgProfitRate >= 0.3 ? 'text-green-600' : stats.avgProfitRate >= 0.2 ? 'text-amber-600' : 'text-red-600';
    return (
      <div className="space-y-6">
        {/* 统计卡片 */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          {[
            { label: '产品数量', value: stats.productCount, icon: <Package className="w-5 h-5" />, color: 'from-amber-500 to-orange-600', suffix: '款' },
            { label: '原料种类', value: stats.materialCount, icon: <Warehouse className="w-5 h-5" />, color: 'from-blue-500 to-cyan-600', suffix: '种' },
            { label: '供应商数', value: stats.supplierCount, icon: <ShoppingBag className="w-5 h-5" />, color: 'from-violet-500 to-purple-600', suffix: '家' },
            { label: '平均利润率', value: (stats.avgProfitRate * 100).toFixed(1), icon: <TrendingUp className="w-5 h-5" />, color: 'from-green-500 to-emerald-600', suffix: '%', valueColor: profitRateColor },
          ].map((card, i) => (
            <div key={i} className="bg-white rounded-xl border border-slate-200 p-5 hover:shadow-lg transition-all duration-200">
              <div className="flex items-center justify-between mb-3">
                <span className="text-sm text-slate-500">{card.label}</span>
                <div className={`w-9 h-9 rounded-lg bg-gradient-to-r ${card.color} flex items-center justify-center text-white`}>
                  {card.icon}
                </div>
              </div>
              <div className={`text-2xl font-bold ${card.valueColor || 'text-slate-800'}`}>{card.value}<span className="text-sm font-normal text-slate-400 ml-1">{card.suffix}</span></div>
            </div>
          ))}
        </div>

        {/* 产品报价概览 */}
        <div className="bg-white rounded-xl border border-slate-200 p-5">
          <h3 className="text-lg font-semibold text-slate-800 mb-4 flex items-center gap-2">
            <DollarSign className="w-5 h-5 text-amber-500" />产品报价概览
          </h3>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-100">
                  <th className="text-left py-3 px-3 text-slate-500 font-medium">产品编码</th>
                  <th className="text-left py-3 px-3 text-slate-500 font-medium">生产编码</th>
                  <th className="text-left py-3 px-3 text-slate-500 font-medium">客户</th>
                  <th className="text-left py-3 px-3 text-slate-500 font-medium">销售员</th>
                  <th className="text-left py-3 px-3 text-slate-500 font-medium">审批状态</th>
                  <th className="text-right py-3 px-3 text-slate-500 font-medium">辅料费用</th>
                </tr>
              </thead>
              <tbody>
                {(quotations || []).map((q: any) => (
                  <tr key={q.id} className="border-b border-slate-50 hover:bg-amber-50/50 transition-colors">
                    <td className="py-3 px-3 font-medium text-slate-800">{q.productCode}</td>
                    <td className="py-3 px-3 text-slate-600">{q.productionCode}</td>
                    <td className="py-3 px-3 text-slate-600">{q.customer}</td>
                    <td className="py-3 px-3 text-slate-600">{q.salesperson}</td>
                    <td className="py-3 px-3">
                      <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${q.approvalStatus === '已终审' ? 'bg-green-100 text-green-700' : 'bg-amber-100 text-amber-700'}`}>
                        {q.approvalStatus}
                      </span>
                    </td>
                    <td className="py-3 px-3 text-right font-mono text-slate-700">¥{formatMoneyShort(q.accessoryPrice)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        {/* 供应商最优推荐 */}
        <div className="bg-white rounded-xl border border-slate-200 p-5">
          <h3 className="text-lg font-semibold text-slate-800 mb-4 flex items-center gap-2">
            <Zap className="w-5 h-5 text-violet-500" />供应商最优推荐
          </h3>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {(supplierCompares || []).map((sc, i) => (
              <div key={i} className="border border-slate-100 rounded-lg p-4 hover:border-amber-300 transition-colors">
                <div className="flex items-center justify-between mb-2">
                  <span className="font-medium text-slate-800">{sc.materialCode}</span>
                  <span className="text-xs bg-green-100 text-green-700 px-2 py-0.5 rounded-full">节省{(sc.savingRate * 100).toFixed(1)}%</span>
                </div>
                <div className="text-sm text-slate-500 mb-1">最优: <span className="text-green-600 font-medium">{sc.bestSupplier}</span> ¥{formatMoneyShort(sc.bestPrice)}</div>
                <div className="flex gap-2 flex-wrap">
                  {(sc.suppliers || []).filter((s: any) => s.supplier !== sc.bestSupplier).map((s: any, j: number) => (
                    <span key={j} className="text-xs bg-slate-100 text-slate-500 px-2 py-0.5 rounded">
                      {s.supplier} ¥{formatMoneyShort(s.unitPrice)}
                    </span>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    );
  };

  // ============ 渲染：智能报价 ============
  const renderSmartQuote = () => (
    <div className="space-y-6">
      {/* 参数配置 */}
      <div className="bg-white rounded-xl border border-slate-200 p-5">
        <h3 className="text-lg font-semibold text-slate-800 mb-4 flex items-center gap-2">
          <Calculator className="w-5 h-5 text-amber-500" />报价参数配置
        </h3>
        <div className="flex flex-wrap gap-6 items-end">
          <div>
            <label className="block text-sm text-slate-500 mb-1">目标利润率 (%)</label>
            <input type="number" value={targetProfitRate} onChange={e => setTargetProfitRate(Number(e.target.value))}
              className="w-28 px-3 py-2 border border-slate-300 rounded-lg text-sm focus:ring-2 focus:ring-amber-400 focus:border-amber-400 outline-none" />
          </div>
          <div>
            <label className="block text-sm text-slate-500 mb-1">加工费 (元/条)</label>
            <input type="number" step="0.01" value={processingCost} onChange={e => setProcessingCost(Number(e.target.value))}
              className="w-28 px-3 py-2 border border-slate-300 rounded-lg text-sm focus:ring-2 focus:ring-amber-400 focus:border-amber-400 outline-none" />
          </div>
          <button onClick={loadAllData} className="px-4 py-2 bg-gradient-to-r from-amber-500 to-orange-600 text-white rounded-lg text-sm font-medium hover:shadow-lg transition-all">
            <RefreshCw className="w-4 h-4 inline mr-1" />重新计算
          </button>
        </div>
      </div>

      {/* 逐产品报价卡片 */}
      {(smartQuotes || []).map((sq, idx) => {
        const profitColor = sq.profitRate >= 0.3 ? 'text-green-600' : sq.profitRate >= 0.2 ? 'text-amber-600' : 'text-red-600';
        const profitBg = sq.profitRate >= 0.3 ? 'bg-green-50 border-green-200' : sq.profitRate >= 0.2 ? 'bg-amber-50 border-amber-200' : 'bg-red-50 border-red-200';
        return (
          <div key={idx} className="bg-white rounded-xl border border-slate-200 overflow-hidden">
            <div className="bg-gradient-to-r from-amber-500/10 to-orange-500/10 px-5 py-4 border-b border-slate-100 flex items-center justify-between">
              <div>
                <h4 className="text-lg font-bold text-slate-800">{sq.productCode}
                  <span className="text-sm font-normal text-slate-500 ml-2">({sq.productionCode})</span>
                </h4>
                <div className="text-sm text-slate-500 mt-0.5">客户: {sq.customer} | 缝头重量: {sq.sewingWeight}g | 日产能: {sq.dailyCapacity}条</div>
              </div>
              <div className={`px-4 py-2 rounded-lg border ${profitBg}`}>
                <div className="text-xs text-slate-500">利润率</div>
                <div className={`text-xl font-bold ${profitColor}`}>{(sq.profitRate * 100).toFixed(1)}%</div>
              </div>
            </div>
            <div className="p-5">
              {/* 成本构成表格 */}
              <div className="overflow-x-auto mb-4">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b-2 border-slate-200">
                      <th className="text-left py-2 px-3 text-slate-600 font-semibold">原料名称</th>
                      <th className="text-right py-2 px-3 text-slate-600 font-semibold">用量</th>
                      <th className="text-right py-2 px-3 text-slate-600 font-semibold">采购最低价</th>
                      <th className="text-right py-2 px-3 text-slate-600 font-semibold">原料成本</th>
                      <th className="text-left py-2 px-3 text-slate-600 font-semibold">最优供应商</th>
                    </tr>
                  </thead>
                  <tbody>
                    {((sq.materials || [])).map((m: any, mi: number) => (
                      <tr key={mi} className="border-b border-slate-50 hover:bg-amber-50/30">
                        <td className="py-2 px-3 text-slate-800 font-medium">{m.name}</td>
                        <td className="py-2 px-3 text-right font-mono text-slate-600">{m.usage}</td>
                        <td className="py-2 px-3 text-right font-mono text-slate-600">¥{formatMoney(m.purchasePrice)}</td>
                        <td className="py-2 px-3 text-right font-mono text-amber-700 font-semibold">¥{formatMoney(m.cost)}</td>
                        <td className="py-2 px-3 text-slate-500 text-xs">{m.bestSupplier || '-'}</td>
                      </tr>
                    ))}
                    {/* 辅料行 */}
                    <tr className="border-b border-slate-50 bg-blue-50/30">
                      <td className="py-2 px-3 text-blue-800 font-medium">{sq.accessoryName || '辅料'}</td>
                      <td className="py-2 px-3 text-right font-mono text-slate-600">1</td>
                      <td className="py-2 px-3 text-right font-mono text-slate-600">-</td>
                      <td className="py-2 px-3 text-right font-mono text-blue-700 font-semibold">¥{formatMoney(sq.accessoryCost)}</td>
                      <td className="py-2 px-3 text-slate-500 text-xs">-</td>
                    </tr>
                    {/* 加工费行 */}
                    <tr className="border-b border-slate-50 bg-purple-50/30">
                      <td className="py-2 px-3 text-purple-800 font-medium">加工费</td>
                      <td className="py-2 px-3 text-right font-mono text-slate-600">1条</td>
                      <td className="py-2 px-3 text-right font-mono text-slate-600">-</td>
                      <td className="py-2 px-3 text-right font-mono text-purple-700 font-semibold">¥{formatMoney(sq.processingCostPerUnit)}</td>
                      <td className="py-2 px-3 text-slate-500 text-xs">-</td>
                    </tr>
                  </tbody>
                </table>
              </div>

              {/* 汇总行 */}
              <div className="grid grid-cols-3 gap-4">
                <div className="bg-slate-50 rounded-lg p-3 text-center">
                  <div className="text-xs text-slate-500 mb-1">成本价(原料+辅料)</div>
                  <div className="text-lg font-bold text-slate-700">¥{formatMoneyShort((sq.totalMaterialCost || 0) + (sq.accessoryCost || 0))}</div>
                </div>
                <div className="bg-amber-50 rounded-lg p-3 text-center">
                  <div className="text-xs text-slate-500 mb-1">总成本/条</div>
                  <div className="text-lg font-bold text-amber-700">¥{formatMoneyShort(sq.totalCostPerUnit)}</div>
                </div>
                <div className="bg-green-50 rounded-lg p-3 text-center">
                  <div className="text-xs text-slate-500 mb-1">建议报价/条</div>
                  <div className="text-lg font-bold text-green-700">¥{formatMoneyShort(sq.suggestedPrice)}</div>
                </div>
              </div>
            </div>
          </div>
        );
      })}

      {/* 供应商对比 */}
      <div className="bg-white rounded-xl border border-slate-200 p-5">
        <h3 className="text-lg font-semibold text-slate-800 mb-4 flex items-center gap-2">
          <Target className="w-5 h-5 text-blue-500" />原料采购供应商对比
        </h3>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b-2 border-slate-200">
                <th className="text-left py-3 px-3 text-slate-600 font-semibold">原料编码</th>
                <th className="text-left py-3 px-3 text-slate-600 font-semibold">最优供应商</th>
                <th className="text-right py-3 px-3 text-slate-600 font-semibold">最低价</th>
                <th className="text-right py-3 px-3 text-slate-600 font-semibold">最高价</th>
                <th className="text-right py-3 px-3 text-slate-600 font-semibold">节省比例</th>
                <th className="text-left py-3 px-3 text-slate-600 font-semibold">全部供应商</th>
              </tr>
            </thead>
            <tbody>
              {(supplierCompares || []).map((sc, i) => (
                <tr key={i} className="border-b border-slate-50 hover:bg-blue-50/30">
                  <td className="py-3 px-3 font-medium text-slate-800">{sc.materialCode}</td>
                  <td className="py-3 px-3">
                    <span className="text-green-700 font-medium">{sc.bestSupplier}</span>
                  </td>
                  <td className="py-3 px-3 text-right font-mono text-green-600 font-semibold">¥{formatMoney(sc.bestPrice)}</td>
                  <td className="py-3 px-3 text-right font-mono text-red-500">¥{formatMoney(sc.worstPrice)}</td>
                  <td className="py-3 px-3 text-right">
                    <span className="bg-green-100 text-green-700 px-2 py-0.5 rounded-full text-xs font-medium">
                      -{(sc.savingRate * 100).toFixed(1)}%
                    </span>
                  </td>
                  <td className="py-3 px-3">
                    <div className="flex flex-wrap gap-1">
                      {(sc.suppliers || []).map((s: any, j: number) => (
                        <span key={j} className={`text-xs px-2 py-0.5 rounded ${s.supplier === sc.bestSupplier ? 'bg-green-100 text-green-700' : 'bg-slate-100 text-slate-500'}`}>
                          {s.supplier} ¥{formatMoneyShort(s.unitPrice)}
                        </span>
                      ))}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );

  // ============ 渲染：通用数据表 ============
  const renderDataTable = () => {
    let data: any[] = [];
    let columns: { key: string; label: string; align?: string; format?: (v: any) => string }[] = [];
    let apiType = '';

    switch (activeTab) {
      case 'quotation':
        apiType = 'quotations';
        data = quotations;
        columns = [
          { key: 'productCode', label: '产品编码' },
          { key: 'productionCode', label: '生产编码' },
          { key: 'customer', label: '客户' },
          { key: 'salesperson', label: '销售员' },
          { key: 'approvalStatus', label: '审批状态' },
          { key: 'rawMaterialName1', label: '原料1' },
          { key: 'materialUsage1', label: '用量1', align: 'right', format: formatMoney },
          { key: 'materialUnitPrice1', label: '单价1', align: 'right', format: formatMoney },
          { key: 'rawMaterialName2', label: '原料2' },
          { key: 'materialUsage2', label: '用量2', align: 'right', format: formatMoney },
          { key: 'materialUnitPrice2', label: '单价2', align: 'right', format: formatMoney },
          { key: 'accessoryName', label: '辅料' },
          { key: 'accessoryPrice', label: '辅料价', align: 'right', format: formatMoneyShort },
        ];
        break;
      case 'warehouse':
        apiType = 'warehouse';
        data = warehouse;
        columns = [
          { key: 'productCode', label: '原料编码' },
          { key: 'color', label: '颜色' },
          { key: 'batchNo', label: '批次号' },
          { key: 'unit', label: '单位' },
          { key: 'unitPrice', label: '单价', align: 'right', format: formatMoneyShort },
        ];
        break;
      case 'purchase':
        apiType = 'purchases';
        data = purchases;
        columns = [
          { key: 'materialCode', label: '原料编码' },
          { key: 'unit', label: '单位' },
          { key: 'supplier', label: '供应商' },
          { key: 'batchNo', label: '批次号' },
          { key: 'unitPrice', label: '单价', align: 'right', format: formatMoney },
        ];
        break;
      case 'plan':
        apiType = 'plans';
        data = plans;
        columns = [
          { key: 'semiProductCode', label: '半成品编码' },
          { key: 'productCode', label: '产品编码' },
          { key: 'sewingWeight', label: '缝头重量(g)', align: 'right' },
          { key: 'machineType', label: '机型' },
          { key: 'needleCount', label: '针数' },
          { key: 'seconds', label: '耗时(秒)', align: 'right' },
          { key: 'machineCount', label: '机台数', align: 'right' },
          { key: 'singleMachineOutput', label: '单机产量', align: 'right' },
        ];
        break;
      case 'accessory':
        apiType = 'accessories';
        data = accessories;
        columns = [
          { key: 'accessoryName', label: '辅料名称' },
          { key: 'accessoryCategory', label: '分类' },
          { key: 'unit', label: '单位' },
          { key: 'supplier', label: '供应商' },
          { key: 'accessoryUnitPrice', label: '单价', align: 'right', format: formatMoneyShort },
        ];
        break;
      default:
        return null;
    }

    const deleteType = activeTab === 'quotation' ? 'quotations' : activeTab === 'warehouse' ? 'warehouse' : activeTab === 'purchase' ? 'purchases' : activeTab === 'plan' ? 'plans' : 'accessories';

    return (
      <div className="bg-white rounded-xl border border-slate-200 overflow-hidden">
        {/* 工具栏 */}
        <div className="px-5 py-3 border-b border-slate-100 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="relative">
              <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
              <input type="text" placeholder="搜索..." value={searchKeyword}
                onChange={e => setSearchKeyword(e.target.value)}
                className="pl-9 pr-3 py-1.5 text-sm border border-slate-300 rounded-lg w-52 focus:ring-2 focus:ring-amber-400 focus:border-amber-400 outline-none" />
            </div>
          </div>
          <div className="flex items-center gap-2">
            <input type="file" ref={fileInputRef} onChange={handleImport} accept=".xlsx,.xls" className="hidden" />
            <button onClick={() => fileInputRef.current?.click()}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-gradient-to-r from-amber-500 to-orange-600 text-white text-sm rounded-lg hover:shadow-lg transition-all">
              <Upload className="w-4 h-4" />导入Excel
            </button>
          </div>
        </div>
        {/* 表格 */}
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200">
                {columns.map(col => (
                  <th key={col.key} className={`py-3 px-3 text-slate-600 font-semibold ${col.align === 'right' ? 'text-right' : 'text-left'}`}>
                    {col.label}
                  </th>
                ))}
                <th className="py-3 px-3 text-slate-600 font-semibold text-right">操作</th>
              </tr>
            </thead>
            <tbody>
              {(data || []).length === 0 ? (
                <tr><td colSpan={columns.length + 1} className="py-10 text-center text-slate-400">暂无数据</td></tr>
              ) : (data || []).map((row: any, ri: number) => (
                <tr key={row.id || ri} className="border-b border-slate-50 hover:bg-amber-50/30 transition-colors">
                  {columns.map((col: any) => (
                    <td key={col.key} className={`py-2.5 px-3 ${col.align === 'right' ? 'text-right font-mono' : ''} text-slate-700`}>
                      {col.format ? col.format(row[col.key]) : (row[col.key] ?? '-')}
                    </td>
                  ))}
                  <td className="py-2.5 px-3 text-right">
                    <button onClick={() => handleDelete(deleteType, row.id)}
                      className="p-1.5 text-red-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors">
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="px-5 py-3 border-t border-slate-100 text-sm text-slate-500">
          共 {data.length} 条记录
        </div>
      </div>
    );
  };

  // ============ 主渲染 ============
  return (
    <div className="min-h-screen bg-gradient-to-br from-amber-50 via-orange-50 to-slate-50">
      {/* 顶部导航 */}
      <header className="bg-white/80 backdrop-blur-xl border-b border-slate-200 sticky top-0 z-50">
        <div className="max-w-[1600px] mx-auto px-4 h-14 flex items-center justify-between">
          <div className="flex items-center gap-2">
              <div className="w-8 h-8 rounded-lg bg-gradient-to-r from-amber-500 to-orange-600 flex items-center justify-center text-white">
                <Factory className="w-4 h-4" />
              </div>
              <h1 className="text-lg font-bold text-slate-800">盈云产品智能中台</h1>
              <span className="text-xs bg-amber-100 text-amber-700 px-2 py-0.5 rounded-full font-medium">供应链 & 工厂</span>
            </div>
          <div className="flex items-center gap-2">
            <button onClick={handleLogout}
              className="text-sm text-slate-500 hover:text-red-500 transition-colors px-3 py-1.5 hover:bg-red-50 rounded-lg">
              退出登录
            </button>
          </div>
        </div>

      </header>

      <div className="max-w-[1600px] mx-auto px-4 py-4">
        {/* Tab导航 */}
        <div className="flex gap-1 mb-4 bg-white/60 backdrop-blur rounded-xl p-1 border border-slate-200 overflow-x-auto">
          {TABS.map(tab => (
            <button key={tab.key} onClick={() => setActiveTab(tab.key)}
              className={`flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium whitespace-nowrap transition-all ${
                activeTab === tab.key
                  ? 'bg-gradient-to-r from-amber-500 to-orange-600 text-white shadow-md'
                  : 'text-slate-600 hover:bg-slate-100'
              }`}>
              {tab.icon}{tab.label}
            </button>
          ))}
        </div>

        {/* 内容区 */}
        {loading ? (
          <div className="flex items-center justify-center py-20">
            <RefreshCw className="w-6 h-6 text-amber-500 animate-spin mr-2" />
            <span className="text-slate-500">加载数据中...</span>
          </div>
        ) : (
          <>
            {activeTab === 'dashboard' && renderDashboard()}
            {activeTab === 'smart-quote' && renderSmartQuote()}
            {['quotation', 'warehouse', 'purchase', 'plan', 'accessory'].includes(activeTab) && renderDataTable()}
          </>
        )}
      </div>
    </div>
  );
}
