package com.imagemanager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.imagemanager.config.ErpProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * ERP 业务数据落库器 —— 「仅新增同步」
 * <p>
 * 规则：拉取 ERP 全量数据与本地表匹配比对，仅插入匹配失败的新增数据；
 * 已匹配到的存量数据不做任何修改/更新。
 * <p>
 * 匹配策略（杜绝 N+1，利用索引，内存可控）：
 * <ul>
 *   <li>有主键表（order_xs_list / order_jfk_gongyidan / order_sw_gongyidan）：
 *       INSERT ... ON CONFLICT (pk) DO NOTHING，由数据库主键唯一索引完成匹配。</li>
 *   <li>无唯一约束表（order_buj_component / order_gongxu_process / order_gongxu_price / raw_material_warehouse）：
 *       依赖 V58 的 md5(业务键) 表达式索引，分批用 WHERE md5(...) IN (...) 一次查询批量比对，
 *       差集即新增。md5 定长 16 字节，规避多列 varchar(500) 组合索引超 2704 字节上限。</li>
 * </ul>
 * 事务控制：每批（默认 1000 条，erp.sync-batch-size 可调）独立事务提交；
 * 某批失败仅回滚当前批、记录失败明细，不影响已提交批次；重复执行幂等。
 * <p>
 * ⚠️ 事务陷阱：application.yml 中 HikariCP auto-commit=false，无事务时 JdbcTemplate
 * 写操作会被连接池回滚，因此所有写库操作均通过 TransactionTemplate 编程式事务。
 */
@Component
public class ErpDataPersister {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ErpProperties erpProperties;

    public ErpDataPersister(JdbcTemplate jdbcTemplate,
                            TransactionTemplate transactionTemplate,
                            ErpProperties erpProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.erpProperties = erpProperties;
    }

    /** 同步落库结果：inserted=实际插入，skipped=已存在跳过，failed=失败条数，total=ERP 返回总数 */
    public record PersistResult(int inserted, int skipped, int failed, int total,
                                List<String> failedBatchDetails) {
        public static PersistResult empty() {
            return new PersistResult(0, 0, 0, 0, List.of());
        }
    }

    /** 将 ERP 返回的数组按模块落库（仅新增） */
    public PersistResult persist(String moduleKey, JsonNode rows) {
        if (rows == null || !rows.isArray() || rows.isEmpty()) {
            return PersistResult.empty();
        }
        List<JsonNode> list = new ArrayList<>(rows.size());
        rows.forEach(list::add);
        int batchSize = Math.max(1, erpProperties.getSyncBatchSize());
        try {
            return switch (moduleKey) {
                case "orders" -> persistWithPk(list, INSERT_SALES_ORDER, ErpDataPersister::buildSalesOrderArgs, batchSize);
                case "neiyi-gongyidan" -> persistWithPk(list, INSERT_JFK_GYD, ErpDataPersister::buildJfkArgs, batchSize);
                case "siwa-gongyidan" -> persistWithPk(list, INSERT_SW_GYD, ErpDataPersister::buildSwArgs, batchSize);
                case "gongyi-bujian" -> persistInsertOnly(list, "order_buj_component",
                        List.of("hhname", "color", "chima", "buj", "zbj", "jix"),
                        List.of("hhname", "color", "chima", "buj", "zbj", "jix"),
                        INSERT_BUJ, ErpDataPersister::buildBujArgs, batchSize);
                case "gongyi-gongxu" -> persistInsertOnly(list, "order_gongxu_process",
                        List.of("hhname", "wtname", "jizhong", "zhenju", "zhenhao", "zhenmu"),
                        List.of("hhname", "wtname", "jizhong", "zhenju", "zhenhao", "zhenmu"),
                        INSERT_GONGXU, ErpDataPersister::buildGongxuArgs, batchSize);
                case "gongxu-gongjia" -> persistInsertOnly(list, "order_gongxu_price",
                        List.of("hhname", "wtname"),
                        List.of("hhname", "wtname"),
                        INSERT_GONGJIA, ErpDataPersister::buildGongjiaArgs, batchSize);
                case "yuanliao-bom" -> persistInsertOnly(list, "raw_material_warehouse",
                        List.of("huohao", "color", "size", "component", "material_name", "specification", "batch_no"),
                        List.of("hhname", "color", "chima", "buj", "wlname", "guige", "pihao"),
                        INSERT_MATERIAL, ErpDataPersister::buildMaterialArgs, batchSize);
                default -> new PersistResult(0, 0, 0, list.size(),
                        List.of("未知模块: " + moduleKey));
            };
        } catch (Exception e) {
            return new PersistResult(0, 0, list.size(), list.size(),
                    List.of("落库异常: " + e.getMessage()));
        }
    }

