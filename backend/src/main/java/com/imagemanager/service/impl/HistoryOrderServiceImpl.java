package com.imagemanager.service.impl;

import com.imagemanager.service.HistoryOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 历史订单服务实现
 * 数据源：order_xs_list（销售订单），固定只查 state='1'（已审核投入生产）的订单；
 * 品名通过生产货号 LEFT JOIN 内衣工艺单（order_jfk_gongyidan）去重带出。
 * 只读查询，不涉及写库，无需事务。
 */
@Slf4j
@Service
public class HistoryOrderServiceImpl implements HistoryOrderService {

    /** 已审核状态值（state：0→编辑、1→审核、其他→待审核） */
    /** 实际库中 state 存中文文本："审核"=已审核投入生产、"未审核"=未审核（表注释中的0/1与实际数据不符，以实际为准） */
    private static final String STATE_APPROVED = "审核";

    /** 排序字段白名单（防注入） */
    private static final Set<String> SORT_FIELDS = Set.of("zhdate", "jh_date", "sl_sum", "dh");

    /** 品名子查询：同一货号在工艺单可能有多条（不同版号），按货号去重取一条 */
    private static final String SPNAME_JOIN =
            " LEFT JOIN (SELECT DISTINCT ON (huohao) huohao, spname FROM order_jfk_gongyidan" +
            " WHERE huohao IS NOT NULL) j ON xs.detailhuohao = j.huohao";

    private static final String BASE_SELECT =
            "SELECT xs.dh, xs.zhdate, xs.state, xs.zxtate, xs.jh_date, xs.business_dh, xs.ddtype," +
            " xs.khname, xs.detailhuohaocp, xs.detailhuohao, xs.sl_sum, xs.remark, xs.ywyname," +
            " xs.sfplan, xs.zhuser, xs.checkuser, xs.ckeckdate, j.spname" +
            " FROM order_xs_list xs" + SPNAME_JOIN;

    private final JdbcTemplate jdbcTemplate;

    public HistoryOrderServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, Object> listOrders(int page, int size, String keyword, String zxtate, String sfplan,
                                          String dateFrom, String dateTo, String sortField, String sortOrder) {
        page = Math.max(1, page);
        size = Math.min(Math.max(1, size), 100);

        StringBuilder where = new StringBuilder(" WHERE xs.state = ?");
        List<Object> params = new ArrayList<>();
        params.add(STATE_APPROVED);

        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (xs.dh ILIKE ? OR xs.business_dh ILIKE ? OR xs.khname ILIKE ?" +
                    " OR xs.detailhuohao ILIKE ? OR xs.detailhuohaocp ILIKE ? OR xs.ywyname ILIKE ?)");
            String like = "%" + keyword.trim() + "%";
            for (int i = 0; i < 6; i++) {
                params.add(like);
            }
        }
        if (zxtate != null && !zxtate.isBlank()) {
            // zxtate 实际值可能是中文（未审核/已复审/已经终审）或代码（0/1/其他），做双兼容
            switch (zxtate.trim()) {
                case "reviewed", "1", "已复审" -> where.append(" AND xs.zxtate IN ('1', '已复审')");
                case "unaudited", "0", "未审核" -> where.append(" AND xs.zxtate IN ('0', '未审核')");
                case "final", "已经终审" ->
                        where.append(" AND xs.zxtate NOT IN ('0', '1', '未审核', '已复审')" +
                                " AND BTRIM(COALESCE(xs.zxtate, '')) <> ''");
                default -> {
                    where.append(" AND xs.zxtate = ?");
                    params.add(zxtate.trim());
                }
            }
        }
        if (sfplan != null && !sfplan.isBlank()) {
            // sfplan 实际值兼容"是"/"已下计划"
            if ("是".equals(sfplan.trim()) || "已下计划".equals(sfplan.trim())) {
                where.append(" AND xs.sfplan IN ('是', '已下计划')");
            } else {
                where.append(" AND COALESCE(xs.sfplan, '否') = ?");
                params.add(sfplan.trim());
            }
        }
        if (dateFrom != null && !dateFrom.isBlank()) {
            where.append(" AND xs.zhdate >= ?::date");
            params.add(dateFrom.trim());
        }
        if (dateTo != null && !dateTo.isBlank()) {
            where.append(" AND xs.zhdate < (?::date + 1)");
            params.add(dateTo.trim());
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_xs_list xs" + where, Long.class, params.toArray());

        String orderCol = SORT_FIELDS.contains(sortField) ? sortField : "zhdate";
        String orderDir = "asc".equalsIgnoreCase(sortOrder) ? "ASC" : "DESC";
        // zhdate/dh 之外加 dh 兜底保证分页稳定
        String orderBy = " ORDER BY xs." + orderCol + " " + orderDir + " NULLS LAST, xs.dh ASC";

        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(size);
        queryParams.add((page - 1) * size);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                BASE_SELECT + where + orderBy + " LIMIT ? OFFSET ?", queryParams.toArray());

