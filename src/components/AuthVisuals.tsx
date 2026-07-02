'use client';

import React, { useEffect, useRef } from 'react';
import { cn } from '@/lib/utils';

/* ============ 品牌侧边栏 ============ */
export function BrandSidebar() {
  return (
    <div className="hidden lg:flex lg:w-[45%] xl:w-[48%] relative overflow-hidden bg-gradient-to-br from-blue-600 via-indigo-600 to-violet-700">
      {/* 装饰网格 */}
      <div
        className="absolute inset-0 opacity-[0.06] pointer-events-none"
        style={{
          backgroundImage: `
            linear-gradient(rgba(255,255,255,0.5) 1px, transparent 1px),
            linear-gradient(90deg, rgba(255,255,255,0.5) 1px, transparent 1px)
          `,
          backgroundSize: '48px 48px',
        }}
      />

      {/* 装饰圆形 */}
      <div className="absolute -top-32 -right-32 w-96 h-96 bg-white/[0.04] rounded-full blur-2xl" />
      <div className="absolute -bottom-24 -left-24 w-72 h-72 bg-cyan-400/[0.08] rounded-full blur-3xl" />
      <div className="absolute top-1/3 right-1/4 w-48 h-48 bg-indigo-300/[0.06] rounded-full blur-2xl" />

      {/* 浮动装饰元素 */}
      <FloatingShapes />

      {/* 内容 */}
      <div className="relative z-10 flex flex-col justify-between p-12 xl:p-16 w-full">
        <div>
          {/* Logo */}
          <div className="flex items-center gap-3 mb-12">
            <div className="w-12 h-12 rounded-2xl bg-white/15 backdrop-blur-sm border border-white/20 flex items-center justify-center shadow-lg">
              <span className="text-2xl font-bold text-white">盈</span>
            </div>
            <div>
              <h2 className="text-xl font-bold text-white tracking-tight">盈云</h2>
              <p className="text-xs text-blue-200/70">YingYun Platform</p>
            </div>
          </div>

          {/* 主标题 */}
          <h1 className="text-4xl xl:text-5xl font-bold text-white leading-tight mb-4">
            产品智能
            <br />
            <span className="text-blue-200">中台</span>
          </h1>
          <p className="text-base text-blue-100/70 leading-relaxed max-w-sm">
            AI 驱动的服装行业数据智能平台，赋能设计、供应链与市场营销全链路
          </p>
        </div>

        {/* 特性列表 */}
        <div className="space-y-4">
          {[
            { icon: '◆', title: 'AI 智能识别', desc: '自动分类标签，智能推荐' },
            { icon: '◉', title: '供应链协同', desc: '原料·生产·物流全链路管控' },
            { icon: '◎', title: '多品牌管理', desc: '统一中台，独立运营' },
            { icon: '◇', title: '实时数据洞察', desc: '秒级监控，智能预警' },
          ].map((item, i) => (
            <div
              key={i}
              className="flex items-start gap-3 p-3 rounded-xl bg-white/[0.06] backdrop-blur-sm border border-white/[0.08]"
            >
              <span className="text-lg text-blue-200/80 mt-0.5">{item.icon}</span>
              <div>
                <h4 className="text-sm font-semibold text-white/90">{item.title}</h4>
                <p className="text-xs text-blue-200/50">{item.desc}</p>
              </div>
            </div>
          ))}
        </div>

        {/* 底部 */}
        <p className="text-xs text-blue-200/30 mt-8">
          © 2026 盈云科技 · 产品智能中台 v2.0
        </p>
      </div>
    </div>
  );
}

