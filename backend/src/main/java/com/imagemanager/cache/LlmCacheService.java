package com.imagemanager.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM 两级缓存服务（基于 Redis，按用户 ID 物理隔离）
 *
 * 缓存层级：
 *   L1  - SQL 生成缓存：相同问题→相同SQL，避免重复调用 LLM 生成 SQL（5分钟TTL）
 *   L1.5 - RAG 检索结果缓存：相同问题→相同RAG结果，避免重复向量检索（5分钟TTL）
 *   L2  - DB 查询结果缓存：相同SQL→相同结果，避免重复查询数据库（2分钟TTL）
 *
 * 隔离策略：
 *   - 所有缓存 Key 拼接用户唯一标识 userId，不同用户缓存条目物理隔离
 *   - 支持按用户批量清理缓存（权限变更、登出场景）
 *   - Redis 不可用时自动降级为本地 ConcurrentHashMap（线程安全）
 *
 * Key 格式：
 *   L1:   llm:cache:sql:{userId}:{md5(question)}
 *   L1.5: llm:cache:rag:{userId}:{md5(query)}
 *   L2:   llm:cache:db:{userId}:{md5(sql)}
 *   用户索引: llm:cache:user:{userId} (Set，存储该用户所有缓存Key)
 */
@Slf4j
@Service
public class LlmCacheService {

    private static final String SQL_CACHE_PREFIX = "llm:cache:sql:";
    private static final String DB_CACHE_PREFIX = "llm:cache:db:";
    private static final String USER_INDEX_PREFIX = "llm:cache:user:";

    /** L1 缓存 TTL：5 分钟（SQL 生成结果变化频率低） */
    private static final Duration SQL_CACHE_TTL = Duration.ofMinutes(5);
    /** L2 缓存 TTL：2 分钟（DB 查询结果，数据可能随时变化） */
    private static final Duration DB_CACHE_TTL = Duration.ofMinutes(2);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    @Qualifier("redisTemplate")
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 本地降级缓存（Redis 不可用时使用，线程安全）
     * 结构：userId -> (cacheKey -> {value, expireAt})
     */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, CacheEntry>> localCache = new ConcurrentHashMap<>();

    // ======================== L1: SQL 生成缓存 ========================

    /**
     * 获取 SQL 生成缓存
     * @param userId 用户ID
     * @param question 用户问题
     * @return 缓存的 SQL，不存在返回 null
     */
    public String getCachedSql(String userId, String question) {
        String key = buildSqlCacheKey(userId, question);
        return get(key, String.class);
    }

    /**
     * 写入 SQL 生成缓存
     */
    public void putCachedSql(String userId, String question, String sql) {
        String key = buildSqlCacheKey(userId, question);
        put(key, sql, SQL_CACHE_TTL, userId);
    }

    // ======================== L1.5: RAG 检索结果缓存 ========================

