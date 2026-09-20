package com.imagemanager.service;

import java.util.Optional;

/**
 * 打样工作通知投递记录。HikariCP auto-commit=false，实现侧写操作必须包事务。
 */
public interface GoodsSamplerNoticeStore {

    void saveAssignment(GoodsSamplerNoticeRecord record);

    /**
     * 只更新最近投递结果，不覆盖已发出的 ding_userid / task_id（失败补偿、PENDING、跳过原因）。
     */
    void saveLastResult(long goodsId, String samplerName, String status, String message,
                        String kind, boolean persistOk);

    Optional<GoodsSamplerNoticeRecord> findByGoodsId(long goodsId);

    void markFollowupSent(long goodsId, long assignmentTaskId, long followupTaskId);
}
