'use client';

import React from 'react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { useNotifications } from '@/contexts/NotificationContext';
import {
  Upload,
  X,
  FileSpreadsheet,
  Loader2,
  CheckCircle2,
  AlertCircle,
  Download,
} from 'lucide-react';
import * as XLSX from 'xlsx';

interface ExcelBatchUploadProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onUploadSuccess: () => void;
}

interface ExcelRow {
  id: string;
  productName: string;
  mainImageUrl: string; // 商品详情（主图）
  detailImageUrls: string[]; // 图片地址（详情图，可能多张）
  category?: string;
  description?: string;
  status: 'pending' | 'downloading' | 'success' | 'error';
  error?: string;
}

interface UploadedImage {
  productName: string;
  mainImageUrl: string; // 主图
  detailImageUrls: string[]; // 详情图列表（至少为空数组）
  category?: string;
  description?: string;
}

/**
 * 安全解码 URL 编码的字符串
 * 支持 UTF-8、GBK、GB2312 等编码的 URL 编码
 */
function decodeURIComponentSafe(str: string): string {
  if (!str || str.length === 0) {
    return str;
  }
  
  // 如果包含 %，尝试解码
  if (str.indexOf('%') !== -1) {
    // 移除 URL 锚点（如 #bd）
    const cleanStr = str.split('#')[0];
    
    try {
      // 尝试直接解码
      const decoded = decodeURIComponent(cleanStr);
      // 检查是否包含有效的中文字符
      if (/[\u4e00-\u9fa5]/.test(decoded)) {
        return decoded;
      }
    } catch (e) {
      // 解码失败，尝试其他方式
    }
    
    // 如果解码后没有中文字符，尝试 GBK 编码替换
    try {
      // GBK 编码映射（完整）
      const gbkMap: Record<string, string> = {
        '%B3%AC%B7%E7': '冲锋',
        '%B3%AC%B7%E7%D2%B3': '冲锋衣',
        '%CC%F9%C9%ED': '贴身',
        '%CC%F9%C9%ED%B2%E3': '贴身层',
        '%D1%C7%CA%BF': '女士',
        '%C4%BE%CA%BF': '女装',
        '%C4%BF%CA%BF': '男装',
        '%BF%A1%CD%E8': '套装',
        '%D7%D4%C8%AF': '自在',
        '%B3%A1%D7%B0': '系列',
        '%C9%AB%B7%E7': '全域',
        '%BB%F9%D2%C7': '基础',
        '%D0%A1%C9%AB': '小卡',
        '%B5%E7%C4%D4': '电脑',
      };
      
      let result = cleanStr;
      for (const [encoded, decoded] of Object.entries(gbkMap)) {
        if (result.toUpperCase().indexOf(encoded.toUpperCase()) !== -1) {
          result = result.replace(new RegExp(encoded, 'gi'), decoded);
        }
      }
      // 如果替换后有中文字符，返回替换结果
      if (/[\u4e00-\u9fa5]/.test(result)) {
        return result;
      }
    } catch (e) {
      // 替换失败
    }
  }
  
  return str;
}

/**
 * 从文本中提取图片 URL
 * 支持以下格式：
 * 1. Image: [https://xxx.png]
 * 2. Image:[https://xxx.png]
 * 3. 直接的 URL: https://xxx.png
 */
function extractImageUrls(text: string): string[] {
  if (!text) return [];
  
  const urls: string[] = [];
  
  // 匹配 Image: [URL] 或 Image:[URL] 格式
  const imageBracketPattern = /Image:\s*\[(https?:\/\/[^\s\)\]]+)\]/gi;
  let match;
  while ((match = imageBracketPattern.exec(text)) !== null) {
    urls.push(match[1]);
  }
  
  // 如果没找到 Image:[] 格式，尝试匹配直接的 URL
  if (urls.length === 0) {
    const urlPattern = /https?:\/\/[^\s"'\)\]]{20,}/g;
    const matches = text.match(urlPattern);
    if (matches) {
      urls.push(...matches);
    }
  }
  
  // 过滤无效 URL
  return urls.filter(url => 
    !url.includes('s.gif') && 
    (url.includes('.jpg') || url.includes('.png') || url.includes('.jpeg') || 
     url.includes('.gif') || url.includes('.webp') || url.length > 50)
  );
}

