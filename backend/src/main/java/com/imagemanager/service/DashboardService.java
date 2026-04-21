package com.imagemanager.service;

import com.imagemanager.dto.DashboardStatsResponse;

import java.util.List;

/**
 * 仪表盘统计服务接口
 *
 * @author Image Manager Team
 * @version 1.0.0
 */
public interface DashboardService {

    /**
     * 获取仪表盘统计数据
     *
     * @param period 统计周期（week, month, year）
     * @return 统计数据
     */
    DashboardStatsResponse getDashboardStats(String period);

    /**
     * 获取热门资源
     *
     * @param limit 返回数量限制
     * @return 热门资源列表
     */
    List<DashboardStatsResponse.HotResource> getHotResources(int limit);

    /**
     * 获取热门相册
     *
     * @param limit 返回数量限制
     * @return 热门相册列表
     */
    List<DashboardStatsResponse.HotAlbum> getHotAlbums(int limit);

    /**
     * 获取活跃度统计
     *
     * @return 活跃度统计数据
     */
    DashboardStatsResponse.ActivityStats getActivityStats();
}
