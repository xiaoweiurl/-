package com.imagemanager.service;

import java.util.Optional;

/**
 * 打样工作通知投递记录。HikariCP auto-commit=false，实现侧写操作必须包事务。
 */
public interface GoodsSamplerNoticeStore {

    void saveAssignment(GoodsSamplerNoticeRecord record);

    Optional<GoodsSamplerNoticeRecord> findByGoodsId(long goodsId);

    void markFollowupSent(long goodsId, long assignmentTaskId, long followupTaskId);
}
