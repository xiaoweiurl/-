'use client';

import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { backendFetch } from '@/lib/backend-proxy';
import {
  Target, Zap, Check, ArrowLeft, Scissors, Cloud,
  BarChart3, MessageSquare, Send, Bot, User, X, Copy, Globe,
  Lightbulb, Paperclip, FileText, ClipboardList, RefreshCcw
} from 'lucide-react';
import { getCurrentBrand, BRANDS } from '@/lib/brand';
import { cn } from '@/lib/utils';
import { isAdminOrAbove } from '@/lib/auth';
import MarkdownRenderer from '@/components/MarkdownRenderer';
import PdfExportButton from '@/components/PdfExportButton';
import DocumentStatsDashboard from '@/components/DocumentStatsDashboard';

// ============ 类型定义 ============
interface QuotationOrderRow {
  dh: string; chima?: string; huohao?: string;
  sbdj?: number | string; lyl?: number | string; zpl?: number | string;
  rcl?: number | string; zzcb?: number | string; rs?: number | string;
  ykgj?: number | string; qjprice?: number | string;
  fpkz?: number | string; rsdj?: number | string;
  yl?: number | string; qd?: number | string; fl?: number | string; hd?: number | string;
  jcb?: number | string; shuijin?: number | string; shuijinSg?: number | string; xscb?: number | string;
}

type TabKey = 'chat' | 'dashboard';