export default function ExcelBatchUpload({
  open,
  onOpenChange,
  onUploadSuccess,
}: ExcelBatchUploadProps) {
  const [isDragging, setIsDragging] = React.useState(false);
  const [excelData, setExcelData] = React.useState<ExcelRow[]>([]);
  const [isProcessing, setIsProcessing] = React.useState(false);
  const [excelFileName, setExcelFileName] = React.useState<string>(''); // 保存Excel文件名
  const excelFileNameRef = React.useRef<string>(''); // 使用 ref 保存文件名（同步访问）
  const fileInputRef = React.useRef<HTMLInputElement>(null);
  const { addNotification } = useNotifications();

  // 处理Excel文件选择
  const handleFileSelect = async (files: FileList | null) => {
    if (!files || files.length === 0) return;

    const file = files[0];
    const allowedTypes = [
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      'application/vnd.ms-excel',
    ];

    if (!allowedTypes.includes(file.type) && !file.name.match(/\.(xlsx|xls)$/i)) {
      addNotification({
        type: 'warning',
        title: '文件格式错误',
        message: '请上传 Excel 文件（.xlsx 或 .xls 格式）',
      });
      return;
    }

    try {
      setIsProcessing(true);
      
      // 保存Excel文件名（用于创建层级相册）
      setExcelFileName(file.name);
      excelFileNameRef.current = file.name; // 同步更新 ref
      console.log('[ExcelUpload] 选择的文件:', file.name);

      // 读取Excel文件
      const data = await file.arrayBuffer();
      const workbook = XLSX.read(data, { type: 'array' });
      const sheetName = workbook.SheetNames[0];
      const worksheet = workbook.Sheets[sheetName];
      
      // 使用 raw: false 保留格式，使用 header: 1 获取二维数组（保持列顺序）
      const rawData = XLSX.utils.sheet_to_json(worksheet, { header: 1 }) as unknown[][];
      
      console.log('[ExcelUpload] 解析Excel原始数据（前3行）:', rawData.slice(0, 3));

      // 提取列名
      const headers = rawData[0] as string[];
      
      // 转换数据格式
      const rows: ExcelRow[] = [];
      
      for (let rowIndex = 1; rowIndex < rawData.length; rowIndex++) {
        const row = rawData[rowIndex] as unknown[];
        
        if (!row || row.length === 0) continue;
        
        // 按列索引提取数据（根据实际表头）
        // 列A(0): 分类, 列B(1): 商品名称, 列C(2): 价格, 列D(3): 商品详情（主图）, 列E+(4): 详情图
        
        // 解析分类层级 - 根据实际数据结构：
        // 文件名(X-BIONIC) = 第一层父相册
        // A列值(功能内衣_男士专区_) = 用下划线分隔的子相册
        // 完整路径: X-BIONIC-功能内衣-男士专区
        const firstLevel = excelFileNameRef.current ? excelFileNameRef.current.replace(/\.xlsx$/i, '') : '';
        const categoryColumn = String(row[0] || '').trim(); // A列: 功能内衣_男士专区_
        
        // 分割类别列（下划线分隔），反转顺序后连接
        // A列值: 功能内衣_男士专区_ → 反转后: 男士专区_功能内衣
        const subCategories = categoryColumn.split('_').map(s => s.trim()).filter(s => s).reverse();
        
        // 构建完整分类路径: X-BIONIC-男士专区-功能内衣
        const categoryParts = [firstLevel, ...subCategories];
        const category = categoryParts.join('-');
        
        // 商品名称 - B列才是商品名称
        const rawProductName = String(row[1] || '').trim(); // B列
        const productName = rawProductName; // 不在前端解码，后端统一处理
        
        // 解析主图链接 - 支持两种格式：
        // 1. Image: [https://xxx.png] 格式
        // 2. 直接的 URL: //img.alicdn.com/xxx.jpg
        const rawMainImage = String(row[3] || '').trim();
        let mainImageUrl = '';
        
        // 尝试提取 Image: [URL] 格式
        const imageUrls = extractImageUrls(rawMainImage);
        if (imageUrls.length > 0) {
          mainImageUrl = imageUrls[0];
        } else if (rawMainImage.startsWith('//') || rawMainImage.startsWith('http')) {
          // 直接是 URL 格式
          mainImageUrl = rawMainImage.startsWith('//') ? 'https:' + rawMainImage : rawMainImage;
        }
        
        // 收集详情图URL（从E列开始，索引4）
        // 支持 Image: [URL] 格式和直接 URL 格式
        const detailImageUrls: string[] = [];
        
        // 1. 先收集E列及之后所有非空的单元格文本
        let allText = '';
        for (let colIndex = 4; colIndex < row.length; colIndex++) {
          const value = row[colIndex];
          if (value && typeof value === 'string' && value.trim().length > 0) {
            allText += ' ' + value.trim();
          }
        }
        
        // 2. 使用 extractImageUrls 提取所有图片 URL
        const extractedUrls = extractImageUrls(allText);
        detailImageUrls.push(...extractedUrls);
        
        console.log(`[ExcelUpload] 从E列提取到 ${detailImageUrls.length} 个有效URL`);
        
        // category 已从 row[0] 正确获取，不再重复获取
        const description: string = ''; // 描述在Excel中没有对应列
        
        // 打印解析结果（只打印前3行）
        if (rowIndex <= 3) {
          console.log(`[ExcelUpload] 第${rowIndex}行解析结果:`, {
            productName,
            mainImageUrl: mainImageUrl ? mainImageUrl.substring(0, 50) + '...' : '无',
            detailImageCount: detailImageUrls.length,
            firstDetailUrl: detailImageUrls[0] ? detailImageUrls[0].substring(0, 50) + '...' : '无'
          });
        }
        
        // 过滤无效行
        if (productName && productName.trim() && (mainImageUrl || detailImageUrls.length > 0)) {
          rows.push({
            id: `${Date.now()}_${rowIndex}`,
            productName: productName.trim(),
            mainImageUrl: mainImageUrl ? mainImageUrl.trim() : '',
            detailImageUrls: detailImageUrls,
            category: category && category.trim() ? category.trim() : undefined,
            description: description && description.trim() ? description.trim() : undefined,
            status: 'pending' as const,
          });
        }
      }

      console.log('[ExcelUpload] 有效数据行数:', rows.length);

      if (rows.length === 0) {
        addNotification({
          type: 'warning',
          title: '未找到有效数据',
          message: 'Excel文件中未找到有效的商品名称和图片链接数据',
        });
      } else {
        setExcelData(rows);
        addNotification({
          type: 'success',
          title: 'Excel解析成功',
          message: `成功解析 ${rows.length} 条数据`,
        });
      }
    } catch (error) {
      console.error('[ExcelUpload] 解析Excel失败:', error);
      addNotification({
        type: 'warning',
        title: 'Excel解析失败',
        message: error instanceof Error ? error.message : '解析Excel文件时出错',
      });
    } finally {
      setIsProcessing(false);
    }
  };

  // 处理拖拽
  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    handleFileSelect(e.dataTransfer.files);
  };

  // 移除单行
  const removeRow = (id: string) => {
    setExcelData(prev => prev.filter(row => row.id !== id));
  };

  // 批量下载图片
  const downloadImages = async () => {
    if (excelData.length === 0) return;

    setIsProcessing(true);
    let successCount = 0;
    let failCount = 0;
    let skipCount = 0;

    // 过滤出待处理的行
    const pendingRows = excelData.filter(row => row.status === 'pending');

    // 构造请求数据，过滤掉无效数据
    const imagesToDownload: UploadedImage[] = pendingRows
      .map(row => {
        // 验证商品名称
        if (!row.productName || !row.productName.trim()) {
          return null;
        }

        // 过滤有效的详情图URL
        const validDetailUrls = (row.detailImageUrls || []).filter(url =>
          url && url.trim().length > 0 && url.startsWith('http')
        );

        return {
          productName: row.productName.trim(),
          mainImageUrl: row.mainImageUrl ? row.mainImageUrl.trim() : '',
          detailImageUrls: validDetailUrls,
          category: row.category && row.category.trim() ? row.category.trim() : undefined,
          description: row.description && row.description.trim() ? row.description.trim() : undefined,
        } as UploadedImage | null;
      })
      .filter((item): item is UploadedImage => {
        if (!item) return false;

        // 确保至少有一个有效的URL
        const hasMainImage = item.mainImageUrl && item.mainImageUrl.length > 0;
        const hasDetailImages = item.detailImageUrls && item.detailImageUrls.length > 0;

        return hasMainImage || hasDetailImages;
      });

    console.log('[ExcelUpload] 准备下载的商品数量:', imagesToDownload.length);

    if (imagesToDownload.length === 0) {
      addNotification({
        type: 'warning',
        title: '没有有效的数据',
        message: '没有找到有效的商品名称和图片链接',
      });
      setIsProcessing(false);
      return;
    }

    try {
      console.log('[ExcelUpload] 开始批量下载图片，数量:', imagesToDownload.length);

      // 逐个更新状态为下载中
      for (const row of pendingRows) {
        setExcelData(prev =>
          prev.map(r =>
            r.id === row.id ? { ...r, status: 'downloading' as const } : r
          )
        );
      }

      // 调用后端API批量下载（传递文件名用于创建层级相册）
      const response = await fetch('/api/images/batch-download', {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ 
          images: imagesToDownload,
          parentAlbumName: excelFileNameRef.current, // 使用 ref 获取文件名（确保同步访问）
        }),
      });

      const result = await response.json();

      console.log('[ExcelUpload] 批量下载响应:', result);

      if (result.success && result.data) {
        // 创建一个映射来跟踪每个商品的成功/失败/跳过状态
        const productStatus = new Map<string, { success: number; fail: number; skipped: number; hasError: boolean }>();
        
        // 初始化所有商品的状态
        pendingRows.forEach(row => {
          productStatus.set(row.id, { success: 0, fail: 0, skipped: 0, hasError: false });
        });
        
        // 记录每个商品的总图片数（1张主图 + N张详情图）
        const productTotalCounts = new Map<string, number>();
        excelData.forEach(row => {
          productTotalCounts.set(row.id, 1 + row.detailImageUrls.length);
        });

        // 记录每个商品已下载成功的图片数
        const productSuccessCounts = new Map<string, number>();

        // 处理每个图片的响应
        result.data.forEach((item: { originalUrl: string; success: boolean; skipped?: boolean; error?: string; imageId?: string }) => {
          // 根据originalUrl找到对应的商品
          let matchedRowId: string | null = null;

          // 首先尝试匹配主图
          for (const row of excelData) {
            if (row.mainImageUrl === item.originalUrl) {
              matchedRowId = row.id;
              break;
            }
          }

          // 如果没匹配到主图，尝试匹配详情图
          if (!matchedRowId) {
            for (const row of excelData) {
              if (row.detailImageUrls.includes(item.originalUrl)) {
                matchedRowId = row.id;
                break;
              }
            }
          }

          if (matchedRowId) {
            const status = productStatus.get(matchedRowId);
            if (!status) return;

            if (item.success) {
              status.success++;
              successCount++;

              // 更新该商品已下载成功的图片数
              const currentSuccessCount = productSuccessCounts.get(matchedRowId) || 0;
              productSuccessCounts.set(matchedRowId, currentSuccessCount + 1);

              // 检查该商品的所有图片是否都已下载完成
              const totalCount = productTotalCounts.get(matchedRowId) || 0;
              if (currentSuccessCount + 1 >= totalCount) {
                // 所有图片都下载完成，显示一次成功通知
                const row = excelData.find(r => r.id === matchedRowId);
                if (row) {
                  addNotification({
                    type: 'success',
                    title: '图片下载成功',
                    message: `商品「${row.productName}」下载成功（主图 + ${row.detailImageUrls.length}张详情图）`,
                  });
                }
              }
            } else if (item.skipped || (item.error && item.error.includes('已存在'))) {
              // 图片已存在，跳过
              status.skipped++;
            } else {
              // 真正的失败
              status.fail++;
              status.hasError = true;
              failCount++;
            }
            productStatus.set(matchedRowId, status);
          }
        });
        
        // 更新每个商品的状态
        setExcelData(prev => {
          const updatedRows = prev.map(row => {
            const status = productStatus.get(row.id);
            if (!status) return row;

            // 如果全部跳过，显示"已存在"
            if (status.success === 0 && status.skipped > 0 && status.fail === 0) {
              return {
                ...row,
                status: 'success' as const,
                error: `图片已存在（跳过${status.skipped}张）`,
              };
            }
            
            // 如果至少有一张成功，则标记为成功（即使部分失败）
            const hasSuccess = status.success > 0;
            const newStatus: ExcelRow['status'] = hasSuccess ? 'success' : 'error';
            
            // 构建错误消息
            let errorMsg: string | undefined;
            if (status.hasError) {
              errorMsg = `部分失败（成功${status.success}张，失败${status.fail}张，跳过${status.skipped}张）`;
            } else if (status.skipped > 0) {
              errorMsg = `成功${status.success}张，跳过${status.skipped}张`;
            }

            return {
              ...row,
              status: newStatus,
              error: errorMsg,
            };
          });

          return updatedRows;
        });
      } else {
        // 整体失败
        setExcelData(prev =>
          prev.map(row => ({
            ...row,
            status: 'error' as const,
            error: result.error || '下载失败',
          }))
        );
        failCount = pendingRows.length;
      }
    } catch (error) {
      console.error('[ExcelUpload] 批量下载失败:', error);
      const errorMessage = error instanceof Error ? error.message : '网络错误';
      setExcelData(prev =>
        prev.map(row => ({
          ...row,
          status: 'error' as const,
          error: errorMessage,
        }))
      );
      failCount = pendingRows.length;
    }

    setIsProcessing(false);

    // 如果有成功的或跳过的，显示通知
    if (successCount > 0 || skipCount > 0) {
      let message = '';
      if (successCount > 0) {
        message = `成功下载 ${successCount} 张图片`;
      }
      if (skipCount > 0) {
        message += message ? `，` : '';
        message += `跳过 ${skipCount} 张（图片已存在）`;
      }
      if (failCount > 0) {
        message += `，${failCount} 张失败`;
      }
      
      addNotification({
        type: 'upload',
        title: '图片导入完成',
        message,
      });

      // 延迟关闭并刷新
      setTimeout(() => {
        setExcelData([]);
        onOpenChange(false);
        onUploadSuccess();
      }, 1500);
    } else {
      addNotification({
        type: 'warning',
        title: '下载失败',
        message: `所有图片下载失败，请检查图片URL是否有效`,
      });
    }
  };

  // 关闭时清理
  const handleClose = () => {
    if (!isProcessing) {
      setExcelData([]);
      onOpenChange(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="sm:max-w-[800px] max-h-[90vh] overflow-hidden flex flex-col">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <FileSpreadsheet className="w-5 h-5" />
            Excel批量导入图片
          </DialogTitle>
        </DialogHeader>

        <div className="flex-1 flex flex-col min-h-0 overflow-hidden">
          {excelData.length === 0 ? (
            // 上传区域
            <div
              className={cn(
                'flex-1 border-2 border-dashed rounded-xl p-8 text-center transition-all cursor-pointer flex flex-col items-center justify-center',
                isDragging
                  ? 'border-violet-500 bg-violet-50'
                  : 'border-slate-200 hover:border-violet-300 hover:bg-slate-50'
              )}
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onDrop={handleDrop}
              onClick={() => fileInputRef.current?.click()}
            >
              <input
                ref={fileInputRef}
                type="file"
                accept=".xlsx,.xls,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel"
                className="hidden"
                onChange={e => handleFileSelect(e.target.files)}
              />
              <div className="flex flex-col items-center gap-3">
                <div className="w-16 h-16 rounded-full bg-violet-100 flex items-center justify-center">
                  <FileSpreadsheet className="w-8 h-8 text-violet-600" />
                </div>
                <div>
                  <p className="text-lg font-medium text-slate-700">
                    拖拽Excel文件到这里
                  </p>
                  <p className="text-sm text-slate-500 mt-1">
                    或点击选择文件
                  </p>
                </div>
                <p className="text-xs text-slate-400">
                  支持 .xlsx、.xls 格式
                </p>
                <div className="mt-4 p-4 bg-slate-50 rounded-lg text-left max-w-md">
                  <p className="text-xs font-medium text-slate-700 mb-2">Excel文件格式要求：</p>
                  <ul className="text-xs text-slate-500 space-y-1">
                    <li>• 列1：商品名称</li>
                    <li>• 列2：价格</li>
                    <li>• 列3：商品详情（主图URL，1张）</li>
                    <li>• 列4及以后：图片地址（详情图URL，每列1张，可能多列）</li>
                    <li>• 系统会自动识别主图和详情图，并通过商品名称进行关联</li>
                  </ul>
                </div>
              </div>
            </div>
          ) : (
            // 数据预览区域
            <div className="flex-1 flex flex-col min-h-0">
              {/* 数据列表 */}
              <div className="flex-1 overflow-y-auto space-y-2 p-4 bg-slate-50 rounded-lg">
                {excelData.map(row => (
                  <div
                    key={row.id}
                    className="flex items-center gap-3 p-3 bg-white rounded-lg shadow-sm"
                  >
                    {/* 缩略图 */}
                    <div className="w-12 h-12 rounded-lg bg-slate-200 flex items-center justify-center overflow-hidden flex-shrink-0">
                      {row.mainImageUrl && (
                        <img
                          src={row.mainImageUrl}
                          alt={row.productName}
                          className="w-full h-full object-cover"
                          onError={e => {
                            e.currentTarget.style.display = 'none';
                          }}
                        />
                      )}
                    </div>

                    {/* 商品信息 */}
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-slate-700 truncate">
                        {row.productName}
                      </p>
                      <p className="text-xs text-slate-400">
                        主图 {row.mainImageUrl ? '✓' : '✗'} · 详情图 {row.detailImageUrls.length > 0 ? `${row.detailImageUrls.length}张` : '✗'}
                      </p>
                      {row.category && (
                        <p className="text-xs text-violet-600">
                          分类: {row.category}
                        </p>
                      )}

                      {/* 状态 */}
                      {row.status === 'downloading' && (
                        <p className="text-xs text-violet-600 flex items-center gap-1 mt-1">
                          <Loader2 className="w-3 h-3 animate-spin" />
                          下载中...
                        </p>
                      )}
                      {row.status === 'success' && (
                        <p className="text-xs text-green-600 flex items-center gap-1 mt-1">
                          <CheckCircle2 className="w-3 h-3" />
                          下载成功（主图 + {row.detailImageUrls.length}张详情图）
                        </p>
                      )}
                      {row.status === 'error' && !row.error?.includes('已存在') && (
                        <p className="text-xs text-red-500 flex items-center gap-1 mt-1">
                          <AlertCircle className="w-3 h-3" />
                          {row.error || '下载失败'}
                        </p>
                      )}
                      {row.status === 'error' && row.error?.includes('已存在') && (
                        <p className="text-xs text-amber-500 flex items-center gap-1 mt-1">
                          <AlertCircle className="w-3 h-3" />
                          {row.error}
                        </p>
                      )}
                    </div>

                    {/* 删除按钮 */}
                    <button
                      onClick={e => {
                        e.stopPropagation();
                        removeRow(row.id);
                      }}
                      className="p-1.5 hover:bg-slate-100 rounded-lg transition-colors"
                      disabled={isProcessing}
                    >
                      <X className="w-4 h-4 text-slate-400" />
                    </button>
                  </div>
                ))}
              </div>

              {/* 操作按钮 */}
              <div className="flex justify-between items-center pt-4">
                <Button
                  variant="outline"
                  onClick={() => setExcelData([])}
                  disabled={isProcessing}
                >
                  重新选择文件
                </Button>
                <Button
                  onClick={downloadImages}
                  disabled={isProcessing}
                  className="bg-violet-600 hover:bg-violet-700"
                >
                  {isProcessing ? (
                    <>
                      <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                      下载中...
                    </>
                  ) : (
                    <>
                      <Download className="w-4 h-4 mr-2" />
                      开始下载 ({excelData.reduce((acc, row) => acc + 1 + (row.detailImageUrls?.length || 0), 0)}张图片)
                    </>
                  )}
                </Button>
              </div>
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
