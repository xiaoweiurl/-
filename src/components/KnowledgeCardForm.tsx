'use client';

import { useState, useEffect, useCallback, useMemo } from 'react';
import {
  Plus, X, Save, Trash2, ChevronDown, ChevronUp, Loader2,
  FileText, Users, Briefcase, Target, GitBranch, AlertTriangle,
  TrendingUp, MessageSquare, CheckCircle
} from 'lucide-react';
import { getCurrentBrand } from '@/lib/brand';

interface KnowledgeCard {
  id?: string;
  cardCode?: string;
  submitDate?: string;
  department?: string;
  positionName: string;
  onDutyPerson?: string;
  reportTo?: string;
  team?: string;
  positionNature?: string;
  coreDuties: string;
  auxiliaryDuties?: string;
  keyOutputs?: string;
  hardSkills?: string;
  softSkills?: string;
  upstreamInputs?: string;
  downstreamOutputs?: string;
  completedWork?: string;
  inProgress?: string;
  bottlenecks?: string;
  supportNeeded?: string;
  improvementDirection?: string;
  processOptimization?: string;
  toolResourceNeeds?: string;
  additionalNotes?: string;
  createdAt?: string;
}

interface Props {
  onClose?: () => void;
  onSaved?: () => void;
  editCard?: KnowledgeCard | null;
}

/** 按公司区分的团队和部门配置 */
const TEAM_BY_COMPANY: Record<string, string[]> = {
  '盈云': ['产品开发(盈云)', '品牌运营(盈云)', '供应链(盈云)', '财务(盈云)', '投资委员会(盈云)'],
  '宝娜斯': ['品牌运营(宝娜斯)', '产品开发(宝娜斯)', '供应链(宝娜斯)', '财务(宝娜斯)', '针织技术(宝娜斯)'],
};
const DEPT_BY_COMPANY: Record<string, string[]> = {
  '盈云': ['产品开发', '品牌运营', '供应链', '财务', '投资委员会', '人力资源', '技术部'],
  '宝娜斯': ['品牌运营', '产品开发', '供应链', '财务', '针织技术部', '人力资源', '品质管理'],
};
const DEFAULT_TEAMS = TEAM_BY_COMPANY['盈云'];
const DEFAULT_DEPARTMENTS = DEPT_BY_COMPANY['盈云'];
const NATURES = ['全职', '兼职', '顾问', 'Agent辅助'];

const PH = '';

// 多行输入组件
function MultiLineInput({ value, onChange, placeholder, rows = 2 }: {
  value: string; onChange: (v: string) => void; placeholder?: string; rows?: number;
}) {
  return (
    <textarea
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder={placeholder || PH}
      rows={rows}
      className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-300 focus:border-indigo-400 bg-white resize-none"
    />
  );
}

// 编号列表输入组件
function NumberedListInput({ value, onChange, placeholder, maxItems = 5 }: {
  value: string; onChange: (v: string) => void; placeholder?: string; maxItems?: number;
}) {
  const lines = value ? value.split('\n') : [];
  const items = [...lines];
  while (items.length < maxItems) items.push('');

  const handleChange = (index: number, val: string) => {
    const newItems = [...items];
    newItems[index] = val;
    onChange(newItems.filter(l => l.trim()).join('\n'));
  };

  return (
    <div className="space-y-1.5">
      {items.slice(0, maxItems).map((item, i) => (
        <div key={i} className="flex items-start gap-2">
          <span className="text-xs text-slate-400 mt-2 w-4 text-right shrink-0">{i + 1}.</span>
          <input
            value={item}
            onChange={(e) => handleChange(i, e.target.value)}
            placeholder={i === 0 ? (placeholder || PH) : PH}
            className="flex-1 px-3 py-1.5 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-300 focus:border-indigo-400 bg-white"
          />
        </div>
      ))}
    </div>
  );
}

// 必填标记
function Required() {
  return <span className="text-red-400 ml-0.5">*</span>;
}