    /**
     * 获取 RAG 检索结果缓存
     * @param userId 用户ID
     * @param query 用户问题
     * @return 缓存的检索结果列表，不存在返回 null
     */
    public List<Map<String, Object>> getCachedRagResult(String userId, String query) {
        String key = buildRagCacheKey(userId, query);
        String json = get(key, String.class);
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("[LlmCache] RAG结果反序列化失败, key={}", key);
            return null;
        }
    }

    /**
     * 写入 RAG 检索结果缓存
     */
    public void putCachedRagResult(String userId, String query, List<Map<String, Object>> results) {
        String key = buildRagCacheKey(userId, query);
        try {
            String json = objectMapper.writeValueAsString(results);
            put(key, json, SQL_CACHE_TTL, userId);
        } catch (Exception e) {
            log.warn("[LlmCache] RAG结果序列化失败: {}", e.getMessage());
        }
    }

    private String buildRagCacheKey(String userId, String query) {
        return "llm:cache:rag:" + safeUserId(userId) + ":" + md5(query);
    }

    // ======================== L2: DB 查询结果缓存 ========================

    /**
     * 获取 DB 查询结果缓存
     * @param userId 用户ID
     * @param sql 查询SQL
     * @return 缓存的查询结果列表，不存在返回 null
     */
    public List<Map<String, Object>> getCachedDbResult(String userId, String sql) {
        String key = buildDbCacheKey(userId, sql);
        String json = get(key, String.class);
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("[LlmCache] DB结果反序列化失败, key={}", key);
            return null;
        }
    }

    /**
     * 写入 DB 查询结果缓存
     */
    public void putCachedDbResult(String userId, String sql, List<Map<String, Object>> rows) {
        String key = buildDbCacheKey(userId, sql);
        try {
            String json = objectMapper.writeValueAsString(rows);
            put(key, json, DB_CACHE_TTL, userId);
        } catch (Exception e) {
            log.warn("[LlmCache] DB结果序列化失败: {}", e.getMessage());
        }
    }

    // ======================== 用户级批量清理 ========================

    /**
     * 清理指定用户的所有缓存（登出、权限变更时调用）
     */
    public void clearUserCache(String userId) {
        if (userId == null || userId.isEmpty()) return;
        log.info("[LlmCache] 清理用户 {} 的所有缓存", userId);

        if (isRedisAvailable()) {
            try {
                String indexKey = USER_INDEX_PREFIX + userId;
                Set<Object> keys = redisTemplate.opsForSet().members(indexKey);
                if (keys != null) {
                    for (Object k : keys) {
                        redisTemplate.delete((String) k);
                    }
                }
                redisTemplate.delete(indexKey);
                log.info("[LlmCache] Redis 清理用户 {} 共 {} 个缓存Key", userId, keys != null ? keys.size() : 0);
            } catch (Exception e) {
                log.warn("[LlmCache] Redis 批量清理失败，降级到本地: {}", e.getMessage());
            }
        }

        // 本地缓存清理
        ConcurrentHashMap<String, CacheEntry> userCache = localCache.get(userId);
        if (userCache != null) {
            int count = userCache.size();
            userCache.clear();
            localCache.remove(userId);
            log.info("[LlmCache] 本地缓存清理用户 {} 共 {} 个条目", userId, count);
        }
    }

    // ======================== 内部方法 ========================

    private boolean isRedisAvailable() {
        return redisTemplate != null;
    }

    private String buildSqlCacheKey(String userId, String question) {
        return SQL_CACHE_PREFIX + safeUserId(userId) + ":" + md5(question);
    }

    private String buildDbCacheKey(String userId, String sql) {
        return DB_CACHE_PREFIX + safeUserId(userId) + ":" + md5(sql);
    }

    private String safeUserId(String userId) {
        if (userId == null || userId.isEmpty()) return "anonymous";
        return userId.replaceAll("[^a-zA-Z0-9_-]", "");
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T get(String key, Class<T> type) {
        // 优先 Redis
        if (isRedisAvailable()) {
            try {
                Object val = redisTemplate.opsForValue().get(key);
                if (val != null) {
                    log.debug("[LlmCache] Redis命中: key={}", key);
                    return (T) val;
                }
            } catch (Exception e) {
                log.warn("[LlmCache] Redis读取异常: {}", e.getMessage());
            }
        }
        // 降级本地
        for (ConcurrentHashMap<String, CacheEntry> userCache : localCache.values()) {
            CacheEntry entry = userCache.get(key);
            if (entry != null && !entry.isExpired()) {
                log.debug("[LlmCache] 本地缓存命中: key={}", key);
                return (T) entry.value;
            } else if (entry != null) {
                userCache.remove(key);
            }
        }
        return null;
    }

    private void put(String key, Object value, Duration ttl, String userId) {
        // Redis
        if (isRedisAvailable()) {
            try {
                redisTemplate.opsForValue().set(key, value, ttl);
                // 记录到用户索引 Set，便于批量清理
                String indexKey = USER_INDEX_PREFIX + safeUserId(userId);
                redisTemplate.opsForSet().add(indexKey, key);
                redisTemplate.expire(indexKey, ttl);
                return;
            } catch (Exception e) {
                log.warn("[LlmCache] Redis写入异常，降级到本地: {}", e.getMessage());
            }
        }
        // 本地降级
        String uid = safeUserId(userId);
        localCache.computeIfAbsent(uid, k -> new ConcurrentHashMap<>())
                .put(key, new CacheEntry(value, System.currentTimeMillis() + ttl.toMillis()));
    }

    // ======================== 本地缓存条目 ========================

    private static class CacheEntry {
        final Object value;
        final long expireAt;

        CacheEntry(Object value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }
}
