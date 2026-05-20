'use client';

import { useState, useRef } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Progress } from '@/components/ui/progress';
import {
  Download,
  Upload,
  Save,
  RotateCcw,
  Loader2,
  CheckCircle,
  AlertCircle,
} from 'lucide-react';

export default function BackupRestore() {
  const [backupType, setBackupType] = useState<'full' | 'data'>('data');
  const [loading, setLoading] = useState(false);
  const [progress, setProgress] = useState(0);
  const [status, setStatus] = useState<'idle' | 'success' | 'error'>('idle');
  const [message, setMessage] = useState('');
  const fileInputRef = useRef<HTMLInputElement>(null);

  const createBackup = async () => {
    setLoading(true);
    setProgress(0);
    setStatus('idle');

    try {
      // 模拟进度
      const progressInterval = setInterval(() => {
        setProgress((prev) => Math.min(prev + 10, 90));
      }, 200);

      const response = await fetch('/api/backup', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ backupType }),
      });

      clearInterval(progressInterval);
      setProgress(100);

      const data = await response.json();
      if (data.success) {
        setStatus('success');
        setMessage(`备份创建成功：${data.fileName || 'backup.json'}`);
      } else {
        setStatus('error');
        setMessage(data.error || '备份创建失败');
      }
    } catch (error) {
      console.error('Create backup failed:', error);
      setStatus('error');
      setMessage('备份创建失败');
    } finally {
      setLoading(false);
    }
  };

  const downloadBackup = async () => {
    setLoading(true);
    try {
      const response = await fetch('/api/backup/download');
      if (!response.ok) {
        throw new Error('下载失败');
      }

      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `backup-${new Date().toISOString().slice(0, 10)}.json`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      a.remove();

      setStatus('success');
      setMessage('备份文件下载成功');
    } catch (error) {
      console.error('Download backup failed:', error);
      setStatus('error');
      setMessage('备份下载失败');
    } finally {
      setLoading(false);
    }
  };

  const handleFileSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setLoading(true);
    setProgress(0);
    setStatus('idle');

    try {
      const progressInterval = setInterval(() => {
        setProgress((prev) => Math.min(prev + 5, 80));
      }, 100);

      const formData = new FormData();
      formData.append('file', file);

      const response = await fetch('/api/backup/import', {
        method: 'POST',
        body: formData,
      });

      clearInterval(progressInterval);
      setProgress(100);

      const data = await response.json();
      if (data.success) {
        setStatus('success');
        setMessage(
          `数据导入成功：${data.imagesCount || 0} 张图片，${data.albumsCount || 0} 个相册`
        );
      } else {
        setStatus('error');
        setMessage(data.error || '数据导入失败');
      }
    } catch (error) {
      console.error('Import data failed:', error);
      setStatus('error');
      setMessage('数据导入失败');
    } finally {
      setLoading(false);
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Save className="w-5 h-5" />
          数据备份与恢复
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-6">
        {/* 创建备份 */}
        <div className="space-y-4">
          <h3 className="font-medium">创建备份</h3>
          <div className="flex items-center gap-4">
            <Select
              value={backupType}
              onValueChange={(v) => setBackupType(v as 'full' | 'data')}
            >
              <SelectTrigger className="w-40">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="data">仅数据</SelectItem>
                <SelectItem value="full">完整备份</SelectItem>
              </SelectContent>
            </Select>
            <Button onClick={createBackup} disabled={loading}>
              {loading ? (
                <Loader2 className="w-4 h-4 mr-2 animate-spin" />
              ) : (
                <Download className="w-4 h-4 mr-2" />
              )}
              创建备份
            </Button>
            <Button
              variant="outline"
              onClick={downloadBackup}
              disabled={loading}
            >
              <Download className="w-4 h-4 mr-2" />
              下载备份
            </Button>
          </div>
        </div>

        {/* 导入数据 */}
        <div className="space-y-4">
          <h3 className="font-medium">导入数据</h3>
          <div className="flex items-center gap-4">
            <input
              ref={fileInputRef}
              type="file"
              accept=".json"
              className="hidden"
              onChange={handleFileSelect}
            />
            <Button
              variant="outline"
              onClick={() => fileInputRef.current?.click()}
              disabled={loading}
            >
              {loading ? (
                <Loader2 className="w-4 h-4 mr-2 animate-spin" />
              ) : (
                <Upload className="w-4 h-4 mr-2" />
              )}
              选择备份文件
            </Button>
            <span className="text-sm text-gray-500">支持 .json 格式</span>
          </div>
        </div>

        {/* 进度和状态 */}
        {loading && progress > 0 && (
          <div className="space-y-2">
            <Progress value={progress} />
            <p className="text-sm text-gray-500 text-center">处理中...</p>
          </div>
        )}

        {status !== 'idle' && !loading && (
          <div
            className={`flex items-center gap-2 p-3 rounded-lg ${
              status === 'success'
                ? 'bg-green-50 text-green-700'
                : 'bg-red-50 text-red-700'
            }`}
          >
            {status === 'success' ? (
              <CheckCircle className="w-4 h-4" />
            ) : (
              <AlertCircle className="w-4 h-4" />
            )}
            <span className="text-sm">{message}</span>
          </div>
        )}

        {/* 说明 */}
        <div className="p-4 bg-gray-50 rounded-lg text-sm text-gray-600 space-y-2">
          <p className="font-medium">说明：</p>
          <ul className="list-disc list-inside space-y-1">
            <li>仅数据：备份相册、图片元数据（不含实际文件）</li>
            <li>完整备份：包含所有数据和设置</li>
            <li>导入数据会与现有数据合并，不会删除现有数据</li>
            <li>建议定期备份重要数据</li>
          </ul>
        </div>
      </CardContent>
    </Card>
  );
}
