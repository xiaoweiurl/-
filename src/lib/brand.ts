/**
 * 品牌配置系统
 * 宝娜斯集团统一品牌（历史双品牌机制保留，文案统一为宝娜斯集团）
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

  // 纯色背景（iOS 风格，无渐变）
  primaryBg: string;
  primaryLight: string;

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
    
    primaryFrom: 'from-[#007aff]',
    primaryTo: 'to-[#af52de]',
    primarySolid: 'text-[#007aff]',
    
    accentFrom: 'from-[#007aff]',
    accentTo: 'to-[#ff3b30]',
    
    sidebarActiveBg: 'bg-[rgba(0,122,255,0.1)]',
    sidebarActiveText: 'text-[#007aff]',
    sidebarHoverBg: 'hover:bg-[rgba(0,122,255,0.1)]',
    
    buttonGradient: 'bg-[#007AFF]',
    buttonShadow: 'shadow-[0_2px_12px_rgba(0,0,0,0.04)]',

    primaryBg: 'bg-[#007AFF]',
    primaryLight: 'bg-[rgba(0,122,255,0.12)]',

    tagBg: 'bg-[rgba(0,122,255,0.1)]',
    tagText: 'text-[#007aff]',

    loginBg: 'bg-[#F2F2F7]',
    loginCardHoverBorder: 'hover:border-[#007aff]',
  },
  yingyun: {
    key: 'yingyun',
    name: '宝娜斯',
    fullName: '宝娜斯集团',
    slogan: '品质生活 从芯开始',
    logoIcon: 'Cloud',
    
    primaryFrom: 'from-[#007aff]',
    primaryTo: 'to-[#007aff]',
    primarySolid: 'text-[#007aff]',
    
    accentFrom: 'from-[#007aff]',
    accentTo: 'to-[#007aff]',
    
    sidebarActiveBg: 'bg-[rgba(0,122,255,0.1)]',
    sidebarActiveText: 'text-[#007aff]',
    sidebarHoverBg: 'hover:bg-[rgba(0,122,255,0.1)]',
    
    buttonGradient: 'bg-[#007AFF]',
    buttonShadow: 'shadow-[0_2px_12px_rgba(0,0,0,0.04)]',

    primaryBg: 'bg-[#007AFF]',
    primaryLight: 'bg-[rgba(0,122,255,0.12)]',

    tagBg: 'bg-[rgba(0,122,255,0.1)]',
    tagText: 'text-[#007aff]',

    loginBg: 'bg-[#F2F2F7]',
    loginCardHoverBorder: 'hover:border-[#007aff]',
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
 * 公司选择列表（统一为宝娜斯集团）
 */
export const COMPANY_OPTIONS = [
  {
    key: 'bonasi' as BrandKey,
    name: '宝娜斯集团',
    fullName: '宝娜斯集团',
    description: '无缝针织行业领导者，专注品质与创新',
    color: 'rose',
  },
];