    // ================================================================
    // 有主键表：INSERT ... ON CONFLICT (pk) DO NOTHING（主键索引匹配，天然幂等）
    // ================================================================

    private PersistResult persistWithPk(List<JsonNode> rows, String insertSql,
                                        Function<JsonNode, Object[]> argsBuilder, int batchSize) {
        int inserted = 0, skipped = 0, failed = 0;
        List<String> failedDetails = new ArrayList<>();
        for (int start = 0, batchNo = 1; start < rows.size(); start += batchSize, batchNo++) {
            List<JsonNode> batch = rows.subList(start, Math.min(start + batchSize, rows.size()));
            List<Object[]> argsList = new ArrayList<>(batch.size());
            for (JsonNode row : batch) {
                argsList.add(argsBuilder.apply(row));
            }
            try {
                int[] results = transactionTemplate.execute(status -> jdbcTemplate.batchUpdate(insertSql, argsList));
                if (results != null) {
                    for (int r : results) {
                        if (r > 0) {
                            inserted++;
                        } else if (r == 0 || r == Statement.SUCCESS_NO_INFO) {
                            skipped++;
                        } else {
                            failed++;
                        }
                    }
                }
            } catch (Exception e) {
                failed += batch.size();
                failedDetails.add("批次" + batchNo + "(" + batch.size() + "条)失败: " + abbreviate(rootMessage(e)));
            }
        }
        return new PersistResult(inserted, skipped, failed, rows.size(), List.copyOf(failedDetails));
    }

    // ================================================================
    // 无唯一约束表：md5(业务键) 表达式索引批量比对 + 差集插入
    // 每批：1 次 IN 查询（走 idx_*_match_md5 索引）+ 1 次 batchUpdate，无 N+1
    // ================================================================

    private PersistResult persistInsertOnly(List<JsonNode> rows, String table,
                                            List<String> localKeyCols, List<String> erpKeyFields,
                                            String insertSql, Function<JsonNode, Object[]> argsBuilder,
                                            int batchSize) {
        String md5Expr = buildMd5Expr(localKeyCols);
        int inserted = 0, skipped = 0, failed = 0;
        List<String> failedDetails = new ArrayList<>();

        for (int start = 0, batchNo = 1; start < rows.size(); start += batchSize, batchNo++) {
            List<JsonNode> batch = rows.subList(start, Math.min(start + batchSize, rows.size()));
            // 批内按业务键去重（ERP 数据自身重复时仅保留首条，其余计跳过，保证幂等）
            Map<String, JsonNode> uniqueRows = new LinkedHashMap<>();
            int dupInBatch = 0;
            for (JsonNode row : batch) {
                String key = md5Hex(joinKey(row, erpKeyFields));
                if (uniqueRows.putIfAbsent(key, row) != null) {
                    dupInBatch++;
                }
            }
            skipped += dupInBatch;

            try {
                int[] counters = transactionTemplate.execute(status -> {
                    List<String> keys = new ArrayList<>(uniqueRows.keySet());
                    String inClause = String.join(", ", Collections.nCopies(keys.size(), "?"));
                    String matchSql = "SELECT " + md5Expr + " FROM " + table
                            + " WHERE " + md5Expr + " IN (" + inClause + ")";
                    Set<String> existing = new HashSet<>(
                            jdbcTemplate.queryForList(matchSql, String.class, keys.toArray()));
                    List<Object[]> toInsert = new ArrayList<>();
                    for (Map.Entry<String, JsonNode> entry : uniqueRows.entrySet()) {
                        if (!existing.contains(entry.getKey())) {
                            toInsert.add(argsBuilder.apply(entry.getValue()));
                        }
                    }
                    if (!toInsert.isEmpty()) {
                        jdbcTemplate.batchUpdate(insertSql, toInsert);
                    }
                    return new int[]{toInsert.size(), keys.size() - toInsert.size()};
                });
                if (counters != null) {
                    inserted += counters[0];
                    skipped += counters[1];
                }
            } catch (Exception e) {
                failed += uniqueRows.size();
                failedDetails.add("批次" + batchNo + "(" + uniqueRows.size() + "条)失败: " + abbreviate(rootMessage(e)));
            }
        }
        return new PersistResult(inserted, skipped, failed, rows.size(), List.copyOf(failedDetails));
    }

    // ================================================================
    // INSERT SQL（仅新增；PK 表带 ON CONFLICT DO NOTHING）
    // ================================================================