const TABS: { key: TabKey; label: string; icon: React.ReactNode }[] = [
  { key: 'chat', label: 'AI 对话', icon: <MessageSquare className="w-4 h-4" /> },
  { key: 'dashboard', label: '单据概览', icon: <BarChart3 className="w-4 h-4" /> },
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
      className="p-1 rounded-md text-[#8e8e93] hover:text-[#1c1c1e] hover:bg-[rgba(118,118,128,0.12)] transition-all"
      title="复制内容"
    >
      {copied ? <Check className="w-3.5 h-3.5 text-[#34c759]" /> : <Copy className="w-3.5 h-3.5" />}
    </button>
  );
}
// ============ 主组件 ============
export default function SupplyChainPage() {
  const router = useRouter();
  const [activeTab, setActiveTab] = useState<TabKey>('dashboard');

  // AI对话状态
  const [chatMessages, setChatMessages] = useState<Array<{
    role: 'user' | 'assistant';
    content: string;
    reasoning?: string;
    isThinking?: boolean;
    isStreaming?: boolean;
    searchResults?: Array<{ title: string; url: string }>;
    attachments?: Array<{ name: string; type: string; base64: string; mimeType: string }>;
    quotationList?: { customer: string; total: number; orders: QuotationOrderRow[] };
  }>>([]);
  const [chatInput, setChatInput] = useState('');
  const [chatLoading, setChatLoading] = useState(false);
  // 业务智能体子模式：general=通用业务助手 / planning=商品企划智能体 / decision=决策辅助智能体
  const [chatAgent, setChatAgent] = useState<'general' | 'planning' | 'decision'>('general');
  const chatMessagesEndRef = useRef<HTMLDivElement>(null);
  const isUserScrollingRef = useRef(false);
  const chatScrollContainerRef = useRef<HTMLDivElement>(null);

  // 当前用户角色（用于 ERP 同步入口可见性：仅管理员以上）
  const [currentUserRole, setCurrentUserRole] = useState<string | null>(null);

  // 认证检查 - 后端不可用时进入降级模式；同时获取当前用户角色
  useEffect(() => {
    const sessionId = localStorage.getItem('session_id');
    if (!sessionId) {
      backendFetch('/auth/session').then(res => {
        if (res.status === 502) {
          return;
        } else {
          window.location.href = '/login';
        }
      }).catch(() => {
      });
    }
    fetch('/api/auth/login', { credentials: 'include' })
      .then(res => (res.ok ? res.json() : null))
      .then(data => {
        if (data?.success && data.data?.role) setCurrentUserRole(String(data.data.role).toLowerCase());
      })
      .catch(() => {});
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

  // ============ 退出登录 ============
  // ============ AI对话 ============
  type UploadedAttachment = {
    name: string;
    type: 'image' | 'pdf' | 'document';
    base64: string;
    mimeType: string;
  };
  const [chatAttachments, setChatAttachments] = useState<UploadedAttachment[]>([]);
  const chatFileInputRef = useRef<HTMLInputElement>(null);

  const handleFileUpload = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files) return;
    const maxFiles = 5;
    const maxImageSize = 5 * 1024 * 1024;
    const maxDocSize = 20 * 1024 * 1024;
    Array.from(files).slice(0, maxFiles - chatAttachments.length).forEach(file => {
      const isImage = file.type.startsWith('image/');
      const isPdf = file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf');
      if (!isImage && !isPdf) return;
      const maxSize = isImage ? maxImageSize : maxDocSize;
      if (file.size > maxSize) return;
      const reader = new FileReader();
      reader.onload = () => {
        const base64 = (reader.result as string).split(',')[1];
        if (base64) {
          const attachment: UploadedAttachment = {
            name: file.name,
            type: isImage ? 'image' : (isPdf ? 'pdf' : 'document'),
            base64,
            mimeType: file.type,
          };
          setChatAttachments(prev => [...prev, attachment]);
        }
      };
      reader.readAsDataURL(file);
    });
    if (chatFileInputRef.current) chatFileInputRef.current.value = '';
  }, [chatAttachments.length]);

  const handleFactoryChat = useCallback(async (message?: string) => {
    const msg = message || chatInput.trim();
    if (!msg || chatLoading) return;

    const userMsg = { role: 'user' as const, content: msg, attachments: chatAttachments.length > 0 ? [...chatAttachments] : undefined };
    setChatMessages(prev => [...prev, userMsg]);
    setChatInput('');
    const currentAttachments = [...chatAttachments];
    setChatAttachments([]); // 清空已上传的附件
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
      // 显式智能体选择：general 也传（用于从子模式切回通用助手）
      params.set('subMode', chatAgent);
      // 如果有附件，用POST方式发送
      let res: Response;
      if (currentAttachments.length > 0) {
        const images = currentAttachments.filter(a => a.type === 'image').map(a => a.base64);
        const pdfs = currentAttachments.filter(a => a.type === 'pdf').map(a => ({ name: a.name, base64: a.base64 }));
        const body: Record<string, unknown> = { message: msg, subMode: chatAgent };
        if (images.length > 0) body.images = images;
        if (pdfs.length > 0) body.pdfs = pdfs;
        res = await fetch(`/api/chat/smart?mode=factory`, {
          method: 'POST',
          credentials: 'include',
          headers,
          body: JSON.stringify(body),
        });
      } else {
        res = await fetch(`/api/chat/smart?${params}`, {
          credentials: 'include',
          headers,
        });
      }

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
            } else if (parsed.type === 'quotation_list' && Array.isArray(parsed.orders)) {
              setChatMessages(prev => {
                const updated = [...prev];
                const last = updated[updated.length - 1];
                updated[updated.length - 1] = { ...last, quotationList: { customer: String(parsed.customer || ''), total: Number(parsed.total || parsed.orders.length), orders: parsed.orders.map((o: Record<string, unknown>) => ({ dh: String(o.dh ?? ''), chima: o.chima as string, huohao: o.huohao as string, sbdj: o.sbdj as number, lyl: o.lyl as number, zpl: o.zpl as number, rcl: o.rcl as number, jcb: o.jcb as number, xscb: o.xscb as number })) } };
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
      // 处理buffer中残留的数据
      if (buffer.trim()) {
        const remainingLines = buffer.split('\n');
        for (const line of remainingLines) {
          if (!line.startsWith('data:')) continue;
          const data = line.substring(5).trim();
          if (!data || data === '[DONE]') continue;
          try {
            const parsed = JSON.parse(data);
            if (parsed.type === 'content' && parsed.content) {
              setChatMessages(prev => {
                const updated = [...prev];
                const last = updated[updated.length - 1];
                if (last.role === 'assistant') {
                  updated[updated.length - 1] = { ...last, content: (last.content || '') + parsed.content, isThinking: false };
                }
                return updated;
              });
            } else if (parsed.type === 'done') {
              setChatMessages(prev => {
                const updated = [...prev];
                const last = updated[updated.length - 1];
                if (last.role === 'assistant') {
                  updated[updated.length - 1] = { ...last, isThinking: false, isStreaming: false };
                }
                return updated;
              });
            }
          } catch { /* ignore */ }
        }
      }

      // 确保最终状态正确（防止done事件丢失）
      setChatMessages(prev => {
        const updated = [...prev];
        const last = updated[updated.length - 1];
        if (last.role === 'assistant' && last.isStreaming) {
          updated[updated.length - 1] = { ...last, isThinking: false, isStreaming: false };
        }
        return updated;
      });
    } catch {
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
  }, [chatInput, chatLoading, chatAgent]);

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

  // ============ 主渲染 ============
  const [brand, setBrand] = useState(BRANDS.yingyun);
  const BrandIcon = brand.key === 'bonasi' ? Scissors : Cloud;

  useEffect(() => {
    setBrand(getCurrentBrand());
  }, []);

  return (
    <div className="min-h-screen bg-[#F2F2F7]">
      {/* 顶部导航 */}
      <header className="bg-white/80 backdrop-blur-xl border-b border-[#E5E5EA] sticky top-0 z-50">
        <div className="max-w-[1600px] mx-auto px-5 h-14 flex items-center justify-between">
          <div className="flex items-center gap-3">
              <button
                onClick={() => { localStorage.setItem('back_to_portal', 'true'); router.push('/login'); }}
                className="flex items-center gap-1 text-sm text-[#8e8e93] hover:text-[#1c1c1e] transition-colors shrink-0"
              >
                <ArrowLeft className="w-4 h-4" />
                <span>返回</span>
              </button>
              <span className="text-[#3a3a3c]">|</span>
              <div className={cn("w-8 h-8 rounded-lg flex items-center justify-center text-white shadow-sm", brand.primaryBg)}>
                <BrandIcon className="w-4 h-4" />
              </div>
              <div>
                <h1 className="text-[15px] font-bold text-[#1c1c1e] leading-tight">{brand.name}企业数智中台系统</h1>
                <p className="text-[11px] text-[#8e8e93]">供应链 & 工厂管理</p>
              </div>
            </div>
          <div className="flex items-center gap-2">
            <button onClick={handleLogout}
              className="text-sm text-[#8e8e93] hover:text-[#ff3b30] transition-colors px-3 py-1.5 hover:bg-[rgba(255,59,48,0.1)] rounded-lg">
              退出登录
            </button>
          </div>
        </div>

      </header>

      <div className="max-w-[1600px] mx-auto px-4 py-4">
        {/* Tab导航 */}
        <div className="flex gap-0.5 mb-4 bg-white rounded-2xl p-1 shadow-[0_2px_12px_rgba(0,0,0,0.04)] overflow-x-auto">
          {TABS.map(tab => (
            <button key={tab.key} onClick={() => setActiveTab(tab.key)}
              className={`flex items-center gap-1.5 px-4 py-2 rounded-lg text-[13px] font-medium whitespace-nowrap transition-all ${
                activeTab === tab.key
                  ? 'bg-[#007AFF] text-white shadow-sm'
                  : 'text-[#8e8e93] hover:bg-[rgba(118,118,128,0.12)] hover:text-[#1c1c1e]'
              }`}>
              {tab.icon}{tab.label}
            </button>
          ))}
          {/* 历史订单（独立子页面） */}
          <button onClick={() => router.push('/supply-chain/history-orders')}
            className="flex items-center gap-1.5 px-4 py-2 rounded-lg text-[13px] font-medium whitespace-nowrap transition-all text-[#8e8e93] hover:bg-[rgba(118,118,128,0.12)] hover:text-[#1c1c1e] border-l border-[rgba(229,229,234,0.5)] ml-1 pl-3">
            <ClipboardList className="w-4 h-4" />历史订单
          </button>
          {/* ERP 数据同步（仅管理员以上可见） */}
          {isAdminOrAbove(currentUserRole) && (
            <button onClick={() => router.push('/erp-sync')}
              className="flex items-center gap-1.5 px-4 py-2 rounded-lg text-[13px] font-medium whitespace-nowrap transition-all text-[#8e8e93] hover:bg-[rgba(118,118,128,0.12)] hover:text-[#1c1c1e]">
              <RefreshCcw className="w-4 h-4" />ERP 同步
            </button>
          )}
        </div>

        {/* 内容区 */}
        <>
            {activeTab === 'chat' && (
              <div className="flex flex-col h-[calc(100vh-140px)] bg-white rounded-2xl shadow-[0_2px_12px_rgba(0,0,0,0.04)] overflow-hidden">
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
                      <div className="w-16 h-16 rounded-2xl bg-[#007AFF] flex items-center justify-center mb-4 shadow-[0_2px_12px_rgba(0,0,0,0.04)]">
                        <MessageSquare className="w-8 h-8 text-white" />
                      </div>
                      <h2 className="text-xl font-bold text-[#1c1c1e] mb-2">工厂AI助手</h2>
                      <p className="text-sm text-[#8e8e93] mb-6 max-w-md leading-relaxed">基于DeepSeek大模型，专注供应链与工厂业务知识，支持产品报价、原料采购、生产计划等智能问答</p>
                      <div className="flex flex-wrap gap-2 justify-center">
                        <span className="px-3 py-1 rounded-full text-xs font-medium bg-[rgba(255,149,0,0.1)] text-[#ff9500] border border-[rgba(255,149,0,0.2)]">产品报价</span>
                        <span className="px-3 py-1 rounded-full text-xs font-medium bg-[rgba(0,122,255,0.1)] text-[#007aff] border border-[rgba(0,122,255,0.2)]">原料采购</span>
                        <span className="px-3 py-1 rounded-full text-xs font-medium bg-[rgba(52,199,89,0.1)] text-[#34c759] border border-[rgba(52,199,89,0.2)]">生产计划</span>
                        <span className="px-3 py-1 rounded-full text-xs font-medium bg-[rgba(0,122,255,0.1)] text-[#007aff] border border-[rgba(0,122,255,0.2)]">联网搜索</span>
                        <span className="px-3 py-1 rounded-full text-xs font-medium bg-[rgba(118,118,128,0.12)] text-[#3a3a3c] border border-[#E5E5EA]">
                          <Zap className="w-3 h-3 inline-block text-[#ff9500] mr-0.5" />DeepSeek V4 Pro
                        </span>
                      </div>
                      <div className="mt-6 flex flex-col gap-2.5">
                        {['帮我分析原料采购的供应商报价', '最新的涤纶面料市场行情如何', '查询产品SK-H001的成本构成'].map(q => (
                          <button key={q} onClick={() => handleFactoryChat(q)}
                            className="text-left px-4 py-3 text-sm text-[#3a3a3c] bg-white hover:bg-[#F9F9FB] rounded-xl transition-all shadow-[0_2px_12px_rgba(0,0,0,0.04)] hover:shadow-[0_6px_24px_rgba(0,0,0,0.08)]">
                            <span className="text-[#8e8e93] mr-2">→</span>{q}
                          </button>
                        ))}
                      </div>
                    </div>
                  )}
                  {chatMessages.map((msg, idx) => (
                    <div key={idx} className={`flex gap-3 ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}>
                      {msg.role === 'assistant' && (
                        <div className="shrink-0 w-8 h-8 rounded-lg bg-[#007AFF] flex items-center justify-center shadow-sm mt-0.5">
                          <Bot className="w-4 h-4 text-white" />
                        </div>
                      )}
                      <div className={`max-w-[80%] group/msg ${msg.role === 'user' ? 'order-first' : ''}`}>
                        {/* 思维链（DeepSeek思考模式） */}
                        {msg.role === 'assistant' && msg.reasoning && msg.reasoning.length > 0 && (
                          <details className="mb-2.5 group">
                            <summary className="flex items-center gap-2 text-[11px] text-[#8e8e93] cursor-pointer hover:text-[#1c1c1e] transition-colors select-none py-1">
                              <svg className="w-3 h-3 transition-transform group-open:rotate-90 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                              </svg>
                              <Lightbulb className="w-3 h-3 shrink-0" />
                              {msg.isThinking ? (
                                <span className="flex items-center gap-1.5">
                                  <span className="inline-block w-1.5 h-1.5 rounded-full bg-[rgba(0,0,0,0.12)] animate-pulse" />
                                  正在深度思考...
                                </span>
                              ) : (
                                <span>思考过程</span>
                              )}
                            </summary>
                            <div className="mt-1.5 p-3 bg-white border border-[rgba(229,229,234,0.6)] rounded-xl text-[11.5px] text-[#8e8e93] leading-relaxed max-h-52 overflow-y-auto whitespace-pre-wrap shadow-sm">
                              {msg.reasoning}
                            </div>
                          </details>
                        )}

                        {/* 报价单全量列表(结构化渲染, 零省略) */}
                        {msg.role === 'assistant' && msg.quotationList && msg.quotationList.orders.length > 0 && (
                          <div className="mb-2.5 rounded-xl border border-[#007AFF]/20 bg-[#007AFF]/[0.04] p-3">
                            <div className="flex items-center gap-2 mb-2">
                              <span className="text-[11px] font-semibold text-[#007aff]">客户「{msg.quotationList.customer}」报价单号全量列表</span>
                              <span className="text-[10px] px-1.5 py-0.5 rounded bg-[rgba(0,122,255,0.2)] text-[#007aff] font-mono">共 {msg.quotationList.total} 个</span>
                            </div>
                            <div className="max-h-96 overflow-auto rounded-lg border border-[rgba(229,229,234,0.6)]">
                              <table className="w-full min-w-[1200px] text-[11px]">
                                <thead className="sticky top-0 bg-[#ffffff] text-[#3a3a3c]">
                                  <tr>
                                    <th className="px-2 py-1.5 text-left font-medium">报价单号</th>
                                    <th className="px-2 py-1.5 text-left font-medium">尺码</th>
                                    <th className="px-2 py-1.5 text-right font-medium">日产量</th>
                                    <th className="px-2 py-1.5 text-right font-medium">织造成本</th>
                                    <th className="px-2 py-1.5 text-right font-medium">腰口工价</th>
                                    <th className="px-2 py-1.5 text-right font-medium">染色成本</th>
                                    <th className="px-2 py-1.5 text-right font-medium">原料金额</th>
                                    <th className="px-2 py-1.5 text-right font-medium">前道合计</th>
                                    <th className="px-2 py-1.5 text-right font-medium">辅料金额</th>
                                    <th className="px-2 py-1.5 text-right font-medium">全检工价</th>
                                    <th className="px-2 py-1.5 text-right font-medium">后道合计</th>
                                    <th className="px-2 py-1.5 text-right font-medium">净成本</th>
                                    <th className="px-2 py-1.5 text-right font-medium">理论税金</th>
                                    <th className="px-2 py-1.5 text-right font-medium">实际税金</th>
                                    <th className="px-2 py-1.5 text-right font-medium">销售成本</th>
                                  </tr>
                                </thead>
                                <tbody>
                                  {msg.quotationList.orders.map((o, i) => (
                                    <tr key={i} className={i % 2 === 0 ? 'bg-[rgba(242,242,247,0.4)]' : 'bg-white'}>
                                      <td className="px-2 py-1 font-mono text-[#1c1c1e] whitespace-nowrap">{o.dh}</td>
                                      <td className="px-2 py-1 text-[#3a3a3c]">{o.chima ?? '-'}</td>
                                      <td className="px-2 py-1 text-right font-mono text-[#3a3a3c]">{o.rcl != null ? Number(o.rcl).toFixed(2) : '-'}</td>
                                      <td className="px-2 py-1 text-right font-mono text-[#3a3a3c]">{o.zzcb != null ? Number(o.zzcb).toFixed(2) : '-'}</td>
                                      <td className="px-2 py-1 text-right font-mono text-[#3a3a3c]">{o.ykgj != null ? Number(o.ykgj).toFixed(2) : '-'}</td>
                                      <td className="px-2 py-1 text-right font-mono text-[#3a3a3c]">{o.rs != null ? Number(o.rs).toFixed(2) : '-'}</td>
                                      <td className="px-2 py-1 text-right font-mono text-[#3a3a3c]">{o.yl != null ? Number(o.yl).toFixed(2) : '-'}</td>
                                      <td className="px-2 py-1 text-right font-mono text-[#3a3a3c]">{o.qd != null ? Number(o.qd).toFixed(2) : '-'}</td>
                                      <td className="px-2 py-1 text-right font-mono text-[#3a3a3c]">{o.fl != null ? Number(o.fl).toFixed(2) : '-'}</td>
                                      <td className="px-2 py-1 text-right font-mono text-[#3a3a3c]">{o.qjprice != null ? Number(o.qjprice).toFixed(2) : '-'}</td>
                                      <td className="px-2 py-1 text-right font-mono text-[#3a3a3c]">{o.hd != null ? Number(o.hd).toFixed(2) : '-'}</td>
                                      <td className="px-2 py-1 text-right font-mono text-[#ff3b30]">{o.jcb != null ? Number(o.jcb).toFixed(2) : '-'}</td>
                                      <td className="px-2 py-1 text-right font-mono text-[#ff9500]">{o.shuijin != null ? Number(o.shuijin).toFixed(2) : '-'}</td>
                                      <td className="px-2 py-1 text-right font-mono text-[#ff9500]">{o.shuijinSg != null ? Number(o.shuijinSg).toFixed(2) : '-'}</td>
                                      <td className="px-2 py-1 text-right font-mono text-[#ff3b30]">{o.xscb != null ? Number(o.xscb).toFixed(2) : '-'}</td>
                                    </tr>
                                  ))}
                                </tbody>
                              </table>
                            </div>
                          </div>
                        )}

                        {/* 联网搜索结果 */}
                        {msg.role === 'assistant' && msg.searchResults && msg.searchResults.length > 0 && (
                          <details className="mb-2.5 group" open>
                            <summary className="flex items-center gap-2 text-[11px] text-[#007aff] cursor-pointer hover:text-[#007aff] transition-colors select-none py-1">
                              <svg className="w-3 h-3 transition-transform group-open:rotate-90 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                              </svg>
                              <Globe className="w-3 h-3 shrink-0" />
                              <span>联网搜索结果</span>
                            </summary>
                            <div className="mt-1.5 text-[11.5px] bg-[#007AFF] rounded-xl p-3 border border-[rgba(0,122,255,0.2)] shadow-sm space-y-1.5">
                              {msg.searchResults.map((r, i) => (
                                <div key={i} className="text-[#007aff] truncate">
                                  <span className="text-[#007aff] mr-1">{i + 1}.</span>
                                  {r.title}
                                </div>
                              ))}
                            </div>
                          </details>
                        )}

                        {/* 消息内容 */}
                        <div className={`relative group/msg
                          ${msg.role === 'user'
                            ? 'px-4 py-3 rounded-2xl rounded-tr-md bg-[#007AFF] text-white shadow-md shadow-[0_2px_12px_rgba(0,0,0,0.04)]'
                            : 'px-4 py-3 rounded-2xl rounded-tl-md bg-white border border-[#E5E5EA] text-[#3a3a3c] shadow-sm'}`}
                        >
                          {msg.role === 'user' ? (
                            <div>
                              {msg.attachments && msg.attachments.length > 0 && (
                                <div className="flex gap-1.5 mb-2 flex-wrap">
                                  {msg.attachments.map((att, i) => (
                                    att.type === 'image' ? (
                                      <img key={i} src={`data:${att.mimeType};base64,${att.base64}`} alt={att.name} className="w-16 h-16 rounded-lg object-cover opacity-90" />
                                    ) : (
                                      <div key={i} className="flex items-center gap-1 px-2 py-1 rounded-lg bg-[rgba(118,118,128,0.12)] text-[#1C1C1E]/80 text-[11px]">
                                        <FileText className="w-3.5 h-3.5 text-[#ff3b30]" />
                                        <span className="truncate max-w-[80px]">{att.name}</span>
                                      </div>
                                    )
                                  ))}
                                </div>
                              )}
                              <div className="whitespace-pre-wrap text-[13px] leading-relaxed">{msg.content}</div>
                            </div>
                          ) : msg.content ? (
                            <MarkdownRenderer content={msg.content || ''} darkMode />
                          ) : (
                            /* AI 思考中加载动画 */
                            <div className="flex items-center gap-3 py-1">
                              <div className="flex gap-1">
                                <span className="w-2 h-2 rounded-full bg-[#007aff] animate-[bounce_1.4s_ease-in-out_infinite]" style={{animationDelay: '0s'}} />
                                <span className="w-2 h-2 rounded-full bg-[#007aff] animate-[bounce_1.4s_ease-in-out_infinite]" style={{animationDelay: '0.2s'}} />
                                <span className="w-2 h-2 rounded-full bg-[#007aff] animate-[bounce_1.4s_ease-in-out_infinite]" style={{animationDelay: '0.4s'}} />
                              </div>
                              <span className="text-xs text-[#8e8e93] animate-pulse">AI 正在检索知识库并思考...</span>
                            </div>
                          )}
                          {msg.isStreaming && (
                            <span className={`inline-block w-1.5 h-4 ml-0.5 align-middle animate-pulse rounded-full
                              ${msg.isThinking ? 'bg-[#007aff]' : 'bg-[#007aff]'}`} />
                          )}
                          {/* 复制/导出PDF按钮 - 仅assistant消息完成时显示 */}
                          {msg.role === 'assistant' && !msg.isStreaming && msg.content && (
                            <div className="absolute -right-1 -top-1 opacity-0 group-hover/msg:opacity-100 transition-opacity flex items-center gap-0.5">
                              <PdfExportButton content={msg.content} />
                              <CopyButton text={msg.content} />
                            </div>
                          )}
                        </div>
                      </div>
                      {msg.role === 'user' && (
                        <div className="shrink-0 w-8 h-8 rounded-lg bg-[#8E8E93] flex items-center justify-center shadow-sm mt-0.5">
                          <User className="w-4 h-4 text-white" />
                        </div>
                      )}
                    </div>
                  ))}
                  <div ref={chatMessagesEndRef} />
                </div>
                {/* 输入区 */}
                <div className="border-t border-[#E5E5EA] px-4 py-3 bg-white">
                  {/* 业务智能体切换：点击即切换对应智能体，消息显式携带 subMode */}
                  <div className="flex items-center gap-1.5 mb-2.5 p-1 rounded-xl bg-[rgba(242,242,247,0.6)] border border-[#E5E5EA] w-fit">
                    {([
                      { key: 'general', label: '通用助手', icon: <Bot className="w-3.5 h-3.5" />, activeCls: 'bg-[rgba(118,118,128,0.12)] text-[#1c1c1e] shadow-sm' },
                      { key: 'planning', label: '商品企划', icon: <Lightbulb className="w-3.5 h-3.5" />, activeCls: 'bg-[#007AFF] text-white shadow-sm shadow-[0_2px_12px_rgba(0,0,0,0.04)]' },
                      { key: 'decision', label: '决策辅助', icon: <Target className="w-3.5 h-3.5" />, activeCls: 'bg-[#007AFF] text-white shadow-sm shadow-[0_2px_12px_rgba(0,0,0,0.04)]' },
                    ] as const).map(agent => (
                      <button
                        key={agent.key}
                        onClick={() => {
                          if (chatAgent === agent.key) return;
                          setChatAgent(agent.key);
                          const tip = agent.key === 'planning'
                            ? '已切换到【商品企划智能体】。请输入客户名/品牌名/品类启动企划（可选补充国家、渠道、价格、季节），我将输出企划任务卡并按7步流程多轮共创。'
                            : agent.key === 'decision'
                            ? '已切换到【决策辅助智能体】。请提出待决策的经营议题（产能/客户/外部环境/研发/人效/报价利润），我将按六维分析输出A/B/C备选方案，最终决策由总经理确认。'
                            : '已切换回【通用业务助手】。报价、成本、原料、供应商、生产计划等业务问题都可以直接提问。';
                          setChatMessages(prev => [...prev, { role: 'assistant' as const, content: tip }]);
                        }}
                        className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium transition-all
                          ${chatAgent === agent.key ? agent.activeCls : 'text-[#8e8e93] hover:text-[#1c1c1e] hover:bg-white'}`}
                      >
                        {agent.icon}
                        {agent.label}
                      </button>
                    ))}
                  </div>
                  {chatAttachments.length > 0 && (
                    <div className="flex gap-2 mb-2 flex-wrap">
                      {chatAttachments.map((att, i) => (
                        <div key={i} className="relative group">
                          {att.type === 'image' ? (
                            <img src={`data:${att.mimeType};base64,${att.base64}`} alt={att.name} className="w-12 h-12 rounded-lg object-cover border border-[#e5e5ea]" />
                          ) : (
                            <div className="w-12 h-12 rounded-lg border border-[#e5e5ea] bg-[#f2f2f7] flex flex-col items-center justify-center gap-0.5">
                              <FileText className="w-4 h-4 text-[#ff3b30]" />
                              <span className="text-[7px] text-[#8e8e93] truncate max-w-[40px] px-0.5">{att.name.length > 6 ? att.name.slice(0, 6) + '...' : att.name}</span>
                            </div>
                          )}
                          <button
                            onClick={() => setChatAttachments(prev => prev.filter((_, idx) => idx !== i))}
                            className="absolute -top-1 -right-1 w-4 h-4 rounded-full bg-[#ff3b30] text-white text-[10px] flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity"
                          ><X className="w-2.5 h-2.5" /></button>
                        </div>
                      ))}
                    </div>
                  )}
                  <div className="flex gap-2 items-end">
                    <input ref={chatFileInputRef} type="file" accept="image/*,.pdf" multiple className="hidden" onChange={handleFileUpload} />
                    <button
                      onClick={() => chatFileInputRef.current?.click()}
                      disabled={chatLoading || chatAttachments.length >= 5}
                      className={`shrink-0 w-10 h-10 rounded-xl flex items-center justify-center transition-all border
                        ${chatLoading || chatAttachments.length >= 5
                          ? 'bg-[rgba(118,118,128,0.12)] text-[#8e8e93] border-[rgba(229,229,234,0.5)] cursor-not-allowed'
                          : 'bg-[rgba(0,122,255,0.12)] text-[#007aff] border-transparent hover:bg-[rgba(0,122,255,0.2)] shadow-sm'}`}
                      title="上传图片或PDF文档(最多5个)"
                    >
                      <Paperclip className="w-[18px] h-[18px]" />
                    </button>
                    <textarea
                      value={chatInput}
                      onChange={e => setChatInput(e.target.value)}
                      onKeyDown={e => {
                        if (e.key === 'Enter' && !e.shiftKey) {
                          e.preventDefault();
                          handleFactoryChat();
                        }
                      }}
                      placeholder={chatAgent === 'planning'
                        ? '商品企划模式：输入客户名/品牌名/品类启动企划...'
                        : chatAgent === 'decision'
                        ? '决策辅助模式：提出待决策的经营议题...'
                        : '输入工厂业务问题...'}
                      rows={1}
                      className="flex-1 resize-none rounded-xl border border-[#E5E5EA] bg-[rgba(118,118,128,0.12)] px-4 py-2.5 text-sm text-[#1c1c1e] focus:outline-none focus:ring-2 focus:ring-[rgba(0,122,255,0.3)] focus:border-[rgba(0,122,255,0.5)] transition-all placeholder:text-[#8e8e93]"
                    />
                    <button
                      onClick={() => handleFactoryChat()}
                      disabled={chatLoading || !chatInput.trim()}
                      className="shrink-0 w-10 h-10 rounded-xl bg-[#007AFF] text-white flex items-center justify-center hover:opacity-90 transition-all disabled:opacity-40 disabled:cursor-not-allowed active:scale-95"
                    >
                      <Send className="w-4 h-4" />
                    </button>
                  </div>
                  <div className="flex items-center justify-center gap-2 mt-2">
                    <span className="px-2 py-0.5 rounded-full text-[10px] font-medium bg-[rgba(255,149,0,0.1)] text-[#ff9500] border border-[rgba(255,149,0,0.2)]">供应链数据</span>
                    <span className="px-2 py-0.5 rounded-full text-[10px] font-medium bg-[rgba(52,199,89,0.1)] text-[#34c759] border border-[rgba(52,199,89,0.2)]">知识库</span>
                    <span className="px-2 py-0.5 rounded-full text-[10px] font-medium bg-[rgba(0,122,255,0.1)] text-[#007aff] border border-[rgba(0,122,255,0.2)]">联网搜索</span>
                    <span className="px-2 py-0.5 rounded-full text-[10px] font-medium bg-[rgba(118,118,128,0.12)] text-[#3a3a3c] border border-[#E5E5EA]">
                      <Zap className="w-2.5 h-2.5 inline-block text-[#ff9500] mr-0.5" />DeepSeek V4 Pro
                    </span>
                  </div>
                </div>
              </div>
            )}
            {activeTab === 'dashboard' && <DocumentStatsDashboard />}
          </>
      </div>
    </div>
  );
}
