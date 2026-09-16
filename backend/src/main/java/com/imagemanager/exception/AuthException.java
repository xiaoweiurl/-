package com.imagemanager.exception;

/**
 * 认证异常 - 未登录或会话过期
 * 由 GlobalExceptionHandler 统一捕获并返回 401
 */
public class AuthException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public AuthException(String message) {
        super(message);
    }
}
