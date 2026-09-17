package com.imagemanager.service.impl;

import com.imagemanager.org.OrgNameMatcher;
import com.imagemanager.service.FileStorageService;
import com.imagemanager.service.GoodsLibraryService;
import com.imagemanager.service.GoodsSamplerNotice;
import com.imagemanager.service.GoodsSamplerNoticeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 商品库服务实现
 * OSS 键规范：goods-library/{文件夹名安全形式}/{槽位}.{扩展名}
 */
@Slf4j
@Service
public class GoodsLibraryServiceImpl implements GoodsLibraryService {

    private static final Set<String> SLOTS = Set.of("main", "side", "detail", "product");
    private static final Map<String, String> SLOT_LABELS = Map.of(
            "main", "主图", "side", "侧面图", "detail", "细节", "product", "产品图");
    private static final List<String> INFO_FIELDS = List.of(
            "initiator", "sampler", "product_name", "goods_no", "customer", "order_no", "remark");
    private static final long MAX_IMAGE_SIZE = 20L * 1024 * 1024;
    private static final int PRESIGN_EXPIRE_SECONDS = 24 * 3600;

    private final JdbcTemplate jdbcTemplate;
    private final FileStorageService fileStorageService;
    private final TransactionTemplate txTemplate;
    private final GoodsSamplerNoticeService samplerNoticeService;

