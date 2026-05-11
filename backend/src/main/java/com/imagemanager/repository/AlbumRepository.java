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
     * 按用户和完整名称查询（精确匹配）
     */
    Optional<Album> findByUserIdAndFullName(String userId, String fullName);
    
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
    
    /**
     * 查询顶级相册（parentId 为 null）
     */
    List<Album> findByUserIdAndParentIdIsNullOrderBySortOrderAsc(String userId);
    
    /**
     * 查询子相册（根据父ID）
     */
    List<Album> findByUserIdAndParentIdOrderBySortOrderAsc(String userId, String parentId);
    
    /**
     * 查询子相册（根据父ID，不限制用户）
     */
    List<Album> findByParentIdOrderBySortOrderAsc(String parentId);
    
    /**
     * 根据路径查询相册
     */
    Optional<Album> findByUserIdAndPath(String userId, String path);
    
    /**
     * 根据路径前缀模糊查找相册（用于处理文件名包含父相册名称的情况）
     */
    Optional<Album> findByUserIdAndPathStartingWith(String userId, String pathPrefix);
    
    /**
     * 根据路径关键字模糊查找相册（用于处理文件名包含父相册名称的情况）
     */
    List<Album> findByUserIdAndPathContaining(String userId, String pathKeyword);
    
    /**
     * 根据父相册名称+子相册名称精确查找相册
     * 用于判断导入时相册是否已存在
     */
    Optional<Album> findByUserIdAndNameAndParentId(String userId, String name, String parentId);
    
    /**
     * 查询所有子相册（包括深层级）
     */
    @Query("SELECT a FROM Album a WHERE a.path LIKE CONCAT(:pathPrefix, '%') AND a.userId = :userId")
    List<Album> findAllDescendants(@Param("userId") String userId, @Param("pathPrefix") String pathPrefix);
}
