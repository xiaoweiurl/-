'use client';

import React, { useEffect, useRef } from 'react';
import { cn } from '@/lib/utils';
import { Hexagon } from 'lucide-react';

/* ============ 粒子背景 Canvas ============ */
export function ParticleBackground() {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let animId: number;
    let w = 0, h = 0;

    const resize = () => {
      w = canvas.width = window.innerWidth;
      h = canvas.height = window.innerHeight;
    };
    resize();
    window.addEventListener('resize', resize);

    const particles: { x: number; y: number; vx: number; vy: number; size: number; alpha: number }[] = [];
    for (let i = 0; i < 80; i++) {
      particles.push({
        x: Math.random() * w,
        y: Math.random() * h,
        vx: (Math.random() - 0.5) * 0.3,
        vy: (Math.random() - 0.5) * 0.3,
        size: Math.random() * 1.5 + 0.5,
        alpha: Math.random() * 0.5 + 0.2,
      });
    }

    const draw = () => {
      ctx.clearRect(0, 0, w, h);

      for (let i = 0; i < particles.length; i++) {
        for (let j = i + 1; j < particles.length; j++) {
          const dx = particles[i].x - particles[j].x;
          const dy = particles[i].y - particles[j].y;
          const dist = Math.sqrt(dx * dx + dy * dy);
          if (dist < 150) {
            const alpha = (1 - dist / 150) * 0.15;
            ctx.beginPath();
            ctx.strokeStyle = `rgba(59, 130, 246, ${alpha})`;
            ctx.lineWidth = 0.5;
            ctx.moveTo(particles[i].x, particles[i].y);
            ctx.lineTo(particles[j].x, particles[j].y);
            ctx.stroke();
          }
        }
      }

      particles.forEach(p => {
        p.x += p.vx;
        p.y += p.vy;
        if (p.x < 0 || p.x > w) p.vx *= -1;
        if (p.y < 0 || p.y > h) p.vy *= -1;

        ctx.beginPath();
        ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2);
        ctx.fillStyle = `rgba(59, 130, 246, ${p.alpha})`;
        ctx.fill();
      });

      animId = requestAnimationFrame(draw);
    };
    draw();

    return () => {
      cancelAnimationFrame(animId);
      window.removeEventListener('resize', resize);
    };
  }, []);

  return (
    <canvas
      ref={canvasRef}
      className="absolute inset-0 w-full h-full"
      style={{ opacity: 0.6 }}
    />
  );
}

/* ============ 扫描线装饰 ============ */
export function ScanLine() {
  return (
    <div className="absolute top-0 left-0 right-0 h-px bg-gradient-to-r from-transparent via-blue-500/40 to-transparent" />
  );
}

/* ============ 浮动角标装饰 ============ */
export function CornerDecoration() {
  return (
    <>
      <div className="absolute top-0 left-0 w-16 h-px bg-gradient-to-r from-blue-500/60 to-transparent" />
      <div className="absolute top-0 left-0 w-px h-16 bg-gradient-to-b from-blue-500/60 to-transparent" />
      <div className="absolute top-0 right-0 w-16 h-px bg-gradient-to-l from-blue-500/60 to-transparent" />
      <div className="absolute top-0 right-0 w-px h-16 bg-gradient-to-b from-blue-500/60 to-transparent" />
      <div className="absolute bottom-0 left-0 w-16 h-px bg-gradient-to-r from-blue-500/60 to-transparent" />
      <div className="absolute bottom-0 left-0 w-px h-16 bg-gradient-to-t from-blue-500/60 to-transparent" />
      <div className="absolute bottom-0 right-0 w-16 h-px bg-gradient-to-l from-blue-500/60 to-transparent" />
      <div className="absolute bottom-0 right-0 w-px h-16 bg-gradient-to-t from-blue-500/60 to-transparent" />
    </>
  );
}

