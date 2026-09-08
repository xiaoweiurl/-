import { NextRequest, NextResponse } from 'next/server';
import { backendFetch } from '@/lib/backend-proxy';

/**
 * 预览批量替换主图
 * 获取选中图片所属商品的所有图片，用于用户选择要替换的主图
 */
export async function POST(request: NextRequest) {
    try {
        // 获取所有 cookies 并构建 cookie header
        const cookieStore = request.cookies;
        const sessionId = cookieStore.get('session_id')?.value;
        const cookieHeader = sessionId ? `session_id=${sessionId}` : '';
        
        console.log('[API] 预览批量替换主图 - sessionId:', sessionId ? `${sessionId.substring(0, 8)}...` : 'null');
        
        const body = await request.json();
        const { imageIds } = body;

        if (!imageIds || !Array.isArray(imageIds) || imageIds.length === 0) {
            return NextResponse.json({
                code: 400,
                message: '请选择要操作的图片',
                success: false,
            }, { status: 400 });
        }

        // 先获取这些图片的详情，拿到商品ID
        const imageDetailsResponse = await backendFetch(`/images/by-ids?ids=${imageIds.join(',')}`, {
            method: 'GET',
            headers: sessionId ? { 'X-Session-Id': sessionId } : undefined,
        });
        const imageDetailsResult = await imageDetailsResponse.json();

        if (!imageDetailsResult.success && imageDetailsResult.code !== 200) {
            return NextResponse.json(imageDetailsResult, { status: imageDetailsResponse.status });
        }

        const images = imageDetailsResult.data || [];
        
        // 提取商品ID（去重）
        const productIds = [...new Set(images.map((img: any) => img.productId).filter(Boolean))];

        if (productIds.length === 0) {
            return NextResponse.json({
                code: 200,
                success: true,
                data: [],
            });
        }

        // 获取每个商品的所有图片
        const productGroups: any[] = [];
        
        for (const productId of productIds) {
            // 获取该商品的所有图片
            const productImagesResponse = await backendFetch(`/images/by-product/${productId}`, {
                method: 'GET',
                headers: sessionId ? { 'X-Session-Id': sessionId } : undefined,
            });
            const productImagesResult = await productImagesResponse.json();

            if (productImagesResult.success || productImagesResult.code === 200) {
                const productImages = productImagesResult.data || [];
                
                // 分离主图和详情图，并映射字段名
                const mainImage = productImages.find((img: any) => img.isMainImage === true) || null;
                const detailImages = productImages
                    .filter((img: any) => img.isMainImage !== true)
                    .sort((a: any, b: any) => (a.displayOrder || 999) - (b.displayOrder || 999))
                    .map((img: any) => ({
                        imgId: img.id,  // 后端返回 id，映射为 imgId
                        url: img.url,
                        title: img.title,
                        isMainImage: img.isMainImage,
                        displayOrder: img.displayOrder,
                        productId: img.productId,
                    }));

                productGroups.push({
                    productId,
                    mainImage: mainImage ? {
                        imgId: mainImage.id,
                        url: mainImage.url,
                        title: mainImage.title,
                        isMainImage: mainImage.isMainImage,
                        displayOrder: mainImage.displayOrder,
                        productId: mainImage.productId,
                    } : null,
                    detailImages,
                });
            }
        }

        return NextResponse.json({
            code: 200,
            success: true,
            data: productGroups,
        });
    } catch (error) {
        console.error('[API] 预览批量替换主图失败:', error);
        return NextResponse.json({
            code: 500,
            message: '系统异常，请稍后重试',
            success: false,
        }, { status: 500 });
    }
}
