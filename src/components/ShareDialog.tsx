'use client';

import { useState, useEffect } from 'react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Switch } from '@/components/ui/switch';
import { Copy, Link, Lock, Calendar, Eye, Share2, Trash2 } from 'lucide-react';

interface ShareDialogProps {
  open?: boolean;
  onClose: () => void;
  resourceId: string;
  resourceName: string;
  resourceType: 'album' | 'image';
}

interface ShareLink {
  id: string;
  shareCode: string;
  resourceType: string;
  resourceId: string;
  resourceName: string;
  password: string | null;
  expiresAt: string | null;
  accessCount: number;
  createdAt: string;
}

export default function ShareDialog({
  open: externalOpen,
  onClose,
  resourceId,
  resourceName,
  resourceType,
}: ShareDialogProps) {
  const [internalOpen, setInternalOpen] = useState(false);
  const open = externalOpen ?? internalOpen;
  const [shareLinks, setShareLinks] = useState<ShareLink[]>([]);
  const [loading, setLoading] = useState(false);
  const [creating, setCreating] = useState(false);
  const [showCreateForm, setShowCreateForm] = useState(false);

  // 创建表单
  const [hasPassword, setHasPassword] = useState(false);
  const [password, setPassword] = useState('');
  const [hasExpiry, setHasExpiry] = useState(false);
  const [expiryDays, setExpiryDays] = useState('7');

  useEffect(() => {
    if (open) {
      loadShareLinks();
    }
  }, [open, resourceId]);

  const loadShareLinks = async () => {
    setLoading(true);
    try {
      const response = await fetch(
        `/api/share?resourceType=${resourceType}&resourceId=${resourceId}`
      );
      const data = await response.json();
      if (data.shareLinks) {
        setShareLinks(data.shareLinks);
      }
    } catch (error) {
      console.error('Load share links failed:', error);
    } finally {
      setLoading(false);
    }
  };

  const createShareLink = async () => {
    setCreating(true);
    try {
      const body: Record<string, unknown> = {
        resourceType,
        resourceId,
        resourceName,
      };

      if (hasPassword && password) {
        body.password = password;
      }

      if (hasExpiry) {
        body.expireDays = parseInt(expiryDays);
      }

      const response = await fetch('/api/share', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });

      const data = await response.json();
      
      // 获取分享码
      const shareCode = data.shareCode || (data.shareLink?.shareCode);
      
      // 始终使用前端域名生成分享链接，忽略后端返回的 shareUrl
      const shareUrl = `${window.location.origin}/share/${shareCode}`;
      
      if (shareCode) {
        // 自动复制到剪贴板
        navigator.clipboard.writeText(shareUrl);
        
        // 显示成功提示
        alert(`分享链接创建成功！\n\n链接已复制到剪贴板：\n${shareUrl}`);
        
        // 将新创建的链接添加到列表顶部
        const newLink = data.shareLink;
        if (newLink) {
          setShareLinks(prev => [newLink, ...prev]);
        }
        
        setShowCreateForm(false);
        setHasPassword(false);
        setPassword('');
        setHasExpiry(false);
        setExpiryDays('7');
      } else {
        alert(data.error || '创建分享链接失败');
      }
    } catch (error) {
      console.error('Create share link failed:', error);
      alert('创建分享链接失败');
    } finally {
      setCreating(false);
    }
  };

  const deleteShareLink = async (shareCode: string) => {
    if (!confirm('确定要删除这个分享链接吗？')) return;

    try {
      const response = await fetch(`/api/share/${shareCode}`, {
        method: 'DELETE',
      });
      const data = await response.json();
      if (data.success) {
        await loadShareLinks();
      }
    } catch (error) {
      console.error('Delete share link failed:', error);
    }
  };

  const copyShareLink = (shareCode: string) => {
    const shareUrl = `${window.location.origin}/share/${shareCode}`;
    navigator.clipboard.writeText(shareUrl);
    alert('分享链接已复制到剪贴板');
  };

  const formatDate = (dateStr: string | null) => {
    if (!dateStr) return '永久有效';
    return new Date(dateStr).toLocaleString('zh-CN');
  };

  const getShareStatus = (expiresAt: string | null) => {
    if (!expiresAt) return { text: '有效', color: 'text-green-600' };
    const expired = new Date(expiresAt) < new Date();
    return expired
      ? { text: '已过期', color: 'text-red-600' }
      : { text: '有效', color: 'text-green-600' };
  };

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent className="max-w-2xl max-h-[80vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Share2 className="w-5 h-5" />
            分享 - {resourceName}
          </DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          {/* 现有分享链接 */}
          <div>
            <div className="flex items-center justify-between mb-3">
              <h3 className="font-medium">分享链接</h3>
              <Button
                size="sm"
                onClick={() => setShowCreateForm(!showCreateForm)}
              >
                {showCreateForm ? '取消' : '创建新链接'}
              </Button>
            </div>

            {loading ? (
              <div className="text-center py-8 text-gray-500">加载中...</div>
            ) : shareLinks.length === 0 ? (
              <div className="text-center py-8 text-gray-500">
                暂无分享链接，点击上方按钮创建
              </div>
            ) : (
              <div className="space-y-3">
                {shareLinks.map((link) => {
                  const status = getShareStatus(link.expiresAt);
                  return (
                    <div
                      key={link.id}
                      className="p-4 border rounded-lg hover:bg-gray-50"
                    >
                      <div className="flex items-center justify-between mb-2">
                        <div className="flex items-center gap-2">
                          <Link className="w-4 h-4 text-gray-400" />
                          <code className="text-sm bg-gray-100 px-2 py-1 rounded">
                            /share/{link.shareCode}
                          </code>
                        </div>
                        <div className="flex items-center gap-2">
                          <span className={`text-sm ${status.color}`}>
                            {status.text}
                          </span>
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => copyShareLink(link.shareCode)}
                          >
                            <Copy className="w-4 h-4" />
                          </Button>
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => deleteShareLink(link.shareCode)}
                          >
                            <Trash2 className="w-4 h-4 text-red-500" />
                          </Button>
                        </div>
                      </div>

                      <div className="flex items-center gap-4 text-sm text-gray-500">
                        <span className="flex items-center gap-1">
                          <Calendar className="w-3 h-3" />
                          {link.expiresAt ? (
                            <span title={`过期时间: ${new Date(link.expiresAt).toLocaleString('zh-CN')}`}>
                              {new Date(link.expiresAt) < new Date() ? '已过期' : `${Math.ceil((new Date(link.expiresAt).getTime() - Date.now()) / (1000 * 60 * 60 * 24))} 天后过期`}
                            </span>
                          ) : (
                            <span>永久有效</span>
                          )}
                        </span>
                        {link.password && (
                          <span className="flex items-center gap-1">
                            <Lock className="w-3 h-3" />
                            密码保护
                          </span>
                        )}
                        <span className="flex items-center gap-1">
                          <Eye className="w-3 h-3" />
                          {link.accessCount} 次访问
                        </span>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* 创建新链接表单 */}
          {showCreateForm && (
            <div className="border-t pt-4 space-y-4">
              <h3 className="font-medium">创建新分享链接</h3>

              {/* 密码保护 */}
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Lock className="w-4 h-4" />
                  <Label htmlFor="password-toggle">密码保护</Label>
                </div>
                <Switch
                  id="password-toggle"
                  checked={hasPassword}
                  onCheckedChange={setHasPassword}
                />
              </div>

              {hasPassword && (
                <Input
                  placeholder="输入访问密码"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
              )}

              {/* 有效期 */}
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Calendar className="w-4 h-4" />
                    <Label htmlFor="expiry-toggle">设置有效期</Label>
                  </div>
                  <Switch
                    id="expiry-toggle"
                    checked={hasExpiry}
                    onCheckedChange={setHasExpiry}
                  />
                </div>

                {!hasExpiry ? (
                  <p className="text-sm text-gray-500">未设置有效期，链接将永久有效</p>
                ) : (
                  <div className="flex items-center gap-2">
                    <span className="text-sm text-gray-500">有效期：</span>
                    <Select value={expiryDays} onValueChange={setExpiryDays}>
                      <SelectTrigger className="w-32">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="1">1 天</SelectItem>
                        <SelectItem value="7">7 天</SelectItem>
                        <SelectItem value="30">30 天</SelectItem>
                        <SelectItem value="90">90 天</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                )}

              </div>

              <Button
                className="w-full"
                onClick={createShareLink}
                disabled={creating}
              >
                {creating ? '创建中...' : '创建分享链接'}
              </Button>
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
