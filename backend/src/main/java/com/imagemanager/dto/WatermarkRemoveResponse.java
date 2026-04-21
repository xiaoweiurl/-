package com.imagemanager.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 去水印响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "去水印响应")
public class WatermarkRemoveResponse {
    
    @Schema(description = "是否成功", example = "true")
    private boolean success;
    
    @Schema(description = "去水印后的图片URL", example = "https://example.com/image-no-watermark.jpg")
    private String imageUrl;
    
    @Schema(description = "错误信息（如果失败）", example = "去水印失败")
    private String error;
    
    @Schema(description = "处理时间（毫秒）", example = "1500")
    private Long processingTime;
    
    public static WatermarkRemoveResponse success(String imageUrl, Long processingTime) {
        return WatermarkRemoveResponse.builder()
                .success(true)
                .imageUrl(imageUrl)
                .processingTime(processingTime)
                .build();
    }
    
    public static WatermarkRemoveResponse error(String error) {
        return WatermarkRemoveResponse.builder()
                .success(false)
                .error(error)
                .build();
    }
}
