package com.imagemanager.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imagemanager.dto.MemorySearchResult;
import com.imagemanager.service.KnowledgeBaseService;
import com.imagemanager.service.MemoryService;
import com.imagemanager.service.SmartChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能对话服务实现 - 双库检索(知识库+记忆库) + MiniMax流式对话
 *
 * 检索流程:
 * 1. 记忆库检索: PostgreSQL向量搜索(MemoryService.search)
 * 2. 知识库检索: 同样使用PostgreSQL向量搜索(MemoryService.search，不限定domain)
 * 3. 合并去重结果作为上下文
 * 4. 调MiniMax API流式对话
 */
@Slf4j
@Service
public class SmartChatServiceImpl implements SmartChatService {

    @Autowired
    private MemoryService memoryService;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.minimax.api-key:}")
    private String minimaxApiKey;

    @Value("${app.minimax.base-url:https://api.minimaxi.com/anthropic/v1/messages}")
    private String minimaxBaseUrl;

    @Value("${app.minimax.model:MiniMax-M3}")
    private String minimaxModel;

    @Override
    public SseEmitter smartChat(String message, String userId, String company) {
        log.info("智能对话: message='{}', userId='{}', company='{}'", message, userId, company);
        SseEmitter emitter = new SseEmitter(600000L); // 10分钟超时

        new Thread(() -> {
            try {
                // 1. 加载历史对话（按userId+company绑定）
                List<Map<String, Object>> history = getChatHistory(userId, company);

                // 2. 双库检索
                // 2a. 记忆库检索(PostgreSQL向量)
                List<MemorySearchResult> memoryResults = Collections.emptyList();
                try {
                    memoryResults = memoryService.search(message, null, 0.2, 5, company, userId);
                    log.info("记忆库检索到 {} 条结果", memoryResults.size());
                } catch (Exception e) {
                    log.warn("记忆库检索异常: {}", e.getMessage());
                }

                // 2b. 知识库检索(Coze SDK via Next.js)
                List<Map<String, Object>> knowledgeResults = Collections.emptyList();
                try {
                    knowledgeResults = searchKnowledgeBase(message, userId, company);
                    log.info("知识库检索到 {} 条结果", knowledgeResults.size());
                } catch (Exception e) {
                    log.warn("知识库检索异常: {}", e.getMessage());
                }

                // 2c. 图片搜索(当用户意图涉及找图时)
                List<Map<String, Object>> imageResults = Collections.emptyList();
                if (isImageSearchIntent(message)) {
                    try {
                        imageResults = searchImages(message, userId);
                        log.info("图片搜索匹配到 {} 条结果", imageResults.size());
                    } catch (Exception e) {
                        log.warn("图片搜索异常: {}", e.getMessage());
                    }
                }

                // 2d. 供应链/工厂数据检索(当用户意图涉及报价、成本、原料、供应商等)
                List<Map<String, Object>> supplyChainResults = Collections.emptyList();
                if (isSupplyChainIntent(message)) {
                    try {
                        supplyChainResults = searchSupplyChain(message);
                        log.info("供应链数据检索到 {} 条结果", supplyChainResults.size());
                    } catch (Exception e) {
                        log.warn("供应链数据检索异常: {}", e.getMessage());
                    }
                }

                // 3. 发送来源信息
                List<Map<String, Object>> sources = new ArrayList<>();

                // 记忆库来源
                for (MemorySearchResult r : memoryResults) {
                    sources.add(Map.of(
                            "source", "memory",
                            "id", r.getId().toString(),
                            "title", r.getTitle() != null ? r.getTitle() : "",
                            "domain", r.getDomainName() != null ? r.getDomainName() : "",
                            "score", r.getScore() != null ? r.getScore() : 0
                    ));
                }

                // 知识库来源
                for (Map<String, Object> r : knowledgeResults) {
                    sources.add(Map.of(
                            "source", "knowledge",
                            "content", r.getOrDefault("content", "").toString(),
                            "score", r.getOrDefault("score", 0)
                    ));
                }

                // 供应链来源
                for (Map<String, Object> r : supplyChainResults) {
                    sources.add(Map.of(
                            "source", "supply_chain",
                            "type", r.getOrDefault("type", ""),
                            "summary", r.getOrDefault("summary", "").toString()
                    ));
                }

                emitter.send(SseEmitter.event().name("message").data(
                        objectMapper.writeValueAsString(Map.of("type", "sources", "sources", sources))
                ));

                // 3b. 发送图片结果(如果有)
                if (!imageResults.isEmpty()) {
                    emitter.send(SseEmitter.event().name("message").data(
                            objectMapper.writeValueAsString(Map.of("type", "images", "images", imageResults))
                    ));
                }

                // 4. 构建知识上下文
                StringBuilder knowledgeContext = new StringBuilder();

                if (!memoryResults.isEmpty()) {
                    knowledgeContext.append("## 记忆库相关知识卡片：\n");
                    for (int i = 0; i < memoryResults.size(); i++) {
                        MemorySearchResult r = memoryResults.get(i);
                        String content = r.getContent();
                        if (content != null && content.length() > 300) content = content.substring(0, 300) + "...";
                        knowledgeContext.append(String.format("### 卡片%d [%s] %s\n%s\n置信度: %s | 来源: %s\n\n",
                                i + 1, r.getDomainName(), r.getTitle(), content,
                                r.getConfidence(), r.getSource() != null ? r.getSource() : "未知"));
                    }
                }

                if (!knowledgeResults.isEmpty()) {
                    knowledgeContext.append("## 知识库相关文档片段：\n");
                    for (int i = 0; i < knowledgeResults.size(); i++) {
                        Map<String, Object> r = knowledgeResults.get(i);
                        double score = ((Number) r.getOrDefault("score", 0)).doubleValue();
                        String content = r.getOrDefault("content", "").toString();
                        if (content.length() > 300) content = content.substring(0, 300) + "...";
                        knowledgeContext.append(String.format("### 片段%d (相关度: %.1f%%)\n%s\n\n",
                                i + 1, score * 100, content));
                    }
                }

                if (!imageResults.isEmpty()) {
                    knowledgeContext.append("## 图片库搜索结果：\n");
                    for (int i = 0; i < imageResults.size(); i++) {
                        Map<String, Object> product = imageResults.get(i);
                        String productName = product.getOrDefault("productName", "").toString();
                        String albumName = product.getOrDefault("albumName", "").toString();
                        knowledgeContext.append(String.format("产品%d: %s (相册: %s)\n", i + 1, productName, albumName));

                        @SuppressWarnings("unchecked")
                        Map<String, Object> mainImage = (Map<String, Object>) product.get("mainImage");
                        if (mainImage != null) {
                            knowledgeContext.append(String.format("  [主图] %s (URL: %s)\n",
                                    mainImage.getOrDefault("title", ""), mainImage.getOrDefault("url", "")));
                        }

                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> detailImages = (List<Map<String, Object>>) product.get("detailImages");
                        if (detailImages != null && !detailImages.isEmpty()) {
                            knowledgeContext.append(String.format("  [详情图 %d张] ", detailImages.size()));
                            for (int j = 0; j < detailImages.size() && j < 5; j++) {
                                Map<String, Object> di = detailImages.get(j);
                                knowledgeContext.append(String.format("%s ", di.getOrDefault("title", "")));
                            }
                            knowledgeContext.append("\n");
                        }
                    }
                    knowledgeContext.append("\n用户请求查找图片，请基于以上图片列表组织回答，简要说明找到了哪些产品及其图片。\n");
                }

                // 供应链/工厂数据上下文
                if (!supplyChainResults.isEmpty()) {
                    knowledgeContext.append("## 供应链/工厂业务数据：\n");
                    for (Map<String, Object> r : supplyChainResults) {
                        String type = r.getOrDefault("type", "").toString();
                        String summary = r.getOrDefault("summary", "").toString();
                        knowledgeContext.append(String.format("### [%s] %s\n", type, summary));
                        @SuppressWarnings("unchecked")
                        Map<String, Object> data = (Map<String, Object>) r.get("data");
                        if (data != null) {
                            for (Map.Entry<String, Object> entry : data.entrySet()) {
                                Object val = entry.getValue();
                                if (val != null) {
                                    String valStr = val.toString();
                                    if (valStr.length() > 200) valStr = valStr.substring(0, 200) + "...";
                                    knowledgeContext.append(String.format("  %s: %s\n", entry.getKey(), valStr));
                                }
                            }
                        }
                        knowledgeContext.append("\n");
                    }
                    knowledgeContext.append("用户询问供应链/工厂相关问题，请基于以上业务数据回答，包括报价、成本、原料、供应商等具体数据。\n");
                }

                // 5. 构建messages(含历史上下文)
                List<Map<String, Object>> messages = new ArrayList<>();

                // System prompt: 定义AI角色和行为
                messages.add(Map.of("role", "system", "content",
                        "你是盈云产品智能中台的AI助手。你拥有企业知识库（记忆库和知识库）和供应链业务数据的访问权限。" +
                        "回答问题时优先基于检索到的知识库内容和业务数据，并标注引用来源。" +
                        "当涉及产品报价、成本核算、原料采购、供应商对比、生产计划等问题时，务必基于检索到的供应链业务数据精确回答，包括具体数字。" +
                        "如果知识库中没有相关信息，你可以基于自身知识回答，但要说明信息来源。" +
                        "保持专业、简洁、有帮助的回答风格。"));

                // 加入历史对话(最近10轮)
                int startIdx = Math.max(0, history.size() - 5);
                for (int i = startIdx; i < history.size(); i++) {
                    messages.add(history.get(i));
                }

                // 当前用户消息(带知识上下文)
                String userContent = message;
                if (!knowledgeContext.isEmpty()) {
                    userContent = knowledgeContext.toString() + "\n---\n用户问题: " + message +
                            "\n\n请基于以上知识和业务数据回答用户问题，并标注引用来源和出处(记忆库/知识库/供应链数据)。";
                } else {
                    userContent = "用户问题: " + message +
                            "\n\n(知识库中未检索到相关内容，请基于自身知识回答。)";
                }
                messages.add(Map.of("role", "user", "content", userContent));

                // 6. 保存用户消息
                saveChatMessage(userId, userId, "user", message, company);

                // 7. 流式调用MiniMax
                StringBuilder fullResponse = new StringBuilder();
                streamChat(emitter, messages, fullResponse);

                // 8. 保存AI回复
                saveChatMessage(userId, userId, "assistant", fullResponse.toString(), company);

                emitter.complete();
            } catch (Exception e) {
                log.error("智能对话失败: {}", e.getMessage());
                try {
                    emitter.send(SseEmitter.event().name("message").data(
                            objectMapper.writeValueAsString(Map.of("type", "error", "content", "AI对话失败: " + e.getMessage()))
                    ));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    @Override
    public List<Map<String, Object>> getChatHistory(String userId, String company) {
        // 按userId+company查询最近10轮对话（20条消息：10个user + 10个assistant）
        String sql = "SELECT role, content, created_at FROM smart_chat_history " +
                "WHERE user_id = ? AND (company = ? OR company IS NULL) " +
                "ORDER BY created_at DESC LIMIT 20";
        List<Map<String, Object>> results = jdbcTemplate.query(sql,
                (rs, rowNum) -> {
                    Map<String, Object> msg = new LinkedHashMap<>();
                    msg.put("role", rs.getString("role"));
                    msg.put("content", rs.getString("content"));
                    msg.put("createdAt", rs.getTimestamp("created_at").toLocalDateTime().toString());
                    return msg;
                },
                userId, company
        );
        // 反转回时间正序
        Collections.reverse(results);
        return results;
    }

    @Override
    @Transactional
    public void clearChatHistory(String userId, String company) {
        jdbcTemplate.update(
                "DELETE FROM smart_chat_history WHERE user_id = ? AND (company = ? OR company IS NULL)",
                userId, company
        );
    }

    // ========== 私有方法 ==========

    /**
     * 判断用户意图是否为图片搜索
     */
    private boolean isImageSearchIntent(String message) {
        String lower = message.toLowerCase();
        // 精确匹配：仅当用户明确表达查找图片意图时才触发
        String[] strongPatterns = {
            "找图", "搜图", "查图", "看图",
            "找图片", "搜图片", "查图片",
            "图片搜索", "图片查询",
            "找照片", "搜照片",
            "主图", "详情图", "效果图",
            "产品图", "商品图",
            "图片库", "图片列表"
        };
        for (String kw : strongPatterns) {
            if (lower.contains(kw)) return true;
        }
        // 弱匹配：需要同时包含动作词+图片词
        String[] actionWords = {"找", "搜", "查", "看", "推荐", "展示", "显示"};
        String[] imageWords = {"图片", "照片", "相册"};
        boolean hasAction = false;
        boolean hasImage = false;
        for (String a : actionWords) { if (lower.contains(a)) { hasAction = true; break; } }
        for (String i : imageWords) { if (lower.contains(i)) { hasImage = true; break; } }
        return hasAction && hasImage;
    }

    /**
     * 图片搜索 - 按标题/描述/标签模糊匹配，按产品分组返回(主图+详情图)
     */
    private List<Map<String, Object>> searchImages(String query, String userId) {
        try {
            // 提取查询关键词(简单分词，取2-6字的关键词)
            String[] terms = query.split("[\\s,，。！？?]+");
            List<String> keywords = new ArrayList<>();
            for (String term : terms) {
                String t = term.trim();
                if (t.length() >= 2 && t.length() <= 10 && !isStopWord(t)) {
                    keywords.add(t);
                }
            }
            if (keywords.isEmpty()) {
                keywords.add(query.trim());
            }

            // 第一步: 先搜索匹配的图片(最多50张，确保每个产品都有图)
            // 只用 images 表现有字段搜索，避免依赖可能不存在的 image_tags/image_ai_tags 表
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT id, title, url, thumbnail_url, is_main_image, file_type, ");
            sql.append("width, height, product_id, album_name, created_at ");
            sql.append("FROM images WHERE deleted = false AND user_id = ? ");
            sql.append("AND (");
            for (int i = 0; i < keywords.size(); i++) {
                if (i > 0) sql.append(" OR ");
                sql.append("(COALESCE(title, '') ILIKE ? OR COALESCE(description, '') ILIKE ? OR COALESCE(album_name, '') ILIKE ?)");
            }
            sql.append(") ");
            sql.append("ORDER BY is_main_image DESC, created_at DESC ");
            sql.append("LIMIT 50");

            List<Object> params = new ArrayList<>();
            params.add(userId);
            for (String kw : keywords) {
                String pattern = "%" + kw + "%";
                params.add(pattern);
                params.add(pattern);
                params.add(pattern);
            }

            log.info("图片搜索SQL: {}", sql.toString());
            log.info("图片搜索参数: userId={}, keywords={}", userId, keywords);

            List<Map<String, Object>> rawImages = jdbcTemplate.query(sql.toString(),
                    (rs, rowNum) -> {
                        Map<String, Object> img = new LinkedHashMap<>();
                        img.put("id", rs.getString("id"));
                        img.put("title", rs.getString("title"));
                        img.put("url", rs.getString("url"));
                        img.put("thumbnailUrl", rs.getString("thumbnail_url"));
                        img.put("isMainImage", rs.getBoolean("is_main_image"));
                        img.put("fileType", rs.getString("file_type"));
                        img.put("width", rs.getInt("width"));
                        img.put("height", rs.getInt("height"));
                        img.put("productId", rs.getString("product_id"));
                        img.put("albumName", rs.getString("album_name"));
                        img.put("createdAt", rs.getTimestamp("created_at") != null
                                ? rs.getTimestamp("created_at").toLocalDateTime().toString() : null);
                        return img;
                    },
                    params.toArray()
            );

            log.info("图片搜索关键词匹配到 {} 条原始记录", rawImages.size());

            // 如果关键词搜索不到，兜底返回用户最新的20张图片
            if (rawImages.isEmpty()) {
                log.info("关键词未匹配到图片，兜底返回用户最新图片");
                String fallbackSql = "SELECT id, title, url, thumbnail_url, is_main_image, file_type, " +
                        "width, height, product_id, album_name, created_at " +
                        "FROM images WHERE deleted = false AND user_id = ? " +
                        "ORDER BY created_at DESC LIMIT 20";
                rawImages = jdbcTemplate.query(fallbackSql,
                        (rs, rowNum) -> {
                            Map<String, Object> img = new LinkedHashMap<>();
                            img.put("id", rs.getString("id"));
                            img.put("title", rs.getString("title"));
                            img.put("url", rs.getString("url"));
                            img.put("thumbnailUrl", rs.getString("thumbnail_url"));
                            img.put("isMainImage", rs.getBoolean("is_main_image"));
                            img.put("fileType", rs.getString("file_type"));
                            img.put("width", rs.getInt("width"));
                            img.put("height", rs.getInt("height"));
                            img.put("productId", rs.getString("product_id"));
                            img.put("albumName", rs.getString("album_name"));
                            img.put("createdAt", rs.getTimestamp("created_at") != null
                                    ? rs.getTimestamp("created_at").toLocalDateTime().toString() : null);
                            return img;
                        },
                        userId
                );
                log.info("兜底查询返回 {} 条图片", rawImages.size());
            }

            // 第二步: 按 product_id 分组，每个产品保留主图+详情图
            Map<String, List<Map<String, Object>>> productGroups = new LinkedHashMap<>();
            for (Map<String, Object> img : rawImages) {
                String pid = img.getOrDefault("productId", "").toString();
                if (pid == null || pid.isEmpty()) {
                    pid = "no_product_" + img.get("id");
                }
                productGroups.computeIfAbsent(pid, k -> new ArrayList<>()).add(img);
            }

            // 第三步: 构建按产品分组的结果(最多10个产品)
            List<Map<String, Object>> products = new ArrayList<>();
            int productCount = 0;
            for (Map.Entry<String, List<Map<String, Object>>> entry : productGroups.entrySet()) {
                if (productCount >= 10) break;
                List<Map<String, Object>> imgs = entry.getValue();
                if (imgs.isEmpty()) continue;

                Map<String, Object> mainImage = null;
                List<Map<String, Object>> detailImages = new ArrayList<>();
                for (Map<String, Object> img : imgs) {
                    if (Boolean.TRUE.equals(img.get("isMainImage"))) {
                        mainImage = img;
                    } else {
                        detailImages.add(img);
                    }
                }
                // 如果没有主图，用第一张作为主图
                if (mainImage == null && !detailImages.isEmpty()) {
                    mainImage = detailImages.remove(0);
                }

                Map<String, Object> product = new LinkedHashMap<>();
                product.put("productId", entry.getKey());
                product.put("productName", mainImage != null ? mainImage.getOrDefault("title", "") : "");
                product.put("mainImage", mainImage);
                product.put("detailImages", detailImages);
                product.put("albumName", mainImage != null ? mainImage.getOrDefault("albumName", "") : "");
                products.add(product);
                productCount++;
            }

            log.info("图片搜索最终返回 {} 个产品", products.size());
            return products;
        } catch (Exception e) {
            log.error("图片搜索失败", e);
            return Collections.emptyList();
        }
    }

    private boolean isStopWord(String word) {
        String[] stops = {"一下", "一下", "什么", "怎么", "这个", "那个", "可以", "帮我", "请问"};
        for (String s : stops) {
            if (word.equals(s)) return true;
        }
        return false;
    }

    /**
     * 知识库检索 - 查询知识库独立的向量表(knowledge_embeddings, source_type='KNOWLEDGE_BASE')
     */
    private List<Map<String, Object>> searchKnowledgeBase(String query, String userId, String company) {
        try {
            List<MemorySearchResult> allResults = knowledgeBaseService.search(query, 0.15, 8, company, userId);

            List<Map<String, Object>> results = new ArrayList<>();
            for (MemorySearchResult r : allResults) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("content", r.getContent() != null ? r.getContent() : "");
                item.put("score", r.getScore() != null ? r.getScore() : 0);
                item.put("cardId", r.getId().toString());
                item.put("title", r.getTitle() != null ? r.getTitle() : "");
                item.put("domain", r.getDomainName() != null ? r.getDomainName() : "知识库");
                item.put("source", "knowledge_base");
                results.add(item);
            }
            return results;
        } catch (Exception e) {
            log.warn("知识库检索异常: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ========== 供应链/工厂数据检索 ==========

    /**
     * 判断用户意图是否涉及供应链/工厂数据
     */
    private boolean isSupplyChainIntent(String message) {
        String lower = message.toLowerCase();
        // 强模式：直接涉及报价/成本/原料/供应商等
        String[] patterns = {
            "报价", "成本", "原料", "供应商", "采购", "单价", "利润",
            "纱线", "克重", "织造", "缝头", "染色", "定型", "包装",
            "入库", "出库", "生产计划", "辅料", "日产量", "正品率",
            "费率", "机台", "产量", "交期", "工艺", "下机",
            "袜", "内衣", "无缝", "针织",
            "多少钱", "价格", "费用", "花多少", "最便宜", "最低价",
            "对比", "比较价格", "供应商对比", "节省", "成本优化",
            "报价单", "成本核算", "成本分析", "智能报价"
        };
        for (String kw : patterns) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    /**
     * 供应链数据检索 - 根据用户意图查询相关业务数据
     */
    private List<Map<String, Object>> searchSupplyChain(String query) {
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            // 提取产品编码关键词
            String productCode = extractProductCode(query);

            // 1. 产品报价查询
            if (productCode != null) {
                searchQuotationByProductCode(productCode, results);
            } else {
                // 模糊搜索报价单
                searchQuotationByKeyword(query, results);
            }

            // 2. 原料采购价格查询
            searchRawMaterialPurchase(query, results);

            // 3. 原料入库信息
            searchRawMaterialWarehouse(query, results);

            // 4. 生产计划查询
            if (productCode != null) {
                searchProductionPlan(productCode, results);
            }

            // 5. 辅料采购查询
            searchAccessoryPurchase(query, results);

            // 6. 如果用户问的是供应商对比，额外查询
            if (query.contains("对比") || query.contains("比较") || query.contains("最便宜") || query.contains("最低价")) {
                searchSupplierComparison(query, results);
            }

        } catch (Exception e) {
            log.error("供应链数据检索失败", e);
        }
        return results;
    }

    /**
     * 从用户消息中提取产品编码
     */
    private String extractProductCode(String query) {
        // 匹配常见产品编码格式: HT01-S, HT01-M, HT01-L 等
        Pattern pattern = Pattern.compile("\\b(HT\\d+[-][A-Z]+|[A-Z]{2}\\d+[A-Z]?)\\b");
        Matcher matcher = pattern.matcher(query);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 按产品编码查询报价单
     */
    private void searchQuotationByProductCode(String productCode, List<Map<String, Object>> results) {
        try {
            String sql = "SELECT id, product_code, production_code, document_no, period, customer, salesperson, " +
                "product_category, approval_status, sales_type, " +
                "raw_material_name1, material_usage1, material_unit_price1, " +
                "raw_material_name2, material_usage2, material_unit_price2, " +
                "raw_material_name3, material_usage3, material_unit_price3, " +
                "raw_material_name4, material_usage4, material_unit_price4, " +
                "raw_material_name5, material_usage5, material_unit_price5, " +
                "raw_material_name6, material_usage6, material_unit_price6, " +
                "accessory_name, accessory_price, " +
                "weaving_seconds, daily_output, equipment_daily_cost, weaving_cost, " +
                "yield_rate, sewing_weight, sewing_cost, " +
                "dyeing_unit_price, dyeing_cost, setting_cost, packaging_cost, " +
                "manufacturing_total, net_cost, sales_cost, " +
                "machine_hourly_rate, single_machine_output_hourly " +
                "FROM product_quotation WHERE product_code = ? LIMIT 5";
            List<Map<String, Object>> rows = jdbcTemplate.query(sql,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("productCode", rs.getString("product_code"));
                    row.put("productionCode", rs.getString("production_code"));
                    row.put("customer", rs.getString("customer"));
                    row.put("salesperson", rs.getString("salesperson"));
                    row.put("productCategory", rs.getString("product_category"));
                    row.put("approvalStatus", rs.getString("approval_status"));
                    row.put("salesType", rs.getString("sales_type"));
                    // 原料明细
                    row.put("rawMaterial1", rs.getString("raw_material_name1") + " 用量:" + rs.getBigDecimal("material_usage1") + " 单价:" + rs.getBigDecimal("material_unit_price1"));
                    row.put("rawMaterial2", rs.getString("raw_material_name2") + " 用量:" + rs.getBigDecimal("material_usage2") + " 单价:" + rs.getBigDecimal("material_unit_price2"));
                    row.put("rawMaterial3", rs.getString("raw_material_name3") + " 用量:" + rs.getBigDecimal("material_usage3") + " 单价:" + rs.getBigDecimal("material_unit_price3"));
                    row.put("rawMaterial4", rs.getString("raw_material_name4") + " 用量:" + rs.getBigDecimal("material_usage4") + " 单价:" + rs.getBigDecimal("material_unit_price4"));
                    row.put("rawMaterial5", rs.getString("raw_material_name5") + " 用量:" + rs.getBigDecimal("material_usage5") + " 单价:" + rs.getBigDecimal("material_unit_price5"));
                    row.put("rawMaterial6", rs.getString("raw_material_name6") + " 用量:" + rs.getBigDecimal("material_usage6") + " 单价:" + rs.getBigDecimal("material_unit_price6"));
                    row.put("accessoryName", rs.getString("accessory_name"));
                    row.put("accessoryPrice", rs.getBigDecimal("accessory_price"));
                    // 制造成本
                    row.put("weavingCost", rs.getBigDecimal("weaving_cost"));
                    row.put("yieldRate", rs.getBigDecimal("yield_rate"));
                    row.put("dyeingCost", rs.getBigDecimal("dyeing_cost"));
                    row.put("manufacturingTotal", rs.getBigDecimal("manufacturing_total"));
                    row.put("netCost", rs.getBigDecimal("net_cost"));
                    row.put("salesCost", rs.getBigDecimal("sales_cost"));
                    row.put("machineHourlyRate", rs.getBigDecimal("machine_hourly_rate"));
                    row.put("singleMachineOutputHourly", rs.getBigDecimal("single_machine_output_hourly"));
                    return row;
                }, productCode);
            for (Map<String, Object> row : rows) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("type", "产品报价");
                result.put("summary", "产品编码: " + productCode + " | 客户: " + row.get("customer") +
                    " | 净成本: " + row.get("netCost") + " | 销售成本: " + row.get("salesCost"));
                result.put("data", row);
                results.add(result);
            }
        } catch (Exception e) {
            log.warn("查询产品报价失败: {}", e.getMessage());
        }
    }

    /**
     * 按关键词模糊搜索报价单
     */
    private void searchQuotationByKeyword(String query, List<Map<String, Object>> results) {
        try {
            // 提取关键词
            String[] terms = query.split("[\\s,，。！？?]+");
            List<String> keywords = new ArrayList<>();
            for (String term : terms) {
                String t = term.trim();
                if (t.length() >= 2 && t.length() <= 10 && !isStopWord(t)) {
                    keywords.add(t);
                }
            }
            if (keywords.isEmpty()) return;

            StringBuilder sql = new StringBuilder();
            sql.append("SELECT product_code, customer, salesperson, product_category, net_cost, sales_cost, " +
                "manufacturing_total, yield_rate FROM product_quotation WHERE ");
            List<Object> params = new ArrayList<>();
            for (int i = 0; i < keywords.size(); i++) {
                if (i > 0) sql.append(" OR ");
                sql.append("(COALESCE(product_code, '') ILIKE ? OR COALESCE(customer, '') ILIKE ? " +
                    "OR COALESCE(salesperson, '') ILIKE ? OR COALESCE(product_category, '') ILIKE ? " +
                    "OR COALESCE(raw_material_name1, '') ILIKE ? OR COALESCE(raw_material_name2, '') ILIKE ? " +
                    "OR COALESCE(raw_material_name3, '') ILIKE ? OR COALESCE(raw_material_name4, '') ILIKE ? " +
                    "OR COALESCE(raw_material_name5, '') ILIKE ? OR COALESCE(raw_material_name6, '') ILIKE ?)");
                String pattern = "%" + keywords.get(i) + "%";
                for (int j = 0; j < 10; j++) params.add(pattern);
            }
            sql.append(" LIMIT 10");

            List<Map<String, Object>> rows = jdbcTemplate.query(sql.toString(),
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("productCode", rs.getString("product_code"));
                    row.put("customer", rs.getString("customer"));
                    row.put("salesperson", rs.getString("salesperson"));
                    row.put("productCategory", rs.getString("product_category"));
                    row.put("netCost", rs.getBigDecimal("net_cost"));
                    row.put("salesCost", rs.getBigDecimal("sales_cost"));
                    row.put("manufacturingTotal", rs.getBigDecimal("manufacturing_total"));
                    row.put("yieldRate", rs.getBigDecimal("yield_rate"));
                    return row;
                }, params.toArray());

            for (Map<String, Object> row : rows) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("type", "产品报价");
                result.put("summary", "产品: " + row.get("productCode") + " | 客户: " + row.get("customer") +
                    " | 净成本: " + row.get("netCost"));
                result.put("data", row);
                results.add(result);
            }
        } catch (Exception e) {
            log.warn("模糊搜索报价单失败: {}", e.getMessage());
        }
    }

    /**
     * 查询原料采购价格
     */
    private void searchRawMaterialPurchase(String query, List<Map<String, Object>> results) {
        try {
            String[] terms = query.split("[\\s,，。！？?]+");
            List<String> keywords = new ArrayList<>();
            for (String term : terms) {
                String t = term.trim();
                if (t.length() >= 2 && t.length() <= 20 && !isStopWord(t)) {
                    keywords.add(t);
                }
            }
            if (keywords.isEmpty()) return;

            StringBuilder sql = new StringBuilder();
            sql.append("SELECT material_code, unit, supplier, batch_no, unit_price FROM raw_material_purchase WHERE ");
            List<Object> params = new ArrayList<>();
            for (int i = 0; i < keywords.size(); i++) {
                if (i > 0) sql.append(" OR ");
                sql.append("(COALESCE(material_code, '') ILIKE ? OR COALESCE(supplier, '') ILIKE ?)");
                String pattern = "%" + keywords.get(i) + "%";
                params.add(pattern);
                params.add(pattern);
            }
            sql.append(" LIMIT 20");

            List<Map<String, Object>> rows = jdbcTemplate.query(sql.toString(),
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("materialCode", rs.getString("material_code"));
                    row.put("unit", rs.getString("unit"));
                    row.put("supplier", rs.getString("supplier"));
                    row.put("batchNo", rs.getString("batch_no"));
                    row.put("unitPrice", rs.getBigDecimal("unit_price"));
                    return row;
                }, params.toArray());

            if (!rows.isEmpty()) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("type", "原料采购");
                result.put("summary", "找到 " + rows.size() + " 条原料采购记录");
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("count", rows.size());
                data.put("items", rows);
                result.put("data", data);
                results.add(result);
            }
        } catch (Exception e) {
            log.warn("查询原料采购失败: {}", e.getMessage());
        }
    }

    /**
     * 查询原料入库信息
     */
    private void searchRawMaterialWarehouse(String query, List<Map<String, Object>> results) {
        try {
            String[] terms = query.split("[\\s,，。！？?]+");
            List<String> keywords = new ArrayList<>();
            for (String term : terms) {
                String t = term.trim();
                if (t.length() >= 2 && t.length() <= 20 && !isStopWord(t)) {
                    keywords.add(t);
                }
            }
            if (keywords.isEmpty()) return;

            StringBuilder sql = new StringBuilder();
            sql.append("SELECT product_code, color, batch_no, unit, unit_price FROM raw_material_warehouse WHERE ");
            List<Object> params = new ArrayList<>();
            for (int i = 0; i < keywords.size(); i++) {
                if (i > 0) sql.append(" OR ");
                sql.append("(COALESCE(product_code, '') ILIKE ? OR COALESCE(batch_no, '') ILIKE ?)");
                String pattern = "%" + keywords.get(i) + "%";
                params.add(pattern);
                params.add(pattern);
            }
            sql.append(" LIMIT 20");

            List<Map<String, Object>> rows = jdbcTemplate.query(sql.toString(),
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("productCode", rs.getString("product_code"));
                    row.put("color", rs.getString("color"));
                    row.put("batchNo", rs.getString("batch_no"));
                    row.put("unit", rs.getString("unit"));
                    row.put("unitPrice", rs.getBigDecimal("unit_price"));
                    return row;
                }, params.toArray());

            if (!rows.isEmpty()) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("type", "原料入库");
                result.put("summary", "找到 " + rows.size() + " 条原料入库记录");
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("count", rows.size());
                data.put("items", rows);
                result.put("data", data);
                results.add(result);
            }
        } catch (Exception e) {
            log.warn("查询原料入库失败: {}", e.getMessage());
        }
    }

    /**
     * 查询生产计划
     */
    private void searchProductionPlan(String productCode, List<Map<String, Object>> results) {
        try {
            String sql = "SELECT semi_product_code, product_code, sewing_weight, machine_type, " +
                "needle_count, seconds, machine_count, single_machine_output " +
                "FROM production_plan WHERE product_code = ? LIMIT 5";
            List<Map<String, Object>> rows = jdbcTemplate.query(sql,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("semiProductCode", rs.getString("semi_product_code"));
                    row.put("productCode", rs.getString("product_code"));
                    row.put("sewingWeight", rs.getBigDecimal("sewing_weight"));
                    row.put("machineType", rs.getString("machine_type"));
                    row.put("needleCount", rs.getString("needle_count"));
                    row.put("seconds", rs.getBigDecimal("seconds"));
                    row.put("machineCount", rs.getInt("machine_count"));
                    row.put("singleMachineOutput", rs.getBigDecimal("single_machine_output"));
                    return row;
                }, productCode);
            if (!rows.isEmpty()) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("type", "生产计划");
                result.put("summary", "产品 " + productCode + " 的生产计划");
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("items", rows);
                result.put("data", data);
                results.add(result);
            }
        } catch (Exception e) {
            log.warn("查询生产计划失败: {}", e.getMessage());
        }
    }

    /**
     * 查询辅料采购
     */
    private void searchAccessoryPurchase(String query, List<Map<String, Object>> results) {
        try {
            String[] terms = query.split("[\\s,，。！？?]+");
            List<String> keywords = new ArrayList<>();
            for (String term : terms) {
                String t = term.trim();
                if (t.length() >= 2 && t.length() <= 20 && !isStopWord(t)) {
                    keywords.add(t);
                }
            }
            if (keywords.isEmpty()) return;

            StringBuilder sql = new StringBuilder();
            sql.append("SELECT accessory_name, accessory_category, unit, supplier, accessory_unit_price " +
                "FROM accessory_purchase WHERE ");
            List<Object> params = new ArrayList<>();
            for (int i = 0; i < keywords.size(); i++) {
                if (i > 0) sql.append(" OR ");
                sql.append("(COALESCE(accessory_name, '') ILIKE ? OR COALESCE(accessory_category, '') ILIKE ? " +
                    "OR COALESCE(supplier, '') ILIKE ?)");
                String pattern = "%" + keywords.get(i) + "%";
                params.add(pattern);
                params.add(pattern);
                params.add(pattern);
            }
            sql.append(" LIMIT 20");

            List<Map<String, Object>> rows = jdbcTemplate.query(sql.toString(),
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("accessoryName", rs.getString("accessory_name"));
                    row.put("accessoryCategory", rs.getString("accessory_category"));
                    row.put("unit", rs.getString("unit"));
                    row.put("supplier", rs.getString("supplier"));
                    row.put("unitPrice", rs.getBigDecimal("accessory_unit_price"));
                    return row;
                }, params.toArray());

            if (!rows.isEmpty()) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("type", "辅料采购");
                result.put("summary", "找到 " + rows.size() + " 条辅料采购记录");
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("count", rows.size());
                data.put("items", rows);
                result.put("data", data);
                results.add(result);
            }
        } catch (Exception e) {
            log.warn("查询辅料采购失败: {}", e.getMessage());
        }
    }

    /**
     * 供应商对比 - 按原料编码汇总各供应商报价
     */
    private void searchSupplierComparison(String query, List<Map<String, Object>> results) {
        try {
            // 按原料编码汇总供应商报价，找最低价
            String sql = "SELECT material_code, " +
                "COUNT(*) as supplier_count, " +
                "MIN(unit_price) as min_price, " +
                "MAX(unit_price) as max_price, " +
                "AVG(unit_price) as avg_price " +
                "FROM raw_material_purchase " +
                "WHERE material_code IS NOT NULL " +
                "GROUP BY material_code " +
                "ORDER BY material_code LIMIT 20";
            List<Map<String, Object>> rows = jdbcTemplate.query(sql,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("materialCode", rs.getString("material_code"));
                    row.put("supplierCount", rs.getInt("supplier_count"));
                    row.put("minPrice", rs.getBigDecimal("min_price"));
                    row.put("maxPrice", rs.getBigDecimal("max_price"));
                    row.put("avgPrice", rs.getBigDecimal("avg_price"));
                    // 计算节省比例
                    BigDecimal maxP = rs.getBigDecimal("max_price");
                    BigDecimal minP = rs.getBigDecimal("min_price");
                    if (maxP != null && minP != null && maxP.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal saving = maxP.subtract(minP)
                            .divide(maxP, 4, BigDecimal.ROUND_HALF_UP)
                            .multiply(new BigDecimal("100"));
                        row.put("savingPercent", saving + "%");
                    }
                    return row;
                });

            if (!rows.isEmpty()) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("type", "供应商对比");
                result.put("summary", "共 " + rows.size() + " 种原料有多个供应商报价");
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("items", rows);
                result.put("data", data);
                results.add(result);
            }
        } catch (Exception e) {
            log.warn("供应商对比查询失败: {}", e.getMessage());
        }
    }

    /**
     * 加载对话历史
     */

    /**
     * 保存对话消息（按userId+company绑定，session_id存储为基于userId生成的确定性UUID）
     */
    private void saveChatMessage(String userId, String _unused, String role, String content, String company) {
        try {
            // 用 userId 生成确定性 UUID 作为 session_id（同一用户始终相同）
            String sessionId = UUID.nameUUIDFromBytes(("chat-" + userId).getBytes()).toString();
            jdbcTemplate.update(
                    "INSERT INTO smart_chat_history (id, session_id, role, content, user_id, company, created_at) " +
                            "VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, NOW())",
                    sessionId, role, content, userId, company
            );
        } catch (Exception e) {
            log.warn("保存对话消息失败: {}", e.getMessage());
        }
    }

    /**
     * 流式调用MiniMax API (Anthropic兼容接口)
     */
    private void streamChat(SseEmitter emitter, List<Map<String, Object>> messages,
                            StringBuilder fullResponse) {
        try {
            String apiKey = minimaxApiKey;
            if (apiKey == null || apiKey.isEmpty()) {
                apiKey = System.getenv("MINIMAX_API_KEY");
            }
            if (apiKey == null || apiKey.isEmpty()) {
                throw new RuntimeException("未配置MiniMax API密钥, 请设置环境变量 MINIMAX_API_KEY");
            }

            Map<String, Object> body = new HashMap<>();
            body.put("model", minimaxModel);
            body.put("max_tokens", 8192);
            body.put("stream", true);
            body.put("temperature", 0.7);
            body.put("system", "你是盈云产品智能中台的AI助手，专注于供应链、工厂管理、产品知识、报价和成本核算领域。" +
                    "请基于提供的记忆库知识卡片、知识库文档片段和供应链业务数据回答用户问题。" +
                    "回答时标注引用来源(记忆库/知识库/供应链数据)。" +
                    "当涉及报价、成本、原料、供应商等数据时，务必引用具体的数字和计算过程。" +
                    "如果参考资料中没有相关信息，请明确说明，不要编造。" +
                    "回答使用中文。保持对话连贯性，参考上下文历史。" +
                    "重要：请确保每句话都完整说完，不要在中途停止或截断。即使回答较长，也要把所有内容完整输出到自然结束。");
            body.put("messages", messages);

            String endpointUrl = minimaxBaseUrl.endsWith("/") ? minimaxBaseUrl.substring(0, minimaxBaseUrl.length() - 1) : minimaxBaseUrl;
            if (!endpointUrl.endsWith("/messages")) {
                endpointUrl = endpointUrl + "/anthropic/v1/messages";
            }
            HttpURLConnection conn = (HttpURLConnection) URI.create(endpointUrl).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(60000);
            conn.setReadTimeout(600000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                BufferedReader errorReader = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
                StringBuilder errorBody = new StringBuilder();
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorBody.append(line);
                }
                errorReader.close();
                throw new RuntimeException("MiniMax API返回错误 " + responseCode + ": " + errorBody);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        String data = line.substring(5).trim();
                        if (data.isEmpty()) continue;

                        try {
                            JsonNode node = objectMapper.readTree(data);
                            String eventType = node.path("type").asText("");

                            switch (eventType) {
                                case "content_block_start":
                                    JsonNode cbStart = node.path("content_block");
                                    String cbType = cbStart.path("type").asText("");
                                    log.debug("content_block_start 类型: {}", cbType);
                                    if ("text".equals(cbType)) {
                                        String text = cbStart.path("text").asText("");
                                        if (!text.isEmpty()) {
                                            fullResponse.append(text);
                                            emitter.send(SseEmitter.event().name("message").data(
                                                    objectMapper.writeValueAsString(Map.of("type", "content", "content", text))
                                            ));
                                            log.info("content_block_start text: 长度={}, 内容前50字={}", text.length(), text.substring(0, Math.min(text.length(), 50)));
                                        }
                                    }
                                    break;
                                case "content_block_delta":
                                    JsonNode delta = node.path("delta");
                                    String deltaType = delta.path("type").asText("");
                                    // MiniMax兼容: 支持 text_delta 和 text 两种类型
                                    if ("text_delta".equals(deltaType) || "text".equals(deltaType)) {
                                        String text = delta.has("text") ? delta.path("text").asText("")
                                                : delta.has("content") ? delta.path("content").asText("")
                                                : delta.path("partial_json").asText("");
                                        if (!text.isEmpty()) {
                                            fullResponse.append(text);
                                            emitter.send(SseEmitter.event().name("message").data(
                                                    objectMapper.writeValueAsString(Map.of("type", "content", "content", text))
                                            ));
                                            log.debug("text_delta: 长度={}", text.length());
                                        }
                                    } else if (!deltaType.isEmpty()) {
                                        log.info("未处理的delta类型: {}, 数据: {}", deltaType, delta.toString().substring(0, Math.min(delta.toString().length(), 200)));
                                    }
                                    break;
                                case "content_block_stop":
                                    int cbIndex = node.path("index").asInt(-1);
                                    log.debug("content_block_stop index: {}", cbIndex);
                                    break;
                                case "message_delta":
                                    JsonNode msgDelta = node.path("delta");
                                    String stopReason = msgDelta.path("stop_reason").asText("");
                                    if (!stopReason.isEmpty()) {
                                        log.info("MiniMax消息停止原因: {}, 累计输出字符数: {}", stopReason, fullResponse.length());
                                    }
                                    break;
                                case "message_stop":
                                    emitter.send(SseEmitter.event().name("message").data(
                                            objectMapper.writeValueAsString(Map.of("type", "done"))
                                    ));
                                    return;
                                case "error":
                                    String errorMsg = node.path("error").path("message").asText("未知错误");
                                    throw new RuntimeException("MiniMax流式错误: " + errorMsg);
                                default:
                                    // message_start, ping 等事件是正常协议事件，无需处理
                                    if (!eventType.isEmpty() && !"message_start".equals(eventType) && !"ping".equals(eventType)) {
                                        log.debug("未处理的SSE事件类型: {}, data: {}", eventType, data.substring(0, Math.min(data.length(), 200)));
                                    }
                                    break;
                            }
                        } catch (Exception parseEx) {
                            if (parseEx instanceof RuntimeException) throw parseEx;
                            log.debug("解析SSE行失败: {}", data);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("MiniMax流式对话失败: {}", e.getMessage());
            throw new RuntimeException("流式对话失败: " + e.getMessage());
        }
    }
}