/* ============ 浮动装饰图形 ============ */
function FloatingShapes() {
  return (
    <div className="absolute inset-0 pointer-events-none overflow-hidden">
      {/* 旋转方块 */}
      <div className="absolute top-[15%] right-[20%] w-16 h-16 border border-white/[0.08] rounded-lg rotate-45 animate-[spin_20s_linear_infinite]" />
      <div className="absolute top-[60%] right-[10%] w-10 h-10 border border-white/[0.06] rounded-md rotate-12 animate-[spin_30s_linear_infinite_reverse]" />
      {/* 小圆点 */}
      <div className="absolute top-[30%] left-[20%] w-2 h-2 bg-white/10 rounded-full animate-pulse" />
      <div className="absolute top-[70%] left-[15%] w-1.5 h-1.5 bg-cyan-300/15 rounded-full animate-pulse" style={{ animationDelay: '1s' }} />
      <div className="absolute top-[45%] right-[35%] w-1 h-1 bg-white/10 rounded-full animate-pulse" style={{ animationDelay: '2s' }} />
      {/* 线段 */}
      <div className="absolute top-[25%] left-[10%] w-24 h-px bg-gradient-to-r from-white/[0.08] to-transparent rotate-[30deg]" />
      <div className="absolute top-[80%] right-[25%] w-32 h-px bg-gradient-to-l from-white/[0.06] to-transparent rotate-[-15deg]" />
    </div>
  );
}

/* ============ 输入框样式 ============ */
export const inputClass = cn(
  'w-full h-11 px-4 rounded-xl',
  'bg-slate-50 border border-slate-200',
  'text-slate-800 text-sm placeholder:text-slate-400',
  'focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-400',
  'transition-all duration-200'
);

/* ============ 主按钮样式 ============ */
export const primaryBtnClass = cn(
  'w-full h-11 rounded-xl',
  'bg-gradient-to-r from-blue-600 to-indigo-600',
  'text-white text-sm font-semibold',
  'hover:from-blue-500 hover:to-indigo-500',
  'active:from-blue-700 active:to-indigo-700',
  'shadow-lg shadow-blue-500/25',
  'hover:shadow-blue-500/40',
  'transition-all duration-200',
  'disabled:opacity-50 disabled:cursor-not-allowed'
);

/* ============ 次要按钮样式 ============ */
export const secondaryBtnClass = cn(
  'px-5 h-11 rounded-xl',
  'bg-white border border-slate-200',
  'text-slate-700 text-sm font-medium',
  'hover:bg-slate-50 hover:border-slate-300',
  'shadow-sm',
  'transition-all duration-200'
);

/* ============ 分隔线 ============ */
export function Divider({ text }: { text: string }) {
  return (
    <div className="flex items-center gap-3 my-6">
      <div className="flex-1 h-px bg-slate-200" />
      <span className="text-xs text-slate-400 uppercase tracking-wider">{text}</span>
      <div className="flex-1 h-px bg-slate-200" />
    </div>
  );
}

/* ============ 密码强度指示 ============ */
export function PasswordStrength({ password }: { password: string }) {
  if (!password) return null;
  let strength = 0;
  if (password.length >= 6) strength++;
  if (password.length >= 10) strength++;
  if (/[A-Z]/.test(password) && /[a-z]/.test(password)) strength++;
  if (/\d/.test(password)) strength++;
  if (/[^A-Za-z0-9]/.test(password)) strength++;

  const level = Math.min(Math.ceil(strength / 1.2), 4);
  const labels = ['', '弱', '较弱', '良好', '强'];
  const colors = ['', 'bg-red-400', 'bg-orange-400', 'bg-yellow-400', 'bg-green-500'];

  return (
    <div className="flex items-center gap-2 mt-1.5">
      <div className="flex gap-1 flex-1">
        {[1, 2, 3, 4].map(i => (
          <div
            key={i}
            className={cn(
              'h-1 flex-1 rounded-full transition-all duration-300',
              i <= level ? colors[level] : 'bg-slate-200'
            )}
          />
        ))}
      </div>
      <span className={cn(
        'text-[10px] font-medium',
        level <= 1 ? 'text-red-400' : level <= 2 ? 'text-orange-400' : level <= 3 ? 'text-yellow-500' : 'text-green-500'
      )}>
        {labels[level]}
      </span>
    </div>
  );
}
