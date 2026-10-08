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
                remove(ImageSearchFilters.libraryVectorId(imageId));
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
                remove(ImageSearchFilters.goodsVectorId(goodsId, slot));
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
            index.deleteById(EmbeddingVariant.FULL, vectorId);
            if (properties.isCropEnabled() && index.supports(EmbeddingVariant.CROP)) {
                index.deleteById(EmbeddingVariant.CROP, vectorId);
            }
        } catch (Exception e) {
            log.warn("删除图片向量失败（不影响删除）: id={}, err={}", vectorId, e.getMessage());
        }
    }

    /**
     * 回填调用。失败会写入进度表再抛出，让引擎继续下一条。
     * 只写传入的那一个变体，整图和裁剪可以分开重跑。
     */
    public void indexForBackfill(ImageVectorRecord record) throws Exception {
        indexForBackfill(record, EmbeddingVariant.FULL);
    }

    public void indexForBackfill(ImageVectorRecord record, EmbeddingVariant variant) throws Exception {
        EmbeddingVariant actual = variant == null ? EmbeddingVariant.FULL : variant;
        try {
            byte[] bytes = readBytes(record.ossKey());
            String sha = sha256(bytes);
            writeVariant(record, bytes, sha, actual);
        } catch (Exception e) {
            try {
                progressStore.save(record, "FAILED", null, e.getMessage(), actual);
            } catch (Exception saveError) {
                log.warn("记录失败项也失败了: {} {}", record.vectorId(), saveError.getMessage());
            }
            throw e;
        }
    }

    private void indexRecord(ImageVectorRecord record) throws Exception {
        if (record.ossKey() == null || record.ossKey().isBlank()) {
            progressStore.save(record, "SKIPPED", null, "没有可读取的图片地址", EmbeddingVariant.FULL);
            return;
        }
        byte[] bytes = readBytes(record.ossKey());
        String sha = sha256(bytes);
        writeVariant(record, bytes, sha, EmbeddingVariant.FULL);
        if (properties.isCropEnabled() && index.supports(EmbeddingVariant.CROP)) {
            try {
                writeVariant(record, bytes, sha, EmbeddingVariant.CROP);
            } catch (Exception e) {
                log.warn("裁剪向量失败（整图向量已写入）: id={}, err={}", record.vectorId(), e.getMessage());
            }
        }
    }

    private void writeVariant(ImageVectorRecord record, byte[] bytes, String sha, EmbeddingVariant variant) {
        float[] embedding = embedder.embedImage(
                bytes, ImageSearchFilters.fileNameOf(record.ossKey()), variant == EmbeddingVariant.CROP);
        index.upsert(variant, record, embedding);
        progressStore.save(record, "DONE", sha, null, variant);
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
