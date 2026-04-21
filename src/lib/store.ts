// 共享的图片数据存储
// 注意：生产环境应使用数据库

export interface ImageData {
  id: string;
  url: string;
  title: string;
  size: string;
  sizeInBytes: number;
  resolution: string;
  date: string;
  favorite: boolean;
  tags?: string[];
  albumId?: string;
  albumName?: string;
  fileType: string;
  deleted?: boolean;
  deletedAt?: string;
}

// 相册定义
export interface AlbumInfo {
  id: string;
  name: string;
  description: string;
  keywords: string[]; // 用于自动分类的关键词
}

// 相册列表 - 根据服装类型细分
export const ALBUMS: AlbumInfo[] = [
  {
    id: 'album-tshirt',
    name: 'T恤',
    description: '短袖/长袖T恤、速干衣',
    keywords: ['T恤', 't恤'],
  },
  {
    id: 'album-underwear',
    name: '内衣',
    description: '贴身内衣、打底衣',
    keywords: ['内衣'],
  },
  {
    id: 'album-fleece',
    name: '抓绒衣',
    description: '抓绒衣、保暖中层',
    keywords: ['抓绒衣', '抓绒'],
  },
  {
    id: 'album-jacket',
    name: '冲锋衣',
    description: '防风防雨冲锋衣、硬壳外套',
    keywords: ['冲锋衣'],
  },
  {
    id: 'album-softshell',
    name: '软壳',
    description: '软壳外套、防泼水外套',
    keywords: ['软壳'],
  },
];

/**
 * 根据图片名称自动匹配相册
 * @param name 图片名称或文件名
 * @returns 匹配的相册信息，如果没有匹配则返回 undefined
 */
export function autoClassifyByName(name: string): { albumId: string; albumName: string } | undefined {
  // 定义分类规则 - 只匹配明确的服装类型关键词
  const categoryRules: { albumId: string; albumName: string; keywords: string[] }[] = [
    // 外套类
    {
      albumId: 'album-jacket',
      albumName: '冲锋衣',
      keywords: ['冲锋衣'],
    },
    {
      albumId: 'album-softshell',
      albumName: '软壳',
      keywords: ['软壳'],
    },
    {
      albumId: 'album-fleece',
      albumName: '抓绒衣',
      keywords: ['抓绒衣', '抓绒'],
    },
    // 内衣类
    {
      albumId: 'album-underwear',
      albumName: '内衣',
      keywords: ['内衣'],
    },
    {
      albumId: 'album-tshirt',
      albumName: 'T恤',
      keywords: ['T恤', 't恤'],
    },
  ];
  
  // 按顺序匹配关键词
  for (const rule of categoryRules) {
    for (const keyword of rule.keywords) {
      if (name.includes(keyword)) {
        return { albumId: rule.albumId, albumName: rule.albumName };
      }
    }
  }
  
  // 没有匹配的分类
  return undefined;
}

/**
 * 根据图片标题和文件名综合判断分类
 * 优先使用标题中的分类关键词，如果没有再使用文件名
 * @param title 图片标题
 * @param fileName 图片文件名
 * @returns 匹配的相册信息
 */
export function classifyImage(title: string, fileName?: string): { albumId: string; albumName: string } | undefined {
  // 优先检查标题中的分类关键词
  const titleClassification = autoClassifyByName(title);
  if (titleClassification) {
    return titleClassification;
  }
  
  // 如果标题没有匹配到分类，再检查文件名
  if (fileName) {
    return autoClassifyByName(fileName);
  }
  
  return undefined;
}

/**
 * 根据图片名称自动生成标签
 * @param name 图片名称或文件名
 * @param brand 品牌名称（可选）
 * @returns 生成的标签数组
 */
export function autoGenerateTags(name: string, brand?: string): string[] {
  const tags: string[] = [];
  
  // 根据关键词添加标签
  const tagRules: { keywords: string[]; tag: string }[] = [
    { keywords: ['T恤', 't恤'], tag: 'T恤' },
    { keywords: ['内衣', '打底'], tag: '内衣' },
    { keywords: ['美利奴', '羊毛'], tag: '美利奴羊毛' },
    { keywords: ['抓绒', '抓绒衣'], tag: '抓绒衣' },
    { keywords: ['冲锋衣'], tag: '冲锋衣' },
    { keywords: ['软壳'], tag: '软壳' },
    { keywords: ['防风'], tag: '防风' },
    { keywords: ['防雨', '防泼水'], tag: '防水' },
    { keywords: ['保暖', '保暖层'], tag: '保暖' },
    { keywords: ['速干'], tag: '速干' },
    { keywords: ['透气'], tag: '透气' },
    { keywords: ['户外'], tag: '户外' },
    { keywords: ['登山', '徒步'], tag: '登山' },
    { keywords: ['休闲'], tag: '休闲' },
  ];
  
  // 匹配标签
  for (const rule of tagRules) {
    if (rule.keywords.some(kw => name.includes(kw))) {
      tags.push(rule.tag);
    }
  }
  
  // 添加品牌标签
  if (brand) {
    tags.push(brand);
  }
  
  // 去重
  return [...new Set(tags)];
}

