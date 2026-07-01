'use client';

import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { backendFetch } from '@/lib/backend-proxy';
import {
  Package, TrendingUp, DollarSign, Factory,
  Search, Upload, Download, Plus, Edit3, Trash2, ChevronLeft,
  ChevronRight, RefreshCw, Calculator, Target, Zap,
  ChevronDown, Check, AlertCircle, Info, Warehouse,
  ShoppingBag, BoxIcon, Cog, BarChart3, Sparkles, Scissors, Cloud,
  ArrowLeft, MessageSquare, Send, Bot, User, X, Copy, CheckCircle, Globe,
  Lightbulb
} from 'lucide-react';
import { getCurrentBrand, BRANDS } from '@/lib/brand';
import { cn } from '@/lib/utils';
import MarkdownRenderer from '@/components/MarkdownRenderer';

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
  materials: { name: string; usage: number; unitPrice: number; unitPricePerGram?: number; purchaseRefPrice?: number; cost: number; bestSupplier: string }[];
  accessoryCost: number; accessoryName: string;
  totalMaterialCost: number; processingCostPerUnit: number;
  totalCostPerUnit: number; suggestedPrice: number; profitRate: number;
  dailyCapacity: number; sewingWeight: number;
  machineHourlyRate: number; singleMachineOutput: number;
  weavingCost: number; productWeight: number;
  dyeingUnitPrice: number; postProcessCost: number;
  manufacturingCost: number; costPrice: number;
  yieldRate: number; netCost: number;
}

interface SupplierCompare {
  materialCode: string; suppliers: { supplier: string; unitPrice: number }[];
  bestPrice: number; bestSupplier: string; worstPrice: number; savingRate: number;
}

type TabKey = 'chat' | 'dashboard' | 'smart-quote' | 'quotation' | 'warehouse' | 'purchase' | 'plan' | 'accessory';

const TABS: { key: TabKey; label: string; icon: React.ReactNode }[] = [
  { key: 'chat', label: 'AI 对话', icon: <MessageSquare className="w-4 h-4" /> },
  { key: 'dashboard', label: '智能仪表盘', icon: <BarChart3 className="w-4 h-4" /> },
  { key: 'smart-quote', label: '智能报价', icon: <Sparkles className="w-4 h-4" /> },
  { key: 'quotation', label: '产品报价', icon: <FileText className="w-4 h-4" /> },
  { key: 'warehouse', label: '原料入库', icon: <Warehouse className="w-4 h-4" /> },
  { key: 'purchase', label: '原料采购', icon: <ShoppingBag className="w-4 h-4" /> },
  { key: 'plan', label: '生产计划', icon: <Cog className="w-4 h-4" /> },
  { key: 'accessory', label: '辅料采购', icon: <BoxIcon className="w-4 h-4" /> },
];