    private static final String INSERT_SALES_ORDER =
            "INSERT INTO order_xs_list (dh, zhdate, state, zxtate, printnum, jh_date, business_dh, ddtype, khname,"
                    + " detailhuohaocp, detailhuohao, sl_sum, remark, ywyname, sfplan, zhuser, checkuser, ckeckdate)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                    + " ON CONFLICT (dh) DO NOTHING";

    private static final String INSERT_JFK_GYD =
            "INSERT INTO order_jfk_gongyidan (bh, hhtype, huohao, spname, designer, dw, rsjgh, qd_dys, hd_dys, dybanhao, remark)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                    + " ON CONFLICT (bh) DO NOTHING";

    private static final String INSERT_SW_GYD =
            "INSERT INTO order_sw_gongyidan (bh, hhtype, huohao, spname, dybanhao, cxm, xjkz, xjsl, pfkz, cpkz, zcl,"
                    + " jix, zs, yajiao, nd, djcl, hhywy, qd_dys, hd_dys, dw, remark)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                    + " ON CONFLICT (bh) DO NOTHING";

    private static final String INSERT_BUJ =
            "INSERT INTO order_buj_component (hhname, color, chima, buj, zbj, jix, zs, cxm, tongjing, bili, kez,"
                    + " xjtime, tjcxm, tjxs, tzs, skzjj, xf, zznd, llcl, remark, vchima, vcolor, vtzs, ischeck, isrecheck)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_GONGXU =
            "INSERT INTO order_gongxu_process (hhname, wtname, jizhong, zhenju, zhenhao, zhenmu, zhens, zline, sline,"
                    + " yongl, yongl2, \"sort\", tjtype, sctype, using_state, zhgx, tims, ischeck, isrecheck)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_GONGJIA =
            "INSERT INTO order_gongxu_price (hhname, wtname, jsprice, price, tempworker_price, state)"
                    + " VALUES (?, ?, ?, ?, ?, ?)";

    private static final String INSERT_MATERIAL =
            "INSERT INTO raw_material_warehouse (huohao, color, size, component, supplier, material_name, specification,"
                    + " material_color, batch_no, twist_direction, unit, usage_per_unit, loss_rate, remark)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    // ================================================================
    // 行参数构建（ERP 字段 → 本地列）
    // ================================================================

    private static Object[] buildSalesOrderArgs(JsonNode r) {
        return new Object[]{
                str(r, "dh"), toTs(r.get("zhdate")), str(r, "state"), str(r, "zxtate"),
                integer(r, "printnum"), toTs(r.get("jh_date")), str(r, "business_dh"), str(r, "ddtype"),
                str(r, "khname"), str(r, "detailhuohaocp"), str(r, "detailhuohao"),
                decimal(r, "sl_sum"), str(r, "remark"), str(r, "ywyname"), str(r, "sfplan"),
                str(r, "zhuser"), str(r, "checkuser"), toTs(r.get("ckeckdate"))};
    }

    private static Object[] buildJfkArgs(JsonNode r) {
        return new Object[]{
                str(r, "bh"), str(r, "hhtype"), str(r, "huohao"), str(r, "spname"), str(r, "designer"),
                str(r, "dw"), integer(r, "rsjgh"), str(r, "qd_dys"), str(r, "hd_dys"),
                str(r, "dybanhao"), str(r, "remark")};
    }

    private static Object[] buildSwArgs(JsonNode r) {
        return new Object[]{
                str(r, "bh"), str(r, "hhtype"), str(r, "huohao"), str(r, "spname"), str(r, "dybanhao"),
                str(r, "cxm"), str(r, "xjkz"), integer(r, "xjsl"), str(r, "pfkz"), str(r, "cpkz"),
                str(r, "zcl"), str(r, "jix"), str(r, "zs"), str(r, "yajiao"), integer(r, "nd"),
                str(r, "djcl"), str(r, "hhywy"), str(r, "qd_dys"), str(r, "hd_dys"), str(r, "dw"),
                str(r, "remark")};
    }

    private static Object[] buildBujArgs(JsonNode r) {
        // 列类型（order_buj_component）：zbj/zs=int4，tongjing/kez/xjtime/llcl=numeric，其余 varchar/text
        // varchar 列必须 str()（BigDecimal/Integer setObject 到 varchar 会报类型错误）；数值列可 str()（PG 隐式转换）
        return new Object[]{
                str(r, "hhname"), str(r, "color"), str(r, "chima"), str(r, "buj"), str(r, "zbj"),
                str(r, "jix"), str(r, "zs"), str(r, "cxm"), str(r, "tongjing"), str(r, "bili"),
                str(r, "kez"), decimal(r, "xjtime"), str(r, "tjcxm"), str(r, "tjxs"), str(r, "tzs"),
                str(r, "skzjj"), str(r, "xf"), str(r, "zznd"), str(r, "llcl"), str(r, "remark"),
                str(r, "vchima"), str(r, "vcolor"), str(r, "vtzs"), str(r, "ischeck"), str(r, "isrecheck")};
    }

