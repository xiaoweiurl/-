'use client';

import React, { useState } from 'react';
import {
  X, Plus, Trash2, ChevronDown, ChevronUp,
  User, Briefcase, Target, Award, Users, AlertCircle, TrendingUp, FileText
} from 'lucide-react';
import { cn } from '@/lib/utils';

interface PositionCardFormProps {
  initialData?: Record<string, any>;
  onSubmit: (data: Record<string, any>) => void;
  onCancel: () => void;
  loading?: boolean;
}

// 团队选项
const TEAM_OPTIONS = [
  { value: '品牌运营(携创云织)', label: '品牌运营(携创云织)' },
  { value: '产品开发(盈云)', label: '产品开发(盈云)' },
  { value: '供应链', label: '供应链' },
  { value: '财务', label: '财务' },
  { value: '投资委员会', label: '投资委员会' },
];

// 岗位性质选项
const NATURE_OPTIONS = [
  { value: '全职', label: '全职' },
  { value: '兼职', label: '兼职' },
  { value: '顾问', label: '顾问' },
  { value: 'Agent辅助', label: 'Agent辅助' },
];

// 频率选项
const FREQUENCY_OPTIONS = ['日', '周', '月', '季'];

// 区块定义
const SECTIONS = [
  { key: 'basic', title: '岗位基本信息', icon: User, required: true },
  { key: 'responsibilities', title: '岗位职责', icon: Briefcase, required: true },
  { key: 'deliverables', title: '关键产出物', icon: Target, required: false },
  { key: 'skills', title: '能力要求', icon: Award, required: false },
  { key: 'collaboration', title: '协作关系', icon: Users, required: false },
  { key: 'status', title: '当前状态', icon: AlertCircle, required: false },
  { key: 'improvement', title: '改进计划', icon: TrendingUp, required: false },
  { key: 'notes', title: '补充说明', icon: FileText, required: false },
];

