'use client';

import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';

export interface AppSettings {
  theme: 'light' | 'dark' | 'system';
  language: 'zh-CN' | 'en-US';
  pageSize: 20 | 40 | 60 | 100;
  defaultSort: string;
  aiRecognitionEnabled: boolean;
  emailNotifications: boolean;
  systemNotifications: boolean;
  uploadNotifications: boolean;
  autoPlayVideos: boolean;
  highQualityPreviews: boolean;
  compactMode: boolean;
  showFileInfo: boolean;
  defaultView: 'grid' | 'masonry' | 'list';
}

interface SettingsContextType {
  settings: AppSettings | null;
  isLoading: boolean;
  updateSetting: <K extends keyof AppSettings>(key: K, value: AppSettings[K]) => Promise<boolean>;
  refreshSettings: () => Promise<void>;
}

const DEFAULT_SETTINGS: AppSettings = {
  theme: 'system',
  language: 'zh-CN',
  pageSize: 40,
  defaultSort: 'createdAt',
  aiRecognitionEnabled: true,
  emailNotifications: true,
  systemNotifications: true,
  uploadNotifications: true,
  autoPlayVideos: true,
  highQualityPreviews: true,
  compactMode: false,
  showFileInfo: true,
  defaultView: 'grid',
};

const SettingsContext = createContext<SettingsContextType | undefined>(undefined);

export function SettingsProvider({ children }: { children: React.ReactNode }) {
  const [settings, setSettings] = useState<AppSettings | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // 应用主题
  useEffect(() => {
    if (!settings) return;

    const root = document.documentElement;
    const body = document.body;

    if (settings.theme === 'dark') {
      root.classList.add('dark');
      body.classList.add('dark');
      body.style.colorScheme = 'dark';
    } else if (settings.theme === 'light') {
      root.classList.remove('dark');
      body.classList.remove('dark');
      body.style.colorScheme = 'light';
    } else {
      // system - 跟随系统
      const isDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
      if (isDark) {
        root.classList.add('dark');
        body.classList.add('dark');
        body.style.colorScheme = 'dark';
      } else {
        root.classList.remove('dark');
        body.classList.remove('dark');
        body.style.colorScheme = 'light';
      }
    }

    // 保存到 localStorage 以便快速加载
    localStorage.setItem('app_settings', JSON.stringify(settings));
  }, [settings?.theme]);

  // 监听系统主题变化
  useEffect(() => {
    if (settings?.theme !== 'system') return;

    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const handleChange = (e: MediaQueryListEvent) => {
      const root = document.documentElement;
      const body = document.body;
      if (e.matches) {
        root.classList.add('dark');
        body.classList.add('dark');
        body.style.colorScheme = 'dark';
      } else {
        root.classList.remove('dark');
        body.classList.remove('dark');
        body.style.colorScheme = 'light';
      }
    };

    mediaQuery.addEventListener('change', handleChange);
    return () => mediaQuery.removeEventListener('change', handleChange);
  }, [settings?.theme]);

  // 加载设置
  const loadSettings = useCallback(async () => {
    // 先从 localStorage 加载，确保快速响应
    const cached = localStorage.getItem('app_settings');
    if (cached) {
      try {
        const parsed = JSON.parse(cached);
        setSettings(parsed);
      } catch {
        // ignore
      }
    }

    // 然后从服务器获取最新设置
    try {
      const res = await fetch('/api/user/settings', {
        credentials: 'include',  // 发送 Cookie 以便 API route 获取 sessionId
      });
      const data = await res.json();
      if (data.success && data.data) {
        setSettings(data.data);
      } else {
        // 如果没有登录或获取失败，使用默认设置
        if (!cached) {
          setSettings(DEFAULT_SETTINGS);
        }
      }
    } catch {
      // 网络错误，使用缓存或默认设置
      if (!cached) {
        setSettings(DEFAULT_SETTINGS);
      }
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadSettings();
  }, [loadSettings]);

  // 更新单个设置
  const updateSetting = useCallback(async <K extends keyof AppSettings>(
    key: K,
    value: AppSettings[K]
  ): Promise<boolean> => {
    // 乐观更新
    setSettings(prev => prev ? { ...prev, [key]: value } : null);

    try {
      const res = await fetch('/api/user/settings', {
        method: 'PATCH',
        credentials: 'include',  // 发送 Cookie 以便 API route 获取 sessionId
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ [key]: value }),
      });
      const data = await res.json();
      
      if (data.success) {
        setSettings(data.data);
        return true;
      } else {
        // 恢复旧值
        await loadSettings();
        return false;
      }
    } catch {
      // 网络错误，恢复旧值
      await loadSettings();
      return false;
    }
  }, [loadSettings]);

  const refreshSettings = useCallback(async () => {
    await loadSettings();
  }, [loadSettings]);

  return (
    <SettingsContext.Provider value={{ settings, isLoading, updateSetting, refreshSettings }}>
      {children}
    </SettingsContext.Provider>
  );
}

export function useSettings() {
  const context = useContext(SettingsContext);
  if (context === undefined) {
    throw new Error('useSettings must be used within a SettingsProvider');
  }
  return context;
}

// 获取默认设置
export function getDefaultSettings(): AppSettings {
  return { ...DEFAULT_SETTINGS };
}
