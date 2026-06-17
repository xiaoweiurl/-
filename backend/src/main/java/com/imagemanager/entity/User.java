package com.imagemanager.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 用户实体类
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_email", columnList = "email", unique = true),
    @Index(name = "idx_user_username", columnList = "username", unique = true)
})
public class User {
    
    /**
     * 用户ID
     */
    @Id
    @Column(length = 36)
    private String id;
    
    /**
     * 用户名
     */
    @Column(length = 50, nullable = false, unique = true)
    private String username;
    
    /**
     * 密码
     */
    @Column(length = 255)
    private String password;
    
    /**
     * 邮箱
     */
    @Column(length = 100, nullable = false, unique = true)
    private String email;
    
    /**
     * 头像URL
     */
    @Column(length = 500)
    private String avatarUrl;
    
    /**
     * 昵称
     */
    @Column(length = 50)
    private String nickname;
    
    /**
     * 个人简介
     */
    @Column(columnDefinition = "TEXT")
    private String bio;
    
    /**
     * 手机号
     */
    @Column(length = 20)
    private String phone;
    
    /**
     * 所属公司（宝娜斯/盈云）
     */
    @Column(length = 20)
    private String company;
    
    /**
     * 用户角色
     */
    @Column(length = 20)
    private String role;
    
    /**
     * 会员类型（free, pro, premium）
     */
    @Column(length = 20)
    private String membership;
    
    /**
     * 存储空间使用量（字节）
     */
    private Long storageUsed;
    
    /**
     * 存储空间总量（字节）
     */
    private Long storageLimit;
    
    /**
     * 创建时间
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    /**
     * 最后登录时间
     */
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
}
