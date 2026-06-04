'use client';

import React from 'react';
import { cn } from '@/lib/utils';
import { User, Shield, LogOut, ChevronDown } from 'lucide-react';

interface UserInfo {
  id: string;
  username: string;
  email: string;
  role: 'admin' | 'user';
  avatar?: string;
}

interface UserMenuProps {
  user: UserInfo;
  onLogout: () => void;
}

export default function UserMenu({ user, onLogout }: UserMenuProps) {
  const [isOpen, setIsOpen] = React.useState(false);
  const menuRef = React.useRef<HTMLDivElement>(null);

  // 点击外部关闭菜单
  React.useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const isAdmin = user.role?.toUpperCase() === 'ADMIN';

  return (
    <div className="relative" ref={menuRef}>
      {/* 触发按钮 */}
      <button
        onClick={() => setIsOpen(!isOpen)}
        className={cn(
          'flex items-center gap-2 px-3 py-2 rounded-xl',
          'bg-white/80 border border-slate-200/60',
          'hover:bg-slate-50 transition-all duration-200',
          'shadow-sm'
        )}
      >
        {/* 头像 */}
        <div className="w-8 h-8 rounded-full bg-gradient-to-br from-violet-500 to-purple-600 flex items-center justify-center overflow-hidden">
          {user.avatar ? (
            <img 
              src={user.avatar} 
              alt={user.username} 
              className="w-full h-full object-cover"
              onError={(e) => {
                // 当头像加载失败时，隐藏图片，显示默认图标
                (e.target as HTMLImageElement).style.display = 'none';
              }}
            />
          ) : null}
          <User className="w-4 h-4 text-white" />
        </div>
        
        {/* 用户名和角色 */}
        <div className="text-left hidden sm:block">
          <p className="text-sm font-medium text-slate-700">{user.username}</p>
          <div className="flex items-center gap-1">
            {isAdmin ? (
              <Shield className="w-3 h-3 text-violet-500" />
            ) : (
              <User className="w-3 h-3 text-slate-400" />
            )}
            <span className={cn(
              'text-xs',
              isAdmin ? 'text-violet-500' : 'text-slate-400'
            )}>
              {isAdmin ? '管理员' : '普通用户'}
            </span>
          </div>
        </div>
        
        <ChevronDown className={cn(
          'w-4 h-4 text-slate-400 transition-transform',
          isOpen && 'rotate-180'
        )} />
      </button>

      {/* 下拉菜单 */}
      {isOpen && (
        <div className={cn(
          'absolute right-0 top-full mt-2 w-56',
          'bg-white rounded-xl shadow-lg border border-slate-200/60',
          'py-2 z-50',
          'animate-in fade-in-0 zoom-in-95 duration-200'
        )}>
          {/* 用户信息 */}
          <div className="px-4 py-3 border-b border-slate-100">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-full bg-gradient-to-br from-violet-500 to-purple-600 flex items-center justify-center overflow-hidden">
                {user.avatar ? (
                  <img 
                    src={user.avatar} 
                    alt={user.username} 
                    className="w-full h-full object-cover"
                    onError={(e) => {
                      (e.target as HTMLImageElement).style.display = 'none';
                    }}
                  />
                ) : null}
                <User className="w-5 h-5 text-white" />
              </div>
              <div>
                <p className="font-medium text-slate-800">{user.username}</p>
                <p className="text-xs text-slate-400">{user.email}</p>
              </div>
            </div>
            <div className={cn(
              'mt-2 px-2 py-1 rounded-lg text-xs font-medium inline-flex items-center gap-1',
              isAdmin 
                ? 'bg-violet-100 text-violet-700' 
                : 'bg-slate-100 text-slate-600'
            )}>
              {isAdmin ? (
                <>
                  <Shield className="w-3 h-3" />
                  管理员权限
                </>
              ) : (
                <>
                  <User className="w-3 h-3" />
                  普通用户
                </>
              )}
            </div>
          </div>

          {/* 菜单项 */}
          <div className="py-1">
            {isAdmin && (
              <button
                onClick={() => {
                  setIsOpen(false);
                  // TODO: 打开用户管理
                }}
                className={cn(
                  'w-full px-4 py-2 text-left text-sm',
                  'hover:bg-slate-50 transition-colors',
                  'flex items-center gap-2 text-slate-700'
                )}
              >
                <Shield className="w-4 h-4 text-violet-500" />
                用户管理
              </button>
            )}
            
            <button
              onClick={() => {
                setIsOpen(false);
                onLogout();
              }}
              className={cn(
                'w-full px-4 py-2 text-left text-sm',
                'hover:bg-red-50 transition-colors',
                'flex items-center gap-2 text-red-600'
              )}
            >
              <LogOut className="w-4 h-4" />
              退出登录
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
