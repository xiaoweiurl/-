'use client';

import React from 'react';
import { useRouter } from 'next/navigation';
import { 
  User, Lock, Bell, Palette, Image, Camera, Loader2, Save, Eye, EyeOff
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Toaster } from '@/components/ui/sonner';
import { toast } from 'sonner';
import { cn } from '@/lib/utils';
import { useSettings, type AppSettings } from '@/contexts/SettingsContext';

interface UserInfo {
  id: string;
  username: string;
  email: string;
  role: 'admin' | 'user';
  avatar?: string;
  avatarUrl?: string;
  nickname?: string;
  bio?: string;
  phone?: string;
  createdAt: string;
  lastLoginAt?: string;
}

type SettingsTab = 'profile' | 'security' | 'notifications' | 'appearance' | 'display';

export default function SettingsPage() {
  const router = useRouter();
  const [isLoading, setIsLoading] = React.useState(true);
  const [isSaving, setIsSaving] = React.useState(false);
  const { settings, updateSetting, isLoading: settingsLoading } = useSettings();
  const [currentUser, setCurrentUser] = React.useState<UserInfo | null>(null);
  const [activeTab, setActiveTab] = React.useState<SettingsTab>('profile');
  
  // 表单状态
  const [profileForm, setProfileForm] = React.useState({
    nickname: '',
    bio: '',
    phone: '',
    email: '',
  });
  const [passwordForm, setPasswordForm] = React.useState({
    oldPassword: '',
    newPassword: '',
    confirmPassword: '',
  });
  const [showPasswords, setShowPasswords] = React.useState({
    old: false,
    new: false,
    confirm: false,
  });
  const [isUploadingAvatar, setIsUploadingAvatar] = React.useState(false);
  const fileInputRef = React.useRef<HTMLInputElement>(null);

  // 检查登录状态并获取数据
  React.useEffect(() => {
    const fetchData = async () => {
      try {
        // 检查登录状态
        const authRes = await fetch('/api/auth/login');
        const authData = await authRes.json();
        
        if (!authRes.ok || !authData.success) {
          router.push('/login');
          return;
        }
        
        setCurrentUser(authData.data);
        setProfileForm({
          nickname: authData.data.nickname || '',
          bio: authData.data.bio || '',
          phone: authData.data.phone || '',
          email: authData.data.email || '',
        });
      } catch (error) {
        console.error('获取数据失败:', error);
        router.push('/login');
      } finally {
        setIsLoading(false);
      }
    };
    
    fetchData();
  }, [router]);

  // 保存个人资料
  const handleSaveProfile = async () => {
    setIsSaving(true);
    try {
      const res = await fetch('/api/user/profile', {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(profileForm),
      });
      const data = await res.json();
      
      if (data.success) {
        toast.success('资料保存成功');
        setCurrentUser(prev => prev ? { ...prev, ...data.data } : null);
      } else {
        toast.error(data.error || '保存失败');
      }
    } catch {
      toast.error('保存失败');
    } finally {
      setIsSaving(false);
    }
  };

  // 修改密码
  const handleChangePassword = async () => {
    if (passwordForm.newPassword !== passwordForm.confirmPassword) {
      toast.error('两次输入的新密码不一致');
      return;
    }
    
    if (passwordForm.newPassword.length < 6) {
      toast.error('新密码长度至少6位');
      return;
    }
    
    setIsSaving(true);
    try {
      const res = await fetch('/api/user/password', {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(passwordForm),
      });
      const data = await res.json();
      
      if (data.success) {
        toast.success('密码修改成功，请重新登录');
        setPasswordForm({ oldPassword: '', newPassword: '', confirmPassword: '' });
        
        // 退出登录
        await fetch('/api/auth/login', { method: 'DELETE' });
        
        // 延迟跳转，让用户看到成功提示
        setTimeout(() => {
          router.push('/login');
        }, 1500);
      } else {
        toast.error(data.error || '修改失败');
      }
    } catch {
      toast.error('修改失败');
    } finally {
      setIsSaving(false);
    }
  };

  // 更新设置
  const handleUpdateSetting = async <K extends keyof AppSettings>(
    key: K, 
    value: AppSettings[K]
  ) => {
    const success = await updateSetting(key, value);
    if (success) {
      toast.success('设置已保存');
    } else {
      toast.error('保存失败');
    }
  };

  // 处理头像上传
  const handleAvatarClick = () => {
    fileInputRef.current?.click();
  };

  const handleAvatarChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    
    // 验证文件类型
    if (!file.type.startsWith('image/')) {
      toast.error('请上传图片文件');
      return;
    }
    
    // 验证文件大小（最大2MB）
    if (file.size > 2 * 1024 * 1024) {
      toast.error('图片大小不能超过2MB');
      return;
    }
    
    setIsUploadingAvatar(true);
    try {
      const formData = new FormData();
      formData.append('file', file);
      
      const res = await fetch('/api/user/avatar', {
        method: 'POST',
        body: formData,
      });
      const data = await res.json();
      
      if (data.success) {
        toast.success('头像上传成功');
        // 更新用户信息
        setCurrentUser(prev => prev ? { ...prev, avatar: data.data.avatarUrl } : null);
      } else {
        toast.error(data.error || '上传失败');
      }
    } catch {
      toast.error('上传失败');
    } finally {
      setIsUploadingAvatar(false);
      // 清空文件输入
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    }
  };

  // 返回主页
  const handleBack = () => {
    router.push('/');
  };

  if (isLoading || settingsLoading) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center">
        <Loader2 className="w-8 h-8 text-violet-500 animate-spin" />
      </div>
    );
  }

  const tabs: { id: SettingsTab; label: string; icon: React.ReactNode }[] = [
    { id: 'profile', label: '个人资料', icon: <User className="w-5 h-5" /> },
    { id: 'security', label: '账号安全', icon: <Lock className="w-5 h-5" /> },
    { id: 'notifications', label: '通知设置', icon: <Bell className="w-5 h-5" /> },
    { id: 'appearance', label: '外观设置', icon: <Palette className="w-5 h-5" /> },
    { id: 'display', label: '显示设置', icon: <Image className="w-5 h-5" /> },
  ];

  return (
    <div className="min-h-screen bg-slate-50">
      <Toaster position="top-center" richColors closeButton />
      
      {/* 顶部导航 */}
      <header className="bg-white border-b border-slate-200 sticky top-0 z-10">
        <div className="max-w-5xl mx-auto px-6 h-16 flex items-center justify-between">
          <div className="flex items-center gap-4">
            <button 
              onClick={handleBack}
              className="text-slate-500 hover:text-slate-700 transition-colors"
            >
              ← 返回
            </button>
            <h1 className="text-xl font-semibold text-slate-800">账户设置</h1>
          </div>
          <div className="flex items-center gap-3">
            <div className="text-right">
              <p className="text-sm font-medium text-slate-700">{currentUser?.nickname || currentUser?.username}</p>
              <p className="text-xs text-slate-500">{currentUser?.email}</p>
            </div>
            <div className="w-10 h-10 rounded-full bg-gradient-to-br from-violet-500 to-purple-600 flex items-center justify-center text-white font-medium">
              {(currentUser?.nickname || currentUser?.username)?.[0]?.toUpperCase()}
            </div>
          </div>
        </div>
      </header>

      <div className="max-w-5xl mx-auto px-6 py-8">
        <div className="flex gap-8">
          {/* 侧边导航 */}
          <nav className="w-56 shrink-0">
            <div className="bg-white rounded-2xl border border-slate-200 p-3 sticky top-24">
              {tabs.map((tab) => (
                <button
                  key={tab.id}
                  onClick={() => setActiveTab(tab.id)}
                  className={cn(
                    "w-full flex items-center gap-3 px-4 py-3 rounded-xl text-left transition-colors",
                    activeTab === tab.id
                      ? "bg-violet-50 text-violet-700"
                      : "text-slate-600 hover:bg-slate-50"
                  )}
                >
                  {tab.icon}
                  <span className="font-medium">{tab.label}</span>
                </button>
              ))}
            </div>
          </nav>

          {/* 主内容区 */}
          <main className="flex-1">
            {/* 个人资料 */}
            {activeTab === 'profile' && (
              <div className="bg-white rounded-2xl border border-slate-200 p-6">
                <h2 className="text-lg font-semibold text-slate-800 mb-6">个人资料</h2>
                
                <div className="space-y-6">
                  {/* 头像 */}
                  <div className="flex items-center gap-6">
                    <div className="relative group">
                      {currentUser?.avatar ? (
                        <img 
                          src={currentUser.avatar} 
                          alt="头像"
                          className="w-20 h-20 rounded-2xl object-cover shadow-lg"
                        />
                      ) : (
                        <div className="w-20 h-20 rounded-2xl bg-gradient-to-br from-violet-500 to-purple-600 flex items-center justify-center text-white text-2xl font-bold shadow-lg">
                          {(currentUser?.nickname || currentUser?.username)?.[0]?.toUpperCase()}
                        </div>
                      )}
                      {isUploadingAvatar && (
                        <div className="absolute inset-0 bg-black/50 rounded-2xl flex items-center justify-center">
                          <Loader2 className="w-6 h-6 text-white animate-spin" />
                        </div>
                      )}
                    </div>
                    <div>
                      <input
                        ref={fileInputRef}
                        type="file"
                        accept="image/*"
                        onChange={handleAvatarChange}
                        className="hidden"
                      />
                      <Button 
                        variant="outline" 
                        size="sm"
                        onClick={handleAvatarClick}
                        disabled={isUploadingAvatar}
                      >
                        {isUploadingAvatar ? (
                          <Loader2 className="w-4 h-4 animate-spin mr-2" />
                        ) : (
                          <Camera className="w-4 h-4 mr-2" />
                        )}
                        更换头像
                      </Button>
                      <p className="text-xs text-slate-500 mt-2">支持 JPG、PNG 格式，最大 2MB</p>
                    </div>
                  </div>

                  {/* 用户名 */}
                  <div>
                    <label className="block text-sm font-medium text-slate-700 mb-2">用户名</label>
                    <Input
                      value={currentUser?.username || ''}
                      disabled
                      className="bg-slate-50"
                    />
                    <p className="text-xs text-slate-500 mt-1">用户名不可修改</p>
                  </div>

                  {/* 昵称 */}
                  <div>
                    <label className="block text-sm font-medium text-slate-700 mb-2">昵称</label>
                    <Input
                      value={profileForm.nickname}
                      onChange={(e) => setProfileForm(prev => ({ ...prev, nickname: e.target.value }))}
                      placeholder="设置一个昵称"
                    />
                  </div>

                  {/* 邮箱 */}
                  <div>
                    <label className="block text-sm font-medium text-slate-700 mb-2">邮箱</label>
                    <Input
                      type="email"
                      value={profileForm.email}
                      onChange={(e) => setProfileForm(prev => ({ ...prev, email: e.target.value }))}
                    />
                  </div>

                  {/* 手机号 */}
                  <div>
                    <label className="block text-sm font-medium text-slate-700 mb-2">手机号</label>
                    <Input
                      value={profileForm.phone}
                      onChange={(e) => setProfileForm(prev => ({ ...prev, phone: e.target.value }))}
                      placeholder="输入手机号"
                    />
                  </div>

                  {/* 个人简介 */}
                  <div>
                    <label className="block text-sm font-medium text-slate-700 mb-2">个人简介</label>
                    <textarea
                      value={profileForm.bio}
                      onChange={(e) => setProfileForm(prev => ({ ...prev, bio: e.target.value }))}
                      placeholder="介绍一下自己..."
                      rows={3}
                      className="w-full px-4 py-3 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-violet-500/20 focus:border-violet-300"
                    />
                  </div>

                  <div className="pt-4 border-t border-slate-100">
                    <Button
                      onClick={handleSaveProfile}
                      disabled={isSaving}
                      className="bg-violet-600 hover:bg-violet-700"
                    >
                      {isSaving ? <Loader2 className="w-4 h-4 animate-spin mr-2" /> : <Save className="w-4 h-4 mr-2" />}
                      保存修改
                    </Button>
                  </div>
                </div>
              </div>
            )}

            {/* 账号安全 */}
            {activeTab === 'security' && (
              <div className="bg-white rounded-2xl border border-slate-200 p-6">
                <h2 className="text-lg font-semibold text-slate-800 mb-6">修改密码</h2>
                
                <div className="space-y-6 max-w-md">
                  {/* 当前密码 */}
                  <div>
                    <label className="block text-sm font-medium text-slate-700 mb-2">当前密码</label>
                    <div className="relative">
                      <Input
                        type={showPasswords.old ? 'text' : 'password'}
                        value={passwordForm.oldPassword}
                        onChange={(e) => setPasswordForm(prev => ({ ...prev, oldPassword: e.target.value }))}
                        placeholder="输入当前密码"
                      />
                      <button
                        type="button"
                        onClick={() => setShowPasswords(prev => ({ ...prev, old: !prev.old }))}
                        className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
                      >
                        {showPasswords.old ? <EyeOff className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
                      </button>
                    </div>
                  </div>

                  {/* 新密码 */}
                  <div>
                    <label className="block text-sm font-medium text-slate-700 mb-2">新密码</label>
                    <div className="relative">
                      <Input
                        type={showPasswords.new ? 'text' : 'password'}
                        value={passwordForm.newPassword}
                        onChange={(e) => setPasswordForm(prev => ({ ...prev, newPassword: e.target.value }))}
                        placeholder="输入新密码（至少6位）"
                      />
                      <button
                        type="button"
                        onClick={() => setShowPasswords(prev => ({ ...prev, new: !prev.new }))}
                        className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
                      >
                        {showPasswords.new ? <EyeOff className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
                      </button>
                    </div>
                  </div>

                  {/* 确认密码 */}
                  <div>
                    <label className="block text-sm font-medium text-slate-700 mb-2">确认新密码</label>
                    <div className="relative">
                      <Input
                        type={showPasswords.confirm ? 'text' : 'password'}
                        value={passwordForm.confirmPassword}
                        onChange={(e) => setPasswordForm(prev => ({ ...prev, confirmPassword: e.target.value }))}
                        placeholder="再次输入新密码"
                      />
                      <button
                        type="button"
                        onClick={() => setShowPasswords(prev => ({ ...prev, confirm: !prev.confirm }))}
                        className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
                      >
                        {showPasswords.confirm ? <EyeOff className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
                      </button>
                    </div>
                  </div>

                  <div className="pt-4 border-t border-slate-100">
                    <Button
                      onClick={handleChangePassword}
                      disabled={isSaving || !passwordForm.oldPassword || !passwordForm.newPassword}
                      className="bg-violet-600 hover:bg-violet-700"
                    >
                      {isSaving ? <Loader2 className="w-4 h-4 animate-spin mr-2" /> : <Lock className="w-4 h-4 mr-2" />}
                      修改密码
                    </Button>
                  </div>
                </div>

                {/* 账号信息 */}
                <div className="mt-8 pt-8 border-t border-slate-200">
                  <h3 className="text-lg font-semibold text-slate-800 mb-4">账号信息</h3>
                  <div className="space-y-3 text-sm">
                    <div className="flex justify-between py-2">
                      <span className="text-slate-500">账号ID</span>
                      <span className="text-slate-700 font-mono">{currentUser?.id}</span>
                    </div>
                    <div className="flex justify-between py-2">
                      <span className="text-slate-500">角色</span>
                      <span className="text-slate-700">{currentUser?.role === 'admin' ? '管理员' : '普通用户'}</span>
                    </div>
                    <div className="flex justify-between py-2">
                      <span className="text-slate-500">注册时间</span>
                      <span className="text-slate-700">{currentUser?.createdAt}</span>
                    </div>
                    <div className="flex justify-between py-2">
                      <span className="text-slate-500">最后登录</span>
                      <span className="text-slate-700">{currentUser?.lastLoginAt ? new Date(currentUser.lastLoginAt).toLocaleString() : '-'}</span>
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* 通知设置 */}
            {activeTab === 'notifications' && settings && (
              <div className="bg-white rounded-2xl border border-slate-200 p-6">
                <h2 className="text-lg font-semibold text-slate-800 mb-6">通知设置</h2>
                
                <div className="space-y-6">
                  <div className="flex items-center justify-between py-3">
                    <div>
                      <p className="font-medium text-slate-700">邮件通知</p>
                      <p className="text-sm text-slate-500">接收重要更新和提醒的邮件通知</p>
                    </div>
                    <label className="relative inline-flex items-center cursor-pointer">
                      <input
                        type="checkbox"
                        checked={settings.emailNotifications}
                        onChange={(e) => handleUpdateSetting('emailNotifications', e.target.checked)}
                        className="sr-only peer"
                      />
                      <div className="w-11 h-6 bg-slate-200 peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-violet-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-violet-600"></div>
                    </label>
                  </div>

                  <div className="flex items-center justify-between py-3 border-t border-slate-100">
                    <div>
                      <p className="font-medium text-slate-700">系统通知</p>
                      <p className="text-sm text-slate-500">接收系统公告和安全提醒</p>
                    </div>
                    <label className="relative inline-flex items-center cursor-pointer">
                      <input
                        type="checkbox"
                        checked={settings.systemNotifications}
                        onChange={(e) => handleUpdateSetting('systemNotifications', e.target.checked)}
                        className="sr-only peer"
                      />
                      <div className="w-11 h-6 bg-slate-200 peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-violet-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-violet-600"></div>
                    </label>
                  </div>

                  <div className="flex items-center justify-between py-3 border-t border-slate-100">
                    <div>
                      <p className="font-medium text-slate-700">上传通知</p>
                      <p className="text-sm text-slate-500">图片上传完成后的通知提醒</p>
                    </div>
                    <label className="relative inline-flex items-center cursor-pointer">
                      <input
                        type="checkbox"
                        checked={settings.uploadNotifications}
                        onChange={(e) => handleUpdateSetting('uploadNotifications', e.target.checked)}
                        className="sr-only peer"
                      />
                      <div className="w-11 h-6 bg-slate-200 peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-violet-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-violet-600"></div>
                    </label>
                  </div>

                  <div className="flex items-center justify-between py-3 border-t border-slate-100">
                    <div>
                      <p className="font-medium text-slate-700">AI智能识别</p>
                      <p className="text-sm text-slate-500">上传知识时自动识别分类</p>
                    </div>
                    <label className="relative inline-flex items-center cursor-pointer">
                      <input
                        type="checkbox"
                        checked={settings.aiRecognitionEnabled}
                        onChange={(e) => handleUpdateSetting('aiRecognitionEnabled', e.target.checked)}
                        className="sr-only peer"
                      />
                      <div className="w-11 h-6 bg-slate-200 peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-violet-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-violet-600"></div>
                    </label>
                  </div>
                </div>
              </div>
            )}

            {/* 外观设置 */}
            {activeTab === 'appearance' && settings && (
              <div className="bg-white rounded-2xl border border-slate-200 p-6">
                <h2 className="text-lg font-semibold text-slate-800 mb-6">外观设置</h2>
                
                <div className="space-y-6">
                  {/* 主题 */}
                  <div>
                    <label className="block text-sm font-medium text-slate-700 mb-3">主题模式</label>
                    <div className="grid grid-cols-3 gap-3">
                      {[
                        { value: 'light', label: '浅色', icon: '☀️' },
                        { value: 'dark', label: '深色', icon: '🌙' },
                        { value: 'system', label: '跟随系统', icon: '💻' },
                      ].map((option) => (
                        <button
                          key={option.value}
                          onClick={() => handleUpdateSetting('theme', option.value as AppSettings['theme'])}
                          className={cn(
                            "flex flex-col items-center gap-2 p-4 rounded-xl border-2 transition-colors",
                            settings.theme === option.value
                              ? "border-violet-500 bg-violet-50"
                              : "border-slate-200 hover:border-slate-300"
                          )}
                        >
                          <span className="text-2xl">{option.icon}</span>
                          <span className="text-sm font-medium text-slate-700">{option.label}</span>
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* 语言 */}
                  <div className="pt-4 border-t border-slate-100">
                    <label className="block text-sm font-medium text-slate-700 mb-3">语言</label>
                    <div className="flex gap-3">
                      {[
                        { value: 'zh-CN', label: '简体中文' },
                        { value: 'en-US', label: 'English' },
                      ].map((option) => (
                        <button
                          key={option.value}
                          onClick={() => handleUpdateSetting('language', option.value as AppSettings['language'])}
                          className={cn(
                            "px-4 py-2 rounded-lg border-2 transition-colors",
                            settings.language === option.value
                              ? "border-violet-500 bg-violet-50 text-violet-700"
                              : "border-slate-200 text-slate-600 hover:border-slate-300"
                          )}
                        >
                          {option.label}
                        </button>
                      ))}
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* 显示设置 */}
            {activeTab === 'display' && settings && (
              <div className="bg-white rounded-2xl border border-slate-200 p-6">
                <h2 className="text-lg font-semibold text-slate-800 mb-6">显示设置</h2>
                
                <div className="space-y-6">
                  {/* 默认视图 */}
                  <div>
                    <label className="block text-sm font-medium text-slate-700 mb-3">默认视图模式</label>
                    <div className="grid grid-cols-3 gap-3">
                      {[
                        { value: 'grid', label: '网格视图', icon: '▦' },
                        { value: 'masonry', label: '瀑布流', icon: '▤' },
                        { value: 'list', label: '列表视图', icon: '☰' },
                      ].map((option) => (
                        <button
                          key={option.value}
                          onClick={() => handleUpdateSetting('defaultView', option.value as AppSettings['defaultView'])}
                          className={cn(
                            "flex flex-col items-center gap-2 p-4 rounded-xl border-2 transition-colors",
                            settings.defaultView === option.value
                              ? "border-violet-500 bg-violet-50"
                              : "border-slate-200 hover:border-slate-300"
                          )}
                        >
                          <span className="text-2xl">{option.icon}</span>
                          <span className="text-sm font-medium text-slate-700">{option.label}</span>
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* 每页数量 */}
                  <div className="pt-4 border-t border-slate-100">
                    <label className="block text-sm font-medium text-slate-700 mb-3">每页显示数量</label>
                    <div className="flex gap-3">
                      {[20, 40, 60, 100].map((size) => (
                        <button
                          key={size}
                          onClick={() => handleUpdateSetting('pageSize', size as AppSettings['pageSize'])}
                          className={cn(
                            "px-4 py-2 rounded-lg border-2 transition-colors",
                            settings.pageSize === size
                              ? "border-violet-500 bg-violet-50 text-violet-700"
                              : "border-slate-200 text-slate-600 hover:border-slate-300"
                          )}
                        >
                          {size} 张
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* 其他开关 */}
                  <div className="pt-4 border-t border-slate-100 space-y-4">
                    <div className="flex items-center justify-between py-3">
                      <div>
                        <p className="font-medium text-slate-700">自动播放视频</p>
                        <p className="text-sm text-slate-500">鼠标悬停时自动播放视频</p>
                      </div>
                      <label className="relative inline-flex items-center cursor-pointer">
                        <input
                          type="checkbox"
                          checked={settings.autoPlayVideos}
                          onChange={(e) => handleUpdateSetting('autoPlayVideos', e.target.checked)}
                          className="sr-only peer"
                        />
                        <div className="w-11 h-6 bg-slate-200 peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-violet-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-violet-600"></div>
                      </label>
                    </div>

                    <div className="flex items-center justify-between py-3">
                      <div>
                        <p className="font-medium text-slate-700">高质量预览</p>
                        <p className="text-sm text-slate-500">加载高质量图片预览</p>
                      </div>
                      <label className="relative inline-flex items-center cursor-pointer">
                        <input
                          type="checkbox"
                          checked={settings.highQualityPreviews}
                          onChange={(e) => handleUpdateSetting('highQualityPreviews', e.target.checked)}
                          className="sr-only peer"
                        />
                        <div className="w-11 h-6 bg-slate-200 peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-violet-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-violet-600"></div>
                      </label>
                    </div>

                    <div className="flex items-center justify-between py-3">
                      <div>
                        <p className="font-medium text-slate-700">紧凑模式</p>
                        <p className="text-sm text-slate-500">减少元素间距，显示更多内容</p>
                      </div>
                      <label className="relative inline-flex items-center cursor-pointer">
                        <input
                          type="checkbox"
                          checked={settings.compactMode}
                          onChange={(e) => handleUpdateSetting('compactMode', e.target.checked)}
                          className="sr-only peer"
                        />
                        <div className="w-11 h-6 bg-slate-200 peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-violet-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-violet-600"></div>
                      </label>
                    </div>

                    <div className="flex items-center justify-between py-3">
                      <div>
                        <p className="font-medium text-slate-700">显示文件信息</p>
                        <p className="text-sm text-slate-500">在图片上显示文件名和大小</p>
                      </div>
                      <label className="relative inline-flex items-center cursor-pointer">
                        <input
                          type="checkbox"
                          checked={settings.showFileInfo}
                          onChange={(e) => handleUpdateSetting('showFileInfo', e.target.checked)}
                          className="sr-only peer"
                        />
                        <div className="w-11 h-6 bg-slate-200 peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-violet-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-violet-600"></div>
                      </label>
                    </div>
                  </div>
                </div>
              </div>
            )}
          </main>
        </div>
      </div>
    </div>
  );
}
