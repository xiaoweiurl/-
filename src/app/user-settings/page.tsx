'use client';

import React from 'react';
import { useRouter } from 'next/navigation';
import Image from 'next/image';
import { backendFetch } from '@/lib/backend-proxy';
import {
  User,
  Plus,
  Pencil,
  Trash2,
  KeyRound,
  ArrowLeft,
  Loader2,
  Search,
  Shield,
  Crown,
  UserCircle,
  MoreVertical,
  Mail,
  Phone,
  Calendar,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Toaster } from '@/components/ui/sonner';
import { toast } from 'sonner';
import { cn } from '@/lib/utils';

interface UserInfo {
  id: string;
  username: string;
  email: string;
  avatar?: string;
  nickname?: string;
  phone?: string;
  bio?: string;
  role: string;
  membership: string;
  createdAt: string;
  lastLoginAt?: string;
}

interface FormData {
  username: string;
  email: string;
  password: string;
  nickname: string;
  phone: string;
  role: string;
  membership: string;
}

const initialFormData: FormData = {
  username: '',
  email: '',
  password: '',
  nickname: '',
  phone: '',
  role: 'user',
  membership: 'free',
};

export default function UserManagementPage() {
  const router = useRouter();
  const [isLoading, setIsLoading] = React.useState(true);
  const [accessDenied, setAccessDenied] = React.useState(false);
  const [users, setUsers] = React.useState<UserInfo[]>([]);
  const [searchQuery, setSearchQuery] = React.useState('');
  const [showAddModal, setShowAddModal] = React.useState(false);
  const [showEditModal, setShowEditModal] = React.useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = React.useState(false);
  const [showResetPasswordModal, setShowResetPasswordModal] = React.useState(false);
  const [selectedUser, setSelectedUser] = React.useState<UserInfo | null>(null);
  const [formData, setFormData] = React.useState<FormData>(initialFormData);
  const [newPassword, setNewPassword] = React.useState('');
  const [isSaving, setIsSaving] = React.useState(false);

  // 权限检查：仅管理员可访问
  React.useEffect(() => {
    const checkAdmin = async () => {
      try {
        const sessionId = localStorage.getItem('session_id');
        if (!sessionId) { router.push('/login'); return; }
        const res = await backendFetch('/auth/session', { headers: { 'X-Session-Id': sessionId } });
        const result = await res.json();
        if (result.code === 200 && result.data?.role === 'admin') {
          setAccessDenied(false);
        } else {
          setAccessDenied(true);
        }
      } catch {
        setAccessDenied(true);
      }
    };
    checkAdmin();
  }, [router]);

  // 获取用户列表
  const fetchUsers = React.useCallback(async () => {
    setIsLoading(true);
    try {
      const res = await fetch('/api/admin/users', { credentials: 'include' });
      const data = await res.json();
      
      if (data.success) {
        setUsers(data.data);
      } else {
        toast.error('获取用户列表失败');
      }
    } catch (error) {
      console.error('获取用户列表失败:', error);
      toast.error('获取用户列表失败');
    } finally {
      setIsLoading(false);
    }
  }, []);

  React.useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  // 过滤用户
  const filteredUsers = users.filter(
    (user) =>
      user.username.toLowerCase().includes(searchQuery.toLowerCase()) ||
      user.email.toLowerCase().includes(searchQuery.toLowerCase()) ||
      (user.nickname && user.nickname.toLowerCase().includes(searchQuery.toLowerCase()))
  );

  // 创建用户
  const handleCreateUser = async () => {
    if (!formData.username || !formData.email || !formData.password) {
      toast.error('请填写必填字段');
      return;
    }

    if (formData.password.length < 6) {
      toast.error('密码长度至少6位');
      return;
    }

    setIsSaving(true);
    try {
      const res = await fetch('/api/admin/users', {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(formData),
      });
      const data = await res.json();

      if (data.success) {
        toast.success('用户创建成功');
        setShowAddModal(false);
        setFormData(initialFormData);
        fetchUsers();
      } else {
        toast.error(data.error || '创建失败');
      }
    } catch (error) {
      toast.error('创建失败');
    } finally {
      setIsSaving(false);
    }
  };

  // 更新用户
  const handleUpdateUser = async () => {
    if (!selectedUser) return;

    setIsSaving(true);
    try {
      const res = await fetch(`/api/admin/users/${selectedUser.id}`, {
        method: 'PUT',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          nickname: formData.nickname,
          email: formData.email,
          phone: formData.phone,
          role: formData.role,
          membership: formData.membership,
        }),
      });
      const data = await res.json();

      if (data.success) {
        toast.success('用户更新成功');
        setShowEditModal(false);
        setSelectedUser(null);
        fetchUsers();
      } else {
        toast.error(data.error || '更新失败');
      }
    } catch (error) {
      toast.error('更新失败');
    } finally {
      setIsSaving(false);
    }
  };

  // 删除用户
  const handleDeleteUser = async () => {
    if (!selectedUser) return;

    setIsSaving(true);
    try {
      const res = await fetch(`/api/admin/users/${selectedUser.id}`, {
        method: 'DELETE',
        credentials: 'include',
      });
      const data = await res.json();

      if (data.success) {
        toast.success('用户删除成功');
        setShowDeleteConfirm(false);
        setSelectedUser(null);
        fetchUsers();
      } else {
        toast.error(data.error || '删除失败');
      }
    } catch (error) {
      toast.error('删除失败');
    } finally {
      setIsSaving(false);
    }
  };

  // 重置密码
  const handleResetPassword = async () => {
    if (!selectedUser) return;

    if (newPassword.length < 6) {
      toast.error('密码长度至少6位');
      return;
    }

    setIsSaving(true);
    try {
      const res = await fetch(`/api/admin/users/${selectedUser.id}/reset-password`, {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ newPassword }),
      });
      const data = await res.json();

      if (data.success) {
        toast.success('密码重置成功');
        setShowResetPasswordModal(false);
        setSelectedUser(null);
        setNewPassword('');
      } else {
        toast.error(data.error || '重置失败');
      }
    } catch (error) {
      toast.error('重置失败');
    } finally {
      setIsSaving(false);
    }
  };

  // 打开编辑模态框
  const openEditModal = (user: UserInfo) => {
    setSelectedUser(user);
    setFormData({
      username: user.username,
      email: user.email,
      password: '',
      nickname: user.nickname || '',
      phone: user.phone || '',
      role: user.role,
      membership: user.membership,
    });
    setShowEditModal(true);
  };

  // 获取角色显示
  const getRoleBadge = (role: string) => {
    if (role === 'admin') {
      return (
        <span className="inline-flex items-center gap-1 px-2 py-1 rounded-full text-xs font-medium bg-violet-100 text-violet-700">
          <Shield className="w-3 h-3" />
          管理员
        </span>
      );
    }
    return (
      <span className="inline-flex items-center gap-1 px-2 py-1 rounded-full text-xs font-medium bg-slate-100 text-slate-600">
        <UserCircle className="w-3 h-3" />
        普通用户
      </span>
    );
  };

  // 获取会员显示
  const getMembershipBadge = (membership: string) => {
    const styles: Record<string, string> = {
      premium: 'bg-gradient-to-r from-amber-400 to-orange-500 text-white',
      pro: 'bg-gradient-to-r from-blue-400 to-indigo-500 text-white',
      free: 'bg-slate-100 text-slate-600',
    };
    const labels: Record<string, string> = {
      premium: '高级会员',
      pro: '专业会员',
      free: '免费用户',
    };
    return (
      <span className={cn('px-2 py-1 rounded-full text-xs font-medium', styles[membership] || styles.free)}>
        {membership === 'premium' && <Crown className="w-3 h-3 inline mr-1" />}
        {labels[membership] || labels.free}
      </span>
    );
  };

  return (
    <div className="min-h-screen bg-slate-50">
      <Toaster position="top-center" richColors closeButton />

      {/* 权限拦截 */}
      {accessDenied && (
        <div className="flex flex-col items-center justify-center min-h-screen gap-4">
          <Shield className="w-16 h-16 text-slate-300" />
          <h2 className="text-xl font-semibold text-slate-600">无访问权限</h2>
          <p className="text-slate-400">仅管理员可访问此页面</p>
          <button
            onClick={() => router.push('/')}
            className="px-4 py-2 bg-violet-600 text-white rounded-lg hover:bg-violet-700 transition-colors"
          >
            返回首页
          </button>
        </div>
      )}
      {!accessDenied && (
      <>

      {/* 顶部导航 */}
      <header className="bg-white border-b border-slate-200 sticky top-0 z-40">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between">
          <div className="flex items-center gap-2 sm:gap-4 min-w-0">
            <button
              onClick={() => router.push('/')}
              className="text-slate-500 hover:text-slate-700 transition-colors flex items-center gap-1 shrink-0"
            >
              <ArrowLeft className="w-4 h-4" />
              <span className="hidden sm:inline">返回</span>
            </button>
            <h1 className="text-lg sm:text-xl font-semibold text-slate-800 truncate">用户管理</h1>
          </div>
          <Button
            onClick={() => {
              setFormData(initialFormData);
              setShowAddModal(true);
            }}
            className="gap-2 bg-gradient-to-r from-violet-500 to-purple-600 hover:from-violet-600 hover:to-purple-700 shrink-0"
          >
            <Plus className="w-4 h-4" />
            <span className="hidden sm:inline">新增用户</span>
            <span className="sm:hidden">新增</span>
          </Button>
        </div>
      </header>

      <div className="max-w-7xl mx-auto px-4 sm:px-6 py-6 sm:py-8">
        {/* 搜索栏 */}
        <div className="mb-6">
          <div className="relative max-w-md">
            <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
            <Input
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="搜索用户名、邮箱或昵称..."
              className="pl-11"
            />
          </div>
        </div>

        {/* 用户列表 */}
        {isLoading ? (
          <div className="flex items-center justify-center py-20">
            <Loader2 className="w-8 h-8 text-violet-500 animate-spin" />
          </div>
        ) : filteredUsers.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20">
            <div className="w-20 h-20 rounded-full bg-slate-100 flex items-center justify-center mb-4">
              <User className="w-10 h-10 text-slate-300" />
            </div>
            <h3 className="text-lg font-medium text-slate-700 mb-2">暂无用户</h3>
            <p className="text-sm text-slate-500">点击上方"新增用户"添加第一个用户</p>
          </div>
        ) : (
          <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
            <div className="overflow-x-auto -mx-px">
              <table className="w-full min-w-[640px]">
              <thead className="bg-slate-50 border-b border-slate-200">
                <tr>
                  <th className="px-4 sm:px-6 py-4 text-left text-sm font-medium text-slate-600">用户</th>
                  <th className="px-4 sm:px-6 py-4 text-left text-sm font-medium text-slate-600">联系信息</th>
                  <th className="px-4 sm:px-6 py-4 text-left text-sm font-medium text-slate-600">角色</th>
                  <th className="px-4 sm:px-6 py-4 text-left text-sm font-medium text-slate-600">会员</th>
                  <th className="px-4 sm:px-6 py-4 text-left text-sm font-medium text-slate-600 hidden sm:table-cell">注册时间</th>
                  <th className="px-4 sm:px-6 py-4 text-right text-sm font-medium text-slate-600">操作</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filteredUsers.map((user) => (
                  <tr key={user.id} className="hover:bg-slate-50 transition-colors">
                    <td className="px-4 sm:px-6 py-4">
                      <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-full bg-gradient-to-br from-violet-500 to-purple-600 flex items-center justify-center text-white font-medium overflow-hidden shrink-0">
                          {user.avatar ? (
                            <Image
                              src={user.avatar}
                              alt={user.username}
                              width={40}
                              height={40}
                              className="w-full h-full object-cover"
                            />
                          ) : (
                            <span>{(user.nickname || user.username)?.[0]?.toUpperCase()}</span>
                          )}
                        </div>
                        <div className="min-w-0">
                          <p className="font-medium text-slate-800 truncate">{user.nickname || user.username}</p>
                          <p className="text-xs text-slate-500 truncate">@{user.username}</p>
                        </div>
                      </div>
                    </td>
                    <td className="px-4 sm:px-6 py-4">
                      <div className="space-y-1">
                        <div className="flex items-center gap-1.5 text-sm text-slate-600">
                          <Mail className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                          <span className="truncate">{user.email}</span>
                        </div>
                        {user.phone && (
                          <div className="flex items-center gap-1.5 text-sm text-slate-500">
                            <Phone className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                            <span className="truncate">{user.phone}</span>
                          </div>
                        )}
                      </div>
                    </td>
                    <td className="px-4 sm:px-6 py-4">{getRoleBadge(user.role)}</td>
                    <td className="px-4 sm:px-6 py-4">{getMembershipBadge(user.membership)}</td>
                    <td className="px-4 sm:px-6 py-4 hidden sm:table-cell">
                      <div className="flex items-center gap-1.5 text-sm text-slate-600">
                        <Calendar className="w-3.5 h-3.5 text-slate-400" />
                        {new Date(user.createdAt).toLocaleDateString('zh-CN')}
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <button className="p-2 hover:bg-slate-100 rounded-lg transition-colors">
                            <MoreVertical className="w-4 h-4 text-slate-500" />
                          </button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end" className="w-40">
                          <DropdownMenuItem onClick={() => openEditModal(user)} className="gap-2">
                            <Pencil className="w-4 h-4" />
                            编辑用户
                          </DropdownMenuItem>
                          <DropdownMenuItem
                            onClick={() => {
                              setSelectedUser(user);
                              setShowResetPasswordModal(true);
                            }}
                            className="gap-2"
                          >
                            <KeyRound className="w-4 h-4" />
                            重置密码
                          </DropdownMenuItem>
                          <DropdownMenuItem
                            onClick={() => {
                              setSelectedUser(user);
                              setShowDeleteConfirm(true);
                            }}
                            className="gap-2 text-red-600 focus:text-red-600 focus:bg-red-50"
                          >
                            <Trash2 className="w-4 h-4" />
                            删除用户
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            </div>
          </div>
        )}
      </div>

      {/* 新增用户模态框 */}
      {showAddModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl w-full max-w-lg p-6 max-h-[90vh] overflow-y-auto">
            <h2 className="text-xl font-semibold text-slate-800 mb-6">新增用户</h2>
            
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-2">
                    用户名 <span className="text-red-500">*</span>
                  </label>
                  <Input
                    value={formData.username}
                    onChange={(e) => setFormData({ ...formData, username: e.target.value })}
                    placeholder="请输入用户名"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-2">
                    邮箱 <span className="text-red-500">*</span>
                  </label>
                  <Input
                    type="email"
                    value={formData.email}
                    onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                    placeholder="请输入邮箱"
                  />
                </div>
              </div>
              
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-2">
                  密码 <span className="text-red-500">*</span>
                </label>
                <Input
                  type="password"
                  value={formData.password}
                  onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                  placeholder="请输入密码（至少6位）"
                />
              </div>
              
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-2">昵称</label>
                  <Input
                    value={formData.nickname}
                    onChange={(e) => setFormData({ ...formData, nickname: e.target.value })}
                    placeholder="请输入昵称"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-2">手机号</label>
                  <Input
                    value={formData.phone}
                    onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
                    placeholder="请输入手机号"
                  />
                </div>
              </div>
              
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-2">角色</label>
                  <select
                    value={formData.role}
                    onChange={(e) => setFormData({ ...formData, role: e.target.value })}
                    className="w-full px-4 py-2.5 rounded-xl border border-slate-200 focus:border-violet-500 focus:ring-2 focus:ring-violet-200 outline-none"
                  >
                    <option value="user">普通用户</option>
                    <option value="admin">管理员</option>
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-2">会员类型</label>
                  <select
                    value={formData.membership}
                    onChange={(e) => setFormData({ ...formData, membership: e.target.value })}
                    className="w-full px-4 py-2.5 rounded-xl border border-slate-200 focus:border-violet-500 focus:ring-2 focus:ring-violet-200 outline-none"
                  >
                    <option value="free">免费用户</option>
                    <option value="pro">专业会员</option>
                    <option value="premium">高级会员</option>
                  </select>
                </div>
              </div>
            </div>
            
            <div className="flex justify-end gap-3 mt-6 pt-6 border-t border-slate-100">
              <Button variant="outline" onClick={() => setShowAddModal(false)}>
                取消
              </Button>
              <Button
                onClick={handleCreateUser}
                disabled={isSaving}
                className="gap-2 bg-gradient-to-r from-violet-500 to-purple-600"
              >
                {isSaving && <Loader2 className="w-4 h-4 animate-spin" />}
                创建用户
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* 编辑用户模态框 */}
      {showEditModal && selectedUser && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl w-full max-w-lg p-6 max-h-[90vh] overflow-y-auto">
            <h2 className="text-xl font-semibold text-slate-800 mb-6">编辑用户</h2>
            
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-2">用户名</label>
                <Input value={formData.username} disabled className="bg-slate-50" />
                <p className="text-xs text-slate-500 mt-1">用户名不可修改</p>
              </div>
              
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-2">邮箱</label>
                  <Input
                    type="email"
                    value={formData.email}
                    onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-2">昵称</label>
                  <Input
                    value={formData.nickname}
                    onChange={(e) => setFormData({ ...formData, nickname: e.target.value })}
                  />
                </div>
              </div>
              
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-2">手机号</label>
                <Input
                  value={formData.phone}
                  onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
                />
              </div>
              
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-2">角色</label>
                  <select
                    value={formData.role}
                    onChange={(e) => setFormData({ ...formData, role: e.target.value })}
                    className="w-full px-4 py-2.5 rounded-xl border border-slate-200 focus:border-violet-500 focus:ring-2 focus:ring-violet-200 outline-none"
                  >
                    <option value="user">普通用户</option>
                    <option value="admin">管理员</option>
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-2">会员类型</label>
                  <select
                    value={formData.membership}
                    onChange={(e) => setFormData({ ...formData, membership: e.target.value })}
                    className="w-full px-4 py-2.5 rounded-xl border border-slate-200 focus:border-violet-500 focus:ring-2 focus:ring-violet-200 outline-none"
                  >
                    <option value="free">免费用户</option>
                    <option value="pro">专业会员</option>
                    <option value="premium">高级会员</option>
                  </select>
                </div>
              </div>
            </div>
            
            <div className="flex justify-end gap-3 mt-6 pt-6 border-t border-slate-100">
              <Button variant="outline" onClick={() => setShowEditModal(false)}>
                取消
              </Button>
              <Button
                onClick={handleUpdateUser}
                disabled={isSaving}
                className="gap-2 bg-gradient-to-r from-violet-500 to-purple-600"
              >
                {isSaving && <Loader2 className="w-4 h-4 animate-spin" />}
                保存修改
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* 删除确认 */}
      {showDeleteConfirm && selectedUser && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl w-full max-w-md p-6">
            <h2 className="text-xl font-semibold text-slate-800 mb-4">确认删除</h2>
            <p className="text-slate-600 mb-6">
              确定要删除用户 <span className="font-medium text-slate-800">"{selectedUser.nickname || selectedUser.username}"</span> 吗？此操作不可恢复。
            </p>
            <div className="flex justify-end gap-3">
              <Button variant="outline" onClick={() => setShowDeleteConfirm(false)}>
                取消
              </Button>
              <Button
                variant="destructive"
                onClick={handleDeleteUser}
                disabled={isSaving}
                className="gap-2"
              >
                {isSaving && <Loader2 className="w-4 h-4 animate-spin" />}
                确认删除
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* 重置密码模态框 */}
      {showResetPasswordModal && selectedUser && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl w-full max-w-md p-6">
            <h2 className="text-xl font-semibold text-slate-800 mb-4">重置密码</h2>
            <p className="text-slate-600 mb-4">
              为用户 <span className="font-medium text-slate-800">"{selectedUser.nickname || selectedUser.username}"</span> 设置新密码
            </p>
            <Input
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              placeholder="请输入新密码（至少6位）"
            />
            <div className="flex justify-end gap-3 mt-6">
              <Button variant="outline" onClick={() => setShowResetPasswordModal(false)}>
                取消
              </Button>
              <Button
                onClick={handleResetPassword}
                disabled={isSaving || newPassword.length < 6}
                className="gap-2 bg-gradient-to-r from-violet-500 to-purple-600"
              >
                {isSaving && <Loader2 className="w-4 h-4 animate-spin" />}
                确认重置
              </Button>
            </div>
          </div>
        </div>
      )}
      </>)}
    </div>
  );
}
