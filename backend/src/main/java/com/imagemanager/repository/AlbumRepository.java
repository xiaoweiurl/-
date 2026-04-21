package com.imagemanager.repository;

import com.imagemanager.entity.Album;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 相册数据访问层
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Repository
public interface AlbumRepository extends JpaRepository<Album, String> {
    
    /**
     * 查询用户的所有相册
     */
    List<Album> findByUserIdOrderBySortOrderAsc(String userId);
    
    /**
     * 查询所有相册（按排序）
     */
    List<Album> findAllByOrderBySortOrderAsc();
    
    /**
     * 查询系统预置相册
     */
    List<Album> findByIsSystemTrue();
    
    /**
     * 按名称查询相册
     */
    Optional<Album> findByName(String name);
    
    /**
     * 按用户和名称查询
     */
    Optional<Album> findByUserIdAndName(String userId, String name);
    
    /**
     * 检查相册名称是否存在
     */
    boolean existsByName(String name);
    
    /**
     * 检查用户下相册名称是否存在
     */
    boolean existsByUserIdAndName(String userId, String name);
    
    /**
     * 统计用户的相册数量
     */
    long countByUserId(String userId);
    
    /**
     * 查询包含指定关键词的相册
     */
    @Query("SELECT a FROM Album a JOIN a.keywords k WHERE LOWER(k) LIKE LOWER(:keyword)")
    List<Album> findByKeyword(@Param("keyword") String keyword);
}
