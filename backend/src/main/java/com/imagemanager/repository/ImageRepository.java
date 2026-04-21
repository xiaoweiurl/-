package com.imagemanager.repository;

import com.imagemanager.entity.Image;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 图片数据访问层
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Repository
public interface ImageRepository extends JpaRepository<Image, String> {
    
    /**
     * 查询未删除的图片（立即加载tags）
     */
    @Query("SELECT DISTINCT i FROM Image i LEFT JOIN FETCH i.tags WHERE i.deleted = false")
    Page<Image> findByDeletedFalseWithTags(Pageable pageable);

    /**
     * 查询未删除的图片（立即加载aiTags）
     */
    @Query("SELECT DISTINCT i FROM Image i LEFT JOIN FETCH i.aiTags WHERE i.deleted = false")
    Page<Image> findByDeletedFalseWithAiTags(Pageable pageable);

    /**
     * 查询未删除的图片
     */
    Page<Image> findByDeletedFalse(Pageable pageable);
    
    /**
     * 查询所有未删除的图片（不带分页）
     */
    List<Image> findByDeletedFalse();
    
    // ==================== 主图查询方法（只查主图，不查详情图） ====================
    
    /**
     * 查询未删除的主图（只查主图）
     */
    @Query("SELECT i FROM Image i WHERE i.deleted = false AND i.isMainImage = true")
    Page<Image> findByDeletedFalseAndIsMainImageTrue(Pageable pageable);
    
    /**
     * 查询所有未删除的主图（不带分页，用于高级搜索）
     */
    @Query("SELECT i FROM Image i WHERE i.deleted = false AND i.isMainImage = true")
    List<Image> findByDeletedFalseAndIsMainImageTrue();
    
    /**
     * 查询回收站主图（只查主图）
     */
    @Query("SELECT i FROM Image i WHERE i.deleted = true AND i.isMainImage = true")
    Page<Image> findByDeletedTrueAndIsMainImageTrue(Pageable pageable);
    
    /**
     * 查询回收站主图（不带分页，用于清空回收站）
     */
    @Query("SELECT i FROM Image i WHERE i.deleted = true AND i.isMainImage = true")
    List<Image> findByDeletedTrueAndIsMainImageTrueList();
    
    /**
     * 统计回收站主图数量
     */
    @Query("SELECT COUNT(i) FROM Image i WHERE i.deleted = true AND i.isMainImage = true")
    long countByDeletedTrueAndIsMainImageTrue();
    
    /**
     * 查询收藏的主图（只查主图）
     */
    @Query("SELECT i FROM Image i WHERE i.deleted = false AND i.favorite = true AND i.isMainImage = true")
    Page<Image> findByFavoriteTrueAndDeletedFalseAndIsMainImageTrue(Pageable pageable);
    
    /**
     * 按文件类型查询主图（只查主图）
     */
    @Query("SELECT i FROM Image i WHERE i.deleted = false AND i.fileType = :fileType AND i.isMainImage = true")
    Page<Image> findByFileTypeAndDeletedFalseAndIsMainImageTrue(@Param("fileType") String fileType, Pageable pageable);
    
    /**
     * 按相册查询未删除的图片
     */
    Page<Image> findByAlbumIdAndDeletedFalse(String albumId, Pageable pageable);

    /**
     * 按相册查询未删除的主图（商品数量）
     */
    @Query("SELECT i FROM Image i WHERE i.deleted = false AND i.isMainImage = true AND i.albumId = :albumId")
    Page<Image> findByAlbumIdAndIsMainImageAndDeletedFalse(@Param("albumId") String albumId, Pageable pageable);

    /**
     * 按用户查询未删除的图片
     */
    Page<Image> findByUserIdAndDeletedFalse(String userId, Pageable pageable);
    
    /**
     * 查询收藏的图片
     */
    Page<Image> findByFavoriteTrueAndDeletedFalse(Pageable pageable);
    
    /**
     * 统计收藏图片数量
     */
    int countByFavoriteTrue();
    
    /**
     * 查询回收站图片
     */
    Page<Image> findByDeletedTrue(Pageable pageable);
    
    /**
     * 查询指定时间之后上传的未删除图片
     */
    Page<Image> findByCreatedAtAfterAndDeletedFalse(LocalDateTime createdAt, Pageable pageable);
    
