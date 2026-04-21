package com.imagemanager.service.impl;

/**
 * 弱密码异常
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
public class WeakPasswordException extends RuntimeException {
    
    public WeakPasswordException(String message) {
        super(message);
    }
    
    public WeakPasswordException(String message, Throwable cause) {
        super(message, cause);
    }
}
