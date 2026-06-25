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
 * 智能对话服务实现 - 双库检索(知识库+记忆库) + DeepSeek V4 Pro流式对话(思考模式)
 *
 * 检索流程:
 * 1. 记忆库检索: PostgreSQL向量搜索(MemoryService.search)
 * 2. 知识库检索: 同样使用PostgreSQL向量搜索(MemoryService.search，不限定domain)
 * 3. 合并去重结果作为上下文
 * 4. 调DeepSeek V4 Pro API流式对话(思考模式，含思维链)
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

    @Value("${app.minimax.embedding-url:https://api.minimaxi.com/v1/embeddings}")
    private String minimaxEmbeddingUrl;

    @Value("${app.minimax.embedding-model:embo-01}")
    private String minimaxEmbeddingModel;

    @Value("${app.minimax.model:MiniMax-M3}")
    private String minimaxModel;

    @Value("${app.deepseek.api-key:}")
    private String deepseekApiKey;

    @Value("${app.deepseek.base-url:https://api.deepseek.com}")
    private String deepseekBaseUrl;

    @Value("${app.deepseek.model:deepseek-v4-pro}")
    private String deepseekModel;

    @Value("${app.deepseek.thinking-enabled:true}")
    private boolean deepseekThinkingEnabled;

    @Value("${app.deepseek.reasoning-effort:high}")
    private String deepseekReasoningEffort;

    @Value("${app.deepseek.web-search-enabled:true}")
    private boolean deepseekWebSearchEnabled;

    @Value("${app.deepseek.web-search-context-size:medium}")
    private String deepseekWebSearchContextSize;

    @Override
    public SseEmitter smartChat(String message, String userId, String company, String conversationId, String mode) {
        log.info("智能对话: message='{}', userId='{}', company='{}', conversationId='{}', mode='{}'", message, userId, company, conversationId, mode);
        SseEmitter emitter = new SseEmitter(600000L); // 10分钟超时

        new Thread(() -> {
            try {
                // 0. 确定对话ID：如果未传则获取或创建
                String convId = conversationId;
                if (convId == null || convId.isEmpty()) {
                    convId = getOrCreateDefaultConversation(userId, company, mode);
                }

                // 1. 加载历史对话（按conversationId）
                List<Map<String, Object>> history = getChatHistory(userId, company, convId);

                // 2. 意图识别：判断是否涉及供应链/工厂数据
                boolean supplyChainIntent = isSupplyChainIntent(message);
                boolean hasProductCode = extractProductCode(message) != null;
                // 当用户提到具体产品编码+供应链意图时，认为是"强供应链意图"
                boolean strongSupplyChainIntent = supplyChainIntent && hasProductCode;

                // 岗位意图识别
                boolean positionIntent = isPositionIntent(message);

                // 通用闲聊意图识别：当用户问的是闲聊/身份/通用常识类问题时，跳过所有知识库检索
                boolean generalChatIntent = isGeneralChatIntent(message);

                // 联网搜索意图识别：当用户明确要求联网/全网搜索时，强制启用联网搜索
                boolean webSearchIntent = isWebSearchIntent(message);
                log.info("意图识别: generalChatIntent={}, webSearchIntent={}, supplyChainIntent={}, positionIntent={}",
                        generalChatIntent, webSearchIntent, supplyChainIntent, positionIntent);

                // 3. 供应链/工厂数据检索(优先检索，命中后降低知识库检索权重)
                List<Map<String, Object>> supplyChainResults = Collections.emptyList();
                if (supplyChainIntent && !generalChatIntent) {
                    try {
                        supplyChainResults = searchSupplyChain(message);
                        log.info("供应链数据检索到 {} 条结果", supplyChainResults.size());
                    } catch (Exception e) {
                        log.warn("供应链数据检索异常: {}", e.getMessage());
                    }
                }

                // 4. 双库检索（当强供应链意图且已找到数据时，或通用闲聊意图时，跳过向量检索）
                boolean skipVectorSearch = (strongSupplyChainIntent && !supplyChainResults.isEmpty()) || generalChatIntent;

                // 4a. 岗位卡片向量检索（优先于知识库PDF，避免岗位问题被无关PDF干扰）
                List<Map<String, Object>> positionCardResults = Collections.emptyList();
                if (!skipVectorSearch) {
                    try {
                        positionCardResults = searchPositionCards(message, company);
                        log.info("岗位卡片检索到 {} 条结果", positionCardResults.size());
                    } catch (Exception e) {
                        log.warn("岗位卡片检索异常: {}", e.getMessage());
                    }
                }

                // 当岗位意图且岗位卡片有结果时，或通用闲聊意图时，跳过知识库PDF检索
                boolean skipKnowledgeSearch = skipVectorSearch || (positionIntent && !positionCardResults.isEmpty());

                // 4b. 记忆库检索(PostgreSQL向量)
                List<MemorySearchResult> memoryResults = Collections.emptyList();
                if (!skipVectorSearch) {
                    try {
                        memoryResults = memoryService.search(message, null, 0.2, 5, company, userId);
                        log.info("记忆库检索到 {} 条结果", memoryResults.size());
                    } catch (Exception e) {
                        log.warn("记忆库检索异常: {}", e.getMessage());
                    }
                } else {
                    log.info("跳过向量检索: generalChatIntent={}, strongSupplyChain={}", generalChatIntent, strongSupplyChainIntent && !supplyChainResults.isEmpty());
                }

                // 4c. 知识库检索(Coze SDK via Next.js)
                List<Map<String, Object>> knowledgeResults = Collections.emptyList();
                if (!skipKnowledgeSearch) {
                    try {
                        knowledgeResults = searchKnowledgeBase(message, company);
                        log.info("知识库检索到 {} 条结果", knowledgeResults.size());
                    } catch (Exception e) {
                        log.warn("知识库检索异常: {}", e.getMessage());
                    }
                } else {
                    String reason = generalChatIntent ? "通用闲聊意图" : (skipVectorSearch ? "强供应链意图" : "岗位意图已命中岗位卡片");
                    log.info("跳过知识库检索避免干扰（原因: {}）", reason);
                }

                // 4d. 图片搜索(当用户意图涉及找图时)
                List<Map<String, Object>> imageResults = Collections.emptyList();
                if (isImageSearchIntent(message)) {
                    try {
                        imageResults = searchImages(message, userId);
                        log.info("图片搜索匹配到 {} 条结果", imageResults.size());
                    } catch (Exception e) {
                        log.warn("图片搜索异常: {}", e.getMessage());
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

                // 岗位卡片来源
                for (Map<String, Object> r : positionCardResults) {
                    sources.add(Map.of(
                            "source", "position_card",
                            "content", r.getOrDefault("content", "").toString(),
                            "score", r.getOrDefault("score", 0)
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

                // 4. 构建知识上下文（供应链数据优先放置在前面，确保AI优先参考）
                StringBuilder knowledgeContext = new StringBuilder();

                // 供应链/工厂数据上下文（优先级最高，放在最前面）
                if (!supplyChainResults.isEmpty()) {
                    knowledgeContext.append("## 【重要】供应链/工厂业务数据（精确数据，优先引用）：\n");
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
                    knowledgeContext.append("⚠️ 用户询问的是供应链/工厂相关问题，请务必基于以上精确业务数据回答，引用具体数字。" +
                            "不要用知识库文档中的泛泛内容替代这些精确数据！\n\n");
                }

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

                // 岗位卡片上下文（岗位意图时标注优先级最高，排在知识库PDF之前）
                if (!positionCardResults.isEmpty()) {
                    if (positionIntent) {
                        knowledgeContext.append("## 【重要】岗位知识卡片（用户询问的是岗位相关问题，请优先基于以下岗位卡片回答）：\n");
                    } else {
                        knowledgeContext.append("## 岗位知识卡片（员工实际工作经验）：\n");
                    }
                    for (int i = 0; i < positionCardResults.size(); i++) {
                        Map<String, Object> r = positionCardResults.get(i);
                        double score = ((Number) r.getOrDefault("score", 0)).doubleValue();
                        String content = r.getOrDefault("content", "").toString();
                        if (content.length() > 500) content = content.substring(0, 500) + "...";
                        knowledgeContext.append(String.format("### 岗位卡片%d (相关度: %.1f%%)\n%s\n\n",
                                i + 1, score * 100, content));
                    }
                    if (positionIntent) {
                        knowledgeContext.append("⚠️ 用户询问的是岗位相关问题，请务必基于以上岗位知识卡片中的实际工作经验回答，不要用知识库文档中的泛泛内容替代！\n");
                    } else {
                        knowledgeContext.append("⚠️ 以上来自员工填写的岗位知识卡片，包含真实工作经验和职责描述，回答岗位相关问题时应优先参考。\n");
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
                } else if (isImageSearchIntent(message)) {
                    knowledgeContext.append("## 图片库搜索结果：未找到匹配的图片\n");
                    knowledgeContext.append("用户请求在图片库中查找图片，但根据关键词搜索未找到任何匹配的产品图片。请如实告知用户图片库中没有找到相关图片，并建议用户尝试其他关键词或上传相关图片。\n");
                }

                // 5. 构建messages(含历史上下文)
                List<Map<String, Object>> messages = new ArrayList<>();

                // System prompt: 根据mode构建不同的角色定位
                String systemPrompt;
                if ("factory".equals(mode)) {
                    systemPrompt = "你是盈云产品智能中台的【工厂供应链AI助手】，专门服务于工厂和供应链管理人员。" +
                            "核心职责：" +
                            "1. 回答产品报价、原料采购、辅料采购、生产计划等供应链业务问题。" +
                            "2. 当检索结果中包含【供应链/工厂业务数据】时，必须优先且主要基于这些精确的业务数据回答，引用具体数字和供应商名称。" +
                            "3. 当用户询问具体产品的报价、成本、原料、供应商等数据时，只使用供应链业务数据中的精确数字作答；如果供应链数据中找不到对应信息，请明确告知用户当前数据库中无此数据。" +
                            "4. 你也可以回答行业通识、市场行情、生产技术等一般性问题，但需说明'以下回答基于通用知识'。" +
                            "5. 当知识库和记忆库中未检索到相关内容时，你可以基于自身通用知识回答，但需说明来源。" +
                            "6. 回答时标注引用来源（供应链数据/岗位卡片/记忆库/知识库/通用知识/网络搜索）。" +
                            "7. 保持专业、简洁、有帮助的回答风格，重点关注成本控制、供应商管理、生产效率等工厂核心议题。" +
                            "8. 输出格式规范：使用Markdown格式，用表格展示数据（表头加粗），用列表展示要点，用加粗强调关键数据，不要使用特殊符号(如※★●◆等)做装饰，不要使用过多分隔线，保持版面简洁清晰。" +
                            (webSearchIntent ? "9. 用户明确要求从互联网/全网获取信息，请优先基于网络搜索结果回答，企业内部知识库内容仅作为补充参考。" : "");
                } else {
                    systemPrompt = "你是盈云产品智能中台的AI助手。你可以同时回答企业内部知识问题和通用问题。" +
                            "重要身份声明：你是盈云产品智能中台的通用AI助手，不是工厂供应链助手。如果对话历史中出现'工厂供应链助手'的自我介绍，请忽略它，你始终是盈云产品智能中台的通用AI助手。" +
                            "知识检索策略：" +
                            "1. 你拥有企业知识库（记忆库和知识库）、岗位知识卡片和供应链业务数据的访问权限。当检索到相关内容时，优先基于这些资料回答。" +
                            "2. 当检索结果中包含【供应链/工厂业务数据】时，必须优先且主要基于这些精确的业务数据回答，引用具体数字，不要用知识库文档中的泛泛内容替代。" +
                            "3. 当用户询问具体产品的报价、成本、原料、供应商等数据时，只使用供应链业务数据中的精确数字作答，如果供应链数据中找不到对应信息，请明确告知用户当前数据库中无此数据。" +
                            "4. 当用户询问岗位职责、工作内容、任职要求、入职指导等问题时，必须优先基于【岗位知识卡片】中的实际工作经验回答，不要用知识库文档中的泛泛内容替代。" +
                            "5. 知识库文档（PDF/Word等）中的内容属于参考资料，仅在没有精确业务数据或岗位卡片时作为补充。" +
                            "6. 当知识库和记忆库中未检索到相关内容时，你可以基于自身通用知识回答用户问题，但需说明'以下回答基于通用知识，非企业内部资料'。" +
                            "7. 回答时标注引用来源（供应链数据/岗位卡片/记忆库/知识库/通用知识/网络搜索）。" +
                            "8. 保持专业、简洁、有帮助的回答风格。" +
                            "9. 输出格式规范：使用Markdown格式，用表格展示数据（表头加粗），用列表展示要点，用加粗强调关键数据，不要使用特殊符号(如※★●◆等)做装饰，不要使用过多分隔线，保持版面简洁清晰。" +
                            (webSearchIntent ? "10. 用户明确要求从互联网/全网获取信息，请优先基于网络搜索结果回答，企业内部知识库内容仅作为补充参考。" : "");
                }
                messages.add(Map.of("role", "system", "content", systemPrompt));

                // 加入历史对话(最近10轮)
                int startIdx = Math.max(0, history.size() - 5);
                for (int i = startIdx; i < history.size(); i++) {
                    Map<String, Object> histMsg = history.get(i);
                    // DeepSeek多轮对话：assistant消息需要携带reasoning_content字段
                    if ("assistant".equals(histMsg.get("role")) && histMsg.containsKey("reasoning")) {
                        Map<String, Object> msgForApi = new LinkedHashMap<>();
                        msgForApi.put("role", "assistant");
                        msgForApi.put("content", histMsg.get("content"));
                        msgForApi.put("reasoning_content", histMsg.get("reasoning"));
                        messages.add(msgForApi);
                    } else {
                        // user和system消息只保留role和content
                        Map<String, Object> msgForApi = new LinkedHashMap<>();
                        msgForApi.put("role", histMsg.get("role"));
                        msgForApi.put("content", histMsg.get("content"));
                        messages.add(msgForApi);
                    }
                }

                // 当前用户消息(带知识上下文)
                String userContent = message;
                if (!knowledgeContext.isEmpty()) {
                    boolean hasSupplyChain = !supplyChainResults.isEmpty();
                    boolean hasPositionCards = !positionCardResults.isEmpty();
                    userContent = knowledgeContext.toString() + "\n---\n用户问题: " + message;
                    if (hasSupplyChain) {
                        userContent += "\n\n请优先基于上方【供应链/工厂业务数据】中的精确数字回答，不要使用知识库文档内容替代业务数据。";
                    } else if (positionIntent && hasPositionCards) {
                        userContent += "\n\n请优先基于上方【岗位知识卡片】中的实际工作经验回答，不要使用知识库文档内容替代岗位卡片中的精确信息。";
                    } else {
                        userContent += "\n\n请优先基于以上知识内容回答，如资料不足以完整回答，可补充自身通用知识，并标注来源。";
                    }
                } else {
                    userContent = message;
                }
                messages.add(Map.of("role", "user", "content", userContent));

                // 6. 保存用户消息
                saveChatMessage(userId, convId, "user", message, company, null, mode);

                // 7. 流式调用DeepSeek V4 Pro
                // 联网搜索策略：用户明确要求联网搜索，或通用闲聊，或知识库无结果时启用联网搜索
                boolean enableWebSearch = webSearchIntent || generalChatIntent || knowledgeContext.isEmpty();
                StringBuilder fullResponse = new StringBuilder();
                StringBuilder fullReasoning = new StringBuilder();
                try {
                    streamChat(emitter, messages, fullResponse, fullReasoning, enableWebSearch);
                } finally {
                    // 8. 无论流是否成功，都保存已收集的AI回复（含思维链）
                    if (fullResponse.length() > 0) {
                        String reasoning = fullReasoning.length() > 0 ? fullReasoning.toString() : null;
                        saveChatMessage(userId, convId, "assistant", fullResponse.toString(), company, reasoning, mode);
                    }
                }

                // 9. 更新对话标题（如果是新对话的第一条消息）
                updateConversationTitleFromMessage(convId, message);

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
    @Override
    public List<Map<String, Object>> getChatHistory(String userId, String company, String conversationId, String mode) {
        String modeCondition = "";
        Object[] params;
        if (mode != null && !mode.isEmpty()) {
            modeCondition = " AND mode = ? ";
        }

        List<Map<String, Object>> results;
        if (conversationId != null && !conversationId.isEmpty()) {
            // 按conversationId查询对话历史
            String sql = "SELECT role, content, reasoning_content, created_at FROM smart_chat_history " +
                    "WHERE conversation_id = ?::uuid AND user_id = ? AND (company = ? OR company IS NULL) " +
                    modeCondition +
                    "ORDER BY created_at ASC LIMIT 100";
            if (mode != null && !mode.isEmpty()) {
                params = new Object[]{conversationId, userId, company, mode};
            } else {
                params = new Object[]{conversationId, userId, company};
            }
            results = jdbcTemplate.query(sql,
                    (rs, rowNum) -> {
                        Map<String, Object> msg = new LinkedHashMap<>();
                        msg.put("role", rs.getString("role"));
                        msg.put("content", rs.getString("content"));
                        String reasoning = rs.getString("reasoning_content");
                        if (reasoning != null && !reasoning.isEmpty()) {
                            msg.put("reasoning", reasoning);
                        }
                        msg.put("createdAt", rs.getTimestamp("created_at").toLocalDateTime().toString());
                        return msg;
                    },
                    params
            );
        } else {
            // 兼容旧逻辑：按userId+company查询最近10轮对话
            String sql = "SELECT role, content, reasoning_content, created_at FROM smart_chat_history " +
                    "WHERE user_id = ? AND (company = ? OR company IS NULL) " +
                    modeCondition +
                    "ORDER BY created_at DESC LIMIT 20";
            if (mode != null && !mode.isEmpty()) {
                params = new Object[]{userId, company, mode};
            } else {
                params = new Object[]{userId, company};
            }
            results = jdbcTemplate.query(sql,
                    (rs, rowNum) -> {
                        Map<String, Object> msg = new LinkedHashMap<>();
                        msg.put("role", rs.getString("role"));
                        msg.put("content", rs.getString("content"));
                        String reasoning = rs.getString("reasoning_content");
                        if (reasoning != null && !reasoning.isEmpty()) {
                            msg.put("reasoning", reasoning);
                        }
                        msg.put("createdAt", rs.getTimestamp("created_at").toLocalDateTime().toString());
                        return msg;
                    },
                    params
            );
            Collections.reverse(results);
        }
        return results;
    }

    // 兼容旧版3参数调用
    public List<Map<String, Object>> getChatHistory(String userId, String company, String conversationId) {
        List<Map<String, Object>> results;
        if (conversationId != null && !conversationId.isEmpty()) {
            // 按conversationId查询对话历史
            String sql = "SELECT role, content, reasoning_content, created_at FROM smart_chat_history " +
                    "WHERE conversation_id = ?::uuid AND user_id = ? AND (company = ? OR company IS NULL) " +
                    "ORDER BY created_at ASC LIMIT 100";
            results = jdbcTemplate.query(sql,
                    (rs, rowNum) -> {
                        Map<String, Object> msg = new LinkedHashMap<>();
                        msg.put("role", rs.getString("role"));
                        msg.put("content", rs.getString("content"));
                        String reasoning = rs.getString("reasoning_content");
                        if (reasoning != null && !reasoning.isEmpty()) {
                            msg.put("reasoning", reasoning);
                        }
                        msg.put("createdAt", rs.getTimestamp("created_at").toLocalDateTime().toString());
                        return msg;
                    },
                    conversationId, userId, company
            );
        } else {
            // 兼容旧逻辑：按userId+company查询最近10轮对话
            String sql = "SELECT role, content, reasoning_content, created_at FROM smart_chat_history " +
                    "WHERE user_id = ? AND (company = ? OR company IS NULL) " +
                    "ORDER BY created_at DESC LIMIT 20";
            results = jdbcTemplate.query(sql,
                    (rs, rowNum) -> {
                        Map<String, Object> msg = new LinkedHashMap<>();
                        msg.put("role", rs.getString("role"));
                        msg.put("content", rs.getString("content"));
                        String reasoning = rs.getString("reasoning_content");
                        if (reasoning != null && !reasoning.isEmpty()) {
                            msg.put("reasoning", reasoning);
                        }
                        msg.put("createdAt", rs.getTimestamp("created_at").toLocalDateTime().toString());
                        return msg;
                    },
                    userId, company
            );
            Collections.reverse(results);
        }
        return results;
    }

    @Override
    @Transactional
    public void clearChatHistory(String userId, String company, String conversationId, String mode) {
        if (conversationId != null && !conversationId.isEmpty()) {
            jdbcTemplate.update(
                    "DELETE FROM smart_chat_history WHERE conversation_id = ?::uuid AND user_id = ? AND (company = ? OR company IS NULL)",
                    conversationId, userId, company
            );
        } else {
            // 按mode筛选删除：只删除对应模式的对话历史
            String modeCondition = (mode != null && !mode.isEmpty()) ? " AND conversation_id IN (SELECT id FROM smart_chat_conversations WHERE mode = ?)" : "";
            if (mode != null && !mode.isEmpty()) {
                jdbcTemplate.update(
                        "DELETE FROM smart_chat_history WHERE user_id = ? AND (company = ? OR company IS NULL)" + modeCondition,
                        userId, company, mode
                );
            } else {
                jdbcTemplate.update(
                        "DELETE FROM smart_chat_history WHERE user_id = ? AND (company = ? OR company IS NULL)",
                        userId, company
                );
            }
        }
    }

    // ========== 对话管理 ==========

    @Override
    public Map<String, Object> createConversation(String userId, String company, String title, String mode) {
        String convId = UUID.randomUUID().toString();
        String convTitle = (title != null && !title.isEmpty()) ? title : "新对话";
        String modeValue = (mode != null && !mode.isEmpty()) ? mode : "designer";
        jdbcTemplate.update(
                "INSERT INTO smart_chat_conversations (id, user_id, company, title, mode, created_at, updated_at) " +
                        "VALUES (?::uuid, ?, ?, ?, ?::varchar, NOW(), NOW())",
                convId, userId, company, convTitle, modeValue
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", convId);
        result.put("title", convTitle);
        result.put("createdAt", java.time.LocalDateTime.now().toString());
        return result;
    }

    @Override
    public List<Map<String, Object>> getConversations(String userId, String company, String mode) {
        String modeValue = (mode != null && !mode.isEmpty()) ? mode : null;
        String sql;
        Object[] params;
        if (modeValue != null) {
            sql = "SELECT id, title, created_at, updated_at FROM smart_chat_conversations " +
                    "WHERE user_id = ? AND (company = ? OR company IS NULL) AND mode = ? " +
                    "ORDER BY updated_at DESC";
            params = new Object[]{userId, company, modeValue};
        } else {
            // 兼容旧数据：mode IS NULL 视为 designer
            sql = "SELECT id, title, created_at, updated_at FROM smart_chat_conversations " +
                    "WHERE user_id = ? AND (company = ? OR company IS NULL) AND (mode = 'designer' OR mode IS NULL) " +
                    "ORDER BY updated_at DESC";
            params = new Object[]{userId, company};
        }
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> {
                    Map<String, Object> conv = new LinkedHashMap<>();
                    conv.put("id", rs.getString("id"));
                    conv.put("title", rs.getString("title"));
                    conv.put("createdAt", rs.getTimestamp("created_at").toLocalDateTime().toString());
                    conv.put("updatedAt", rs.getTimestamp("updated_at").toLocalDateTime().toString());
                    return conv;
                },
                params
        );
    }

    @Override
    @Transactional
    public void updateConversationTitle(String conversationId, String title) {
        jdbcTemplate.update(
                "UPDATE smart_chat_conversations SET title = ?, updated_at = NOW() WHERE id = ?::uuid",
                title, conversationId
        );
    }

    @Override
    @Transactional
    public void deleteConversation(String conversationId, String userId, String company) {
        // 先删除消息
        jdbcTemplate.update(
                "DELETE FROM smart_chat_history WHERE conversation_id = ?::uuid AND user_id = ? AND (company = ? OR company IS NULL)",
                conversationId, userId, company
        );
        // 再删除对话
        jdbcTemplate.update(
                "DELETE FROM smart_chat_conversations WHERE id = ?::uuid AND user_id = ? AND (company = ? OR company IS NULL)",
                conversationId, userId, company
        );
    }

    /**
     * 获取或创建默认对话
     */
    private String getOrCreateDefaultConversation(String userId, String company, String mode) {
        // 查找最近的对话（按mode筛选）
        String modeCondition = (mode != null && !mode.isEmpty()) ? "AND mode = ?" : "AND mode = 'designer'";
        String sql = "SELECT id FROM smart_chat_conversations " +
                "WHERE user_id = ? AND (company = ? OR company IS NULL) " + modeCondition + " " +
                "ORDER BY updated_at DESC LIMIT 1";
        List<String> existing;
        if (mode != null && !mode.isEmpty()) {
            existing = jdbcTemplate.query(sql,
                    (rs, rowNum) -> rs.getString("id"),
                    userId, company, mode
            );
        } else {
            existing = jdbcTemplate.query(sql,
                    (rs, rowNum) -> rs.getString("id"),
                    userId, company
            );
        }
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        // 没有对话则创建
        Map<String, Object> conv = createConversation(userId, company, "新对话", mode);
        return (String) conv.get("id");
    }

    /**
     * 根据第一条消息自动更新对话标题
     */
    private void updateConversationTitleFromMessage(String conversationId, String message) {
        try {
            // 检查该对话是否只有0-1条消息（刚创建的对话）
            String countSql = "SELECT COUNT(*) FROM smart_chat_history WHERE conversation_id = ?::uuid";
            Integer count = jdbcTemplate.queryForObject(countSql, Integer.class, conversationId);
            if (count != null && count <= 2) {
                // 用消息前20个字符作为标题
                String title = message.length() > 20 ? message.substring(0, 20) + "..." : message;
                updateConversationTitle(conversationId, title);
            }
            // 更新对话的 updated_at
            jdbcTemplate.update(
                    "UPDATE smart_chat_conversations SET updated_at = NOW() WHERE id = ?::uuid",
                    conversationId
            );
        } catch (Exception e) {
            log.warn("更新对话标题失败: {}", e.getMessage());
        }
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
            // 智能提取中文关键词
            List<String> keywords = extractChineseKeywords(query);
            log.info("图片搜索关键词提取: query={}, keywords={}", query, keywords);

            if (keywords.isEmpty()) {
                log.info("未提取到有效关键词，跳过图片搜索");
                return Collections.emptyList();
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

            // 如果关键词搜索不到，尝试用更短的关键词（取每个关键词的前2字）再搜一次
            if (rawImages.isEmpty() && keywords.stream().anyMatch(kw -> kw.length() > 2)) {
                List<String> shortKeywords = new ArrayList<>();
                for (String kw : keywords) {
                    if (kw.length() > 2) {
                        shortKeywords.add(kw.substring(0, 2));
                    } else {
                        shortKeywords.add(kw);
                    }
                }
                log.info("尝试短关键词搜索: {}", shortKeywords);

                StringBuilder fallbackSql = new StringBuilder();
                fallbackSql.append("SELECT id, title, url, thumbnail_url, is_main_image, file_type, ");
                fallbackSql.append("width, height, product_id, album_name, created_at ");
                fallbackSql.append("FROM images WHERE deleted = false AND user_id = ? ");
                fallbackSql.append("AND (");
                for (int i = 0; i < shortKeywords.size(); i++) {
                    if (i > 0) fallbackSql.append(" OR ");
                    fallbackSql.append("(COALESCE(title, '') ILIKE ? OR COALESCE(description, '') ILIKE ? OR COALESCE(album_name, '') ILIKE ?)");
                }
                fallbackSql.append(") ");
                fallbackSql.append("ORDER BY is_main_image DESC, created_at DESC ");
                fallbackSql.append("LIMIT 50");

                List<Object> fallbackParams = new ArrayList<>();
                fallbackParams.add(userId);
                for (String kw : shortKeywords) {
                    String pattern = "%" + kw + "%";
                    fallbackParams.add(pattern);
                    fallbackParams.add(pattern);
                    fallbackParams.add(pattern);
                }

                rawImages = jdbcTemplate.query(fallbackSql.toString(),
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
                        fallbackParams.toArray()
                );
                log.info("短关键词搜索返回 {} 条记录", rawImages.size());
            }

            // 如果仍然搜索不到，返回空列表让AI告知用户（不再兜底返回无关图片）
            if (rawImages.isEmpty()) {
                log.info("关键词未匹配到图片，返回空结果");
                return Collections.emptyList();
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
        String[] stops = {"一下", "什么", "怎么", "这个", "那个", "可以", "帮我", "请问"};
        for (String s : stops) {
            if (word.equals(s)) return true;
        }
        return false;
    }

    /**
     * 智能中文关键词提取
     * 1. 先按标点和功能词分割
     * 2. 去除停用词/功能词
     * 3. 用滑动窗口提取2-4字子串，优先保留短词
     * 4. 去重并保持顺序
     */
    private List<String> extractChineseKeywords(String query) {
        // 第一步：按标点、空格和常见功能词分割
        String cleaned = query.replaceAll("[\\s,，。！？?、；：\u201c\u201d\u2018\u2019（）()\\[\\]【】{}]+", " ");

        // 去除常见的功能词/停用短语（按长度从长到短替换，避免部分匹配）
        String[] functionalPhrases = {
            "帮我去", "帮我找", "帮我看", "帮我搜", "帮我查",
            "帮我推荐", "帮我搜索", "帮我查找", "帮我展示", "帮我显示",
            "请帮我", "能不能", "可不可以",
            "图片库中", "图片库里面", "图片库里",
            "给我推荐", "给我找", "给我看", "给我搜",
            "去图片库", "从图片库",
            "推荐几款", "推荐几个", "推荐一些",
            "有没有", "有多少", "是什么样的",
            "是什么", "怎么样", "长什么样"
        };
        for (String phrase : functionalPhrases) {
            cleaned = cleaned.replace(phrase, " ");
        }

        // 去除图片库相关词（搜索图片时这些不是有效关键词）
        String[] imageStopWords = {"图片", "照片", "图片库", "图片列表", "相册", "主图", "详情图", "效果图",
            "产品图", "商品图", "图片搜索", "图片查询"};
        for (String sw : imageStopWords) {
            cleaned = cleaned.replace(sw, " ");
        }

        // 第二步：按空格分割并收集候选词
        String[] segments = cleaned.split("\\s+");
        List<String> candidates = new ArrayList<>();
        for (String seg : segments) {
            String s = seg.trim();
            if (s.length() >= 2 && s.length() <= 10) {
                candidates.add(s);
            }
        }

        // 第三步：如果候选词太少，用滑动窗口从原始查询中提取2-4字子串
        if (candidates.size() < 2) {
            String raw = query.replaceAll("[\\s,，。！？?、；：\u201c\u201d\u2018\u2019（）()\\[\\]【】{}]+", "");
            Set<String> added = new HashSet<>();
            for (String c : candidates) added.add(c);

            // 先尝试4字窗口，再3字，再2字
            for (int windowSize = 4; windowSize >= 2; windowSize--) {
                for (int i = 0; i <= raw.length() - windowSize; i++) {
                    String sub = raw.substring(i, i + windowSize);
                    if (!isStopWord(sub) && !isSubStringOfExisting(sub, candidates) && !added.contains(sub)) {
                        if (!isMostlyFunctional(sub)) {
                            candidates.add(sub);
                            added.add(sub);
                        }
                    }
                }
            }
        }

        // 去重并保持顺序，限制最多8个关键词
        List<String> keywords = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String kw : candidates) {
            if (!seen.contains(kw) && kw.length() >= 2) {
                seen.add(kw);
                keywords.add(kw);
                if (keywords.size() >= 8) break;
            }
        }

        return keywords;
    }

    /** 检查子串是否已是某个候选词的子串（避免冗余） */
    private boolean isSubStringOfExisting(String sub, List<String> candidates) {
        for (String c : candidates) {
            if (c.contains(sub) && !c.equals(sub)) return true;
        }
        return false;
    }

    /** 检查一个短子串是否主要由功能词组成 */
    private boolean isMostlyFunctional(String sub) {
        String[] functional = {"帮", "我", "去", "给", "的", "了", "吗", "呢", "几", "款", "些",
            "一", "个", "张", "找", "看", "搜", "查", "要", "想", "能", "会", "有", "在"};
        int funcCount = 0;
        for (String f : functional) {
            if (sub.contains(f)) funcCount++;
        }
        return funcCount > sub.length() / 2;
    }

    /**
     * 岗位卡片检索 - 查询岗位知识卡片的向量(knowledge_embeddings, source_type='POSITION_CARD')
     */
    private List<Map<String, Object>> searchPositionCards(String query, String company) {
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            float[] embeddingArray = getEmbedding(query);
            if (embeddingArray == null || embeddingArray.length == 0) {
                return results;
            }
            // 将float[]转为PostgreSQL vector格式的字符串
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < embeddingArray.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(embeddingArray[i]);
            }
            sb.append("]");
            String queryEmbedding = sb.toString();

            String sql = "SELECT e.chunk_text, e.chunk_index, e.source_doc_id, " +
                    "1 - (e.embedding <#> ?::vector) AS similarity " +
                    "FROM knowledge_embeddings e " +
                    "WHERE e.source_type = 'POSITION_CARD' " +
                    "AND (e.company = ? OR e.company IS NULL) " +
                    "AND 1 - (e.embedding <#> ?::vector) > 0.25 " +
                    "ORDER BY e.embedding <#> ?::vector " +
                    "LIMIT 5";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, queryEmbedding, company, queryEmbedding, queryEmbedding);
            for (Map<String, Object> row : rows) {
                Map<String, Object> item = new HashMap<>();
                item.put("content", row.get("chunk_text"));
                item.put("sourceDocId", row.get("source_doc_id"));
                item.put("similarity", row.get("similarity"));
                item.put("source", "position_card");
                results.add(item);
            }
            log.info("岗位卡片检索完成, 查询: '{}', 命中: {}条", query, results.size());
        } catch (Exception e) {
            log.warn("岗位卡片检索失败: {}", e.getMessage());
        }
        return results;
    }

    /**
     * 知识库检索 - 查询知识库独立的向量表(knowledge_embeddings, source_type='KNOWLEDGE_BASE')
     */
    private List<Map<String, Object>> searchKnowledgeBase(String query, String company) {
        try {
            List<MemorySearchResult> allResults = knowledgeBaseService.search(query, 0.15, 8, company);

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
     * 岗位意图识别 - 判断用户是否在询问岗位职责、工作内容等
     */
    private boolean isPositionIntent(String message) {
        String lower = message.toLowerCase();
        String[] patterns = {
            "岗位", "职位", "职责", "工作内容", "任职要求", "能力要求",
            "入职", "新人", "上手", "交接", "指导", "培训",
            "产出物", "协作", "上下游", "改进", "瓶颈",
            "做什么", "负责什么", "需要什么能力", "工作流程",
            "团队", "部门职责", "岗位职责", "岗位要求",
            "经验", "工作经历", "工作经验", "岗位卡片"
        };
        for (String kw : patterns) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    /**
     * 通用闲聊意图识别 - 当用户问的是闲聊/身份/通用常识类问题时，
     * 不需要检索知识库/记忆库/岗位卡片，直接由大模型自身知识回答。
     * 
     * 判断逻辑：
     * 1. 消息很短（<=10字）且不包含任何专业领域关键词
     * 2. 包含典型的闲聊/身份/问候/元问题关键词
     */
    private boolean isGeneralChatIntent(String message) {
        String lower = message.toLowerCase().trim();
        
        // 典型的通用闲聊关键词（元问题/身份/问候/闲聊）
        String[] generalPatterns = {
            // AI身份/元问题
            "你是谁", "你是什么", "你叫什么", "你叫啥", "你的名字",
            "什么模型", "哪个模型", "什么大模型", "你用的什么模型",
            "你是ai", "你是人工智能", "你是机器人", "你是助手",
            "你能做什么", "你会什么", "你有什么功能", "你擅长什么",
            "你是gpt", "你是chatgpt", "你是deepseek", "你是minimax",
            // 问候/闲聊
            "你好", "嗨", "哈喽", "hello", "hi ", "早上好", "下午好", "晚上好",
            "再见", "拜拜", "谢谢", "感谢",
            "讲个笑话", "说个笑话", "脑筋急转弯", "猜谜",
            // 通用常识/编程/数学（非企业内部）
            "写一段代码", "帮我写代码", "python", "java代码", "javascript",
            "翻译一下", "翻译成", "算一下", "计算",
            "今天天气", "几号了", "几点了", "星期几",
            "推荐一部", "推荐一首", "推荐一本",
            // 纯感叹/无意义
            "哈哈", "呵呵", "嗯嗯", "好的", "ok", "明白", "知道了"
        };
        
        for (String kw : generalPatterns) {
            if (lower.contains(kw)) return true;
        }
        
        // 消息很短（<=6字）且不包含任何专业领域关键词，很可能是闲聊
        if (lower.length() <= 6) {
            // 排除可能是专业问题的情况
            String[] domainHints = {
                "报价", "成本", "原料", "纱线", "单价", "采购", "供应商",
                "岗位", "职责", "流程", "工艺", "生产", "入库",
                "文档", "知识", "手册", "规范", "标准", "操作"
            };
            for (String hint : domainHints) {
                if (lower.contains(hint)) return false;
            }
            // 短消息+无领域关键词 = 很可能闲聊
            // 但也要排除真正的短问题，比如"袜子怎么织"
            // 只排除纯感叹/纯问候/纯身份问题
            String[] shortGeneral = {
                "你好", "嗨", "hi", "ok", "谢谢", "感谢", "好的",
                "再见", "拜拜", "嗯", "啊", "哦", "哈", "嘿"
            };
            for (String kw : shortGeneral) {
                if (lower.equals(kw)) return true;
            }
        }
        
        return false;
    }

    /**
     * 联网搜索意图识别：当用户明确要求从互联网/全网获取信息时返回true
     * 典型场景："帮我去全网学习无缝内衣的行业知识"、"联网查一下最新的行情"、"网上搜索..."
     */
    private boolean isWebSearchIntent(String message) {
        String lower = message.toLowerCase().trim();

        // 明确要求联网/全网/网上搜索的关键词
        String[] webSearchPatterns = {
            // 直接要求联网搜索（强烈信号）
            "全网", "联网", "网上查", "网上搜", "网上找", "互联网上",
            "在线搜索", "在线查找",
            "去全网", "从网上", "从互联网", "在网",
            // 搜索引擎相关
            "百度一下", "百度搜", "谷歌搜", "google搜",
            // 行业调研/市场分析（通常需要外部信息源）
            "市场调研", "行业调研", "竞品分析", "市场分析",
            "行业报告", "行业趋势", "行业动态", "行业资讯",
            "了解行情", "了解市场", "了解行业",
            "学习行业知识", "行业知识",
            "查行情", "看行情", "行情分析",
            // 明确要求最新外部资讯
            "最新行情", "最新资讯", "最新动态", "最新趋势",
            "实时行情", "实时资讯",
            // 对比分析类
            "对比分析", "竞品对比", "价格对比"
        };

        for (String kw : webSearchPatterns) {
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
        // 匹配常见产品编码格式: HT01-S, HT01-M, HT01-L, AB12C 等
        // 移除可能的中文前缀干扰，如"产品HT01-S"
        Pattern pattern = Pattern.compile("(HT\\d+[-][A-Z]+|[A-Z]{2}\\d+[-]?[A-Z]?)");
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
    private void saveChatMessage(String userId, String conversationId, String role, String content, String company, String reasoningContent, String mode) {
        try {
            if (content == null || content.trim().isEmpty()) {
                log.warn("保存对话消息跳过: content为空, userId={}, role={}", userId, role);
                return;
            }
            String modeValue = (mode != null && !mode.isEmpty()) ? mode : "designer";
            log.info("保存对话消息: userId={}, role={}, contentLength={}, company={}, conversationId={}, hasReasoning={}, mode={}", 
                    userId, role, content.length(), company, conversationId, reasoningContent != null && !reasoningContent.isEmpty(), modeValue);
            jdbcTemplate.update(
                    "INSERT INTO smart_chat_history (id, session_id, conversation_id, role, content, reasoning_content, user_id, company, mode, created_at) " +
                            "VALUES (gen_random_uuid(), ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, NOW())",
                    conversationId, conversationId, role, content, reasoningContent, userId, company, modeValue
            );
            log.info("保存对话消息成功: userId={}, role={}, mode={}", userId, role, modeValue);
        } catch (Exception e) {
            log.error("保存对话消息失败: userId={}, role={}, error={}", userId, role, e.getMessage(), e);
        }
    }

    /**
     * 流式调用MiniMax API (Anthropic兼容接口)
     */
    /**
     * 流式调用DeepSeek V4 Pro（思考模式）
     * 
     * 两种模式：
     * 1. 普通模式：使用Chat Completions API（无联网搜索）
     * 2. 联网搜索模式：使用Anthropic兼容端点 + web_search_20250305工具
     * 
     * Chat Completions格式：
     * - 端点: POST {base_url}/chat/completions
     * - SSE: data: {"choices":[{"delta":{"reasoning_content":"..."}}]}
     * 
     * Anthropic格式（联网搜索）：
     * - 端点: POST {base_url}/anthropic/v1/messages
     * - 请求: {"model":"deepseek-v4-pro","messages":[...],"tools":[{"type":"web_search_20250305"}]}
     * - SSE: event: content_block_delta, data: {"delta":{"type":"thinking_delta","thinking":"..."}}
     *        event: content_block_delta, data: {"delta":{"type":"text_delta","text":"..."}}
     */
    private void streamChat(SseEmitter emitter, List<Map<String, Object>> messages,
                            StringBuilder fullResponse, StringBuilder reasoningContent, boolean enableWebSearch) {
        try {
            String apiKey = deepseekApiKey;
            if (apiKey == null || apiKey.isEmpty()) {
                apiKey = System.getenv("DEEPSEEK_API_KEY");
            }
            if (apiKey == null || apiKey.isEmpty()) {
                throw new RuntimeException("未配置DeepSeek API密钥, 请设置环境变量 DEEPSEEK_API_KEY");
            }

            // 根据是否需要联网搜索选择API端点和格式
            boolean useWebSearch = deepseekWebSearchEnabled && enableWebSearch;

            if (useWebSearch) {
                streamChatWithWebSearch(emitter, messages, fullResponse, reasoningContent, apiKey);
            } else {
                streamChatStandard(emitter, messages, fullResponse, reasoningContent, apiKey);
            }
        } catch (Exception e) {
            log.error("DeepSeek流式对话失败: {}", e.getMessage());
            throw new RuntimeException("流式对话失败: " + e.getMessage());
        }
    }

    /**
     * 标准Chat Completions API调用（无联网搜索）
     */
    private void streamChatStandard(SseEmitter emitter, List<Map<String, Object>> messages,
                                     StringBuilder fullResponse, StringBuilder reasoningContent, String apiKey) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("model", deepseekModel);
        body.put("max_tokens", 8192);
        body.put("stream", true);

        if (deepseekThinkingEnabled) {
            Map<String, Object> thinking = new HashMap<>();
            thinking.put("type", "enabled");
            body.put("thinking", thinking);
            body.put("reasoning_effort", deepseekReasoningEffort);
        } else {
            body.put("temperature", 0.7);
        }

        body.put("messages", messages);

        String endpointUrl = buildEndpointUrl(deepseekBaseUrl, "/chat/completions");
        HttpURLConnection conn = createConnection(endpointUrl, apiKey, "Bearer");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8));
        }

        checkResponseCode(conn);
        parseOpenAISSEStream(emitter, conn, fullResponse, reasoningContent);
    }

    /**
     * Anthropic兼容端点调用（支持联网搜索）
     * 
     * DeepSeek Anthropic兼容端点: https://api.deepseek.com/anthropic
     * 使用web_search_20250305工具实现联网搜索
     * 
     * Anthropic SSE事件流:
     *   event: message_start       → 消息开始
     *   event: content_block_start → 内容块开始（thinking/text/web_search_tool）
     *   event: content_block_delta → 内容增量（thinking_delta/text_delta）
     *   event: content_block_stop  → 内容块结束
     *   event: message_stop        → 消息结束
     */
    private void streamChatWithWebSearch(SseEmitter emitter, List<Map<String, Object>> openAIMessages,
                                          StringBuilder fullResponse, StringBuilder reasoningContent, String apiKey) throws Exception {
        log.info("使用DeepSeek Anthropic兼容端点（联网搜索模式）");

        // 转换消息格式：OpenAI → Anthropic
        List<Map<String, Object>> anthropicMessages = convertToAnthropicMessages(openAIMessages);

        // 构建Anthropic请求体
        Map<String, Object> body = new HashMap<>();
        body.put("model", deepseekModel);
        body.put("max_tokens", 8192);
        body.put("stream", true);

        // Anthropic格式：system是顶层参数
        // 从messages中提取system消息
        String systemPrompt = null;
        List<Map<String, Object>> chatMessages = new ArrayList<>();
        for (Map<String, Object> msg : anthropicMessages) {
            if ("system".equals(msg.get("role"))) {
                systemPrompt = (String) msg.get("content");
            } else {
                chatMessages.add(msg);
            }
        }
        if (systemPrompt != null) {
            body.put("system", systemPrompt);
        }
        body.put("messages", chatMessages);

        // 思考模式
        if (deepseekThinkingEnabled) {
            Map<String, Object> thinking = new HashMap<>();
            thinking.put("type", "enabled");
            thinking.put("budget_tokens", 8192);
            body.put("thinking", thinking);
        }

        // 联网搜索工具
        List<Map<String, Object>> tools = new ArrayList<>();
        Map<String, Object> webSearchTool = new HashMap<>();
        webSearchTool.put("type", "web_search_20250305");
        webSearchTool.put("name", "web_search");
        webSearchTool.put("max_uses", 3);
        tools.add(webSearchTool);
        body.put("tools", tools);

        // 请求Anthropic兼容端点
        String endpointUrl = buildEndpointUrl(deepseekBaseUrl, "/anthropic/v1/messages");
        HttpURLConnection conn = createConnection(endpointUrl, apiKey, "Bearer");

        // Anthropic额外Header
        conn.setRequestProperty("anthropic-version", "2023-06-01");
        conn.setRequestProperty("anthropic-beta", "web-search-2025-03-05");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8));
        }

        checkResponseCode(conn);
        parseAnthropicSSEStream(emitter, conn, fullResponse, reasoningContent);
    }

    /**
     * 转换消息格式：OpenAI → Anthropic
     * OpenAI: {"role":"user","content":"text"}
     * Anthropic: {"role":"user","content":"text"} (基本相同)
     * 
     * 特殊处理：
     * - assistant消息中的reasoning_content：Anthropic不支持此字段，移除
     * - tool消息：暂不处理
     */
    private List<Map<String, Object>> convertToAnthropicMessages(List<Map<String, Object>> openAIMessages) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> msg : openAIMessages) {
            Map<String, Object> converted = new HashMap<>(msg);
            // 移除reasoning_content（Anthropic格式不支持在消息中传此字段）
            converted.remove("reasoning_content");
            result.add(converted);
        }
        return result;
    }

    /**
     * 构建API端点URL
     */
    private String buildEndpointUrl(String baseUrl, String path) {
        String url = baseUrl;
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        // 移除已有的路径后缀（如/chat/completions）
        if (url.endsWith("/chat/completions")) {
            url = url.substring(0, url.length() - "/chat/completions".length());
        }
        if (url.endsWith("/anthropic/v1/messages")) {
            url = url.substring(0, url.length() - "/anthropic/v1/messages".length());
        }
        return url + path;
    }

    /**
     * 创建HTTP连接
     */
    private HttpURLConnection createConnection(String url, String apiKey, String authType) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", authType + " " + apiKey);
        conn.setDoOutput(true);
        conn.setConnectTimeout(60000);
        conn.setReadTimeout(600000);
        return conn;
    }

    /**
     * 检查HTTP响应码
     */
    private void checkResponseCode(HttpURLConnection conn) throws Exception {
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
            throw new RuntimeException("DeepSeek API返回错误 " + responseCode + ": " + errorBody);
        }
    }

    /**
     * 解析OpenAI格式的SSE流（Chat Completions API）
     * 
     * SSE格式:
     *   data: {"choices":[{"delta":{"reasoning_content":"..."}}]}
     *   data: {"choices":[{"delta":{"content":"..."}}]}
     *   data: [DONE]
     */
    private void parseOpenAISSEStream(SseEmitter emitter, HttpURLConnection conn,
                                       StringBuilder fullResponse, StringBuilder reasoningContent) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data:")) {
                    String data = line.substring(5).trim();
                    if (data.isEmpty()) continue;

                    if ("[DONE]".equals(data)) {
                        sendDoneEvent(emitter, reasoningContent);
                        return;
                    }

                    try {
                        JsonNode node = objectMapper.readTree(data);
                        JsonNode choices = node.path("choices");
                        if (choices.isArray() && choices.size() > 0) {
                            JsonNode choice = choices.get(0);
                            JsonNode delta = choice.path("delta");
                            String finishReason = choice.path("finish_reason").asText("");

                            // 思维链内容
                            if (delta.has("reasoning_content") && !delta.path("reasoning_content").isNull()) {
                                String reasoning = delta.path("reasoning_content").asText("");
                                if (!reasoning.isEmpty()) {
                                    reasoningContent.append(reasoning);
                                    emitter.send(SseEmitter.event().name("message").data(
                                            objectMapper.writeValueAsString(Map.of(
                                                    "type", "reasoning_delta",
                                                    "content", reasoning
                                            ))
                                    ));
                                }
                            }

                            // 最终回答内容
                            if (delta.has("content") && !delta.path("content").isNull()) {
                                String content = delta.path("content").asText("");
                                if (!content.isEmpty()) {
                                    fullResponse.append(content);
                                    emitter.send(SseEmitter.event().name("message").data(
                                            objectMapper.writeValueAsString(Map.of(
                                                    "type", "content",
                                                    "content", content
                                            ))
                                    ));
                                }
                            }

                            if (!finishReason.isEmpty() && !"null".equals(finishReason)) {
                                log.info("DeepSeek完成原因: {}, 累计输出字符数: {}", finishReason, fullResponse.length());
                            }
                        }
                    } catch (Exception parseEx) {
                        if (parseEx instanceof RuntimeException) throw parseEx;
                        log.debug("解析SSE行失败: {}", data);
                    }
                }
            }
        }
        // 如果没有收到[DONE]但流正常结束
        sendDoneEvent(emitter, reasoningContent);
    }

    /**
     * 解析Anthropic格式的SSE流（联网搜索模式）
     * 
     * Anthropic SSE事件流:
     *   event: message_start
     *     data: {"type":"message_start","message":{"id":"...","role":"assistant",...}}
     *   event: content_block_start
     *     data: {"type":"content_block_start","index":0,"content_block":{"type":"thinking",...}}
     *   event: content_block_start
     *     data: {"type":"content_block_start","index":1,"content_block":{"type":"text",...}}
     *   event: content_block_start
     *     data: {"type":"content_block_start","index":2,"content_block":{"type":"web_search_tool_result",...}}
     *   event: content_block_delta
     *     data: {"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"..."}}
     *   event: content_block_delta
     *     data: {"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"..."}}
     *   event: content_block_stop
     *   event: message_stop
     */
    private void parseAnthropicSSEStream(SseEmitter emitter, HttpURLConnection conn,
                                          StringBuilder fullResponse, StringBuilder reasoningContent) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            String currentEvent = null;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    currentEvent = null;
                    continue;
                }

                // 事件类型
                if (line.startsWith("event:")) {
                    currentEvent = line.substring(6).trim();
                    continue;
                }

                // 事件数据
                if (line.startsWith("data:")) {
                    String data = line.substring(5).trim();
                    if (data.isEmpty()) continue;

                    try {
                        JsonNode node = objectMapper.readTree(data);
                        String type = node.path("type").asText("");

                        switch (type) {
                            case "content_block_start": {
                                JsonNode contentBlock = node.path("content_block");
                                String blockType = contentBlock.path("type").asText("");
                                if ("web_search_tool_result".equals(blockType)) {
                                    // 联网搜索结果，通知前端
                                    emitter.send(SseEmitter.event().name("message").data(
                                            objectMapper.writeValueAsString(Map.of(
                                                    "type", "web_search_result",
                                                    "content", "正在检索互联网信息..."
                                            ))
                                    ));
                                }
                                break;
                            }

                            case "content_block_delta": {
                                int index = node.path("index").asInt();
                                JsonNode delta = node.path("delta");
                                String deltaType = delta.path("type").asText("");

                                switch (deltaType) {
                                    case "thinking_delta": {
                                        // 思维链增量
                                        String thinking = delta.path("thinking").asText("");
                                        if (!thinking.isEmpty()) {
                                            reasoningContent.append(thinking);
                                            emitter.send(SseEmitter.event().name("message").data(
                                                    objectMapper.writeValueAsString(Map.of(
                                                            "type", "reasoning_delta",
                                                            "content", thinking
                                                    ))
                                            ));
                                        }
                                        break;
                                    }

                                    case "text_delta": {
                                        // 回答文本增量
                                        String text = delta.path("text").asText("");
                                        if (!text.isEmpty()) {
                                            fullResponse.append(text);
                                            emitter.send(SseEmitter.event().name("message").data(
                                                    objectMapper.writeValueAsString(Map.of(
                                                            "type", "content",
                                                            "content", text
                                                    ))
                                            ));
                                        }
                                        break;
                                    }

                                    default:
                                        log.debug("未处理的Anthropic delta类型: {}", deltaType);
                                }
                                break;
                            }

                            case "message_stop": {
                                // 消息结束
                                sendDoneEvent(emitter, reasoningContent);
                                return;
                            }

                            case "message_start":
                            case "content_block_stop":
                            case "ping":
                                // 忽略这些事件
                                break;

                            default:
                                log.debug("未处理的Anthropic事件类型: {}", type);
                        }
                    } catch (Exception parseEx) {
                        if (parseEx instanceof RuntimeException) throw parseEx;
                        log.debug("解析Anthropic SSE行失败: {}", data);
                    }
                }
            }
        }
        // 如果没有收到message_stop但流正常结束
        sendDoneEvent(emitter, reasoningContent);
    }

    /**
     * 发送完成事件（思维链汇总 + done标记）
     */
    private void sendDoneEvent(SseEmitter emitter, StringBuilder reasoningContent) throws Exception {
        if (reasoningContent.length() > 0) {
            emitter.send(SseEmitter.event().name("message").data(
                    objectMapper.writeValueAsString(Map.of(
                            "type", "reasoning",
                            "content", reasoningContent.toString()
                    ))
            ));
        }
        emitter.send(SseEmitter.event().name("message").data(
                objectMapper.writeValueAsString(Map.of("type", "done"))
        ));
    }

    /**
     * 调用MiniMax Embedding API获取文本向量
     */
    private float[] getEmbedding(String text) {
        try {
            String url = minimaxEmbeddingUrl;
            if (url == null || url.isEmpty()) {
                url = "https://api.minimax.chat/v1/embeddings";
            }
            String model = minimaxEmbeddingModel;
            if (model == null || model.isEmpty()) {
                model = "embo-01";
            }

            java.net.URL apiUrl = new java.net.URL(url);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) apiUrl.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + minimaxApiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);

            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "input", List.of(text),
                    "type", "db"
            ));

            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(requestBody.getBytes("UTF-8"));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                String errorBody = new String(conn.getErrorStream().readAllBytes(), "UTF-8");
                log.error("Embedding API调用失败, status={}, body={}", responseCode, errorBody);
                return null;
            }

            String responseBody = new String(conn.getInputStream().readAllBytes(), "UTF-8");
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(responseBody);
            com.fasterxml.jackson.databind.JsonNode dataNode = root.get("data");
            if (dataNode != null && dataNode.isArray() && dataNode.size() > 0) {
                com.fasterxml.jackson.databind.JsonNode embeddingNode = dataNode.get(0).get("embedding");
                if (embeddingNode != null && embeddingNode.isArray()) {
                    float[] embedding = new float[embeddingNode.size()];
                    for (int i = 0; i < embeddingNode.size(); i++) {
                        embedding[i] = (float) embeddingNode.get(i).asDouble();
                    }
                    return embedding;
                }
            }
            log.error("Embedding API返回数据格式异常: {}", responseBody.substring(0, Math.min(responseBody.length(), 200)));
            return null;
        } catch (Exception e) {
            log.error("获取Embedding失败: {}", e.getMessage());
            return null;
        }
    }
}