// 使用全局变量确保数据在热更新时不会丢失
declare global {
   
  var __imagesStore: ImageData[] | undefined;
}

// 初始化图片数据 - 使用自动分类
const initialImages: ImageData[] = [
  // 抓绒衣
  {
    id: '1',
    url: '/assets/「折扣」patagonia巴塔R1AIR抓绒衣男女户外透气排汗保暖速干圆领_619.png',
    title: 'Patagonia R1 AIR 抓绒衣',
    size: '2.4 MB',
    sizeInBytes: 2400000,
    resolution: '800×800',
    date: '2024-01-15',
    favorite: true,
    tags: ['抓绒衣', '保暖', '户外', '透气', '速干'],
    fileType: 'png',
    ...classifyImage('Patagonia R1 AIR 抓绒衣', '「折扣」patagonia巴塔R1AIR抓绒衣男女户外透气排汗保暖速干圆领_619.png'),
  },
  // 内衣
  {
    id: '2',
    url: '/assets/【单依纯同款】icebreaker美利奴羊毛女200 Oasis吸湿长袖T恤徒步_4.png',
    title: 'Icebreaker 美利奴羊毛内衣',
    size: '3.1 MB',
    sizeInBytes: 3100000,
    resolution: '800×800',
    date: '2024-01-14',
    favorite: false,
    tags: ['内衣', '美利奴羊毛', '保暖', '户外'],
    fileType: 'png',
    ...classifyImage('Icebreaker 美利奴羊毛内衣', '【单依纯同款】icebreaker美利奴羊毛女200 Oasis吸湿长袖T恤徒步_4.png'),
  },
  // 软壳外套
  {
    id: '3',
    url: '/assets/【经典CREW】 HELLY HANSEN_HH男款户外软壳防泼水保暖登山服抓绒_98.png',
    title: 'HELLY HANSEN 软壳外套',
    size: '2.8 MB',
    sizeInBytes: 2800000,
    resolution: '800×800',
    date: '2024-01-13',
    favorite: true,
    tags: ['软壳', '防水', '保暖', '户外'],
    fileType: 'png',
    ...classifyImage('HELLY HANSEN 软壳外套', '【经典CREW】 HELLY HANSEN_HH男款户外软壳防泼水保暖登山服抓绒_98.png'),
  },
  // T恤
  {
    id: '4',
    url: '/assets/【经典款】HELLYHANSEN_HH 男款吸湿速干轻户外都市休闲长袖T恤_372.png',
    title: 'HELLY HANSEN 长袖T恤',
    size: '1.9 MB',
    sizeInBytes: 1900000,
    resolution: '800×800',
    date: '2024-01-12',
    favorite: false,
    tags: ['T恤', '速干', '休闲', '户外'],
    fileType: 'png',
    ...classifyImage('HELLY HANSEN 长袖T恤', '【经典款】HELLYHANSEN_HH 男款吸湿速干轻户外都市休闲长袖T恤_372.png'),
  },
  // 冲锋衣
  {
    id: '5',
    url: '/assets/【王一博同款】HELLY HANSEN_HH 专业Ⅰ级登山3L防风防雨冲锋衣_371.png',
    title: 'HELLY HANSEN 专业冲锋衣',
    size: '4.2 MB',
    sizeInBytes: 4200000,
    resolution: '800×800',
    date: '2024-01-11',
    favorite: true,
    tags: ['冲锋衣', '防风', '防水', '专业', '登山'],
    fileType: 'png',
    ...classifyImage('HELLY HANSEN 专业冲锋衣', '【王一博同款】HELLY HANSEN_HH 专业Ⅰ级登山3L防风防雨冲锋衣_371.png'),
  },
];

// 获取或初始化全局存储
// 数据版本号 - 更新此值可强制重新初始化数据
const DATA_VERSION = '5.0';

// 全局版本号存储
declare global {
   
  var __imagesStoreVersion: string | undefined;
}

export function getImagesStore(): ImageData[] {
  // 检查版本号，如果版本不匹配则重新初始化
  if (!globalThis.__imagesStore || globalThis.__imagesStoreVersion !== DATA_VERSION) {
    globalThis.__imagesStore = [...initialImages];
    globalThis.__imagesStoreVersion = DATA_VERSION;
    console.log('图片数据已重新初始化，版本:', DATA_VERSION);
  }
  return globalThis.__imagesStore;
}

/**
 * 添加新图片到存储（自动根据名称分类和生成标签）
 * @param image 新图片数据（至少包含 id, url, title）
 * @returns 添加后的图片数据（包含自动生成的分类和标签）
 */
