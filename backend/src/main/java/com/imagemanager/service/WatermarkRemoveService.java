package com.imagemanager.service;

import com.imagemanager.dto.WatermarkRemoveRequest;
import com.imagemanager.dto.WatermarkRemoveResponse;

/**
 * 去水印服务接口
 */
public interface WatermarkRemoveService {
    
    /**
     * 去除图片水印
     * 
     * @param request 去水印请求
     * @return 去水印响应
     */
    WatermarkRemoveResponse removeWatermark(WatermarkRemoveRequest request);
}
