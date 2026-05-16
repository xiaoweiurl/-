"use client";

import { useState, useEffect } from "react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { RefreshCw, Check, ImageIcon, Loader2 } from "lucide-react";
import { toast } from "sonner";

interface ImageData {
  imgId: string;
  url: string;
  title?: string;
  isMainImage: boolean;
  displayOrder: number;
  productId: string;
}

interface ProductGroup {
  productId: string;
  mainImage: ImageData | null;
  detailImages: ImageData[];
  selectedImageId: string | null; // 用户选择要设为主图的图片ID
}

interface BatchReplaceMainImageDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  selectedImageIds: string[];
  onSuccess: () => void;
}

export function BatchReplaceMainImageDialog({
  open,
  onOpenChange,
  selectedImageIds,
  onSuccess,
}: BatchReplaceMainImageDialogProps) {
  const [productGroups, setProductGroups] = useState<ProductGroup[]>([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  // 加载选中图片所属的商品及其所有图片
  useEffect(() => {
    if (open && selectedImageIds.length > 0) {
      loadProductImages();
    }
  }, [open, selectedImageIds]);

  const loadProductImages = async () => {
    setLoading(true);
    try {
      // 获取选中图片的商品ID
      const response = await fetch("/api/images/batch-replace-main-image/preview", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ imageIds: selectedImageIds }),
      });
      const result = await response.json();
      
      if (result.success || result.code === 200) {
        const groups: ProductGroup[] = result.data?.map((group: any) => ({
          productId: group.productId,
          mainImage: group.mainImage,
          detailImages: group.detailImages || [],
          // 默认选中 displayOrder=1 的详情图，如果没有则保持当前主图不变（null）
          selectedImageId: group.detailImages?.find((img: ImageData) => img.displayOrder === 1)?.imgId || null,
        })) || [];
        setProductGroups(groups);
      } else {
        toast.error(result.message || "加载商品图片失败");
      }
    } catch (error) {
      console.error("加载商品图片失败:", error);
      toast.error("加载商品图片失败");
    } finally {
      setLoading(false);
    }
  };

  // 选择要设为主图的图片（点击已选中的可取消选择）
  const handleSelectImage = (productId: string, imageId: string) => {
    setProductGroups((prev) =>
      prev.map((group) =>
        group.productId === productId
          ? { ...group, selectedImageId: group.selectedImageId === imageId ? null : imageId }
          : group
      )
    );
  };

  // 执行批量替换
  const handleSubmit = async () => {
    // 收集所有选中的图片ID
    const imageIdsToReplace = productGroups
      .filter((group) => group.selectedImageId)
      .map((group) => group.selectedImageId as string);

    // 检查是否有可替换的商品
    const keepUnchangedCount = productGroups.filter((g) => !g.selectedImageId).length;
    
    if (imageIdsToReplace.length === 0) {
      toast.warning("没有可替换的商品（所有商品都保持当前主图不变）");
      return;
    }

    setSubmitting(true);
    try {
      const response = await fetch("/api/images/batch-replace-main-image", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ imageIds: imageIdsToReplace }),
      });
      const result = await response.json();

      if (result.success || result.code === 200) {
        let message = `成功替换 ${imageIdsToReplace.length} 个商品的主图`;
        if (keepUnchangedCount > 0) {
          message += `，${keepUnchangedCount} 个商品保持不变`;
        }
        toast.success(result.data?.message || message);
        onOpenChange(false);
        onSuccess();
      } else {
        toast.error(result.message || "批量替换主图失败");
      }
    } catch (error) {
      console.error("批量替换主图失败:", error);
      toast.error("批量替换主图失败");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-4xl max-h-[80vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <RefreshCw className="h-5 w-5 text-orange-500" />
            批量替换主图
          </DialogTitle>
          <DialogDescription>
            为每个商品选择要设为主图的详情图，默认选中顺序为1的详情图
          </DialogDescription>
        </DialogHeader>

        {loading ? (
          <div className="flex items-center justify-center py-12">
            <Loader2 className="h-8 w-8 animate-spin text-violet-500" />
            <span className="ml-2 text-gray-500">加载中...</span>
          </div>
        ) : productGroups.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-12 text-gray-500">
            <ImageIcon className="h-12 w-12 mb-2 opacity-50" />
            <p>没有可替换的商品</p>
          </div>
        ) : (
          <div className="space-y-6">
            {productGroups.map((group, groupIndex) => (
              <div
                key={group.productId || `product-${groupIndex}`}
                className="border rounded-lg p-4 bg-slate-50/50"
              >
                <div className="flex items-center gap-2 mb-3">
                  <span className="font-medium text-sm">商品ID: {group.productId || '未知'}</span>
                  <Badge variant="outline" className="text-xs">
                    {group.detailImages.length} 张详情图
                  </Badge>
                  {group.selectedImageId === null && (
                    <Badge variant="secondary" className="text-xs bg-gray-200">
                      保持不变
                    </Badge>
                  )}
                </div>

                <div className="grid grid-cols-2 gap-4">
                  {/* 当前主图 */}
                  <div>
                    <p className="text-xs text-gray-500 mb-2">当前主图</p>
                    <div className="relative aspect-square rounded-lg overflow-hidden border bg-white">
                      {group.mainImage ? (
                        <img
                          src={group.mainImage.url}
                          alt="当前主图"
                          className="w-full h-full object-cover"
                        />
                      ) : (
                        <div className="flex items-center justify-center h-full text-gray-400">
                          <ImageIcon className="h-8 w-8" />
                        </div>
                      )}
                      <Badge className="absolute top-2 left-2 bg-violet-500 text-white text-xs">
                        主图
                      </Badge>
                    </div>
                  </div>

                  {/* 详情图列表 */}
                  <div>
                    <p className="text-xs text-gray-500 mb-2">
                      选择新主图
                      <span className="text-orange-500 ml-1">(点击选择/取消)</span>
                    </p>
                    {group.detailImages.length === 0 ? (
                      <div className="flex items-center justify-center h-24 border rounded-lg bg-gray-50 text-gray-400 text-sm">
                        无详情图，保持当前主图不变
                      </div>
                    ) : (
                      <div className="grid grid-cols-3 gap-2">
                        {group.detailImages.map((img, imgIndex) => (
                          <div
                            key={img.imgId || `detail-${groupIndex}-${imgIndex}`}
                            onClick={() => handleSelectImage(group.productId, img.imgId)}
                            className={`
                              relative aspect-square rounded-lg overflow-hidden border-2 cursor-pointer
                              transition-all duration-200 hover:ring-2 hover:ring-violet-300
                              ${group.selectedImageId === img.imgId
                                ? "border-orange-500 ring-2 ring-orange-300"
                                : "border-gray-200"
                              }
                            `}
                          >
                            <img
                              src={img.url}
                              alt={`详情图 ${img.displayOrder}`}
                              className="w-full h-full object-cover"
                            />
                            <Badge
                              variant="secondary"
                              className="absolute bottom-1 left-1 text-xs"
                            >
                              顺序 {img.displayOrder}
                            </Badge>
                            {group.selectedImageId === img.imgId && (
                              <div className="absolute top-1 right-1 bg-orange-500 rounded-full p-0.5">
                                <Check className="h-3 w-3 text-white" />
                              </div>
                            )}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button
            onClick={handleSubmit}
            disabled={submitting || productGroups.length === 0}
            className="bg-orange-500 hover:bg-orange-600"
          >
            {submitting ? (
              <>
                <Loader2 className="h-4 w-4 animate-spin mr-2" />
                替换中...
              </>
            ) : (
              <>
                <RefreshCw className="h-4 w-4 mr-2" />
                确认替换 ({productGroups.filter(g => g.selectedImageId).length} 个商品)
              </>
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
