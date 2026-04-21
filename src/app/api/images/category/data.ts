import { NextRequest, NextResponse } from 'next/server';

// 模拟内存存储（生产环境应使用数据库）
const imagesStore: ImageData[] = [
  {
    id: '1',
    url: 'https://images.unsplash.com/photo-1682687220742-aba13b6e50ba?w=800&h=600&fit=crop',
    title: '壮丽山脉风景',
    size: '2.4 MB',
    sizeInBytes: 2400000,
    resolution: '1920×1280',
    date: '2024-01-15',
    favorite: true,
    tags: ['风景', '山脉', '自然'],
    fileType: 'jpg',
  },
  {
    id: '2',
    url: 'https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=800&h=600&fit=crop',
    title: '晨雾森林',
    size: '3.1 MB',
    sizeInBytes: 3100000,
    resolution: '2560×1440',
    date: '2024-01-14',
    favorite: false,
    tags: ['风景', '森林', '自然'],
    fileType: 'png',
  },
  {
    id: '3',
    url: 'https://images.unsplash.com/photo-1469474968028-56623f02e42e?w=800&h=600&fit=crop',
    title: '日落云海',
    size: '2.8 MB',
    sizeInBytes: 2800000,
    resolution: '3840×2160',
    date: '2024-01-13',
    favorite: true,
    tags: ['风景', '日落', '云海'],
    fileType: 'jpg',
  },
  {
    id: '4',
    url: 'https://images.unsplash.com/photo-1447752875215-b2761acb3c5d?w=800&h=600&fit=crop',
    title: '宁静湖泊',
    size: '1.9 MB',
    sizeInBytes: 1900000,
    resolution: '1920×1080',
    date: '2024-01-12',
    favorite: false,
    tags: ['风景', '湖泊', '倒影'],
    fileType: 'png',
  },
  {
    id: '5',
    url: 'https://images.unsplash.com/photo-1433086966358-54859d0ed716?w=800&h=600&fit=crop',
    title: '瀑布溪流',
    size: '4.2 MB',
    sizeInBytes: 4200000,
    resolution: '4000×3000',
    date: '2024-01-11',
    favorite: false,
    tags: ['风景', '瀑布', '溪流'],
    fileType: 'gif',
  },
  {
    id: '6',
    url: 'https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=800&h=600&fit=crop',
    title: '雪山全景',
    size: '5.1 MB',
    sizeInBytes: 5100000,
    resolution: '5120×2880',
    date: '2024-01-10',
    favorite: true,
    tags: ['风景', '雪山', '全景'],
    fileType: 'jpg',
  },
  {
    id: '7',
    url: 'https://images.unsplash.com/photo-1501785888041-af3ef285b470?w=800&h=600&fit=crop',
    title: '湖畔黄昏',
    size: '2.3 MB',
    sizeInBytes: 2300000,
    resolution: '1920×1080',
    date: '2024-01-09',
    favorite: false,
    tags: ['风景', '湖泊', '黄昏'],
    fileType: 'jpg',
  },
  {
    id: '8',
    url: 'https://images.unsplash.com/photo-1475924156734-496f6cac6ec1?w=800&h=600&fit=crop',
    title: '草原落日',
    size: '2.7 MB',
    sizeInBytes: 2700000,
    resolution: '2560×1600',
    date: '2024-01-08',
    favorite: false,
    tags: ['风景', '草原', '落日'],
    fileType: 'png',
  },
  {
    id: '9',
    url: 'https://images.unsplash.com/photo-1439066615861-d1af74d74000?w=800&h=600&fit=crop',
    title: '山间湖泊',
    size: '3.5 MB',
    sizeInBytes: 3500000,
    resolution: '3200×2000',
    date: '2024-01-07',
    favorite: true,
    tags: ['风景', '山脉', '湖泊'],
    fileType: 'jpg',
  },
  {
    id: '10',
    url: 'https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=800&h=600&fit=crop',
    title: '迷雾山谷',
    size: '2.1 MB',
    sizeInBytes: 2100000,
    resolution: '1920×1200',
    date: '2024-01-06',
    favorite: false,
    tags: ['风景', '山谷', '雾'],
    fileType: 'gif',
  },
  {
    id: '11',
    url: 'https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=800&h=600&fit=crop',
    title: '阳光森林',
    size: '2.6 MB',
    sizeInBytes: 2600000,
    resolution: '2400×1600',
    date: '2024-01-05',
    favorite: false,
    tags: ['风景', '森林', '阳光'],
    fileType: 'jpg',
  },
  {
    id: '12',
    url: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=800&h=600&fit=crop',
    title: '海岸线',
    size: '3.8 MB',
    sizeInBytes: 3800000,
    resolution: '4000×2667',
    date: '2024-01-04',
    favorite: true,
    tags: ['风景', '海岸', '海浪'],
    fileType: 'png',
  },
  // 人物相册图片
  {
    id: '13',
    url: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=800&h=600&fit=crop',
    title: '微笑少女',
    size: '2.2 MB',
    sizeInBytes: 2200000,
    resolution: '2000×1333',
    date: '2024-01-15',
    favorite: true,
    tags: ['人物', '肖像', '微笑'],
    fileType: 'jpg',
  },
  {
    id: '14',
    url: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=800&h=600&fit=crop',
    title: '青年肖像',
    size: '1.8 MB',
    sizeInBytes: 1800000,
    resolution: '1800×1200',
    date: '2024-01-14',
    favorite: false,
    tags: ['人物', '肖像', '青年'],
    fileType: 'jpg',
  },
  {
    id: '15',
    url: 'https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=800&h=600&fit=crop',
    title: '思考中的女性',
    size: '2.5 MB',
    sizeInBytes: 2500000,
    resolution: '2200×1467',
    date: '2024-01-13',
    favorite: true,
    tags: ['人物', '肖像', '女性'],
    fileType: 'png',
  },
  {
    id: '16',
    url: 'https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?w=800&h=600&fit=crop',
    title: '户外人像',
    size: '3.0 MB',
    sizeInBytes: 3000000,
    resolution: '2400×1600',
    date: '2024-01-12',
    favorite: false,
    tags: ['人物', '户外', '肖像'],
    fileType: 'jpg',
  },
  // 建筑相册图片
  {
    id: '17',
    url: 'https://images.unsplash.com/photo-1486325212027-8081e485255e?w=800&h=600&fit=crop',
    title: '现代建筑',
    size: '3.2 MB',
    sizeInBytes: 3200000,
    resolution: '2600×1733',
    date: '2024-01-15',
    favorite: true,
    tags: ['建筑', '现代', '城市'],
    fileType: 'jpg',
  },
  {
    id: '18',
    url: 'https://images.unsplash.com/photo-1449824913935-59a10b8d2000?w=800&h=600&fit=crop',
    title: '城市天际线',
    size: '4.0 MB',
    sizeInBytes: 4000000,
    resolution: '3000×2000',
    date: '2024-01-14',
    favorite: false,
    tags: ['建筑', '城市', '天际线'],
    fileType: 'jpg',
  },
  {
    id: '19',
    url: 'https://images.unsplash.com/photo-1487958449943-2429e8be8625?w=800&h=600&fit=crop',
    title: '古典建筑',
    size: '2.8 MB',
    sizeInBytes: 2800000,
    resolution: '2400×1600',
    date: '2024-01-13',
    favorite: true,
    tags: ['建筑', '古典', '历史'],
    fileType: 'png',
  },
  {
    id: '20',
    url: 'https://images.unsplash.com/photo-1448630360428-65456885c650?w=800&h=600&fit=crop',
    title: '桥梁夜景',
    size: '3.5 MB',
    sizeInBytes: 3500000,
    resolution: '2800×1867',
    date: '2024-01-12',
    favorite: false,
    tags: ['建筑', '桥梁', '夜景'],
    fileType: 'jpg',
  },
  // 美食相册图片
  {
    id: '21',
    url: 'https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=800&h=600&fit=crop',
    title: '精致料理',
    size: '2.0 MB',
    sizeInBytes: 2000000,
    resolution: '2000×1333',
    date: '2024-01-15',
    favorite: true,
    tags: ['美食', '料理', '精致'],
    fileType: 'jpg',
  },
  {
    id: '22',
    url: 'https://images.unsplash.com/photo-1476224203421-9ac39bcb3327?w=800&h=600&fit=crop',
    title: '甜点蛋糕',
    size: '1.6 MB',
    sizeInBytes: 1600000,
    resolution: '1800×1200',
    date: '2024-01-14',
    favorite: false,
    tags: ['美食', '甜点', '蛋糕'],
    fileType: 'jpg',
  },
  {
    id: '23',
    url: 'https://images.unsplash.com/photo-1540189549336-e6e99c3679fe?w=800&h=600&fit=crop',
    title: '健康沙拉',
    size: '1.8 MB',
    sizeInBytes: 1800000,
    resolution: '1900×1267',
    date: '2024-01-13',
    favorite: true,
    tags: ['美食', '健康', '沙拉'],
    fileType: 'png',
  },
  {
    id: '24',
    url: 'https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?w=800&h=600&fit=crop',
    title: '披萨美食',
    size: '2.2 MB',
    sizeInBytes: 2200000,
    resolution: '2100×1400',
    date: '2024-01-12',
    favorite: false,
    tags: ['美食', '披萨', '西餐'],
    fileType: 'jpg',
  },
  // 旅行相册图片
  {
    id: '25',
    url: 'https://images.unsplash.com/photo-1469474968028-56623f02e42e?w=800&h=600&fit=crop',
    title: '山间旅行',
    size: '3.0 MB',
    sizeInBytes: 3000000,
    resolution: '2400×1600',
    date: '2024-01-15',
    favorite: true,
    tags: ['旅行', '山区', '探险'],
    fileType: 'jpg',
  },
  {
    id: '26',
    url: 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=800&h=600&fit=crop',
    title: '海滩度假',
    size: '2.5 MB',
    sizeInBytes: 2500000,
    resolution: '2200×1467',
    date: '2024-01-14',
    favorite: false,
    tags: ['旅行', '海滩', '度假'],
    fileType: 'jpg',
  },
  {
    id: '27',
    url: 'https://images.unsplash.com/photo-1493976040374-85c8e12f0c0e?w=800&h=600&fit=crop',
    title: '古镇游历',
    size: '2.8 MB',
    sizeInBytes: 2800000,
    resolution: '2400×1600',
    date: '2024-01-13',
    favorite: true,
    tags: ['旅行', '古镇', '文化'],
    fileType: 'png',
  },
  {
    id: '28',
    url: 'https://images.unsplash.com/photo-1476514525535-07fb3b4ae5f1?w=800&h=600&fit=crop',
    title: '湖泊之旅',
    size: '3.2 MB',
    sizeInBytes: 3200000,
    resolution: '2600×1733',
    date: '2024-01-12',
    favorite: false,
    tags: ['旅行', '湖泊', '自然'],
    fileType: 'jpg',
  },
];

