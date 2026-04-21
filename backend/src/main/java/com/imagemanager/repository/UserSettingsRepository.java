package com.imagemanager.repository;

import com.imagemanager.entity.UserSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户设置仓库
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettingsEntity, String> {
    
    /**
     * 根据用户ID查找设置
     */
    Optional<UserSettingsEntity> findByUserId(String userId);
    
    /**
     * 根据用户ID删除设置
     */
    void deleteByUserId(String userId);
}
