/**
 * 品牌配置系统
 * 支持宝娜斯和盈云双品牌切换
 */

export type BrandKey = 'bonasi' | 'yingyun';

export interface BrandConfig {
  key: BrandKey;
  name: string;               // 品牌名
  fullName: string;           // 完整品牌名
  slogan: string;             // 品牌口号
  logoIcon: string;           // Logo图标 (Lucide icon name)
  
  // 主色系
  primaryFrom: string;        // 渐变起始色
  primaryTo: string;          // 渐变结束色
  primarySolid: string;       // 纯色
  
  // 强调色
  accentFrom: string;
  accentTo: string;
  
  // 侧边栏配色
  sidebarActiveBg: string;
  sidebarActiveText: string;
  sidebarHoverBg: string;
  
  // 按钮配色
  buttonGradient: string;
  buttonShadow: string;
  
  // 标签配色
  tagBg: string;
  tagText: string;
  
  // 登录页背景
  loginBg: string;
  loginCardHoverBorder: string;
}

export const BRANDS: Record<BrandKey, BrandConfig> = {
  bonasi: {
    key: 'bonasi',
    name: '宝娜斯',
    fullName: '宝娜斯集团',
    slogan: '品质生活 从芯开始',
    logoIcon: 'Scissors',
    
    primaryFrom: 'from-rose-500',
    primaryTo: 'to-pink-600',
    primarySolid: 'text-rose-600',
    
    accentFrom: 'from-rose-500',
    accentTo: 'to-red-600',
    
    sidebarActiveBg: 'bg-rose-50',
    sidebarActiveText: 'text-rose-700',
    sidebarHoverBg: 'hover:bg-rose-50',
    
    buttonGradient: 'bg-gradient-to-r from-rose-500 to-pink-600',
    buttonShadow: 'shadow-rose-500/25',
    
    tagBg: 'bg-rose-100',
    tagText: 'text-rose-700',
    
    loginBg: 'bg-gradient-to-br from-rose-50 via-white to-pink-50',
    loginCardHoverBorder: 'hover:border-rose-300',
  },
  yingyun: {
    key: 'yingyun',
    name: '盈云',
    fullName: '盈云科技',
    slogan: '智能驱动 价值创造',
    logoIcon: 'Cloud',
    
    primaryFrom: 'from-violet-500',
    primaryTo: 'to-purple-600',
    primarySolid: 'text-violet-600',
    
    accentFrom: 'from-violet-500',
    accentTo: 'to-purple-600',
    
    sidebarActiveBg: 'bg-violet-50',
    sidebarActiveText: 'text-violet-700',
    sidebarHoverBg: 'hover:bg-violet-50',
    
    buttonGradient: 'bg-gradient-to-r from-violet-500 to-purple-600',
    buttonShadow: 'shadow-violet-500/25',
    
    tagBg: 'bg-indigo-100',
    tagText: 'text-indigo-700',
    
    loginBg: 'bg-gradient-to-br from-violet-50 via-white to-purple-50',
    loginCardHoverBorder: 'hover:border-violet-300',
  },
};

/**
 * 根据公司名称获取品牌配置
 */
export function getBrandByCompany(company: string | null | undefined): BrandConfig {
  if (!company) return BRANDS.yingyun;
  if (company.includes('宝娜斯') || company.toLowerCase().includes('bonasi')) {
    return BRANDS.bonasi;
  }
  return BRANDS.yingyun;
}

/**
 * 从 localStorage 获取当前品牌
 */
export function getCurrentBrand(): BrandConfig {
  if (typeof window === 'undefined') return BRANDS.yingyun;
  const company = localStorage.getItem('user_company');
  return getBrandByCompany(company);
}

/**
 * 公司选择列表
 */
export const COMPANY_OPTIONS = [
  {
    key: 'bonasi' as BrandKey,
    name: '宝娜斯',
    fullName: '宝娜斯集团',
    description: '无缝针织行业领导者，专注品质与创新',
    color: 'rose',
  },
  {
    key: 'yingyun' as BrandKey,
    name: '盈云',
    fullName: '盈云科技',
    description: '智能产品中台，驱动数字化转型',
    color: 'violet',
  },
];
