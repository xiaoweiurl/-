package com.imagemanager.service.impl;

/**
 * 速率限制异常
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
public class RateLimitException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    
    public RateLimitException(String message) {
        super(message);
    }
    
    public RateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
