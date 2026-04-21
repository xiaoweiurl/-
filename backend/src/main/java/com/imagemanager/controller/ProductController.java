package com.imagemanager.controller;

import com.imagemanager.dto.ApiResponse;
import com.imagemanager.dto.PageResponse;
import com.imagemanager.entity.Image;
import com.imagemanager.entity.Product;
import com.imagemanager.repository.ImageRepository;
import com.imagemanager.repository.ProductRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 商品管理控制器
 *
 * @author Image Manager Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/products")
@Tag(name = "商品管理", description = "商品查询、商品图片查询等操作")
public class ProductController {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ImageRepository imageRepository;

    /**
     * 获取商品主图列表（用于全部图片展示）
     */
    @GetMapping("/main-images")
    @Operation(summary = "获取商品主图列表", description = "获取所有商品的主图，用于在全部图片列表中展示")
    public ApiResponse<PageResponse<Image>> getMainImages(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") Integer pageSize,
            @Parameter(description = "分类筛选") @RequestParam(required = false) String category,
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword) {
        log.info("获取商品主图列表，页码: {}, 每页: {}, 分类: {}, 关键词: {}", page, pageSize, category, keyword);

        PageRequest pageRequest = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        // 构建查询条件
        Page<Image> result;

        if (keyword != null && !keyword.isEmpty()) {
            // 按商品名称搜索
            List<Product> products = productRepository.searchByName("user-1", keyword);
            List<String> productIds = products.stream().map(Product::getId).collect(Collectors.toList());

            if (productIds.isEmpty()) {
                result = Page.empty(pageRequest);
            } else {
                result = imageRepository.findByProductIdInAndIsMainImageAndDeleted(
                    productIds, true, false, pageRequest
                );
            }
        } else if (category != null && !category.isEmpty()) {
            // 按分类筛选
            List<Product> products = productRepository.findByUserIdAndCategory("user-1", category);
            List<String> productIds = products.stream().map(Product::getId).collect(Collectors.toList());

            if (productIds.isEmpty()) {
                result = Page.empty(pageRequest);
            } else {
                result = imageRepository.findByProductIdInAndIsMainImageAndDeleted(
                    productIds, true, false, pageRequest
                );
            }
        } else {
            // 获取所有主图
            result = imageRepository.findByIsMainImageAndDeleted(true, false, pageRequest);
        }

        PageResponse<Image> response = PageResponse.of(
            result.getContent(),
            result.getTotalElements(),
            result.getNumber() + 1,
            result.getSize()
        );

        return ApiResponse.success(response);
    }

    /**
     * 获取商品详情（包含所有图片）
     */
    @GetMapping("/{productId}")
    @Operation(summary = "获取商品详情", description = "根据商品ID获取商品信息和所有图片")
    public ApiResponse<ProductDetailResponse> getProductDetail(
            @Parameter(description = "商品ID") @PathVariable String productId) {
        log.info("获取商品详情，商品ID: {}", productId);

        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return ApiResponse.error("商品不存在");
        }

        // 获取商品的所有图片
        List<Image> images = imageRepository.findByProductIdAndDeleted(productId, false);

        ProductDetailResponse response = new ProductDetailResponse();
        response.setProduct(product);
        response.setImages(images);

        return ApiResponse.success(response);
    }

    /**
     * 获取商品的所有图片
     */
    @GetMapping("/{productId}/images")
    @Operation(summary = "获取商品所有图片", description = "根据商品ID获取该商品的所有图片")
    public ApiResponse<List<Image>> getProductImages(
            @Parameter(description = "商品ID") @PathVariable String productId) {
        log.info("获取商品所有图片，商品ID: {}", productId);

        List<Image> images = imageRepository.findByProductIdAndDeletedOrderByDisplayOrderAsc(productId, false);

        return ApiResponse.success(images);
    }

    /**
     * 获取商品列表
     */
    @GetMapping
    @Operation(summary = "获取商品列表", description = "获取所有商品列表")
    public ApiResponse<List<Product>> getProducts(
            @Parameter(description = "分类筛选") @RequestParam(required = false) String category) {
        log.info("获取商品列表，分类: {}", category);

        List<Product> products;
        if (category != null && !category.isEmpty()) {
            products = productRepository.findByUserIdAndCategory("user-1", category);
        } else {
            products = productRepository.findAllByUserIdOrderByCreatedAtDesc("user-1");
        }

        return ApiResponse.success(products);
    }

    /**
     * 商品详情响应DTO
     */
    public static class ProductDetailResponse {
        private Product product;
        private List<Image> images;

        public Product getProduct() {
            return product;
        }

        public void setProduct(Product product) {
            this.product = product;
        }

        public List<Image> getImages() {
            return images;
        }

        public void setImages(List<Image> images) {
            this.images = images;
        }
    }
}
