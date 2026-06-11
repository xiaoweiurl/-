'use client';

import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  Brain, Plus, Search, Send, Package, FlaskConical, Users, Swords,
  Truck, ShieldCheck, Calculator, Gavel, Tag, ChevronRight,
  MessageSquare, X, Eye, Clock, CheckCircle, AlertCircle, Sparkles,
  BookOpen, Filter, Loader2
} from 'lucide-react';

// 知识域图标映射
const DOMAIN_ICONS: Record<string, React.ReactNode> = {
  product: <Package className="w-4 h-4" />,
  rd: <FlaskConical className="w-4 h-4" />,
  customer: <Users className="w-4 h-4" />,
  competitive: <Swords className="w-4 h-4" />,
  supply_chain: <Truck className="w-4 h-4" />,
  quality: <ShieldCheck className="w-4 h-4" />,
  finance: <Calculator className="w-4 h-4" />,
  governance: <Gavel className="w-4 h-4" />,
};

const DOMAIN_COLORS: Record<string, string> = {
  product: 'from-violet-500 to-purple-600',
  rd: 'from-blue-500 to-cyan-600',
  customer: 'from-green-500 to-emerald-600',
  competitive: 'from-red-500 to-rose-600',
  supply_chain: 'from-orange-500 to-amber-600',
  quality: 'from-emerald-500 to-teal-600',
  finance: 'from-yellow-500 to-orange-600',
  governance: 'from-slate-500 to-gray-600',
};

interface Domain {
  code: string;
  name: string;
  description: string;
  icon: string;
  color: string;
  sort_order: number;
  published_count: string;
  total_count: string;
}

interface KnowledgeCard {
  id: string;
  domain_code: string;
  domain_name: string;
  domain_icon: string;
  domain_color: string;
  title: string;
  content: string;
  tags: string[];
  product_code: string | null;
  source: string | null;
  confidence: string;
  status: string;
  created_by: string;
  created_at: string;
}

interface ChatMessage {
  role: 'user' | 'assistant' | 'system';
  content: string;
  sources?: Array<{ id: string; title: string; domain: string; score: number }>;
  isLoading?: boolean;
}

