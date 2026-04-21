import { NextRequest, NextResponse } from 'next/server';
import { S3Storage } from 'coze-coding-dev-sdk';

// 初始化 S3 存储
const storage = new S3Storage({
  endpointUrl: process.env.COZE_BUCKET_ENDPOINT_URL,
  bucketName: process.env.COZE_BUCKET_NAME,
  region: 'cn-beijing',
});

// 最大文件大小：5GB
const MAX_FILE_SIZE = 5 * 1024 * 1024 * 1024;

export async function POST(request: NextRequest) {
  try {
    const formData = await request.formData();
    const file = formData.get('file') as File;
    const fileName = formData.get('fileName') as string || file?.name || 'unknown';

    if (!file) {
      return NextResponse.json(
        { success: false, message: '没有上传文件' },
        { status: 400 }
      );
    }

    // 验证文件大小
    if (file.size > MAX_FILE_SIZE) {
      return NextResponse.json(
        { success: false, message: `文件大小超过限制 (最大 ${MAX_FILE_SIZE / (1024 * 1024 * 1024)}GB)` },
        { status: 400 }
      );
    }

    // 读取文件内容
    const arrayBuffer = await file.arrayBuffer();
    const buffer = Buffer.from(arrayBuffer);

    // 生成安全的文件名
    const safeFileName = fileName
      .replace(/[^a-zA-Z0-9._-]/g, '_')
      .substring(0, 200);

    // 生成唯一键名
    const timestamp = Date.now();
    const randomStr = Math.random().toString(36).substring(2, 10);
    const ext = safeFileName.includes('.') ? safeFileName.substring(safeFileName.lastIndexOf('.')) : '';
    const baseName = safeFileName.replace(/\.[^/.]+$/, '');
    const key = `uploads/${timestamp}_${randomStr}_${baseName}${ext}`;

    // 上传文件
    const actualKey = await storage.uploadFile({
      fileContent: buffer,
      fileName: key,
      contentType: file.type || 'application/octet-stream',
    });

    // 生成可访问的 URL
    const url = await storage.generatePresignedUrl({
      key: actualKey,
      expireTime: 86400 * 30, // 30 天有效期
    });

    console.log(`[文件上传] 成功: ${fileName} -> ${actualKey}`);

    return NextResponse.json({
      success: true,
      message: '上传成功',
      data: {
        key: actualKey,
        url: url,
        fileName: fileName,
        size: file.size,
        type: file.type,
      },
    });
  } catch (error: any) {
    console.error('[文件上传] 失败:', error);
    return NextResponse.json(
      { success: false, message: error.message || '上传失败' },
      { status: 500 }
    );
  }
}

// 配置上传大小限制
export const config = {
  api: {
    bodyParser: false,
  },
};
