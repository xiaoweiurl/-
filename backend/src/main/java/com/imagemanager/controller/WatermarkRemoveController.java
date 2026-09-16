package com.imagemanager.controller;

import com.imagemanager.dto.ApiResponse;
import com.imagemanager.dto.WatermarkRemoveRequest;
import com.imagemanager.dto.WatermarkRemoveResponse;
import com.imagemanager.service.WatermarkRemoveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 去水印控制器
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@RestController
@RequestMapping("/images")
@Tag(name = "图片去水印", description = "图片去水印功能")
public class WatermarkRemoveController {
    
    @Autowired
    private WatermarkRemoveService watermarkRemoveService;
    
    /**
     * 去除图片水印
     */
    @PostMapping("/watermark-remove")
    @Operation(summary = "去除图片水印", description = "使用AI或其他方式去除图片水印")
    public ApiResponse<WatermarkRemoveResponse> removeWatermark(
            @Valid @RequestBody WatermarkRemoveRequest request) {
        
        
        WatermarkRemoveResponse response = watermarkRemoveService.removeWatermark(request);
        
        if (response.isSuccess()) {
            return ApiResponse.success(response);
        } else {
            return ApiResponse.error(response.getError());
        }
    }
}
