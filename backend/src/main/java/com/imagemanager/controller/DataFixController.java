package com.imagemanager.controller;

import com.imagemanager.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 数据修复控制器
 * 用于修复数据不一致问题
 */
@RestController
@RequestMapping("/fix")
@Slf4j
public class DataFixController {

    @org.springframework.beans.factory.annotation.Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 修复 is_main_image 字段 - 将所有图片标记为主图
     */
    @PostMapping("/is-main-image")
    public ApiResponse<String> fixIsMainImage() {
        try {
            // 统计修复前的值分布
            List<Map<String, Object>> beforeStats = jdbcTemplate.queryForList(
                "SELECT is_main_image, COUNT(*) as count FROM images WHERE album_id = 'album-underwear' GROUP BY is_main_image"
            );
            log.info("修复前的值分布: {}", beforeStats);

            // 统计所有图片的 is_main_image 值分布
            List<Map<String, Object>> allStats = jdbcTemplate.queryForList(
                "SELECT is_main_image, COUNT(*) as count FROM images GROUP BY is_main_image"
            );
            log.info("所有图片的 is_main_image 值分布: {}", allStats);

            // 修复：将内衣相册的所有图片都标记为主图
            int updated = jdbcTemplate.update(
                "UPDATE images SET is_main_image = true WHERE album_id = 'album-underwear'"
            );
            log.info("修复了 {} 条记录", updated);

            // 统计修复后的值分布
            List<Map<String, Object>> afterStats = jdbcTemplate.queryForList(
                "SELECT is_main_image, COUNT(*) as count FROM images WHERE album_id = 'album-underwear' GROUP BY is_main_image"
            );
            log.info("修复后的值分布: {}", afterStats);

            // 统计主图数量
            Integer mainImageCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM images WHERE album_id = 'album-underwear' AND is_main_image = true",
                Integer.class
            );
            log.info("主图总数 (is_main_image = true): {}", mainImageCount);

            // 统计所有图片数量
            Integer allImageCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM images WHERE album_id = 'album-underwear'",
                Integer.class
            );
            log.info("所有图片总数: {}", allImageCount);

            return ApiResponse.success(
                "修复完成！修复了 " + updated + " 条记录，" +
                "现在有 " + mainImageCount + " 张主图，" +
                "总共 " + allImageCount + " 张图片。" +
                "修复前值分布: " + beforeStats +
                "，修复后值分布: " + afterStats
            );
        } catch (Exception e) {
            log.error("修复失败", e);
            return ApiResponse.error("修复失败: " + e.getMessage());
        }
    }

    /**
     * 查看 is_main_image 字段的值分布
     */
    @PostMapping("/check-is-main-image")
    public ApiResponse<String> checkIsMainImage() {
        try {
            // 统计所有图片的 is_main_image 值分布
            List<Map<String, Object>> allStats = jdbcTemplate.queryForList(
                "SELECT is_main_image, COUNT(*) as count FROM images WHERE album_id = 'album-underwear' GROUP BY is_main_image"
            );

            // 统计主图数量
            Integer mainImageCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM images WHERE album_id = 'album-underwear' AND is_main_image = true",
                Integer.class
            );

            // 统计所有图片数量
            Integer allImageCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM images WHERE album_id = 'album-underwear'",
                Integer.class
            );

            return ApiResponse.success(
                "内衣相册统计：" +
                "总图片数: " + allImageCount + "，" +
                "主图数 (is_main_image=true): " + mainImageCount + "。" +
                "值分布: " + allStats
            );
        } catch (Exception e) {
            log.error("查询失败", e);
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }
}
