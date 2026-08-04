package com.imagemanager.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能关键词提取器
 * 
 * 针对纺织/服装/供应链行业的业务场景优化：
 * 1. 内置行业复合词典，保持"棉质面料"、"原料采购"等业务术语完整
 * 2. 正向最大匹配分词（FMM），避免把复合词拆碎
 * 3. 分层优先级：货号 > 行业复合词 > 行业单词 > 普通词
 * 4. 激进的去停用词，只保留有搜索价值的关键词
 * 
 * @author AI Assistant
 */
public class KeywordExtractor {
    
    private static final Logger log = LoggerFactory.getLogger(KeywordExtractor.class);
    
    // ==================== 行业复合词典 ====================
    // 这些词在分词时必须保持完整，不能拆开
    private static final Set<String> BUSINESS_COMPOUNDS = new LinkedHashSet<>(Arrays.asList(
        // 面料相关
        "棉质面料", "涤纶面料", "尼龙面料", "丝绸面料", "麻质面料", "混纺面料",
        "针织面料", "梭织面料", "提花面料", "印花面料", "染色面料", "色织面料",
        "复合面料", "防水面料", "阻燃面料", "防紫外线面料", "抗菌面料",
        "弹力面料", "磨毛面料", "摇粒绒", "珊瑚绒", "法兰绒", "雪纺",
        "欧根纱", "塔夫绸", "缎面", "牛仔布", "灯芯绒", "天鹅绒",
        "蕾丝面料", "网眼布", "牛津布", "帆布", "亚麻布", "棉麻布",
        "面料成分", "面料克重", "面料幅宽", "面料缩水率", "面料色牢度",
        "面料手感", "面料垂感", "面料透气性", "面料吸湿性",
        
        // 原料相关
        "原料采购", "原料入库", "原料成本", "原料用量", "原料单价",
        "原材料", "辅料采购", "辅料成本", "辅料用量",
        "纱线", "棉纱", "涤纱", "混纺纱", "色纱",
        "纤维", "天然纤维", "化学纤维", "再生纤维",
        
        // 供应链/生产
        "供应商", "供应商对比", "供应商报价", "供应商评估",
        "采购订单", "采购计划", "采购价格", "采购成本",
        "生产计划", "生产工艺", "生产成本", "生产效率",
        "质量检验", "质量检测", "质检报告", "合格率",
        "仓储管理", "库存管理", "出入库", "库存预警",
        "物流配送", "发货计划", "交期", "交货期",
        
        // 成本/报价
        "成本核算", "成本分析", "成本构成", "成本控制",
        "报价单", "报价明细", "销售报价", "采购报价",
        "加工费", "织造费", "染整费", "包装费", "运输费",
        "利润率", "毛利率", "净利率",
        "单件成本", "总成本", "材料成本", "人工成本",
        
        // 产品/款式
        "产品型号", "产品编码", "货号", "款号",
        "产品规格", "产品尺寸", "颜色", "尺码",
        "款式设计", "版型", "裁剪", "缝制",
        "成衣", "半成品", "样品", "样衣",
        
        // 工艺/技术
        "染色工艺", "印花工艺", "后整理", "预缩处理",
        "定型", "退浆", "漂白", "丝光",
        "经编", "纬编", "平纹", "斜纹", "缎纹",
        "数码印花", "丝网印花", "转移印花",
        
        // 检测/标准
        "色牢度测试", "缩水率测试", "强力测试", "起毛起球",
        "甲醛含量", "PH值", "偶氮染料",
        "国标", "行标", "企标", "AQL标准",
        
        // 系统/管理
        "ERP系统", "MES系统", "BOM表", "工艺单",
        "订单管理", "客户管理", "合同管理"
    ));
    
    // ==================== 行业单词词典 ====================
    // 单个行业术语，搜索价值高
    private static final Set<String> BUSINESS_TERMS = new LinkedHashSet<>(Arrays.asList(
        // 面料/材料
        "面料", "布料", "织物", "纺织", "纺织品",
        "棉", "涤纶", "尼龙", "氨纶", "腈纶", "维纶",
        "丝", "麻", "毛", "绒", "纱",
        "纤维", "纱线", "丝线", "线线",
        "里布", "衬布", "垫布", "标签",
        
        // 供应链
        "供应商", "采购商", "工厂", "车间", "仓库",
        "采购", "入库", "出库", "库存", "盘点",
        "报价", "成本", "价格", "单价", "总价",
        "订单", "合同", "交期", "发货", "物流",
        
        // 生产
        "生产", "加工", "制造", "织造", "染整",
        "裁剪", "缝制", "整烫", "包装",
        "工艺", "工序", "产线", "产能",
        "质检", "检验", "检测", "合格率",
        
        // 产品
        "产品", "款式", "型号", "规格", "货号",
        "颜色", "色号", "尺码", "尺寸",
        "样品", "样衣", "大货", "量产",
        
        // 业务动作
        "对比", "汇总", "统计", "分析",
        "最低", "最高", "平均", "合计",
        "趋势", "变化", "增长", "下降"
    ));
    
