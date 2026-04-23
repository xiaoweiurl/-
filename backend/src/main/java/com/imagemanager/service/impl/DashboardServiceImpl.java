package com.imagemanager.service.impl;

import com.imagemanager.dto.DashboardStatsResponse;
import com.imagemanager.entity.Album;
import com.imagemanager.entity.Image;
import com.imagemanager.repository.AlbumRepository;
import com.imagemanager.repository.ImageRepository;
import com.imagemanager.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 仪表盘统计服务实现
 * 使用已有的 Repository 组合实现统计功能
 *
 * @author Image Manager Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final ImageRepository imageRepository;
    private final AlbumRepository albumRepository;

    @Override
    public DashboardStatsResponse getDashboardStats(String period) {
        log.info("[Dashboard] 获取仪表盘统计数据，周期: {}", period);

        // 计算统计天数
        int days = switch (period.toLowerCase()) {
            case "week" -> 7;
            case "year" -> 365;
            default -> 30; // month
        };

        // 构建响应
        DashboardStatsResponse response = new DashboardStatsResponse();

        // 1. 概览统计
        response.setOverview(getOverviewStats());

        // 2. 上传趋势和存储趋势
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        List<DashboardStatsResponse.TrendData> trendData = getTrendData(startDate, days);
        response.setUploadTrend(trendData);
        response.setStorageTrend(trendData);

        // 3. 相册分布
        response.setAlbumDistribution(getAlbumDistribution());

        // 4. 热门标签
        response.setTopTags(getTopTags(20));

        // 5. 文件类型统计
        response.setFileTypeStats(getFileTypeStats());

        log.info("[Dashboard] 统计数据获取完成");
        return response;
    }

    /**
     * 获取概览统计
     */
    private DashboardStatsResponse.OverviewStats getOverviewStats() {
        // 获取所有未删除图片用于统计（EAGER fetch自动加载tags）
        List<Image> allImages = imageRepository.findByDeletedFalse();

        long totalImages = allImages.size();
        long totalSize = allImages.stream().mapToLong(Image::getSize).sum();
        long favoritesCount = allImages.stream()
            .filter(img -> img.getFavorite() != null && img.getFavorite())
            .count();

        // 回收站图片
        List<Image> trashImages = imageRepository.findByDeletedTrueAndIsMainImageTrueList();

        // 近7天和30天上传
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime sevenDaysAgo = now.minusDays(7);
        LocalDateTime thirtyDaysAgo = now.minusDays(30);

        // 今日上传数
        long todayUploads = allImages.stream()
            .filter(img -> img.getCreatedAt() != null && !img.getCreatedAt().isBefore(todayStart))
            .count();

        long recentUploads7d = allImages.stream()
            .filter(img -> img.getCreatedAt() != null && img.getCreatedAt().isAfter(sevenDaysAgo))
            .count();

        long recentUploads30d = allImages.stream()
            .filter(img -> img.getCreatedAt() != null && img.getCreatedAt().isAfter(thirtyDaysAgo))
            .count();

        // 今日预览次数（所有图片的 viewCount 累加）
        long todayViews = allImages.stream()
            .mapToLong(img -> img.getViewCount() != null ? img.getViewCount() : 0)
            .sum();

        // 今日下载次数（所有图片的 downloadCount 累加）
        long todayDownloads = allImages.stream()
            .mapToLong(img -> img.getDownloadCount() != null ? img.getDownloadCount() : 0)
            .sum();

        // 相册数量和标签数量
        long totalAlbums = albumRepository.count();

        // 从所有图片中提取标签
        long totalTags = allImages.stream()
            .flatMap(img -> img.getTags() != null ? img.getTags().stream() : java.util.stream.Stream.empty())
            .distinct()
            .count();

        return new DashboardStatsResponse.OverviewStats(
            totalImages,
            totalSize,
            totalAlbums,
            totalTags,
            favoritesCount,
            (long) trashImages.size(),
            todayUploads,
            recentUploads7d,
            recentUploads30d,
            todayViews,
            todayDownloads
        );
    }

    /**
     * 获取趋势数据（按天补齐）
     */
    private List<DashboardStatsResponse.TrendData> getTrendData(LocalDateTime startDate, int days) {
        // 获取所有未删除图片
        List<Image> allImages = imageRepository.findAll().stream()
            .filter(img -> (img.getDeleted() == null || !img.getDeleted()) && img.getCreatedAt() != null)
            .collect(Collectors.toList());

        // 按日期分组统计
        Map<LocalDate, List<Image>> imagesByDate = allImages.stream()
            .filter(img -> img.getCreatedAt().isAfter(startDate))
            .collect(Collectors.groupingBy(
                img -> img.getCreatedAt().toLocalDate()
            ));

        // 补齐所有日期
        List<DashboardStatsResponse.TrendData> result = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate today = LocalDate.now();

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            String dateStr = date.format(formatter);

            List<Image> dayImages = imagesByDate.getOrDefault(date, new ArrayList<>());
            long count = dayImages.size();
            long size = dayImages.stream().mapToLong(Image::getSize).sum();

            result.add(new DashboardStatsResponse.TrendData(dateStr, count, size));
        }

        return result;
    }

    /**
     * 获取相册分布
     */
    private List<DashboardStatsResponse.AlbumDistribution> getAlbumDistribution() {
        List<Album> albums = albumRepository.findAll();
        List<Image> allImages = imageRepository.findAll().stream()
            .filter(img -> img.getDeleted() == null || !img.getDeleted())
            .collect(Collectors.toList());

        long total = allImages.size();

        // 统计每个相册的图片数量
        Map<String, Long> albumCounts = allImages.stream()
            .collect(Collectors.groupingBy(
                img -> img.getAlbumId() != null ? img.getAlbumId() : "未分类",
                Collectors.counting()
            ));

        // 构建分布数据
        List<DashboardStatsResponse.AlbumDistribution> result = new ArrayList<>();
        for (Album album : albums) {
            String albumId = album.getId();
            String name = album.getName();
            Long count = albumCounts.getOrDefault(albumId, 0L);
            int percentage = total > 0 ? (int) Math.round((count * 100.0) / total) : 0;

            result.add(new DashboardStatsResponse.AlbumDistribution(name, count, percentage));
        }

        // 添加未分类图片
        Long uncategorizedCount = albumCounts.getOrDefault("未分类", 0L);
        if (uncategorizedCount > 0) {
            int percentage = total > 0 ? (int) Math.round((uncategorizedCount * 100.0) / total) : 0;
            result.add(new DashboardStatsResponse.AlbumDistribution("未分类", uncategorizedCount, percentage));
        }

        // 按数量排序
        result.sort((a, b) -> Long.compare(b.getCount(), a.getCount()));

        return result;
    }

    /**
     * 获取热门标签
     */
    private List<DashboardStatsResponse.TagStat> getTopTags(int limit) {
        // 获取所有未删除图片的标签（EAGER fetch自动加载tags）
        List<Image> allImages = imageRepository.findByDeletedFalse();

        // 统计标签使用次数
        Map<String, Long> tagCounts = new HashMap<>();
        for (Image image : allImages) {
            if (image.getTags() != null) {
                for (String tag : image.getTags()) {
                    tagCounts.merge(tag, 1L, Long::sum);
                }
            }
        }

        // 排序并限制数量
        return tagCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(limit)
            .map(entry -> new DashboardStatsResponse.TagStat(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());
    }

    /**
     * 获取文件类型统计
     */
    private List<DashboardStatsResponse.FileTypeStat> getFileTypeStats() {
        // 获取所有未删除图片
        List<Image> allImages = imageRepository.findAll().stream()
            .filter(img -> img.getDeleted() == null || !img.getDeleted())
            .collect(Collectors.toList());

        // 按文件类型分组统计
        Map<String, List<Image>> imagesByType = allImages.stream()
            .collect(Collectors.groupingBy(
                img -> img.getFileType() != null ? img.getFileType().toUpperCase() : "UNKNOWN"
            ));

        // 构建统计结果
        List<DashboardStatsResponse.FileTypeStat> result = new ArrayList<>();
        for (Map.Entry<String, List<Image>> entry : imagesByType.entrySet()) {
            String type = entry.getKey();
            Long count = (long) entry.getValue().size();
            Long size = entry.getValue().stream().mapToLong(Image::getSize).sum();
            result.add(new DashboardStatsResponse.FileTypeStat(type, count, size));
        }

        // 按数量排序
        result.sort((a, b) -> Long.compare(b.getCount(), a.getCount()));

        return result;
    }

    /**
     * 获取热门资源（按浏览/下载/收藏排序）
     */
    public List<DashboardStatsResponse.HotResource> getHotResources(int limit) {
        log.info("[Dashboard] 获取热门资源，数量: {}", limit);

        // 获取所有未删除图片
        List<Image> allImages = imageRepository.findAll().stream()
                .filter(img -> img.getDeleted() == null || !img.getDeleted())
                .collect(Collectors.toList());

        // 获取所有相册
        Map<String, Album> albumMap = albumRepository.findAll().stream()
                .collect(Collectors.toMap(Album::getId, album -> album, (a, b) -> a));

        // 计算热度分数（浏览3 + 下载5 + 收藏10）
        return allImages.stream()
                .map(img -> {
                    DashboardStatsResponse.HotResource resource = new DashboardStatsResponse.HotResource();
                    resource.setId(img.getId());
                    resource.setTitle(img.getTitle());
                    resource.setThumbnailUrl(img.getThumbnailUrl() != null ? img.getThumbnailUrl() : img.getUrl());
                    resource.setViewCount(img.getViewCount() != null ? img.getViewCount() : 0L);
                    resource.setDownloadCount(img.getDownloadCount() != null ? img.getDownloadCount() : 0L);
                    resource.setFavoriteCount(img.getFavorite() != null && img.getFavorite() ? 1L : 0L);
                    // 获取相册名称
                    String albumName = img.getAlbumId() != null && albumMap.containsKey(img.getAlbumId())
                            ? albumMap.get(img.getAlbumId()).getName() : "未分类";
                    resource.setAlbumName(albumName);
                    return resource;
                })
                .sorted((a, b) -> {
                    // 按热度排序：收藏数*10 + 下载数*5 + 浏览数*1
                    long scoreA = a.getFavoriteCount() * 10 + a.getDownloadCount() * 5 + a.getViewCount();
                    long scoreB = b.getFavoriteCount() * 10 + b.getDownloadCount() * 5 + b.getViewCount();
                    return Long.compare(scoreB, scoreA);
                })
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 获取热门相册
     */
    public List<DashboardStatsResponse.HotAlbum> getHotAlbums(int limit) {
        log.info("[Dashboard] 获取热门相册，数量: {}", limit);

        // 获取所有未删除图片
        List<Image> allImages = imageRepository.findAll().stream()
                .filter(img -> img.getDeleted() == null || !img.getDeleted())
                .collect(Collectors.toList());

        // 按相册分组
        Map<String, List<Image>> imagesByAlbum = allImages.stream()
                .collect(Collectors.groupingBy(
                        img -> img.getAlbumId() != null ? img.getAlbumId() : "uncategorized"
                ));

        // 获取相册信息
        Map<String, Album> albumMap = albumRepository.findAll().stream()
                .collect(Collectors.toMap(Album::getId, album -> album, (a, b) -> a));

        // 转换为热门相册数据
        return imagesByAlbum.entrySet().stream()
                .map(entry -> {
                    DashboardStatsResponse.HotAlbum hotAlbum = new DashboardStatsResponse.HotAlbum();
                    String albumId = entry.getKey();
                    List<Image> images = entry.getValue();

                    hotAlbum.setId(albumId);
                    hotAlbum.setImageCount((long) images.size());
                    hotAlbum.setTotalSize(images.stream().mapToLong(img -> img.getSize() != null ? img.getSize() : 0).sum());

                    // 设置相册名称和封面
                    if ("uncategorized".equals(albumId)) {
                        hotAlbum.setName("未分类");
                        hotAlbum.setCoverUrl(null);
                    } else if (albumMap.containsKey(albumId)) {
                        Album album = albumMap.get(albumId);
                        hotAlbum.setName(album.getName());
                        hotAlbum.setCoverUrl(album.getCoverUrl());
                    } else {
                        hotAlbum.setName("未知相册");
                        hotAlbum.setCoverUrl(null);
                    }

                    return hotAlbum;
                })
                .sorted((a, b) -> Long.compare(b.getImageCount(), a.getImageCount()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 获取今日活跃度统计
     */
    public DashboardStatsResponse.ActivityStats getActivityStats() {
        log.info("[Dashboard] 获取活跃度统计");

        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime yesterday = today.minusDays(1);

        // 获取今日图片
        List<Image> allImages = imageRepository.findAll().stream()
                .filter(img -> img.getDeleted() == null || !img.getDeleted())
                .collect(Collectors.toList());

        List<Image> todayImages = allImages.stream()
                .filter(img -> img.getCreatedAt() != null && img.getCreatedAt().isAfter(today))
                .collect(Collectors.toList());

        List<Image> yesterdayImages = allImages.stream()
                .filter(img -> img.getCreatedAt() != null && img.getCreatedAt().isAfter(yesterday) && img.getCreatedAt().isBefore(today))
                .collect(Collectors.toList());

        // 今日统计
        Long todayUploads = (long) todayImages.size();
        Long todayViews = allImages.stream()
                .mapToLong(img -> img.getViewCount() != null ? img.getViewCount() : 0)
                .sum();
        Long todayDownloads = allImages.stream()
                .mapToLong(img -> img.getDownloadCount() != null ? img.getDownloadCount() : 0)
                .sum();
        Long todayFavorites = allImages.stream()
                .filter(img -> img.getFavorite() != null && img.getFavorite())
                .count();

        // 计算增长率（相对于昨日）
        double growthRate = 0.0;
        if (yesterdayImages.size() > 0) {
            growthRate = ((double) todayImages.size() - yesterdayImages.size()) / yesterdayImages.size() * 100;
        }

        return new DashboardStatsResponse.ActivityStats(
                todayUploads,
                todayViews,
                todayDownloads,
                todayFavorites,
                growthRate
        );
    }
}
