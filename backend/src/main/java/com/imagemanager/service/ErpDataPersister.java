package com.imagemanager.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ERP 同步数据落库器：将 ERP 接口返回的 JSON 数组解析并写入本地业务表。
 *
 * 落库策略（项目 HikariCP auto-commit=false，所有写库必须在事务中）：
 * - 有主键表（order_xs_list / order_jfk_gongyidan / order_sw_gongyidan）：
 *   ON CONFLICT (主键) DO UPDATE，天然幂等，增量/全量通用
 * - 无主键明细表（order_buj_component / order_gongxu_process / order_gongxu_price）：
 *   事务内 DELETE 全量 + 批量 INSERT（数据以 ERP 为准，全量替换幂等）
 * - raw_material_warehouse：含本地维护字段（unit_price/company/product_code），
 *   按业务键 merge——存在则仅更新 ERP 侧字段（保护本地字段），不存在则插入
 *
 * 字段名兼容：ERP（ASP.NET）JSON 序列化可能为 camelCase 或 PascalCase，读取时两种都尝试。
 * 日期兼容："yyyy-MM-dd HH:mm:ss" / ISO "yyyy-MM-ddTHH:mm:ss" / ASP.NET "/Date(millis)/"。
 */
@Slf4j
@Component
public class ErpDataPersister {

    /** 落库结果：处理条数（插入+更新） */
    public record PersistResult(int inserted, int updated) {
        public int total() { return inserted + updated; }
    }

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public ErpDataPersister(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 按模块落库（事务包裹）
     *
     * @param moduleKey 模块标识
     * @param rows      ERP 接口返回的 result 数组
     * @return 落库结果（新增/更新条数）
     */
    public PersistResult persist(String moduleKey, JsonNode rows) {
        if (rows == null || !rows.isArray() || rows.isEmpty()) {
            return new PersistResult(0, 0);
        }
        return switch (moduleKey) {
            case "orders" -> persistOrders(rows);
            case "neiyi-gongyidan" -> persistNeiyiGongyidan(rows);
            case "siwa-gongyidan" -> persistSiwaGongyidan(rows);
            case "gongyi-bujian" -> replaceAll("order_buj_component", rows, ErpDataPersister::bujRow);
            case "gongyi-gongxu" -> replaceAll("order_gongxu_process", rows, ErpDataPersister::gongxuRow);
            case "gongxu-gongjia" -> replaceAll("order_gongxu_price", rows, ErpDataPersister::gongjiaRow);
            case "yuanliao-bom" -> mergeRawMaterials(rows);
            default -> {
                log.warn("[ERP落库] 未知模块 {}，跳过落库", moduleKey);
                yield new PersistResult(0, 0);
            }
        };
    }

    // ==================== 销售订单（PK: dh，upsert） ====================

    private static final String ORDER_INSERT_SQL =
            "INSERT INTO order_xs_list (dh, zhdate, state, zxtate, printnum, jh_date, business_dh, ddtype, "
                    + "khname, detailhuohaocp, detailhuohao, sl_sum, remark, ywyname, sfplan, zhuser, checkuser, ckeckdate) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                    + "ON CONFLICT (dh) DO UPDATE SET zhdate=EXCLUDED.zhdate, state=EXCLUDED.state, "
                    + "zxtate=EXCLUDED.zxtate, printnum=EXCLUDED.printnum, jh_date=EXCLUDED.jh_date, "
                    + "business_dh=EXCLUDED.business_dh, ddtype=EXCLUDED.ddtype, khname=EXCLUDED.khname, "
                    + "detailhuohaocp=EXCLUDED.detailhuohaocp, detailhuohao=EXCLUDED.detailhuohao, "
                    + "sl_sum=EXCLUDED.sl_sum, remark=EXCLUDED.remark, ywyname=EXCLUDED.ywyname, "
                    + "sfplan=EXCLUDED.sfplan, zhuser=EXCLUDED.zhuser, checkuser=EXCLUDED.checkuser, "
                    + "ckeckdate=EXCLUDED.ckeckdate";

    private PersistResult persistOrders(JsonNode rows) {
        List<Object[]> batch = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode row : rows) {
            String dh = text(row, "dh");
            if (dh == null || !seen.add(dh)) continue; // 无单号或重复跳过
            batch.add(new Object[]{
                    dh, ts(row, "zhdate"), text(row, "state"), text(row, "zxtate"),
                    integer(row, "printnum"), ts(row, "jh_date"), text(row, "business_dh"),
                    text(row, "ddtype"), text(row, "khname"), text(row, "detailhuohaocp"),
                    text(row, "detailhuohao"), dec(row, "sl_sum"), text(row, "remark"),
                    text(row, "ywyname"), text(row, "sfplan"), text(row, "zhuser"),
                    text(row, "checkuser"), ts(row, "ckeckdate")
            });
        }
        if (batch.isEmpty()) return new PersistResult(0, 0);
        transactionTemplate.executeWithoutResult(tx -> jdbcTemplate.batchUpdate(ORDER_INSERT_SQL, batch));
        log.info("[ERP落库] 销售订单 upsert {} 条", batch.size());
        return new PersistResult(batch.size(), 0);
    }

