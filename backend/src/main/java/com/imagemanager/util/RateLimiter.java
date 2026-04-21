package com.imagemanager.util;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 速率限制工具
 * 基于令牌桶算法
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Slf4j
public class RateLimiter {
    
    // 限制类型
    public enum LimitType {
        LOGIN(5, 5),           // 登录: 5分钟最多5次
        PASSWORD_CHANGE(3, 60), // 修改密码: 1小时最多3次
        UPLOAD(20, 1),          // 上传: 1分钟最多20次
        DEFAULT(100, 1);        // 默认: 1分钟最多100次
        
        private final int maxRequests;
        private final int windowMinutes;
        
        LimitType(int maxRequests, int windowMinutes) {
            this.maxRequests = maxRequests;
            this.windowMinutes = windowMinutes;
        }
        
        public int getMaxRequests() {
            return maxRequests;
        }
        
        public int getWindowMinutes() {
            return windowMinutes;
        }
    }
    
    // 存储每个客户端的请求记录
    private static final Map<String, RateLimitRecord> records = new ConcurrentHashMap<>();
    
    // 清理过期记录的间隔（毫秒）
    private static final long CLEANUP_INTERVAL = 60 * 1000; // 1分钟
    private static volatile long lastCleanup = System.currentTimeMillis();
    
    /**
     * 检查是否允许请求
     * 
     * @param clientId 客户端标识（IP、用户ID等）
     * @param limitType 限制类型
     * @return true 表示允许，false 表示超出限制
     */
    public static boolean allow(String clientId, LimitType limitType) {
        // 定期清理过期记录
        cleanupIfNeeded();
        
        RateLimitRecord record = records.computeIfAbsent(clientId, k -> new RateLimitRecord());
        
        long now = System.currentTimeMillis();
        long windowMs = limitType.getWindowMinutes() * 60 * 1000L;
        
        // 检查并更新记录
        synchronized (record) {
            // 如果时间窗口已过，重置计数器
            if (now - record.windowStart > windowMs) {
                record.windowStart = now;
                record.count.set(0);
            }
            
            // 检查是否超出限制
            if (record.count.get() >= limitType.getMaxRequests()) {
                log.warn("速率限制触发: clientId={}, type={}, count={}", 
                        clientId, limitType, record.count.get());
                return false;
            }
            
            // 增加计数
            record.count.incrementAndGet();
            record.lastRequest.set(now);
            
            return true;
        }
    }
    
    /**
     * 获取剩余请求次数
     */
    public static int getRemainingRequests(String clientId, LimitType limitType) {
        RateLimitRecord record = records.get(clientId);
        if (record == null) {
            return limitType.getMaxRequests();
        }
        
        long now = System.currentTimeMillis();
        long windowMs = limitType.getWindowMinutes() * 60 * 1000L;
        
        if (now - record.windowStart > windowMs) {
            return limitType.getMaxRequests();
        }
        
        return Math.max(0, limitType.getMaxRequests() - record.count.get());
    }
    
    /**
     * 获取重置时间（秒）
     */
    public static long getResetTime(String clientId, LimitType limitType) {
        RateLimitRecord record = records.get(clientId);
        if (record == null) {
            return 0;
        }
        
        long windowMs = limitType.getWindowMinutes() * 60 * 1000L;
        long elapsed = System.currentTimeMillis() - record.windowStart;
        
        if (elapsed >= windowMs) {
            return 0;
        }
        
        return (windowMs - elapsed) / 1000;
    }
    
    /**
     * 重置限制
     */
    public static void reset(String clientId) {
        records.remove(clientId);
    }
    
    /**
     * 清理过期记录
     */
    private static void cleanupIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup > CLEANUP_INTERVAL) {
            synchronized (RateLimiter.class) {
                if (now - lastCleanup > CLEANUP_INTERVAL) {
                    long cutoff = now - 10 * 60 * 1000; // 10分钟前的记录
                    records.entrySet().removeIf(entry -> 
                            entry.getValue().lastRequest.get() < cutoff);
                    lastCleanup = now;
                    log.debug("清理了过期速率限制记录，当前记录数: {}", records.size());
                }
            }
        }
    }
    
    /**
     * 速率限制记录
     */
    private static class RateLimitRecord {
        long windowStart = System.currentTimeMillis();
        AtomicInteger count = new AtomicInteger(0);
        AtomicLong lastRequest = new AtomicLong(System.currentTimeMillis());
    }
}
