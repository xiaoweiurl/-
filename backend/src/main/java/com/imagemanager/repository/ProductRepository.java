package com.imagemanager.repository;

import com.imagemanager.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 商品数据访问接口
 *
 * @author Image Manager Team
 * @version 1.0.0
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, String> {

    /**
     * 根据用户ID查询商品列表
     */
    List<Product> findByUserId(String userId);

    /**
     * 根据相册ID查询商品列表
     */
    List<Product> findByAlbumId(String albumId);

    /**
     * 根据相册ID和用户ID查询商品列表
     */
    List<Product> findByAlbumIdAndUserId(String albumId, String userId);

    /**
     * 根据分类查询商品列表
     */
    List<Product> findByCategory(String category);

    /**
     * 根据用户ID和分类查询商品列表
     */
    List<Product> findByUserIdAndCategory(String userId, String category);

    /**
     * 查询用户的所有商品（按创建时间倒序）
     */
    @Query("SELECT p FROM Product p WHERE p.userId = :userId ORDER BY p.createdAt DESC")
    List<Product> findAllByUserIdOrderByCreatedAtDesc(@Param("userId") String userId);

    /**
     * 根据名称查询商品（模糊查询）
     */
    @Query("SELECT p FROM Product p WHERE p.userId = :userId AND p.name LIKE %:name% ORDER BY p.createdAt DESC")
    List<Product> searchByName(@Param("userId") String userId, @Param("name") String name);

    /**
     * 根据商品名称精确查找（用于判断是否重复导入）
     */
    Optional<Product> findByName(String name);
}