interface ImageData {
  id: string;
  url: string;
  title: string;
  size: string;
  sizeInBytes: number;
  resolution: string;
  date: string;
  favorite: boolean;
  tags: string[];
  fileType: string;
  deleted?: boolean;
  deletedAt?: string;
}

// 相册名称与标签的映射关系
const albumTagMapping: Record<string, string[]> = {
  '风景': ['风景', '山脉', '森林', '湖泊', '瀑布', '海岸', '草原', '山谷', '自然'],
  '人物': ['人物', '肖像', '青年', '女性', '微笑'],
  '建筑': ['建筑', '现代', '古典', '城市', '桥梁', '天际线'],
  '美食': ['美食', '料理', '甜点', '蛋糕', '沙拉', '披萨', '西餐', '健康'],
  '旅行': ['旅行', '山区', '海滩', '度假', '古镇', '文化', '探险'],
};

// 获取所有可用的标签
export async function GET(request: NextRequest) {
  const { searchParams } = new URL(request.url);
  const action = searchParams.get('action');
  
  // 获取所有标签统计
  if (action === 'tags') {
    const tagCount: Record<string, number> = {};
    imagesStore.forEach(img => {
      if (img.deleted) return;
      img.tags.forEach(tag => {
        tagCount[tag] = (tagCount[tag] || 0) + 1;
      });
    });
    
    return NextResponse.json({
      success: true,
      data: tagCount,
    });
  }
  
  // 获取相册标签映射
  if (action === 'mapping') {
    return NextResponse.json({
      success: true,
      data: albumTagMapping,
    });
  }
  
  return NextResponse.json({
    success: false,
    error: '请指定action参数',
  });
}

// 导出图片数据供其他模块使用
export { imagesStore, albumTagMapping };
