/**
 * 品牌配置系统
 * 全系统统一为宝娜斯品牌（不再区分公司）
 */

export type BrandKey = 'bonasi';

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
};

/** 默认品牌：宝娜斯 */
export const DEFAULT_BRAND: BrandConfig = BRANDS.bonasi;

/**
 * 获取品牌配置（全系统统一宝娜斯，参数保留仅为兼容旧调用）
 */
export function getBrandByCompany(_company?: string | null): BrandConfig {
  return DEFAULT_BRAND;
}

/**
 * 从 localStorage 获取当前品牌（统一宝娜斯）
 */
export function getCurrentBrand(): BrandConfig {
  return DEFAULT_BRAND;
}