export default function PositionCardForm({ initialData, onSubmit, onCancel, loading }: PositionCardFormProps) {
  const [expandedSections, setExpandedSections] = useState<Set<string>>(new Set(['basic', 'responsibilities']));
  const [form, setForm] = useState<Record<string, any>>({
    positionName: '',
    positionHolder: '',
    reportTo: '',
    department: '',
    team: '',
    positionNature: '',
    coreResponsibilities: '[]',
    auxiliaryResponsibilities: '[]',
    keyDeliverables: '[]',
    hardSkills: '[]',
    softSkills: '[]',
    upstreamInputs: '[]',
    downstreamOutputs: '[]',
    completedWork: '',
    ongoingWork: '',
    bottlenecks: '',
    supportNeeded: '',
    improvementDirection: '',
    processOptimization: '',
    toolResourceNeeds: '',
    additionalNotes: '',
    status: 'draft',
    ...initialData,
  });

  const toggleSection = (key: string) => {
    const next = new Set(expandedSections);
    if (next.has(key)) next.delete(key);
    else next.add(key);
    setExpandedSections(next);
  };

  const updateField = (field: string, value: any) => {
    setForm(prev => ({ ...prev, [field]: value }));
  };

  // JSON数组辅助函数
  const getListItems = (field: string): string[] => {
    try {
      const val = form[field];
      if (!val) return [''];
      const arr = typeof val === 'string' ? JSON.parse(val) : val;
      if (!Array.isArray(arr)) return [''];
      return arr.length === 0 ? [''] : arr.map(String);
    } catch { return ['']; }
  };

  const updateListItem = (field: string, index: number, value: string) => {
    const items = getListItems(field);
    items[index] = value;
    updateField(field, JSON.stringify(items.filter(Boolean)));
  };

  const addListItem = (field: string) => {
    const items = getListItems(field);
    items.push('');
    updateField(field, JSON.stringify(items));
  };

  const removeListItem = (field: string, index: number) => {
    const items = getListItems(field);
    items.splice(index, 1);
    updateField(field, JSON.stringify(items.filter(Boolean).length > 0 ? items : ['']));
  };

  // 产出物辅助
  const getDeliverables = (): Array<{ name: string; frequency: string; deliverTo: string; standard: string }> => {
    try {
      const val = form.keyDeliverables;
      if (!val) return [{ name: '', frequency: '', deliverTo: '', standard: '' }];
      const arr = typeof val === 'string' ? JSON.parse(val) : val;
      if (!Array.isArray(arr) || arr.length === 0) return [{ name: '', frequency: '', deliverTo: '', standard: '' }];
      return arr;
    } catch { return [{ name: '', frequency: '', deliverTo: '', standard: '' }]; }
  };

  const updateDeliverable = (index: number, key: string, value: string) => {
    const items = getDeliverables();
    items[index] = { ...items[index], [key]: value };
    updateField('keyDeliverables', JSON.stringify(items));
  };

  const addDeliverable = () => {
    const items = getDeliverables();
    items.push({ name: '', frequency: '', deliverTo: '', standard: '' });
    updateField('keyDeliverables', JSON.stringify(items));
  };

  const removeDeliverable = (index: number) => {
    const items = getDeliverables();
    items.splice(index, 1);
    updateField('keyDeliverables', JSON.stringify(items.length > 0 ? items : [{ name: '', frequency: '', deliverTo: '', standard: '' }]));
  };

  // 协作关系辅助
  const getCollaboration = (field: string): Array<{ position: string; content: string; frequency: string }> => {
    try {
      const val = form[field];
      if (!val) return [{ position: '', content: '', frequency: '' }];
      const arr = typeof val === 'string' ? JSON.parse(val) : val;
      if (!Array.isArray(arr) || arr.length === 0) return [{ position: '', content: '', frequency: '' }];
      return arr;
    } catch { return [{ position: '', content: '', frequency: '' }]; }
  };

  const updateCollaboration = (field: string, index: number, key: string, value: string) => {
    const items = getCollaboration(field);
    items[index] = { ...items[index], [key]: value };
    updateField(field, JSON.stringify(items));
  };

  const addCollaboration = (field: string) => {
    const items = getCollaboration(field);
    items.push({ position: '', content: '', frequency: '' });
    updateField(field, JSON.stringify(items));
  };

  const removeCollaboration = (field: string, index: number) => {
    const items = getCollaboration(field);
    items.splice(index, 1);
    updateField(field, JSON.stringify(items.length > 0 ? items : [{ position: '', content: '', frequency: '' }]));
  };

  const handleSubmit = () => {
    // 必填校验
    if (!form.positionName?.trim()) {
      alert('岗位名称不能为空');
      return;
    }
    if (!form.department?.trim()) {
      alert('所属部门不能为空');
      return;
    }
    onSubmit(form);
  };

  // 通用样式
  const inputCls = "w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-violet-500/20 focus:border-violet-400 transition-colors";
  const labelCls = "block text-xs font-medium text-slate-500 mb-1";

  return (
    <div className="flex flex-col h-full">
      {/* 头部 */}
      <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200">
        <div>
          <h2 className="text-lg font-semibold text-slate-800">
            {initialData ? '编辑岗位知识卡片' : '新建岗位知识卡片'}
          </h2>
          <p className="text-xs text-slate-400 mt-0.5">每岗位一卡，季度更新</p>
        </div>
        <button onClick={onCancel} className="p-1.5 hover:bg-slate-100 rounded-lg transition-colors">
          <X size={18} className="text-slate-400" />
        </button>
      </div>

      {/* 表单内容 */}
      <div className="flex-1 overflow-y-auto px-6 py-4 space-y-2">
        {SECTIONS.map(section => {
          const Icon = section.icon;
          const isExpanded = expandedSections.has(section.key);

          return (
            <div key={section.key} className="border border-slate-200 rounded-xl overflow-hidden">
              {/* 区块标题 */}
              <button
                type="button"
                onClick={() => toggleSection(section.key)}
                className={cn(
                  "w-full flex items-center justify-between px-4 py-3 text-left transition-colors",
                  isExpanded ? "bg-slate-50" : "hover:bg-slate-50/50"
                )}
              >
                <div className="flex items-center gap-2">
                  <Icon size={16} className="text-violet-500" />
                  <span className="text-sm font-medium text-slate-700">{section.title}</span>
                  {section.required && <span className="text-[10px] text-rose-500 font-medium">*必填</span>}
                </div>
                {isExpanded ? <ChevronUp size={16} className="text-slate-400" /> : <ChevronDown size={16} className="text-slate-400" />}
              </button>

              {/* 区块内容 */}
              {isExpanded && (
                <div className="px-4 pb-4 pt-2 space-y-3">
                  {/* 一、岗位基本信息 */}
                  {section.key === 'basic' && (
                    <>
                      <div className="grid grid-cols-2 gap-3">
                        <div>
                          <label className={labelCls}>岗位名称 <span className="text-rose-500">*</span></label>
                          <input className={inputCls} placeholder="请输入岗位名称" value={form.positionName || ''} onChange={e => updateField('positionName', e.target.value)} />
                        </div>
                        <div>
                          <label className={labelCls}>在岗人员</label>
                          <input className={inputCls} placeholder="请输入在岗人员" value={form.positionHolder || ''} onChange={e => updateField('positionHolder', e.target.value)} />
                        </div>
                        <div>
                          <label className={labelCls}>汇报上级</label>
                          <input className={inputCls} placeholder="请输入汇报上级" value={form.reportTo || ''} onChange={e => updateField('reportTo', e.target.value)} />
                        </div>
                        <div>
                          <label className={labelCls}>所属部门 <span className="text-rose-500">*</span></label>
                          <input className={inputCls} placeholder="请输入所属部门" value={form.department || ''} onChange={e => updateField('department', e.target.value)} />
                        </div>
                      </div>
                      <div className="grid grid-cols-2 gap-3">
                        <div>
                          <label className={labelCls}>所属团队</label>
                          <select className={inputCls} value={form.team || ''} onChange={e => updateField('team', e.target.value)}>
                            <option value="">请选择</option>
                            {TEAM_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
                          </select>
                        </div>
                        <div>
                          <label className={labelCls}>岗位性质</label>
                          <select className={inputCls} value={form.positionNature || ''} onChange={e => updateField('positionNature', e.target.value)}>
                            <option value="">请选择</option>
                            {NATURE_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
                          </select>
                        </div>
                      </div>
                    </>
                  )}

                  {/* 二、岗位职责 */}
                  {section.key === 'responsibilities' && (
                    <>
                      <div>
                        <label className={cn(labelCls, "mb-2")}>核心职责 <span className="text-slate-400 font-normal">(不超过5条)</span></label>
                        {getListItems('coreResponsibilities').map((item, i) => (
                          <div key={i} className="flex items-center gap-2 mb-2">
                            <span className="text-xs text-slate-400 w-5 shrink-0">{i + 1}.</span>
                            <input className={cn(inputCls, "flex-1")} placeholder="请输入核心职责" value={item} onChange={e => updateListItem('coreResponsibilities', i, e.target.value)} />
                            {getListItems('coreResponsibilities').length > 1 && (
                              <button onClick={() => removeListItem('coreResponsibilities', i)} className="p-1 hover:bg-rose-50 rounded transition-colors">
                                <Trash2 size={14} className="text-rose-400" />
                              </button>
                            )}
                          </div>
                        ))}
                        {getListItems('coreResponsibilities').length < 5 && (
                          <button onClick={() => addListItem('coreResponsibilities')} className="flex items-center gap-1 text-xs text-violet-600 hover:text-violet-700 mt-1">
                            <Plus size={14} /> 添加核心职责
                          </button>
                        )}
                      </div>
                      <div>
                        <label className={cn(labelCls, "mb-2")}>辅助职责</label>
                        {getListItems('auxiliaryResponsibilities').map((item, i) => (
                          <div key={i} className="flex items-center gap-2 mb-2">
                            <span className="text-xs text-slate-400 w-5 shrink-0">{i + 1}.</span>
                            <input className={cn(inputCls, "flex-1")} placeholder="请输入辅助职责" value={item} onChange={e => updateListItem('auxiliaryResponsibilities', i, e.target.value)} />
                            {getListItems('auxiliaryResponsibilities').length > 1 && (
                              <button onClick={() => removeListItem('auxiliaryResponsibilities', i)} className="p-1 hover:bg-rose-50 rounded transition-colors">
                                <Trash2 size={14} className="text-rose-400" />
                              </button>
                            )}
                          </div>
                        ))}
                        <button onClick={() => addListItem('auxiliaryResponsibilities')} className="flex items-center gap-1 text-xs text-violet-600 hover:text-violet-700 mt-1">
                          <Plus size={14} /> 添加辅助职责
                        </button>
                      </div>
                    </>
                  )}

                  {/* 三、关键产出物 */}
                  {section.key === 'deliverables' && (
                    <>
                      {getDeliverables().map((d, i) => (
                        <div key={i} className="p-3 bg-slate-50 rounded-lg space-y-2 relative">
                          <div className="flex items-center justify-between">
                            <span className="text-xs font-medium text-slate-600">产出物 {i + 1}</span>
                            {getDeliverables().length > 1 && (
                              <button onClick={() => removeDeliverable(i)} className="p-0.5 hover:bg-rose-50 rounded">
                                <Trash2 size={12} className="text-rose-400" />
                              </button>
                            )}
                          </div>
                          <div className="grid grid-cols-2 gap-2">
                            <input className={inputCls} placeholder="产出物名称" value={d.name} onChange={e => updateDeliverable(i, 'name', e.target.value)} />
                            <select className={inputCls} value={d.frequency} onChange={e => updateDeliverable(i, 'frequency', e.target.value)}>
                              <option value="">频率</option>
                              {FREQUENCY_OPTIONS.map(f => <option key={f} value={f}>{f}</option>)}
                            </select>
                            <input className={inputCls} placeholder="交付对象" value={d.deliverTo} onChange={e => updateDeliverable(i, 'deliverTo', e.target.value)} />
                            <input className={inputCls} placeholder="关键标准" value={d.standard} onChange={e => updateDeliverable(i, 'standard', e.target.value)} />
                          </div>
                        </div>
                      ))}
                      <button onClick={addDeliverable} className="flex items-center gap-1 text-xs text-violet-600 hover:text-violet-700">
                        <Plus size={14} /> 添加产出物
                      </button>
                    </>
                  )}

                  {/* 四、能力要求 */}
                  {section.key === 'skills' && (
                    <div className="grid grid-cols-2 gap-4">
                      <div>
                        <label className={cn(labelCls, "mb-2")}>硬技能 <span className="text-slate-400 font-normal">(工具/技术/资质)</span></label>
                        {getListItems('hardSkills').map((item, i) => (
                          <div key={i} className="flex items-center gap-2 mb-2">
                            <span className="text-xs text-slate-400 w-5 shrink-0">{i + 1}.</span>
                            <input className={cn(inputCls, "flex-1")} placeholder="请输入硬技能" value={item} onChange={e => updateListItem('hardSkills', i, e.target.value)} />
                            {getListItems('hardSkills').length > 1 && (
                              <button onClick={() => removeListItem('hardSkills', i)} className="p-1 hover:bg-rose-50 rounded">
                                <Trash2 size={14} className="text-rose-400" />
                              </button>
                            )}
                          </div>
                        ))}
                        <button onClick={() => addListItem('hardSkills')} className="flex items-center gap-1 text-xs text-violet-600 hover:text-violet-700 mt-1">
                          <Plus size={14} /> 添加
                        </button>
                      </div>
                      <div>
                        <label className={cn(labelCls, "mb-2")}>软技能 <span className="text-slate-400 font-normal">(沟通/判断/协作)</span></label>
                        {getListItems('softSkills').map((item, i) => (
                          <div key={i} className="flex items-center gap-2 mb-2">
                            <span className="text-xs text-slate-400 w-5 shrink-0">{i + 1}.</span>
                            <input className={cn(inputCls, "flex-1")} placeholder="请输入软技能" value={item} onChange={e => updateListItem('softSkills', i, e.target.value)} />
                            {getListItems('softSkills').length > 1 && (
                              <button onClick={() => removeListItem('softSkills', i)} className="p-1 hover:bg-rose-50 rounded">
                                <Trash2 size={14} className="text-rose-400" />
                              </button>
                            )}
                          </div>
                        ))}
                        <button onClick={() => addListItem('softSkills')} className="flex items-center gap-1 text-xs text-violet-600 hover:text-violet-700 mt-1">
                          <Plus size={14} /> 添加
                        </button>
                      </div>
                    </div>
                  )}

                  {/* 五、协作关系 */}
                  {section.key === 'collaboration' && (
                    <>
                      <div>
                        <label className={cn(labelCls, "mb-2")}>上游输入 <span className="text-slate-400 font-normal">(我需要谁给我什么)</span></label>
                        {getCollaboration('upstreamInputs').map((c, i) => (
                          <div key={i} className="grid grid-cols-3 gap-2 mb-2 items-end">
                            <input className={inputCls} placeholder="协作岗位" value={c.position} onChange={e => updateCollaboration('upstreamInputs', i, 'position', e.target.value)} />
                            <input className={inputCls} placeholder="输入内容" value={c.content} onChange={e => updateCollaboration('upstreamInputs', i, 'content', e.target.value)} />
                            <div className="flex items-center gap-1">
                              <select className={cn(inputCls, "flex-1")} value={c.frequency} onChange={e => updateCollaboration('upstreamInputs', i, 'frequency', e.target.value)}>
                                <option value="">频率</option>
                                {FREQUENCY_OPTIONS.map(f => <option key={f} value={f}>{f}</option>)}
                              </select>
                              {getCollaboration('upstreamInputs').length > 1 && (
                                <button onClick={() => removeCollaboration('upstreamInputs', i)} className="p-1.5 hover:bg-rose-50 rounded shrink-0">
                                  <Trash2 size={14} className="text-rose-400" />
                                </button>
                              )}
                            </div>
                          </div>
                        ))}
                        <button onClick={() => addCollaboration('upstreamInputs')} className="flex items-center gap-1 text-xs text-violet-600 hover:text-violet-700 mt-1">
                          <Plus size={14} /> 添加上游输入
                        </button>
                      </div>
                      <div>
                        <label className={cn(labelCls, "mb-2")}>下游输出 <span className="text-slate-400 font-normal">(我给谁提供什么)</span></label>
                        {getCollaboration('downstreamOutputs').map((c, i) => (
                          <div key={i} className="grid grid-cols-3 gap-2 mb-2 items-end">
                            <input className={inputCls} placeholder="协作岗位" value={c.position} onChange={e => updateCollaboration('downstreamOutputs', i, 'position', e.target.value)} />
                            <input className={inputCls} placeholder="输出内容" value={c.content} onChange={e => updateCollaboration('downstreamOutputs', i, 'content', e.target.value)} />
                            <div className="flex items-center gap-1">
                              <select className={cn(inputCls, "flex-1")} value={c.frequency} onChange={e => updateCollaboration('downstreamOutputs', i, 'frequency', e.target.value)}>
                                <option value="">频率</option>
                                {FREQUENCY_OPTIONS.map(f => <option key={f} value={f}>{f}</option>)}
                              </select>
                              {getCollaboration('downstreamOutputs').length > 1 && (
                                <button onClick={() => removeCollaboration('downstreamOutputs', i)} className="p-1.5 hover:bg-rose-50 rounded shrink-0">
                                  <Trash2 size={14} className="text-rose-400" />
                                </button>
                              )}
                            </div>
                          </div>
                        ))}
                        <button onClick={() => addCollaboration('downstreamOutputs')} className="flex items-center gap-1 text-xs text-violet-600 hover:text-violet-700 mt-1">
                          <Plus size={14} /> 添加下游输出
                        </button>
                      </div>
                    </>
                  )}

                  {/* 六、当前状态 */}
                  {section.key === 'status' && (
                    <div className="space-y-3">
                      <div>
                        <label className={labelCls}>已完成的主要工作(本季度)</label>
                        <textarea className={cn(inputCls, "min-h-[60px] resize-y")} placeholder="请输入本季度已完成的主要工作" value={form.completedWork || ''} onChange={e => updateField('completedWork', e.target.value)} />
                      </div>
                      <div>
                        <label className={labelCls}>当前进行中</label>
                        <textarea className={cn(inputCls, "min-h-[60px] resize-y")} placeholder="请输入当前进行中的工作" value={form.ongoingWork || ''} onChange={e => updateField('ongoingWork', e.target.value)} />
                      </div>
                      <div>
                        <label className={labelCls}>卡点和瓶颈</label>
                        <textarea className={cn(inputCls, "min-h-[60px] resize-y")} placeholder="请输入遇到的卡点和瓶颈" value={form.bottlenecks || ''} onChange={e => updateField('bottlenecks', e.target.value)} />
                      </div>
                      <div>
                        <label className={labelCls}>需要的支持</label>
                        <textarea className={cn(inputCls, "min-h-[60px] resize-y")} placeholder="请输入需要的支持" value={form.supportNeeded || ''} onChange={e => updateField('supportNeeded', e.target.value)} />
                      </div>
                    </div>
                  )}

                  {/* 七、改进计划 */}
                  {section.key === 'improvement' && (
                    <div className="space-y-3">
                      <div>
                        <label className={labelCls}>本人提升方向</label>
                        <textarea className={cn(inputCls, "min-h-[60px] resize-y")} placeholder="请输入本人提升方向" value={form.improvementDirection || ''} onChange={e => updateField('improvementDirection', e.target.value)} />
                      </div>
                      <div>
                        <label className={labelCls}>流程优化建议</label>
                        <textarea className={cn(inputCls, "min-h-[60px] resize-y")} placeholder="请输入流程优化建议" value={form.processOptimization || ''} onChange={e => updateField('processOptimization', e.target.value)} />
                      </div>
                      <div>
                        <label className={labelCls}>工具/资源需求</label>
                        <textarea className={cn(inputCls, "min-h-[60px] resize-y")} placeholder="请输入工具/资源需求" value={form.toolResourceNeeds || ''} onChange={e => updateField('toolResourceNeeds', e.target.value)} />
                      </div>
                    </div>
                  )}

                  {/* 八、补充说明 */}
                  {section.key === 'notes' && (
                    <div>
                      <label className={labelCls}>任何卡片格式无法覆盖但需要说明的事项</label>
                      <textarea className={cn(inputCls, "min-h-[80px] resize-y")} placeholder="请输入补充说明" value={form.additionalNotes || ''} onChange={e => updateField('additionalNotes', e.target.value)} />
                    </div>
                  )}
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* 底部操作 */}
      <div className="flex items-center justify-between px-6 py-4 border-t border-slate-200 bg-white">
        <button onClick={onCancel} className="px-4 py-2 text-sm text-slate-600 hover:bg-slate-100 rounded-lg transition-colors">
          取消
        </button>
        <div className="flex items-center gap-3">
          <button
            onClick={() => { updateField('status', 'draft'); handleSubmit(); }}
            disabled={loading}
            className="px-4 py-2 text-sm text-slate-600 border border-slate-200 hover:bg-slate-50 rounded-lg transition-colors disabled:opacity-50"
          >
            保存草稿
          </button>
          <button
            onClick={() => { updateField('status', 'published'); handleSubmit(); }}
            disabled={loading}
            className="px-4 py-2 text-sm text-white bg-gradient-to-r from-violet-500 to-purple-600 hover:from-violet-600 hover:to-purple-700 rounded-lg shadow-sm transition-colors disabled:opacity-50"
          >
            发布卡片
          </button>
        </div>
      </div>
    </div>
  );
}
