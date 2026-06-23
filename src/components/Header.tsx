'use client';

import React from 'react';
import { useRouter } from 'next/navigation';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useNotifications } from '@/contexts/NotificationContext';
import {
  Search,
  Bell,
  Moon,
  Sun,
  Grid3x3,
  LayoutGrid,
  List,
  SlidersHorizontal,
  CheckSquare,
  User,
  ChevronDown,
  LogOut,
  Settings,
  UserCog,
  Shield,
  X,
  FileSpreadsheet,
  Upload,
  Download,
  Filter,
  RefreshCw,
  Sparkles,
  Scissors,
  Cloud,
  Zap,
} from 'lucide-react';
import { type BrandConfig } from '@/lib/brand';

export interface CurrentUser {
  id: string;
  username: string;
  email: string;
  role: 'admin' | 'user';
  company?: string;
}

interface HeaderProps {
  searchQuery: string;
  onSearchChange: (query: string) => void;
  onSearchSubmit?: () => void;
  viewMode: 'grid' | 'masonry' | 'list';
  onViewModeChange: (mode: 'grid' | 'masonry' | 'list') => void;
  selectedCount: number;
  onBulkAction: () => void;
  currentUser?: CurrentUser | null;
  onLogout?: () => void;
  onFilterClick?: () => void;
  onExcelUploadClick?: () => void;
  onExportClick?: () => void;
  hasAlbums?: boolean;
  brand?: BrandConfig;

  showSearch?: boolean;
  onBatchReplaceMainImage?: () => void;
}

