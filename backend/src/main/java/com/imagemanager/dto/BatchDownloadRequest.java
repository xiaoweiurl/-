package com.imagemanager.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * 批量下载图片请求参数
 *
 * @author Image Manager Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchDownloadRequest {

    /**
     * 待下载的图片信息列表
     */
    @NotEmpty(message = "图片列表不能为空")
    @Valid
    private List<ImageToDownload> images;

    /**
     * 待下载的图片信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageToDownload {

        /**
         * 商品名称（作为图片标题）
         */
        private String productName;

        /**
         * 主图URL（商品详情）
         */
        private String mainImageUrl;

        /**
         * 详情图URL列表（图片地址，可能多张）
         */
        private java.util.List<String> detailImageUrls;

        /**
         * 分类
         */
        private String category;

        /**
         * 描述
         */
        private String description;
    }
}