export default function MemoryPage() {
  const [domains, setDomains] = useState<Domain[]>([]);
  const [activeDomain, setActiveDomain] = useState<string>('');
  const [cards, setCards] = useState<KnowledgeCard[]>([]);
  const [cardsTotal, setCardsTotal] = useState(0);
  const [cardsPage, setCardsPage] = useState(1);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [viewingCard, setViewingCard] = useState<KnowledgeCard | null>(null);

  // AI问答
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([]);
  const [chatInput, setChatInput] = useState('');
  const [isChatting, setIsChatting] = useState(false);
  const chatEndRef = useRef<HTMLDivElement>(null);
  const [showChat, setShowChat] = useState(false);

  // 新建卡片表单
  const [newCard, setNewCard] = useState({
    domainCode: '',
    title: '',
    content: '',
    tags: '',
    productCode: '',
    source: '',
    confidence: 'medium',
  });
  const [isCreating, setIsCreating] = useState(false);

  // 加载知识域
  useEffect(() => {
    fetchDomains();
  }, []);

  // 切换知识域时加载卡片
  useEffect(() => {
    if (activeDomain) {
      setCardsPage(1);
      fetchCards();
    }
  }, [activeDomain, cardsPage]);

  const fetchDomains = async () => {
    try {
      const res = await fetch('/api/memory/domains');
      const data = await res.json();
      if (data.success) {
        setDomains(data.domains);
        if (data.domains.length > 0 && !activeDomain) {
          setActiveDomain(data.domains[0].code);
          setNewCard(prev => ({ ...prev, domainCode: data.domains[0].code }));
        }
      }
    } catch (err) {
      console.error('加载知识域失败:', err);
    }
  };

  const fetchCards = async () => {
    try {
      const params = new URLSearchParams({
        domain: activeDomain,
        status: 'published',
        page: cardsPage.toString(),
        pageSize: '20',
      });
      if (searchKeyword) params.set('keyword', searchKeyword);
      const res = await fetch(`/api/memory/cards?${params}`);
      const data = await res.json();
      if (data.success) {
        setCards(data.cards || []);
        setCardsTotal(data.total);
      }
    } catch (err) {
      console.error('加载卡片失败:', err);
    }
  };

  const handleCreateCard = async () => {
    if (!newCard.title || !newCard.content) return;
    setIsCreating(true);
    try {
      const res = await fetch('/api/memory/cards', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          domainCode: newCard.domainCode,
          title: newCard.title,
          content: newCard.content,
          tags: newCard.tags ? newCard.tags.split(',').map(t => t.trim()).filter(Boolean) : [],
          productCode: newCard.productCode || undefined,
          source: newCard.source || undefined,
          confidence: newCard.confidence,
          createdBy: 'admin',
        }),
      });
      const data = await res.json();
      if (data.success) {
        setShowCreateModal(false);
        setNewCard(prev => ({ ...prev, title: '', content: '', tags: '', productCode: '', source: '' }));
        fetchCards();
        fetchDomains();
      } else {
        alert(data.error || '创建失败');
      }
    } catch (err) {
      console.error('创建卡片失败:', err);
    } finally {
      setIsCreating(false);
    }
  };

  // AI问答
  const handleSendChat = useCallback(async () => {
    if (!chatInput.trim() || isChatting) return;

    const userMsg: ChatMessage = { role: 'user', content: chatInput.trim() };
    setChatMessages(prev => [...prev, userMsg]);
    setChatInput('');
    setIsChatting(true);

    // 添加loading占位
    const assistantMsg: ChatMessage = {
      role: 'assistant',
      content: '',
      sources: [],
      isLoading: true,
    };
    setChatMessages(prev => [...prev, assistantMsg]);

    try {
      const res = await fetch('/api/memory/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          message: userMsg.content,
          history: chatMessages.slice(-10).map(m => ({ role: m.role, content: m.content })),
          domainCode: activeDomain || undefined,
        }),
      });

      if (!res.ok) throw new Error('请求失败');

      const reader = res.body?.getReader();
      if (!reader) throw new Error('无法读取响应');

      const decoder = new TextDecoder();
      let fullContent = '';
      let sources: ChatMessage['sources'] = [];

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        const text = decoder.decode(value, { stream: true });
        const lines = text.split('\n');

        for (const line of lines) {
          if (line.startsWith('data: ')) {
            const data = line.slice(6);
            if (data === '[DONE]') continue;
            try {
              const parsed = JSON.parse(data);
              if (parsed.type === 'sources') {
                sources = parsed.sources;
              } else if (parsed.type === 'content') {
                fullContent += parsed.content;
                setChatMessages(prev => {
                  const updated = [...prev];
                  updated[updated.length - 1] = {
                    role: 'assistant',
                    content: fullContent,
                    sources,
                    isLoading: false,
                  };
                  return updated;
                });
              } else if (parsed.type === 'error') {
                fullContent += `\n\n❌ ${parsed.error}`;
              }
            } catch {
              // ignore
            }
          }
        }
      }

      setChatMessages(prev => {
        const updated = [...prev];
        updated[updated.length - 1] = {
          role: 'assistant',
          content: fullContent,
          sources,
          isLoading: false,
        };
        return updated;
      });
    } catch (err) {
      console.error('对话失败:', err);
      setChatMessages(prev => {
        const updated = [...prev];
        updated[updated.length - 1] = {
          role: 'assistant',
          content: '抱歉，对话出现错误，请稍后重试。',
          isLoading: false,
        };
        return updated;
      });
    } finally {
      setIsChatting(false);
    }
  }, [chatInput, isChatting, chatMessages, activeDomain]);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [chatMessages]);

  const handleSearch = () => {
    setCardsPage(1);
    fetchCards();
  };

  const confidenceLabel: Record<string, { text: string; color: string }> = {
    high: { text: '高', color: 'text-green-600 bg-green-50' },
    medium: { text: '中', color: 'text-yellow-600 bg-yellow-50' },
    low: { text: '低', color: 'text-red-600 bg-red-50' },
  };

  return (
    <div className="flex h-screen bg-slate-50">
      {/* 左侧 - 知识域导航 */}
      <div className="w-64 bg-white border-r border-slate-200 flex flex-col flex-shrink-0">
        <div className="p-4 border-b border-slate-200">
          <div className="flex items-center gap-2">
            <Brain className="w-5 h-5 text-violet-600" />
            <h1 className="font-bold text-slate-800 text-lg">记忆库</h1>
          </div>
          <p className="text-xs text-slate-500 mt-1">企业知识 · 卡片化管理 · 语义检索</p>
        </div>

        <div className="flex-1 overflow-y-auto p-2">
          {domains.map((domain) => {
            const isActive = activeDomain === domain.code;
            const count = parseInt(domain.published_count || '0');
            return (
              <button
                key={domain.code}
                onClick={() => setActiveDomain(domain.code)}
                className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-left transition-all duration-200 mb-1 ${
                  isActive
                    ? 'bg-gradient-to-r from-violet-50 to-purple-50 text-violet-700 shadow-sm'
                    : 'text-slate-600 hover:bg-slate-50'
                }`}
              >
                <div className={`w-8 h-8 rounded-lg flex items-center justify-center ${
                  isActive
                    ? `bg-gradient-to-br ${DOMAIN_COLORS[domain.code] || 'from-slate-400 to-slate-500'} text-white`
                    : 'bg-slate-100 text-slate-500'
                }`}>
                  {DOMAIN_ICONS[domain.code] || <BookOpen className="w-4 h-4" />}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="text-sm font-medium truncate">{domain.name}</div>
                  <div className="text-xs text-slate-400">{count} 张卡片</div>
                </div>
                {isActive && <ChevronRight className="w-4 h-4 text-violet-400" />}
              </button>
            );
          })}
        </div>

        <div className="p-3 border-t border-slate-200">
          <button
            onClick={() => setShowChat(!showChat)}
            className={`w-full flex items-center gap-2 px-3 py-2.5 rounded-xl text-sm font-medium transition-all ${
              showChat
                ? 'bg-gradient-to-r from-violet-500 to-purple-600 text-white shadow-lg'
                : 'bg-gradient-to-r from-violet-50 to-purple-50 text-violet-700 hover:shadow-md'
            }`}
          >
            <Sparkles className="w-4 h-4" />
            AI 智能问答
          </button>
        </div>
      </div>

      {/* 中间 - 卡片列表 */}
      <div className="flex-1 flex flex-col min-w-0">
        {/* 顶部操作栏 */}
        <div className="bg-white border-b border-slate-200 px-6 py-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <h2 className="text-lg font-semibold text-slate-800">
                {domains.find(d => d.code === activeDomain)?.name || '知识卡片'}
              </h2>
              <span className="text-sm text-slate-400">{cardsTotal} 条</span>
            </div>
            <div className="flex items-center gap-3">
              <div className="relative">
                <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type="text"
                  placeholder="搜索卡片..."
                  value={searchKeyword}
                  onChange={(e) => setSearchKeyword(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
                  className="pl-9 pr-4 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-violet-300 focus:border-violet-300 w-56"
                />
              </div>
              <button
                onClick={() => {
                  setNewCard(prev => ({ ...prev, domainCode: activeDomain }));
                  setShowCreateModal(true);
                }}
                className="flex items-center gap-1.5 px-4 py-2 bg-gradient-to-r from-violet-500 to-purple-600 text-white rounded-xl text-sm font-medium hover:shadow-lg transition-all"
              >
                <Plus className="w-4 h-4" />
                新建卡片
              </button>
            </div>
          </div>
          {activeDomain && (
            <p className="text-xs text-slate-500 mt-1">
              {domains.find(d => d.code === activeDomain)?.description}
            </p>
          )}
        </div>

        {/* 卡片网格 */}
        <div className="flex-1 overflow-y-auto p-6">
          {cards.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-full text-slate-400">
              <BookOpen className="w-16 h-16 mb-4 text-slate-300" />
              <p className="text-lg font-medium">暂无知识卡片</p>
              <p className="text-sm mt-1">点击「新建卡片」开始沉淀知识</p>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
              {cards.map((card) => (
                <div
                  key={card.id}
                  onClick={() => setViewingCard(card)}
                  className="bg-white rounded-2xl border border-slate-200 p-4 hover:shadow-lg hover:-translate-y-0.5 transition-all duration-200 cursor-pointer group"
                >
                  <div className="flex items-start justify-between mb-2">
                    <div className="flex items-center gap-2">
                      <div className={`w-6 h-6 rounded-md flex items-center justify-center bg-gradient-to-br ${DOMAIN_COLORS[card.domain_code] || 'from-slate-400 to-slate-500'} text-white`}>
                        {DOMAIN_ICONS[card.domain_code] || <BookOpen className="w-3 h-3" />}
                      </div>
                      <span className="text-xs text-slate-400">{card.domain_name}</span>
                    </div>
                    {card.confidence && confidenceLabel[card.confidence] && (
                      <span className={`text-xs px-1.5 py-0.5 rounded-md font-medium ${confidenceLabel[card.confidence].color}`}>
                        {confidenceLabel[card.confidence].text}
                      </span>
                    )}
                  </div>
                  <h3 className="font-semibold text-slate-800 text-sm mb-2 line-clamp-2 group-hover:text-violet-700 transition-colors">
                    {card.title}
                  </h3>
                  <p className="text-xs text-slate-500 line-clamp-3 mb-3">{card.content}</p>
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-1 flex-wrap">
                      {(card.tags || []).slice(0, 3).map((tag, i) => (
                        <span key={i} className="text-xs px-1.5 py-0.5 bg-slate-100 text-slate-500 rounded">
                          {tag}
                        </span>
                      ))}
                      {(card.tags || []).length > 3 && (
                        <span className="text-xs text-slate-400">+{card.tags.length - 3}</span>
                      )}
                    </div>
                    <div className="flex items-center gap-1 text-slate-400">
                      <Clock className="w-3 h-3" />
                      <span className="text-xs">{new Date(card.created_at).toLocaleDateString()}</span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* 右侧 - AI问答面板 */}
      {showChat && (
        <div className="w-96 bg-white border-l border-slate-200 flex flex-col flex-shrink-0">
          <div className="p-4 border-b border-slate-200">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Sparkles className="w-5 h-5 text-violet-600" />
                <h3 className="font-semibold text-slate-800">AI 问答</h3>
              </div>
              <button
                onClick={() => setShowChat(false)}
                className="p-1 hover:bg-slate-100 rounded-lg"
              >
                <X className="w-4 h-4 text-slate-500" />
              </button>
            </div>
            <p className="text-xs text-slate-500 mt-1">
              基于记忆库语义检索 · 向量数据库驱动
            </p>
          </div>

          <div className="flex-1 overflow-y-auto p-4 space-y-4">
            {chatMessages.length === 0 && (
              <div className="text-center py-8">
                <Brain className="w-12 h-12 mx-auto text-violet-300 mb-3" />
                <p className="text-sm text-slate-500">向AI助手提问，获取基于记忆库的专业解答</p>
                <div className="mt-4 space-y-2">
                  {['HT01产品的成本构成是什么？', '锦纶纱线的供应商有哪些？', '丝袜织造工艺的关键参数？'].map((q, i) => (
                    <button
                      key={i}
                      onClick={() => setChatInput(q)}
                      className="block w-full text-left text-xs px-3 py-2 bg-slate-50 rounded-lg hover:bg-violet-50 hover:text-violet-700 transition-colors"
                    >
                      💡 {q}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {chatMessages.map((msg, i) => (
              <div key={i} className={`${msg.role === 'user' ? 'text-right' : 'text-left'}`}>
                <div className={`inline-block max-w-[85%] px-4 py-2.5 rounded-2xl text-sm ${
                  msg.role === 'user'
                    ? 'bg-gradient-to-r from-violet-500 to-purple-600 text-white'
                    : 'bg-slate-100 text-slate-800'
                }`}>
                  <div className="whitespace-pre-wrap">{msg.content || '...'}</div>
                </div>
                {msg.sources && msg.sources.length > 0 && (
                  <div className="mt-1.5 space-y-1">
                    <span className="text-xs text-slate-400">参考来源:</span>
                    {msg.sources.map((s, j) => (
                      <div key={j} className="flex items-center gap-1 text-xs text-violet-600">
                        <CheckCircle className="w-3 h-3" />
                        <span>{s.title}</span>
                        <span className="text-slate-400">({s.domain}, {(s.score * 100).toFixed(0)}%)</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            ))}
            <div ref={chatEndRef} />
          </div>

          <div className="p-4 border-t border-slate-200">
            <div className="flex items-center gap-2">
              <input
                type="text"
                placeholder="输入问题..."
                value={chatInput}
                onChange={(e) => setChatInput(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && !e.shiftKey && handleSendChat()}
                className="flex-1 px-4 py-2.5 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-violet-300"
                disabled={isChatting}
              />
              <button
                onClick={handleSendChat}
                disabled={isChatting || !chatInput.trim()}
                className="p-2.5 bg-gradient-to-r from-violet-500 to-purple-600 text-white rounded-xl hover:shadow-lg transition-all disabled:opacity-50"
              >
                <Send className="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 新建卡片弹窗 */}
      {showCreateModal && (
        <div className="fixed inset-0 bg-black/30 flex items-center justify-center z-50">
          <div className="bg-white rounded-2xl shadow-2xl w-[600px] max-h-[80vh] overflow-y-auto">
            <div className="p-6 border-b border-slate-200">
              <div className="flex items-center justify-between">
                <h3 className="text-lg font-semibold text-slate-800">新建知识卡片</h3>
                <button onClick={() => setShowCreateModal(false)} className="p-1 hover:bg-slate-100 rounded-lg">
                  <X className="w-5 h-5 text-slate-500" />
                </button>
              </div>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label className="text-sm font-medium text-slate-700">知识域 *</label>
                <select
                  value={newCard.domainCode}
                  onChange={(e) => setNewCard(prev => ({ ...prev, domainCode: e.target.value }))}
                  className="mt-1 w-full px-3 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-violet-300"
                >
                  {domains.map(d => (
                    <option key={d.code} value={d.code}>{d.name}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="text-sm font-medium text-slate-700">标题 *</label>
                <input
                  type="text"
                  value={newCard.title}
                  onChange={(e) => setNewCard(prev => ({ ...prev, title: e.target.value }))}
                  placeholder="一句话概括这张卡片的核心判断"
                  className="mt-1 w-full px-3 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-violet-300"
                />
              </div>
              <div>
                <label className="text-sm font-medium text-slate-700">内容 *</label>
                <textarea
                  value={newCard.content}
                  onChange={(e) => setNewCard(prev => ({ ...prev, content: e.target.value }))}
                  placeholder="详细描述经验、判断、数据依据..."
                  rows={6}
                  className="mt-1 w-full px-3 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-violet-300 resize-none"
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="text-sm font-medium text-slate-700">标签</label>
                  <input
                    type="text"
                    value={newCard.tags}
                    onChange={(e) => setNewCard(prev => ({ ...prev, tags: e.target.value }))}
                    placeholder="用逗号分隔，如：锦纶,40D,丝袜"
                    className="mt-1 w-full px-3 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-violet-300"
                  />
                </div>
                <div>
                  <label className="text-sm font-medium text-slate-700">关联产品编码</label>
                  <input
                    type="text"
                    value={newCard.productCode}
                    onChange={(e) => setNewCard(prev => ({ ...prev, productCode: e.target.value }))}
                    placeholder="如：HT01"
                    className="mt-1 w-full px-3 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-violet-300"
                  />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="text-sm font-medium text-slate-700">来源</label>
                  <input
                    type="text"
                    value={newCard.source}
                    onChange={(e) => setNewCard(prev => ({ ...prev, source: e.target.value }))}
                    placeholder="如：麻花/红薯/肉饼"
                    className="mt-1 w-full px-3 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-violet-300"
                  />
                </div>
                <div>
                  <label className="text-sm font-medium text-slate-700">置信度</label>
                  <select
                    value={newCard.confidence}
                    onChange={(e) => setNewCard(prev => ({ ...prev, confidence: e.target.value }))}
                    className="mt-1 w-full px-3 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-violet-300"
                  >
                    <option value="high">高 - 已验证事实</option>
                    <option value="medium">中 - 经验判断</option>
                    <option value="low">低 - 待验证</option>
                  </select>
                </div>
              </div>
            </div>
            <div className="p-6 border-t border-slate-200 flex justify-end gap-3">
              <button
                onClick={() => setShowCreateModal(false)}
                className="px-4 py-2 rounded-xl border border-slate-200 text-sm text-slate-600 hover:bg-slate-50"
              >
                取消
              </button>
              <button
                onClick={handleCreateCard}
                disabled={isCreating || !newCard.title || !newCard.content}
                className="px-6 py-2 bg-gradient-to-r from-violet-500 to-purple-600 text-white rounded-xl text-sm font-medium hover:shadow-lg disabled:opacity-50"
              >
                {isCreating ? '创建中...' : '创建卡片'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 查看卡片弹窗 */}
      {viewingCard && (
        <div className="fixed inset-0 bg-black/30 flex items-center justify-center z-50">
          <div className="bg-white rounded-2xl shadow-2xl w-[600px] max-h-[80vh] overflow-y-auto">
            <div className="p-6 border-b border-slate-200">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className={`w-8 h-8 rounded-lg flex items-center justify-center bg-gradient-to-br ${DOMAIN_COLORS[viewingCard.domain_code] || 'from-slate-400 to-slate-500'} text-white`}>
                    {DOMAIN_ICONS[viewingCard.domain_code] || <BookOpen className="w-4 h-4" />}
                  </div>
                  <div>
                    <h3 className="font-semibold text-slate-800">{viewingCard.title}</h3>
                    <span className="text-xs text-slate-400">{viewingCard.domain_name}</span>
                  </div>
                </div>
                <button onClick={() => setViewingCard(null)} className="p-1 hover:bg-slate-100 rounded-lg">
                  <X className="w-5 h-5 text-slate-500" />
                </button>
              </div>
            </div>
            <div className="p-6 space-y-4">
              <div className="text-sm text-slate-700 whitespace-pre-wrap leading-relaxed">{viewingCard.content}</div>
              <div className="flex flex-wrap gap-1.5">
                {(viewingCard.tags || []).map((tag, i) => (
                  <span key={i} className="text-xs px-2 py-1 bg-violet-50 text-violet-600 rounded-lg">{tag}</span>
                ))}
              </div>
              <div className="grid grid-cols-2 gap-3 text-xs text-slate-500">
                {viewingCard.product_code && (
                  <div className="flex items-center gap-1">
                    <Package className="w-3 h-3" />
                    <span>产品: {viewingCard.product_code}</span>
                  </div>
                )}
                {viewingCard.source && (
                  <div className="flex items-center gap-1">
                    <Eye className="w-3 h-3" />
                    <span>来源: {viewingCard.source}</span>
                  </div>
                )}
                <div className="flex items-center gap-1">
                  <Clock className="w-3 h-3" />
                  <span>创建: {new Date(viewingCard.created_at).toLocaleString()}</span>
                </div>
                <div className="flex items-center gap-1">
                  {confidenceLabel[viewingCard.confidence] && (
                    <span className={`px-1.5 py-0.5 rounded-md font-medium ${confidenceLabel[viewingCard.confidence].color}`}>
                      置信度: {confidenceLabel[viewingCard.confidence].text}
                    </span>
                  )}
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
