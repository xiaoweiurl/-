package com.imagemanager.util;

import lombok.extern.slf4j.Slf4j;

/**
 * 密码强度验证工具
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Slf4j
public class PasswordValidator {
    
    // 密码强度等级
    public enum Strength {
        WEAK(0, "弱"),
        FAIR(1, "中等"),
        GOOD(2, "良好"),
        STRONG(3, "强"),
        VERY_STRONG(4, "非常强");
        
        private final int level;
        private final String description;
        
        Strength(int level, String description) {
            this.level = level;
            this.description = description;
        }
        
        public int getLevel() {
            return level;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 验证密码强度
     * 
     * @param password 密码
     * @return 密码强度等级
     */
    public static Strength checkStrength(String password) {
        if (password == null || password.isEmpty()) {
            return Strength.WEAK;
        }
        
        int score = 0;
        
        // 长度检查
        if (password.length() >= 8) score++;
        if (password.length() >= 12) score++;
        if (password.length() >= 16) score++;
        
        // 包含小写字母
        if (password.matches(".*[a-z].*")) score++;
        
        // 包含大写字母
        if (password.matches(".*[A-Z].*")) score++;
        
        // 包含数字
        if (password.matches(".*\\d.*")) score++;
        
        // 包含特殊字符
        if (password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) score++;
        
        // 根据分数返回强度等级
        if (score <= 2) return Strength.WEAK;
        if (score <= 3) return Strength.FAIR;
        if (score <= 4) return Strength.GOOD;
        if (score <= 5) return Strength.STRONG;
        return Strength.VERY_STRONG;
    }
    
    /**
     * 验证密码是否满足最低要求
     * 
     * @param password 密码
     * @param minLength 最小长度
     * @return true 表示满足要求
     */
    public static boolean isValid(String password, int minLength) {
        if (password == null || password.length() < minLength) {
            return false;
        }
        
        Strength strength = checkStrength(password);
        return strength.getLevel() >= Strength.FAIR.getLevel();
    }
    
    /**
     * 验证密码是否满足最低要求（默认8位）
     */
    public static boolean isValid(String password) {
        return isValid(password, 8);
    }
    
    /**
     * 获取密码强度描述
     */
    public static String getStrengthDescription(String password) {
        Strength strength = checkStrength(password);
        StringBuilder desc = new StringBuilder();
        desc.append("强度: ").append(strength.getDescription());
        
        // 提供改进建议
        if (strength.getLevel() < Strength.GOOD.getLevel()) {
            desc.append("。建议: ");
            if (password.length() < 8) {
                desc.append("密码长度至少8位；");
            }
            if (!password.matches(".*[a-z].*")) {
                desc.append("添加小写字母；");
            }
            if (!password.matches(".*[A-Z].*")) {
                desc.append("添加大写字母；");
            }
            if (!password.matches(".*\\d.*")) {
                desc.append("添加数字；");
            }
            if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) {
                desc.append("添加特殊字符");
            }
        }
        
        return desc.toString();
    }
}
