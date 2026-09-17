'use client';

import React from 'react';
import { useRouter } from 'next/navigation';
import { cn } from '@/lib/utils';
import { Input } from '@/components/ui/input';
import { useNotifications } from '@/contexts/NotificationContext';
import {
  Search,
  Bell,
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
  Download,
  RefreshCw,
  Zap,
} from 'lucide-react';
import { type BrandConfig } from '@/lib/brand';
import { isAdminOrAbove } from '@/lib/auth';

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
  const [showUserMenu, setShowUserMenu] = React.useState(false);
  
  const { notifications, unreadCount, markAsRead, markAllAsRead, clearNewFlag, fetchNotifications } = useNotifications();

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
    <header className="h-[60px] ios-glass border-b border-[#E5E5EA] px-5 flex items-center justify-between sticky top-0 z-10">
      {/* 左侧搜索栏 */}
      {showSearch ? (
        <div className="flex-1 max-w-xl">
          <div className="relative group">
            <Search className={cn(
              "absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 transition-colors text-[#8E8E93] group-focus-within:text-[#007AFF]"
            )} />
            <Input
              type="text"
              placeholder="搜索图片、相册、标签..."
              value={searchQuery}
              onChange={(e) => onSearchChange(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter' && onSearchSubmit) onSearchSubmit(); }}
              className={cn(
                "pl-10 pr-4 h-9 text-[13px] bg-[rgba(118,118,128,0.12)] border-transparent text-[#1C1C1E] rounded-xl",
                'focus:bg-white focus:border-[#007AFF] focus:ring-[#007AFF]/20',
                'transition-all duration-200'
              )}
            />
            <kbd className="absolute right-3 top-1/2 -translate-y-1/2 hidden sm:inline-flex items-center gap-0.5 px-1.5 py-0.5 text-[10px] text-[#8E8E93] bg-white rounded-md border border-[#E5E5EA]">
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
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-[12px] font-medium text-[#FF9500] bg-[#FF9500]/[0.12] rounded-xl hover:bg-[#FF9500]/[0.18] transition-colors"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            <span className="hidden sm:inline">替换主图</span>
          </button>
        )}

        {/* 批量操作 */}
        {selectedCount > 0 && (
          <button
            onClick={onBulkAction}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-[12px] font-medium rounded-xl transition-colors text-[#007AFF] bg-[#007AFF]/[0.1] hover:bg-[#007AFF]/[0.16]"
          >
            <CheckSquare className="w-3.5 h-3.5" />
            {selectedCount} 选中
          </button>
        )}

        {/* 视图切换 */}
        <div className="flex items-center bg-[rgba(118,118,128,0.12)] rounded-xl p-0.5">
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
                  ? 'bg-white shadow-sm text-[#007AFF]'
                  : 'text-[#8E8E93] hover:text-[#3A3A3C]'
              )}
            >
              <Icon className="w-3.5 h-3.5" />
            </button>
          ))}
        </div>

        {/* 筛选 */}
        <button
          onClick={onFilterClick}
          className="p-2 text-[#8E8E93] hover:text-[#3A3A3C] hover:bg-black/5 rounded-xl transition-colors"
          title="筛选"
        >
          <SlidersHorizontal className="w-4 h-4" />
        </button>

        {/* 分隔线 */}
        <div className="w-px h-5 bg-[#E5E5EA] mx-0.5" />

        {/* Excel导入 */}
        {currentUser?.role === 'admin' && (
          <button
            onClick={onExcelUploadClick}
            title="Excel导入"
            className="p-2 rounded-xl transition-colors text-[#007AFF] hover:bg-[#007AFF]/[0.08]"
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
              "p-2 rounded-xl transition-colors",
              hasAlbums
                ? 'text-[#007AFF] hover:bg-[#007AFF]/[0.08]'
                : 'text-[#C7C7CC] cursor-not-allowed'
            )}
          >
            <Download className="w-4 h-4" />
          </button>
        )}

        {/* 分隔线 */}
        <div className="w-px h-5 bg-[#E5E5EA] mx-0.5" />

        {/* AI生图入口 */}
        <button
          onClick={() => window.location.href = '/ai-image'}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-[12px] font-medium bg-[#007AFF] text-white hover:opacity-88 active:scale-97 transition-all duration-200"
        >
          <Zap className="w-3.5 h-3.5" />
          <span className="hidden sm:inline">{brand?.name || '宝娜斯'}AI</span>
        </button>

        {/* 通知 */}
        <div className="relative">
          <button
            onClick={() => {
              const next = !showNotifications;
              setShowNotifications(next);
              if (next) fetchNotifications(); // 打开时刷新通知列表
            }}
            className="relative p-2 text-[#8E8E93] hover:text-[#3A3A3C] hover:bg-black/5 rounded-xl transition-colors"
          >
            <Bell className={cn("w-4 h-4", unreadCount > 0 && 'animate-bell-shake')} />
            {unreadCount > 0 && (
              <span className="absolute -top-0.5 -right-0.5 min-w-[16px] h-4 px-1 bg-[#FF3B30] rounded-full text-white text-[10px] font-bold flex items-center justify-center">
                {unreadCount > 99 ? '99+' : unreadCount}
              </span>
            )}
          </button>

          {showNotifications && (
            <div className="absolute right-0 top-11 w-80 bg-white rounded-2xl shadow-[0_6px_24px_rgba(0,0,0,0.12)] border border-[#E5E5EA] overflow-hidden animate-in fade-in slide-in-from-top-2 duration-200">
              <div className="px-4 py-3 border-b border-[#E5E5EA] flex items-center justify-between">
                <h3 className="text-[13px] font-semibold text-[#1C1C1E]">通知</h3>
                <div className="flex items-center gap-2">
                  {unreadCount > 0 && (
                    <button onClick={markAllAsRead} className="text-[12px] font-medium text-[#007AFF]">
                      全部已读
                    </button>
                  )}
                  <button onClick={() => setShowNotifications(false)} className="p-1 text-[#8E8E93] hover:text-[#3A3A3C] hover:bg-black/5 rounded-xl transition-colors">
                    <X className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
              <div className="max-h-80 overflow-y-auto">
                {notifications.length === 0 ? (
                  <div className="py-10 text-center">
                    <Bell className="w-8 h-8 text-[#E5E5EA] mx-auto mb-2" />
                    <p className="text-[13px] text-[#8E8E93]">暂无通知</p>
                  </div>
                ) : (
                  notifications.slice(0, 5).map((notification) => (
                    <div
                      key={notification.id}
                      onClick={() => handleNotificationClick(notification.id, notification.read)}
                      className={cn(
                        'px-4 py-3 border-b border-[#E5E5EA] hover:bg-black/[0.03] transition-colors cursor-pointer',
                        !notification.read && 'bg-[#007AFF]/[0.04]'
                      )}
                    >
                      <div className="flex gap-2.5">
                        <span className="text-base flex-shrink-0 mt-0.5">{getNotificationIcon(notification.type)}</span>
                        <div className="flex-1 min-w-0">
                          <p className="text-[13px] font-medium text-[#1C1C1E]">{notification.title}</p>
                          <p className="text-[12px] text-[#8E8E93] truncate mt-0.5">{notification.message}</p>
                          <span className="text-[11px] text-[#8E8E93] mt-1">{formatTime(notification.createdAt)}</span>
                        </div>
                        {!notification.read && (
                          <span className="w-1.5 h-1.5 rounded-full mt-2 flex-shrink-0 animate-pulse bg-[#007AFF]" />
                        )}
                      </div>
                    </div>
                  ))
                )}
              </div>
              <div className="px-3 py-2.5 bg-[#F2F2F7]/60 border-t border-[#E5E5EA]">
                <button
                  onClick={() => { setShowNotifications(false); router.push('/notifications'); }}
                  className="w-full text-[12px] font-medium py-1 rounded-md text-[#007AFF] hover:bg-black/5 transition-colors"
                >
                  查看全部
                </button>
              </div>
            </div>
          )}
        </div>

        {/* 用户菜单 */}
        <div className="relative">
          <button
            onClick={() => setShowUserMenu(!showUserMenu)}
            className="flex items-center gap-2 pl-2 pr-1.5 py-1 rounded-xl hover:bg-black/5 transition-colors"
          >
            <div className="text-right hidden sm:block">
              <p className="text-[13px] font-medium text-[#1C1C1E] leading-tight flex items-center gap-1.5">
                {currentUser?.username || '未登录'}
                {currentUser?.company && (
                  <span className="inline-flex items-center px-1.5 py-px rounded text-[9px] font-bold tracking-wide uppercase bg-[#007AFF]/[0.1] text-[#007AFF]">
                    {currentUser.company}
                  </span>
                )}
              </p>
              <p className="text-[11px] text-[#8E8E93] flex items-center gap-1 mt-0.5">
                {currentUser?.role === 'admin' && <Shield className="w-3 h-3" />}
                {currentUser?.role === 'admin' ? '管理员' : '普通用户'}
              </p>
            </div>
            <div className="relative">
              <div className={cn(
                "w-8 h-8 rounded-full flex items-center justify-center shadow-sm",
                currentUser?.role === 'admin' ? "bg-[#007AFF]" : "bg-[#8E8E93]"
              )}>
                <User className="w-4 h-4 text-[#1C1C1E]" />
              </div>
              <div className="absolute -bottom-0.5 -right-0.5 w-2.5 h-2.5 bg-[#34C759] rounded-full border-2 border-white" />
            </div>
            <ChevronDown className={cn("w-3.5 h-3.5 text-[#8E8E93] transition-transform", showUserMenu && 'rotate-180')} />
          </button>

          {showUserMenu && (
            <div className="absolute right-0 top-11 w-56 bg-white rounded-2xl shadow-[0_6px_24px_rgba(0,0,0,0.12)] border border-[#E5E5EA] overflow-hidden animate-in fade-in slide-in-from-top-2 duration-200">
              <div className="px-4 py-3 border-b border-[#E5E5EA] bg-[#F2F2F7]/60">
                <div className="flex items-center gap-3">
                  <div className="w-9 h-9 rounded-full flex items-center justify-center shadow-sm bg-[#007AFF]">
                    <User className="w-5 h-5 text-[#1C1C1E]" />
                  </div>
                  <div>
                    <p className="text-[13px] font-semibold text-[#1C1C1E]">{currentUser?.username}</p>
                    <p className="text-[11px] text-[#8E8E93]">{currentUser?.email}</p>
                  </div>
                </div>
                {currentUser?.role === 'admin' && (
                  <span className="inline-flex items-center gap-1 px-1.5 py-0.5 mt-2 text-[10px] font-bold rounded-md bg-[#007AFF]/[0.1] text-[#007AFF]">
                    <Shield className="w-3 h-3" />
                    管理员
                  </span>
                )}
              </div>
              
              <div className="p-1.5">
                {isAdminOrAbove(currentUser?.role) && (
                  <button
                    onClick={() => { setShowUserMenu(false); router.push('/user-settings'); }}
                    className="w-full flex items-center gap-2.5 px-3 py-2 rounded-xl hover:bg-black/5 transition-colors text-left"
                  >
                    <UserCog className="w-4 h-4 text-[#8E8E93]" />
                    <span className="text-[13px] text-[#3A3A3C]">用户管理</span>
                  </button>
                )}
                <button
                  onClick={() => { setShowUserMenu(false); router.push('/settings'); }}
                  className="w-full flex items-center gap-2.5 px-3 py-2 rounded-xl hover:bg-black/5 transition-colors text-left"
                >
                  <Settings className="w-4 h-4 text-[#8E8E93]" />
                  <span className="text-[13px] text-[#3A3A3C]">账户设置</span>
                </button>
              </div>

              <div className="p-1.5 border-t border-[#E5E5EA]">
                <button
                  onClick={() => { setShowUserMenu(false); onLogout?.(); }}
                  className="w-full flex items-center gap-2.5 px-3 py-2 rounded-xl hover:bg-[#FF3B30]/[0.08] transition-colors text-left group"
                >
                  <LogOut className="w-4 h-4 text-[#8E8E93] group-hover:text-[#FF3B30]" />
                  <span className="text-[13px] text-[#3A3A3C] group-hover:text-[#FF3B30]">退出登录</span>
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