    // ==================== 内衣工艺单（PK: bh，upsert） ====================

    private static final String NEIYI_INSERT_SQL =
            "INSERT INTO order_jfk_gongyidan (bh, hhtype, huohao, spname, designer, dw, rsjgh, "
                    + "qd_dys, hd_dys, dybanhao, remark) VALUES (?,?,?,?,?,?,?,?,?,?,?) "
                    + "ON CONFLICT (bh) DO UPDATE SET hhtype=EXCLUDED.hhtype, huohao=EXCLUDED.huohao, "
                    + "spname=EXCLUDED.spname, designer=EXCLUDED.designer, dw=EXCLUDED.dw, "
                    + "rsjgh=EXCLUDED.rsjgh, qd_dys=EXCLUDED.qd_dys, hd_dys=EXCLUDED.hd_dys, "
                    + "dybanhao=EXCLUDED.dybanhao, remark=EXCLUDED.remark";

    private PersistResult persistNeiyiGongyidan(JsonNode rows) {
        List<Object[]> batch = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode row : rows) {
            String bh = text(row, "bh");
            if (bh == null || !seen.add(bh)) continue;
            batch.add(new Object[]{
                    bh, text(row, "hhtype"), text(row, "huohao"), text(row, "spname"),
                    text(row, "designer"), text(row, "dw"), text(row, "rsjgh"),
                    text(row, "qd_dys"), text(row, "hd_dys"), text(row, "dybanhao"),
                    text(row, "remark")
            });
        }
        if (batch.isEmpty()) return new PersistResult(0, 0);
        transactionTemplate.executeWithoutResult(tx -> jdbcTemplate.batchUpdate(NEIYI_INSERT_SQL, batch));
        log.info("[ERP落库] 内衣工艺单 upsert {} 条", batch.size());
        return new PersistResult(batch.size(), 0);
    }

    // ==================== 丝袜工艺单（PK: bh，upsert） ====================

    private static final String SIWA_INSERT_SQL =
            "INSERT INTO order_sw_gongyidan (bh, hhtype, huohao, spname, dybanhao, cxm, xjkz, xjsl, "
                    + "pfkz, cpkz, zcl, jix, zs, yajiao, nd, djcl, hhywy, qd_dys, hd_dys, dw, remark) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                    + "ON CONFLICT (bh) DO UPDATE SET hhtype=EXCLUDED.hhtype, huohao=EXCLUDED.huohao, "
                    + "spname=EXCLUDED.spname, dybanhao=EXCLUDED.dybanhao, cxm=EXCLUDED.cxm, "
                    + "xjkz=EXCLUDED.xjkz, xjsl=EXCLUDED.xjsl, pfkz=EXCLUDED.pfkz, cpkz=EXCLUDED.cpkz, "
                    + "zcl=EXCLUDED.zcl, jix=EXCLUDED.jix, zs=EXCLUDED.zs, yajiao=EXCLUDED.yajiao, "
                    + "nd=EXCLUDED.nd, djcl=EXCLUDED.djcl, hhywy=EXCLUDED.hhywy, qd_dys=EXCLUDED.qd_dys, "
                    + "hd_dys=EXCLUDED.hd_dys, dw=EXCLUDED.dw, remark=EXCLUDED.remark";

    private PersistResult persistSiwaGongyidan(JsonNode rows) {
        List<Object[]> batch = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode row : rows) {
            String bh = text(row, "bh");
            if (bh == null || !seen.add(bh)) continue;
            batch.add(new Object[]{
                    bh, text(row, "hhtype"), text(row, "huohao"), text(row, "spname"),
                    text(row, "dybanhao"), text(row, "cxm"), dec(row, "xjkz"), dec(row, "xjsl"),
                    dec(row, "pfkz"), dec(row, "cpkz"), dec(row, "zcl"), text(row, "jix"),
                    text(row, "zs"), text(row, "yajiao"), text(row, "nd"), dec(row, "djcl"),
                    text(row, "hhywy"), text(row, "qd_dys"), text(row, "hd_dys"),
                    text(row, "dw"), text(row, "remark")
            });
        }
        if (batch.isEmpty()) return new PersistResult(0, 0);
        transactionTemplate.executeWithoutResult(tx -> jdbcTemplate.batchUpdate(SIWA_INSERT_SQL, batch));
        log.info("[ERP落库] 丝袜工艺单 upsert {} 条", batch.size());
        return new PersistResult(batch.size(), 0);
    }

    // ==================== 无主键明细表：事务内全量替换 ====================

    /** 行解析函数：JsonNode → INSERT 参数数组 */
    private interface RowMapper {
        Object[] map(JsonNode row);
    }

    /** 工艺部件 INSERT（25 列） */
    private static final String BUJ_INSERT_SQL =
            "INSERT INTO order_buj_component (hhname, color, chima, buj, zbj, jix, zs, cxm, tongjing, "
                    + "bili, kez, xjtime, tjcxm, tjxs, tzs, skzjj, xf, zznd, llcl, remark, "
                    + "vchima, vcolor, vtzs, ischeck, isrecheck) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private static Object[] bujRow(JsonNode row) {
        return new Object[]{
                text(row, "hhname"), text(row, "color"), text(row, "chima"), text(row, "buj"),
                integer(row, "zbj"), text(row, "jix"), integer(row, "zs"), text(row, "cxm"),
                dec(row, "tongjing"), text(row, "bili"), dec(row, "kez"), dec(row, "xjtime"),
                text(row, "tjcxm"), text(row, "tjxs"), text(row, "tzs"), text(row, "skzjj"),
                text(row, "xf"), text(row, "zznd"), dec(row, "llcl"), text(row, "remark"),
                text(row, "vchima"), text(row, "vcolor"), text(row, "vtzs"),
                text(row, "ischeck"), text(row, "isrecheck")
        };
    }

    /** 工艺工序 INSERT（19 列，"sort" 加引号防关键字歧义） */
    private static final String GONGXU_INSERT_SQL =
            "INSERT INTO order_gongxu_process (hhname, wtname, jizhong, zhenju, zhenhao, zhenmu, "
                    + "zhens, zline, sline, yongl, yongl2, \"sort\", tjtype, sctype, using_state, "
                    + "zhgx, tims, ischeck, isrecheck) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private static Object[] gongxuRow(JsonNode row) {
        return new Object[]{
                text(row, "hhname"), text(row, "wtname"), text(row, "jizhong"), text(row, "zhenju"),
                text(row, "zhenhao"), text(row, "zhenmu"), text(row, "zhens"), text(row, "zline"),
                text(row, "sline"), dec(row, "yongl"), text(row, "yongl2"), integer(row, "sort"),
                text(row, "tjtype"), text(row, "sctype"), text(row, "using_state"),
                text(row, "zhgx"), dec(row, "tims"), text(row, "ischeck"), text(row, "isrecheck")
        };
    }

    /** 工序工价 INSERT（6 列，remarkgz 本地维护不覆盖） */
    private static final String GONGJIA_INSERT_SQL =
            "INSERT INTO order_gongxu_price (hhname, wtname, jsprice, price, tempworker_price, state) "
                    + "VALUES (?,?,?,?,?,?)";

    private static Object[] gongjiaRow(JsonNode row) {
        return new Object[]{
                text(row, "hhname"), text(row, "wtname"), dec(row, "jsprice"), dec(row, "price"),
                dec(row, "tempworker_price"), text(row, "state")
        };
    }

    /**
     * 无主键明细表全量替换：事务内 DELETE + 批量 INSERT（数据以 ERP 为准，原子替换）
     */
    private PersistResult replaceAll(String table, JsonNode rows, RowMapper mapper) {
        List<Object[]> batch = new ArrayList<>();
        for (JsonNode row : rows) {
            batch.add(mapper.map(row));
        }
        if (batch.isEmpty()) return new PersistResult(0, 0);
        String insertSql = switch (table) {
            case "order_buj_component" -> BUJ_INSERT_SQL;
            case "order_gongxu_process" -> GONGXU_INSERT_SQL;
            case "order_gongxu_price" -> GONGJIA_INSERT_SQL;
            default -> throw new IllegalArgumentException("不支持全量替换的表: " + table);
        };
        transactionTemplate.executeWithoutResult(tx -> {
            jdbcTemplate.update("DELETE FROM " + table);
            jdbcTemplate.batchUpdate(insertSql, batch);
        });
        log.info("[ERP落库] {} 全量替换 {} 条", table, batch.size());
        return new PersistResult(batch.size(), 0);
    }

    // ==================== 原料 BOM（merge，保护本地维护字段） ====================

    /** 业务键：货号+颜色+尺码+部件+供应商+物料名称+规格+批号（本地唯一标识一行用料） */
    private static String rawMaterialKey(String huohao, String color, String size, String component,
                                         String supplier, String materialName, String specification, String batchNo) {
        return String.join("",
                nullToEmpty(huohao), nullToEmpty(color), nullToEmpty(size), nullToEmpty(component),
                nullToEmpty(supplier), nullToEmpty(materialName), nullToEmpty(specification), nullToEmpty(batchNo));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private static final String RAW_INSERT_SQL =
            "INSERT INTO raw_material_warehouse (huohao, color, size, component, supplier, material_name, "
                    + "specification, material_color, batch_no, twist_direction, unit, usage_per_unit, loss_rate, remark) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    /** 更新时仅更新 ERP 侧字段，绝不覆盖 unit_price / company / product_code（本地维护） */
    private static final String RAW_UPDATE_SQL =
            "UPDATE raw_material_warehouse SET material_color=?, twist_direction=?, unit=?, "
                    + "usage_per_unit=?, loss_rate=?, remark=? WHERE id=?";

    private PersistResult mergeRawMaterials(JsonNode rows) {
        // 1. 加载现有业务键 → id 映射（一次查询，避免逐行 SELECT）
        Map<String, Long> existing = new HashMap<>();
        jdbcTemplate.query(
                "SELECT id, huohao, color, size, component, supplier, material_name, specification, batch_no "
                        + "FROM raw_material_warehouse",
                rs -> {
                    existing.put(rawMaterialKey(
                            rs.getString("huohao"), rs.getString("color"), rs.getString("size"),
                            rs.getString("component"), rs.getString("supplier"), rs.getString("material_name"),
                            rs.getString("specification"), rs.getString("batch_no")),
                            rs.getLong("id"));
                });

        // 2. 分拆插入/更新批次（ERP 返回中的重复业务键只处理一次）
        List<Object[]> toInsert = new ArrayList<>();
        List<Object[]> toUpdate = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode row : rows) {
            String key = rawMaterialKey(
                    text(row, "hhname"), text(row, "color"), text(row, "chima"), text(row, "buj"),
                    text(row, "gys"), text(row, "wlname"), text(row, "guige"), text(row, "pihao"));
            if (!seen.add(key)) continue;
            Long existingId = existing.get(key);
            if (existingId != null) {
                toUpdate.add(new Object[]{
                        text(row, "wlcolor"), text(row, "nianx"), text(row, "dw"),
                        dec(row, "djyl"), dec(row, "sh"), text(row, "remark"), existingId
                });
            } else {
                toInsert.add(new Object[]{
                        text(row, "hhname"), text(row, "color"), text(row, "chima"), text(row, "buj"),
                        text(row, "gys"), text(row, "wlname"), text(row, "guige"), text(row, "wlcolor"),
                        text(row, "pihao"), text(row, "nianx"), text(row, "dw"),
                        dec(row, "djyl"), dec(row, "sh"), text(row, "remark")
                });
            }
        }

        // 3. 事务内批量写入
        if (!toInsert.isEmpty() || !toUpdate.isEmpty()) {
            transactionTemplate.executeWithoutResult(tx -> {
                if (!toInsert.isEmpty()) jdbcTemplate.batchUpdate(RAW_INSERT_SQL, toInsert);
                if (!toUpdate.isEmpty()) jdbcTemplate.batchUpdate(RAW_UPDATE_SQL, toUpdate);
            });
        }
        log.info("[ERP落库] 原料BOM merge：新增 {} 条，更新 {} 条", toInsert.size(), toUpdate.size());
        return new PersistResult(toInsert.size(), toUpdate.size());
    }

    // ==================== JSON 字段解析工具（camelCase / PascalCase 兼容） ====================

    /** 取字段节点：先 camelCase，再尝试 PascalCase（ASP.NET 默认序列化） */
    private static JsonNode field(JsonNode row, String name) {
        JsonNode n = row.get(name);
        if (n == null && !name.isEmpty()) {
            n = row.get(Character.toUpperCase(name.charAt(0)) + name.substring(1));
        }
        return n;
    }

    /** 字符串字段（空白归一为 null） */
    private static String text(JsonNode row, String name) {
        JsonNode n = field(row, name);
        if (n == null || n.isNull()) return null;
        String s = n.asText();
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    /** 数值字段（number / 数字字符串兼容） */
    private static BigDecimal dec(JsonNode row, String name) {
        JsonNode n = field(row, name);
        if (n == null || n.isNull()) return null;
        if (n.isNumber()) return n.decimalValue();
        String s = n.asText("").trim();
        if (s.isEmpty()) return null;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 整数字段 */
    private static Integer integer(JsonNode row, String name) {
        BigDecimal d = dec(row, name);
        return d != null ? d.intValue() : null;
    }

    /** 时间戳字段：兼容 "yyyy-MM-dd HH:mm:ss" / ISO / ASP.NET "/Date(millis)/" */
    private static Timestamp ts(JsonNode row, String name) {
        JsonNode n = field(row, name);
        if (n == null || n.isNull()) return null;
        if (n.isNumber()) {
            return new Timestamp(n.asLong());
        }
        String s = n.asText("").trim();
        if (s.isEmpty()) return null;
        try {
            if (s.startsWith("/Date(")) {
                int start = s.indexOf('(');
                int end = s.indexOf(')');
                if (start > 0 && end > start) {
                    String millis = s.substring(start + 1, end);
                    // 兼容 "/Date(1694025600000+0800)/" 时区后缀
                    int plus = millis.indexOf('+');
                    int minus = millis.indexOf('-', 1);
                    if (plus > 0) millis = millis.substring(0, plus);
                    if (minus > 0) millis = millis.substring(0, minus);
                    return new Timestamp(Long.parseLong(millis));
                }
            }
            String normalized = s.replace('T', ' ');
            if (normalized.length() == 10) {
                normalized += " 00:00:00";
            }
            // 截断到秒（去掉毫秒/时区后缀）
            if (normalized.length() > 19) {
                normalized = normalized.substring(0, 19);
            }
            return Timestamp.valueOf(normalized);
        } catch (Exception e) {
            return null;
        }
    }
}
