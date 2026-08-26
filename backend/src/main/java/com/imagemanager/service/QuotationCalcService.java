package com.imagemanager.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 报价单计算服务（确定性计算引擎）
 *
 * 最稳定实现原则：
 * 1. 查询全部使用参数化 SQL（防注入），只读 SELECT，强制 LIMIT
 * 2. 所有金额计算使用 BigDecimal，避免浮点误差
 * 3. 除法统一保留 4 位小数 + HALF_UP，除零保护
 * 4. 大模型只负责理解问题与解释结果，数学计算全部由本服务完成
 *
 * 计算公式（与业务方确认）：
 * 日产量 rcl      = 24*3600/下机时间 * 利用率
 * 织造成本 zzcb   = 机台费/日产量
 * 染色成本        = 表内存储值 rsprice 优先；否则 = 缝拼克重 fpkz × 染色单价 rsdj（两者现为表内列，外部输入可覆盖）
 * 原料金额 sumprice = 原料合计*(1-原料利用率+1)，原料合计为BOM明细合计（外部输入，缺省用表内 sumprice）
 * 前道合计 countprice = 织造成本+前道管理费用+染色成本+定型+其他工价+原料金额+缝制工价+腰口工价
 * 辅料金额 flsum  = 辅料合计*(1-辅料利用率+1)，辅料合计为BOM明细合计（外部输入，缺省用表内 flsum）
 * 后道合计 hdprice = 包装+后道管理费用+辅料金额+全检工价
 * 净成本 jcb      = (织造成本+染色成本+定型+其他工价+原料金额+缝制工价+腰口工价+全检工价+包装)*(1-正品率+1)+辅料金额+前道管理费用+后道管理费用
 * 理论税金 shuijin = 净成本*0.08
 * 实际税金 shuijin_sg = 默认理论税金（可修改）
 * 销售成本 xscb   = 净成本+运费+实际税金
 */
@Slf4j
@Service
public class QuotationCalcService {

    private static final int SCALE = 4;
    private static final String TABLE = "order_bjd_query";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ====== 安全查询（参数化 + 只读 + LIMIT） ======

    /** 按报价单号精确查询 */
    public List<Map<String, Object>> queryByDh(String dh) {
        String sql = "SELECT * FROM " + TABLE + " WHERE dh = ? LIMIT 50";
        return jdbcTemplate.queryForList(sql, dh);
    }

    /** 按客户名称精确查询 */
    public List<Map<String, Object>> queryByKhname(String khname) {
        String sql = "SELECT * FROM " + TABLE + " WHERE khname = ? LIMIT 50";
        return jdbcTemplate.queryForList(sql, khname);
    }

    /** 按客户名称查询全部行(供前端全量明细表, 上限1000) */
    public List<Map<String, Object>> queryByKhnameAll(String khname) {
        String sql = "SELECT * FROM " + TABLE + " WHERE khname = ? ORDER BY dh LIMIT 1000";
        return jdbcTemplate.queryForList(sql, khname);
    }

    /** 按生产货号模糊查询 */
    public List<Map<String, Object>> queryByHuohao(String huohao) {
        String sql = "SELECT * FROM " + TABLE + " WHERE huohao ILIKE ? LIMIT 50";
        return jdbcTemplate.queryForList(sql, "%" + huohao + "%");
    }

    /** 关键字精确查询（单号/客户精确匹配，不用模糊，保证准确） */
    public List<Map<String, Object>> queryByKeyword(String keyword) {
        String sql = "SELECT * FROM " + TABLE
                + " WHERE dh = ? OR khname = ? LIMIT 50";
        return jdbcTemplate.queryForList(sql, keyword, keyword);
    }

    // ====== 客户名称反向匹配与统计 ======

    private volatile List<String> khnameCache;
    private volatile long khnameCacheTime = 0L;

    /** 库内全部客户名称（5分钟缓存），用于从自然语言问题中反向识别客户实体 */
    public List<String> distinctKhnames() {
        long now = System.currentTimeMillis();
        List<String> cached = khnameCache;
        if (cached != null && now - khnameCacheTime < 5 * 60 * 1000L) return cached;
        try {
            List<String> list = jdbcTemplate.queryForList(
                    "SELECT DISTINCT khname FROM " + TABLE
                            + " WHERE khname IS NOT NULL AND TRIM(khname) <> '' ORDER BY khname LIMIT 500",
                    String.class);
            khnameCache = list;
            khnameCacheTime = now;
            return list;
        } catch (Exception e) {
            log.warn("查询客户名称列表失败: {}", e.getMessage());
            return cached != null ? cached : List.of();
        }
    }

