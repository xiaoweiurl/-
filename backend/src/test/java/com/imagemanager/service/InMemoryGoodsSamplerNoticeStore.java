package com.imagemanager.service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单测用内存投递记录，避免打到数据库。
 */
final class InMemoryGoodsSamplerNoticeStore implements GoodsSamplerNoticeStore {

    private final Map<Long, GoodsSamplerNoticeRecord> byGoodsId = new ConcurrentHashMap<>();

    @Override
    public void saveAssignment(GoodsSamplerNoticeRecord record) {
        if (record == null) {
            return;
        }
        byGoodsId.put(record.getGoodsId(), record);
    }

    @Override
    public Optional<GoodsSamplerNoticeRecord> findByGoodsId(long goodsId) {
        return Optional.ofNullable(byGoodsId.get(goodsId));
    }

    @Override
    public void markFollowupSent(long goodsId, long assignmentTaskId, long followupTaskId) {
        GoodsSamplerNoticeRecord current = byGoodsId.get(goodsId);
        if (current == null || current.getTaskId() != assignmentTaskId) {
            return;
        }
        byGoodsId.put(goodsId, new GoodsSamplerNoticeRecord(
                current.getGoodsId(),
                current.getDingUserId(),
                current.getSamplerName(),
                current.getTaskId(),
                current.getSentFolderName(),
                current.getSentGoodsNo(),
                current.getSentProductName(),
                current.getSentInitiator(),
                followupTaskId));
    }

    void clear() {
        byGoodsId.clear();
    }
}
