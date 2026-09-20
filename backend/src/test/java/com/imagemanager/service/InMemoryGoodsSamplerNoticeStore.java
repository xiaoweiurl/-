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
    public void saveLastResult(long goodsId, String samplerName, String status, String message,
                               String kind, boolean persistOk) {
        GoodsSamplerNoticeRecord current = byGoodsId.get(goodsId);
        if (current == null) {
            byGoodsId.put(goodsId, new GoodsSamplerNoticeRecord(
                    goodsId, "", samplerName, 0L, "", "", "", "", null,
                    status, message, kind, persistOk));
            return;
        }
        String name = samplerName == null || samplerName.isBlank() ? current.getSamplerName() : samplerName;
        byGoodsId.put(goodsId, new GoodsSamplerNoticeRecord(
                current.getGoodsId(),
                current.getDingUserId(),
                name,
                current.getTaskId(),
                current.getSentFolderName(),
                current.getSentGoodsNo(),
                current.getSentProductName(),
                current.getSentInitiator(),
                current.getFollowupTaskId(),
                status,
                message,
                kind,
                persistOk));
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
        byGoodsId.put(goodsId, current.withFollowup(followupTaskId));
    }

    void clear() {
        byGoodsId.clear();
    }
}