/* ============ 玻璃态卡片 ============ */
export function GlassCard({ children, className, style, onClick }: { children: React.ReactNode; className?: string; style?: React.CSSProperties; onClick?: () => void }) {
  return (
    <div
      onClick={onClick}
      style={style}
      className={cn(
        'relative overflow-hidden rounded-2xl',
        'backdrop-blur-xl',
        'bg-slate-900/40',
        'border border-blue-500/10',
        'shadow-[0_0_40px_-12px_rgba(59,130,246,0.15)]',
        className
      )}
    >
      <CornerDecoration />
      <div className="absolute inset-0 bg-gradient-to-b from-blue-500/[0.02] to-transparent pointer-events-none" />
      {children}
    </div>
  );
}

/* ============ Hexagon Logo ============ */
export function HexagonLogo({ text, className }: { text: string; className?: string }) {
  return (
    <div className={cn('relative', className)}>
      <div className="absolute inset-0 bg-blue-500/20 blur-2xl rounded-full animate-pulse" />
      <div className="relative w-20 h-20 mx-auto rounded-2xl bg-gradient-to-br from-slate-800 to-slate-900 border border-blue-500/20 flex items-center justify-center">
        <Hexagon className="absolute w-12 h-12 text-blue-500/20" />
        <span className="text-3xl font-bold bg-gradient-to-br from-blue-400 to-cyan-400 bg-clip-text text-transparent z-10">
          {text}
        </span>
      </div>
    </div>
  );
}

/* ============ 数据流装饰 ============ */
export function DataFlowDecoration() {
  return (
    <div className="flex items-center justify-center gap-4 text-xs text-slate-500/40 font-mono">
      {['0x7F...A3', '0x3A...E1', '0x9C...B2', '0x1D...F4', '0x8B...C7'].map((hex, i) => (
        <span key={i} className="flex items-center gap-2">
          <span className="w-1.5 h-1.5 rounded-full bg-blue-500/30 animate-pulse" style={{ animationDelay: `${i * 0.3}s` }} />
          {hex}
        </span>
      ))}
    </div>
  );
}

/* ============ 安全标签 ============ */
export function SecurityBadges() {
  return (
    <div className="flex items-center justify-center gap-6 text-[10px] text-slate-500/50 uppercase tracking-widest">
      <span className="flex items-center gap-1.5">
        <span className="w-1 h-1 rounded-full bg-green-500/50" />
        加密传输
      </span>
      <span className="flex items-center gap-1.5">
        <span className="w-1 h-1 rounded-full bg-blue-500/50" />
        安全连接
      </span>
      <span className="flex items-center gap-1.5">
        <span className="w-1 h-1 rounded-full bg-cyan-500/50" />
        实时监控
      </span>
    </div>
  );
}

/* ============ 能力标签 ============ */
export function CapabilityTags() {
  return (
    <div className="flex flex-wrap items-center justify-center gap-2">
      {['AI 驱动', '供应链', '多品牌', '实时同步'].map((tag) => (
        <span
          key={tag}
          className="px-2.5 py-1 rounded-md text-[10px] text-blue-400/60 bg-blue-500/5 border border-blue-500/10"
        >
          {tag}
        </span>
      ))}
    </div>
  );
}

/* ============ 深色页面外壳 ============ */
export function AuthShell({ children }: { children?: React.ReactNode }) {
  return (
    <div className="min-h-screen bg-[#060b14] relative flex items-center justify-center p-4 overflow-hidden">
      <ParticleBackground />

      {/* 径向光晕 */}
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[800px] h-[800px] bg-blue-500/[0.03] rounded-full blur-3xl pointer-events-none" />
      <div className="absolute top-1/4 right-1/4 w-[400px] h-[400px] bg-cyan-500/[0.02] rounded-full blur-3xl pointer-events-none" />

      {/* 网格背景 */}
      <div
        className="absolute inset-0 opacity-[0.03] pointer-events-none"
        style={{
          backgroundImage: `
            linear-gradient(rgba(59,130,246,0.3) 1px, transparent 1px),
            linear-gradient(90deg, rgba(59,130,246,0.3) 1px, transparent 1px)
          `,
          backgroundSize: '60px 60px',
        }}
      />

      <ScanLine />

      {children}
    </div>
  );
}
