package com.imagemanager.controller;

import com.imagemanager.dto.ApiResponse;
import com.imagemanager.service.GoodsLibraryService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品库控制器。
 * <p>
 * 权限：任意<strong>已登录</strong>用户（含钉钉姓名注册的普通 user），不是管理员专属。
 * 未登录由 Spring Security 返回 401；已登录角色不足才会 403（本模块无角色门槛）。
 */
@RestController
@RequestMapping("/goods-library")
public class GoodsLibraryController {

    private final GoodsLibraryService goodsLibraryService;

    public GoodsLibraryController(GoodsLibraryService goodsLibraryService) {
        this.goodsLibraryService = goodsLibraryService;
    }

    /**
     * 获取商品文件夹列表（含主图签名 URL 作封面）
     */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listGoods(
            @RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResponse.success("获取成功", goodsLibraryService.listGoods(keyword));
    }

    /**
     * 创建商品文件夹（multipart：第一层信息字段 + 四类图片一次性上传，均允许为空）
     * 文本字段：initiator/sampler/product_name/goods_no/customer/order_no
     * 图片字段：mainImage/sideImage/detailImage/productImage
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> createGoods(
            @RequestParam Map<String, String> fields,
            @RequestParam(value = "mainImage", required = false) MultipartFile mainImage,
            @RequestParam(value = "sideImage", required = false) MultipartFile sideImage,
            @RequestParam(value = "detailImage", required = false) MultipartFile detailImage,
            @RequestParam(value = "productImage", required = false) MultipartFile productImage,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        Map<String, MultipartFile> images = new HashMap<>(4);
        if (mainImage != null) images.put("main", mainImage);
        if (sideImage != null) images.put("side", sideImage);
        if (detailImage != null) images.put("detail", detailImage);
        if (productImage != null) images.put("product", productImage);
        return ApiResponse.success("创建成功", goodsLibraryService.createGoods(fields, images, sessionId));
    }

    /**
     * 获取商品详情（含四类图片签名 URL）
     */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getGoods(@PathVariable long id) {
        return ApiResponse.success("获取成功", goodsLibraryService.getGoods(id));
    }

    /**
     * 更新商品信息/备注（货号或品名变更时文件夹自动重命名）
     */
    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> updateGoods(
            @PathVariable long id,
            @RequestBody Map<String, String> body) {
        return ApiResponse.success("更新成功", goodsLibraryService.updateGoods(id, body));
    }

    /**
     * 手动补发当前打样员的钉钉工作通知
     */
    @PostMapping("/{id}/sampler-notice/resend")
    public ApiResponse<Map<String, Object>> resendSamplerNotice(@PathVariable long id) {
        return ApiResponse.success("已触发补发", goodsLibraryService.resendSamplerNotice(id));
    }

    /**
     * 删除商品（同步删除 OSS 图片）
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> deleteGoods(@PathVariable long id) {
        goodsLibraryService.deleteGoods(id);
        return ApiResponse.success("删除成功", Map.of("id", String.valueOf(id)));
    }

    /**
     * 上传/替换指定槽位图片（slot: main/side/detail/product）
     */
    @PostMapping("/{id}/images")
    public ApiResponse<Map<String, Object>> uploadImage(
            @PathVariable long id,
            @RequestParam("slot") String slot,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.success("上传成功", goodsLibraryService.uploadImage(id, slot, file));
    }

    /**
     * 删除指定槽位图片
     */
    @DeleteMapping("/{id}/images")
    public ApiResponse<Map<String, Object>> deleteImage(
            @PathVariable long id,
            @RequestParam("slot") String slot) {
        goodsLibraryService.deleteImage(id, slot);
        return ApiResponse.success("删除成功", Map.of("slot", slot));
    }
}
