'use client';

import React from 'react';
import {
  User, Briefcase, Target, Award, Users, AlertCircle, TrendingUp, FileText,
  Calendar, Building2, Tag, CheckCircle, Clock, XCircle
} from 'lucide-react';
import { cn } from '@/lib/utils';

interface PositionCardDetailProps {
  card: Record<string, any>;
  onEdit?: () => void;
  onDelete?: () => void;
}

// 状态标签
const STATUS_MAP: Record<string, { label: string; color: string; icon: React.ReactNode }> = {
  draft: { label: '草稿', color: 'bg-slate-100 text-slate-600', icon: <Clock size={12} /> },
  published: { label: '已发布', color: 'bg-green-50 text-green-600', icon: <CheckCircle size={12} /> },
  archived: { label: '已归档', color: 'bg-amber-50 text-amber-600', icon: <XCircle size={12} /> },
};

export default function PositionCardDetail({ card, onEdit, onDelete }: PositionCardDetailProps) {
  // 解析JSON数组
  const parseList = (field: string): string[] => {
    try {
      const val = card[field];
      if (!val) return [];
      const arr = typeof val === 'string' ? JSON.parse(val) : val;
      return Array.isArray(arr) ? arr.filter(Boolean).map(String) : [];
    } catch { return []; }
  };

  // 解析产出物
  const parseDeliverables = (): Array<{ name: string; frequency: string; deliverTo: string; standard: string }> => {
    try {
      const val = card.keyDeliverables;
      if (!val) return [];
      const arr = typeof val === 'string' ? JSON.parse(val) : val;
      return Array.isArray(arr) ? arr : [];
    } catch { return []; }
  };

  // 解析协作关系
  const parseCollaboration = (field: string): Array<{ position: string; content: string; frequency: string }> => {
    try {
      const val = card[field];
      if (!val) return [];
      const arr = typeof val === 'string' ? JSON.parse(val) : val;
      return Array.isArray(arr) ? arr : [];
    } catch { return []; }
  };

  const status = STATUS_MAP[card.status] || STATUS_MAP.draft;

  // 区块样式
  const sectionCls = "border border-slate-100 rounded-xl p-4";
  const sectionTitleCls = "flex items-center gap-2 text-sm font-semibold text-slate-700 mb-3";
  const rowCls = "flex items-start gap-2 py-1.5";
  const labelCls = "text-xs text-slate-400 w-20 shrink-0 pt-0.5";
  const valueCls = "text-sm text-slate-700 flex-1";

  return (
    <div className="max-w-3xl mx-auto">
      {/* 头部信息 */}
      <div className="flex items-start justify-between mb-4">
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <h2 className="text-lg font-bold text-slate-800">{card.positionName || '未命名岗位'}</h2>
            <span className={cn("inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-medium", status.color)}>
              {status.icon} {status.label}
            </span>
          </div>
          <div className="flex items-center gap-3 text-xs text-slate-400">
            <span className="flex items-center gap-1"><Tag size={12} /> {card.cardNo}</span>
            <span className="flex items-center gap-1"><Calendar size={12} /> {card.submitDate || '未设置'}</span>
            <span className="flex items-center gap-1"><Building2 size={12} /> {card.department || '未设置'}</span>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {onEdit && (
            <button onClick={onEdit} className="px-3 py-1.5 text-xs text-violet-600 border border-violet-200 hover:bg-violet-50 rounded-lg transition-colors">
              编辑
            </button>
          )}
          {onDelete && (
            <button onClick={onDelete} className="px-3 py-1.5 text-xs text-rose-600 border border-rose-200 hover:bg-rose-50 rounded-lg transition-colors">
              删除
            </button>
          )}
        </div>
      </div>

      <div className="space-y-3">
        {/* 一、岗位基本信息 */}
        <div className={sectionCls}>
          <div className={sectionTitleCls}><User size={15} className="text-violet-500" /> 岗位基本信息</div>
          <div className="grid grid-cols-2 gap-x-6 gap-y-1">
            <div className={rowCls}><span className={labelCls}>岗位名称</span><span className={valueCls}>{card.positionName || '-'}</span></div>
            <div className={rowCls}><span className={labelCls}>在岗人员</span><span className={valueCls}>{card.positionHolder || '-'}</span></div>
            <div className={rowCls}><span className={labelCls}>汇报上级</span><span className={valueCls}>{card.reportTo || '-'}</span></div>
            <div className={rowCls}><span className={labelCls}>所属部门</span><span className={valueCls}>{card.department || '-'}</span></div>
            <div className={rowCls}><span className={labelCls}>所属团队</span><span className={valueCls}>{card.team || '-'}</span></div>
            <div className={rowCls}><span className={labelCls}>岗位性质</span><span className={valueCls}>{card.positionNature || '-'}</span></div>
          </div>
        </div>

        {/* 二、岗位职责 */}
        <div className={sectionCls}>
          <div className={sectionTitleCls}><Briefcase size={15} className="text-violet-500" /> 岗位职责</div>
          {parseList('coreResponsibilities').length > 0 && (
            <div className="mb-3">
              <div className="text-xs font-medium text-slate-500 mb-1.5">核心职责</div>
              <div className="space-y-1">
                {parseList('coreResponsibilities').map((item, i) => (
                  <div key={i} className="flex items-start gap-2 text-sm text-slate-700">
                    <span className="text-xs text-violet-400 mt-0.5 shrink-0">{i + 1}.</span>
                    {item}
                  </div>
                ))}
              </div>
            </div>
          )}
          {parseList('auxiliaryResponsibilities').length > 0 && (
            <div>
              <div className="text-xs font-medium text-slate-500 mb-1.5">辅助职责</div>
              <div className="space-y-1">
                {parseList('auxiliaryResponsibilities').map((item, i) => (
                  <div key={i} className="flex items-start gap-2 text-sm text-slate-600">
                    <span className="text-xs text-slate-400 mt-0.5 shrink-0">{i + 1}.</span>
                    {item}
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* 三、关键产出物 */}
        {parseDeliverables().length > 0 && (
          <div className={sectionCls}>
            <div className={sectionTitleCls}><Target size={15} className="text-violet-500" /> 关键产出物</div>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-slate-100">
                    <th className="text-left py-1.5 text-xs text-slate-400 font-medium">产出物</th>
                    <th className="text-left py-1.5 text-xs text-slate-400 font-medium">频率</th>
                    <th className="text-left py-1.5 text-xs text-slate-400 font-medium">交付对象</th>
                    <th className="text-left py-1.5 text-xs text-slate-400 font-medium">关键标准</th>
                  </tr>
                </thead>
                <tbody>
                  {parseDeliverables().map((d, i) => (
                    <tr key={i} className="border-b border-slate-50">
                      <td className="py-1.5 text-slate-700">{d.name || '-'}</td>
                      <td className="py-1.5 text-slate-600">{d.frequency ? `${d.frequency}频` : '-'}</td>
                      <td className="py-1.5 text-slate-600">{d.deliverTo || '-'}</td>
                      <td className="py-1.5 text-slate-600">{d.standard || '-'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* 四、能力要求 */}
        {(parseList('hardSkills').length > 0 || parseList('softSkills').length > 0) && (
          <div className={sectionCls}>
            <div className={sectionTitleCls}><Award size={15} className="text-violet-500" /> 能力要求</div>
            <div className="grid grid-cols-2 gap-4">
              {parseList('hardSkills').length > 0 && (
                <div>
                  <div className="text-xs font-medium text-slate-500 mb-1.5">硬技能 (工具/技术/资质)</div>
                  <div className="space-y-1">
                    {parseList('hardSkills').map((item, i) => (
                      <div key={i} className="flex items-center gap-2 text-sm text-slate-700">
                        <span className="w-1.5 h-1.5 bg-violet-400 rounded-full shrink-0" />
                        {item}
                      </div>
                    ))}
                  </div>
                </div>
              )}
              {parseList('softSkills').length > 0 && (
                <div>
                  <div className="text-xs font-medium text-slate-500 mb-1.5">软技能 (沟通/判断/协作)</div>
                  <div className="space-y-1">
                    {parseList('softSkills').map((item, i) => (
                      <div key={i} className="flex items-center gap-2 text-sm text-slate-700">
                        <span className="w-1.5 h-1.5 bg-purple-400 rounded-full shrink-0" />
                        {item}
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </div>
        )}

        {/* 五、协作关系 */}
        {(parseCollaboration('upstreamInputs').length > 0 || parseCollaboration('downstreamOutputs').length > 0) && (
          <div className={sectionCls}>
            <div className={sectionTitleCls}><Users size={15} className="text-violet-500" /> 协作关系</div>
            {parseCollaboration('upstreamInputs').length > 0 && (
              <div className="mb-3">
                <div className="text-xs font-medium text-slate-500 mb-1.5">上游输入 (我需要谁给我什么)</div>
                <div className="space-y-1">
                  {parseCollaboration('upstreamInputs').map((c, i) => (
                    <div key={i} className="flex items-center gap-2 text-sm text-slate-700">
                      <span className="text-xs text-blue-500 shrink-0">{c.position || '-'}</span>
                      <span className="text-slate-300">→</span>
                      <span>{c.content || '-'}</span>
                      {c.frequency && <span className="text-[10px] text-slate-400 ml-auto">{c.frequency}频</span>}
                    </div>
                  ))}
                </div>
              </div>
            )}
            {parseCollaboration('downstreamOutputs').length > 0 && (
              <div>
                <div className="text-xs font-medium text-slate-500 mb-1.5">下游输出 (我给谁提供什么)</div>
                <div className="space-y-1">
                  {parseCollaboration('downstreamOutputs').map((c, i) => (
                    <div key={i} className="flex items-center gap-2 text-sm text-slate-700">
                      <span className="text-xs text-green-500 shrink-0">{c.position || '-'}</span>
                      <span className="text-slate-300">→</span>
                      <span>{c.content || '-'}</span>
                      {c.frequency && <span className="text-[10px] text-slate-400 ml-auto">{c.frequency}频</span>}
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}

        {/* 六、当前状态 */}
        {(card.completedWork || card.ongoingWork || card.bottlenecks || card.supportNeeded) && (
          <div className={sectionCls}>
            <div className={sectionTitleCls}><AlertCircle size={15} className="text-violet-500" /> 当前状态</div>
            <div className="space-y-2">
              {card.completedWork && <div className={rowCls}><span className={labelCls}>已完成</span><span className={valueCls}>{card.completedWork}</span></div>}
              {card.ongoingWork && <div className={rowCls}><span className={labelCls}>进行中</span><span className={valueCls}>{card.ongoingWork}</span></div>}
              {card.bottlenecks && <div className={rowCls}><span className={labelCls}>卡点瓶颈</span><span className={cn(valueCls, "text-rose-600")}>{card.bottlenecks}</span></div>}
              {card.supportNeeded && <div className={rowCls}><span className={labelCls}>需要支持</span><span className={cn(valueCls, "text-amber-600")}>{card.supportNeeded}</span></div>}
            </div>
          </div>
        )}

        {/* 七、改进计划 */}
        {(card.improvementDirection || card.processOptimization || card.toolResourceNeeds) && (
          <div className={sectionCls}>
            <div className={sectionTitleCls}><TrendingUp size={15} className="text-violet-500" /> 改进计划</div>
            <div className="space-y-2">
              {card.improvementDirection && <div className={rowCls}><span className={labelCls}>提升方向</span><span className={valueCls}>{card.improvementDirection}</span></div>}
              {card.processOptimization && <div className={rowCls}><span className={labelCls}>流程优化</span><span className={valueCls}>{card.processOptimization}</span></div>}
              {card.toolResourceNeeds && <div className={rowCls}><span className={labelCls}>工具/资源</span><span className={valueCls}>{card.toolResourceNeeds}</span></div>}
            </div>
          </div>
        )}

        {/* 八、补充说明 */}
        {card.additionalNotes && (
          <div className={sectionCls}>
            <div className={sectionTitleCls}><FileText size={15} className="text-violet-500" /> 补充说明</div>
            <p className="text-sm text-slate-600">{card.additionalNotes}</p>
          </div>
        )}
      </div>
    </div>
  );
}
