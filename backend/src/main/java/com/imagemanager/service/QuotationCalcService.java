package com.imagemanager.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * 染色成本        = 缝拼克重*染色单价（外部输入，可选）
 * 原料金额 sumprice = 原料合计*(1-原料利用率+1)，原料合计为BOM明细合计（外部输入，缺省用表内 sumprice）
 * 前道合计 countprice = 织造成本+前道管理费用+染色成本+定型+其他工价+原料金额+缝制工价
 * 辅料金额 flsum  = 辅料合计*(1-辅料利用率+1)，辅料合计为BOM明细合计（外部输入，缺省用表内 flsum）
 * 后道合计 hdprice = 包装+后道管理费用+辅料金额
 * 净成本 jcb      = (织造成本+染色成本+定型+其他工价+原料金额+缝制工价+包装)*(1-正品率+1)+辅料金额+前道管理费用+后道管理费用
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
     * @param extra 可选外部输入：fpkz(缝拼克重) rsdj(染色单价) rawTotal(原料合计) auxTotal(辅料合计)
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

        BigDecimal fpkz = get(ex, "fpkz");        // 缝拼克重(外部)
        BigDecimal rsdj = get(ex, "rsdj");        // 染色单价(外部)
        BigDecimal rawTotal = get(ex, "rawTotal"); // 原料合计BOM(外部)
        BigDecimal auxTotal = get(ex, "auxTotal"); // 辅料合计BOM(外部)

        Map<String, BigDecimal> r = new LinkedHashMap<>();

        // 日产量 = 24*3600/下机时间 * 利用率
        BigDecimal rcl = BigDecimal.ZERO;
        if (zhis.compareTo(BigDecimal.ZERO) != 0) {
            rcl = div(mul(BigDecimal.valueOf(24 * 3600), lyl), zhis);
        }
        r.put("rcl_日产量", rcl);

        // 织造成本 = 机台费/日产量
        BigDecimal zzcb = rcl.compareTo(BigDecimal.ZERO) != 0 ? div(sbdj, rcl) : BigDecimal.ZERO;
        r.put("zzcb_织造成本", zzcb);

        // 染色成本 = 缝拼克重*染色单价
        BigDecimal dyeCost = mul(fpkz, rsdj);
        r.put("染色成本", dyeCost);

        // 原料金额 = 原料合计*(1-原料利用率+1)，无BOM合计则用表内值
        BigDecimal rawAmount = rawTotal.compareTo(BigDecimal.ZERO) != 0
                ? mul(rawTotal, factor(yllyl)) : sumpriceStored;
        r.put("sumprice_原料金额", rawAmount);

        // 辅料金额 = 辅料合计*(1-辅料利用率+1)，无BOM合计则用表内值
        BigDecimal auxAmount = auxTotal.compareTo(BigDecimal.ZERO) != 0
                ? mul(auxTotal, factor(fllyl)) : flsumStored;
        r.put("flsum_辅料金额", auxAmount);

        // 前道合计 = 织造成本+前道管理费用+染色成本+定型+其他工价+原料金额+缝制工价
        BigDecimal countprice = sum(zzcb, qdglf, dyeCost, dxprice, otherprice, rawAmount, fpprice);
        r.put("countprice_前道合计", countprice);

        // 后道合计 = 包装+后道管理费用+辅料金额
        BigDecimal hdprice = sum(bzprice, hdglf, auxAmount);
        r.put("hdprice_后道合计", hdprice);

        // 净成本 = (织造成本+染色成本+定型+其他工价+原料金额+缝制工价+包装)*(1-正品率+1)+辅料金额+前道管理费用+后道管理费用
        BigDecimal base = sum(zzcb, dyeCost, dxprice, otherprice, rawAmount, fpprice, bzprice);
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
        return BigDecimal.ONE.subtract(rate).add(BigDecimal.ONE);
    }

    // ====== BigDecimal 工具（null 安全 + 除零保护） ======

    private BigDecimal get(Map<String, BigDecimal> m, String key) {
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
