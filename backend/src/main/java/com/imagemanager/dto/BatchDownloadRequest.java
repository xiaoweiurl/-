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
     * 父相册名称（可选，如 Excel 文件名）
     * 如果设置，分类将在此父相册下创建层级结构，如：父相册=松野湃，分类=鞋袜 -> 松野湃/鞋袜
     */
    private String parentAlbumName;
    
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
         * 分类（用于第三层级相册，如：羽绒服）
         */
        private String category;

        /**
         * 子分类（用于第二层级相册，如：女士专区）
         */
        private String subCategory;

        /**
         * 描述
         */
        private String description;
    }
}
