package com.imagemanager.repository;

import com.imagemanager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户数据访问层
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Repository
public interface UserRepository extends JpaRepository<User, String> {
    
    /**
     * 按用户名查询
     */
    Optional<User> findByUsername(String username);
    
    /**
     * 按邮箱查询
     */
    Optional<User> findByEmail(String email);
    
    /**
     * 检查用户名是否存在
     */
    boolean existsByUsername(String username);
    
    /**
     * 检查邮箱是否存在
     */
    boolean existsByEmail(String email);
}
