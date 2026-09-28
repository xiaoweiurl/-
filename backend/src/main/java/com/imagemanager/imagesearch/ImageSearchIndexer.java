package com.imagemanager.imagesearch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

/**
 * 上传成功后的异步向量。提交失败或向量失败都不影响上传本身。
 */
@Slf4j
@Service
public class ImageSearchIndexer {

    private final ImageSearchProperties properties;
    private final ImageSearchIndexWorker worker;
    private final Executor executor;

    public ImageSearchIndexer(ImageSearchProperties properties,
                              ImageSearchIndexWorker worker,
                              @Qualifier("embeddingExecutor") Executor executor) {
        this.properties = properties;
        this.worker = worker;
        this.executor = executor;
    }

    public void submitLibrary(String imageId) {
        if (!properties.isEnabled() || imageId == null || imageId.isBlank()) {
            return;
        }
        submit("素材 " + imageId, () -> worker.indexLibrary(imageId));
    }

    public void submitGoodsSlot(long goodsId, String slot) {
        if (!properties.isEnabled() || goodsId <= 0) {
            return;
        }
        submit("商品 " + goodsId + " " + slot, () -> worker.indexGoodsSlot(goodsId, slot));
    }

    public void submitRemoval(String vectorId) {
        if (!properties.isEnabled() || vectorId == null || vectorId.isBlank()) {
            return;
        }
        submit("删除 " + vectorId, () -> worker.remove(vectorId));
    }

    private void submit(String label, Runnable task) {
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } catch (Exception e) {
                    log.warn("图片向量任务失败（不影响主流程）: {} {}", label, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("提交图片向量任务失败（不影响主流程）: {} {}", label, e.getMessage());
        }
    }
}
