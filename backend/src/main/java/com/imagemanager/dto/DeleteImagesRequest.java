package com.imagemanager.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * 删除图片请求
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteImagesRequest {
    
    /**
     * 图片ID列表
     */
    private List<String> imageIds;
    
    /**
     * 是否永久删除
     */
    private Boolean permanent;
}
