package com.imagemanager.repository;

import com.imagemanager.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 文档数据访问层
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, String> {
    
    /**
     * 根据用户ID和分类查询文档列表（分页）
     */
    Page<Document> findByUserIdAndCategoryAndDeletedFalse(String userId, String category, Pageable pageable);
    
    /**
     * 根据用户ID查询文档列表（分页）
     */
    Page<Document> findByUserIdAndDeletedFalse(String userId, Pageable pageable);
    
    /**
     * 根据用户ID和分类查询文档列表（不分页）
     */
    List<Document> findByUserIdAndCategoryAndDeletedFalse(String userId, String category);
    
    /**
     * 根据用户ID查询文档列表（不分页）
     */
    List<Document> findByUserIdAndDeletedFalse(String userId);
    
    /**
     * 根据用户ID和分类统计文档数量
     */
    long countByUserIdAndCategoryAndDeletedFalse(String userId, String category);
    
    /**
     * 根据用户ID统计文档数量
     */
    long countByUserIdAndDeletedFalse(String userId);
    
    /**
     * 根据ID和用户ID查询文档
     */
    Optional<Document> findByIdAndUserId(String id, String userId);
    
    /**
     * 批量删除文档（软删除）
     */
    @Modifying
    @Query("UPDATE Document d SET d.deleted = true, d.updatedAt = CURRENT_TIMESTAMP WHERE d.id IN :ids")
    int softDeleteByIds(@Param("ids") List<String> ids);
    
    /**
     * 恢复文档（取消软删除）
     */
    @Modifying
    @Query("UPDATE Document d SET d.deleted = false, d.updatedAt = CURRENT_TIMESTAMP WHERE d.id IN :ids")
    int restoreByIds(@Param("ids") List<String> ids);
    
    /**
     * 根据用户ID统计各分类文档数量
     */
    @Query("SELECT d.category, COUNT(d) FROM Document d WHERE d.userId = :userId AND d.deleted = false GROUP BY d.category")
    List<Object[]> countByCategoryGroupByCategory(@Param("userId") String userId);
}
