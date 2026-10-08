package com.imagemanager.imagesearch;

/**
 * 一条可写入图片向量库的图片。vectorId 在集合内唯一，重复回填覆盖同一条。
 */
public record ImageVectorRecord(
        String vectorId,
        String source,
        String sourceId,
        String slot,
        String company,
        String ossKey,
        String goodsNo,
        String title,
        String productId
) {
    public ImageVectorRecord {
        vectorId = vectorId == null ? "" : vectorId;
        source = source == null ? "" : source;
        sourceId = sourceId == null ? "" : sourceId;
        slot = slot == null ? "" : slot;
        company = company == null ? "" : company;
        ossKey = ossKey == null ? "" : ossKey;
        goodsNo = goodsNo == null ? "" : goodsNo;
        title = title == null ? "" : title;
        productId = productId == null ? "" : productId;
    }

    public static ImageVectorRecord library(String imageId, String company, String ossKey,
                                            String title, String productId, String defaultCompany) {
        return new ImageVectorRecord(
                ImageSearchFilters.libraryVectorId(imageId),
                ImageSearchFilters.SOURCE_LIBRARY,
                imageId.trim(),
                "",
                ImageSearchFilters.companyKey(company, defaultCompany),
                ossKey == null ? "" : ossKey,
                "",
                ImageSearchFilters.cut(title, 180),
                ImageSearchFilters.cut(productId, 180));
    }

    public static ImageVectorRecord goods(long goodsId, String slot, String company, String ossKey,
                                          String goodsNo, String title, String defaultCompany) {
        String normalizedSlot = ImageSearchFilters.normalizeSlot(slot);
        return new ImageVectorRecord(
                ImageSearchFilters.goodsVectorId(goodsId, normalizedSlot),
                ImageSearchFilters.SOURCE_GOODS,
                Long.toString(goodsId),
                normalizedSlot,
                ImageSearchFilters.companyKey(company, defaultCompany),
                ossKey == null ? "" : ossKey,
                ImageSearchFilters.cut(goodsNo, 100),
                ImageSearchFilters.cut(title, 180),
                ImageSearchFilters.cut(goodsNo, 180));
    }
}