        List<Map<String, Object>> list = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            list.add(toOrderMap(row));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", total == null ? 0 : total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    @Override
    public Map<String, Object> stats() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT COUNT(*) AS total_orders, COALESCE(SUM(sl_sum), 0) AS total_quantity," +
                            " COUNT(DISTINCT khname) AS customer_count," +
                            " COUNT(*) FILTER (WHERE sfplan IN ('是', '已下计划')) AS planned_count," +
                            " COUNT(*) FILTER (WHERE zhdate >= date_trunc('month', CURRENT_DATE)) AS month_new_count" +
                            " FROM order_xs_list WHERE state = ?", STATE_APPROVED);
            result.put("totalOrders", row.get("total_orders"));
            result.put("totalQuantity", row.get("total_quantity"));
            result.put("customerCount", row.get("customer_count"));
            result.put("plannedCount", row.get("planned_count"));
            result.put("monthNewCount", row.get("month_new_count"));
        } catch (Exception e) {
            log.warn("[历史订单] 统计查询失败: {}", e.getMessage());
            result.put("totalOrders", 0);
            result.put("totalQuantity", 0);
            result.put("customerCount", 0);
            result.put("plannedCount", 0);
            result.put("monthNewCount", 0);
        }
        return result;
    }

    @Override
    public Map<String, Object> getOrder(String dh) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                BASE_SELECT + " WHERE xs.dh = ?", dh);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("订单不存在(dh=" + dh + ")");
        }
        return toOrderMap(rows.get(0));
    }

    /** 行数据 → 前端结构（状态码转文本） */
    private Map<String, Object> toOrderMap(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dh", row.get("dh"));
        m.put("zhdate", row.get("zhdate"));
        m.put("jhDate", row.get("jh_date"));
        m.put("state", row.get("state"));
        m.put("stateText", stateText(nz(row.get("state"))));
        m.put("zxtate", row.get("zxtate"));
        m.put("zxtateText", zxtateText(nz(row.get("zxtate"))));
        m.put("businessDh", row.get("business_dh"));
        m.put("ddtype", row.get("ddtype"));
        m.put("khname", row.get("khname"));
        m.put("detailhuohaocp", row.get("detailhuohaocp"));
        m.put("detailhuohao", row.get("detailhuohao"));
        m.put("spname", row.get("spname"));
        m.put("slSum", row.get("sl_sum"));
        m.put("remark", row.get("remark"));
        String ywy = nz(row.get("ywyname"));
        m.put("ywyname", ywy);
        m.put("ywynameText", ywy != null ? ywy : "（业务员数据未维护）");
        m.put("sfplan", row.get("sfplan"));
        String sfplanVal = nz(row.get("sfplan"));
        m.put("sfplanText", ("是".equals(sfplanVal) || "已下计划".equals(sfplanVal)) ? "已下计划" : "未下计划");
        m.put("zhuser", row.get("zhuser"));
        m.put("checkuser", row.get("checkuser"));
        m.put("ckeckdate", row.get("ckeckdate"));
        return m;
    }

    /** state 实际值为中文（审核/未审核），兼容历史代码值（0/1） */
    private String stateText(String state) {
        if (state == null) return "待审核";
        return switch (state) {
            case "审核", "1" -> "已审核";
            case "0" -> "编辑";
            default -> "待审核";
        };
    }

    /** zxtate（执行状态）实际值可能为中文（未审核/已复审/已经终审），兼容代码值（0/1/其他） */
    private String zxtateText(String zxtate) {
        if (zxtate == null) return "已经终审";
        return switch (zxtate) {
            case "0", "未审核" -> "未审核";
            case "1", "已复审" -> "已复审";
            default -> "已经终审";
        };
    }

    private String nz(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }
}
