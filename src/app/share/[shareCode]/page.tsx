'use client';

import { useState, useEffect } from 'react';
import { useParams } from 'next/navigation';
import { Lock, Eye, Calendar, AlertCircle } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import Image from 'next/image';

interface ShareData {
  shareCode: string;
  resourceType: string;
  resourceId: string;
  resourceName: string;
  hasPassword: boolean;
  expiresAt: string | null;
  isExpired: boolean;
  // 图片分享
  images?: Array<{
    id: string;
    title: string;
    url: string;
    thumbnailUrl: string;
    width: number;
    height: number;
  }>;
  // 相册分享
  album?: {
    id: string;
    name: string;
    description: string;
  };
}

export default function SharePage() {
  const params = useParams();
  const shareCode = params.shareCode as string;

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [shareData, setShareData] = useState<ShareData | null>(null);
  const [needPassword, setNeedPassword] = useState(false);
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [selectedImage, setSelectedImage] = useState<string | null>(null);

  useEffect(() => {
    loadShareData();
  }, [shareCode]);

  const loadShareData = async (pwd?: string) => {
    setLoading(true);
    setError(null);

    try {
      const url = new URL(`/api/share/${shareCode}`, window.location.origin);
      if (pwd) {
        url.searchParams.set('password', pwd);
      }

      const response = await fetch(url.toString());
      const data = await response.json();

      if (data.error) {
        if (data.needPassword) {
          setNeedPassword(true);
          setShareData(null);
        } else {
          setError(data.error);
        }
      } else {
        setShareData(data);
        setNeedPassword(false);
      }
    } catch (err) {
      setError('加载分享内容失败');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleSubmitPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    await loadShareData(password);
    setSubmitting(false);
  };

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-violet-600 mx-auto mb-4"></div>
          <p className="text-gray-500">加载中...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="text-center max-w-md p-8 bg-white rounded-xl shadow-lg">
          <AlertCircle className="w-16 h-16 text-red-500 mx-auto mb-4" />
          <h1 className="text-xl font-bold text-gray-900 mb-2">访问失败</h1>
          <p className="text-gray-500">{error}</p>
        </div>
      </div>
    );
  }

  if (needPassword) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="text-center max-w-md p-8 bg-white rounded-xl shadow-lg">
          <Lock className="w-16 h-16 text-violet-600 mx-auto mb-4" />
          <h1 className="text-xl font-bold text-gray-900 mb-2">需要密码</h1>
          <p className="text-gray-500 mb-6">此分享链接需要密码才能访问</p>
          <form onSubmit={handleSubmitPassword}>
            <div className="flex gap-2">
              <Input
                type="password"
                placeholder="请输入访问密码"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="flex-1"
              />
              <Button type="submit" disabled={submitting}>
                {submitting ? '验证中...' : '验证'}
              </Button>
            </div>
          </form>
        </div>
      </div>
    );
  }

  if (!shareData) {
    return null;
  }

  const isExpired = shareData.isExpired || 
    (shareData.expiresAt && new Date(shareData.expiresAt) < new Date());

  if (isExpired) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="text-center max-w-md p-8 bg-white rounded-xl shadow-lg">
          <Calendar className="w-16 h-16 text-gray-400 mx-auto mb-4" />
          <h1 className="text-xl font-bold text-gray-900 mb-2">分享已过期</h1>
          <p className="text-gray-500">此分享链接已过期，请联系分享者获取新的链接</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50">
      {/* 头部 */}
      <header className="bg-white shadow-sm sticky top-0 z-40">
        <div className="max-w-7xl mx-auto px-4 py-4">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-xl font-bold text-gray-900">{shareData.resourceName}</h1>
              <div className="flex items-center gap-4 text-sm text-gray-500 mt-1">
                <span className="flex items-center gap-1">
                  <Eye className="w-4 h-4" />
                  分享内容
                </span>
                {shareData.expiresAt && (
                  <span className="flex items-center gap-1">
                    <Calendar className="w-4 h-4" />
                    {new Date(shareData.expiresAt).toLocaleDateString('zh-CN')} 到期
                  </span>
                )}
              </div>
            </div>
          </div>
        </div>
      </header>

      {/* 内容区 */}
      <main className="max-w-7xl mx-auto px-4 py-8">
        {shareData.resourceType === 'image' && shareData.images && (
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
            {shareData.images.map((image) => (
              <div
                key={image.id}
                className="relative aspect-square rounded-lg overflow-hidden bg-gray-100 cursor-pointer group"
                onClick={() => setSelectedImage(image.url)}
              >
                <Image
                  src={image.thumbnailUrl || image.url}
                  alt={image.title}
                  fill
                  className="object-cover group-hover:scale-105 transition-transform duration-300"
                  unoptimized
                />
                <div className="absolute inset-0 bg-black/0 group-hover:bg-black/20 transition-colors" />
              </div>
            ))}
          </div>
        )}

        {shareData.resourceType === 'album' && shareData.album && (
          <div className="bg-white rounded-xl shadow-lg p-6">
            <h2 className="text-2xl font-bold mb-4">{shareData.album.name}</h2>
            {shareData.album.description && (
              <p className="text-gray-600 mb-6">{shareData.album.description}</p>
            )}
            {shareData.images && shareData.images.length > 0 ? (
              <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
                {shareData.images.map((image) => (
                  <div
                    key={image.id}
                    className="relative aspect-square rounded-lg overflow-hidden bg-gray-100 cursor-pointer group"
                    onClick={() => setSelectedImage(image.url)}
                  >
                    <Image
                      src={image.thumbnailUrl || image.url}
                      alt={image.title}
                      fill
                      className="object-cover group-hover:scale-105 transition-transform duration-300"
                      unoptimized
                    />
                    <div className="absolute inset-0 bg-black/0 group-hover:bg-black/20 transition-colors" />
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-gray-500 text-center py-8">暂无图片</p>
            )}
          </div>
        )}
      </main>

      {/* 图片预览弹窗 */}
      {selectedImage && (
        <div
          className="fixed inset-0 bg-black/90 z-50 flex items-center justify-center"
          onClick={() => setSelectedImage(null)}
        >
          <button
            className="absolute top-4 right-4 text-white text-4xl hover:text-gray-300"
            onClick={() => setSelectedImage(null)}
          >
            ×
          </button>
          <div className="relative max-w-[90vw] max-h-[90vh]">
            <Image
              src={selectedImage}
              alt="Preview"
              width={1200}
              height={800}
              className="object-contain max-w-[90vw] max-h-[90vh]"
              unoptimized
            />
          </div>
        </div>
      )}
    </div>
  );
}
