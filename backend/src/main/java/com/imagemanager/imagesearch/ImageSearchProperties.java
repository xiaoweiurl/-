package com.imagemanager.imagesearch;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 以图搜图开关。默认关闭：不连接图片向量集合，不在上传后生成向量。
 */
@Data
@Component
@ConfigurationProperties(prefix = "image-search")
public class ImageSearchProperties {

    /** 总开关。false 时本功能的联网调用和 Milvus 写入都不会发生。 */
    private boolean enabled = false;

    /** 本地图片向量服务，例如 http://127.0.0.1:8002 */
    private String embedBaseUrl = "http://127.0.0.1:8002";

    private int timeoutMs = 60_000;

    /** 必须是 image_ 前缀，禁止指向 salesperson_*。 */
    private String collection = "image_vectors";

    /** 与 Chinese-CLIP ViT-B/16 的输出一致。换模型时要一起改，并换新集合名。 */
    private int dimension = 512;

    private int topK = 20;

    /** 对话里默认返回的相似图条数。 */
    private int chatTopK = 5;

    /** 余弦相似度下限。低于这个值的结果不进对话。 */
    private double chatMinScore = 0.30d;

    /** 用户明确要求“全部”时，最多返回这么多条。 */
    private int chatMaxResults = 50;

    private String milvusHost = "localhost";

    private int milvusPort = 19530;

    /** 单张图最大字节数。查询接口另有 10MB 上限。 */
    private int maxImageBytes = 20 * 1024 * 1024;

    /** 与 SessionUtil 的默认公司一致，历史图片 company 为空时按这个值索引。 */
    private String defaultCompany = ImageSearchFilters.DEFAULT_COMPANY;

    public void requireEnabled() {
        if (!enabled) {
            throw new ImageSearchDisabledException();
        }
    }
}
