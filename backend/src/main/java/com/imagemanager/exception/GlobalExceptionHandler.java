package com.imagemanager.exception;

import com.imagemanager.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 全局异常处理器
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * 判断当前请求是否为 SSE 流式请求
     */
    private boolean isSSERequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        String contentType = request.getContentType();
        return (accept != null && accept.contains("text/event-stream"))
            || (contentType != null && contentType.contains("text/event-stream"));
    }

    /**
     * 以 SSE 事件格式写入错误信息，避免 Content-Type 冲突导致序列化失败
     */
    private void writeSSEError(HttpServletResponse response, int code, String message) {
        try {
            // SSE 响应可能已部分提交（headers 已发送），不能 reset()
            // 直接写入 SSE 格式的错误事件
            String json = "{\"code\":" + code + ",\"message\":\"" + message.replace("\"", "\\\"") + "\"}";
            String sseData = "data: " + json + "\n\n";
            response.getWriter().write(sseData);
            response.getWriter().flush();
        } catch (Exception ex) {
            log.error("写入 SSE 错误响应失败", ex);
        }
    }

    /**
     * 处理认证异常 - 返回 401
     */
    @ExceptionHandler(AuthException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleAuthException(AuthException e, HttpServletRequest request, HttpServletResponse response) {
        log.warn("认证失败：{}", e.getMessage());
        if (isSSERequest(request)) {
            writeSSEError(response, 401, e.getMessage());
            return null;
        }
        return ApiResponse.error(401, e.getMessage());
    }
    
    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleRuntimeException(RuntimeException e, HttpServletRequest request, HttpServletResponse response) {
        log.error("运行时异常：", e);
        if (isSSERequest(request)) {
            writeSSEError(response, 500, e.getMessage() != null ? e.getMessage() : "运行时异常");
            return null;
        }
        return ApiResponse.error(e.getMessage());
    }
    
    /**
     * 处理文件上传大小超限异常
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e, HttpServletRequest request, HttpServletResponse response) {
        log.error("文件大小超限：", e);
        if (isSSERequest(request)) {
            writeSSEError(response, 400, "文件大小超过限制");
            return null;
        }
        return ApiResponse.error("文件大小超过限制");
    }
    
    /**
     * 处理所有未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e, HttpServletRequest request, HttpServletResponse response) {
        log.error("系统异常：", e);
        if (isSSERequest(request)) {
            writeSSEError(response, 500, "系统异常，请稍后重试");
            return null;
        }
        return ApiResponse.error("系统异常，请稍后重试");
    }
}