    public GoodsLibraryServiceImpl(JdbcTemplate jdbcTemplate, FileStorageService fileStorageService,
                                   PlatformTransactionManager transactionManager,
                                   GoodsSamplerNoticeService samplerNoticeService) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileStorageService = fileStorageService;
        // 编程式事务：HikariCP auto-commit=false 且本类方法不声明 @Transactional（避免 OSS 网络调用拖长事务），
        // 因此所有写库操作必须通过 TransactionTemplate 显式提交，否则连接归还时会被回滚
        this.txTemplate = new TransactionTemplate(java.util.Objects.requireNonNull(transactionManager));
        this.samplerNoticeService = samplerNoticeService;
    }

    @Override
    public List<Map<String, Object>> listGoods(String keyword) {
        List<Map<String, Object>> rows;
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            rows = jdbcTemplate.queryForList(
                    "SELECT * FROM goods_library WHERE folder_name ILIKE ? OR goods_no ILIKE ?" +
                            " OR product_name ILIKE ? OR customer ILIKE ? ORDER BY created_at DESC",
                    like, like, like, like);
        } else {
            rows = jdbcTemplate.queryForList(
                    "SELECT * FROM goods_library ORDER BY created_at DESC");
        }
        for (Map<String, Object> row : rows) {
            toFrontendMap(row, true);
        }
        return rows;
    }

    @Override
    public Map<String, Object> createGoods(Map<String, String> fields, Map<String, MultipartFile> images, String userId) {
        // 发起人为必填字段（业务要求：商品文件夹必须归属到具体发起人）
        String initiator = nz(fields.get("initiator"));
        if (initiator.isBlank()) {
            throw new IllegalArgumentException("发起人不能为空");
        }
        String folderName = folderName(fields.get("goods_no"), fields.get("product_name"));
        // 事务内仅执行 INSERT 并立即提交（返回即落库），OSS 网络上传放在事务外
        Map<String, Object> created = txTemplate.execute(status -> jdbcTemplate.queryForMap(
                "INSERT INTO goods_library (folder_name, initiator, sampler, product_name, goods_no," +
                        " customer, order_no, remark, user_id) VALUES (?,?,?,?,?,?,?,?,?) RETURNING *",
                folderName,
                initiator, nz(fields.get("sampler")), nz(fields.get("product_name")),
                nz(fields.get("goods_no")), nz(fields.get("customer")), nz(fields.get("order_no")),
                nz(fields.get("remark")),
                (userId == null || userId.isBlank()) ? null : userId));
        Object idObj = created == null ? null : created.get("id");
        if (idObj == null) {
            throw new IllegalStateException("创建失败：未获取到商品ID");
        }
        long id = ((Number) idObj).longValue();
        notifySamplerIfChanged(null, nz(fields.get("sampler")), created, id);

        // 一次性上传创建时携带的四类图片（均允许为空）；图片失败不影响已创建的文件夹
        if (images != null) {
            for (String slot : SLOTS) {
                MultipartFile file = images.get(slot);
                if (file == null || file.isEmpty()) continue;
                try {
                    validateImageFile(file);
                    String storedKey = fileStorageService.uploadFileForKey(
                            file, "goods-library/" + safeFolderName(folderName, id), slot + extOf(file.getOriginalFilename()));
                    String col = slot + "_image_key";
                    txTemplate.executeWithoutResult(s -> jdbcTemplate.update(
                            "UPDATE goods_library SET " + col + " = ?, updated_at = now() WHERE id = ?", storedKey, id));
                } catch (Exception e) {
                    log.error("[GoodsLibrary] 创建时上传图片失败: id={}, slot={}, err={}", id, slot, e.getMessage());
                }
            }
        }
        return getGoods(id);
    }

    @Override
    public Map<String, Object> getGoods(long id) {
        Map<String, Object> row = mustGet(id);
        toFrontendMap(row, true);
        return row;
    }

    @Override
    public Map<String, Object> updateGoods(long id, Map<String, String> body) {
        Map<String, Object> existing = mustGet(id);

        // 合并更新：仅覆盖请求中出现的白名单字段
        Map<String, String> merged = new LinkedHashMap<>();
        for (String field : INFO_FIELDS) {
            String current = existing.get(field) == null ? null : String.valueOf(existing.get(field));
            merged.put(field, body.containsKey(field) ? nz(body.get(field)) : current);
        }
        // 发起人为必填字段，更新时不允许清空
        if (merged.get("initiator") == null || merged.get("initiator").isBlank()) {
            throw new IllegalArgumentException("发起人不能为空");
        }
        String folderName = folderName(merged.get("goods_no"), merged.get("product_name"));
        String previousSampler = existing.get("sampler") == null ? "" : String.valueOf(existing.get("sampler"));

        txTemplate.executeWithoutResult(s -> jdbcTemplate.update(
                "UPDATE goods_library SET folder_name=?, initiator=?, sampler=?, product_name=?, goods_no=?," +
                        " customer=?, order_no=?, remark=?, updated_at=now() WHERE id=?",
                folderName,
                merged.get("initiator"), merged.get("sampler"), merged.get("product_name"),
                merged.get("goods_no"), merged.get("customer"), merged.get("order_no"),
                merged.get("remark"),
                id));
        Map<String, String> noticeFields = new LinkedHashMap<>(merged);
        noticeFields.put("folder_name", folderName);
        notifySamplerIfChanged(previousSampler, merged.get("sampler"), noticeFields, id);
        if (OrgNameMatcher.namesEqual(previousSampler, merged.get("sampler"))) {
            notifySamplerFormFilled(noticeFields, id, merged.get("sampler"));
        }
        return getGoods(id);
    }

    @Override
    public void deleteGoods(long id) {
        Map<String, Object> row = mustGet(id);
        txTemplate.executeWithoutResult(s -> jdbcTemplate.update("DELETE FROM goods_library WHERE id=?", id));
        for (String slot : SLOTS) {
            Object key = row.get(slot + "_image_key");
            if (key != null) {
                try {
                    fileStorageService.deleteFile(String.valueOf(key));
                } catch (Exception e) {
                    log.warn("[GoodsLibrary] 删除 OSS 图片失败: key={}, err={}", key, e.getMessage());
                }
            }
        }
    }

    @Override
    public Map<String, Object> uploadImage(long id, String slot, MultipartFile file) {
        validateSlot(slot);
        Map<String, Object> row = mustGet(id);

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的图片");
        }
        validateImageFile(file);

        String keyColumn = slot + "_image_key";
        Object oldKeyObj = row.get(keyColumn);
        String oldKey = oldKeyObj == null ? null : String.valueOf(oldKeyObj);

        String folder = safeFolderName(String.valueOf(row.get("folder_name")), id);
        String fileName = slot + extOf(file.getOriginalFilename());
        String storedKey = fileStorageService.uploadFileForKey(file, "goods-library/" + folder, fileName);

        txTemplate.executeWithoutResult(s -> jdbcTemplate.update(
                "UPDATE goods_library SET " + keyColumn + " = ?, updated_at = now() WHERE id = ?", storedKey, id));

        // 替换场景：旧 key 与新 key 不同（扩展名变化）时删除旧文件
        if (oldKey != null && !oldKey.equals(storedKey)) {
            try {
                fileStorageService.deleteFile(oldKey);
            } catch (Exception e) {
                log.warn("[GoodsLibrary] 删除旧图片失败: key={}, err={}", oldKey, e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("slot", slot);
        result.put("label", SLOT_LABELS.get(slot));
        result.put("key", storedKey);
        result.put("url", fileStorageService.generatePresignedUrl(storedKey, PRESIGN_EXPIRE_SECONDS));
        return result;
    }

    @Override
    public void deleteImage(long id, String slot) {
        validateSlot(slot);
        Map<String, Object> row = mustGet(id);
        String keyColumn = slot + "_image_key";
        Object key = row.get(keyColumn);
        if (key != null) {
            try {
                fileStorageService.deleteFile(String.valueOf(key));
            } catch (Exception e) {
                log.warn("[GoodsLibrary] 删除 OSS 图片失败: key={}, err={}", key, e.getMessage());
            }
        }
        txTemplate.executeWithoutResult(s -> jdbcTemplate.update(
                "UPDATE goods_library SET " + keyColumn + " = NULL, updated_at = now() WHERE id = ?", id));
    }

    // ==================== 内部辅助 ====================

    /**
     * 事务已提交后再发工作通知：sampler 未变不推送；解析/发送失败不影响商品保存。
     */
    private void notifySamplerIfChanged(String previousSampler, String newSampler,
                                        Map<String, ?> fields, long id) {
        if (samplerNoticeService == null) {
            return;
        }
        try {
            samplerNoticeService.notifySamplerAssignedAsync(previousSampler, newSampler,
                    toSamplerNotice(id, fields));
        } catch (Exception e) {
            log.warn("[GoodsLibrary] 打样员工作通知调度失败（不影响保存）: id={}, err={}",
                    id, e.getMessage());
        }
    }

    /**
     * 打样员未变的保存：若指派卡发出时货号/品名为空，补发一封已填写摘要（钉钉不能改原卡正文）。
     */
    private void notifySamplerFormFilled(Map<String, ?> fields, long id, String samplerName) {
        if (samplerNoticeService == null) {
            return;
        }
        try {
            samplerNoticeService.notifySamplerFormFilledAsync(toSamplerNotice(id, fields), samplerName);
        } catch (Exception e) {
            log.warn("[GoodsLibrary] 打样回填通知调度失败（不影响保存）: id={}, err={}",
                    id, e.getMessage());
        }
    }

    private GoodsSamplerNotice toSamplerNotice(long id, Map<String, ?> fields) {
        return new GoodsSamplerNotice(
                id,
                str(fields, "folder_name"),
                str(fields, "goods_no"),
                str(fields, "product_name"),
                str(fields, "initiator"),
                str(fields, "customer"),
                str(fields, "order_no"));
    }

    private String str(Map<String, ?> fields, String key) {
        if (fields == null) {
            return "";
        }
        Object v = fields.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    /** 图片文件基础校验：类型 + 大小 */
    private void validateImageFile(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("仅支持图片文件");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException("图片大小不能超过 20MB");
        }
    }

    private Map<String, Object> mustGet(long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM goods_library WHERE id=?", id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("商品不存在(id=" + id + ")");
        }
        return rows.get(0);
    }

    /** 数据库行 → 前端结构：id 转字符串、时间戳转 ISO、附签名 URL */
    private void toFrontendMap(Map<String, Object> row, boolean withUrls) {
        Object idObj = row.get("id");
        if (idObj != null) {
            row.put("id", String.valueOf(idObj));
        }
        for (String ts : new String[]{"created_at", "updated_at"}) {
            Object v = row.get(ts);
            if (v instanceof Timestamp) {
                row.put(ts, ((Timestamp) v).toInstant().toString());
            }
        }
        if (withUrls) {
            for (String slot : SLOTS) {
                Object key = row.get(slot + "_image_key");
                String url = null;
                if (key != null) {
                    try {
                        url = fileStorageService.generatePresignedUrl(String.valueOf(key), PRESIGN_EXPIRE_SECONDS);
                    } catch (Exception e) {
                        log.warn("[GoodsLibrary] 生成签名 URL 失败: key={}, err={}", key, e.getMessage());
                    }
                }
                row.put(slot + "_image_url", url);
            }
        }
    }

    /** 文件夹名 = 货号 + 品名（允许为空） */
    private String folderName(String goodsNo, String productName) {
        String name = nz(goodsNo) + nz(productName);
        return name.isEmpty() ? "未命名商品" : name;
    }

    /** OSS 目录名安全形式：仅保留字母数字 ._-，其余替换为 -，兜底 goods-{id} */
    private String safeFolderName(String folderName, long id) {
        String safe = folderName == null ? "" : folderName.replaceAll("[^a-zA-Z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (safe.length() > 120) {
            safe = safe.substring(0, 120);
        }
        return safe.isEmpty() ? "goods-" + id : safe;
    }

    private void validateSlot(String slot) {
        if (slot == null || !SLOTS.contains(slot)) {
            throw new IllegalArgumentException("无效的图片槽位: " + slot + "（支持 main/side/detail/product）");
        }
    }

    private String extOf(String filename) {
        if (filename != null && filename.contains(".")) {
            String ext = filename.substring(filename.lastIndexOf('.')).toLowerCase();
            if (ext.matches("\\.(jpg|jpeg|png|gif|webp|bmp|svg)")) {
                return ext;
            }
        }
        return ".jpg";
    }

    private String nz(String v) {
        return v == null ? "" : v.trim();
    }
}
