package com.imagemanager.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 统一响应对象
 * 
 * @param <T> 数据类型
 * @author Image Manager Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
public class ApiResponse<T> {
    
    /**
     * 响应码（200成功，其他失败）
     */
    private Integer code;
    
    /**
     * 响应消息
     */
    private String message;
    
    /**
     * 响应数据
     */
    private T data;
    
    /**
     * 时间戳
     */
    private Long timestamp;
    
    /**
     * 兼容前端 success 字段
     */
    private Boolean success;
    
    /**
     * 成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage("操作成功");
        response.setData(data);
        response.setTimestamp(System.currentTimeMillis());
        response.setSuccess(true);
        return response;
    }
    
    /**
     * 成功响应（带消息）
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage(message);
        response.setData(data);
        response.setTimestamp(System.currentTimeMillis());
        response.setSuccess(true);
        return response;
    }
    
    /**
     * 失败响应
     */
    public static <T> ApiResponse<T> error(String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(500);
        response.setMessage(message);
        response.setData(null);
        response.setTimestamp(System.currentTimeMillis());
        response.setSuccess(false);
        return response;
    }
    
    /**
     * 失败响应（带错误码）
     */
    public static <T> ApiResponse<T> error(Integer code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(code);
        response.setMessage(message);
        response.setData(null);
        response.setTimestamp(System.currentTimeMillis());
        response.setSuccess(false);
        return response;
    }
}
