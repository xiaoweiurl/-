'use client';

import React from 'react';
import { useRouter } from 'next/navigation';
import { 
  User, Plus, Search, MoreVertical, Edit2, Trash2, 
  Shield, Loader2, X, Save
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Toaster } from '@/components/ui/sonner';
import { toast } from 'sonner';
import { cn } from '@/lib/utils';
import { isAdminOrAbove, isSuperAdmin, canResetPasswordOf, roleDisplayName } from '@/lib/auth';

type RoleType = 'admin' | 'user' | 'superadmin';

interface UserInfo {
  id: string;
  username: string;
  email: string;
  role: RoleType;
  avatar?: string;
  nickname?: string;
  bio?: string;
  phone?: string;
  createdAt: string;
  lastLoginAt?: string;
}

interface CurrentUser {
  id: string;
  username: string;
  email: string;
  role: RoleType;
  nickname?: string;
}

export default function UsersPage() {
  const router = useRouter();
  const [isLoading, setIsLoading] = React.useState(true);
  const [currentUser, setCurrentUser] = React.useState<CurrentUser | null>(null);
  const [users, setUsers] = React.useState<UserInfo[]>([]);
  const [searchQuery, setSearchQuery] = React.useState('');
  const [showAddModal, setShowAddModal] = React.useState(false);
  const [showEditModal, setShowEditModal] = React.useState(false);
  const [selectedUser, setSelectedUser] = React.useState<UserInfo | null>(null);
  const [isSaving, setIsSaving] = React.useState(false);

  // 新用户表单
  const [newUserForm, setNewUserForm] = React.useState({
    username: '',
    password: '',
    email: '',
    role: 'user' as RoleType,
    nickname: '',
    phone: '',
  });

  // 编辑用户表单
  const [editForm, setEditForm] = React.useState({
    email: '',
    role: 'user' as RoleType,
    nickname: '',
    phone: '',
    password: '',
  });

  // 检查权限并获取用户列表
  React.useEffect(() => {
    const fetchData = async () => {
      try {
        // 检查登录状态
        const authRes = await fetch('/api/auth/login', { credentials: 'include' });
        const authData = await authRes.json();
        
        if (!authRes.ok || !authData.success) {
          router.push('/login');
          return;
        }
        
        // 检查是否为管理员及以上（admin / superadmin）
        if (!isAdminOrAbove(authData.data.role)) {
          toast.error('权限不足');
          router.push('/');
          return;
        }
        
        setCurrentUser(authData.data);
        
        // 获取用户列表
        const usersRes = await fetch('/api/admin/users', { credentials: 'include' });
        const usersData = await usersRes.json();
        
        if (usersData.success) {
          setUsers(usersData.data);
        }
      } catch (error) {
        console.error('获取数据失败:', error);
        router.push('/login');
      } finally {
        setIsLoading(false);
      }
    };
    
    fetchData();
  }, []);

  // 筛选用户
  const filteredUsers = React.useMemo(() => {
    const query = searchQuery.toLowerCase().trim();
    if (!query) return users;
    
    return users.filter(user => 
      user.username.toLowerCase().includes(query) ||
      user.email.toLowerCase().includes(query) ||
      user.nickname?.toLowerCase().includes(query) ||
      user.phone?.includes(query)
    );
  }, [users, searchQuery]);

  // 创建新用户
  const handleCreateUser = async () => {
    if (!newUserForm.username || !newUserForm.password || !newUserForm.email) {
      toast.error('用户名、密码和邮箱为必填项');
      return;
    }

    setIsSaving(true);
    try {
      const res = await fetch('/api/admin/users', {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(newUserForm),
      });
      const data = await res.json();
      
      if (data.success) {
        toast.success('用户创建成功');
        setUsers(prev => [...prev, data.data]);
        setShowAddModal(false);
        setNewUserForm({
          username: '',
          password: '',
          email: '',
          role: 'user',
          nickname: '',
          phone: '',
        });
      } else {
        toast.error(data.error || '创建失败');
      }
    } catch {
      toast.error('创建失败');
    } finally {
      setIsSaving(false);
    }
  };

  // 打开编辑弹窗
  const openEditModal = (user: UserInfo) => {
    setSelectedUser(user);
    setEditForm({
      email: user.email,
      role: (user.role?.toLowerCase() || 'user') as RoleType,
      nickname: user.nickname || '',
      phone: user.phone || '',
      password: '',
    });
    setShowEditModal(true);
  };

  // 更新用户
  const handleUpdateUser = async () => {
    if (!selectedUser) return;
    
    setIsSaving(true);
    try {
      const updates: Record<string, unknown> = {
        email: editForm.email,
        role: editForm.role,
        nickname: editForm.nickname,
        phone: editForm.phone,
      };
      
      if (editForm.password) {
        updates.password = editForm.password;
      }
      
      const res = await fetch(`/api/admin/users/${selectedUser.id}`, {
        method: 'PUT',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(updates),
      });
      const data = await res.json();
      
      if (data.success) {
        toast.success('用户更新成功');
        setUsers(prev => prev.map(u => u.id === selectedUser.id ? { ...u, ...data.data } : u));
        setShowEditModal(false);
        setSelectedUser(null);
      } else {
        toast.error(data.error || '更新失败');
      }
    } catch {
      toast.error('更新失败');
    } finally {
      setIsSaving(false);
    }
  };

  // 删除用户
  const handleDeleteUser = async (user: UserInfo) => {
    if (user.id === currentUser?.id) {
      toast.error('不能删除自己的账号');
      return;
    }
    
    if (!confirm(`确定要删除用户 "${user.nickname || user.username}" 吗？`)) {
      return;
    }
    
    try {
      const res = await fetch(`/api/admin/users/${user.id}`, {
        method: 'DELETE',
        credentials: 'include',
      });
      const data = await res.json();
      
      if (data.success) {
        toast.success('用户已删除');
        setUsers(prev => prev.filter(u => u.id !== user.id));
      } else {
        toast.error(data.error || '删除失败');
      }
    } catch {
      toast.error('删除失败');
    }
  };

  // 返回主页
  const handleBack = () => {
    router.push('/');
  };

  // 角色徽章样式（三级）
  const roleBadgeClass = (role: RoleType) => {
    if (role === 'superadmin') return 'bg-[rgba(175,82,222,0.1)] text-[#AF52DE]';
    if (role === 'admin') return 'bg-[rgba(0,122,255,0.1)] text-[#007aff]';
    return 'bg-[rgba(118,118,128,0.08)] text-[#8e8e93]';
  };

  // 角色头像底色（三级）
  const roleAvatarClass = (role: RoleType) => {
    if (role === 'superadmin') return 'bg-[#AF52DE]';
    if (role === 'admin') return 'bg-[#007AFF]';
    return 'bg-[#8E8E93]';
  };

  // 是否可编辑目标用户（管理员不能编辑其他管理员/超级管理员，超级管理员不受限）
  const canEditTarget = (target: UserInfo) => {
    if (!currentUser) return false;
    if (isSuperAdmin(currentUser.role)) return true;
    if (target.id === currentUser.id) return true;
    return !isAdminOrAbove(target.role);
  };

  // 是否可删除目标用户
  const canDeleteTarget = (target: UserInfo) => {
    if (!currentUser) return false;
    if (target.id === currentUser.id) return false; // 不能删除自己
    if (isSuperAdmin(currentUser.role)) return target.role !== 'superadmin'; // 超管不能删超管
    return !isAdminOrAbove(target.role);
  };

  if (isLoading) {
    return (
      <div className="min-h-screen bg-[#f2f2f7] flex items-center justify-center">
        <Loader2 className="w-8 h-8 text-[#007aff] animate-spin" />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#f2f2f7]">
      <Toaster position="top-center" richColors closeButton />
      
      {/* 顶部导航 */}
      <header className="bg-white border-b border-[#e5e5ea] sticky top-0 z-10">
        <div className="max-w-6xl mx-auto px-6 h-16 flex items-center justify-between">
          <div className="flex items-center gap-4">
            <button 
              onClick={handleBack}
              className="text-[#8e8e93] hover:text-[#8e8e93] transition-colors"
            >
              ← 返回
            </button>
            <h1 className="text-xl font-semibold text-[#8e8e93]">用户管理</h1>
          </div>
          <div className="flex items-center gap-4">
            <Button
              onClick={() => setShowAddModal(true)}
              className="bg-[#007aff] hover:bg-[#007aff] gap-2"
            >
              <Plus className="w-4 h-4" />
              添加用户
            </Button>
          </div>
        </div>
      </header>

      <main className="max-w-6xl mx-auto px-6 py-8">
        {/* 搜索栏 */}
        <div className="mb-6">
          <div className="relative max-w-md">
            <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-[#8e8e93]" />
            <Input
              type="text"
              placeholder="搜索用户名、邮箱、昵称..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="pl-12"
            />
          </div>
        </div>

        {/* 用户列表 */}
        <div className="bg-white rounded-2xl border border-[#e5e5ea] overflow-hidden">
          <table className="w-full">
            <thead className="bg-[#f2f2f7] border-b border-[#e5e5ea]">
              <tr>
                <th className="text-left px-6 py-4 text-sm font-medium text-[#8e8e93]">用户</th>
                <th className="text-left px-6 py-4 text-sm font-medium text-[#8e8e93]">邮箱</th>
                <th className="text-left px-6 py-4 text-sm font-medium text-[#8e8e93]">角色</th>
                <th className="text-left px-6 py-4 text-sm font-medium text-[#8e8e93]">注册时间</th>
                <th className="text-left px-6 py-4 text-sm font-medium text-[#8e8e93]">最后登录</th>
                <th className="text-right px-6 py-4 text-sm font-medium text-[#8e8e93]">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#e5e5ea]">
              {filteredUsers.map((user) => (
                <tr key={user.id} className="hover:bg-[#f2f2f7] transition-colors">
                  <td className="px-6 py-4">
                    <div className="flex items-center gap-3">
                      <div className={cn(
                        "w-10 h-10 rounded-xl flex items-center justify-center text-white font-medium",
                        roleAvatarClass(user.role)
                      )}>
                        {(user.nickname || user.username)?.[0]?.toUpperCase()}
                      </div>
                      <div>
                        <p className="font-medium text-[#8e8e93]">{user.nickname || user.username}</p>
                        <p className="text-sm text-[#8e8e93]">@{user.username}</p>
                      </div>
                    </div>
                  </td>
                  <td className="px-6 py-4 text-[#8e8e93]">{user.email}</td>
                  <td className="px-6 py-4">
                    <span className={cn(
                      "inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium",
                      roleBadgeClass(user.role)
                    )}>
                      {isAdminOrAbove(user.role) && <Shield className="w-3 h-3" />}
                      {roleDisplayName(user.role)}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-[#8e8e93]">{user.createdAt}</td>
                  <td className="px-6 py-4 text-[#8e8e93]">
                    {user.lastLoginAt ? new Date(user.lastLoginAt).toLocaleDateString() : '-'}
                  </td>
                  <td className="px-6 py-4">
                    <div className="flex items-center justify-end gap-2">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => openEditModal(user)}
                        disabled={!canEditTarget(user)}
                        title={!canEditTarget(user) ? '管理员不能修改其他管理员的账号' : ''}
                        className="text-[#8e8e93] hover:text-[#007aff] disabled:opacity-30"
                      >
                        <Edit2 className="w-4 h-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => handleDeleteUser(user)}
                        disabled={!canDeleteTarget(user)}
                        className="text-[#8e8e93] hover:text-[#ff3b30] disabled:opacity-30"
                      >
                        <Trash2 className="w-4 h-4" />
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {filteredUsers.length === 0 && (
            <div className="text-center py-12">
              <User className="w-12 h-12 text-[#3a3a3c] mx-auto mb-4" />
              <p className="text-[#8e8e93]">暂无用户数据</p>
            </div>
          )}
        </div>

        {/* 统计信息 */}
        <div className="mt-6 grid grid-cols-2 md:grid-cols-4 gap-4">
          <div className="bg-white rounded-xl border border-[#e5e5ea] p-4">
            <p className="text-sm text-[#8e8e93]">总用户数</p>
            <p className="text-2xl font-semibold text-[#1c1c1e]">{users.length}</p>
          </div>
          <div className="bg-white rounded-xl border border-[#e5e5ea] p-4">
            <p className="text-sm text-[#8e8e93]">超级管理员</p>
            <p className="text-2xl font-semibold text-[#AF52DE]">
              {users.filter(u => u.role === 'superadmin').length}
            </p>
          </div>
          <div className="bg-white rounded-xl border border-[#e5e5ea] p-4">
            <p className="text-sm text-[#8e8e93]">管理员</p>
            <p className="text-2xl font-semibold text-[#007aff]">
              {users.filter(u => u.role === 'admin').length}
            </p>
          </div>
          <div className="bg-white rounded-xl border border-[#e5e5ea] p-4">
            <p className="text-sm text-[#8e8e93]">普通用户</p>
            <p className="text-2xl font-semibold text-[#8e8e93]">
              {users.filter(u => u.role === 'user').length}
            </p>
          </div>
        </div>
      </main>

      {/* 添加用户弹窗 */}
      {showAddModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/10" onClick={() => setShowAddModal(false)} />
          <div className="relative bg-white rounded-2xl shadow-2xl w-full max-w-md mx-4 overflow-hidden animate-in fade-in zoom-in-95 duration-200">
            <div className="flex items-center justify-between px-6 py-4 border-b border-[#e5e5ea]">
              <h3 className="text-lg font-semibold text-[#8e8e93]">添加新用户</h3>
              <button onClick={() => setShowAddModal(false)} className="text-[#8e8e93] hover:text-[#8e8e93]">
                <X className="w-5 h-5" />
              </button>
            </div>
            
            <div className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-[#8e8e93] mb-2">用户名 *</label>
                <Input
                  value={newUserForm.username}
                  onChange={(e) => setNewUserForm(prev => ({ ...prev, username: e.target.value }))}
                  placeholder="输入用户名"
                />
              </div>
              
              <div>
                <label className="block text-sm font-medium text-[#8e8e93] mb-2">密码 *</label>
                <Input
                  type="password"
                  value={newUserForm.password}
                  onChange={(e) => setNewUserForm(prev => ({ ...prev, password: e.target.value }))}
                  placeholder="输入密码（至少6位）"
                />
              </div>
              
              <div>
                <label className="block text-sm font-medium text-[#8e8e93] mb-2">邮箱 *</label>
                <Input
                  type="email"
                  value={newUserForm.email}
                  onChange={(e) => setNewUserForm(prev => ({ ...prev, email: e.target.value }))}
                  placeholder="输入邮箱"
                />
              </div>
              
              <div>
                <label className="block text-sm font-medium text-[#8e8e93] mb-2">昵称</label>
                <Input
                  value={newUserForm.nickname}
                  onChange={(e) => setNewUserForm(prev => ({ ...prev, nickname: e.target.value }))}
                  placeholder="输入昵称（可选）"
                />
              </div>
              
              <div>
                <label className="block text-sm font-medium text-[#8e8e93] mb-2">手机号</label>
                <Input
                  value={newUserForm.phone}
                  onChange={(e) => setNewUserForm(prev => ({ ...prev, phone: e.target.value }))}
                  placeholder="输入手机号（可选）"
                />
              </div>
              
              <div>
                <label className="block text-sm font-medium text-[#8e8e93] mb-2">角色</label>
                <div className="flex gap-3">
                  <button
                    onClick={() => setNewUserForm(prev => ({ ...prev, role: 'user' }))}
                    className={cn(
                      "flex-1 py-2 rounded-lg border-2 transition-colors",
                      newUserForm.role === 'user'
                        ? "border-[#007aff] bg-[rgba(0,122,255,0.1)] text-[#007aff]"
                        : "border-[#e5e5ea] text-[#8e8e93]"
                    )}
                  >
                    普通用户
                  </button>
                  <button
                    onClick={() => setNewUserForm(prev => ({ ...prev, role: 'admin' }))}
                    className={cn(
                      "flex-1 py-2 rounded-lg border-2 transition-colors",
                      newUserForm.role === 'admin'
                        ? "border-[#007aff] bg-[rgba(0,122,255,0.1)] text-[#007aff]"
                        : "border-[#e5e5ea] text-[#8e8e93]"
                    )}
                  >
                    管理员
                  </button>
                  {isSuperAdmin(currentUser?.role) && (
                    <button
                      onClick={() => setNewUserForm(prev => ({ ...prev, role: 'superadmin' }))}
                      className={cn(
                        "flex-1 py-2 rounded-lg border-2 transition-colors",
                        newUserForm.role === 'superadmin'
                          ? "border-[#AF52DE] bg-[rgba(175,82,222,0.1)] text-[#AF52DE]"
                          : "border-[#e5e5ea] text-[#8e8e93]"
                      )}
                    >
                      超级管理员
                    </button>
                  )}
                </div>
              </div>
            </div>
            
            <div className="flex gap-3 px-6 py-4 bg-[#f2f2f7] border-t border-[#e5e5ea]">
              <Button variant="outline" onClick={() => setShowAddModal(false)} className="flex-1">
                取消
              </Button>
              <Button
                onClick={handleCreateUser}
                disabled={isSaving}
                className="flex-1 bg-[#007aff] hover:bg-[#007aff]"
              >
                {isSaving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4 mr-2" />}
                创建用户
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* 编辑用户弹窗 */}
      {showEditModal && selectedUser && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/10" onClick={() => setShowEditModal(false)} />
          <div className="relative bg-white rounded-2xl shadow-2xl w-full max-w-md mx-4 overflow-hidden animate-in fade-in zoom-in-95 duration-200">
            <div className="flex items-center justify-between px-6 py-4 border-b border-[#e5e5ea]">
              <h3 className="text-lg font-semibold text-[#8e8e93]">编辑用户</h3>
              <button onClick={() => setShowEditModal(false)} className="text-[#8e8e93] hover:text-[#8e8e93]">
                <X className="w-5 h-5" />
              </button>
            </div>
            
            <div className="p-6 space-y-4">
              <div className="flex items-center gap-3 p-3 bg-[#f2f2f7] rounded-xl">
                <div className={cn(
                  "w-12 h-12 rounded-xl flex items-center justify-center text-white font-medium",
                  roleAvatarClass(selectedUser.role)
                )}>
                  {(selectedUser.nickname || selectedUser.username)?.[0]?.toUpperCase()}
                </div>
                <div>
                  <p className="font-medium text-[#8e8e93]">{selectedUser.nickname || selectedUser.username}</p>
                  <p className="text-sm text-[#8e8e93]">@{selectedUser.username}</p>
                </div>
              </div>
              
              <div>
                <label className="block text-sm font-medium text-[#8e8e93] mb-2">邮箱</label>
                <Input
                  type="email"
                  value={editForm.email}
                  onChange={(e) => setEditForm(prev => ({ ...prev, email: e.target.value }))}
                />
              </div>
              
              <div>
                <label className="block text-sm font-medium text-[#8e8e93] mb-2">昵称</label>
                <Input
                  value={editForm.nickname}
                  onChange={(e) => setEditForm(prev => ({ ...prev, nickname: e.target.value }))}
                />
              </div>
              
              <div>
                <label className="block text-sm font-medium text-[#8e8e93] mb-2">手机号</label>
                <Input
                  value={editForm.phone}
                  onChange={(e) => setEditForm(prev => ({ ...prev, phone: e.target.value }))}
                />
              </div>
              
              <div>
                <label className="block text-sm font-medium text-[#8e8e93] mb-2">新密码</label>
                <Input
                  type="password"
                  value={editForm.password}
                  onChange={(e) => setEditForm(prev => ({ ...prev, password: e.target.value }))}
                  placeholder={
                    canResetPasswordOf(currentUser?.role, currentUser?.id || '', selectedUser.role, selectedUser.id)
                      ? '留空则不修改密码'
                      : '管理员不能修改其他管理员的密码'
                  }
                  disabled={!canResetPasswordOf(currentUser?.role, currentUser?.id || '', selectedUser.role, selectedUser.id)}
                />
                {!canResetPasswordOf(currentUser?.role, currentUser?.id || '', selectedUser.role, selectedUser.id) && (
                  <p className="mt-1.5 text-xs text-[#FF9500]">仅超级管理员可重置其他管理员的密码</p>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-[#8e8e93] mb-2">角色</label>
                <div className="flex gap-3">
                  <button
                    onClick={() => setEditForm(prev => ({ ...prev, role: 'user' }))}
                    disabled={selectedUser.id === currentUser?.id}
                    className={cn(
                      "flex-1 py-2 rounded-lg border-2 transition-colors disabled:opacity-50",
                      editForm.role === 'user'
                        ? "border-[#007aff] bg-[rgba(0,122,255,0.1)] text-[#007aff]"
                        : "border-[#e5e5ea] text-[#8e8e93]"
                    )}
                  >
                    普通用户
                  </button>
                  <button
                    onClick={() => setEditForm(prev => ({ ...prev, role: 'admin' }))}
                    disabled={selectedUser.id === currentUser?.id}
                    className={cn(
                      "flex-1 py-2 rounded-lg border-2 transition-colors disabled:opacity-50",
                      editForm.role === 'admin'
                        ? "border-[#007aff] bg-[rgba(0,122,255,0.1)] text-[#007aff]"
                        : "border-[#e5e5ea] text-[#8e8e93]"
                    )}
                  >
                    管理员
                  </button>
                  {isSuperAdmin(currentUser?.role) && (
                    <button
                      onClick={() => setEditForm(prev => ({ ...prev, role: 'superadmin' }))}
                      disabled={selectedUser.id === currentUser?.id}
                      className={cn(
                        "flex-1 py-2 rounded-lg border-2 transition-colors disabled:opacity-50",
                        editForm.role === 'superadmin'
                          ? "border-[#AF52DE] bg-[rgba(175,82,222,0.1)] text-[#AF52DE]"
                          : "border-[#e5e5ea] text-[#8e8e93]"
                      )}
                    >
                      超级管理员
                    </button>
                  )}
                </div>
              </div>
            </div>
            
            <div className="flex gap-3 px-6 py-4 bg-[#f2f2f7] border-t border-[#e5e5ea]">
              <Button variant="outline" onClick={() => setShowEditModal(false)} className="flex-1">
                取消
              </Button>
              <Button
                onClick={handleUpdateUser}
                disabled={isSaving}
                className="flex-1 bg-[#007aff] hover:bg-[#007aff]"
              >
                {isSaving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4 mr-2" />}
                保存修改
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
