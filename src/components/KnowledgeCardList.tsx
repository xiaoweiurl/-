'use client';

import { useState, useEffect, useCallback, forwardRef, useImperativeHandle } from 'react';
import {
  FileText, Plus, Trash2, Edit3, Eye, ChevronRight,
  Briefcase, Users, Building2, Clock, Search
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

  const fetchCards = useCallback(async () => {
    try {
      const res = await fetch('/api/knowledge/cards');
      const data = await res.json();
      if (data.success) {
        setCards(data.cards || data.data || []);
      }
    } catch (err) {
      console.error('Fetch cards error:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchCards(); }, [fetchCards]);

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
    if (team?.includes('品牌运营')) return 'bg-pink-50 text-pink-600 border-pink-200';
    if (team?.includes('产品开发')) return 'bg-violet-50 text-violet-600 border-violet-200';
    if (team?.includes('供应链')) return 'bg-amber-50 text-amber-600 border-amber-200';
    if (team?.includes('财务')) return 'bg-emerald-50 text-emerald-600 border-emerald-200';
    return 'bg-slate-50 text-slate-600 border-slate-200';
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin w-6 h-6 border-2 border-indigo-500 border-t-transparent rounded-full" />
      </div>
    );
  }

  return (
    <div className="flex flex-col h-full">
      {/* Toolbar */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-slate-200/60 shrink-0">
        <div className="flex items-center gap-2">
          <div className="relative">
            <Search className="w-3.5 h-3.5 text-slate-400 absolute left-2.5 top-1/2 -translate-y-1/2" />
            <input
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="搜索岗位/人员..."
              className="pl-8 pr-3 py-1.5 text-xs border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-300 bg-white w-48"
            />
          </div>
          <span className="text-[10px] text-slate-400">{filtered.length} 张卡片</span>
        </div>
        <button
          onClick={onCreateNew}
          className="flex items-center gap-1.5 px-3 py-1.5 text-xs bg-gradient-to-r from-indigo-500 to-blue-600 text-white rounded-lg hover:shadow-md transition-all"
        >
          <Plus className="w-3.5 h-3.5" />
          新建卡片
        </button>
      </div>

      {/* Card Grid */}
      <div className="flex-1 overflow-y-auto p-4">
        {filtered.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-64 text-slate-400">
            <FileText className="w-12 h-12 mb-3 opacity-30" />
            <p className="text-sm">暂无岗位知识卡片</p>
            <button
              onClick={onCreateNew}
              className="mt-3 text-xs text-indigo-500 hover:text-indigo-600 flex items-center gap-1"
            >
              <Plus className="w-3 h-3" /> 创建第一张卡片
            </button>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
            {filtered.map((card) => (
              <div
                key={card.id}
                className="group bg-white border border-slate-200/80 rounded-xl p-4 hover:shadow-md hover:border-indigo-200/60 transition-all cursor-pointer"
                onClick={() => onEdit(card)}
              >
                {/* Header */}
                <div className="flex items-start justify-between mb-3">
                  <div className="flex items-center gap-2">
                    <div className="w-8 h-8 bg-gradient-to-br from-indigo-500 to-blue-600 rounded-lg flex items-center justify-center shrink-0">
                      <Briefcase className="w-4 h-4 text-white" />
                    </div>
                    <div>
                      <h3 className="text-sm font-semibold text-slate-800 leading-tight">{card.positionName}</h3>
                      <span className="text-[10px] text-slate-400">{card.cardCode}</span>
                    </div>
                  </div>
                </div>

                {/* Info Tags */}
                <div className="flex flex-wrap gap-1.5 mb-3">
                  {card.department && (
                    <span className="inline-flex items-center gap-1 text-[10px] px-1.5 py-0.5 bg-slate-50 text-slate-500 rounded border border-slate-200">
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
                    <span className="inline-flex items-center text-[10px] px-1.5 py-0.5 bg-blue-50 text-blue-500 rounded border border-blue-200">
                      {card.positionNature}
                    </span>
                  )}
                </div>

                {/* Person */}
                <div className="flex items-center gap-1.5 mb-2">
                  <Users className="w-3 h-3 text-slate-400" />
                  <span className="text-xs text-slate-600">{card.onDutyPerson || '未指定'}</span>
                  {card.reportTo && (
                    <>
                      <ChevronRight className="w-3 h-3 text-slate-300" />
                      <span className="text-xs text-slate-400">汇报: {card.reportTo}</span>
                    </>
                  )}
                </div>

                {/* Core Duties Preview */}
                {card.coreDuties && (
                  <div className="text-[11px] text-slate-500 line-clamp-2 leading-relaxed">
                    {card.coreDuties.split('\n').filter(Boolean).map((d, i) => (
                      <span key={i}>{i > 0 && ' · '}{d}</span>
                    ))}
                  </div>
                )}

                {/* Footer */}
                <div className="flex items-center justify-between mt-3 pt-2 border-t border-slate-100">
                  <span className="text-[10px] text-slate-400 flex items-center gap-1">
                    <Clock className="w-2.5 h-2.5" />
                    {card.createdAt ? new Date(card.createdAt).toLocaleDateString('zh-CN') : ''}
                  </span>
                  <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                    <button
                      onClick={(e) => { e.stopPropagation(); onEdit(card); }}
                      className="p-1 hover:bg-indigo-50 rounded transition-colors"
                      title="编辑"
                    >
                      <Edit3 className="w-3 h-3 text-indigo-500" />
                    </button>
                    <button
                      onClick={(e) => { e.stopPropagation(); handleDelete(card.id); }}
                      className="p-1 hover:bg-red-50 rounded transition-colors"
                      title="删除"
                    >
                      <Trash2 className="w-3 h-3 text-red-400" />
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
