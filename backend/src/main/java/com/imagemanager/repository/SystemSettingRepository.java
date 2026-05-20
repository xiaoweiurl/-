package com.imagemanager.repository;

import com.imagemanager.entity.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {
    
    Optional<SystemSetting> findBySettingKey(String settingKey);
    
    default String getValue(String key, String defaultValue) {
        return findBySettingKey(key).map(SystemSetting::getSettingValue).orElse(defaultValue);
    }
    
    default int getIntValue(String key, int defaultValue) {
        try {
            return findBySettingKey(key)
                    .map(s -> Integer.parseInt(s.getSettingValue()))
                    .orElse(defaultValue);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    default long getLongValue(String key, long defaultValue) {
        try {
            return findBySettingKey(key)
                    .map(s -> Long.parseLong(s.getSettingValue()))
                    .orElse(defaultValue);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