    /**
     * 反向匹配：用户问题文本中是否包含库内客户名称。
     * 取最长匹配，避免"海宁"抢先匹配而漏掉"海宁世正"。
     */
    public String matchKhnameInQuery(String query) {
        if (query == null || query.isEmpty()) return null;
        String best = null;
        for (String kh : distinctKhnames()) {
            if (kh != null && kh.length() >= 2 && query.contains(kh)) {
                if (best == null || kh.length() > best.length()) best = kh;
            }
        }
        return best;
    }

    /** 按客户名称统计报价单总数（不受 LIMIT 限制） */
    public int countByKhname(String khname) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE khname = ?", Integer.class, khname);
        return c == null ? 0 : c;
    }

    /** 按客户名称查询全部报价单号（最多1000条，覆盖常规全量） */
    public List<String> listDhByKhname(String khname) {
        return jdbcTemplate.queryForList(
                "SELECT dh FROM " + TABLE + " WHERE khname = ? ORDER BY dh LIMIT 1000",
                String.class, khname);
    }

    // ====== 全局聚合统计（不依赖具体实体，支持"哪个客户单号最多"等任意问法） ======

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(v, max));
    }

    /** 客户单量排行（GROUP BY 统计，降序） */
    public List<Map<String, Object>> customerOrderRanking(int limit) {
        String sql = "SELECT khname, COUNT(*) AS order_count FROM " + TABLE
                + " WHERE khname IS NOT NULL AND TRIM(khname) <> ''"
                + " GROUP BY khname ORDER BY order_count DESC LIMIT " + clamp(limit, 1, 100);
        return jdbcTemplate.queryForList(sql);
    }

    /** 全客户报价汇总：单量 + 平均净成本 + 平均销售成本 */
    public List<Map<String, Object>> customerQuotationSummary(int limit) {
        String sql = "SELECT khname, COUNT(*) AS order_count,"
                + " ROUND(AVG(jcb), 4) AS avg_net_cost, ROUND(AVG(xscb), 4) AS avg_sales_cost"
                + " FROM " + TABLE
                + " WHERE khname IS NOT NULL AND TRIM(khname) <> ''"
                + " GROUP BY khname ORDER BY order_count DESC LIMIT " + clamp(limit, 1, 100);
        return jdbcTemplate.queryForList(sql);
    }

    /** 全局统计：总单数 / 客户总数 */
    public Map<String, Object> globalStats() {
        return jdbcTemplate.queryForMap(
                "SELECT COUNT(*) AS total_orders, COUNT(DISTINCT khname) AS total_customers FROM " + TABLE);
    }

    // ====== 确定性计算 ======

    /**
     * 基于一行数据计算全部派生指标
     * @param row 表行数据（key 为列名）
     * @param extra 可选外部输入（优先于表内列）：fpkz(缝拼克重) rsdj(染色单价) rawTotal(原料合计) auxTotal(辅料合计)
     *              表内新列（上游实体新增）：fpkz 缝拼克重 / rsdj 染色单价 / rsprice 染色成本(存储值优先) /
     *              qjprice 全检工价 / ykgj 腰口工价 / qdzs,hdzs 规格文本(不参与计算)
     * @return 计算结果（有序 Map，含中间量与最终指标）
     */
    public Map<String, BigDecimal> calculate(Map<String, Object> row, Map<String, BigDecimal> extra) {
        Map<String, BigDecimal> in = toDecimalMap(row);
        Map<String, BigDecimal> ex = extra != null ? extra : Map.of();

        BigDecimal zhis = get(in, "zhis");        // 下机时间
        BigDecimal lyl = get(in, "lyl");          // 利用率
        BigDecimal sbdj = get(in, "sbdj");        // 机台费
        BigDecimal qdglf = get(in, "qdglf");      // 前道管理费用
        BigDecimal dxprice = get(in, "dxprice");  // 定型
        BigDecimal otherprice = get(in, "otherprice"); // 其他工价
        BigDecimal yllyl = get(in, "yllyl");      // 原料利用率
        BigDecimal sumpriceStored = get(in, "sumprice"); // 表内原料金额
        BigDecimal fpprice = get(in, "fpprice");  // 缝拼(缝制)工价
        BigDecimal bzprice = get(in, "bzprice");  // 包装
        BigDecimal hdglf = get(in, "hdglf");      // 后道管理费用
        BigDecimal fllyl = get(in, "fllyl");      // 辅料利用率
        BigDecimal flsumStored = get(in, "flsum"); // 表内辅料金额
        BigDecimal zpl = get(in, "zpl");          // 正品率
        BigDecimal yunfei = get(in, "yunfei");    // 运费

        // 新增表内列（上游 C# 实体新增）：外部 extra 输入优先，缺省回退表内列
        BigDecimal fpkzRow = get(in, "fpkz");        // 缝拼克重(表内列)
        BigDecimal rsdjRow = get(in, "rsdj");        // 染色单价(表内列)
        BigDecimal rspriceRow = get(in, "rsprice");  // 染色成本(表内列，上游已算好)
        BigDecimal qjprice = get(in, "qjprice");     // 全检工价(表内列)
        BigDecimal ykgj = get(in, "ykgj");           // 腰口工价(表内列)
        BigDecimal fpkz = ex.containsKey("fpkz") ? get(ex, "fpkz") : fpkzRow;  // 缝拼克重
        BigDecimal rsdj = ex.containsKey("rsdj") ? get(ex, "rsdj") : rsdjRow;  // 染色单价
        BigDecimal rawTotal = get(ex, "rawTotal"); // 原料合计BOM(外部)
        BigDecimal auxTotal = get(ex, "auxTotal"); // 辅料合计BOM(外部)

        // 缝拼克重批量预取兜底（调用方循环外一次 IN 查询后注入 fpkzFallback，消除 N+1 逐行查库）
        if (fpkz.compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal prefetched = get(ex, "fpkzFallback");
            if (prefetched.compareTo(BigDecimal.ZERO) != 0) fpkz = prefetched;
        }
        // 缝拼克重单行兜底：仍无 fpkz 时按货号查工艺单 pfkz（order_sw_gongyidan，V45 权威工艺基准）
        if (fpkz.compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal fromProcess = lookupProcessSewingWeight(row);
            if (fromProcess.compareTo(BigDecimal.ZERO) != 0) fpkz = fromProcess;
        }

        Map<String, BigDecimal> r = new LinkedHashMap<>();

        // 日产量 = 24*3600/下机时间 * 利用率
        BigDecimal rcl = BigDecimal.ZERO;
        if (zhis.compareTo(BigDecimal.ZERO) != 0) {
            rcl = div(mul(BigDecimal.valueOf(24 * 3600), pct(lyl)), zhis);
        }
        r.put("rcl_日产量", rcl);

        // 织造成本 = 机台费/日产量
        BigDecimal zzcb = rcl.compareTo(BigDecimal.ZERO) != 0 ? div(sbdj, rcl) : BigDecimal.ZERO;
        r.put("zzcb_织造成本", zzcb);

        // 染色成本 = 表内存储值 rsprice 优先；无存储值时 = 缝拼克重*染色单价
        BigDecimal dyeCost = rspriceRow.compareTo(BigDecimal.ZERO) != 0
                ? rspriceRow : mul(fpkz, rsdj);
        r.put("染色成本", dyeCost);
        r.put("ykgj_腰口工价", ykgj);
        r.put("qjprice_全检", qjprice);

        // 原料金额 = 原料合计*(1-原料利用率+1)，无BOM合计则用表内值
        BigDecimal rawAmount = rawTotal.compareTo(BigDecimal.ZERO) != 0
                ? mul(rawTotal, factor(yllyl)) : sumpriceStored;
        r.put("sumprice_原料金额", rawAmount);

        // 辅料金额 = 辅料合计*(1-辅料利用率+1)，无BOM合计则用表内值
        BigDecimal auxAmount = auxTotal.compareTo(BigDecimal.ZERO) != 0
                ? mul(auxTotal, factor(fllyl)) : flsumStored;
        r.put("flsum_辅料金额", auxAmount);

        // 前道合计 = 织造成本+前道管理费用+染色成本+定型+其他工价+原料金额+缝制工价+腰口工价
        BigDecimal countprice = sum(zzcb, qdglf, dyeCost, dxprice, otherprice, rawAmount, fpprice, ykgj);
        r.put("countprice_前道合计", countprice);

        // 后道合计 = 包装+后道管理费用+辅料金额+全检工价
        BigDecimal hdprice = sum(bzprice, hdglf, auxAmount, qjprice);
        r.put("hdprice_后道合计", hdprice);

        // 净成本 = (织造成本+染色成本+定型+其他工价+原料金额+缝制工价+腰口工价+全检工价+包装)*(1-正品率+1)+辅料金额+前道管理费用+后道管理费用
        BigDecimal base = sum(zzcb, dyeCost, dxprice, otherprice, rawAmount, fpprice, bzprice, ykgj, qjprice);
        BigDecimal jcb = add(mul(base, factor(zpl)), sum(auxAmount, qdglf, hdglf));
        r.put("jcb_净成本", jcb);

        // 理论税金 = 净成本*0.08
        BigDecimal shuijin = mul(jcb, BigDecimal.valueOf(0.08));
        r.put("shuijin_理论税金", shuijin);

        // 实际税金 = 默认理论税金（可修改）
        BigDecimal shuijinSg = shuijin;
        r.put("shuijin_sg_实际税金", shuijinSg);

        // 销售成本 = 净成本+运费+实际税金
        BigDecimal xscb = sum(jcb, yunfei, shuijinSg);
        r.put("xscb_销售成本", xscb);

        return r;
    }

    /** (1 - 比率 + 1)，比率按小数(0~1)存储 */
    private BigDecimal factor(BigDecimal rate) {
        return BigDecimal.ONE.subtract(pct(rate)).add(BigDecimal.ONE);
    }

    /** 比率归一化：DB 存百分数(94 表示 94%)，>1 时转为小数 0.94；已为小数(<=1)则原样返回 */
    private BigDecimal pct(BigDecimal v) {
        if (v == null) return BigDecimal.ZERO;
        return v.compareTo(BigDecimal.ONE) > 0
                ? v.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
                : v;
    }

    // ====== BigDecimal 工具（null 安全 + 除零保护） ======

    /** 工艺单缝拼克重兜底查询（order_sw_gongyidan.pfkz，按生产货号精确匹配） */
    private BigDecimal lookupProcessSewingWeight(Map<String, Object> row) {
        try {
            Object huohao = row.get("huohao");
            if (huohao == null || huohao.toString().isBlank()) return BigDecimal.ZERO;
            List<BigDecimal> r = jdbcTemplate.queryForList(
                    "SELECT pfkz FROM order_sw_gongyidan WHERE huohao = ? AND pfkz IS NOT NULL AND pfkz > 0 LIMIT 1",
                    BigDecimal.class, huohao.toString());
            return r.isEmpty() ? BigDecimal.ZERO : r.get(0);
        } catch (Exception e) {
            log.debug("工艺单缝拼克重兜底查询失败: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * 批量查询工艺单缝拼克重（消除循环内逐行查库的 N+1 问题）
     * 调用方在循环外一次性传入全部货号，循环内通过 extra.put("fpkzFallback", 值) 注入。
     * 取数优先级保持不变：外部 fpkz > 表内 fpkz 列 > 本批量结果 > 单行兜底查询。
     *
     * @param huohaos 生产货号集合（去重、去空，最多取 500 个防 IN 过大）
     * @return huohao -> pfkz 映射；查询失败返回空 Map（不影响主流程）
     */
    public Map<String, BigDecimal> batchLookupProcessSewingWeights(Collection<String> huohaos) {
        if (huohaos == null || huohaos.isEmpty()) return Collections.emptyMap();
        try {
            List<String> codes = huohaos.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .limit(500)
                    .collect(Collectors.toList());
            if (codes.isEmpty()) return Collections.emptyMap();
            String inClause = codes.stream().map(c -> "?").collect(Collectors.joining(","));
            // 同一货号可能有多版本工艺单，取最大 pfkz（正值过滤后 MAX 稳定可重现）
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT huohao, MAX(pfkz) AS pfkz FROM order_sw_gongyidan "
                            + "WHERE huohao IN (" + inClause + ") AND pfkz IS NOT NULL AND pfkz > 0 "
                            + "GROUP BY huohao",
                    codes.toArray());
            Map<String, BigDecimal> result = new HashMap<>();
            for (Map<String, Object> r : rows) {
                Object hh = r.get("huohao");
                Object pfkz = r.get("pfkz");
                if (hh != null && pfkz != null) {
                    result.put(hh.toString(), new BigDecimal(pfkz.toString()));
                }
            }
            return result;
        } catch (Exception e) {
            log.debug("批量工艺单缝拼克重查询失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private BigDecimal get(Map<String, BigDecimal> m, String key) {
        if (m == null) return BigDecimal.ZERO;
        BigDecimal v = m.get(key);
        return v != null ? v : BigDecimal.ZERO;
    }

    private BigDecimal mul(BigDecimal a, BigDecimal b) {
        return a.multiply(b).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal div(BigDecimal a, BigDecimal b) {
        if (b.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return a.divide(b, SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal sum(BigDecimal... vals) {
        BigDecimal s = BigDecimal.ZERO;
        for (BigDecimal v : vals) s = s.add(v);
        return s.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal add(BigDecimal a, BigDecimal b) {
        return a.add(b).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private Map<String, BigDecimal> toDecimalMap(Map<String, Object> row) {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            m.put(e.getKey().toLowerCase(), toDecimal(e.getValue()));
        }
        return m;
    }

    private BigDecimal toDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(v.toString().trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
