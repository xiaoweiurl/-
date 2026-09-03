package com.imagemanager.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 商品库服务
 * 文件夹式商品管理：第一层信息（发起人/打样员/品名/货号/客户/订单号），
 * 文件夹名 = 货号 + 品名；第二层四类图片（主图/侧面图/细节/产品图）上传至 OSS；
 * 备注五字段（卖点/竞品/功能/对应人群/使用场景）。所有字段允许为空。
 */
public interface GoodsLibraryService {

    /** 获取商品文件夹列表（含主图签名 URL 作封面），keyword 按文件夹名/货号/品名/客户模糊过滤 */
    List<Map<String, Object>> listGoods(String keyword);

    /** 创建商品文件夹（可同时携带四类图片一次性上传，键为槽位 main/side/detail/product） */
    Map<String, Object> createGoods(Map<String, String> fields, Map<String, MultipartFile> images, String userId);

    /** 获取商品详情（含四类图片签名 URL） */
    Map<String, Object> getGoods(long id);

    /** 更新商品信息/备注（货号或品名变更时文件夹自动重命名） */
    Map<String, Object> updateGoods(long id, Map<String, String> body);

    /** 删除商品（同步删除 OSS 图片） */
    void deleteGoods(long id);

    /** 上传/替换指定槽位图片（slot: main/side/detail/product） */
    Map<String, Object> uploadImage(long id, String slot, MultipartFile file);

    /** 删除指定槽位图片 */
    void deleteImage(long id, String slot);
}
