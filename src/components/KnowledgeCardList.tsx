'use client';

import { useState, useEffect, useCallback, useRef, forwardRef, useImperativeHandle } from 'react';
import {
  FileText, Plus, Trash2, Edit3, ChevronRight,
  Briefcase, Users, Building2, Clock, Search, CheckCircle2, XCircle, Loader2
} from 'lucide-react';

interface KnowledgeCard {
  id: string;
  cardCode: string;
  positionName: string;
  onDutyPerson: string;
  department: string;
  team: string;
  positionNature: string;
  coreDuties: string;
  reportTo: string;
  createdAt: string;
  embeddingStatus?: string; // PENDING/PROCESSING/COMPLETED/FAILED
}

interface Props {
  onCreateNew: () => void;
  onEdit: (card: KnowledgeCard) => void;
}

export interface KnowledgeCardListHandle {
  reload: () => void;
}

const KnowledgeCardListInner = forwardRef<KnowledgeCardListHandle, Props>(function KnowledgeCardListInner({ onCreateNew, onEdit }, ref) {
  const [cards, setCards] = useState<KnowledgeCard[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const pollTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const fetchCards = useCallback(async (silent = false) => {
    try {
      const res = await fetch(`/api/knowledge/cards?_t=${Date.now()}`, { cache: 'no-store' });
      const data = await res.json();
      console.log('[KnowledgeCardList] API response:', JSON.stringify(data).substring(0, 500));
      if (data.success) {
        const rawCards = data.cards || data.data || [];
        // Normalize: support both embeddingStatus and embedding_status
        const normalized = rawCards.map((c: any) => ({
          ...c,
          embeddingStatus: c.embeddingStatus ?? c.embedding_status ?? null,
        }));
        console.log('[KnowledgeCardList] Cards embedding status:', normalized.map((c: any) => ({ id: c.id, status: c.embeddingStatus })));
        setCards(normalized);
      }
    } catch (err) {
      console.error('Fetch cards error:', err);
    } finally {
      if (!silent) setLoading(false);
    }
  }, []);

  useEffect(() => { fetchCards(); }, [fetchCards]);

  // Auto-poll when any card is PENDING, PROCESSING, or has no status yet (null/undefined)
  useEffect(() => {
    const hasPending = cards.some(c => {
      const s = c.embeddingStatus;
      return !s || s === 'PENDING' || s === 'PROCESSING';
    });
    if (hasPending) {
      pollTimerRef.current = setInterval(() => fetchCards(true), 3000);
    } else if (pollTimerRef.current) {
      clearInterval(pollTimerRef.current);
      pollTimerRef.current = null;
    }
    return () => {
      if (pollTimerRef.current) {
        clearInterval(pollTimerRef.current);
        pollTimerRef.current = null;
      }
    };
  }, [cards, fetchCards]);

  useImperativeHandle(ref, () => ({ reload: fetchCards }));

  const handleDelete = async (id: string) => {
    if (!confirm('确定删除该岗位知识卡片？')) return;
    try {
      const res = await fetch(`/api/knowledge/cards/${id}`, { method: 'DELETE' });
      const data = await res.json();
      if (data.success) fetchCards();
      else alert(data.error || '删除失败');
    } catch { alert('删除失败'); }
  };

  const filtered = cards.filter(c =>
    c.positionName.includes(searchQuery) ||
    c.onDutyPerson?.includes(searchQuery) ||
    c.department?.includes(searchQuery) ||
    c.cardCode?.includes(searchQuery)
  );

  const getTeamColor = (team: string) => {
    if (team?.includes('品牌运营')) return 'bg-[rgba(175,82,222,0.15)] text-[#af52de] border-[rgba(175,82,222,0.3)]';
    if (team?.includes('产品开发')) return 'bg-[rgba(0,122,255,0.15)] text-[#007aff] border-[rgba(0,122,255,0.3)]';
    if (team?.includes('供应链')) return 'bg-[rgba(255,149,0,0.15)] text-[#ff9500] border-[rgba(255,149,0,0.3)]';
    if (team?.includes('财务')) return 'bg-[rgba(52,199,89,0.15)] text-[#34c759] border-[rgba(52,199,89,0.3)]';
    return 'bg-[rgba(0,0,0,0.015)] text-[#8e8e93] border-[rgba(229,229,234,0.3)]';
  };

  const renderEmbeddingBadge = (status?: string) => {
    switch (status) {
      case 'COMPLETED':
        return (
          <span className="inline-flex items-center gap-1 text-[10px] px-2 py-0.5 bg-[rgba(52,199,89,0.15)] text-[#34c759] rounded-full border border-[rgba(52,199,89,0.3)]">
            <CheckCircle2 className="w-3 h-3" />
            已向量化
          </span>
        );
      case 'FAILED':
        return (
          <span className="inline-flex items-center gap-1 text-[10px] px-2 py-0.5 bg-[rgba(255,59,48,0.15)] text-[#ff3b30] rounded-full border border-[rgba(255,59,48,0.3)]">
            <XCircle className="w-3 h-3" />
            向量化失败
          </span>
        );
      case 'PROCESSING':
        return (
          <span className="inline-flex items-center gap-1 text-[10px] px-2 py-0.5 bg-[rgba(0,122,255,0.15)] text-[#007aff] rounded-full border border-[rgba(0,122,255,0.3)]">
            <Loader2 className="w-3 h-3 animate-spin" />
            向量化中
          </span>
        );
      case 'PENDING':
      default:
        return (
          <span className="inline-flex items-center gap-1 text-[10px] px-2 py-0.5 bg-[rgba(0,0,0,0.015)] text-[#8e8e93] rounded-full border border-[rgba(229,229,234,0.3)]">
            <Clock className="w-3 h-3" />
            待向量化
          </span>
        );
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin w-6 h-6 border-2 border-[#007aff] border-t-transparent rounded-full" />
      </div>
    );
  }

  return (
    <div className="flex flex-col h-full">
      {/* Toolbar */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-[rgba(229,229,234,0.5)] shrink-0">
        <div className="flex items-center gap-2">
          <div className="relative">
            <Search className="w-3.5 h-3.5 text-[#8e8e93] absolute left-2.5 top-1/2 -translate-y-1/2" />
            <input
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="搜索岗位/人员..."
              className="pl-8 pr-3 py-1.5 text-xs border border-[#e5e5ea] rounded-lg focus:outline-none focus:ring-2 focus:ring-[rgba(0,122,255,0.5)] bg-[#ffffff] text-[#1c1c1e] placeholder-[#8e8e93] w-48"
            />
          </div>
          <span className="text-[10px] text-[#8e8e93]">{filtered.length} 张卡片</span>
        </div>
        <button
          onClick={onCreateNew}
          className="flex items-center gap-1.5 px-3 py-1.5 text-xs bg-[#007AFF] text-white rounded-lg hover:shadow-md hover:shadow-[0_2px_12px_rgba(0,0,0,0.04)] transition-all"
        >
          <Plus className="w-3.5 h-3.5" />
          新建卡片
        </button>
      </div>

      {/* Card Grid */}
      <div className="flex-1 overflow-y-auto p-4">
        {filtered.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-64 text-[#8e8e93]">
            <FileText className="w-12 h-12 mb-3 opacity-30" />
            <p className="text-sm">暂无岗位知识卡片</p>
            <button
              onClick={onCreateNew}
              className="mt-3 text-xs text-[#007aff] hover:text-[#007aff] flex items-center gap-1"
            >
              <Plus className="w-3 h-3" /> 创建第一张卡片
            </button>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
            {filtered.map((card) => (
              <div
                key={card.id}
                className="group bg-white border border-[rgba(229,229,234,0.6)] rounded-xl p-4 hover:shadow-lg hover:shadow-[0_2px_12px_rgba(0,0,0,0.04)] hover:border-[rgba(0,122,255,0.3)] transition-all cursor-pointer"
                onClick={() => onEdit(card)}
              >
                {/* Header */}
                <div className="flex items-start justify-between mb-3">
                  <div className="flex items-center gap-2">
                    <div className="w-8 h-8 bg-[#007AFF] rounded-lg flex items-center justify-center shrink-0">
                      <Briefcase className="w-4 h-4 text-[#1C1C1E]" />
                    </div>
                    <div>
                      <h3 className="text-sm font-semibold text-[#1c1c1e] leading-tight">{card.positionName}</h3>
                      <span className="text-[10px] text-[#8e8e93]">{card.cardCode}</span>
                    </div>
                  </div>
                  {renderEmbeddingBadge(card.embeddingStatus)}
                </div>

                {/* Info Tags */}
                <div className="flex flex-wrap gap-1.5 mb-3">
                  {card.department && (
                    <span className="inline-flex items-center gap-1 text-[10px] px-1.5 py-0.5 bg-[rgba(118,118,128,0.12)] text-[#8e8e93] rounded border border-[rgba(229,229,234,0.5)]">
                      <Building2 className="w-2.5 h-2.5" />
                      {card.department}
                    </span>
                  )}
                  {card.team && (
                    <span className={`inline-flex items-center text-[10px] px-1.5 py-0.5 rounded border ${getTeamColor(card.team)}`}>
                      {card.team.split('(')[0]}
                    </span>
                  )}
                  {card.positionNature && (
                    <span className="inline-flex items-center text-[10px] px-1.5 py-0.5 bg-[rgba(0,122,255,0.15)] text-[#007aff] rounded border border-[rgba(0,122,255,0.3)]">
                      {card.positionNature}
                    </span>
                  )}
                </div>

                {/* Person */}
                <div className="flex items-center gap-1.5 mb-2">
                  <Users className="w-3 h-3 text-[#8e8e93]" />
                  <span className="text-xs text-[#3a3a3c]">{card.onDutyPerson || '未指定'}</span>
                  {card.reportTo && (
                    <>
                      <ChevronRight className="w-3 h-3 text-[#8e8e93]" />
                      <span className="text-xs text-[#8e8e93]">汇报: {card.reportTo}</span>
                    </>
                  )}
                </div>

                {/* Core Duties Preview */}
                {card.coreDuties && (
                  <div className="text-[11px] text-[#8e8e93] line-clamp-2 leading-relaxed">
                    {card.coreDuties.split('\n').filter(Boolean).map((d, i) => (
                      <span key={i}>{i > 0 && ' · '}{d}</span>
                    ))}
                  </div>
                )}

                {/* Footer */}
                <div className="flex items-center justify-between mt-3 pt-2 border-t border-[rgba(229,229,234,0.5)]">
                  <span className="text-[10px] text-[#8e8e93] flex items-center gap-1">
                    <Clock className="w-2.5 h-2.5" />
                    {card.createdAt ? new Date(card.createdAt).toLocaleDateString('zh-CN') : ''}
                  </span>
                  <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                    <button
                      onClick={(e) => { e.stopPropagation(); onEdit(card); }}
                      className="p-1 hover:bg-[rgba(0,122,255,0.15)] rounded transition-colors"
                      title="编辑"
                    >
                      <Edit3 className="w-3 h-3 text-[#007aff]" />
                    </button>
                    <button
                      onClick={(e) => { e.stopPropagation(); handleDelete(card.id); }}
                      className="p-1 hover:bg-[rgba(255,59,48,0.15)] rounded transition-colors"
                      title="删除"
                    >
                      <Trash2 className="w-3 h-3 text-[#ff3b30]" />
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
});

export default KnowledgeCardListInner;