    /**
     * 查询指定时间之后上传的未删除主图
     */
    @Query("SELECT i FROM Image i WHERE i.deleted = false AND i.isMainImage = true AND i.createdAt > :createdAt")
    Page<Image> findByCreatedAtAfterAndDeletedFalseAndIsMainImageTrue(@Param("createdAt") LocalDateTime createdAt, Pageable pageable);
    
    /**
     * 按文件类型查询
     */
    Page<Image> findByFileTypeAndDeletedFalse(String fileType, Pageable pageable);
    
    /**
     * 搜索图片（按标题或描述）
     */
    @Query("SELECT i FROM Image i WHERE i.deleted = false AND (LOWER(i.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(i.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Image> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
    
    /**
     * 按相册和关键词搜索图片
     */
    @Query("SELECT i FROM Image i WHERE i.deleted = false AND i.albumId = :albumId AND (LOWER(i.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(i.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Image> searchByAlbumAndKeyword(@Param("albumId") String albumId, @Param("keyword") String keyword, Pageable pageable);
    
    /**
     * 按标签查询
     */
    @Query("SELECT i FROM Image i WHERE i.deleted = false AND :tag MEMBER OF i.tags")
    Page<Image> findByTag(@Param("tag") String tag, Pageable pageable);
    
    /**
     * 获取所有标签
     */
    @Query("SELECT DISTINCT t FROM Image i JOIN i.tags t WHERE i.deleted = false")
    List<String> findAllTags();
    
    /**
     * 批量更新相册
     */
    @Modifying
    @Query("UPDATE Image i SET i.albumId = :albumId WHERE i.id IN :ids")
    int updateAlbumByIds(@Param("albumId") String albumId, @Param("ids") List<String> ids);
    
    /**
     * 批量标记删除
     */
    @Modifying
    @Query("UPDATE Image i SET i.deleted = true WHERE i.id IN :ids")
    int softDeleteByIds(@Param("ids") List<String> ids);
    
    /**
     * 批量收藏
     */
    @Modifying
    @Query("UPDATE Image i SET i.favorite = true WHERE i.id IN :ids")
    int favoriteByIds(@Param("ids") List<String> ids);
    
    /**
     * 按相册统计图片数量
     */
    int countByAlbumId(String albumId);
    
    /**
     * 按相册统计未删除图片数量
     */
    int countByAlbumIdAndDeletedFalse(String albumId);

    /**
     * 按相册统计主图数量（商品数量）
     */
    @Query("SELECT COUNT(i) FROM Image i WHERE i.deleted = false AND i.isMainImage = true AND i.albumId = :albumId")
    long countMainImagesByAlbumId(@Param("albumId") String albumId);

    // ==================== 商品相关查询方法 ====================

    /**
     * 根据商品ID查询图片
     */
    List<Image> findByProductId(String productId);

    /**
     * 根据商品ID查询图片（未删除）
     */
    List<Image> findByProductIdAndDeleted(String productId, boolean deleted);

    /**
     * 根据商品ID查询图片（按显示顺序排序，未删除）
     */
    List<Image> findByProductIdAndDeletedOrderByDisplayOrderAsc(String productId, boolean deleted);

    /**
     * 查询主图（未删除）
     */
    Page<Image> findByIsMainImageAndDeleted(boolean isMainImage, boolean deleted, Pageable pageable);

    /**
     * 根据商品ID列表查询主图
     */
    Page<Image> findByProductIdInAndIsMainImageAndDeleted(List<String> productIds, boolean isMainImage, boolean deleted, Pageable pageable);

    /**
     * 根据商品ID列表查询所有图片
     */
    Page<Image> findByProductIdIn(List<String> productIds, Pageable pageable);

    /**
     * 统计有商品ID的图片数量
     */
    long countByProductIdIsNotNull();

    /**
     * 统计主图数量
     */
    long countByIsMainImageAndDeleted(boolean isMainImage, boolean deleted);

    /**
     * 查询缺少 isMainImage 或 productId 字段的图片
     */
    List<Image> findByIsMainImageNullOrProductIdNull();

    /**
     * 根据相册ID、主图状态和删除状态查询图片
     */
    Page<Image> findByAlbumIdAndIsMainImageAndDeleted(String albumId, boolean isMainImage, boolean deleted, Pageable pageable);

    /**
     * 根据相册ID和删除状态查询图片
     */
    Page<Image> findByAlbumIdAndDeleted(String albumId, boolean deleted, Pageable pageable);

    /**
     * 根据URL检查图片是否已存在（未删除）
     */
    boolean existsByUrlAndDeletedFalse(String url);
    
    // ==================== 高级搜索查询方法 ====================
    
    /**
     * 按多个标签查询（包含任意一个标签即可）
     */
    @Query("SELECT DISTINCT i FROM Image i WHERE i.deleted = false AND EXISTS (SELECT t FROM i.tags t WHERE t IN :tags)")
    Page<Image> findByAnyTagIn(@Param("tags") List<String> tags, Pageable pageable);
    
    /**
     * 按多个标签查询（必须包含所有标签）
     */
    @Query("SELECT DISTINCT i FROM Image i WHERE i.deleted = false AND (SELECT COUNT(t) FROM i.tags t WHERE t IN :tags) = :tagCount")
    Page<Image> findByAllTagsIn(@Param("tags") List<String> tags, @Param("tagCount") Long tagCount, Pageable pageable);
    
    /**
     * 按日期范围查询
     */
    @Query("SELECT i FROM Image i WHERE i.deleted = false AND i.createdAt >= :startDate AND i.createdAt <= :endDate")
    Page<Image> findByCreatedAtBetweenAndDeletedFalse(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate, Pageable pageable);
    
    /**
     * 按关键词和日期范围查询
     */
    @Query("SELECT i FROM Image i WHERE i.deleted = false AND (LOWER(i.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(i.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND i.createdAt >= :startDate AND i.createdAt <= :endDate")
    Page<Image> searchByKeywordAndDateRange(@Param("keyword") String keyword, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate, Pageable pageable);
    
    /**
     * 按关键词和标签查询
     */
    @Query("SELECT DISTINCT i FROM Image i WHERE i.deleted = false AND (LOWER(i.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(i.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND EXISTS (SELECT t FROM i.tags t WHERE t IN :tags)")
    Page<Image> searchByKeywordAndTags(@Param("keyword") String keyword, @Param("tags") List<String> tags, Pageable pageable);
    
    /**
     * 按文件类型列表查询
     */
    @Query("SELECT i FROM Image i WHERE i.deleted = false AND i.fileType IN :fileTypes")
    Page<Image> findByFileTypeInAndDeletedFalse(@Param("fileTypes") List<String> fileTypes, Pageable pageable);
    
    /**
     * 按相册ID列表查询
     */
    @Query("SELECT i FROM Image i WHERE i.deleted = false AND i.albumId IN :albumIds")
    Page<Image> findByAlbumIdInAndDeletedFalse(@Param("albumIds") List<String> albumIds, Pageable pageable);
    
    /**
     * 简单查询 - 按关键词搜索
     */
    @Query("SELECT i FROM Image i WHERE i.deleted = false AND LOWER(i.title) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Image> findByTitleContainingIgnoreCase(@Param("keyword") String keyword, Pageable pageable);
    
    /**
     * 简单查询 - 按关键词搜索（标题或描述）
     */
    @Query("SELECT i FROM Image i WHERE i.deleted = false AND (LOWER(i.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(i.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Image> findByTitleOrDescriptionContainingIgnoreCase(@Param("keyword") String keyword, Pageable pageable);
    
    /**
     * 按标签列表查询
     */
    @Query("SELECT DISTINCT i FROM Image i WHERE i.deleted = false AND EXISTS (SELECT t FROM i.tags t WHERE t IN :tags)")
    Page<Image> findByTagsIn(@Param("tags") List<String> tags, Pageable pageable);
    
    /**
     * 组合查询 - 关键词 + 收藏状态
     */
    @Query("SELECT i FROM Image i WHERE i.deleted = false AND i.favorite = :favorite AND (LOWER(i.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(i.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Image> findByFavoriteAndKeywordContaining(@Param("favorite") Boolean favorite, @Param("keyword") String keyword, Pageable pageable);
    
    /**
     * 组合查询 - 关键词 + 主图状态
     */
    @Query("SELECT i FROM Image i WHERE i.deleted = false AND i.isMainImage = :onlyMainImage AND (LOWER(i.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(i.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Image> findByIsMainImageAndKeywordContaining(@Param("onlyMainImage") Boolean onlyMainImage, @Param("keyword") String keyword, Pageable pageable);
}