// ============ 工具函数 ============
// ============ 复制按钮组件 ============
function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false);
  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch { /* ignore */ }
  };
  return (
    <button
      onClick={handleCopy}
      className="p-1 rounded-md text-slate-400 hover:text-slate-200 hover:bg-slate-700/50 transition-all"
      title="复制内容"
    >
      {copied ? <CheckCircle className="w-3.5 h-3.5 text-green-500" /> : <Copy className="w-3.5 h-3.5" />}
    </button>
  );
}
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

  // AI对话状态
  const [chatMessages, setChatMessages] = useState<Array<{
    role: 'user' | 'assistant';
    content: string;
    reasoning?: string;
    isThinking?: boolean;
    isStreaming?: boolean;
    searchResults?: Array<{ title: string; url: string }>;
  }>>([]);
  const [chatInput, setChatInput] = useState('');
  const [chatLoading, setChatLoading] = useState(false);
  const chatMessagesEndRef = useRef<HTMLDivElement>(null);
  const isUserScrollingRef = useRef(false);
  const chatScrollContainerRef = useRef<HTMLDivElement>(null);

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

  // 认证检查 - 后端不可用时进入降级模式
  useEffect(() => {
    const sessionId = localStorage.getItem('session_id');
    if (!sessionId) {
      backendFetch('/albums?pageSize=1').then(res => {
        if (res.status === 502) {
          console.log('[SupplyChain] 后端不可用，进入降级模式');
        } else {
          window.location.href = '/login';
        }
      }).catch(() => {
        console.log('[SupplyChain] 后端不可用，进入降级模式');
      });
    }
  }, []);

  // 加载工厂AI对话历史
  useEffect(() => {
    const sessionId = localStorage.getItem('session_id');
    if (!sessionId) return;
    
    fetch(`/api/chat/history?mode=factory`, {
      headers: { 'Authorization': `Bearer ${sessionId}` }
    })
      .then(res => res.ok ? res.json() : null)
      .then(data => {
        if (data && data.success && Array.isArray(data.history) && data.history.length > 0) {
          const history = data.history.map((msg: { role: string; content: string; reasoning_content?: string }) => ({
            role: msg.role as 'user' | 'assistant',
            content: msg.content,
            reasoning: msg.reasoning_content || undefined,
          }));
          setChatMessages(history);
        }
      })
      .catch(() => {});
  }, []);

  // 统一走 Next.js 代理，避免外网 CORS 问题
  const getApiBase = useCallback(() => {
    return '/api/proxy';
  }, []);

  // 安全的 fetch + json 解析，401 时跳转登录
  const safeFetch = useCallback(async (url: string) => {
    try {
      const sessionId = localStorage.getItem('session_id');
      const headers: Record<string, string> = { 'Content-Type': 'application/json' };
      if (sessionId) headers['X-Session-Id'] = sessionId;
      const res = await fetch(`${getApiBase()}/supply-chain${url}`, { headers });
      if (res.status === 401) {
        // 401 只在后端可用时才跳转，后端不可用时降级
        console.log('[SupplyChain] 401 未授权');
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
  // ============ AI对话 ============
  const handleFactoryChat = useCallback(async (message?: string) => {
    const msg = message || chatInput.trim();
    if (!msg || chatLoading) return;

    const userMsg = { role: 'user' as const, content: msg };
    setChatMessages(prev => [...prev, userMsg]);
    setChatInput('');
    setChatLoading(true);
    isUserScrollingRef.current = false;

    const assistantMsg: typeof chatMessages[0] = {
      role: 'assistant',
      content: '',
      reasoning: '',
      isThinking: true,
      isStreaming: true,
      searchResults: [],
    };
    setChatMessages(prev => [...prev, assistantMsg]);

    try {
      const sid = localStorage.getItem('session_id');
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
      };
      if (sid) headers['X-Session-Id'] = sid;

      const params = new URLSearchParams({ message: msg, mode: 'factory' });
      const res = await fetch(`/api/chat/smart?${params}`, {
        credentials: 'include',
        headers,
      });

      if (!res.ok) throw new Error('请求失败');

      const reader = res.body?.getReader();
      if (!reader) throw new Error('无法读取流');

      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          if (!line.startsWith('data:')) continue;
          const data = line.substring(5).trim();
          if (!data || data === '[DONE]') continue;

          try {
            const parsed = JSON.parse(data);
            if (parsed.type === 'reasoning_delta' && parsed.content) {
              setChatMessages(prev => {
                const updated = [...prev];
                const last = updated[updated.length - 1];
                updated[updated.length - 1] = { ...last, reasoning: (last.reasoning || '') + parsed.content };
                return updated;
              });
            } else if (parsed.type === 'content' && parsed.content) {
              setChatMessages(prev => {
                const updated = [...prev];
                const last = updated[updated.length - 1];
                updated[updated.length - 1] = { ...last, content: (last.content || '') + parsed.content, isThinking: false };
                return updated;
              });
            } else if (parsed.type === 'web_search_result' && parsed.results) {
              setChatMessages(prev => {
                const updated = [...prev];
                const last = updated[updated.length - 1];
                updated[updated.length - 1] = { ...last, searchResults: parsed.results };
                return updated;
              });
            } else if (parsed.type === 'done') {
              setChatMessages(prev => {
                const updated = [...prev];
                const last = updated[updated.length - 1];
                updated[updated.length - 1] = { ...last, isThinking: false, isStreaming: false };
                return updated;
              });
            }
          } catch { /* ignore parse errors */ }
        }
      }
    } catch (err) {
      setChatMessages(prev => {
        const updated = [...prev];
        const last = updated[updated.length - 1];
        if (last.role === 'assistant') {
          updated[updated.length - 1] = { ...last, content: '抱歉，请求出现问题，请稍后再试。', isThinking: false, isStreaming: false };
        }
        return updated;
      });
    } finally {
      setChatLoading(false);
    }
  }, [chatInput, chatLoading]);

  // 自动滚动到底部
  useEffect(() => {
    if (!isUserScrollingRef.current && chatMessagesEndRef.current) {
      chatMessagesEndRef.current.scrollIntoView({ behavior: 'auto' });
    }
  }, [chatMessages]);

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
            <div key={i} className="bg-slate-800/50 rounded-xl border border-slate-700/50 p-5 hover:shadow-lg hover:shadow-blue-500/5 transition-all duration-200">
              <div className="flex items-center justify-between mb-3">
                <span className="text-sm text-slate-400">{card.label}</span>
                <div className={`w-9 h-9 rounded-lg bg-gradient-to-r ${card.color} flex items-center justify-center text-white`}>
                  {card.icon}
                </div>
              </div>
              <div className={`text-2xl font-bold ${card.valueColor || 'text-slate-100'}`}>{card.value}<span className="text-sm font-normal text-slate-500 ml-1">{card.suffix}</span></div>
            </div>
          ))}
        </div>

        {/* 产品报价概览 */}
        <div className="bg-slate-800/50 rounded-xl border border-slate-700/50 p-5">
          <h3 className="text-lg font-semibold text-slate-100 mb-4 flex items-center gap-2">
            <DollarSign className="w-5 h-5 text-amber-500" />产品报价概览
          </h3>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-700/50">
                  <th className="text-left py-3 px-3 text-slate-400 font-medium">产品编码</th>
                  <th className="text-left py-3 px-3 text-slate-400 font-medium">生产编码</th>
                  <th className="text-left py-3 px-3 text-slate-400 font-medium">客户</th>
                  <th className="text-left py-3 px-3 text-slate-400 font-medium">销售员</th>
                  <th className="text-left py-3 px-3 text-slate-400 font-medium">审批状态</th>
                  <th className="text-right py-3 px-3 text-slate-400 font-medium">辅料费用</th>
                </tr>
              </thead>
              <tbody>
                {(quotations || []).map((q: any) => (
                  <tr key={q.id} className="border-b border-slate-700/30 hover:bg-slate-700/20 transition-colors">
                    <td className="py-3 px-3 font-medium text-slate-200">{q.productCode}</td>
                    <td className="py-3 px-3 text-slate-400">{q.productionCode}</td>
                    <td className="py-3 px-3 text-slate-400">{q.customer}</td>
                    <td className="py-3 px-3 text-slate-400">{q.salesperson}</td>
                    <td className="py-3 px-3">
                      <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${q.approvalStatus === '已终审' ? 'bg-green-500/15 text-green-400' : 'bg-amber-500/15 text-amber-400'}`}>
                        {q.approvalStatus}
                      </span>
                    </td>
                    <td className="py-3 px-3 text-right font-mono text-slate-300">¥{formatMoneyShort(q.accessoryPrice)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        {/* 供应商最优推荐 */}
        <div className="bg-slate-800/50 rounded-xl border border-slate-700/50 p-5">
          <h3 className="text-lg font-semibold text-slate-100 mb-4 flex items-center gap-2">
            <Zap className="w-5 h-5 text-violet-500" />供应商最优推荐
          </h3>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {(supplierCompares || []).map((sc, i) => (
              <div key={i} className="border border-slate-700/50 rounded-lg p-4 hover:border-blue-500/30 transition-colors">
                <div className="flex items-center justify-between mb-2">
                  <span className="font-medium text-slate-200">{sc.materialCode}</span>
                  <span className="text-xs bg-green-500/15 text-green-400 px-2 py-0.5 rounded-full">节省{(sc.savingRate * 100).toFixed(1)}%</span>
                </div>
                <div className="text-sm text-slate-400 mb-1">最优: <span className="text-green-400 font-medium">{sc.bestSupplier}</span> ¥{formatMoneyShort(sc.bestPrice)}</div>
                <div className="flex gap-2 flex-wrap">
                  {(sc.suppliers || []).filter((s: any) => s.supplier !== sc.bestSupplier).map((s: any, j: number) => (
                    <span key={j} className="text-xs bg-slate-700/50 text-slate-400 px-2 py-0.5 rounded">
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
      <div className="bg-slate-800/50 rounded-xl border border-slate-700/50 p-5">
        <h3 className="text-lg font-semibold text-slate-100 mb-4 flex items-center gap-2">
          <Calculator className="w-5 h-5 text-amber-500" />报价参数配置
        </h3>
        <div className="flex flex-wrap gap-6 items-end">
          <div>
            <label className="block text-sm text-slate-400 mb-1">目标利润率 (%)</label>
            <input type="number" value={targetProfitRate} onChange={e => setTargetProfitRate(Number(e.target.value))}
              style={{color: '#f8fafc'}}
              className="w-28 px-3 py-2 border border-slate-600 rounded-lg text-sm focus:ring-2 focus:ring-amber-400 focus:border-amber-400 outline-none bg-slate-900/50" />
          </div>
          <div>
            <label className="block text-sm text-slate-400 mb-1">加工费 (元/条)</label>
            <input type="number" step="0.01" value={processingCost} onChange={e => setProcessingCost(Number(e.target.value))}
              style={{color: '#f8fafc'}}
              className="w-28 px-3 py-2 border border-slate-600 rounded-lg text-sm focus:ring-2 focus:ring-amber-400 focus:border-amber-400 outline-none bg-slate-900/50" />
          </div>
          <button onClick={loadAllData} className="px-4 py-2 bg-gradient-to-r from-amber-500 to-orange-600 text-white rounded-lg text-sm font-medium hover:shadow-lg transition-all">
            <RefreshCw className="w-4 h-4 inline mr-1" />重新计算
          </button>
        </div>
      </div>

      {/* 逐产品报价卡片 */}
      {(smartQuotes || []).map((sq, idx) => {
        const profitColor = sq.profitRate >= 0.3 ? 'text-green-600' : sq.profitRate >= 0.2 ? 'text-amber-600' : 'text-red-600';
        const profitBg = sq.profitRate >= 0.3 ? 'bg-green-500/10 border-green-500/30' : sq.profitRate >= 0.2 ? 'bg-amber-500/10 border-amber-500/30' : 'bg-red-500/10 border-red-500/30';
        return (
          <div key={idx} className="bg-slate-800/50 rounded-xl border border-slate-700/50 overflow-hidden">
            <div className="bg-gradient-to-r from-amber-500/10 to-orange-500/10 px-5 py-4 border-b border-slate-700/50 flex items-center justify-between">
              <div>
                <h4 className="text-lg font-bold text-slate-100">{sq.productCode}
                  <span className="text-sm font-normal text-slate-400 ml-2">({sq.productionCode})</span>
                </h4>
                <div className="text-sm text-slate-400 mt-0.5">客户: {sq.customer} | 下机克重: {sq.productWeight || sq.sewingWeight}g | 日产能: {sq.dailyCapacity}条</div>
              </div>
              <div className={`px-4 py-2 rounded-lg border ${profitBg}`}>
                <div className="text-xs text-slate-400">利润率</div>
                <div className={`text-xl font-bold ${profitColor}`}>{(sq.profitRate * 100).toFixed(1)}%</div>
              </div>
            </div>
            <div className="p-5">
              {/* 成本构成表格 */}
              <div className="overflow-x-auto mb-4">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b-2 border-slate-700/50">
                      <th className="text-left py-2 px-3 text-slate-400 font-semibold">原料名称</th>
                      <th className="text-right py-2 px-3 text-slate-400 font-semibold">用量(g)</th>
                      <th className="text-right py-2 px-3 text-slate-400 font-semibold">单价(元/kg)</th>
                      <th className="text-right py-2 px-3 text-slate-400 font-semibold">采购参考价</th>
                      <th className="text-right py-2 px-3 text-slate-400 font-semibold">原料成本</th>
                      <th className="text-left py-2 px-3 text-slate-400 font-semibold">最优供应商</th>
                    </tr>
                  </thead>
                  <tbody>
                    {((sq.materials || [])).map((m: any, mi: number) => (
                      <tr key={mi} className="border-b border-slate-700/30 hover:bg-slate-700/20">
                        <td className="py-2 px-3 text-slate-200 font-medium">{m.name}</td>
                        <td className="py-2 px-3 text-right font-mono text-slate-400">{m.usage}</td>
                        <td className="py-2 px-3 text-right font-mono text-slate-400">¥{formatMoney(m.unitPrice)}</td>
                        <td className="py-2 px-3 text-right font-mono text-slate-500">{m.purchaseRefPrice ? `¥${formatMoney(m.purchaseRefPrice)}/kg` : '-'}</td>
                        <td className="py-2 px-3 text-right font-mono text-amber-400 font-semibold">¥{formatMoney(m.cost)}</td>
                        <td className="py-2 px-3 text-slate-400 text-xs">{m.bestSupplier || '-'}</td>
                      </tr>
                    ))}
                    {/* 辅料行 */}
                    <tr className="border-b border-slate-700/30 bg-blue-500/10">
                      <td className="py-2 px-3 text-blue-300 font-medium">{sq.accessoryName || '辅料'}</td>
                      <td className="py-2 px-3 text-right font-mono text-slate-400">-</td>
                      <td className="py-2 px-3 text-right font-mono text-slate-400">-</td>
                      <td className="py-2 px-3 text-right font-mono text-blue-400 font-semibold">¥{formatMoney(sq.accessoryCost)}</td>
                      <td className="py-2 px-3 text-slate-400 text-xs">-</td>
                    </tr>
                    {/* 制造部分小计 */}
                    <tr className="border-b border-slate-700/30 bg-purple-500/10">
                      <td className="py-2 px-3 text-purple-300 font-medium">制造合计(织造+后整理)</td>
                      <td className="py-2 px-3 text-right font-mono text-slate-400">-</td>
                      <td className="py-2 px-3 text-right font-mono text-slate-400">-</td>
                      <td className="py-2 px-3 text-right font-mono text-purple-400 font-semibold">¥{formatMoney(sq.manufacturingCost)}</td>
                      <td className="py-2 px-3 text-purple-400 text-xs">R/P + M/1000×D</td>
                    </tr>
                  </tbody>
                </table>
              </div>

              {/* 制造部分明细 - 新公式 */}
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-4">
                <div className="bg-slate-700/30 rounded-lg p-2.5 text-center">
                  <div className="text-xs text-slate-400">机台费率 R(元/h)</div>
                  <div className="text-sm font-bold text-slate-200">{sq.machineHourlyRate || 50}</div>
                </div>
                <div className="bg-slate-700/30 rounded-lg p-2.5 text-center">
                  <div className="text-xs text-slate-400">单机产量 P(双/h)</div>
                  <div className="text-sm font-bold text-slate-200">{sq.singleMachineOutput || 1000}</div>
                </div>
                <div className="bg-purple-500/10 rounded-lg p-2.5 text-center">
                  <div className="text-xs text-slate-400">织造成本 R/P</div>
                  <div className="text-sm font-bold text-purple-400">¥{formatMoney(sq.weavingCost)}</div>
                </div>
                <div className="bg-slate-700/30 rounded-lg p-2.5 text-center">
                  <div className="text-xs text-slate-400">下机克重 M(g)</div>
                  <div className="text-sm font-bold text-slate-200">{sq.productWeight || sq.sewingWeight}</div>
                </div>
                <div className="bg-slate-700/30 rounded-lg p-2.5 text-center">
                  <div className="text-xs text-slate-400">染色单价 D(元/kg)</div>
                  <div className="text-sm font-bold text-slate-200">{sq.dyeingUnitPrice || '-'}</div>
                </div>
                <div className="bg-orange-500/10 rounded-lg p-2.5 text-center">
                  <div className="text-xs text-slate-400">后整理 M/1000×D</div>
                  <div className="text-sm font-bold text-orange-400">¥{formatMoney(sq.postProcessCost)}</div>
                </div>
                <div className="bg-purple-500/10 rounded-lg p-2.5 text-center">
                  <div className="text-xs text-slate-400">制造合计</div>
                  <div className="text-sm font-bold text-purple-400">¥{formatMoney(sq.manufacturingCost)}</div>
                </div>
                <div className="bg-blue-500/10 rounded-lg p-2.5 text-center">
                  <div className="text-xs text-slate-400">正品率</div>
                  <div className="text-sm font-bold text-blue-400">{sq.yieldRate || 100}%</div>
                </div>
              </div>

              {/* 汇总行 */}
              <div className="grid grid-cols-2 sm:grid-cols-5 gap-3">
                <div className="bg-slate-700/30 rounded-lg p-3 text-center">
                  <div className="text-xs text-slate-400 mb-1">原料成本</div>
                  <div className="text-lg font-bold text-slate-200">¥{formatMoneyShort(sq.totalMaterialCost)}</div>
                </div>
                <div className="bg-blue-500/10 rounded-lg p-3 text-center">
                  <div className="text-xs text-slate-400 mb-1">辅料成本</div>
                  <div className="text-lg font-bold text-blue-400">¥{formatMoneyShort(sq.accessoryCost)}</div>
                </div>
                <div className="bg-purple-500/10 rounded-lg p-3 text-center">
                  <div className="text-xs text-slate-400 mb-1">制造合计</div>
                  <div className="text-lg font-bold text-purple-400">¥{formatMoneyShort(sq.manufacturingCost)}</div>
                </div>
                <div className="bg-amber-500/10 rounded-lg p-3 text-center">
                  <div className="text-xs text-slate-400 mb-1">净成本/条</div>
                  <div className="text-lg font-bold text-amber-400">¥{formatMoneyShort(sq.netCost)}</div>
                </div>
                <div className="bg-green-500/10 rounded-lg p-3 text-center">
                  <div className="text-xs text-slate-400 mb-1">建议报价/条</div>
                  <div className="text-lg font-bold text-green-400">¥{formatMoneyShort(sq.suggestedPrice)}</div>
                </div>
              </div>
            </div>
          </div>
        );
      })}

      {/* 供应商对比 */}
      <div className="bg-slate-800/50 rounded-xl border border-slate-700/50 p-5">
        <h3 className="text-lg font-semibold text-slate-100 mb-4 flex items-center gap-2">
          <Target className="w-5 h-5 text-blue-500" />原料采购供应商对比
        </h3>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b-2 border-slate-700/50">
                <th className="text-left py-3 px-3 text-slate-400 font-semibold">原料编码</th>
                <th className="text-left py-3 px-3 text-slate-400 font-semibold">最优供应商</th>
                <th className="text-right py-3 px-3 text-slate-400 font-semibold">最低价</th>
                <th className="text-right py-3 px-3 text-slate-400 font-semibold">最高价</th>
                <th className="text-right py-3 px-3 text-slate-400 font-semibold">节省比例</th>
                <th className="text-left py-3 px-3 text-slate-400 font-semibold">全部供应商</th>
              </tr>
            </thead>
            <tbody>
              {(supplierCompares || []).map((sc, i) => (
                <tr key={i} className="border-b border-slate-700/30 hover:bg-slate-700/20">
                  <td className="py-3 px-3 font-medium text-slate-200">{sc.materialCode}</td>
                  <td className="py-3 px-3">
                    <span className="text-green-400 font-medium">{sc.bestSupplier}</span>
                  </td>
                  <td className="py-3 px-3 text-right font-mono text-green-400 font-semibold">¥{formatMoney(sc.bestPrice)}</td>
                  <td className="py-3 px-3 text-right font-mono text-red-400">¥{formatMoney(sc.worstPrice)}</td>
                  <td className="py-3 px-3 text-right">
                    <span className="bg-green-500/15 text-green-400 px-2 py-0.5 rounded-full text-xs font-medium">
                      -{(sc.savingRate * 100).toFixed(1)}%
                    </span>
                  </td>
                  <td className="py-3 px-3">
                    <div className="flex flex-wrap gap-1">
                      {(sc.suppliers || []).map((s: any, j: number) => (
                        <span key={j} className={`text-xs px-2 py-0.5 rounded ${s.supplier === sc.bestSupplier ? 'bg-green-500/15 text-green-400' : 'bg-slate-700/50 text-slate-400'}`}>
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
      <div className="bg-slate-800/50 rounded-xl border border-slate-700/50 shadow-sm overflow-hidden">
        {/* 工具栏 */}
        <div className="px-4 py-3 border-b border-slate-700/50 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="relative">
              <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
              <input type="text" placeholder="搜索..." value={searchKeyword}
                onChange={e => setSearchKeyword(e.target.value)}
                style={{color: '#f8fafc'}}
                className="pl-9 pr-3 py-1.5 text-[13px] border border-slate-600/50 rounded-lg w-52 focus:ring-2 focus:ring-amber-400/30 focus:border-amber-400/50 outline-none bg-slate-900/50 transition-colors placeholder:text-slate-500" />
            </div>
          </div>
          <div className="flex items-center gap-2">
            <input type="file" ref={fileInputRef} onChange={handleImport} accept=".xlsx,.xls" className="hidden" />
            <button onClick={() => fileInputRef.current?.click()}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-gradient-to-r from-amber-500 to-orange-500 text-white text-[13px] font-medium rounded-lg hover:shadow-md transition-all">
              <Upload className="w-3.5 h-3.5" />导入Excel
            </button>
          </div>
        </div>
        {/* 表格 */}
        <div className="overflow-x-auto">
          <table className="w-full text-[13px]">
            <thead>
              <tr className="bg-slate-700/30 border-b border-slate-700/50">
                {columns.map(col => (
                  <th key={col.key} className={`py-2.5 px-3 text-slate-400 font-semibold text-[12px] uppercase tracking-wider ${col.align === 'right' ? 'text-right' : 'text-left'}`}>
                    {col.label}
                  </th>
                ))}
                <th className="py-2.5 px-3 text-slate-400 font-semibold text-[12px] uppercase tracking-wider text-right">操作</th>
              </tr>
            </thead>
            <tbody>
              {(data || []).length === 0 ? (
                <tr><td colSpan={columns.length + 1} className="py-12 text-center text-slate-400 text-[13px]">暂无数据</td></tr>
              ) : (data || []).map((row: any, ri: number) => (
                <tr key={row.id || ri} className="border-b border-slate-700/30 hover:bg-slate-700/20 transition-colors">
                  {columns.map((col: any) => (
                    <td key={col.key} className={`py-2.5 px-3 text-slate-300 ${col.align === 'right' ? 'text-right font-mono text-[12px]' : ''}`}>
                      {col.format ? col.format(row[col.key]) : (row[col.key] ?? '-')}
                    </td>
                  ))}
                  <td className="py-2.5 px-3 text-right">
                    <button onClick={() => handleDelete(deleteType, row.id)}
                      className="p-1.5 text-red-400 hover:text-red-300 hover:bg-red-500/10 rounded-lg transition-colors">
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="px-4 py-2.5 border-t border-slate-100 text-[12px] text-slate-400">
          共 {data.length} 条记录
        </div>
      </div>
    );
  };

  // ============ 主渲染 ============
  const [brand, setBrand] = useState(BRANDS.yingyun);
  const BrandIcon = brand.key === 'bonasi' ? Scissors : Cloud;

  useEffect(() => {
    setBrand(getCurrentBrand());
  }, []);

  return (
    <div className="min-h-screen bg-[#0a0e1a]">
      {/* 顶部导航 */}
      <header className="bg-[#0f172a]/90 backdrop-blur-xl border-b border-slate-700/50 sticky top-0 z-50">
        <div className="max-w-[1600px] mx-auto px-5 h-14 flex items-center justify-between">
          <div className="flex items-center gap-3">
              <button
                onClick={() => { localStorage.setItem('back_to_portal', 'true'); router.push('/login'); }}
                className="flex items-center gap-1 text-sm text-slate-400 hover:text-slate-200 transition-colors shrink-0"
              >
                <ArrowLeft className="w-4 h-4" />
                <span>返回</span>
              </button>
              <span className="text-slate-300">|</span>
              <div className={cn("w-8 h-8 rounded-lg bg-gradient-to-br flex items-center justify-center text-white shadow-sm", brand.primaryFrom, brand.primaryTo)}>
                <BrandIcon className="w-4 h-4" />
              </div>
              <div>
                <h1 className="text-[15px] font-bold text-slate-100 leading-tight">{brand.name}企业数智中台系统</h1>
                <p className="text-[11px] text-slate-400">供应链 & 工厂管理</p>
              </div>
            </div>
          <div className="flex items-center gap-2">
            <button onClick={handleLogout}
              className="text-sm text-slate-400 hover:text-red-400 transition-colors px-3 py-1.5 hover:bg-red-500/10 rounded-lg">
              退出登录
            </button>
          </div>
        </div>

      </header>

      <div className="max-w-[1600px] mx-auto px-4 py-4">
        {/* Tab导航 */}
        <div className="flex gap-0.5 mb-4 bg-slate-800/50 rounded-xl p-1 border border-slate-700/50 shadow-sm overflow-x-auto">
          {TABS.map(tab => (
            <button key={tab.key} onClick={() => setActiveTab(tab.key)}
              className={`flex items-center gap-1.5 px-4 py-2 rounded-lg text-[13px] font-medium whitespace-nowrap transition-all ${
                activeTab === tab.key
                  ? 'bg-gradient-to-r from-amber-500 to-orange-500 text-white shadow-sm'
                  : 'text-slate-400 hover:bg-slate-700/50 hover:text-slate-200'
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
            {activeTab === 'chat' && (
              <div className="flex flex-col h-[calc(100vh-140px)] bg-slate-800/30 rounded-2xl border border-slate-700/50 shadow-sm overflow-hidden">
                {/* 聊天消息区 */}
                <div
                  ref={chatScrollContainerRef}
                  className="flex-1 overflow-y-auto px-6 py-4 space-y-5"
                  onScroll={() => {
                    if (chatScrollContainerRef.current) {
                      const { scrollTop, scrollHeight, clientHeight } = chatScrollContainerRef.current;
                      isUserScrollingRef.current = scrollHeight - scrollTop - clientHeight > 120;
                    }
                  }}
                >
                  {chatMessages.length === 0 && (
                    <div className="flex flex-col items-center justify-center h-full text-center">
                      <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-slate-700 to-slate-900 flex items-center justify-center mb-4 shadow-lg shadow-slate-200/50">
                        <MessageSquare className="w-8 h-8 text-white" />
                      </div>
                      <h2 className="text-xl font-bold text-slate-100 mb-2">工厂AI助手</h2>
                      <p className="text-sm text-slate-400 mb-6 max-w-md leading-relaxed">基于DeepSeek大模型，专注供应链与工厂业务知识，支持产品报价、原料采购、生产计划等智能问答</p>
                      <div className="flex flex-wrap gap-2 justify-center">
                        <span className="px-3 py-1 rounded-full text-xs font-medium bg-orange-500/10 text-orange-400 border border-orange-500/20">产品报价</span>
                        <span className="px-3 py-1 rounded-full text-xs font-medium bg-blue-500/10 text-blue-400 border border-blue-500/20">原料采购</span>
                        <span className="px-3 py-1 rounded-full text-xs font-medium bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">生产计划</span>
                        <span className="px-3 py-1 rounded-full text-xs font-medium bg-sky-500/10 text-sky-400 border border-sky-500/20">联网搜索</span>
                        <span className="px-3 py-1 rounded-full text-xs font-medium bg-slate-700/50 text-slate-300 border border-slate-600/50">
                          <Zap className="w-3 h-3 inline-block text-yellow-500 mr-0.5" />DeepSeek V4 Pro
                        </span>
                      </div>
                      <div className="mt-6 flex flex-col gap-2.5">
                        {['帮我分析原料采购的供应商报价', '最新的涤纶面料市场行情如何', '查询产品SK-H001的成本构成'].map(q => (
                          <button key={q} onClick={() => handleFactoryChat(q)}
                            className="text-left px-4 py-3 text-sm text-slate-300 bg-slate-800/50 hover:bg-slate-700/60 rounded-xl transition-all border border-slate-700/50 hover:border-slate-600 hover:shadow-sm">
                            <span className="text-slate-400 mr-2">→</span>{q}
                          </button>
                        ))}
                      </div>
                    </div>
                  )}
                  {chatMessages.map((msg, idx) => (
                    <div key={idx} className={`flex gap-3 ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}>
                      {msg.role === 'assistant' && (
                        <div className="shrink-0 w-8 h-8 rounded-lg bg-gradient-to-br from-slate-700 to-slate-900 flex items-center justify-center shadow-sm mt-0.5">
                          <Bot className="w-4 h-4 text-white" />
                        </div>
                      )}
                      <div className={`max-w-[80%] group/msg ${msg.role === 'user' ? 'order-first' : ''}`}>
                        {/* 思维链（DeepSeek思考模式） */}
                        {msg.role === 'assistant' && msg.reasoning && msg.reasoning.length > 0 && (
                          <details className="mb-2.5 group">
                            <summary className="flex items-center gap-2 text-[11px] text-slate-400 cursor-pointer hover:text-slate-200 transition-colors select-none py-1">
                              <svg className="w-3 h-3 transition-transform group-open:rotate-90 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                              </svg>
                              <Lightbulb className="w-3 h-3 shrink-0" />
                              {msg.isThinking ? (
                                <span className="flex items-center gap-1.5">
                                  <span className="inline-block w-1.5 h-1.5 rounded-full bg-slate-400 animate-pulse" />
                                  正在深度思考...
                                </span>
                              ) : (
                                <span>思考过程</span>
                              )}
                            </summary>
                            <div className="mt-1.5 p-3 bg-gradient-to-br from-slate-800/80 to-slate-800/60 border border-slate-700/60 rounded-xl text-[11.5px] text-slate-400 leading-relaxed max-h-52 overflow-y-auto whitespace-pre-wrap shadow-sm">
                              {msg.reasoning}
                            </div>
                          </details>
                        )}

                        {/* 联网搜索结果 */}
                        {msg.role === 'assistant' && msg.searchResults && msg.searchResults.length > 0 && (
                          <details className="mb-2.5 group" open>
                            <summary className="flex items-center gap-2 text-[11px] text-sky-500 cursor-pointer hover:text-sky-600 transition-colors select-none py-1">
                              <svg className="w-3 h-3 transition-transform group-open:rotate-90 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                              </svg>
                              <Globe className="w-3 h-3 shrink-0" />
                              <span>联网搜索结果</span>
                            </summary>
                            <div className="mt-1.5 text-[11.5px] bg-gradient-to-br from-sky-900/20 to-blue-900/10 rounded-xl p-3 border border-sky-500/20 shadow-sm space-y-1.5">
                              {msg.searchResults.map((r, i) => (
                                <div key={i} className="text-sky-300 truncate">
                                  <span className="text-sky-500 mr-1">{i + 1}.</span>
                                  {r.title}
                                </div>
                              ))}
                            </div>
                          </details>
                        )}

                        {/* 消息内容 */}
                        <div className={`relative group/msg
                          ${msg.role === 'user'
                            ? 'px-4 py-3 rounded-2xl rounded-tr-md bg-gradient-to-r from-blue-600 to-cyan-600 text-white shadow-md shadow-blue-500/20'
                            : 'px-4 py-3 rounded-2xl rounded-tl-md bg-slate-800/50 border border-slate-700/50 text-slate-300 shadow-sm'}`}
                        >
                          {msg.role === 'user' ? (
                            <div className="whitespace-pre-wrap text-[13px] leading-relaxed">{msg.content}</div>
                          ) : (
                            <MarkdownRenderer content={msg.content || ''} darkMode />
                          )}
                          {msg.isStreaming && (
                            <span className={`inline-block w-1.5 h-4 ml-0.5 align-middle animate-pulse rounded-full
                              ${msg.isThinking ? 'bg-blue-400' : 'bg-blue-500'}`} />
                          )}
                          {/* 复制按钮 - 仅assistant消息完成时显示 */}
                          {msg.role === 'assistant' && !msg.isStreaming && msg.content && (
                            <div className="absolute -right-1 -top-1 opacity-0 group-hover/msg:opacity-100 transition-opacity">
                              <CopyButton text={msg.content} />
                            </div>
                          )}
                        </div>
                      </div>
                      {msg.role === 'user' && (
                        <div className="shrink-0 w-8 h-8 rounded-lg bg-gradient-to-br from-slate-500 to-slate-600 flex items-center justify-center shadow-sm mt-0.5">
                          <User className="w-4 h-4 text-white" />
                        </div>
                      )}
                    </div>
                  ))}
                  <div ref={chatMessagesEndRef} />
                </div>
                {/* 输入区 */}
                <div className="border-t border-slate-700/50 px-4 py-3 bg-slate-800/50">
                  <div className="flex gap-2 items-end">
                    <textarea
                      value={chatInput}
                      onChange={e => setChatInput(e.target.value)}
                      onKeyDown={e => {
                        if (e.key === 'Enter' && !e.shiftKey) {
                          e.preventDefault();
                          handleFactoryChat();
                        }
                      }}
                      placeholder="输入工厂业务问题..."
                      rows={1}
                      className="flex-1 resize-none rounded-xl border border-slate-700/50 bg-slate-900/50 px-4 py-2.5 text-sm text-slate-200 focus:outline-none focus:ring-2 focus:ring-blue-500/30 focus:border-blue-500/50 transition-all placeholder:text-slate-500"
                    />
                    <button
                      onClick={() => handleFactoryChat()}
                      disabled={chatLoading || !chatInput.trim()}
                      className="shrink-0 w-10 h-10 rounded-xl bg-gradient-to-br from-blue-600 to-cyan-500 text-white flex items-center justify-center hover:shadow-lg hover:shadow-blue-500/30 transition-all disabled:opacity-40 disabled:cursor-not-allowed active:scale-95"
                    >
                      <Send className="w-4 h-4" />
                    </button>
                  </div>
                  <div className="flex items-center justify-center gap-2 mt-2">
                    <span className="px-2 py-0.5 rounded-full text-[10px] font-medium bg-orange-500/10 text-orange-400 border border-orange-500/20">供应链数据</span>
                    <span className="px-2 py-0.5 rounded-full text-[10px] font-medium bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">知识库</span>
                    <span className="px-2 py-0.5 rounded-full text-[10px] font-medium bg-sky-500/10 text-sky-400 border border-sky-500/20">联网搜索</span>
                    <span className="px-2 py-0.5 rounded-full text-[10px] font-medium bg-slate-700/50 text-slate-300 border border-slate-600/50">
                      <Zap className="w-2.5 h-2.5 inline-block text-yellow-500 mr-0.5" />DeepSeek V4 Pro
                    </span>
                  </div>
                </div>
              </div>
            )}
            {activeTab === 'dashboard' && renderDashboard()}
            {activeTab === 'smart-quote' && renderSmartQuote()}
            {['quotation', 'warehouse', 'purchase', 'plan', 'accessory'].includes(activeTab) && renderDataTable()}
          </>
        )}
      </div>
    </div>
  );
}