export function addImageToStore(image: Omit<ImageData, 'albumId' | 'albumName' | 'tags'> & Partial<Pick<ImageData, 'albumId' | 'albumName' | 'tags'>>): ImageData {
  const store = getImagesStore();
  
  // 检查是否已存在
  const existingIndex = store.findIndex(img => img.id === image.id);
  if (existingIndex !== -1) {
    // 已存在，更新分类和标签（如果没有的话）
    const existing = store[existingIndex];
    if (!existing.albumId || !existing.albumName) {
      // 优先使用标题分类，其次使用URL
      const classification = classifyImage(image.title || '', image.url);
      if (classification) {
        existing.albumId = classification.albumId;
        existing.albumName = classification.albumName;
      }
    }
    if (!existing.tags || existing.tags.length === 0) {
      existing.tags = autoGenerateTags(image.title || image.url);
    }
    return existing;
  }
  
  // 新图片，自动生成分类和标签
  // 优先使用标题分类，其次使用URL
  const classification = classifyImage(image.title || '', image.url);
  const tags = image.tags && image.tags.length > 0 
    ? image.tags 
    : autoGenerateTags(image.title || image.url);
  
  const newImage: ImageData = {
    ...image,
    albumId: image.albumId || classification?.albumId,
    albumName: image.albumName || classification?.albumName,
    tags,
    favorite: image.favorite ?? false,
    deleted: image.deleted ?? false,
  } as ImageData;
  
  store.push(newImage);
  console.log('新增图片:', newImage.title, '=> 分类:', newImage.albumName || '未分类', '标签:', newImage.tags?.join(', '));
  
  return newImage;
}

/**
 * 批量添加图片到存储（自动分类）
 * @param images 图片数组
 * @returns 添加成功的数量
 */
export function addImagesToStore(images: Array<Omit<ImageData, 'albumId' | 'albumName' | 'tags'> & Partial<Pick<ImageData, 'albumId' | 'albumName' | 'tags'>>>): number {
  let count = 0;
  for (const image of images) {
    try {
      addImageToStore(image);
      count++;
    } catch (error) {
      console.error('添加图片失败:', image.id, error);
    }
  }
  return count;
}

/**
 * 更新图片的分类和标签（根据名称重新生成）
 * @param imageId 图片ID
 * @returns 更新后的图片数据，如果不存在则返回 null
 */
export function refreshImageClassification(imageId: string): ImageData | null {
  const store = getImagesStore();
  const image = store.find(img => img.id === imageId);
  
  if (!image) {
    return null;
  }
  
  // 重新生成分类和标签
  const classification = autoClassifyByName(image.title || image.url);
  const tags = autoGenerateTags(image.title || image.url);
  
  image.albumId = classification?.albumId;
  image.albumName = classification?.albumName;
  image.tags = tags;
  
  console.log('刷新图片分类:', image.title, '=> 分类:', image.albumName || '未分类', '标签:', image.tags?.join(', '));
  
  return image;
}

/**
 * 批量刷新所有图片的分类和标签
 * @returns 更新的数量
 */
export function refreshAllImagesClassification(): number {
  const store = getImagesStore();
  let count = 0;
  
  for (const image of store) {
    const classification = autoClassifyByName(image.title || image.url);
    const tags = autoGenerateTags(image.title || image.url);
    
    // 只有当分类或标签有变化时才更新
    if (image.albumId !== classification?.albumId || 
        image.albumName !== classification?.albumName ||
        JSON.stringify(image.tags) !== JSON.stringify(tags)) {
      image.albumId = classification?.albumId;
      image.albumName = classification?.albumName;
      image.tags = tags;
      count++;
    }
  }
  
  if (count > 0) {
    console.log('批量刷新分类完成，更新了', count, '张图片');
  }
  
  return count;
}

// 标记图片为已删除
export function markImagesAsDeleted(imageIds: string[]): number {
  const store = getImagesStore();
  let count = 0;
  for (const img of store) {
    if (imageIds.includes(img.id) && !img.deleted) {
      img.deleted = true;
      img.deletedAt = new Date().toISOString();
      count++;
    }
  }
  return count;
}

// 恢复图片
export function restoreImages(imageIds: string[]): number {
  const store = getImagesStore();
  let count = 0;
  for (const img of store) {
    if (imageIds.includes(img.id) && img.deleted) {
      img.deleted = false;
      img.deletedAt = undefined;
      count++;
    }
  }
  return count;
}

// 永久删除图片
export function permanentlyDeleteImages(imageIds: string[]): number {
  const store = getImagesStore();
  let count = 0;
  for (let i = store.length - 1; i >= 0; i--) {
    if (imageIds.includes(store[i].id) && store[i].deleted) {
      store.splice(i, 1);
      count++;
    }
  }
  return count;
}

// 清空回收站
export function clearTrash(): number {
  const store = getImagesStore();
  let count = 0;
  for (let i = store.length - 1; i >= 0; i--) {
    if (store[i].deleted) {
      store.splice(i, 1);
      count++;
    }
  }
  return count;
}