    private static Object[] buildGongxuArgs(JsonNode r) {
        // 列类型（order_gongxu_process）：yongl/tims=numeric，sort=int4，zhenju~sline/yongl2 等均为 varchar
        return new Object[]{
                str(r, "hhname"), str(r, "wtname"), str(r, "jizhong"), str(r, "zhenju"),
                str(r, "zhenhao"), str(r, "zhenmu"), str(r, "zhens"), str(r, "zline"),
                str(r, "sline"), decimal(r, "yongl"), str(r, "yongl2"), integer(r, "sort"),
                str(r, "tjtype"), str(r, "sctype"), str(r, "using_state"), str(r, "zhgx"),
                decimal(r, "tims"), str(r, "ischeck"), str(r, "isrecheck")};
    }

    private static Object[] buildGongjiaArgs(JsonNode r) {
        return new Object[]{
                str(r, "hhname"), str(r, "wtname"), decimal(r, "jsprice"), decimal(r, "price"),
                decimal(r, "tempworker_price"), str(r, "state")};
    }

    private static Object[] buildMaterialArgs(JsonNode r) {
        return new Object[]{
                str(r, "hhname"), str(r, "color"), str(r, "chima"), str(r, "buj"), str(r, "gys"),
                str(r, "wlname"), str(r, "guige"), str(r, "wlcolor"), str(r, "pihao"), str(r, "nianx"),
                str(r, "dw"), decimal(r, "djyl"), decimal(r, "sh"), str(r, "remark")};
    }

    // ================================================================
    // 工具方法
    // ================================================================

    /** 构造与 V58 索引一致的 md5(业务键) SQL 表达式 */
    private static String buildMd5Expr(List<String> localKeyCols) {
        StringBuilder sb = new StringBuilder("md5(concat_ws('|', ");
        for (int i = 0; i < localKeyCols.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            // 统一 ::text 转换：order_buj_component.zbj 等列是 integer，直接 COALESCE(int_col,'') 会报类型错误；
            // 统一 TRIM：Java 侧 str() 带 trim，SQL 侧必须同步 TRIM，否则历史带空格数据匹配失败被误判为新增
            sb.append("COALESCE(TRIM(").append(localKeyCols.get(i)).append("::text), '')");
        }
        return sb.append("))").toString();
    }

    /** 按 ERP 字段拼接业务键（null/缺失统一为空串，与 SQL COALESCE(col,'') 对齐） */
    private static String joinKey(JsonNode row, List<String> erpKeyFields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < erpKeyFields.size(); i++) {
            if (i > 0) {
                sb.append('|');
            }
            // str() 空值返回 null，必须转 "" 与 SQL 侧 COALESCE(TRIM(col::text),'') 严格一致；
            // 直接 append(null) 会拼入 "null" 字符串导致 md5 永不匹配、存量被误判为新增
            String v = str(row, erpKeyFields.get(i));
            sb.append(v == null ? "" : v);
        }
        return sb.toString();
    }

    /** 与 PostgreSQL md5(text) 一致：UTF-8 字节的小写 hex */
    private static String md5Hex(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("MD5 计算失败", e);
        }
    }

    private static String str(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.isTextual() ? v.asText() : v.toString();
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        String s = str(node, field);
        if (s == null) {
            return null;
        }
        try {
            return new BigDecimal(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer integer(JsonNode node, String field) {
        String s = str(node, field);
        if (s == null) {
            return null;
        }
        try {
            return new BigDecimal(s.replace(",", "")).intValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Timestamp toTs(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String s = node.isTextual() ? node.asText().trim() : node.toString();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
            return null;
        }
        try {
            if (s.startsWith("/Date(") && s.endsWith(")/")) {
                long millis = Long.parseLong(s.substring(6, s.length() - 2));
                return new Timestamp(millis);
            }
            String normalized = s.replace('T', ' ');
            if (normalized.length() == 10) {
                normalized += " 00:00:00";
            }
            if (normalized.length() > 19) {
                normalized = normalized.substring(0, 19);
            }
            return Timestamp.valueOf(LocalDateTime.parse(normalized,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        } catch (Exception e) {
            return null;
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        String msg = t.getMessage();
        return msg != null ? msg : t.getClass().getSimpleName();
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }
}
