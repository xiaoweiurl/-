package com.imagemanager.repository;

import com.imagemanager.dto.ImageQueryRequest;
import com.imagemanager.dto.PageResponse;
import com.imagemanager.entity.Image;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PGobject;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 动态表图片数据访问 - 动态切换表名进行查询和保存
 * 每个用户图片存储在专属表中: images_<userId>
 * 全部知识查询需要 UNION 所有用户的表
 * 我的知识查询只查询当前用户的表
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ImageDynamicRepository {

    private final EntityManager entityManager;

    // ==================== 工具方法 ====================

    /**
     * 获取用户表名（按用户名生成）
     */
    public String getUserTableName(String username) {
        String safeUsername = username.replaceAll("-", "_").replaceAll("[^a-zA-Z0-9_]", "");
        return "images_" + safeUsername;
    }

    /**
     * 检查表是否存在
     */
    public boolean tableExists(String tableName) {
        try {
            String checkSQL = "SELECT tablename FROM pg_tables WHERE tablename = ? AND schemaname = 'public'";
            Query query = entityManager.createNativeQuery(checkSQL);
            query.setParameter(1, tableName);
            @SuppressWarnings("unchecked")
            List<String> results = query.getResultList();
            return !results.isEmpty();
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
            String querySQL = "SELECT tablename FROM pg_tables WHERE tablename LIKE 'images\\_%' AND schemaname = 'public' AND tablename != 'images'";
            Query query = entityManager.createNativeQuery(querySQL);
            @SuppressWarnings("unchecked")
            List<String> tables = query.getResultList();
            return tables != null ? tables : new ArrayList<>();
        } catch (Exception e) {
            log.error("获取所有用户图片表失败", e);
            return new ArrayList<>();
        }
    }

    // ==================== 保存/更新 ====================

    /**
     * 保存图片到用户动态表
     */
    @Transactional
    public Image save(Image image, String userId) {
        String tableName = getUserTableName(userId);

        try {
            if (!tableExists(tableName)) {
                throw new RuntimeException("用户图片表不存在: " + tableName);
            }

            if (image.getId() == null || image.getId().isEmpty()) {
                image.setId(UUID.randomUUID().toString());
            }

            // 不修改传入 Image 实体的 userId/sourceTable，避免影响 JPA 主表
            // 动态表 INSERT 直接使用 image 原始字段值
            LocalDateTime now = LocalDateTime.now();
            if (image.getCreatedAt() == null) {
                image.setCreatedAt(now);
            }
            if (image.getUpdatedAt() == null) {
                image.setUpdatedAt(now);
            }

            String insertSQL = String.format(
                "INSERT INTO %s (id, url, title, original_name, size, width, height, file_type, " +
                "album_id, product_id, is_main_image, favorite, view_count, download_count, " +
                "tags, deleted, deleted_at, created_at, updated_at, user_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, original_name = EXCLUDED.original_name, updated_at = EXCLUDED.updated_at",
                tableName);

            org.hibernate.Session hibernateSession = entityManager.unwrap(org.hibernate.Session.class);
            hibernateSession.doWork(connection -> {
                try (java.sql.PreparedStatement pstmt = connection.prepareStatement(insertSQL)) {
                    setInsertParameters(pstmt, image);
                    pstmt.executeUpdate();
                }
            });

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

            String updateSQL = String.format(
                "UPDATE %s SET url = ?, title = ?, original_name = ?, size = ?, width = ?, height = ?, " +
                "file_type = ?, album_id = ?, product_id = ?, is_main_image = ?, " +
                "favorite = ?, view_count = ?, download_count = ?, tags = ?, " +
                "deleted = ?, deleted_at = ?, updated_at = ? WHERE id = ?",
                tableName);

            org.hibernate.Session hibernateSession = entityManager.unwrap(org.hibernate.Session.class);
            hibernateSession.doWork(connection -> {
                try (java.sql.PreparedStatement pstmt = connection.prepareStatement(updateSQL)) {
                    setUpdateParameters(pstmt, image);
                    pstmt.executeUpdate();
                }
            });

            return image;
        } catch (Exception e) {
            log.error("更新图片失败, 表: {}, id: {}", tableName, image.getId(), e);
            throw new RuntimeException("更新图片失败: " + e.getMessage(), e);
        }
    }

    // ==================== 查询 ====================

    /**
     * 根据ID查询图片
     */
    public Image findById(String imageId, String userId) {
        String tableName = getUserTableName(userId);

        try {
            if (!tableExists(tableName)) {
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
                return emptyPage(request);
            }
            return queryFromTable(tableName, request);
        } catch (Exception e) {
            log.error("查询我的知识库失败, 表: {}", tableName, e);
            return emptyPage(request);
        }
    }

    /**
     * 查询全部知识（只查当前用户自己的动态表）
     */
    public PageResponse<Image> queryAllImages(ImageQueryRequest request, String userId) {
        String tableName = getUserTableName(userId);
        try {
            if (!tableExists(tableName)) {
                return emptyPage(request);
            }
            return queryFromTable(tableName, request);
        } catch (Exception e) {
            log.error("查询全部知识失败, userId: {}", userId, e);
            return emptyPage(request);
        }
    }

    /**
     * 二创中心 - 查询其他用户上传的图片（UNION ALL 其他用户的动态表，排除当前用户自己的表）
     */
    public PageResponse<Image> queryOtherUsersImages(ImageQueryRequest request, String currentUserId) {
        try {
            List<String> allTableNames = getAllUserImageTableNames();
            // 过滤掉当前用户的表，只查其他用户的
            String myTableName = getUserTableName(currentUserId);
            List<String> otherTableNames = allTableNames.stream()
                    .filter(name -> !name.equals(myTableName))
                    .collect(Collectors.toList());

            if (otherTableNames.isEmpty()) {
                return emptyPage(request);
            }

            if (otherTableNames.size() == 1) {
                return queryFromTable(otherTableNames.get(0), request);
            }

            return queryFromMultipleTables(otherTableNames, request);
        } catch (Exception e) {
            log.error("查询二创中心失败, currentUserId: {}", currentUserId, e);
            return emptyPage(request);
        }
    }

    /**
     * 查询指定相册的图片
     */
    public PageResponse<Image> queryAlbumImages(String albumId, ImageQueryRequest request, String userId) {
        String tableName = getUserTableName(userId);

        try {
            if (!tableExists(tableName)) {
                return emptyPage(request);
            }

            if (request.getAlbumId() == null) {
                request.setAlbumId(albumId);
            }

            return queryFromTable(tableName, request);
        } catch (Exception e) {
            log.error("查询相册图片失败, albumId: {}", albumId, e);
            return emptyPage(request);
        }
    }

    /**
     * 查询收藏夹
     */
    public PageResponse<Image> queryFavorites(ImageQueryRequest request, String userId) {
        String tableName = getUserTableName(userId);

        try {
            if (!tableExists(tableName)) {
                return emptyPage(request);
            }

            request.setFavorite(true);
            return queryFromTable(tableName, request);
        } catch (Exception e) {
            log.error("查询收藏夹失败", e);
            return emptyPage(request);
        }
    }

    /**
     * 查询回收站
     */
    public PageResponse<Image> queryTrash(ImageQueryRequest request, String userId) {
        String tableName = getUserTableName(userId);

        try {
            if (!tableExists(tableName)) {
                return emptyPage(request);
            }

            request.setIncludeDeleted(true);
            request.setDeleted(true);
            return queryFromTable(tableName, request);
        } catch (Exception e) {
            log.error("查询回收站失败", e);
            return emptyPage(request);
        }
    }

    // ==================== 操作方法 ====================

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
     * 移动到相册
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
            log.error("移动到相册失败, id: {}", imageId, e);
            return false;
        }
    }

    /**
     * 设为主图
     */
    @Transactional
    public boolean setMainImage(String imageId, String userId) {
        String tableName = getUserTableName(userId);

        try {
            if (!tableExists(tableName)) {
                return false;
            }

            // 先取消该商品的所有主图
            Image image = findByIdAny(imageId, userId);
            if (image != null && image.getProductId() != null) {
                String clearSQL = String.format(
                    "UPDATE %s SET is_main_image = false WHERE product_id = ?",
                    tableName);
                Query clearQuery = entityManager.createNativeQuery(clearSQL);
                clearQuery.setParameter(1, image.getProductId());
                clearQuery.executeUpdate();
            }

            // 设置新主图
            String updateSQL = String.format(
                "UPDATE %s SET is_main_image = true, updated_at = ? WHERE id = ?",
                tableName);
            Query query = entityManager.createNativeQuery(updateSQL);
            query.setParameter(1, Timestamp.valueOf(LocalDateTime.now()));
            query.setParameter(2, imageId);

            return query.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("设为主图失败, id: {}", imageId, e);
            return false;
        }
    }

    // ==================== 批量操作 ====================

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

            // 构建 IN 子句的占位符
            String placeholders = imageIds.stream().map(id -> "?").collect(Collectors.joining(","));
            String updateSQL = String.format(
                "UPDATE %s SET deleted = true, deleted_at = ? WHERE id IN (%s)",
                tableName, placeholders);

            Query query = entityManager.createNativeQuery(updateSQL);
            query.setParameter(1, Timestamp.valueOf(LocalDateTime.now()));
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

            String placeholders = imageIds.stream().map(id -> "?").collect(Collectors.joining(","));
            String updateSQL = String.format(
                "UPDATE %s SET deleted = false, deleted_at = null WHERE id IN (%s)",
                tableName, placeholders);

            Query query = entityManager.createNativeQuery(updateSQL);
            for (int i = 0; i < imageIds.size(); i++) {
                query.setParameter(i + 1, imageIds.get(i));
            }

            return query.executeUpdate();
        } catch (Exception e) {
            log.error("批量恢复失败", e);
            return 0;
        }
    }

    /**
     * 批量永久删除
     */
    @Transactional
    public int batchHardDelete(List<String> imageIds, String userId) {
        String tableName = getUserTableName(userId);

        try {
            if (!tableExists(tableName) || imageIds.isEmpty()) {
                return 0;
            }

            String placeholders = imageIds.stream().map(id -> "?").collect(Collectors.joining(","));
            String deleteSQL = String.format(
                "DELETE FROM %s WHERE id IN (%s)",
                tableName, placeholders);

            Query query = entityManager.createNativeQuery(deleteSQL);
            for (int i = 0; i < imageIds.size(); i++) {
                query.setParameter(i + 1, imageIds.get(i));
            }

            return query.executeUpdate();
        } catch (Exception e) {
            log.error("批量永久删除失败", e);
            return 0;
        }
    }

    /**
     * 批量收藏
     */
    @Transactional
    public int batchFavorite(List<String> imageIds, String userId) {
        String tableName = getUserTableName(userId);

        try {
            if (!tableExists(tableName) || imageIds.isEmpty()) {
                return 0;
            }

            String placeholders = imageIds.stream().map(id -> "?").collect(Collectors.joining(","));
            String updateSQL = String.format(
                "UPDATE %s SET favorite = true, updated_at = ? WHERE id IN (%s)",
                tableName, placeholders);

            Query query = entityManager.createNativeQuery(updateSQL);
            query.setParameter(1, Timestamp.valueOf(LocalDateTime.now()));
            for (int i = 0; i < imageIds.size(); i++) {
                query.setParameter(i + 2, imageIds.get(i));
            }

            return query.executeUpdate();
        } catch (Exception e) {
            log.error("批量收藏失败", e);
            return 0;
        }
    }

    /**
     * 批量移动到相册
     */
    @Transactional
    public int batchMoveToAlbum(List<String> imageIds, String albumId, String userId) {
        String tableName = getUserTableName(userId);

        try {
            if (!tableExists(tableName) || imageIds.isEmpty()) {
                return 0;
            }

            String placeholders = imageIds.stream().map(id -> "?").collect(Collectors.joining(","));
            String updateSQL = String.format(
                "UPDATE %s SET album_id = ?, updated_at = ? WHERE id IN (%s)",
                tableName, placeholders);

            Query query = entityManager.createNativeQuery(updateSQL);
            query.setParameter(1, albumId);
            query.setParameter(2, Timestamp.valueOf(LocalDateTime.now()));
            for (int i = 0; i < imageIds.size(); i++) {
                query.setParameter(i + 3, imageIds.get(i));
            }

            return query.executeUpdate();
        } catch (Exception e) {
            log.error("批量移动到相册失败", e);
            return 0;
        }
    }

    // ==================== 统计方法 ====================

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
            long result = ((Number) query.getSingleResult()).longValue();
            return result;
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
            long result = ((Number) query.getSingleResult()).longValue();
            return result;
        } catch (Exception e) {
            log.error("统计用户主图数量失败", e);
            return 0;
        }
    }

    /**
     * 统计用户回收站图片数量
     */
    public long countDeleted(String userId) {
        String tableName = getUserTableName(userId);

        try {
            if (!tableExists(tableName)) {
                return 0;
            }

            String countSQL = String.format("SELECT COUNT(*) FROM %s WHERE deleted = true", tableName);
            Query query = entityManager.createNativeQuery(countSQL);
            long result = ((Number) query.getSingleResult()).longValue();
            return result;
        } catch (Exception e) {
            log.error("统计用户回收站数量失败", e);
            return 0;
        }
    }

    /**
     * 统计所有图片数量（跨所有用户表）
     */
    public long countAllImages() {
        try {
            List<String> tableNames = getAllUserImageTableNames();
            if (tableNames.isEmpty()) {
                return 0;
            }

            long total = 0;
            for (String tableName : tableNames) {
                try {
                    String countSQL = String.format("SELECT COUNT(*) FROM %s WHERE deleted = false", tableName);
                    Query query = entityManager.createNativeQuery(countSQL);
                    long result = ((Number) query.getSingleResult()).longValue();
                    total += result;
                } catch (Exception e) {
                    log.warn("统计表 {} 图片数量失败", tableName);
                }
            }
            return total;
        } catch (Exception e) {
            log.error("统计所有图片数量失败", e);
            return 0;
        }
    }

    /**
     * 统计所有主图数量（跨所有用户表）
     */
    public long countAllMainImages() {
        try {
            List<String> tableNames = getAllUserImageTableNames();
            if (tableNames.isEmpty()) {
                return 0;
            }

            long total = 0;
            for (String tableName : tableNames) {
                try {
                    String countSQL = String.format("SELECT COUNT(*) FROM %s WHERE deleted = false AND is_main_image = true", tableName);
                    Query query = entityManager.createNativeQuery(countSQL);
                    long result = ((Number) query.getSingleResult()).longValue();
                    total += result;
                } catch (Exception e) {
                    log.warn("统计表 {} 主图数量失败", tableName);
                }
            }
            return total;
        } catch (Exception e) {
            log.error("统计所有主图数量失败", e);
            return 0;
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 从单张表查询
     */
    private PageResponse<Image> queryFromTable(String tableName, ImageQueryRequest request) {
        try {
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
                params.put(paramIndex++, request.getAlbumId());
            }

            // favorite 条件
            if (request.getFavorite() != null && request.getFavorite()) {
                whereClause.append(" AND favorite = true");
            }

            // onlyMainImage 条件
            if (request.getOnlyMainImage() != null && request.getOnlyMainImage()) {
                whereClause.append(" AND is_main_image = true");
            }

            // 关键词模糊搜索 - 匹配 title、description、original_name
            if (request.getKeyword() != null && !request.getKeyword().trim().isEmpty()) {
                String kw = request.getKeyword().trim();
                whereClause.append(" AND (LOWER(title) LIKE ? OR LOWER(description) LIKE ? OR LOWER(original_name) LIKE ?)");
                params.put(paramIndex, "%" + kw.toLowerCase() + "%");
                params.put(paramIndex + 1, "%" + kw.toLowerCase() + "%");
                params.put(paramIndex + 2, "%" + kw.toLowerCase() + "%");
                paramIndex += 3;
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
            for (Map.Entry<Integer, Object> entry : params.entrySet()) {
                countQuery.setParameter(entry.getKey(), entry.getValue());
            }
            long total = ((Number) countQuery.getSingleResult()).longValue();

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

            return PageResponse.of(images, total, page, pageSize);
        } catch (Exception e) {
            log.error("单表查询失败, 表: {}", tableName, e);
            return emptyPage(request);
        }
    }

    /**
     * 从多张表查询（UNION ALL）
     */
    private PageResponse<Image> queryFromMultipleTables(List<String> tableNames, ImageQueryRequest request) {
        try {
            if (tableNames.isEmpty()) {
                return emptyPage(request);
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

            // 查询总数
            String countSQL = String.format("SELECT COUNT(*) FROM (%s) AS combined", unionSQL);
            Query countQuery = entityManager.createNativeQuery(countSQL);
            long total = ((Number) countQuery.getSingleResult()).longValue();

            // 分页
            int page = request.getPage() != null ? request.getPage() : 1;
            int pageSize = request.getPageSize() != null ? request.getPageSize() : 20;
            int offset = (page - 1) * pageSize;

            String finalSQL = String.format("SELECT * FROM (%s) AS combined ORDER BY %s LIMIT %d OFFSET %d",
                unionSQL, orderBy, pageSize, offset);
            Query query = entityManager.createNativeQuery(finalSQL);

            @SuppressWarnings("unchecked")
            List<Object[]> results = query.getResultList();
            List<Image> images = new ArrayList<>();
            for (Object[] row : results) {
                images.add(mapToImage(row, null));
            }

            return PageResponse.of(images, total, page, pageSize);
        } catch (Exception e) {
            log.error("多表 UNION 查询失败", e);
            return emptyPage(request);
        }
    }

    /**
     * 设置 INSERT 参数 (JDBC PreparedStatement)
     */
    private void setInsertParameters(java.sql.PreparedStatement pstmt, Image image) throws java.sql.SQLException {
        pstmt.setString(1, image.getId());
        pstmt.setString(2, image.getUrl());
        pstmt.setString(3, image.getTitle());
        pstmt.setString(4, image.getOriginalName());
        pstmt.setLong(5, image.getSize() != null ? image.getSize() : 0L);
        if (image.getWidth() != null) pstmt.setInt(6, image.getWidth()); else pstmt.setNull(6, java.sql.Types.INTEGER);
        if (image.getHeight() != null) pstmt.setInt(7, image.getHeight()); else pstmt.setNull(7, java.sql.Types.INTEGER);
        pstmt.setString(8, image.getFileType());
        pstmt.setString(9, image.getAlbumId());
        pstmt.setString(10, image.getProductId());
        pstmt.setBoolean(11, image.getIsMainImage() != null && image.getIsMainImage());
        pstmt.setBoolean(12, image.getFavorite() != null && image.getFavorite());
        pstmt.setInt(13, image.getViewCount() != null ? image.getViewCount() : 0);
        pstmt.setInt(14, image.getDownloadCount() != null ? image.getDownloadCount() : 0);
        // tags - 使用 setString + ?::jsonb 由数据库自动转换
        pstmt.setString(15, image.getTags() != null ? toJsonArray(image.getTags()) : "[]");
        pstmt.setBoolean(16, image.getDeleted() != null && image.getDeleted());
        if (image.getDeletedAt() != null) pstmt.setTimestamp(17, Timestamp.valueOf(image.getDeletedAt())); else pstmt.setNull(17, java.sql.Types.TIMESTAMP);
        pstmt.setTimestamp(18, Timestamp.valueOf(image.getCreatedAt()));
        pstmt.setTimestamp(19, Timestamp.valueOf(image.getUpdatedAt()));
        pstmt.setString(20, image.getUserId());
    }

    /**
     * 设置 UPDATE 参数
     */
    private void setUpdateParameters(java.sql.PreparedStatement pstmt, Image image) throws java.sql.SQLException {
        pstmt.setString(1, image.getUrl());
        pstmt.setString(2, image.getTitle());
        pstmt.setString(3, image.getOriginalName());
        pstmt.setLong(4, image.getSize() != null ? image.getSize() : 0L);
        if (image.getWidth() != null) pstmt.setInt(5, image.getWidth()); else pstmt.setNull(5, java.sql.Types.INTEGER);
        if (image.getHeight() != null) pstmt.setInt(6, image.getHeight()); else pstmt.setNull(6, java.sql.Types.INTEGER);
        pstmt.setString(7, image.getFileType());
        pstmt.setString(8, image.getAlbumId());
        pstmt.setString(9, image.getProductId());
        pstmt.setBoolean(10, image.getIsMainImage() != null && image.getIsMainImage());
        pstmt.setBoolean(11, image.getFavorite() != null && image.getFavorite());
        pstmt.setInt(12, image.getViewCount() != null ? image.getViewCount() : 0);
        pstmt.setInt(13, image.getDownloadCount() != null ? image.getDownloadCount() : 0);
        // tags - 使用 setString + ?::jsonb 由数据库自动转换
        pstmt.setString(14, image.getTags() != null ? toJsonArray(image.getTags()) : "[]");
        pstmt.setBoolean(15, image.getDeleted() != null && image.getDeleted());
        if (image.getDeletedAt() != null) pstmt.setTimestamp(16, Timestamp.valueOf(image.getDeletedAt())); else pstmt.setNull(16, java.sql.Types.TIMESTAMP);
        pstmt.setTimestamp(17, Timestamp.valueOf(LocalDateTime.now()));
        pstmt.setString(18, image.getId());
    }

    /**
     * 将数据库行映射为 Image 对象
     * 列顺序: id, url, title, original_name, size, width, height, file_type,
     *         album_id, product_id, is_main_image, favorite, view_count, download_count,
     *         tags, deleted, deleted_at, created_at, updated_at, user_id
     */
    private Image mapToImage(Object[] row, String tableName) {
        Image image = new Image();
        image.setId((String) row[0]);
        image.setUrl((String) row[1]);
        image.setTitle((String) row[2]);
        image.setOriginalName((String) row[3]);
        image.setSize(row[4] != null ? ((Number) row[4]).longValue() : 0L);
        image.setWidth(row[5] != null ? ((Number) row[5]).intValue() : null);
        image.setHeight(row[6] != null ? ((Number) row[6]).intValue() : null);
        image.setFileType((String) row[7]);
        image.setAlbumId((String) row[8]);
        image.setProductId((String) row[9]);
        image.setIsMainImage(row[10] != null && (Boolean) row[10]);
        image.setFavorite(row[11] != null && (Boolean) row[11]);
        image.setViewCount(row[12] != null ? ((Number) row[12]).intValue() : 0);
        image.setDownloadCount(row[13] != null ? ((Number) row[13]).intValue() : 0);
        // tags - jsonb 返回为 String 或 PGobject
        Object tagsObj = row[14];
        if (tagsObj != null) {
            image.setTags(parseTags(tagsObj.toString()));
        }
        image.setDeleted(row[15] != null && (Boolean) row[15]);
        // deleted_at
        if (row[16] != null) {
            image.setDeletedAt(((Timestamp) row[16]).toLocalDateTime());
        }
        // created_at
        if (row[17] != null) {
            image.setCreatedAt(((Timestamp) row[17]).toLocalDateTime());
        }
        // updated_at
        if (row[18] != null) {
            image.setUpdatedAt(((Timestamp) row[18]).toLocalDateTime());
        }
        // user_id
        image.setUserId((String) row[19]);
        // sourceTable
        if (tableName != null) {
            image.setSourceTable(tableName);
        } else {
            image.setSourceTable("unknown");
        }
        return image;
    }

    /**
     * 将 List<String> 转为 JSON 数组字符串
     */
    private String toJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(list.get(i).replace("\"", "\\\"")).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 解析 tags JSON 字符串
     */
    private List<String> parseTags(String tagsStr) {
        if (tagsStr == null || tagsStr.isEmpty() || tagsStr.equals("[]") || tagsStr.equals("null")) {
            return new ArrayList<>();
        }
        try {
            // 简单的 JSON 数组解析
            String trimmed = tagsStr.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                trimmed = trimmed.substring(1, trimmed.length() - 1);
                if (trimmed.isEmpty()) {
                    return new ArrayList<>();
                }
                String[] parts = trimmed.split(",");
                List<String> tags = new ArrayList<>();
                for (String part : parts) {
                    String tag = part.trim().replace("\"", "").replace("'", "");
                    if (!tag.isEmpty()) {
                        tags.add(tag);
                    }
                }
                return tags;
            }
        } catch (Exception e) {
            log.warn("解析 tags 失败: {}", tagsStr);
        }
        return new ArrayList<>();
    }

    /**
     * 返回空分页
     */
    private PageResponse<Image> emptyPage(ImageQueryRequest request) {
        int page = request != null && request.getPage() != null ? request.getPage() : 1;
        int pageSize = request != null && request.getPageSize() != null ? request.getPageSize() : 20;
        return PageResponse.of(new ArrayList<>(), 0L, page, pageSize);
    }
}