// 折叠区块 — 全部必填
function Section({ title, icon, children, defaultOpen = true }: {
  title: string; icon: React.ReactNode; children: React.ReactNode; defaultOpen?: boolean;
}) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className="border border-slate-200 rounded-xl overflow-hidden">
      <button
        type="button"
        onClick={() => setOpen(!open)}
        className="w-full flex items-center gap-2.5 px-4 py-3 bg-slate-50/80 hover:bg-slate-100/80 transition-colors"
      >
        <span className="text-indigo-500">{icon}</span>
        <span className="text-sm font-semibold text-slate-700">{title}</span>
        <span className="text-[10px] px-1.5 py-0.5 bg-red-50 text-red-500 rounded font-medium">必填</span>
        <span className="ml-auto">{open ? <ChevronUp className="w-4 h-4 text-slate-400" /> : <ChevronDown className="w-4 h-4 text-slate-400" />}</span>
      </button>
      {open && <div className="px-4 py-3 space-y-3">{children}</div>}
    </div>
  );
}

export default function KnowledgeCardForm({ onClose, onSaved, editCard }: Props) {
  const [saving, setSaving] = useState(false);
  const [card, setCard] = useState<KnowledgeCard>({
    positionName: '',
    coreDuties: '',
    ...editCard,
  });

  // 根据当前公司动态获取团队和部门
  const companyName = useMemo(() => {
    if (typeof window === 'undefined') return '盈云';
    const stored = localStorage.getItem('user_company');
    return stored || '盈云';
  }, []);
  const teams = TEAM_BY_COMPANY[companyName] || DEFAULT_TEAMS;
  const departments = DEPT_BY_COMPANY[companyName] || DEFAULT_DEPARTMENTS;

  const update = (field: keyof KnowledgeCard, value: string) => {
    setCard(prev => ({ ...prev, [field]: value }));
  };

  const validate = (): string | null => {
    if (!card.positionName?.trim()) return '岗位名称不能为空';
    if (!card.onDutyPerson?.trim()) return '在岗人员不能为空';
    if (!card.reportTo?.trim()) return '汇报上级不能为空';
    if (!card.department?.trim()) return '所属部门不能为空';
    if (!card.team?.trim()) return '所属团队不能为空';
    if (!card.positionNature?.trim()) return '岗位性质不能为空';
    if (!card.coreDuties?.trim()) return '核心职责不能为空';
    if (!card.auxiliaryDuties?.trim()) return '辅助职责不能为空';
    if (!card.keyOutputs?.trim()) return '关键产出物不能为空';
    if (!card.hardSkills?.trim()) return '硬技能不能为空';
    if (!card.softSkills?.trim()) return '软技能不能为空';
    if (!card.upstreamInputs?.trim()) return '上游输入不能为空';
    if (!card.downstreamOutputs?.trim()) return '下游输出不能为空';
    if (!card.completedWork?.trim()) return '已完成工作不能为空';
    if (!card.inProgress?.trim()) return '当前进行中不能为空';
    if (!card.bottlenecks?.trim()) return '卡点和瓶颈不能为空';
    if (!card.supportNeeded?.trim()) return '需要的支持不能为空';
    if (!card.improvementDirection?.trim()) return '本人提升方向不能为空';
    if (!card.processOptimization?.trim()) return '流程优化建议不能为空';
    if (!card.toolResourceNeeds?.trim()) return '工具/资源需求不能为空';
    if (!card.additionalNotes?.trim()) return '补充说明不能为空';
    return null;
  };

  const handleSave = async () => {
    const error = validate();
    if (error) {
      alert(error);
      return;
    }
    setSaving(true);
    try {
      const url = editCard?.id ? `/api/knowledge/cards/${editCard.id}` : '/api/knowledge/cards';
      const method = editCard?.id ? 'PUT' : 'POST';
      const res = await fetch(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(card),
      });
      const data = await res.json();
      if (data.success) {
        onSaved?.();
        onClose?.();
      } else {
        alert(data.error || '保存失败');
      }
    } catch (err) {
      console.error('Save card error:', err);
      alert('保存失败，请重试');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-3xl max-h-[90vh] flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 shrink-0">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 bg-gradient-to-br from-indigo-500 to-blue-600 rounded-xl flex items-center justify-center">
              <FileText className="w-4.5 h-4.5 text-white" />
            </div>
            <div>
              <h2 className="text-base font-bold text-slate-800">{editCard ? '编辑岗位知识卡片' : '新建岗位知识卡片'}</h2>
              <p className="text-xs text-slate-400">所有字段均为必填，没有可填"无"</p>
            </div>
          </div>
          <button onClick={onClose} className="p-1.5 hover:bg-slate-100 rounded-lg transition-colors">
            <X className="w-5 h-5 text-slate-400" />
          </button>
        </div>

        {/* Form */}
        <div className="flex-1 overflow-y-auto px-6 py-4 space-y-3">
          {/* 一、岗位基本信息 */}
          <Section title="一、岗位基本信息" icon={<Briefcase className="w-4 h-4" />} defaultOpen>
            <div className="grid grid-cols-3 gap-3">
              <div>
                <label className="block text-xs font-medium text-slate-600 mb-1">岗位名称<Required /></label>
                <input
                  value={card.positionName}
                  onChange={(e) => update('positionName', e.target.value)}
                  placeholder="例如：产品经理"
                  className="w-full px-3 py-1.5 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-300 bg-white"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-600 mb-1">在岗人员<Required /></label>
                <input
                  value={card.onDutyPerson || ''}
                  onChange={(e) => update('onDutyPerson', e.target.value)}
                  placeholder={PH}
                  className="w-full px-3 py-1.5 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-300 bg-white"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-600 mb-1">汇报上级<Required /></label>
                <input
                  value={card.reportTo || ''}
                  onChange={(e) => update('reportTo', e.target.value)}
                  placeholder={PH}
                  className="w-full px-3 py-1.5 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-300 bg-white"
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium text-slate-600 mb-1">所属部门<Required /></label>
                <select
                  value={card.department || ''}
                  onChange={(e) => update('department', e.target.value)}
                  className="w-full px-3 py-1.5 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-300 bg-white"
                >
                  <option value="">选择部门</option>
                  {departments.map(d => <option key={d} value={d}>{d}</option>)}
                </select>
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-600 mb-1">所属团队<Required /></label>
                <select
                  value={card.team || ''}
                  onChange={(e) => update('team', e.target.value)}
                  className="w-full px-3 py-1.5 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-300 bg-white"
                >
                  <option value="">选择团队</option>
                  {teams.map(t => <option key={t} value={t}>{t}</option>)}
                </select>
              </div>
            </div>

            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1">岗位性质<Required /></label>
              <div className="flex gap-3">
                {NATURES.map(n => (
                  <label key={n} className="flex items-center gap-1.5 text-sm text-slate-600 cursor-pointer">
                    <input
                      type="radio"
                      name="positionNature"
                      value={n}
                      checked={card.positionNature === n}
                      onChange={(e) => update('positionNature', e.target.value)}
                      className="accent-indigo-500"
                    />
                    {n}
                  </label>
                ))}
              </div>
            </div>
          </Section>

          {/* 二、岗位职责 */}
          <Section title="二、岗位职责" icon={<Target className="w-4 h-4" />}>
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1.5">核心职责（不超过5条）<Required /></label>
              <NumberedListInput
                value={card.coreDuties || ''}
                onChange={(v) => update('coreDuties', v)}
                placeholder="请输入核心职责"
                maxItems={5}
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1.5">辅助职责<Required /></label>
              <NumberedListInput
                value={card.auxiliaryDuties || ''}
                onChange={(v) => update('auxiliaryDuties', v)}
                placeholder="请输入辅助职责"
                maxItems={3}
              />
            </div>
          </Section>

          {/* 三、关键产出物 */}
          <Section title="三、关键产出物" icon={<FileText className="w-4 h-4" />}>
            <MultiLineInput
              value={card.keyOutputs || ''}
              onChange={(v) => update('keyOutputs', v)}
              placeholder="格式：产出物名称 | 频率(日/周/月/季) | 交付对象 | 关键标准&#10;例如：产品需求文档 | 周 | 开发团队 | 需求明确无歧义"
              rows={3}
            />
          </Section>

          {/* 四、能力要求 */}
          <Section title="四、能力要求" icon={<Users className="w-4 h-4" />}>
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1.5">硬技能（工具/技术/资质）<Required /></label>
              <NumberedListInput
                value={card.hardSkills || ''}
                onChange={(v) => update('hardSkills', v)}
                placeholder="例如：Excel高级应用"
                maxItems={5}
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1.5">软技能（沟通/判断/协作）<Required /></label>
              <NumberedListInput
                value={card.softSkills || ''}
                onChange={(v) => update('softSkills', v)}
                placeholder="例如：跨部门沟通能力"
                maxItems={3}
              />
            </div>
          </Section>

          {/* 五、协作关系 */}
          <Section title="五、协作关系" icon={<GitBranch className="w-4 h-4" />}>
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1.5">上游输入（我需要谁给我什么）<Required /></label>
              <MultiLineInput
                value={card.upstreamInputs || ''}
                onChange={(v) => update('upstreamInputs', v)}
                placeholder="格式：协作岗位 | 输入内容 | 频率(日/周/月)"
                rows={2}
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1.5">下游输出（我给谁提供什么）<Required /></label>
              <MultiLineInput
                value={card.downstreamOutputs || ''}
                onChange={(v) => update('downstreamOutputs', v)}
                placeholder="格式：协作岗位 | 输出内容 | 频率(日/周/月)"
                rows={2}
              />
            </div>
          </Section>

          {/* 六、当前状态 */}
          <Section title="六、当前状态" icon={<AlertTriangle className="w-4 h-4" />}>
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1.5">已完成的主要工作（本季度）<Required /></label>
              <MultiLineInput value={card.completedWork || ''} onChange={(v) => update('completedWork', v)} rows={2} />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1.5">当前进行中<Required /></label>
              <MultiLineInput value={card.inProgress || ''} onChange={(v) => update('inProgress', v)} rows={2} />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1.5">卡点和瓶颈<Required /></label>
              <MultiLineInput value={card.bottlenecks || ''} onChange={(v) => update('bottlenecks', v)} rows={2} />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1.5">需要的支持<Required /></label>
              <MultiLineInput value={card.supportNeeded || ''} onChange={(v) => update('supportNeeded', v)} rows={2} />
            </div>
          </Section>

          {/* 七、改进计划 */}
          <Section title="七、改进计划" icon={<TrendingUp className="w-4 h-4" />}>
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1.5">本人提升方向<Required /></label>
              <MultiLineInput value={card.improvementDirection || ''} onChange={(v) => update('improvementDirection', v)} rows={2} />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1.5">流程优化建议<Required /></label>
              <MultiLineInput value={card.processOptimization || ''} onChange={(v) => update('processOptimization', v)} rows={2} />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1.5">工具/资源需求<Required /></label>
              <MultiLineInput value={card.toolResourceNeeds || ''} onChange={(v) => update('toolResourceNeeds', v)} rows={2} />
            </div>
          </Section>

          {/* 八、补充说明 */}
          <Section title="八、补充说明" icon={<MessageSquare className="w-4 h-4" />} defaultOpen={false}>
            <MultiLineInput
              value={card.additionalNotes || ''}
              onChange={(v) => update('additionalNotes', v)}
              placeholder="任何卡片格式无法覆盖但需要说明的事项"
              rows={3}
            />
          </Section>
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between px-6 py-4 border-t border-slate-200 shrink-0 bg-slate-50/50">
          <p className="text-[10px] text-slate-400">每岗位一卡，季度更新。提交至企业智能中台归档。所有字段必填。</p>
          <div className="flex gap-3">
            <button
              onClick={onClose}
              className="px-4 py-2 text-sm text-slate-600 hover:text-slate-800 border border-slate-200 rounded-lg hover:bg-slate-100 transition-colors"
            >
              取消
            </button>
            <button
              onClick={handleSave}
              disabled={saving}
              className="px-5 py-2 text-sm bg-gradient-to-r from-indigo-500 to-blue-600 text-white rounded-lg hover:shadow-md transition-all disabled:opacity-50 flex items-center gap-2"
            >
              {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
              {saving ? '保存中...' : '保存卡片'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
