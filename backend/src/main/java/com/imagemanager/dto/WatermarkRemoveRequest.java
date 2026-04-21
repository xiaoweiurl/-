package com.imagemanager.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 去水印请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "去水印请求")
public class WatermarkRemoveRequest {
    
    @Schema(description = "图片ID", example = "123e4567-e89b-12d3-a456-426614174000")
    private String imageId;
    
    @Schema(description = "图片URL", example = "https://example.com/image.jpg")
    private String imageUrl;
    
    @Schema(description = "去水印提示词（可选）", example = "请去除图片右下角的水印")
    private String prompt;
    
    @Schema(description = "水印区域坐标X（可选）", example = "100")
    private Integer x;
    
    @Schema(description = "水印区域坐标Y（可选）", example = "100")
    private Integer y;
    
    @Schema(description = "水印区域宽度（可选）", example = "200")
    private Integer width;
    
    @Schema(description = "水印区域高度（可选）", example = "100")
    private Integer height;
}