    // ==================== 停用词 ====================
    // 这些词完全没有搜索价值，必须移除
    private static final Set<String> STOP_WORDS = new LinkedHashSet<>(Arrays.asList(
        // 助词/虚词
        "的", "了", "是", "在", "有", "和", "与", "或", "不", "也", "都",
        "就", "要", "会", "能", "这", "那", "它", "他", "她", "我", "你",
        "吗", "呢", "吧", "啊", "呀", "哦", "嗯",
        
        // 疑问词
        "什么", "怎么", "如何", "为什么", "哪个", "哪些", "多少", "几个",
        "哪里", "谁", "哪", "吗",
        
        // 动词（无搜索价值）
        "请", "帮", "告诉", "查询", "查", "看", "给", "让", "把", "被",
        "从", "到", "对", "为", "以", "于", "可以", "应该", "需要",
        "想", "要", "得", "了", "过", "着",
        
        // 代词/指示词
        "这个", "那个", "这些", "那些", "自己", "别人",
        
        // 时间词（无搜索价值）
        "目前", "现在", "最新", "最近", "今天", "昨天", "明天",
        "上周", "下周", "上月", "下月", "今年", "去年", "明年",
        "之前", "之后", "以前", "以后", "当时", "已经", "将要",
        
        // 范围词（无搜索价值）
        "所有", "全部", "每个", "一切", "任何", "一些", "某些",
        
        // 操作词（无搜索价值）
        "比较", "分析", "统计", "列出", "展示", "显示", "计算", "得出",
        "帮我", "问下", "请问", "我想", "知道", "告诉", "说明",
        "介绍", "解释", "描述", "总结", "归纳",
        
        // 程度词
        "很", "非常", "特别", "比较", "稍微", "稍微", "一点", "一些",
        
        // 连接词
        "而且", "但是", "不过", "因为", "所以", "如果", "虽然", "然而",
        "另外", "同时", "此外", "还有", "以及",
        
        // 数量词
        "一个", "两个", "几个", "多个", "大量", "少量",
        
        // 英文停用词
        "the", "a", "an", "is", "are", "was", "were", "be", "been",
        "and", "or", "but", "not", "for", "with", "without",
        "this", "that", "these", "those", "it", "its"
    ));
    
    // ==================== 产品编码提取 ====================
    private static final Pattern PRODUCT_CODE_PATTERN = Pattern.compile(
        "(HT\\d+[-][A-Z]+|" +
        "[A-Za-z0-9]{4,})"
    );
    
    // 排除的常见英文缩写（不是货号）
    private static final Set<String> CODE_EXCLUSIONS = Set.of(
        "the", "and", "for", "mes", "erp", "bom", "kg", "smv", "aql",
        "pic", "ipc", "ipqc", "iqc", "html", "http", "https",
        "2026", "2025", "2024", "2023", "2022", "2021",
        "true", "false", "null", "none", "test", "demo",
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "jpg", "jpeg", "png", "gif", "svg", "bmp", "tiff"
    );
    
