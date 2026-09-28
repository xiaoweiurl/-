package com.imagemanager.imagesearch;

import com.imagemanager.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 同步索引一张图。上传异步任务和回填都走这里，失败由调用方决定是否影响主流程。
 */
@Slf4j
@Component
public class ImageSearchIndexWorker {

    private final ImageSearchProperties properties;
    private final ImageEmbedder embedder;
    private final ImageVectorIndex index;
    private final ImageSearchCatalog catalog;
    private final ImageVectorProgressStore progressStore;
    private final FileStorageService fileStorageService;

    public ImageSearchIndexWorker(ImageSearchProperties properties,
                                  ImageEmbedder embedder,
                                  ImageVectorIndex index,
                                  ImageSearchCatalog catalog,
                                  ImageVectorProgressStore progressStore,
                                  FileStorageService fileStorageService) {
        this.properties = properties;
        this.embedder = embedder;
        this.index = index;
        this.catalog = catalog;
        this.progressStore = progressStore;
        this.fileStorageService = fileStorageService;
    }

    public void indexLibrary(String imageId) {
        if (!properties.isEnabled() || imageId == null || imageId.isBlank()) {
            return;
        }
        try {
            ensureVectorStore();
            ImageVectorRecord record = catalog.findLibrary(imageId);
            if (record == null) {
                index.deleteById(ImageSearchFilters.libraryVectorId(imageId));
                return;
            }
            indexRecord(record);
        } catch (Exception e) {
            log.warn("素材图片向量失败（不影响上传）: id={}, err={}", imageId, e.getMessage());
        }
    }

    public void indexGoodsSlot(long goodsId, String slot) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            ensureVectorStore();
            ImageVectorRecord record = catalog.findGoodsSlot(goodsId, slot);
            if (record == null) {
                index.deleteById(ImageSearchFilters.goodsVectorId(goodsId, slot));
                return;
            }
            indexRecord(record);
        } catch (Exception e) {
            log.warn("商品图片向量失败（不影响上传）: goodsId={}, slot={}, err={}", goodsId, slot, e.getMessage());
        }
    }

    public void remove(String vectorId) {
        if (!properties.isEnabled() || vectorId == null || vectorId.isBlank()) {
            return;
        }
        try {
            if (!index.isReady()) {
                ((MilvusImageVectorIndex) index).initIfEnabled();
            }
            index.deleteById(vectorId);
        } catch (Exception e) {
            log.warn("删除图片向量失败（不影响删除）: id={}, err={}", vectorId, e.getMessage());
        }
    }

    /**
     * 回填调用。失败会写入进度表再抛出，让引擎继续下一条。
     */
    public void indexForBackfill(ImageVectorRecord record) throws Exception {
        try {
            byte[] bytes = readBytes(record.ossKey());
            String sha = sha256(bytes);
            writeVector(record, bytes);
            progressStore.save(record, "DONE", sha, null);
        } catch (Exception e) {
            try {
                progressStore.save(record, "FAILED", null, e.getMessage());
            } catch (Exception saveError) {
                log.warn("记录失败项也失败了: {} {}", record.vectorId(), saveError.getMessage());
            }
            throw e;
        }
    }

    private void indexRecord(ImageVectorRecord record) throws Exception {
        if (record.ossKey() == null || record.ossKey().isBlank()) {
            progressStore.save(record, "SKIPPED", null, "没有可读取的图片地址");
            return;
        }
        byte[] bytes = readBytes(record.ossKey());
        writeVector(record, bytes);
        progressStore.save(record, "DONE", sha256(bytes), null);
    }

    private void writeVector(ImageVectorRecord record, byte[] bytes) {
        float[] embedding = embedder.embedImage(bytes, ImageSearchFilters.fileNameOf(record.ossKey()));
        index.upsert(record, embedding);
    }

    private void ensureVectorStore() {
        if (!index.isReady() && index instanceof MilvusImageVectorIndex milvus) {
            milvus.initIfEnabled();
        }
        if (!index.isReady()) {
            throw new IllegalStateException("图片向量库未就绪");
        }
    }

    byte[] readBytes(String key) throws Exception {
        try (InputStream in = fileStorageService.getFileInputStream(key)) {
            if (in == null) {
                throw new IllegalStateException("读取图片失败");
            }
            byte[] data = in.readAllBytes();
            if (data.length == 0) {
                throw new IllegalStateException("图片内容为空");
            }
            if (data.length > properties.getMaxImageBytes()) {
                throw new IllegalStateException("图片超过大小上限");
            }
            return data;
        }
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            return "";
        }
    }
}