export default function Header({
  searchQuery,
  onSearchChange,
  onSearchSubmit,
  viewMode,
  onViewModeChange,
  selectedCount,
  onBulkAction,
  currentUser,
  onLogout,
  onFilterClick,
  onExcelUploadClick,
  onExportClick,
  hasAlbums = false,
  showSearch = true,
  brand,
  onBatchReplaceMainImage,
}: HeaderProps) {
  const router = useRouter();
  const [showNotifications, setShowNotifications] = React.useState(false);
  const [isDark, setIsDark] = React.useState(false);
  const [showUserMenu, setShowUserMenu] = React.useState(false);
  const brandAccent = brand?.key === 'bonasi' ? 'rose' : 'violet';
  
  const { notifications, unreadCount, markAsRead, markAllAsRead, clearNewFlag } = useNotifications();

  const formatTime = (dateString: string) => {
    const date = new Date(dateString);
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    const minutes = Math.floor(diff / (1000 * 60));
    const hours = Math.floor(diff / (1000 * 60 * 60));
    const days = Math.floor(diff / (1000 * 60 * 60 * 24));
    
    if (minutes < 1) return '刚刚';
    if (minutes < 60) return `${minutes}分钟前`;
    if (hours < 24) return `${hours}小时前`;
    if (days < 7) return `${days}天前`;
    return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' });
  };

  const getNotificationIcon = (type: string) => {
    const icons: Record<string, string> = {
      system: '📢', upload: '📤', album: '📁', share: '🔗',
      comment: '💬', like: '❤️', warning: '⚠️', document: '📄',
      delete: '🗑️', download: '📥',
    };
    return icons[type] || '📢';
  };

  const handleNotificationClick = (id: string, read: boolean) => {
    if (!read) markAsRead(id);
    clearNewFlag(id);
  };

  return (
    <header className="h-[60px] bg-white/[0.97] backdrop-blur-xl border-b border-slate-200/50 px-5 flex items-center justify-between sticky top-0 z-10">
      {/* 左侧搜索栏 */}
      {showSearch ? (
        <div className="flex-1 max-w-xl">
          <div className="relative group">
            <Search className={cn(
              "absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 transition-colors",
              brandAccent === 'rose' ? 'text-slate-400 group-focus-within:text-rose-500' : 'text-slate-400 group-focus-within:text-violet-500'
            )} />
            <Input
              type="text"
              placeholder="搜索图片、相册、标签..."
              value={searchQuery}
              onChange={(e) => onSearchChange(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter' && onSearchSubmit) onSearchSubmit(); }}
              className={cn(
                "pl-10 pr-4 h-9 text-[13px] bg-slate-50/80 border-slate-200/60 rounded-lg",
                brandAccent === 'rose'
                  ? 'focus:bg-white focus:border-rose-300 focus:ring-rose-500/15'
                  : 'focus:bg-white focus:border-violet-300 focus:ring-violet-500/15',
                'transition-all duration-200'
              )}
            />
            <kbd className="absolute right-3 top-1/2 -translate-y-1/2 hidden sm:inline-flex items-center gap-0.5 px-1.5 py-0.5 text-[10px] text-slate-400 bg-slate-100 rounded border border-slate-200/50">
              ⌘K
            </kbd>
          </div>
        </div>
      ) : (
        <div className="flex-1" />
      )}

      {/* 右侧工具栏 */}
      <div className="flex items-center gap-1.5 ml-4">
        {/* 批量替换主图 */}
        {onBatchReplaceMainImage && currentUser?.role === 'admin' && (
          <button
            onClick={onBatchReplaceMainImage}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-[12px] font-medium text-amber-700 bg-amber-50 border border-amber-200/60 rounded-lg hover:bg-amber-100 transition-colors"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            <span className="hidden sm:inline">替换主图</span>
          </button>
        )}

        {/* 批量操作 */}
        {selectedCount > 0 && (
          <button
            onClick={onBulkAction}
            className={cn(
              "inline-flex items-center gap-1.5 px-3 py-1.5 text-[12px] font-medium rounded-lg transition-colors",
              brandAccent === 'rose'
                ? 'text-rose-700 bg-rose-50 border border-rose-200/60 hover:bg-rose-100'
                : 'text-violet-700 bg-violet-50 border border-violet-200/60 hover:bg-violet-100'
            )}
          >
            <CheckSquare className="w-3.5 h-3.5" />
            {selectedCount} 选中
          </button>
        )}

        {/* 视图切换 */}
        <div className="flex items-center bg-slate-100/80 rounded-lg p-0.5 border border-slate-200/40">
          {[
            { mode: 'grid' as const, Icon: Grid3x3, title: '网格' },
            { mode: 'masonry' as const, Icon: LayoutGrid, title: '瀑布流' },
            { mode: 'list' as const, Icon: List, title: '列表' },
          ].map(({ mode, Icon, title }) => (
            <button
              key={mode}
              onClick={() => onViewModeChange(mode)}
              title={title}
              className={cn(
                'p-1.5 rounded-md transition-all duration-150',
                viewMode === mode
                  ? cn('bg-white shadow-sm', brandAccent === 'rose' ? 'text-rose-600' : 'text-violet-600')
                  : 'text-slate-400 hover:text-slate-600'
              )}
            >
              <Icon className="w-3.5 h-3.5" />
            </button>
          ))}
        </div>

        {/* 筛选 */}
        <button
          onClick={onFilterClick}
          className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100/80 rounded-lg transition-colors"
          title="筛选"
        >
          <SlidersHorizontal className="w-4 h-4" />
        </button>

        {/* 分隔线 */}
        <div className="w-px h-5 bg-slate-200/60 mx-0.5" />

        {/* Excel导入 */}
        {currentUser?.role === 'admin' && (
          <button
            onClick={onExcelUploadClick}
            title="Excel导入"
            className={cn(
              "p-2 rounded-lg transition-colors",
              brandAccent === 'rose'
                ? 'text-rose-500 hover:text-rose-600 hover:bg-rose-50'
                : 'text-violet-500 hover:text-violet-600 hover:bg-violet-50'
            )}
          >
            <FileSpreadsheet className="w-4 h-4" />
          </button>
        )}

        {/* 批量导出 */}
        {currentUser?.role === 'admin' && (
          <button
            onClick={onExportClick}
            disabled={!hasAlbums}
            title={hasAlbums ? '批量导出' : '暂无分类可导出'}
            className={cn(
              "p-2 rounded-lg transition-colors",
              hasAlbums
                ? brandAccent === 'rose'
                  ? 'text-rose-500 hover:text-rose-600 hover:bg-rose-50'
                  : 'text-violet-500 hover:text-violet-600 hover:bg-violet-50'
                : 'text-slate-300 cursor-not-allowed'
            )}
          >
            <Download className="w-4 h-4" />
          </button>
        )}

        {/* 分隔线 */}
        <div className="w-px h-5 bg-slate-200/60 mx-0.5" />

        {/* AI生图入口 */}
        <button
          onClick={() => window.location.href = '/ai-image'}
          className={cn(
            "inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[12px] font-medium",
            "bg-gradient-to-r text-white",
            brand?.primaryFrom || 'from-violet-500', brand?.primaryTo || 'to-purple-600',
            "hover:shadow-md transition-all duration-200",
            brand?.buttonShadow || 'shadow-purple-500/20'
          )}
        >
          <Zap className="w-3.5 h-3.5" />
          <span className="hidden sm:inline">{brand?.name || '盈云'}AI</span>
        </button>

        {/* 通知 */}
        <div className="relative">
          <button
            onClick={() => setShowNotifications(!showNotifications)}
            className="relative p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100/80 rounded-lg transition-colors"
          >
            <Bell className={cn("w-4 h-4", unreadCount > 0 && 'animate-bell-shake')} />
            {unreadCount > 0 && (
              <span className="absolute -top-0.5 -right-0.5 min-w-[16px] h-4 px-1 bg-red-500 rounded-full text-white text-[10px] font-bold flex items-center justify-center">
                {unreadCount > 99 ? '99+' : unreadCount}
              </span>
            )}
          </button>

          {showNotifications && (
            <div className="absolute right-0 top-11 w-80 bg-white rounded-xl shadow-2xl shadow-black/8 border border-slate-200/60 overflow-hidden animate-in fade-in slide-in-from-top-2 duration-200">
              <div className="px-4 py-3 border-b border-slate-100 flex items-center justify-between">
                <h3 className="text-[13px] font-semibold text-slate-800">通知</h3>
                <div className="flex items-center gap-2">
                  {unreadCount > 0 && (
                    <button onClick={markAllAsRead} className={cn("text-[12px] font-medium", brandAccent === 'rose' ? 'text-rose-600' : 'text-violet-600')}>
                      全部已读
                    </button>
                  )}
                  <button onClick={() => setShowNotifications(false)} className="p-1 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-md transition-colors">
                    <X className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
              <div className="max-h-80 overflow-y-auto">
                {notifications.length === 0 ? (
                  <div className="py-10 text-center">
                    <Bell className="w-8 h-8 text-slate-200 mx-auto mb-2" />
                    <p className="text-[13px] text-slate-400">暂无通知</p>
                  </div>
                ) : (
                  notifications.slice(0, 5).map((notification) => (
                    <div
                      key={notification.id}
                      onClick={() => handleNotificationClick(notification.id, notification.read)}
                      className={cn(
                        'px-4 py-3 border-b border-slate-50 hover:bg-slate-50/50 transition-colors cursor-pointer',
                        !notification.read && (brandAccent === 'rose' ? 'bg-rose-50/30' : 'bg-violet-50/30'),
                        notification.isNew && 'animate-pulse-once'
                      )}
                    >
                      <div className="flex gap-2.5">
                        <span className="text-base flex-shrink-0 mt-0.5">{getNotificationIcon(notification.type)}</span>
                        <div className="flex-1 min-w-0">
                          <p className="text-[13px] font-medium text-slate-700">{notification.title}</p>
                          <p className="text-[12px] text-slate-500 truncate mt-0.5">{notification.message}</p>
                          <span className="text-[11px] text-slate-400 mt-1">{formatTime(notification.createdAt)}</span>
                        </div>
                        {!notification.read && (
                          <span className={cn("w-1.5 h-1.5 rounded-full mt-2 flex-shrink-0 animate-pulse", brandAccent === 'rose' ? 'bg-rose-500' : 'bg-violet-500')} />
                        )}
                      </div>
                    </div>
                  ))
                )}
              </div>
              <div className="px-3 py-2.5 bg-slate-50/50 border-t border-slate-100">
                <button
                  onClick={() => { setShowNotifications(false); router.push('/notifications'); }}
                  className={cn("w-full text-[12px] font-medium py-1 rounded-md hover:bg-slate-100/80 transition-colors", brandAccent === 'rose' ? 'text-rose-600' : 'text-violet-600')}
                >
                  查看全部
                </button>
              </div>
            </div>
          )}
        </div>

        {/* 主题切换 */}
        <button
          onClick={() => setIsDark(!isDark)}
          className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100/80 rounded-lg transition-colors"
        >
          {isDark ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
        </button>

        {/* 用户菜单 */}
        <div className="relative">
          <button
            onClick={() => setShowUserMenu(!showUserMenu)}
            className="flex items-center gap-2 pl-2 pr-1.5 py-1 rounded-lg hover:bg-slate-100/80 transition-colors"
          >
            <div className="text-right hidden sm:block">
              <p className="text-[13px] font-medium text-slate-700 leading-tight flex items-center gap-1.5">
                {currentUser?.username || '未登录'}
                {currentUser?.company && (
                  <span className={cn(
                    "inline-flex items-center px-1.5 py-px rounded text-[9px] font-bold tracking-wide uppercase",
                    brand?.tagBg || 'bg-indigo-100', brand?.tagText || 'text-indigo-700'
                  )}>
                    {currentUser.company}
                  </span>
                )}
              </p>
              <p className="text-[11px] text-slate-400 flex items-center gap-1 mt-0.5">
                {currentUser?.role === 'admin' && <Shield className="w-3 h-3" />}
                {currentUser?.role === 'admin' ? '管理员' : '普通用户'}
              </p>
            </div>
            <div className="relative">
              <div className={cn(
                "w-8 h-8 rounded-lg flex items-center justify-center shadow-sm",
                currentUser?.role === 'admin'
                  ? cn("bg-gradient-to-br shadow-sm", brand?.primaryFrom || 'from-violet-500', brand?.primaryTo || 'to-purple-600')
                  : "bg-gradient-to-br from-slate-400 to-slate-500"
              )}>
                <User className="w-4 h-4 text-white" />
              </div>
              <div className="absolute -bottom-0.5 -right-0.5 w-2.5 h-2.5 bg-emerald-400 rounded-full border-2 border-white" />
            </div>
            <ChevronDown className={cn("w-3.5 h-3.5 text-slate-400 transition-transform", showUserMenu && 'rotate-180')} />
          </button>

          {showUserMenu && (
            <div className="absolute right-0 top-11 w-56 bg-white rounded-xl shadow-2xl shadow-black/8 border border-slate-200/60 overflow-hidden animate-in fade-in slide-in-from-top-2 duration-200">
              <div className={cn("px-4 py-3 border-b border-slate-100", brandAccent === 'rose' ? 'bg-gradient-to-r from-rose-50 to-pink-50' : 'bg-gradient-to-r from-violet-50 to-purple-50')}>
                <div className="flex items-center gap-3">
                  <div className={cn(
                    "w-9 h-9 rounded-lg flex items-center justify-center shadow-sm",
                    "bg-gradient-to-br",
                    brand?.primaryFrom || 'from-violet-500', brand?.primaryTo || 'to-purple-600'
                  )}>
                    <User className="w-5 h-5 text-white" />
                  </div>
                  <div>
                    <p className="text-[13px] font-semibold text-slate-800">{currentUser?.username}</p>
                    <p className="text-[11px] text-slate-500">{currentUser?.email}</p>
                  </div>
                </div>
                {currentUser?.role === 'admin' && (
                  <span className={cn(
                    "inline-flex items-center gap-1 px-1.5 py-0.5 mt-2 text-[10px] font-bold rounded-md",
                    brand?.tagBg || 'bg-violet-100', brand?.tagText || 'text-violet-700'
                  )}>
                    <Shield className="w-3 h-3" />
                    管理员
                  </span>
                )}
              </div>
              
              <div className="p-1.5">
                {currentUser?.role === 'admin' && (
                  <button
                    onClick={() => { setShowUserMenu(false); router.push('/user-settings'); }}
                    className="w-full flex items-center gap-2.5 px-3 py-2 rounded-lg hover:bg-slate-50 transition-colors text-left"
                  >
                    <UserCog className="w-4 h-4 text-slate-400" />
                    <span className="text-[13px] text-slate-700">用户管理</span>
                  </button>
                )}
                <button
                  onClick={() => { setShowUserMenu(false); router.push('/settings'); }}
                  className="w-full flex items-center gap-2.5 px-3 py-2 rounded-lg hover:bg-slate-50 transition-colors text-left"
                >
                  <Settings className="w-4 h-4 text-slate-400" />
                  <span className="text-[13px] text-slate-700">账户设置</span>
                </button>
              </div>

              <div className="p-1.5 border-t border-slate-100">
                <button
                  onClick={() => { setShowUserMenu(false); onLogout?.(); }}
                  className="w-full flex items-center gap-2.5 px-3 py-2 rounded-lg hover:bg-red-50 transition-colors text-left group"
                >
                  <LogOut className="w-4 h-4 text-slate-400 group-hover:text-red-500" />
                  <span className="text-[13px] text-slate-700 group-hover:text-red-600">退出登录</span>
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
