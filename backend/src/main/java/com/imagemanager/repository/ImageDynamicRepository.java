package com.imagemanager.repository;

 
 import com.imagemanager.dto.ImageQueryRequest;
import com.imagemanager.dto.PageResponse;
import com.imagemanager.entity.Image;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 动态表图片数据访问 - 宯动态切换表名进行查询和保存
 * 宯用户图片存储在专属表中: images_<userId>
 *  + 全部知识查询需要 UNION 所有用户的表
 *  + 我的知识查询只查询当前用户的表
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ImageDynamicRepository {

    private final EntityManager entityManager;

    /**
     * 获取用户表名
     */
    public String getUserTableName(String userId) {
        // 将 userId 中的特殊字符替换， 如 user-1 -> images_user_1
        String safeUserId = userId.replaceAll("-", "_").replaceAll("[^a-zA-Z0-9_]", "");
        return "images_" + safeUserId;
    }

    /**
     * 检查表是否存在
     */
    public boolean tableExists(String tableName) {
        try {
            String checkSQL = String.format("SELECT to_regclass('%s', 'r')", tableName);
            Query query = entityManager.createNativeQuery(checkSQL);
            Object result = query.getSingleResult();
            return result != null;
        } catch (Exception e) {
            log.warn("检查表是否存在失败: {}", tableName, e.getMessage());
            return false;
        }
    }

    /**
     * 获取所有用户图片表名列表
     */
    public List<String> getAllUserImageTableNames() {
        try {
            String querySQL = "SELECT tablename FROM pg_tables WHERE tablename LIKE 'images_%' AND schemaname = 'public'";
            Query query = entityManager.createNativeQuery(querySQL);
            @SuppressWarnings("unchecked")
            List<String> tables = query.getResultList();
            return tables != null ? tables : new ArrayList<>();
        } catch (Exception e) {
            log.error("获取所有用户图片表失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 保存图片到用户动态表
     */
    @Transactional
    public Image save(Image image, String userId) {
        String tableName = getUserTableName(userId);
        
        try {
            // 确保表存在
            if (!tableExists(tableName)) {
                log.warn("用户图片表不存在: {}", tableName);
                throw new RuntimeException("用户图片表不存在: " + tableName);
            }
            
            if (image.getId() == null || image.getId().isEmpty()) {
                image.setId(UUID.randomUUID().toString());
            }
            
            // 设置用户ID和来源表
            image.setUserId(userId);
            image.setSourceTable(tableName);
            
            LocalDateTime now = LocalDateTime.now();
            if (image.getCreatedAt() == null) {
                image.setCreatedAt(now);
            }
            if (image.getUpdatedAt() == null) {
                image.setUpdatedAt(now);
            }

            String insertSQL = String.format("""
                INSERT INTO %s (id, url, title, original_name, size, width, height, file_type, 
                    album_id, product_id, is_main_image, favorite, view_count, download_count, 
                    tags, deleted, deleted_at, created_at, updated_at, user_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
                ON CONFLICT ON UPDATE 
                SET title = EXCLUDED.title, 
                    original_name = EXCLUDED.original_name, 
                    updated_at = EXCLUDED.updated_at
                """, tableName);
            
            Query query = entityManager.createNativeQuery(insertSQL);
            setQueryParameters(query, image);
            
            query.executeUpdate();
            
            log.info("图片保存成功, 表: {}, id: {}", tableName, image.getId());
            return image;
        } catch (Exception e) {
            log.error("保存图片失败, 表: {}", tableName, e);
            throw new RuntimeException("保存图片失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量保存图片
     */
    @Transactional
    public List<Image> saveBatch(List<Image> images, String userId) {
        String tableName = getUserTableName(userId);
        List<Image> savedImages = new ArrayList<>();
        
        for (Image image : images) {
            try {
                savedImages.add(save(image, userId));
            } catch (Exception e) {
                log.error("批量保存图片失败, id: {}", image.getId(), e);
            }
        }
        
        return savedImages;
    }

    /**
     * 更新图片
     */
    @Transactional
    public Image update(Image image, String userId) {
        String tableName = getUserTableName(userId);
        
        try {
            if (!tableExists(tableName)) {
                throw new RuntimeException("用户图片表不存在: " + tableName);
            }
            
            image.setUpdatedAt(LocalDateTime.now());
            
            String updateSQL = String.format("""
                UPDATE %s SET 
                    url = ?, title = ?, original_name = ?, size = ?, width = ?, height = ?, 
                    file_type = ?, album_id = ?, product_id = ?, is_main_image = ?, 
                    favorite = ?, view_count = ?, download_count = ?, tags = ?::jsonb, 
                    deleted = ?, deleted_at = ?, updated_at = ?
                WHERE id = ?
                """, tableName);
            
            Query query = entityManager.createNativeQuery(updateSQL);
            query.setParameter(1, image.getUrl());
            query.setParameter(2, image.getTitle());
            query.setParameter(3, image.getOriginalName());
            query.setParameter(4, image.getSize());
            query.setParameter(5, image.getWidth());
            query.setParameter(6, image.getHeight());
            query.setParameter(7, image.getFileType());
            query.setParameter(8, image.getAlbumId());
            query.setParameter(9, image.getProductId());
            query.setParameter(10, image.getIsMainImage() != null && image.getIsMainImage());
            query.setParameter(11, image.getFavorite() != null && image.getFavorite());
            query.setParameter(12, image.getViewCount() != null ? image.getViewCount() : 0);
            query.setParameter(13, image.getDownloadCount() != null ? image.getDownloadCount() : 0);
            query.setParameter(14, image.getTags() != null ? image.getTags().toString() : null);
            query.setParameter(15, image.getDeleted() != null && image.getDeleted());
            query.setParameter(16, image.getDeletedAt() != null ? Timestamp.valueOf(image.getDeletedAt()) : null);
            query.setParameter(17, Timestamp.valueOf(LocalDateTime.now()));
            query.setParameter(18, image.getId());
            
            query.executeUpdate();
            
            return image;
        } catch (Exception e) {
            log.error("更新图片失败, 表: {}, id: {}", tableName, image.getId(), e);
            throw new RuntimeException("更新图片失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据ID查询图片
     */
    public Image findById(String imageId, String userId) {
        String tableName = getUserTableName(userId);
        
        try {
            if (!tableExists(tableName)) {
                log.warn("用户图片表不存在: {}", tableName);
                return null;
            }
            
            String querySQL = String.format("SELECT * FROM %s WHERE id = ? AND deleted = false", tableName);
            Query query = entityManager.createNativeQuery(querySQL);
            query.setParameter(1, imageId);
            
            @SuppressWarnings("unchecked")
            List<Object[]> results = query.getResultList();
            if (results.isEmpty()) {
                return null;
            }
            return mapToImage(results.get(0), tableName);
        } catch (Exception e) {
            log.error("查询图片失败, 表: {}, id: {}", tableName, imageId, e);
            return null;
        }
    }

    /**
     * 查询图片（不限 deleted）
     */
    public Image findByIdAny(String imageId, String userId) {
        String tableName = getUserTableName(userId);
        
        try {
            if (!tableExists(tableName)) {
                return null;
            }
            
            String querySQL = String.format("SELECT * FROM %s WHERE id = ?", tableName);
            Query query = entityManager.createNativeQuery(querySQL);
            query.setParameter(1, imageId);
            
            @SuppressWarnings("unchecked")
            List<Object[]> results = query.getResultList();
            if (results.isEmpty()) {
                return null;
            }
            return mapToImage(results.get(0), tableName);
        } catch (Exception e) {
            log.error("查询图片失败, 表: {}, id: {}", tableName, imageId, e);
            return null;
        }
    }

    /**
     * 查询我的知识（当前用户的动态表）
     */
    public PageResponse<Image> queryMyImages(ImageQueryRequest request, String userId) {
        String tableName = getUserTableName(userId);
        
        try {
            if (!tableExists(tableName)) {
                log.warn("用户图片表不存在: {}", tableName);
                return PageResponse.of(new ArrayList<>(), 0L, request.getPage() != null ? request.getPage() : 1, request.getPageSize() != null ? request.getPageSize() : 20);
            }
            
            return queryFromTable(tableName, request);
        } catch (Exception e) {
            log.error("查询我的知识失败, 表: {}", tableName, e);
            return PageResponse.of(new ArrayList<>(), 0L, 1, 20);
        }
    }

    /**
     * 查询全部知识（UNION 所有用户的动态表）
     */
    public PageResponse<Image> queryAllImages(ImageQueryRequest request) {
        try {
            List<String> tableNames = getAllUserImageTableNames();
            
            if (tableNames.isEmpty()) {
                log.warn("没有用户图片表");
                return PageResponse.of(new ArrayList<>(), 0L, request.getPage() != null ? request.getPage() : 1, request.getPageSize() != null ? request.getPageSize() : 20);
            }
            
            return queryFromMultipleTables(tableNames, request);
        } catch (Exception e) {
            log.error("查询全部知识失败", e);
            return PageResponse.of(new ArrayList<>(), 0L, 1, 20);
        }
    }

    /**
     * 查询指定相册的图片
     */
    public PageResponse<Image> queryAlbumImages(String albumId, ImageQueryRequest request, String userId) {
        String tableName = getUserTableName(userId);
        
        try {
            if (!tableExists(tableName)) {
                return PageResponse.of(new ArrayList<>(), 0L, 1, 20);
            }
            
            if (request.getAlbumId() == null) {
                request.setAlbumId(albumId);
            }
            
            return queryFromTable(tableName, request);
        } catch (Exception e) {
            log.error("查询相册图片失败, albumId: {}", albumId, e);
            return PageResponse.of(new ArrayList<>(), 0L, 1, 20);
        }
    }

    /**
     * 查询收藏夹
     */
    public PageResponse<Image> queryFavorites(ImageQueryRequest request, String userId) {
        String tableName = getUserTableName(userId);
        
        try {
            if (!tableExists(tableName)) {
                return PageResponse.of(new ArrayList<>(), 0L, 1, 20);
            }
            
            request.setFavorite(true);
            return queryFromTable(tableName, request);
        } catch (Exception e) {
            log.error("查询收藏夹失败", e);
            return PageResponse.of(new ArrayList<>(), 0L, 1, 20);
        }
    }

    /**
     * 查询回收站
     */
    public PageResponse<Image> queryTrash(ImageQueryRequest request, String userId) {
        String tableName = getUserTableName(userId);
        
        try {
            if (!tableExists(tableName)) {
                return PageResponse.of(new ArrayList<>(), 0L, 1, 20);
            }
            
            request.setIncludeDeleted(true);
            request.setDeleted(true);
            return queryFromTable(tableName, request);
        } catch (Exception e) {
            log.error("查询回收站失败", e);
            return PageResponse.of(new ArrayList<>(), 0L, 1, 20);
        }
    }

    /**
     * 从单张表查询
     */
    private PageResponse<Image> queryFromTable(String tableName, ImageQueryRequest request) {
        try {
            // 构建条件
            StringBuilder whereClause = new StringBuilder();
            Map<Integer, Object> params = new HashMap<>();
            int paramIndex = 1;
            
            // deleted 条件
            if (request.getDeleted() != null && request.getDeleted()) {
                whereClause.append("WHERE deleted = true");
            } else if (request.getIncludeDeleted() == null || !request.getIncludeDeleted()) {
                whereClause.append("WHERE deleted = false");
            } else {
                whereClause.append("WHERE 1=1");
            }
            
            // albumId 条件
            if (request.getAlbumId() != null && !request.getAlbumId().isEmpty()) {
                whereClause.append(" AND album_id = ?");
                params.put(paramIndex, request.getAlbumId());
                paramIndex++;
            }
            
            // favorite 条件
            if (request.getFavorite() != null && request.getFavorite()) {
                whereClause.append(" AND favorite = true");
            }
            
            // onlyMainImage 条件
            if (request.getOnlyMainImage() != null && request.getOnlyMainImage()) {
                whereClause.append(" AND is_main_image = true");
            }
            
            // userId 条件（单表查询时可选）
            if (request.getOnlyMine() != null && request.getOnlyMine()) {
                whereClause.append(" AND user_id = ?");
                params.put(paramIndex, request.getUserId());
                paramIndex++;
            }
            
            // 排序
            String orderBy = "created_at DESC";
            if (request.getSortBy() != null) {
                String sortBy = request.getSortBy();
                String direction = request.getSortOrder() != null && request.getSortOrder().equalsIgnoreCase("asc") ? "ASC" : "DESC";
                orderBy = sortBy + " " + direction;
            }
            
            // 查询总数
            String countSQL = String.format("SELECT COUNT(*) FROM %s %s", tableName, whereClause);
            Query countQuery = entityManager.createNativeQuery(countSQL);
            for (Map.Entry<Integer, Object> entry : params.entrySet()) {
                countQuery.setParameter(entry.getKey(), entry.getValue());
            }
            BigInteger total = (BigInteger) countQuery.getSingleResult();
            
            // 分页
            int page = request.getPage() != null ? request.getPage() : 1;
            int pageSize = request.getPageSize() != null ? request.getPageSize() : 20;
            int offset = (page - 1) * pageSize;
            
            String querySQL = String.format("SELECT * FROM %s %s ORDER BY %s LIMIT %d OFFSET %d", 
                tableName, whereClause, orderBy, pageSize, offset);
            Query query = entityManager.createNativeQuery(querySQL);
            for (Map.Entry<Integer, Object> entry : params.entrySet()) {
                query.setParameter(entry.getKey(), entry.getValue());
            }
            
            @SuppressWarnings("unchecked")
            List<Object[]> results = query.getResultList();
            List<Image> images = new ArrayList<>();
            for (Object[] row : results) {
                images.add(mapToImage(row, tableName));
            }
            
            return PageResponse.of(images, total.longValue(), page, pageSize);
        } catch (Exception e) {
            log.error("单表查询失败, 表: {}", tableName, e);
            return PageResponse.of(new ArrayList<>(), 0L, 1, 20);
        }
    }

    /**
     * 从多张表查询（UNION ALL）
     */
    private PageResponse<Image> queryFromMultipleTables(List<String> tableNames, ImageQueryRequest request) {
        try {
            if (tableNames.isEmpty()) {
                return PageResponse.of(new ArrayList<>(), 0L, 1, 20);
            }
            
            // 构建条件（不包含 userId 过滤）
            StringBuilder whereClause = new StringBuilder("WHERE deleted = false");
            
            if (request.getFavorite() != null && request.getFavorite()) {
                whereClause.append(" AND favorite = true");
            }
            
            if (request.getOnlyMainImage() != null && request.getOnlyMainImage()) {
                whereClause.append(" AND is_main_image = true");
            }
            
            // 排序
            String orderBy = "created_at DESC";
            if (request.getSortBy() != null) {
                String direction = request.getSortOrder() != null && request.getSortOrder().equalsIgnoreCase("asc") ? "ASC" : "DESC";
                orderBy = request.getSortBy() + " " + direction;
            }
            
            // 构建 UNION ALL 查询
            StringBuilder unionSQL = new StringBuilder();
            for (int i = 0; i < tableNames.size(); i++) {
                if (i > 0) {
                    unionSQL.append(" UNION ALL ");
                }
                unionSQL.append(String.format("SELECT * FROM %s %s", tableNames.get(i), whereClause));
            }
            
            // 包装为子查询，用于分页和总数统计
            String wrappedSQL = String.format("SELECT * FROM (%s) AS combined ORDER BY %s", unionSQL, orderBy);
            
            // 查询总数
            String countSQL = String.format("SELECT COUNT(*) FROM (%s) AS combined", unionSQL);
            Query countQuery = entityManager.createNativeQuery(countSQL);
            BigInteger total = (BigInteger) countQuery.getSingleResult();
            
            // 分页
            int page = request.getPage() != null ? request.getPage() : 1;
            int pageSize = request.getPageSize() != null ? request.getPageSize() : 20;
            int offset = (page - 1) * pageSize;
            
            String finalSQL = String.format("%s LIMIT %d OFFSET %d", wrappedSQL, pageSize, offset);
            Query query = entityManager.createNativeQuery(finalSQL);
            
            @SuppressWarnings("unchecked")
            List<Object[]> results = query.getResultList();
            List<Image> images = new ArrayList<>();
            for (Object[] row : results) {
                // UNION 查询时 sourceTable 需要从数据中推断
                images.add(mapToImage(row, null));
            }
            
            return PageResponse.of(images, total.longValue(), page, pageSize);
        } catch (Exception e) {
            log.error("多表 UNION 查询失败", e);
            return PageResponse.of(new ArrayList<>(), 0L, 1, 20);
        }
    }

    /**
     * 软删除图片
     */
    @Transactional
    public boolean softDelete(String imageId, String userId) {
        String tableName = getUserTableName(userId);
        
        try {
            if (!tableExists(tableName)) {
                return false;
            }
            
            String updateSQL = String.format(
                "UPDATE %s SET deleted = true, deleted_at = ? WHERE id = ?", 
                tableName);
            Query query = entityManager.createNativeQuery(updateSQL);
            query.setParameter(1, Timestamp.valueOf(LocalDateTime.now()));
            query.setParameter(2, imageId);
            
            return query.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("软删除图片失败, id: {}", imageId, e);
            return false;
        }
    }

    /**
     * 恢复图片
     */
    @Transactional
    public boolean restore(String imageId, String userId) {
        String tableName = getUserTableName(userId);
        
        try {
            if (!tableExists(tableName)) {
                return false;
            }
            
            String updateSQL = String.format(
                "UPDATE %s SET deleted = false, deleted_at = null WHERE id = ?", 
                tableName);
            Query query = entityManager.createNativeQuery(updateSQL);
            query.setParameter(1, imageId);
            
            return query.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("恢复图片失败, id: {}", imageId, e);
            return false;
        }
    }

    /**
     * 永久删除图片
     */
    @Transactional
    public boolean hardDelete(String imageId, String userId) {
        String tableName = getUserTableName(userId);
        
        try {
            if (!tableExists(tableName)) {
                return false;
            }
            
            String deleteSQL = String.format("DELETE FROM %s WHERE id = ?", tableName);
            Query query = entityManager.createNativeQuery(deleteSQL);
            query.setParameter(1, imageId);
            
            return query.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("永久删除图片失败, id: {}", imageId, e);
            return false;
        }
    }

    /**
     * 切换收藏状态
     */
    @Transactional
    public boolean toggleFavorite(String imageId, String userId) {
        String tableName = getUserTableName(userId);
        
        try {
            if (!tableExists(tableName)) {
                return false;
            }
            
            String updateSQL = String.format(
                "UPDATE %s SET favorite = NOT favorite, updated_at = ? WHERE id = ?", 
                tableName);
            Query query = entityManager.createNativeQuery(updateSQL);
            query.setParameter(1, Timestamp.valueOf(LocalDateTime.now()));
            query.setParameter(2, imageId);
            
            return query.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("切换收藏状态失败, id: {}", imageId, e);
            return false;
        }
    }

    /**
     * 批量软删除
     */
    @Transactional
    public int batchSoftDelete(List<String> imageIds, String userId) {
        String tableName = getUserTableName(userId);
        
        try {
            if (!tableExists(tableName) || imageIds.isEmpty()) {
                return 0;
            }
            
            String updateSQL = String.format(
                "UPDATE %s SET deleted = true, deleted_at = ? WHERE id IN (?)", 
                tableName);
            Query query = entityManager.createNativeQuery(updateSQL);
            query.setParameter(1, Timestamp.valueOf(LocalDateTime.now()));
            query.setParameter(2, imageIds.stream().collect(Collectors.joining(id -> "'" + id + "'")).collect(Collectors.joining(", ", ", ", ")));
            
            // 使用参数列表
            for (int i = 0; i < imageIds.size(); i++) {
                query.setParameter(i + 2, imageIds.get(i));
            }
            
            return query.executeUpdate();
        } catch (Exception e) {
            log.error("批量软删除失败", e);
            return 0;
        }
    }

    /**
     * 批量恢复
     */
    @Transactional
    public int batchRestore(List<String> imageIds, String userId) {
        String tableName = getUserTableName(userId);
        
        try {
            if (!tableExists(tableName) || imageIds.isEmpty()) {
                return 0;
            }
            
            StringBuilder idsStr = new StringBuilder();
            for (int i = 0; i < imageIds.size(); i++) {
                if (i > 0) idsStr.append(",");
                idsStr.append("'").append(imageIds.get(i)).append("'");
            }
            
            String updateSQL = String.format(
                "UPDATE %s SET deleted = false, deleted_at = null WHERE id IN (%s)", 
                tableName, idsStr);
            Query query = entityManager.createNativeQuery(updateSQL);
            
            return query.executeUpdate();
        } catch (Exception e) {
            log.error("批量恢复失败", e);
            return 0;
        }
    }

    /**
     * 移动图片到相册
     */
    @Transactional
    public boolean moveToAlbum(String imageId, String albumId, String userId) {
        String tableName = getUserTableName(userId);
        
        try {
            if (!tableExists(tableName)) {
                return false;
            }
            
            String updateSQL = String.format(
                "UPDATE %s SET album_id = ?, updated_at = ? WHERE id = ?", 
                tableName);
            Query query = entityManager.createNativeQuery(updateSQL);
            query.setParameter(1, albumId);
            query.setParameter(2, Timestamp.valueOf(LocalDateTime.now()));
            query.setParameter(3, imageId);
            
            return query.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("移动图片失败, id: {}", imageId, e);
            return false;
        }
    }

    /**
     * 统计用户图片数量
     */
    public long countByUser(String userId) {
        String tableName = getUserTableName(userId);
        
        try {
            if (!tableExists(tableName)) {
                return 0;
            }
            
            String countSQL = String.format("SELECT COUNT(*) FROM %s WHERE deleted = false", tableName);
            Query query = entityManager.createNativeQuery(countSQL);
            BigInteger result = (BigInteger) query.getSingleResult();
            return result != null ? result.longValue() : 0;
        } catch (Exception e) {
            log.error("统计用户图片数量失败", e);
            return 0;
        }
    }

    /**
     * 统计用户主图数量
     */
    public long countMainImagesByUser(String userId) {
        String tableName = getUserTableName(userId);
        
        try {
            if (!tableExists(tableName)) {
                return 0;
            }
            
            String countSQL = String.format("SELECT COUNT(*) FROM %s WHERE deleted = false AND is_main_image = true", tableName);
            Query query = entityManager.createNativeQuery(countSQL);
            BigInteger result = (BigInteger) query.getSingleResult();
            return result != null ? result.longValue() : 0;
        } catch (Exception e) {
            log.error("统计用户主图数量失败", e);
            return 0;
        }
    }

    /**
     * 统计全部图片数量
     */
    public long countAllImages() {
        try {
            List<String> tableNames = getAllUserImageTableNames();
            if (tableNames.isEmpty()) {
                return 0;
            }
            
            long total = 0;
            for (String tableName : tableNames) {
                String countSQL = String.format("SELECT COUNT(*) FROM %s WHERE deleted = false", tableName);
                Query query = entityManager.createNativeQuery(countSQL);
                BigInteger result = (BigInteger) query.getSingleResult();
                if (result != null) {
                    total += result.longValue();
                }
            }
            return total;
        } catch (Exception e) {
            log.error("统计全部图片数量失败", e);
            return 0;
        }
    }

    /**
     * 统计全部主图数量
     */
    public long countAllMainImages() {
        try {
            List<String> tableNames = getAllUserImageTableNames();
            if (tableNames.isEmpty()) {
                return 0;
            }
            
            long total = 0;
            for (String tableName : tableNames) {
                String countSQL = String.format("SELECT COUNT(*) FROM %s WHERE deleted = false AND is_main_image = true", tableName);
                Query query = entityManager.createNativeQuery(countSQL);
                BigInteger result = (BigInteger) query.getSingleResult();
                if (result != null) {
                    total += result.longValue();
                }
            }
            return total;
        } catch (Exception e) {
            log.error("统计全部主图数量失败", e);
            return 0;
        }
    }

    /**
     * 设置查询参数
     */
    private void setQueryParameters(Query query, Image image) {
        query.setParameter(1, image.getId());
        query.setParameter(2, image.getUrl());
        query.setParameter(3, image.getTitle());
        query.setParameter(4, image.getOriginalName());
        query.setParameter(5, image.getSize());
        query.setParameter(6, image.getWidth());
        query.setParameter(7, image.getHeight());
        query.setParameter(8, image.getFileType());
        query.setParameter(9, image.getAlbumId());
        query.setParameter(10, image.getProductId());
        query.setParameter(11, image.getIsMainImage() != null && image.getIsMainImage());
        query.setParameter(12, image.getFavorite() != null && image.getFavorite());
        query.setParameter(13, image.getViewCount() != null ? image.getViewCount() : 0);
        query.setParameter(14, image.getDownloadCount() != null ? image.getDownloadCount() : 0);
        query.setParameter(15, image.getTags() != null ? image.getTags().toString() : null);
        query.setParameter(16, image.getDeleted() != null && image.getDeleted());
        query.setParameter(17, image.getDeletedAt() != null ? Timestamp.valueOf(image.getDeletedAt()) : null);
        query.setParameter(18, image.getCreatedAt() != null ? Timestamp.valueOf(image.getCreatedAt()) : Timestamp.valueOf(LocalDateTime.now()));
        query.setParameter(19, image.getUpdatedAt() != null ? Timestamp.valueOf(image.getUpdatedAt()) : Timestamp.valueOf(LocalDateTime.now()));
        query.setParameter(20, image.getUserId());
    }

    /**
     * 将查询结果映射为 Image 对象
     */
    private Image mapToImage(Object[] row, String tableName) {
        Image image = new Image();
        
        try {
            image.setId((String) row[0]);
            image.setUrl((String) row[1]);
            image.setTitle((String) row[2]);
            image.setOriginalName((String) row[3]);
            image.setSize((Long) row[4]);
            image.setWidth((Integer) row[5]);
            image.setHeight((Integer) row[6]);
            image.setFileType((String) row[7]);
            image.setAlbumId((String) row[8]);
            image.setProductId((String) row[9]);
            image.setIsMainImage((Boolean) row[10]);
            image.setFavorite((Boolean) row[11]);
            image.setViewCount((Long) row[12]);
            image.setDownloadCount((Long) row[13]);
            
            // 处理 tags (JSONB)
            Object tagsObj = row[14];
            if (tagsObj != null) {
                image.setTags(parseTags(tagsObj));
            }
            
            image.setDeleted((Boolean) row[15]);
            
            // 处理 deleted_at
            Object deletedAtObj = row[16];
            if (deletedAtObj != null) {
                if (deletedAtObj instanceof Timestamp) {
                    image.setDeletedAt(((Timestamp) deletedAtObj).toLocalDateTime());
                }
            }
            
            // 处理 created_at
            Object createdAtObj = row[17];
            if (createdAtObj != null) {
                if (createdAtObj instanceof Timestamp) {
                    image.setCreatedAt(((Timestamp) createdAtObj).toLocalDateTime());
                }
            }
            
            // 处理 updated_at
            Object updatedAtObj = row[18];
            if (updatedAtObj != null) {
                if (updatedAtObj instanceof Timestamp) {
                    image.setUpdatedAt(((Timestamp) updatedAtObj).toLocalDateTime());
                }
            }
            
            image.setUserId((String) row[19]);
            image.setSourceTable(tableName);
            
        } catch (Exception e) {
            log.error("映射图片数据失败", e);
        }
        
        return image;
    }

    /**
     * 解析 tags JSONB 数据
     */
    private List<String> parseTags(Object tagsObj) {
        List<String> tags = new ArrayList<>();
        
        try {
            if (tagsObj == null) {
                return tags;
            }
            
            String tagsStr = tagsObj.toString();
            if (tagsStr.startsWith("[") && tagsStr.endsWith("]")) {
                // JSON 数组格式
                String content = tagsStr.substring(1, tagsStr.length() - 1);
                for (String tag : content.split(",")) {
                    tag = tag.trim().replace("\"", "");
                    if (!tag.isEmpty()) {
                        tags.add(tag);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析 tags 失败: {}", tagsObj);
        }
        
        return tags;
    }
}