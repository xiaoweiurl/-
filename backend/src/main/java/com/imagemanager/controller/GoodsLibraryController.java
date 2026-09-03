package com.imagemanager.controller;

import com.imagemanager.dto.ApiResponse;
import com.imagemanager.service.GoodsLibraryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 商品库控制器
 * 文件夹式商品管理：文件夹名 = 货号 + 品名，
 * 四类图片（主图/侧面图/细节/产品图）上传 OSS，备注五字段（卖点/竞品/功能/对应人群/使用场景）
 */
@Slf4j
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
     * 创建商品文件夹（第一层信息：发起人/打样员/品名/货号/客户/订单号，均允许为空）
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> createGoods(
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        return ApiResponse.success("创建成功", goodsLibraryService.createGoods(body, sessionId));
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
