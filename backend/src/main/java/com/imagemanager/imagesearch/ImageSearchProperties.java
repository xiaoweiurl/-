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

    /**
     * 主体裁剪集合。关闭时不连接、不写入。
     * 打开后，新上传会同时写入整图集合和裁剪集合。
     */
    private boolean cropEnabled = false;

    /**
     * 空着就用「整图集合名_crop」。这台机器整图集合是 image_vectors_vitl 时，裁剪集合是 image_vectors_vitl_crop。
     */
    private String cropCollection = "";

    /**
     * 同款用哪个集合：crop 或 full。默认 crop。
     * 实图里背景和模特会把别的款抬到很高，裁掉主体再比更接近「是不是这款」。
     * 本机评测如果整图更好，改成 full。
     */
    private String sameProductVariant = "crop";

    /** 相似素材用哪个集合。crop 未就绪时自动退回整图集合。 */
    private String similarReferenceVariant = "crop";

    /** 同款有多张图命中时，每多一张加的分。 */
    private double sameProductMultiBonus = 0.02d;

    /** 多图奖励的上限。 */
    private double sameProductMultiBonusCap = 0.06d;

    /**
     * 只发了一张图、几乎没有文字时：商品命中不低于这个值，并且离最高分不超过 0.12，就按同款。
     */
    private double sameProductProbeMinScore = 0.45d;

    /** 同款向 Milvus 多取的倍数，分组后还要凑够返回条数。 */
    private int internalTopKFactor = 5;

    /**
     * 时间、打样员、相册不在向量标量里，要在补全后再滤。
     * 这时把内部条数再乘上这个倍数，避免滤完以后不够展示。
     */
    private int conditionFetchFactor = 3;

    /** 图文重排：final = imageWeight * 图片分 + textWeight * 文字分。 */
    private double textRerankImageWeight = 0.65d;

    private double textRerankTextWeight = 0.35d;

    /**
     * 文字分低于这个值才丢弃。默认 0，绝对下限只丢掉负相关。
     * 相对差距见 {@link #textScoreMargin}，避免颜色词把整批结果清空。
     */
    private double textScoreFloor = 0d;

    /**
     * 颜色、材质、款式：文字分比这批里最高的一条低出这么多就丢掉。
     * 最贴近的一条始终保留。取不到图片向量的命中不能绕过这个条件。
     */
    private double textScoreMargin = 0.18d;

    /**
     * 打样页「拍照查同款」。总开关关闭时这里打开也不生效。
     */
    private boolean samplerEnabled = true;

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

    public String resolvedCropCollection() {
        if (cropCollection != null && !cropCollection.isBlank()) {
            return cropCollection.trim();
        }
        String base = collection == null || collection.isBlank() ? "image_vectors" : collection.trim();
        return base + "_crop";
    }
}
