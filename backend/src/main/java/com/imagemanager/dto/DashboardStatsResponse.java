package com.imagemanager.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * 仪表盘统计数据响应
 *
 * @author Image Manager Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsResponse {

    /**
     * 概览统计
     */
    private OverviewStats overview;

    /**
     * 上传趋势
     */
    private List<TrendData> uploadTrend;

    /**
     * 存储趋势
     */
    private List<TrendData> storageTrend;

    /**
     * 相册分布
     */
    private List<AlbumDistribution> albumDistribution;

    /**
     * 热门标签
     */
    private List<TagStat> topTags;

    /**
     * 文件类型统计
     */
    private List<FileTypeStat> fileTypeStats;

    /**
     * 概览统计数据
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverviewStats {
        /** 总图片数 */
        private Long totalImages;
        /** 总存储大小（字节） */
        private Long totalSize;
        /** 相册数量 */
        private Long totalAlbums;
        /** 标签总数 */
        private Long totalTags;
        /** 收藏图片数 */
        private Long favoritesCount;
        /** 回收站图片数 */
        private Long trashCount;
        /** 今日上传数 */
        private Long todayUploads;
        /** 近7天上传数 */
        private Long recentUploads7d;
        /** 近30天上传数 */
        private Long recentUploads30d;
        /** 今日预览次数 */
        private Long todayViews;
        /** 今日下载次数 */
        private Long todayDownloads;
    }

    /**
     * 趋势数据
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendData {
        /** 日期（yyyy-MM-dd） */
        private String date;
        /** 数量 */
        private Long count;
        /** 大小（字节） */
        private Long size;
    }

    /**
     * 相册分布数据
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlbumDistribution {
        /** 相册名称 */
        private String name;
        /** 图片数量 */
        private Long count;
        /** 占比（百分比） */
        private Integer percentage;
    }

    /**
     * 标签统计
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TagStat {
        /** 标签名称 */
        private String name;
        /** 使用次数 */
        private Long count;
    }

    /**
     * 文件类型统计
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileTypeStat {
        /** 文件类型 */
        private String type;
        /** 文件数量 */
        private Long count;
        /** 总大小（字节） */
        private Long size;
    }

    /**
     * 热门资源数据
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HotResource {
        /** 图片ID */
        private String id;
        /** 图片标题 */
        private String title;
        /** 缩略图URL */
        private String thumbnailUrl;
        /** 浏览次数 */
        private Long viewCount;
        /** 下载次数 */
        private Long downloadCount;
        /** 收藏数 */
        private Long favoriteCount;
        /** 所属相册 */
        private String albumName;
    }

    /**
     * 热门相册数据
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HotAlbum {
        /** 相册ID */
        private String id;
        /** 相册名称 */
        private String name;
        /** 图片数量 */
        private Long imageCount;
        /** 总大小 */
        private Long totalSize;
        /** 封面图URL */
        private String coverUrl;
    }

    /**
     * 活跃度统计
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityStats {
        /** 今日上传 */
        private Long todayUploads;
        /** 今日浏览 */
        private Long todayViews;
        /** 今日下载 */
        private Long todayDownloads;
        /** 今日收藏 */
        private Long todayFavorites;
        /** 较昨日增长 */
        private Double growthRate;
    }
}