    /**
     * 提取产品编码
     * @return 第一个匹配的产品编码，无则返回 null
     */
    public static String extractProductCode(String query) {
        if (query == null || query.isBlank()) return null;
        
        Matcher matcher = PRODUCT_CODE_PATTERN.matcher(query);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (candidate.matches(".*[A-Za-z].*") && candidate.matches(".*[0-9].*") && candidate.length() >= 4) {
                String lower = candidate.toLowerCase();
                if (!CODE_EXCLUSIONS.contains(lower)) {
                    return candidate;
                }
            }
        }
        return null;
    }
    
    /**
     * 提取所有产品编码
     */
    public static List<String> extractAllProductCodes(String query) {
        if (query == null || query.isBlank()) return Collections.emptyList();
        
        List<String> codes = new ArrayList<>();
        Matcher matcher = PRODUCT_CODE_PATTERN.matcher(query);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (candidate.matches(".*[A-Za-z].*") && candidate.matches(".*[0-9].*") && candidate.length() >= 4) {
                String lower = candidate.toLowerCase();
                if (!CODE_EXCLUSIONS.contains(lower)) {
                    codes.add(candidate);
                }
            }
        }
        return codes;
    }
    
    /**
     * 智能关键词提取（核心方法）
     * 
     * 流程：
     * 1. 提取产品编码（最高优先级）
     * 2. 正向最大匹配分词（使用行业复合词典）
     * 3. 去停用词
     * 4. 按优先级排序：行业复合词 > 行业单词 > 其他有效词
     * 5. 限制数量（最多8个关键词，避免SQL过于复杂）
     * 
     * @param query 用户查询
     * @return 按优先级排序的关键词列表
     */
    public static List<String> extractKeywords(String query) {
        if (query == null || query.isBlank()) return Collections.emptyList();
        
        log.debug("[关键词提取] 原始查询: '{}'", query);
        
        // Step 1: 提取产品编码（最高优先级）
        List<String> productCodes = extractAllProductCodes(query);
        
        // Step 2: 预处理 - 移除产品编码、标点、多余空格
        String cleaned = query;
        for (String code : productCodes) {
            cleaned = cleaned.replace(code, " ");
        }
        cleaned = cleaned.replaceAll("[\\s,，、；;！!？?。.：:\"\"''（）()\\[\\]\\{\\}/\\-_+\\-=~`@#$%^&*<>《》|\\\\]+", " ")
                         .trim();
        
        // Step 3: 正向最大匹配分词
        List<String> words = forwardMaxMatch(cleaned);
        
        // Step 4: 分类 + 去停用词
        List<String> businessCompounds = new ArrayList<>();  // 行业复合词
        List<String> businessTerms = new ArrayList<>();      // 行业单词
        List<String> otherWords = new ArrayList<>();         // 其他有效词
        
        for (String word : words) {
            if (word.length() < 2) continue;  // 单字无搜索价值
            if (STOP_WORDS.contains(word)) continue;
            
            if (BUSINESS_COMPOUNDS.contains(word)) {
                businessCompounds.add(word);
            } else if (BUSINESS_TERMS.contains(word)) {
                businessTerms.add(word);
            } else {
                // 其他词：至少2个中文字符才有搜索价值
                if (word.matches("[\\u4e00-\\u9fa5]{2,}") || word.matches("[A-Za-z]{3,}")) {
                    otherWords.add(word);
                }
            }
        }
        
        // Step 5: 按优先级合并
        List<String> result = new ArrayList<>();
        result.addAll(productCodes);           // 优先级1: 产品编码
        result.addAll(businessCompounds);      // 优先级2: 行业复合词
        result.addAll(businessTerms);          // 优先级3: 行业单词
        result.addAll(otherWords);             // 优先级4: 其他有效词
        
        // Step 6: 去重（保持顺序）
        Set<String> seen = new LinkedHashSet<>();
        List<String> deduped = new ArrayList<>();
        for (String kw : result) {
            String lower = kw.toLowerCase();
            if (seen.add(lower)) {
                deduped.add(kw);
            }
        }
        
        // Step 7: 限制数量
        if (deduped.size() > 8) {
            deduped = deduped.subList(0, 8);
        }
        
        log.info("[关键词提取] query='{}' => 产品编码={}, 复合词={}, 行业词={}, 其他={}, 最终={}", 
                 query, productCodes, businessCompounds, businessTerms, otherWords, deduped);
        
        return deduped;
    }
    
    /**
     * 正向最大匹配分词（Forward Maximum Matching）
     * 
     * 使用行业复合词典进行分词，优先匹配长词：
     * "棉质面料的洗涤注意事项" 
     * → ["棉质面料", "的", "洗涤", "注意", "事项"]
     * 
     * 而不是简单按空格拆分
     */
    private static List<String> forwardMaxMatch(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) return result;
        
        // 最大词长（词典中最长的词）
        int maxLen = 6;
        
        int i = 0;
        while (i < text.length()) {
            boolean matched = false;
            
            // 从最长词开始尝试匹配
            for (int len = Math.min(maxLen, text.length() - i); len >= 2; len--) {
                String candidate = text.substring(i, i + len);
                if (BUSINESS_COMPOUNDS.contains(candidate) || BUSINESS_TERMS.contains(candidate)) {
                    result.add(candidate);
                    i += len;
                    matched = true;
                    break;
                }
            }
            
            if (!matched) {
                // 没有匹配到词典中的词
                // 尝试提取连续的中文/英文字符作为一个词
                StringBuilder sb = new StringBuilder();
                while (i < text.length()) {
                    char c = text.charAt(i);
                    if (Character.isLetterOrDigit(c)) {
                        sb.append(c);
                        i++;
                    } else {
                        break;
                    }
                }
                String word = sb.toString();
                if (!word.isEmpty()) {
                    result.add(word);
                } else {
                    // 跳过非字母数字字符
                    i++;
                }
            }
        }
        
        return result;
    }
    
    /**
     * 判断查询是否包含产品编码
     */
    public static boolean hasProductCode(String query) {
        return extractProductCode(query) != null;
    }
    
    /**
     * 判断查询是否主要是业务数据查询（而非文档内容查询）
     * 用于决定走供应链检索还是知识库检索
     */
    public static boolean isBusinessDataQuery(String query) {
        if (query == null || query.isBlank()) return false;
        
        // 包含产品编码 → 大概率是业务数据查询
        if (hasProductCode(query)) return true;
        
        // 包含强业务关键词
        Set<String> strongBusinessSignals = Set.of(
            "报价", "成本", "价格", "单价", "采购", "入库", "库存",
            "供应商", "订单", "交期", "发货", "生产计划",
            "原料", "辅料", "面料", "纱线", "纤维",
            "克重", "幅宽", "缩水率", "色牢度",
            "利润率", "毛利率", "产量", "产能"
        );
        
        List<String> keywords = extractKeywords(query);
        for (String kw : keywords) {
            if (strongBusinessSignals.contains(kw)) return true;
        }
        
        return false;
    }
}
