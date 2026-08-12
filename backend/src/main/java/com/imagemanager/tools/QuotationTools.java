package com.imagemanager.tools;

import com.imagemanager.service.QuotationCalcService;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 报价单 AI 工具集（LangChain4j @Tool 函数调用）
 *
 * 最稳定方案：大模型不直接写 SQL，而是调用预定义的安全工具：
 * - 查询工具：参数化 SQL，只读，防注入
 * - 计算工具：后端 BigDecimal 确定性计算，大模型不碰数学
 */
@Slf4j
@Component
public class QuotationTools {

    @Autowired
    private QuotationCalcService calcService;

    @Tool("按报价单号(dh)精确查询报价单数据，返回该单的原始字段。当用户提到具体报价单号时使用。")
    public String queryByDh(String dh) {
        List<Map<String, Object>> rows = calcService.queryByDh(dh);
        return formatRows(rows);
    }

    @Tool("按客户名称(khname)模糊查询报价单列表。当用户询问某个客户的报价时使用。")
    public String queryByKhname(String khname) {
        List<Map<String, Object>> rows = calcService.queryByKhname(khname);
        return formatRows(rows);
    }

    @Tool("按生产货号(huohao)模糊查询报价单列表。当用户询问某个货号时使用。")
    public String queryByHuohao(String huohao) {
        List<Map<String, Object>> rows = calcService.queryByHuohao(huohao);
        return formatRows(rows);
    }

    @Tool("统计每个客户的报价单数量并按数量降序排行。当用户问'哪个客户单号最多/单量排行/客户单量对比/谁下单最多'等全局聚合问题时使用。")
    public String rankCustomersByOrderCount(int topN) {
        List<Map<String, Object>> rows = calcService.customerOrderRanking(topN);
        if (rows.isEmpty()) return "暂无报价单数据";
        StringBuilder sb = new StringBuilder("客户单量排行(降序):\n");
        for (Map<String, Object> r : rows) {
            sb.append(r.get("khname")).append(" = ").append(r.get("order_count")).append(" 单\n");
        }
        return sb.toString();
    }

    @Tool("汇总所有客户的报价数据：单量、平均净成本、平均销售成本。当用户问'所有客户报价汇总/整体报价情况/各客户成本对比'时使用。")
    public String summarizeAllCustomers(int topN) {
        List<Map<String, Object>> rows = calcService.customerQuotationSummary(topN);
        if (rows.isEmpty()) return "暂无报价单数据";
        StringBuilder sb = new StringBuilder("客户报价汇总:\n");
        for (Map<String, Object> r : rows) {
            sb.append(r.get("khname")).append(": 单量=").append(r.get("order_count"))
              .append(", 平均净成本=").append(r.get("avg_net_cost"))
              .append(", 平均销售成本=").append(r.get("avg_sales_cost")).append("\n");
        }
        return sb.toString();
    }

    @Tool("统计报价单总数与客户总数。当用户问'一共多少单/有多少客户/总体规模'时使用。")
    public String globalQuotationStats() {
        Map<String, Object> s = calcService.globalStats();
        return "报价单总数=" + s.get("total_orders") + ", 客户总数=" + s.get("total_customers");
    }

    @Tool("按客户名称统计报价单数量。当用户问某客户有多少单/下单数量时使用。")
    public String countOrdersByCustomer(String customer) {
        int c = calcService.countByKhname(customer);
        return "客户 " + customer + " 的报价单数量 = " + c;
    }

    @Tool("按客户名称列出全部报价单号。当用户需要某客户的单号清单时使用。")
    public String listOrderNosByCustomer(String customer) {
        List<String> dhs = calcService.listDhByKhname(customer);
        if (dhs.isEmpty()) return "客户 " + customer + " 暂无报价单";
        return "客户 " + customer + " 共 " + dhs.size() + " 单: " + String.join(", ", dhs);
    }

    @Tool("按报价单号(dh)查询并用后端公式引擎计算全部派生指标(日产量/织造成本/原料金额/前道合计/后道合计/净成本/税金/销售成本)。当用户要求计算、核算、验证某单的成本或报价时使用。")
    public String calculateByDh(String dh) {
        List<Map<String, Object>> rows = calcService.queryByDh(dh);
        if (rows.isEmpty()) {
            return "未找到报价单号: " + dh;
        }
        Map<String, BigDecimal> result = calcService.calculate(rows.get(0), null);
        StringBuilder sb = new StringBuilder();
        sb.append("报价单 ").append(dh).append(" 计算结果:\n");
        for (Map.Entry<String, BigDecimal> e : result.entrySet()) {
            sb.append(e.getKey()).append(" = ").append(e.getValue().toPlainString()).append("\n");
        }
        return sb.toString();
    }

    /** 只输出关键列，控制 token 量 */
    private String formatRows(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return "未查询到数据";
        String[] cols = {"dh", "khname", "huohao", "houhaocp", "chima", "zpl", "jcb", "yunfei",
                "xscb", "saleprice", "countprice", "hdprice", "sumprice", "flsum", "zzcb", "rcl"};
        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(rows.size()).append(" 条:\n");
        int n = 0;
        for (Map<String, Object> row : rows) {
            if (n++ >= 10) { sb.append("...(更多省略)\n"); break; }
            for (String c : cols) {
                Object v = row.get(c);
                if (v != null) sb.append(c).append("=").append(v).append(" ");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
