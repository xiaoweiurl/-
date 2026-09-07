package com.imagemanager.service;

import java.util.List;
import java.util.Map;

/**
 * ERP 数据同步服务（增量同步：按「数据库最新时间→当前时间」过滤数据，分模块同步）
 */
public interface ErpSyncService {

    /**
     * 获取全部模块同步状态 + 汇总统计
     */
    Map<String, Object> getStatus();

    /**
     * 同步单个模块（增量）
     *
     * @param moduleKey 模块标识（orders/neiyi-gongyidan/siwa-gongyidan/gongyi-bujian/gongyi-gongxu/gongxu-gongjia/yuanliao-bom）
     * @return 本次同步日志
     */
    Map<String, Object> syncModule(String moduleKey);

    /**
     * 同步全部模块（串行执行，返回各模块日志列表）
     */
    List<Map<String, Object>> syncAll();

    /**
     * 同步日志列表（倒序）
     */
    List<Map<String, Object>> getLogs(int limit);

    /**
     * 清空同步日志
     */
    void clearLogs();
}
