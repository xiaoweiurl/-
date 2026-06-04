package com.imagemanager.repository;

import com.imagemanager.dto.ImageQueryRequest;
import com.imagemanager.dto.PageResponse;
import com.imagemanager.entity.Image;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 动态表图片数据访问
 * 支持按用户切换表名进行查询和保存
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ImageDynamicRepository {

    private final EntityManager entityManager;

    /**
     * 保存图片到用户表
     */
    @Transactional
    public Image save(String tableName, Image image) {
        try {
            if (image.getId() == null || image.getId().isEmpty()) {
                image.setId(UUID.randomUUID().toString());
            }
            
            String insertSQL = String.format("""
                INSERT INTO %s (id, url, title, original_name, size, width, height, file_type, 
                    album_id, product_id, is_main_image, favorite, view_count, download_count, 
                    tags, deleted, deleted_at, created_at, updated_at, user_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
                """, tableName);
            
            Query query = entityManager.createNativeQuery(insertSQL);
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
            query.setParameter(17, image.getDeletedAt());
            query.setParameter(18, image.getCreatedAt() != null ? image.getCreatedAt() : new Timestamp(System.currentTimeMillis()));
            query.setParameter(19, image.getUpdatedAt() != null ? image.getUpdatedAt() : new Timestamp(System.currentTimeMillis()));
            query.setParameter(20, image.getUserId());
            
            query.executeUpdate();
            
            return image;
        } catch (Exception e) {
            log.error("保存图片失败, 表: {}", tableName, e);
            throw e;
        }
    }

    /**
     * 更新图片
     */
    @Transactional
    public Image update(String tableName, Image image) {
        try {
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
            query.setParameter(16, image.getDeletedAt());
            query.setParameter(17, new Timestamp(System.currentTimeMillis()));
            query.setParameter(18, image.getId());
            
            query.executeUpdate();
            
            return image;
        } catch (Exception e) {
            log.error("更新图片失败, 表: {}, id: {}", tableName, image.getId(), e);
            throw e;
        }
    }

    /**
     * 根据ID查询图片
     */
    public Image findById(String tableName, String id) {
        try {
            String querySQL = String.format("SELECT * FROM %s WHERE id = ?", tableName);
            Query query = entityManager.createNativeQuery(querySQL);
            query.setParameter(1, id);
            
            Object[] result = (Object[]) query.getSingleResultOrNull();
            if (result == null) {
                return null;
            }
            return mapToImage(result, tableName);
        } catch (Exception e) {
            log.error("查询图片失败, 表: {}, id: {}", tableName, id, e);
            return null;
        }
    }

    /**
     * 分页查询用户图片
     */
    public PageResponse<Image> findByConditions(String tableName, ImageQueryRequest request) {
        try {
            StringBuilder whereClause = new StringBuilder("WHERE deleted = false");
            Map<String, Object> params = new HashMap<>();
            int paramIndex = 1;
            
            // 构建查询条件
            if (request.getAlbumId() != null && !request.getAlbumId().isEmpty()) {
                whereClause.append(" AND album_id = ?").append(paramIndex);
                params.put(String.valueOf(paramIndex), request.getAlbumId());
                paramIndex++;
            }
            
            if (request.getFavorites() != null && request.getFavorites()) {
                whereClause.append(" AND favorite = true");
            }
            
            if (request.getOnlyMainImage() != null && request.getOnlyMainImage()) {
                whereClause.append(" AND is_main_image = true");
            }
            
            if (request.getProductId() != null && !request.getProductId().isEmpty()) {
                whereClause.append(" AND product_id = ?").append(paramIndex);
                params.put(String.valueOf(paramIndex), request.getProductId());
                paramIndex++;
            }
            
            // 排序
            String orderBy = "created_at DESC";
            if (request.getSortBy() != null) {
                String direction = request.getSortOrder() != null && request.getSortOrder().equalsIgnoreCase("asc") ? "ASC" : "DESC";
                orderBy = request.getSortBy() + " " + direction;
            }
            
            // 查询总数
            String countSQL = String.format("SELECT COUNT(*) FROM %s %s", tableName, whereClause);
            Query countQuery = entityManager.createNativeQuery(countSQL);
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                countQuery.setParameter(Integer.parseInt(entry.getKey()), entry.getValue());
            }
            BigInteger total = (BigInteger) countQuery.getSingleResult();
            
            // 分页查询
            int page = request.getPage() != null ? request.getPage() : 1;
            int pageSize = request.getPageSize() != null ? request.getPageSize() : 20;
            int offset = (page - 1) * pageSize;
            
            String querySQL = String.format("SELECT * FROM %s %s ORDER BY %s LIMIT %d OFFSET %d", 
                tableName, whereClause, orderBy, pageSize, offset);
            Query query = entityManager.createNativeQuery(querySQL);
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                query.setParameter(Integer.parseInt(entry.getKey()), entry.getValue());
            }
            
            List<?> results = query.getResultList();
            List<Image> images = new ArrayList<>();
            for (Object obj : results) {
                Object[] row = (Object[]) obj;
                images.add(mapToImage(row, tableName));
            }
            
            return new PageResponse<>(images, total.longValue(), page, pageSize);
        } catch (Exception e) {
            log.error("分页查询图片失败, 表: {}", tableName, e);
            return new PageResponse<>(new ArrayList<>(), 0L, 1, 20);
        }
    }

    /**
     * 查询所有用户图片（UNION 所有表）
     */
    public PageResponse<Image> findAllUsersImages(ImageQueryRequest request, List<String> tableNames) {
        try {
            if (tableNames.isEmpty()) {
                return new PageResponse<>(new ArrayList<>(), 0L, 1, 20);
            }
            
            // 构建条件
            StringBuilder whereClause = new StringBuilder("WHERE deleted = false");
            Map<String, Object> params = new HashMap<>();
            int paramIndex = 1;
            
            if (request.getOnlyMainImage() != null && request.getOnlyMainImage()) {
                whereClause.append(" AND is_main_image = true");
            }
            
            if (request.getFavorites() != null && request.getFavorites()) {
                whereClause.append(" AND favorite = true");
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
                unionSQL.append(String.format("SELECT *, '%s' as source_table FROM %s %s", 
                    tableNames.get(i), tableNames.get(i), whereClause));
            }
            
            // 查询总数
            String countSQL = String.format("SELECT COUNT(*) FROM (%s) AS combined", unionSQL);
            Query countQuery = entityManager.createNativeQuery(countSQL);
            BigInteger total = (BigInteger) countQuery.getSingleResult();
            
            // 分页查询
            int page = request.getPage() != null ? request.getPage() : 1;
            int pageSize = request.getPageSize() != null ? request.getPageSize() : 20;
            int offset = (page - 1) * pageSize;
            
            String querySQL = String.format("SELECT * FROM (%s) AS combined ORDER BY %s LIMIT %d OFFSET %d", 
                unionSQL, orderBy, pageSize, offset);
            Query query = entityManager.createNativeQuery(querySQL);
            
            List<?> results = query.getResultList();
            List<Image> images = new ArrayList<>();
            for (Object obj : results) {
                Object[] row = (Object[]) obj;
                // 最后一个字段是 source_table，用于标识来源
                String sourceTable = (String) row[row.length - 1];
                images.add(mapToImage(row, sourceTable));
            }
            
            return new PageResponse<>(images, total.longValue(), page, pageSize);
        } catch (Exception e) {
            log.error("查询所有用户图片失败", e);
            return new PageResponse<>(new ArrayList<>(), 0L, 1, 20);
        }
    }

    /**
     * 删除图片（软删除）
     */
    @Transactional
    public void softDelete(String tableName, String id) {
        String updateSQL = String.format(
            "UPDATE %s SET deleted = true, deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?", 
            tableName);
        Query query = entityManager.createNativeQuery(updateSQL);
        query.setParameter(1, id);
        query.executeUpdate();
    }

    /**
     * 永久删除图片
     */
    @Transactional
    public void hardDelete(String tableName, String id) {
        String deleteSQL = String.format("DELETE FROM %s WHERE id = ?", tableName);
        Query query = entityManager.createNativeQuery(deleteSQL);
        query.setParameter(1, id);
        query.executeUpdate();
    }

    /**
     * 恢复图片
     */
    @Transactional
    public void restore(String tableName, String id) {
        String updateSQL = String.format(
            "UPDATE %s SET deleted = false, deleted_at = NULL, updated_at = CURRENT_TIMESTAMP WHERE id = ?", 
            tableName);
        Query query = entityManager.createNativeQuery(updateSQL);
        query.setParameter(1, id);
        query.executeUpdate();
    }

    /**
     * 切换收藏状态
     */
    @Transactional
    public void toggleFavorite(String tableName, String id, boolean favorite) {
        String updateSQL = String.format("UPDATE %s SET favorite = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", tableName);
        Query query = entityManager.createNativeQuery(updateSQL);
        query.setParameter(1, favorite);
        query.setParameter(2, id);
        query.executeUpdate();
    }

    /**
     * 统计用户图片数量
     */
    public long countByUser(String tableName) {
        try {
            String countSQL = String.format("SELECT COUNT(*) FROM %s WHERE deleted = false", tableName);
            Query query = entityManager.createNativeQuery(countSQL);
            BigInteger count = (BigInteger) query.getSingleResult();
            return count.longValue();
        } catch (Exception e) {
            log.error("统计图片数量失败, 表: {}", tableName, e);
            return 0;
        }
    }

    /**
     * 统计用户主图数量
     */
    public long countMainImagesByUser(String tableName) {
        try {
            String countSQL = String.format("SELECT COUNT(*) FROM %s WHERE deleted = false AND is_main_image = true", tableName);
            Query query = entityManager.createNativeQuery(countSQL);
            BigInteger count = (BigInteger) query.getSingleResult();
            return count.longValue();
        } catch (Exception e) {
            log.error("统计主图数量失败, 表: {}", tableName, e);
            return 0;
        }
    }

    /**
     * 将查询结果映射为 Image 对象
     */
    private Image mapToImage(Object[] row, String tableName) {
        Image image = new Image();
        image.setId((String) row[0]);
        image.setUrl((String) row[1]);
        image.setName((String) row[2]);
        image.setOriginalName((String) row[3]);
        image.setSize(row[4] != null ? ((Number) row[4]).longValue() : null);
        image.setWidth(row[5] != null ? ((Number) row[5]).intValue() : null);
        image.setHeight(row[6] != null ? ((Number) row[6]).intValue() : null);
        image.setFileType((String) row[7]);
        image.setAlbumId((String) row[8]);
        image.setProductId((String) row[9]);
        image.setIsMainImage(row[10] != null && ((Boolean) row[10]));
        image.setFavorite(row[11] != null && ((Boolean) row[11]));
        image.setViewCount(row[12] != null ? ((Number) row[12]).intValue() : 0);
        image.setDownloadCount(row[13] != null ? ((Number) row[13]).intValue() : 0);
        // row[14] 是 tags JSONB，暂不处理
        image.setDeleted(row[15] != null && ((Boolean) row[15]));
        image.setDeletedAt(row[16] != null ? (Timestamp) row[16] : null);
        image.setCreatedAt(row[17] != null ? (Timestamp) row[17] : null);
        image.setUpdatedAt(row[18] != null ? (Timestamp) row[18] : null);
        image.setUserId((String) row[19]);
        // 存储来源表名（用于后续操作）
        image.setSourceTable(tableName);
        return image;
    }
}