package com.imagemanager.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 创建通知请求
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "创建通知请求")
public class CreateNotificationRequest {
    
    @Schema(description = "通知类型", example = "upload")
    private String type;
    
    @Schema(description = "通知标题", example = "上传成功")
    private String title;
    
    @Schema(description = "通知内容", example = "图片已成功上传")
    private String content;
    
    @Schema(description = "关联资源ID（如图片ID、相册ID）")
    private String resourceId;
    
    @Schema(description = "附加数据（JSON格式）")
    private String data;
}
