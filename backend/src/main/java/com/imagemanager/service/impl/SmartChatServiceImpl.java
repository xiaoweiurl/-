package com.imagemanager.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imagemanager.dto.MemorySearchResult;
import com.imagemanager.enhance.ChatMemoryManager;
import com.imagemanager.service.KnowledgeBaseService;
import com.imagemanager.service.SmartChatService;
import com.imagemanager.service.FileStorageService;
import com.imagemanager.util.KeywordExtractor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.*;
import java.math.BigDecimal;

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能对话服务实现 - 知识库检索 + Ollama Qwen3.6流式对话
 *
 * 检索流程:
 * 1. 知识库检索: PostgreSQL向量搜索(KnowledgeBaseService.search)
 * 2. 调Ollama本地模型流式对话(自托管)
 */
@Slf4j
@Service
public class SmartChatServiceImpl implements SmartChatService {

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired(required = false)
    private com.imagemanager.tools.SupplyChainAssistant supplyChainAssistant;

    @Autowired(required = false)
    private com.imagemanager.tools.SupplyChainTools supplyChainTools;

    @Autowired(required = false)
    private com.imagemanager.service.QuotationCalcService quotationCalcService;

    @Autowired(required = false)
    private com.imagemanager.service.MilvusService milvusService;

    @Autowired(required = false)
    private com.imagemanager.service.DecisionDataService decisionDataService;

    @Autowired(required = false)
    private com.imagemanager.enhance.RagPipeline ragPipeline;

    @Autowired(required = false)
    private com.imagemanager.enhance.ChatMemoryManager chatMemoryManager;

    @Autowired(required = false)
    private com.imagemanager.cache.LlmCacheService llmCacheService;

    @Autowired(required = false)
    private com.imagemanager.service.AiCallLogService aiCallLogService;

    @Value("${app.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${app.ollama.embedding-model:bge-m3}")
    private String ollamaEmbeddingModel;

    @Value("${app.ollama.chat-model:qwen3.6:35b}")
    private String ollamaChatModel;

    @Value("${app.ollama.timeout:60000}")
    private int ollamaTimeout;

    /** MiniMax API（Anthropic兼容端点）：用于阶段一联网搜索（仅传用户原始问题，内部数据隔离） */
    @Value("${app.minimax.api-key:}")
    private String minimaxApiKey;

    @Value("${app.minimax.base-url:https://api.minimaxi.com}")
    private String minimaxBaseUrl;

    @Value("${app.minimax.model:MiniMax-M3}")
    private String minimaxModel;

    /** 联网搜索专用模型，为空时回落 minimax.model */
    @Value("${app.minimax.web-search-model:}")
    private String minimaxWebSearchModel;

    /** 业务子模式会话记忆：convId -> "planning"(模式A商品企划) / "decision"(模式B总经理决策辅助) */
    private final java.util.concurrent.ConcurrentHashMap<String, String> businessSubModeMap = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public SseEmitter smartChat(String message, String userId, String company, String conversationId, String mode) {
        return smartChatWithImages(message, userId, company, conversationId, mode, null);
    }

    @Override
    public SseEmitter smartChat(String message, String userId, String company, String conversationId, String mode, String subMode) {
        return smartChatWithAttachments(message, userId, company, conversationId, mode, null, null, subMode);
    }

    @Override
    public SseEmitter smartChatWithImages(String message, String userId, String company, String conversationId, String mode, List<String> userImages) {
        return smartChatWithAttachments(message, userId, company, conversationId, mode, userImages, null);
    }

    @Override
    public SseEmitter smartChatWithAttachments(String message, String userId, String company, String conversationId, String mode, List<String> userImages, List<Map<String, String>> userPdfs) {
        return smartChatWithAttachments(message, userId, company, conversationId, mode, userImages, userPdfs, null);
    }

    @Override
    public SseEmitter smartChatWithAttachments(String message, String userId, String company, String conversationId, String mode, List<String> userImages, List<Map<String, String>> userPdfs, String subMode) {
        log.info("智能对话: message='{}', userId='{}', company='{}', conversationId='{}', mode='{}', subMode='{}', hasImages={}, hasPdfs={}",
                message, userId, company, conversationId, mode, subMode,
                userImages != null && !userImages.isEmpty(),
                userPdfs != null && !userPdfs.isEmpty());
        SseEmitter emitter = new SseEmitter(600000L); // 10分钟超时

        new Thread(() -> {
            try {
                // 0. 确定对话ID：如果未传则获取或创建
                String convId = conversationId;
                if (convId == null || convId.isEmpty()) {
                    convId = getOrCreateDefaultConversation(userId, company, mode);
                }

                // 1. 发送conversationId给前端
                final String finalConvId = convId;
                emitter.send(SseEmitter.event().name("conversation").data(finalConvId));

                // 1a. 显式 subMode（前端智能体按钮）优先级最高，直接写入会话记忆
                if (subMode != null && ("planning".equals(subMode) || "decision".equals(subMode) || "general".equals(subMode))) {
                    if ("general".equals(subMode)) {
                        businessSubModeMap.remove(finalConvId);
                    } else {
                        businessSubModeMap.put(finalConvId, subMode);
                    }
                    log.info("业务子模式显式指定: convId={}, subMode={}", finalConvId, subMode);
                }

                // 2. 加载历史对话（按conversationId），优先用ChatMemory缓存，无缓存时查DB
                List<Map<String, Object>> history;
                List<ChatMemoryManager.ChatMessage> memoryMsgs = chatMemoryManager.getMessages(convId);
                if (!memoryMsgs.isEmpty()) {
                    history = new ArrayList<>();
                    for (ChatMemoryManager.ChatMessage msg : memoryMsgs) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("role", msg.role);
                        m.put("content", msg.content);
                        history.add(m);
                    }
                } else {
                    history = getChatHistory(userId, company, convId);
                    // 回填到内存缓存
                    for (Map<String, Object> msg : history) {
                        String role = (String) msg.get("role");
                        String content = (String) msg.get("content");
                        if (role != null && content != null) {
                            if ("user".equals(role)) {
                                chatMemoryManager.addUserMessage(convId, content);
                            } else {
                                chatMemoryManager.addAssistantMessage(convId, content);
                            }
                        }
                    }
                }

                // 2. 意图识别：供应链关键词意图仅在工厂模式下生效（只用于"宽泛模糊检索"的兜底开关）
                // 精确报价检索不依赖关键词，改为"实体驱动"（见步骤3），与提问方式无关
                boolean supplyChainIntent = "factory".equals(mode) && isSupplyChainIntent(message);
                boolean hasProductCode = "factory".equals(mode) && extractProductCode(message) != null;
                // 当用户提到具体产品编码+供应链意图时，认为是"强供应链意图"
                boolean strongSupplyChainIntent = supplyChainIntent && hasProductCode;

                // 岗位意图识别
                boolean positionIntent = isPositionIntent(message);

                // 通用闲聊意图识别：当用户问的是闲聊/身份/通用常识类问题时，跳过所有知识库检索
                boolean generalChatIntent = isGeneralChatIntent(message);
                
                // 关键覆盖逻辑1：工厂模式下任何问题都是业务问题，不应归为闲聊
                // 工厂模式下用户问"帮我计算成本"、"机型产量是多少"等都应触发知识库检索
                if (generalChatIntent && "factory".equals(mode)) {
                    generalChatIntent = false;
                    log.info("闲聊意图被工厂模式覆盖: mode={}", mode);
                }
                
                // 关键覆盖逻辑2：当已识别到供应链意图或岗位意图时，即使关键词也命中闲聊模式，
                // 也应优先按业务意图处理（如"帮我计算成本"包含"成本"→供应链意图优先）
                if (generalChatIntent && (supplyChainIntent || positionIntent)) {
                    generalChatIntent = false;
                    log.info("闲聊意图被业务意图覆盖: supplyChainIntent={}, positionIntent={}", supplyChainIntent, positionIntent);
                }

                // 工厂模式判断（用于后续多处逻辑分支）
                boolean isFactory = "factory".equals(mode);

                // 模式切换指令：纯指令性输入（"切换商品企划模式"/"切换总经理决策辅助模式"），
                // 与业务数据无关，跳过所有检索（供应链/知识库/Milvus/岗位卡片），
                // 避免低分不相关切片注入上下文引发幻觉并拖慢响应
                boolean modeSwitchCmd = detectSubModeSwitch(message) != null;
                if (modeSwitchCmd) {
                    log.info("识别为模式切换指令，跳过所有数据检索: message='{}'", message);
                }

                // 提前解析业务子模式（检索阶段即需：决定是否注入排产/客户订单等结构化决策数据；
                // 后续构建 systemPrompt 时复用本变量，避免重复解析）
                String resolvedSubMode = isFactory ? resolveBusinessSubMode(finalConvId, message) : null;

                // 联网搜索意图识别：仅【企划智能体（factory 模式·子模式 planning）】启用联网搜索；
                // 其他模式（总经理决策/通用业务/设计师等）一律不联网、也不做联网意图判断
                boolean webSearchIntent = "planning".equals(resolvedSubMode) && isWebSearchIntent(message);

                // 企划/市场调研意图识别：品牌+品类+渠道+定位+季节等组合问题（如"宝娜斯 丝袜 中国 抖音电商 中高端 2026秋冬"）
                // 仅在企划子模式下自动触发联网搜索获取最新市场动态，无需用户明确说"联网/全网搜索"；网络数据仅作参考
                boolean planningResearchIntent = "planning".equals(resolvedSubMode) && isPlanningResearchIntent(message);

                // 外部知识意图识别：当问题需要外部/通用知识时，跳过知识库检索直接联网搜索
                boolean externalKnowledgeIntent = !isFactory && isExternalKnowledgeIntent(message);

                log.info("意图识别: mode={}, isFactory={}, generalChatIntent={}, webSearchIntent={}, planningResearchIntent={}, externalKnowledgeIntent={}, supplyChainIntent={}, positionIntent={}",
                        mode, isFactory, generalChatIntent, webSearchIntent, planningResearchIntent, externalKnowledgeIntent, supplyChainIntent, positionIntent);

                // ===== 企划主题切换守卫：防止同一会话多个品牌/品类企划的上下文相互污染 =====
                // 场景: 用户先问"阿迪达斯 内衣"并深入几轮(企划进行中未终稿), 又提出"宝娜斯 保暖袜"——
                // 历史中的旧主题调研数据/参数/结论会混入新主题生成, 导致输出混乱与幻觉。
                // 守卫规则:
                //   ①新主题+企划进行中(上轮助手仍输出操作菜单即未终稿)+用户未确认切换 → 拦截:
                //     不走检索/LLM, 直接提示"先把当前企划跑完(回复'生成终稿')或回复'开始新企划'确认放弃并切换";
                //   ②用户回复"开始新企划"(裸指令, 不含主题) → 自动接管被拦截的新主题:
                //     生成改写消息供联网检索与LLM使用, 并注入【上下文隔离声明】;
                //   ③已终稿后提新主题 / 消息自带确认词(如"新企划 宝娜斯 保暖袜") → 不拦截, 仅注入隔离声明
                String planningIsolationNotice = "";
                String planningOverrideMessage = null;
                if ("planning".equals(resolvedSubMode) && history != null && !history.isEmpty()) {
                    String curBrand = extractBrandName(message);
                    String curCats = extractCategoryWords(message);
                    String curTopic = (!curBrand.isEmpty() && !curCats.isEmpty()) ? curBrand + "×" + curCats : null;
                    String lastTopic = findLastPlanningTopic(history);
                    boolean inProgress = isPlanningInProgress(history);
                    boolean confirmNew = isNewPlanningConfirm(message);
                    if (curTopic != null && lastTopic != null && !lastTopic.equals(curTopic)) {
                        if (inProgress && !confirmNew) {
                            // ①拦截: 提示先跑完当前企划, 或确认放弃切换(不走检索/LLM)
                            String guardPrompt = "⚠️ **检测到企划主题切换请求**\n\n"
                                    + "当前进行中的企划：**" + lastTopic + "**（尚未生成终稿）\n"
                                    + "你新提出的主题：**" + curTopic + "**\n\n"
                                    + "为避免两个主题的调研数据、产品参数、成本数字相互混淆（历史对话中的旧主题数据会污染新主题的结论），请先二选一：\n\n"
                                    + "**1. 继续当前企划** —— 回复菜单编号继续深入，或回复「生成终稿」直接输出《" + lastTopic + "》完整企划案\n"
                                    + "**2. 放弃并开启新主题** —— 回复「开始新企划」，我将清空旧主题上下文，围绕 **" + curTopic + "** 从零启动全新企划";
                            // 保存被拦截的用户消息与守卫提示到历史(供"开始新企划"裸指令接管时回溯新主题)
                            saveChatMessage(userId, finalConvId, "user", message, company, null, mode);
                            chatMemoryManager.addUserMessage(finalConvId, message);
                            saveChatMessage(userId, finalConvId, "assistant", guardPrompt, company, null, mode);
                            chatMemoryManager.addAssistantMessage(finalConvId, guardPrompt);
                            emitter.send(SseEmitter.event().name("message").data(
                                    objectMapper.writeValueAsString(Map.of("type", "content", "content", guardPrompt))));
                            emitter.send(SseEmitter.event().name("message").data(
                                    objectMapper.writeValueAsString(Map.of("type", "done"))));
                            emitter.complete();
                            log.info("[planning-guard] 拦截企划主题切换: 进行中主题={} → 新主题={}, 已提示先跑完当前企划或回复'开始新企划'确认切换",
                                    lastTopic, curTopic);
                            return;
                        }
                        // ③不拦截(已终稿或消息自带确认词): 注入隔离声明后继续
                        planningIsolationNotice = buildPlanningIsolationNotice(lastTopic, curTopic);
                        log.info("[planning-guard] 企划主题切换(不拦截): {} → {}, inProgress={}, confirmNew={}, 已注入上下文隔离声明",
                                lastTopic, curTopic, inProgress, confirmNew);
                    } else if (curTopic == null && confirmNew && lastTopic != null) {
                        // ②裸指令"开始新企划": 接管被拦截的新主题——改写消息供联网检索与LLM使用
                        planningOverrideMessage = "开始全新企划：" + lastTopic.replace("×", " ")
                                + "。说明：用户已确认放弃之前的企划主题，本主题为全新独立企划，请围绕本主题从零开始。";
                        planningIsolationNotice = buildPlanningIsolationNotice(null, lastTopic);
                        // 改写后的消息含品牌+品类, 重新判定联网意图, 确保触发新主题官网参数采集
                        planningResearchIntent = isPlanningResearchIntent(planningOverrideMessage);
                        webSearchIntent = isWebSearchIntent(planningOverrideMessage);
                        log.info("[planning-guard] '开始新企划'裸指令接管被拦截主题: {}, 已生成改写消息并注入隔离声明, planningResearchIntent={}",
                                lastTopic, planningResearchIntent);
                    }
                }

                // 3. 供应链/工厂数据检索
                // 设计原则【实体驱动，而非关键词驱动】：
                //   - 精确报价检索只判断"问题中是否包含库里真实存在的实体"（报价单号/客户名称），
                //     与提问方式无关——"海宁世正有多少单号/海宁世正的订单/查下海宁世正/20250625-001S" 都能命中；
                //   - 宽泛模糊检索仍由关键词意图兜底，防止无关数据导致幻觉。
                List<Map<String, Object>> supplyChainResults = Collections.emptyList();
                if (isFactory && !generalChatIntent && !modeSwitchCmd) {
                    try {
                        List<Map<String, Object>> precise = (quotationCalcService != null)
                                ? searchQuotation(message) : Collections.emptyList();
                        boolean otherIntent = isSchedulingIntent(message) || isPartsIntent(message) || isMaterialIntent(message);
                        if (!precise.isEmpty()) {
                            supplyChainResults = new ArrayList<>(precise);
                            log.info("报价维度实体命中(实体驱动), 条数={}", precise.size());
                            // 问题同时涉及其他维度时再并入宽泛检索
                            if (otherIntent || supplyChainIntent) {
                                supplyChainResults.addAll(searchSupplyChain(message, company, userId));
                            }
                        } else if (supplyChainIntent || otherIntent) {
                            supplyChainResults = searchSupplyChain(message, company, userId);
                        }
                        // 终极兜底【function-calling】：实体/关键词都未命中时，交给工具调用模型自己决定调用哪个工具，
                        // 支持"哪个客户单号最多/所有客户报价汇总/一共多少单"等不含实体的任意问法
                        if (supplyChainResults.isEmpty() && supplyChainAssistant != null) {
                            try {
                                String analysis = supplyChainAssistant.chat(message, company);
                                if (analysis != null && !analysis.isBlank()) {
                                    Map<String, Object> entry = new LinkedHashMap<>();
                                    entry.put("type", "供应链AI工具分析");
                                    entry.put("summary", "AI 通过 function-calling 调用统计/查询工具得出的分析");
                                    Map<String, Object> data = new LinkedHashMap<>();
                                    data.put("分析结论", analysis);
                                    entry.put("data", data);
                                    supplyChainResults = new ArrayList<>();
                                    supplyChainResults.add(entry);
                                    log.info("function-calling 兜底分析完成, 长度={}", analysis.length());
                                }
                            } catch (Exception ex) {
                                log.warn("function-calling 兜底分析异常: {}", ex.getMessage());
                            }
                        }
                        log.info("供应链数据检索到 {} 条结果", supplyChainResults.size());
                    } catch (Exception e) {
                        log.warn("供应链数据检索异常: {}", e.getMessage());
                    }
                }

                // 3b. 结构化决策数据确定性注入（排产产能 / 客户订单维度 / 业务员绩效缺失说明）
                // 与报价单确定性查询同思路：产能数字、客户订单统计全部由参数化 SQL 产出，
                // 使"产能与订单匹配""动态客户经营"板块有确定性数据底座，不流于形式
                List<Map<String, Object>> structuredResults = Collections.emptyList();
                if (isFactory && !generalChatIntent && !modeSwitchCmd && decisionDataService != null) {
                    try {
                        structuredResults = decisionDataService.searchStructuredForMessage(message, resolvedSubMode);
                        if (!structuredResults.isEmpty()) {
                            log.info("结构化决策数据注入 {} 条 (subMode={})", structuredResults.size(), resolvedSubMode);
                        }
                    } catch (Exception e) {
                        log.warn("结构化决策数据检索异常: {}", e.getMessage());
                    }
                }

                // 4. 双库检索（工厂模式也检索知识库向量文档，不检索岗位卡片/外部知识）
                // 外部知识意图不再跳过向量检索：用户可能上传了相关PDF，先查知识库，知识库无结果时再走联网搜索
                // 模式切换指令直接跳过：指令与业务数据无关
                boolean skipVectorSearch = (strongSupplyChainIntent && !supplyChainResults.isEmpty()) || generalChatIntent || modeSwitchCmd;

                // 4a. 岗位卡片向量检索（仅设计师模式）
                List<Map<String, Object>> positionCardResults = Collections.emptyList();
                if (!skipVectorSearch) {
                    try {
                        positionCardResults = searchPositionCards(message, company);
                        log.info("岗位卡片检索到 {} 条结果", positionCardResults.size());
                    } catch (Exception e) {
                        log.warn("岗位卡片检索异常: {}", e.getMessage());
                    }
                } else {
                    log.info("跳过岗位卡片检索: isFactory={}, generalChatIntent={}, strongSupplyChain={}", isFactory, generalChatIntent, strongSupplyChainIntent && !supplyChainResults.isEmpty());
                }

                // 当岗位意图且岗位卡片有结果时，或通用闲聊意图时，跳过知识库PDF检索
                boolean skipKnowledgeSearch = skipVectorSearch || (positionIntent && !positionCardResults.isEmpty());

                // 4a-2. RAG检索历史对话Q&A对（仅设计师模式，通用闲聊时跳过）
                List<Map<String, Object>> chatHistoryQAResults = Collections.emptyList();
                if (!skipVectorSearch && !generalChatIntent) {
                    try {
                        chatHistoryQAResults = searchChatHistoryQA(message, company);
                        log.info("历史对话QA检索到 {} 条结果", chatHistoryQAResults.size());
                    } catch (Exception e) {
                        log.warn("历史对话QA检索异常: {}", e.getMessage());
                    }
                }

                // 4b. 知识库检索
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
                    log.info("跳过知识库检索（原因: {}）", reason);
                }

                // 4c. 业务员资料库 Milvus 向量检索（工厂模式核心上下文，与供应链精确数据互补）
                List<Map<String, Object>> salespersonResults = Collections.emptyList();
                if (isFactory && !generalChatIntent && !modeSwitchCmd) {
                    try {
                        salespersonResults = searchSalespersonKnowledge(message);
                        log.info("业务员资料 Milvus 检索到 {} 条结果", salespersonResults.size());
                    } catch (Exception e) {
                        log.warn("业务员资料检索异常: {}", e.getMessage());
                    }
                }

                // 4d. 图片搜索(当用户意图涉及找图时)
                List<Map<String, Object>> imageResults = Collections.emptyList();
                if (!modeSwitchCmd && isImageSearchIntent(message)) {
                    try {
                        imageResults = searchImages(message, userId);
                        log.info("图片搜索匹配到 {} 条结果", imageResults.size());
                    } catch (Exception e) {
                        log.warn("图片搜索异常: {}", e.getMessage());
                    }
                }

                // 3. 发送来源信息
                List<Map<String, Object>> sources = new ArrayList<>();

                // 知识库来源（设计师和工厂模式都可用）
                for (Map<String, Object> r : knowledgeResults) {
                    sources.add(Map.of(
                            "source", "knowledge",
                            "content", r.getOrDefault("content", "").toString(),
                            "score", r.getOrDefault("score", 0)
                    ));
                }

                // 业务员资料来源（工厂模式）
                for (Map<String, Object> r : salespersonResults) {
                    sources.add(Map.of(
                            "source", "salesperson_kb",
                            "content", r.getOrDefault("content", "").toString(),
                            "fileName", r.getOrDefault("fileName", "").toString(),
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

                // 岗位卡片来源（仅设计师模式）
                if (!isFactory) {
                    for (Map<String, Object> r : positionCardResults) {
                        sources.add(Map.of(
                                "source", "position_card",
                                "content", r.getOrDefault("content", "").toString(),
                                "score", r.getOrDefault("score", 0)
                        ));
                    }
                }

                // 历史对话QA来源（仅设计师模式）
                if (!isFactory) {
                    for (Map<String, Object> r : chatHistoryQAResults) {
                        sources.add(Map.of(
                                "source", "chat_history",
                                "content", r.getOrDefault("content", "").toString(),
                                "score", r.getOrDefault("similarity", 0)
                        ));
                    }
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

                // 3c. 发送报价单全量明细(结构化事件, 前端渲染全量表格, 零省略)
                for (Map<String, Object> r : supplyChainResults) {
                    if ("报价单统计".equals(r.get("type"))) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> statData = (Map<String, Object>) r.get("data");
                        if (statData != null && quotationCalcService != null) {
                            String customer = String.valueOf(statData.getOrDefault("客户名称", ""));
                            List<Map<String, Object>> rows = quotationCalcService.queryByKhnameAll(customer);
                            // 循环外批量预取工艺单缝拼克重（一次 IN 查询，消除逐行查库的 N+1）
                            Map<String, BigDecimal> fpkzBatch = quotationCalcService.batchLookupProcessSewingWeights(
                                    rows.stream().map(qrow -> String.valueOf(qrow.getOrDefault("huohao", ""))).toList());
                            List<Map<String, Object>> orders = new ArrayList<>();
                            for (Map<String, Object> row : rows) {
                                Map<String, Object> o = new LinkedHashMap<>();
                                o.put("dh", row.get("dh"));
                                o.put("chima", row.get("chima"));
                                o.put("huohao", row.get("huohao"));
                                o.put("sbdj", row.get("sbdj"));
                                o.put("lyl", row.get("lyl"));
                                o.put("zpl", row.get("zpl"));
                                // 上游新增字段：缝拼克重/染色单价/腰口工价/全检工价（原始值透传）
                                o.put("fpkz", row.get("fpkz"));
                                o.put("rsdj", row.get("rsdj"));
                                o.put("ykgj", row.get("ykgj"));
                                o.put("qjprice", row.get("qjprice"));
                                try {
                                    Map<String, BigDecimal> extra = new HashMap<>();
                                    BigDecimal prefetched = fpkzBatch.get(String.valueOf(row.getOrDefault("huohao", "")));
                                    if (prefetched != null) extra.put("fpkzFallback", prefetched);
                                    Map<String, BigDecimal> calc = quotationCalcService.calculate(row, extra);
                                    o.put("rcl", calc.get("rcl_日产量"));
                                    o.put("zzcb", calc.get("zzcb_织造成本"));
                                    o.put("rs", calc.get("染色成本"));
                                    o.put("yl", calc.get("sumprice_原料金额"));
                                    o.put("qd", calc.get("countprice_前道合计"));
                                    o.put("fl", calc.get("flsum_辅料金额"));
                                    o.put("hd", calc.get("hdprice_后道合计"));
                                    o.put("jcb", calc.get("jcb_净成本"));
                                    o.put("shuijin", calc.get("shuijin_理论税金"));
                                    o.put("shuijinSg", calc.get("shuijin_sg_实际税金"));
                                    o.put("xscb", calc.get("xscb_销售成本"));
                                } catch (Exception ignore) {
                                }
                                orders.add(o);
                            }
                            emitter.send(SseEmitter.event().name("message").data(
                                    objectMapper.writeValueAsString(Map.of(
                                            "type", "quotation_list",
                                            "customer", customer,
                                            "total", statData.getOrDefault("单号总数", 0),
                                            "orders", orders
                                    ))
                            ));
                        }
                        break;
                    }
                }

                // 4. 构建知识上下文（供应链数据优先放置在前面，确保AI优先参考）
                StringBuilder knowledgeContext = new StringBuilder();
                // 企划主题切换时注入上下文隔离声明(最高优先级, 置于上下文最前)
                if (!planningIsolationNotice.isEmpty()) {
                    knowledgeContext.append(planningIsolationNotice);
                }

                // 供应链/工厂数据上下文（优先级最高，放在最前面）
                // 企划/决策模式激活时启用三段式结构第1段：【结构化数据库查询结果（强制确定性数据）】
                boolean structuredMode = resolvedSubMode != null;
                if (!supplyChainResults.isEmpty() || !structuredResults.isEmpty()) {
                    if (structuredMode) {
                        knowledgeContext.append("## 1.【结构化数据库查询结果（强制确定性数据）】〔数据源优先级 L2·本地业务数据〕\n");
                        knowledgeContext.append("包含：报价单结构化数据、供应链数据、排产表&产能订单结构化数据、客户订单维度结构化数据、业务员基础资料。\n");
                        knowledgeContext.append(">规则：只要本段上下文内附带了产能排产、业务员效能、客户订单结构化查询结果，你100%必须基于给到的结构化数据进行分析，" +
                                "禁止编造任何不在返回结果内的产能数字、订单数据、业务员绩效指标、客户数据；不得脱离给出的数据空谈结论。\n");
                        knowledgeContext.append(">【产能与订单匹配】模块：仅以上下文注入的排产表、客户订单、产能负荷数据作为唯一依据开展匹配分析、产能缺口评估、交期风险预判。\n");
                        knowledgeContext.append(">【业务员效能】模块：仅以上下文注入的业务员维度结构化绩效数据开展效能评估；若无注入，禁止输出该板块，" +
                                "主动提示：缺少业务员绩效结构化查询数据，请先触发结构化数据检索。\n\n");
                    } else {
                        knowledgeContext.append("## 【重要】供应链/工厂业务数据（精确数据，优先引用）〔数据源优先级 L2·本地业务数据〕：\n");
                    }
                    List<Map<String, Object>> allStructured = new ArrayList<>(supplyChainResults);
                    allStructured.addAll(structuredResults);
                    for (Map<String, Object> r : allStructured) {
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
                    if (structuredMode) {
                        knowledgeContext.append("⚠️ 以上为强制确定性数据，数据分析板块每一条结论后面必须标注数据来源（结构化排产数据 / 业务员绩效数据 / 报价单数据 / 知识库）。\n\n");
                    } else {
                        knowledgeContext.append("⚠️ 用户询问的是供应链/工厂相关问题，请务必基于以上精确业务数据回答，引用具体数字。" +
                                "不要用知识库文档中的泛泛内容替代这些精确数据！\n\n");
                    }
                }

                // 业务员资料上下文（Milvus 向量检索，工厂模式第二优先级：业务语义补充）
                if (!salespersonResults.isEmpty()) {
                    knowledgeContext.append("## 【重要】业务员资料库（业务员一手业务文档，向量检索命中）〔数据源优先级 L2·本地业务数据〕：\n");
                    knowledgeContext.append("说明：同一资料文档命中的多个相关切片已按原文顺序拼接为完整段落，切片数越多代表该文档与问题越相关。\n");
                    for (int i = 0; i < salespersonResults.size(); i++) {
                        Map<String, Object> r = salespersonResults.get(i);
                        double score = ((Number) r.getOrDefault("score", 0)).doubleValue();
                        String content = r.getOrDefault("content", "").toString();
                        String fileName = r.getOrDefault("fileName", "未知文件").toString();
                        int mergedChunks = ((Number) r.getOrDefault("mergedChunks", 1)).intValue();
                        int hitChunks = ((Number) r.getOrDefault("hitChunks", mergedChunks)).intValue();
                        // 截断职责已在检索层按全局字符预算完成，注入层全量保留拼接内容，
                        // 仅留单组兜底保护（防止极端超长切片撑爆上下文）
                        int maxLen = 4800;
                        if (content.length() > maxLen) content = content.substring(0, maxLen) + "...[超长截断]";
                        String chunkInfo = mergedChunks > 1
                                ? String.format(" | 已拼接%d个相关切片/共命中%d片", mergedChunks, hitChunks)
                                : "";
                        knowledgeContext.append(String.format("### 资料%d (相关度: %.1f%% | 来源: %s%s)\n%s\n\n",
                                i + 1, score * 100, fileName, chunkInfo, content));
                    }
                    knowledgeContext.append("⚠️ 以上来自业务员资料库（Milvus向量检索），包含业务员的客户资料、产品明细、价格表等一手业务知识。" +
                            "请与【供应链/工厂业务数据】结合使用：精确数字以供应链数据为准，业务员资料用于补充客户背景、产品细节、工艺说明等业务语义信息。\n\n");
                }

                // 岗位卡片上下文（岗位意图时标注优先级最高，排在知识库PDF之前）
                if (!positionCardResults.isEmpty()) {
                    if (positionIntent) {
                        knowledgeContext.append("## 【重要】岗位知识卡片（用户询问的是岗位相关问题，请优先基于以下岗位卡片回答）〔数据源优先级 L1·内部业务规则（岗位经验）〕：\n");
                    } else {
                        knowledgeContext.append("## 岗位知识卡片（员工实际工作经验）〔数据源优先级 L1·内部业务规则（岗位经验）〕：\n");
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
                    if (structuredMode) {
                        knowledgeContext.append("## 2.【向量知识库召回文档】（业务背景、行业参考补充材料，不能覆盖结构化查询得出的数据结论）〔数据源优先级 L3·内部文档参考〕：\n");
                    } else {
                        knowledgeContext.append("## 知识库相关文档片段〔数据源优先级 L3·内部文档参考〕：\n");
                    }
                    for (int i = 0; i < knowledgeResults.size(); i++) {
                        Map<String, Object> r = knowledgeResults.get(i);
                        double score = ((Number) r.getOrDefault("score", 0)).doubleValue();
                        String content = r.getOrDefault("content", "").toString();
                        String source = r.getOrDefault("source", "未知文档").toString();
                        // 按相关度动态调整截断长度：高分保留更多内容
                        int maxLen = score >= 0.7 ? 1200 : (score >= 0.5 ? 800 : 500);
                        if (content.length() > maxLen) content = content.substring(0, maxLen) + "...";
                        knowledgeContext.append(String.format("### 片段%d (相关度: %.1f%% | 来源: %s)\n%s\n\n",
                                i + 1, score * 100, source, content));
                    }
                }

                // 历史对话Q&A对上下文（增强AI对历史专业回答的记忆）
                if (!chatHistoryQAResults.isEmpty()) {
                    knowledgeContext.append("## 历史专业问答参考（相似历史对话）〔数据源优先级 L4·外部参考数据〕：\n");
                    for (int i = 0; i < chatHistoryQAResults.size(); i++) {
                        Map<String, Object> r = chatHistoryQAResults.get(i);
                        double score = ((Number) r.getOrDefault("similarity", 0)).doubleValue();
                        String content = r.getOrDefault("content", "").toString();
                        if (content.length() > 500) content = content.substring(0, 500) + "...";
                        knowledgeContext.append(String.format("### 历史问答%d (相关度: %.1f%%)\n%s\n\n",
                                i + 1, score * 100, content));
                    }
                    knowledgeContext.append("⚠️ 以上来自历史对话中的专业问答，请参考其专业表达风格和知识深度，但以当前知识库内容和供应链数据为准。\n");
                }

                if (!imageResults.isEmpty()) {
                    knowledgeContext.append("## 图片库搜索结果〔数据源优先级 L2·本地业务数据〕：\n");
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
                    knowledgeContext.append("## 图片库搜索结果：未找到匹配的图片〔数据源优先级 L2·本地业务数据〕\n");
                    knowledgeContext.append("用户请求在图片库中查找图片，但根据关键词搜索未找到任何匹配的产品图片。请如实告知用户图片库中没有找到相关图片，并建议用户尝试其他关键词或上传相关图片。\n");
                }

                // 企划/决策模式第3段：用户历史多轮对话提问记录
                // 生成终稿时必须整合历史所有轮次用户提出的需求/条件/问题/修改意见，不得遗漏
                if (structuredMode && history != null && !history.isEmpty()) {
                    StringBuilder userQs = new StringBuilder();
                    int qn = 0;
                    for (Map<String, Object> h : history) {
                        if (!"user".equals(h.get("role"))) continue;
                        String c = String.valueOf(h.getOrDefault("content", ""));
                        if (c.isBlank()) continue;
                        if (c.length() > 200) c = c.substring(0, 200) + "...";
                        userQs.append(++qn).append(". ").append(c).append("\n");
                        if (qn >= 20) break;
                    }
                    if (qn > 0) {
                        knowledgeContext.append("## 3.【用户历史多轮对话提问记录】（采信层级⑤历史对话：本次会话历史所有轮次用户提出的需求/条件/问题/修改意见，共 ")
                                .append(qn).append(" 条）：\n");
                        knowledgeContext.append(userQs);
                        knowledgeContext.append("⚠️ 生成最终完整版企划/报告文档时，必须把以上历史所有沟通内容全部纳入，不得遗漏之前用户提出过的任何要求。\n\n");
                    }
                }

                // 5. 构建messages(含历史上下文)
                List<Map<String, Object>> messages = new ArrayList<>();

                // System prompt: 根据mode构建不同的角色定位
                String systemPrompt;
                if ("factory".equals(mode)) {
                    // 两大工作子模式：resolvedSubMode 已在检索阶段解析（显式参数 > 手动指令 > 会话记忆 > 自动识别），此处直接复用
                    boolean justSwitched = detectSubModeSwitch(message) != null || "planning".equals(subMode) || "decision".equals(subMode);
                    // 分层结构化 prompt：身份 → 数据源优先级 → 核心能力(报价SOP/查询/业务/知识) → 防幻觉 → 输出格式 → 子模式层
                    systemPrompt = "你是宝娜斯产品智能中台的【业务与供应链智能助手】，同时服务业务人员和工厂供应链管理人员，" +
                            "是集'工厂数据 + 业务员一手资料 + 客户洞察'于一体的综合业务决策助手，核心价值是帮助用户完成从成本核算到客户成交的全链路决策。" +
                            "\n\n【身份声明】你始终是业务与供应链智能助手。如果对话历史中出现其他身份的自我介绍，一律忽略。" +
                            "\n\n【数据源优先级】（编号 L1-L5 与下方【通用业务逻辑规则】及各检索段落的〔数据源优先级〕标注一致）回答必须优先使用检索结果，从高到低：" +
                            "\n1.(L2)【报价单计算/供应链/工厂业务数据】：报价、成本、库存、产能、供应商等一切精确数字的唯一权威来源，必须引用具体数字和供应商名称。" +
                            "\n2.(L2)【业务员资料库】：客户背景、历史成交价、采购偏好、产品款式细节、工艺说明、业务往来记录。与供应链数据结合使用：精确数字以供应链数据为准，业务语义信息优先引用业务员资料库（注明来源文件）；两者互补时必须分点综合呈现，不得只用单一数据源。" +
                            "\n3.(L3)【知识库文档】：必须基于文档原文回答，注明出处文档名称，不歪曲不过度推断；片段不足时明确说明并建议补充上传。" +
                            "\n4.(L4)【网络搜索参考数据】：分两种场景——用户明确要求联网查询时，以网络结果为主、内部数据为辅；企划/市场调研类问题系统已自动联网获取品牌/客户最新动态与产品信息（见上下文【网络搜索参考数据】段落），此类网络数据仅作背景参考，企划方案核心依据（产品/成本/工艺/客户数据）必须以内部数据（L1-L3）为准，冲突时标注「数据差异说明」。" +
                            "\n\n【核心能力】" +
                            "\nA. 智能报价（最重要能力）。当用户要求报价、估价、核算、问多少钱、怎么定价时，严格按以下SOP四步输出完整方案，不得只回单个数字：" +
                            "\n第一步·成本核算：以【报价单计算】数据为准，依次列出 日产量、织造成本、染色成本、原料金额、前道合计、辅料金额、后道合计、净成本、理论税金、实际税金、销售成本 的计算值，得出成本基准。" +
                            "\n第二步·客户与市场背景：从【业务员资料库】中提取该客户/产品的历史成交价、采购偏好、议价风格、订单规模；若无记录，明确说'该客户暂无历史业务记录，按标准策略报价'。" +
                            "\n第三步·客户群体分级与差异化策略（依据业务员资料和订单特征判断客户属于哪类，无信息时说明假设）：" +
                            "\n- 战略大客户（订单量大、长期合作）：以成本基准为底线，重点保障供应与账期，报价贴近成本+合理毛利，可提出返利/阶梯价方案；" +
                            "\n- 常规老客户：参照历史成交价微调（±5%以内），保持价格稳定性与客户关系；" +
                            "\n- 新客户（首单）：适度上浮试探（+3%~10%），预留谈判空间，同时提示控制账期与信用风险；" +
                            "\n- 价格敏感型/小批量订单：按起订量与工费分摊核算，若低于成本线须明确说明不可接单，并给出最低可接单量或替代方案（简化工艺/换料/拼单）。" +
                            "\n第四步·输出建议：给出三档建议报价（低/标准/高）及各档适用场景，附1-2条谈判建议或风险提示。" +
                            "\nB. 业务数据查询：先理解问题核心意图再选维度作答，不得张冠李戴——问报价单号/净成本/销售成本/日产量/机台费/税金看【报价单】维度；问排产/批次/交期/投产看【生产排产】；问部件/款式/尺码看部件数据；问原料/辅料/供应商/价格对比看物料/供应商数据；跨维度问题分维度分别说明。问单号总数时必须以'单号总数'为准，不得以明细条数代替，不得说'暂无数据'。全量报价单明细已由系统面板以结构化表格完整展示给用户，你严禁输出任何'以…为例'的示例表格或逐条单号列表，只输出总数、整体统计（平均/最高/最低净成本、产能分布等）与管控建议。" +
                            "\nC. 业务支持：结合业务员资料回答客户背景、款式细节、工艺说明、订单进度等问题；用户需要查看产品图时可搜索图片库。" +
                            "\nD. 知识问答：基于知识库文档回答管理、流程、标准等问题，注明出处。" +
                            "\n\n【防幻觉铁律】只引用检索结果中明确存在的内容；单号/货号/客户名称必须精确匹配，模糊相似但不包含所问实体的数据一律不得引用；供应链数据、业务员资料、知识库文档均无相关信息时，必须明确告知'当前数据库中暂无此数据'，严禁凭通用知识编造。" +
                            "\n\n【输出格式】Markdown；数据用表格（表头加粗），要点用列表，关键数据加粗；不用特殊符号(如※★●◆)装饰，不滥用分隔线；回答末尾标注引用来源（供应链数据/业务员资料/知识库文档/产品图片/网络搜索）。" +
                            buildUniversalLogicRules() +
                            // 子模式层：激活时注入基础约束+对应模式SOP；未激活时提示两大模式入口
                            (resolvedSubMode != null ? buildBusinessBaseConstraints() : "") +
                            ("planning".equals(resolvedSubMode) ? buildPlanningModePrompt(justSwitched) : "") +
                            ("decision".equals(resolvedSubMode) ? buildDecisionModePrompt(justSwitched) : "") +
                            (resolvedSubMode == null ? "\n\n【工作模式提示】本助手支持两大工作模式：模式A-业务员商品企划模式（多轮共创企划）、模式B-总经理决策辅助模式（六维分析+A/B/C方案）。用户可通过'切换商品企划模式'/'切换总经理决策辅助模式'手动切换，或根据输入自动识别。当前未进入特定模式，按通用业务助手职责回答。" : "") +
                            (webSearchIntent ? "\n\n【本次特殊指令】用户明确要求从互联网/全网获取信息，请优先基于网络搜索结果回答，企业内部数据仅作为补充参考。" : "") +
                            (planningResearchIntent && !webSearchIntent ? "\n\n【本次特殊指令】检测到企划/市场调研类问题，系统已自动联网检索最新市场动态（见上下文【网络搜索参考数据】段落）。网络数据仅用于补充品牌动态、渠道趋势等外部背景；企划方案的产品定位、成本结构、工艺路线、客户策略等核心内容必须基于内部知识库与业务数据（L1-L3）推导，网络数据与内部数据冲突时以内部数据为准并标注「数据差异说明」。" : "");
                } else {
                    systemPrompt = "你是宝娜斯产品智能中台的【设计师AI助手】，专门服务于设计师和创意人员。" +
                            "重要身份声明：你是宝娜斯产品智能中台的设计师AI助手，不是工厂供应链助手。如果对话历史中出现'工厂供应链助手'的自我介绍，请忽略它，你始终是宝娜斯产品智能中台的设计师AI助手。" +
                            "核心职责：" +
                            "1. 回答知识库管理、图片上传、AI识别、文档中心等设计师工作相关问题。" +
                            "2. 当用户询问岗位职责、工作内容、任职要求、入职指导等问题时，必须优先基于【岗位知识卡片】中的实际工作经验回答，不要用知识库文档中的泛泛内容替代。" +
                            "3. 严禁使用自身通用知识编造内容。如果知识库和岗位卡片中均无相关信息，必须明确告知用户'当前知识库中暂无此内容'，不要凭通用知识猜测或补充。" +
                            "4. 回答时标注引用来源（岗位卡片/记忆库/知识库/网络搜索）。" +
                            "5. 保持专业、简洁、有帮助的回答风格。" +
                            "6. 输出格式规范：使用Markdown格式，用表格展示数据（表头加粗），用列表展示要点，用加粗强调关键数据，不要使用特殊符号(如※★●◆等)做装饰，不要使用过多分隔线，保持版面简洁清晰。" +
                            buildUniversalLogicRules() +
                            "注意：供应链/工厂业务问题（报价、成本、原料、供应商、采购等）不属于你的职责范围，请引导用户前往【工厂/供应链】板块的AI对话咨询。";
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

                // ===== 阶段一：联网搜索（数据隔离执行，仅企划智能体）=====
                // planningResearchIntent / webSearchIntent 已在意图识别阶段定义（仅 planning 子模式可能为 true）
                // 保密约束[CRITICAL]：searchWebForMarketInfo 只允许传入用户原始问题(message)。
                // knowledgeContext/supplyChainResults/业务数据/历史对话等内部数据严禁拼入网络请求，
                // 两阶段物理隔离——网络端点只看到用户自己输入的公开问题，内部数据仅在阶段二（本地模型）参与。
                if ("planning".equals(resolvedSubMode) && (planningResearchIntent || webSearchIntent)) {
                    try {
                        // 阶段一：用户原始问题 → 通用模板转写 → MiniMax联网检索（数据隔离，无内部数据外传）
                        // 注: "开始新企划"裸指令场景使用改写消息(含被拦截的新主题), 确保采集新主题官网参数
                        String webSummary = searchWebForMarketInfo(
                                planningOverrideMessage != null ? planningOverrideMessage : message);
                        if (webSummary != null && !webSummary.isBlank()) {
                            // 阶段二：网络摘要注入本地LLM上下文，与内部数据共同参与企划方案生成
                            knowledgeContext.append("\n\n## 【网络搜索参考数据】〔数据源优先级 L4·外部参考数据〕\n")
                                    .append("以下为按用户问题联网采集的品牌官方产品参数报告（优先品牌官网产品中心，官网缺失时以行业平台/官方旗舰店兜底，")
                                    .append("每条参数附来源页面URL作溯源；仅作背景参考，非内部数据）。\n")
                                    .append("输出引用规则：引用网络信息时在数据支撑中标注【外部调研】(来源URL,置信度)；")
                                    .append("引用内部数据时标注【内部数据库-XXX】或【知识库文档-XXX】；自身推导标注【AI推断】(依据,置信度)。\n")
                                    .append("冲突处理：若网络数据与上方内部数据（L1-L3）冲突，一律以内部数据为准，")
                                    .append("并按通用业务逻辑规则标注「数据差异说明」，不得静默采用网络数据覆盖内部结论。\n")
                                    .append(webSummary.trim()).append("\n");
                            log.info("[web-search] 联网摘要已注入上下文, 长度={}字符, 触发方式={}",
                                    webSummary.length(), planningResearchIntent ? "企划意图自动触发" : "用户明确要求联网");
                        } else {
                            log.info("[web-search] 未获取到联网摘要, 继续以内部数据生成回答");
                        }
                    } catch (Exception webEx) {
                        log.warn("[web-search] 阶段一联网搜索异常(不影响主流程): {}", webEx.getMessage());
                    }
                }

                // 当前用户消息(带知识上下文)
                String userContent = planningOverrideMessage != null ? planningOverrideMessage : message;
                if (!knowledgeContext.isEmpty()) {
                    boolean hasSupplyChain = !supplyChainResults.isEmpty();
                    boolean hasPositionCards = !positionCardResults.isEmpty();
                    boolean hasKnowledge = !knowledgeResults.isEmpty();
                    // 打印发送给LLM的上下文摘要（便于调试数据传递链路）
                    log.info("[LLM上下文] knowledgeContext总长度={}字符, hasSupplyChain={}, hasKnowledge={}, hasPositionCards={}",
                        knowledgeContext.length(), hasSupplyChain, hasKnowledge, hasPositionCards);
                    if (hasSupplyChain) {
                        log.info("[LLM上下文] 供应链数据条数={}, 上下文前200字: {}",
                            supplyChainResults.size(),
                            knowledgeContext.length() > 200 ? knowledgeContext.substring(0, 200) : knowledgeContext.toString());
                    }
                    userContent = knowledgeContext.toString() + "\n---\n用户问题: " + message;
                    if (isFactory && hasSupplyChain) {
                        userContent += "\n\n请优先基于上方【供应链/工厂业务数据】中的精确数字回答";
                        if (!salespersonResults.isEmpty()) {
                            userContent += "，并结合【业务员资料库】片段补充业务背景与产品细节，给出综合答案";
                        }
                        userContent += "。";
                        if (isQuotationIntent(message)) {
                            userContent += "\n\n【报价任务】本次用户请求属于报价场景，请严格按【核心能力A·智能报价SOP】四步输出完整报价方案：成本核算明细 → 客户与市场背景（从业务员资料库提取，无记录则说明） → 客户群体分级判断与差异化报价策略 → 三档建议报价与谈判建议。";
                        }
                    } else if (hasSupplyChain) {
                        userContent += "\n\n请优先基于上方【供应链/工厂业务数据】中的精确数字回答，不要使用知识库文档内容替代业务数据。";
                    } else if (positionIntent && hasPositionCards) {
                        userContent += "\n\n请优先基于上方【岗位知识卡片】中的实际工作经验回答，不要使用知识库文档内容替代岗位卡片中的精确信息。";
                    } else if (externalKnowledgeIntent && !hasKnowledge && !hasPositionCards && chatHistoryQAResults.isEmpty()) {
                        userContent += "\n\n用户的问题涉及外部通用知识/行业趋势/方法论等，知识库文档中没有找到相关内容，请基于网络搜索结果回答，如果网络搜索无结果则基于自身通用知识回答。";
                    } else if (externalKnowledgeIntent && (hasKnowledge || hasPositionCards || !chatHistoryQAResults.isEmpty())) {
                        userContent += "\n\n知识库中已检索到相关内容，必须严格基于以上知识库内容回答，禁止使用自身通用知识补充或编造知识库中没有的信息。";
                    } else {
                        userContent += "\n\n必须严格基于以上知识内容回答，禁止使用自身通用知识补充或编造知识库中没有的信息。如资料不足以完整回答，请明确指出哪些部分知识库中暂无数据。";
                    }
                } else {
                    userContent = message;
                }
                // 收集知识库检索结果中的图片URL，传给多模态模型
                List<String> imageBase64List = new ArrayList<>();
                for (Map<String, Object> r : knowledgeResults) {
                    String source = r.getOrDefault("source", "").toString();
                    String content = r.getOrDefault("content", "").toString();
                    // 检查来源文件名是否是图片格式
                    if (source.matches("(?i).*\\.(jpg|jpeg|png|gif|webp|bmp)$")) {
                        String imageUrl = r.getOrDefault("url", "").toString();
                        if (!imageUrl.isEmpty()) {
                            try {
                                String base64 = downloadImageAsBase64(imageUrl);
                                if (base64 != null) {
                                    imageBase64List.add(base64);
                                    log.info("知识库图片传入多模态: source={}, url={}", source, imageUrl);
                                }
                            } catch (Exception ex) {
                                log.warn("下载知识库图片失败: url={}, error={}", imageUrl, ex.getMessage());
                            }
                        }
                    }
                }
                // 收集商品库文件夹图片（品名+货号命中的商品库条目，data中含签名URL），传给多模态模型
                for (Map<String, Object> entry : structuredResults) {
                    if (!"商品库文件夹".equals(entry.get("type"))) continue;
                    Object dataObj = entry.get("data");
                    if (!(dataObj instanceof Map)) continue;
                    @SuppressWarnings("unchecked")
                    Map<String, Object> goodsData = (Map<String, Object>) dataObj;
                    for (Map.Entry<String, Object> ge : goodsData.entrySet()) {
                        String slotKey = ge.getKey();
                        if (!slotKey.endsWith("URL") || ge.getValue() == null) continue;
                        String imageUrl = ge.getValue().toString();
                        if (!imageUrl.startsWith("http") || imageBase64List.size() >= 5) continue;
                        try {
                            String base64 = downloadImageAsBase64(imageUrl);
                            if (base64 != null) {
                                imageBase64List.add(base64);
                                log.info("商品库图片传入多模态: folder={}, slot={}", goodsData.get("文件夹名称"), slotKey);
                            }
                        } catch (Exception ex) {
                            log.warn("下载商品库图片失败: url={}, error={}", imageUrl, ex.getMessage());
                        }
                    }
                }
                // 也检查图片库搜索结果
                if (!imageResults.isEmpty()) {
                    for (Map<String, Object> product : imageResults) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> mainImage = (Map<String, Object>) product.get("mainImage");
                        if (mainImage != null) {
                            String imageUrl = mainImage.getOrDefault("url", "").toString();
                            if (!imageUrl.isEmpty() && imageBase64List.size() < 5) {
                                try {
                                    String base64 = downloadImageAsBase64(imageUrl);
                                    if (base64 != null) {
                                        imageBase64List.add(base64);
                                        log.info("产品图片传入多模态: product={}, url={}", 
                                                product.getOrDefault("productName", ""), imageUrl);
                                    }
                                } catch (Exception ex) {
                                    log.warn("下载产品图片失败: url={}, error={}", imageUrl, ex.getMessage());
                                }
                            }
                        }
                    }
                }

                // 构建用户消息（支持多模态）
                // 用户上传的图片优先加入
                if (userImages != null && !userImages.isEmpty()) {
                    imageBase64List.addAll(0, userImages); // 用户上传的图片放在最前面
                    log.info("用户上传{}张图片传入多模态模型", userImages.size());
                }

                // 用户上传的PDF文档：提取文本作为上下文
                StringBuilder pdfContext = new StringBuilder();
                if (userPdfs != null && !userPdfs.isEmpty()) {
                    for (Map<String, String> pdf : userPdfs) {
                        String pdfName = pdf.getOrDefault("name", "未知文件");
                        String pdfBase64 = pdf.get("base64");
                        if (pdfBase64 != null && !pdfBase64.isEmpty()) {
                            try {
                                byte[] pdfBytes = Base64.getDecoder().decode(pdfBase64);
                                String extractedText = extractTextFromPdf(pdfBytes);
                                if (extractedText != null && !extractedText.trim().isEmpty()) {
                                    pdfContext.append("\n\n--- 文档: ").append(pdfName).append(" ---\n");
                                    // 截断过长内容（最多8000字符）
                                    if (extractedText.length() > 8000) {
                                        pdfContext.append(extractedText, 0, 8000).append("\n...[文档内容过长，已截断]");
                                    } else {
                                        pdfContext.append(extractedText);
                                    }
                                    log.info("PDF文档提取文本成功: name={}, textLen={}", pdfName, extractedText.length());
                                } else {
                                    pdfContext.append("\n\n--- 文档: ").append(pdfName).append(" ---\n[无法提取文本内容，可能是扫描件或图片PDF]");
                                    log.warn("PDF文档提取文本为空: name={}", pdfName);
                                }
                            } catch (Exception e) {
                                log.warn("PDF文档处理失败: name={}, error={}", pdfName, e.getMessage());
                                pdfContext.append("\n\n--- 文档: ").append(pdfName).append(" ---\n[文档解析失败: ").append(e.getMessage()).append("]");
                            }
                        }
                    }
                    if (pdfContext.length() > 0) {
                        userContent += "\n\n用户上传了以下文档，请基于文档内容回答问题：" + pdfContext;
                        log.info("用户上传{}个PDF文档，提取文本作为上下文", userPdfs.size());
                    }
                }
                Map<String, Object> userMessage = new HashMap<>();
                userMessage.put("role", "user");
                userMessage.put("content", userContent);
                if (!imageBase64List.isEmpty()) {
                    // Ollama images字段最多传5张图片
                    List<String> limitedImages = imageBase64List.size() > 5 ? imageBase64List.subList(0, 5) : imageBase64List;
                    userMessage.put("images", limitedImages);
                    userContent += String.format("\n\n[已传入%d张图片，请结合图片内容回答]", limitedImages.size());
                    userMessage.put("content", userContent);
                    log.info("传入多模态模型: 共{}张图片(用户上传{}, 知识库/产品{})", 
                            limitedImages.size(), 
                            userImages != null ? Math.min(userImages.size(), 5) : 0,
                            Math.max(0, limitedImages.size() - (userImages != null ? Math.min(userImages.size(), 5) : 0)));
                }
                messages.add(userMessage);

                // 6. 保存用户消息
                saveChatMessage(userId, convId, "user", message, company, null, mode);
                // 6b. 更新ChatMemory（内存级多轮对话记忆，LangChain4j ChatMemory）
                chatMemoryManager.addUserMessage(convId, message);

                // 7. 流式调用本地模型（Ollama）
                // 联网策略：仅企划智能体（factory/planning 子模式）允许联网检索（阶段一 MiniMax web_search 已在前面完成）；
                // 其他模式与子模式一律不联网、也不判断联网意图。
                boolean enableWebSearch = "planning".equals(resolvedSubMode);
                StringBuilder fullResponse = new StringBuilder();
                StringBuilder fullReasoning = new StringBuilder();
                long chatCallStart = System.currentTimeMillis();
                String chatCapability = "factory".equals(mode)
                        ? com.imagemanager.service.AiCallLogService.CAP_FACTORY_CHAT
                        : com.imagemanager.service.AiCallLogService.CAP_SMART_CHAT;
                try {
                    streamChat(emitter, messages, fullResponse, fullReasoning, enableWebSearch);
                    if (aiCallLogService != null) {
                        aiCallLogService.record(chatCapability, null, true,
                                System.currentTimeMillis() - chatCallStart, null, "convId=" + convId);
                    }
                } catch (Exception chatEx) {
                    if (aiCallLogService != null) {
                        aiCallLogService.record(chatCapability, null, false,
                                System.currentTimeMillis() - chatCallStart, null, "convId=" + convId);
                    }
                    throw chatEx;
                } finally {
                    // 8. 无论流是否成功，都保存已收集的AI回复（含思维链）
                    if (fullResponse.length() > 0) {
                        String reasoning = fullReasoning.length() > 0 ? fullReasoning.toString() : null;
                        saveChatMessage(userId, convId, "assistant", fullResponse.toString(), company, reasoning, mode);
                        // 同步更新ChatMemory
                        chatMemoryManager.addAssistantMessage(convId, fullResponse.toString());
                        
                        // 8b. 异步向量化Q&A对（用户问题+AI回答拼接后向量化，供后续RAG检索）
                        // 仅在非闲聊场景下向量化，避免存入无价值对话
                        if (!generalChatIntent) {
                            final String finalMessage = message;
                            final String finalAnswer = fullResponse.toString();
                            final String finalCompany = company;
                            new Thread(() -> {
                                try {
                                    vectorizeChatQA(userId, finalConvId, finalMessage, finalAnswer, finalCompany);
                                } catch (Exception e) {
                                    log.warn("Q&A向量化异步任务异常: {}", e.getMessage());
                                }
                            }).start();
                        }
                    }
                }

                // 9. 更新对话标题（如果是新对话的第一条消息）
                updateConversationTitleFromMessage(convId,
                        planningOverrideMessage != null ? planningOverrideMessage : message);

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
    public List<Map<String, Object>> getChatHistory(String userId, String company, String conversationId, String mode) {
        String modeCondition = "";
        Object[] params;
        if (mode != null && !mode.isEmpty()) {
            modeCondition = " AND model = ? ";
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
            String modeCondition = (mode != null && !mode.isEmpty()) ? " AND conversation_id IN (SELECT id FROM smart_chat_conversations WHERE model = ?)" : "";
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
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update(
                    "INSERT INTO smart_chat_conversations (id, user_id, company, title, model, created_at, updated_at) " +
                            "VALUES (?::uuid, ?, ?, ?, ?::varchar, NOW(), NOW())",
                    convId, userId, company, convTitle, modeValue
            );
        });
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
                    "WHERE user_id = ? AND (company = ? OR company IS NULL) AND model = ? " +
                    "ORDER BY updated_at DESC";
            params = new Object[]{userId, company, modeValue};
        } else {
            // 兼容旧数据：mode IS NULL 视为 designer
            sql = "SELECT id, title, created_at, updated_at FROM smart_chat_conversations " +
                    "WHERE user_id = ? AND (company = ? OR company IS NULL) AND (model = 'designer' OR model IS NULL) " +
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
        // 清理业务子模式记忆
        if (conversationId != null) businessSubModeMap.remove(conversationId);
    }

    /**
     * 获取或创建默认对话
     */
    private String getOrCreateDefaultConversation(String userId, String company, String mode) {
        // 查找最近的对话（按mode筛选）
        String modeCondition = (mode != null && !mode.isEmpty()) ? "AND model = ?" : "AND model = 'designer'";
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
            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
            // 检查该对话是否只有0-1条消息（刚创建的对话）
            Integer count = txTemplate.execute(status -> {
                String countSql = "SELECT COUNT(*) FROM smart_chat_history WHERE conversation_id = ?::uuid";
                return jdbcTemplate.queryForObject(countSql, Integer.class, conversationId);
            });
            if (count != null && count <= 2) {
                // 用消息前20个字符作为标题
                String title = message.length() > 20 ? message.substring(0, 20) + "..." : message;
                updateConversationTitle(conversationId, title);
            }
            // 更新对话的 updated_at
            txTemplate.executeWithoutResult(status -> {
                jdbcTemplate.update(
                        "UPDATE smart_chat_conversations SET updated_at = NOW() WHERE id = ?::uuid",
                        conversationId
                );
            });
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
     * 检测用户问题是否需要外部/通用知识（而非企业内部知识库）。
     * 典型场景：设计趋势、最佳实践、如何做某事、行业通用方法等，
     * 这些问题知识库中通常没有相关内容，应该直接联网搜索而非检索PDF文档。
     */
    private boolean isExternalKnowledgeIntent(String message) {
        String lower = message.toLowerCase().trim();

        // 先排除：明确涉及企业内部数据管理的问题，不应联网搜索
        String[] internalPatterns = {
            "知识库中的", "知识库里的", "记忆库中的", "记忆库里的",
            "我的文档", "我上传的", "文档分类", "图片库中的", "图片库里的",
            "岗位卡片", "我的知识", "内部资料"
        };
        for (String kw : internalPatterns) {
            if (lower.contains(kw)) return false;
        }

        // 明确需要外部知识的模式
        String[] externalPatterns = {
            // 趋势/动态类（需要最新外部信息）
            "趋势", "动态", "潮流", "风向", "流行",
            // 方法论/最佳实践类（通用知识，非企业内部）
            "如何", "怎么", "怎样", "最佳实践", "技巧", "方法论",
            "方法", "策略", "方案", "建议", "推荐",
            // 学习/资料搜索类
            "搜索", "查找资料", "找资料", "学习", "了解",
            "总结", "归纳", "梳理",
            // 通用概念/原理类
            "什么是", "什么叫", "原理", "概念", "定义",
            // 对比/选择类
            "对比", "区别", "选择", "哪个好", "优劣",
            // 行业通用（非企业内部数据）
            "行业", "市场", "竞品", "设计风格", "设计规范"
        };

        for (String kw : externalPatterns) {
            if (lower.contains(kw)) return true;
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
            // 将float[]转为PostgreSQL vector格式的字符串（使用BigDecimal避免科学计数法）
            // pgvector格式: [0.1,0.2,...] 方括号
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < embeddingArray.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(new java.math.BigDecimal(String.valueOf(embeddingArray[i])).toPlainString());
            }
            sb.append("]");
            String queryEmbedding = sb.toString();

            String sql = "SELECT e.chunk_text, e.chunk_index, e.source_doc_id, " +
                    "1 - (e.embedding <=> ?::vector) AS similarity " +
                    "FROM knowledge_embeddings e " +
                    "WHERE e.source_type = 'POSITION_CARD' " +
                    "AND (e.company = ? OR e.company IS NULL) " +
                    "AND 1 - (e.embedding <=> ?::vector) > 0.25 " +
                    "ORDER BY e.embedding <=> ?::vector " +
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
     * RAG检索历史对话Q&A对 - 从knowledge_embeddings中检索source_type='SMART_CHAT'的向量
     * 将用户当前提问向量化后，搜索最相似的历史问答，作为额外上下文注入Prompt
     */
    private List<Map<String, Object>> searchChatHistoryQA(String query, String company) {
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            float[] embeddingArray = getEmbedding(query);
            if (embeddingArray == null || embeddingArray.length == 0) {
                return results;
            }
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < embeddingArray.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(new java.math.BigDecimal(String.valueOf(embeddingArray[i])).toPlainString());
            }
            sb.append("]");
            String queryEmbedding = sb.toString();

            String sql = "SELECT e.chunk_text, e.chunk_index, e.source_doc_id, " +
                    "1 - (e.embedding <=> ?::vector) AS similarity " +
                    "FROM knowledge_embeddings e " +
                    "WHERE e.source_type = 'SMART_CHAT' " +
                    "AND (e.company = ? OR e.company IS NULL) " +
                    "AND 1 - (e.embedding <=> ?::vector) > 0.30 " +
                    "ORDER BY e.embedding <=> ?::vector " +
                    "LIMIT 3";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, queryEmbedding, company, queryEmbedding, queryEmbedding);
            for (Map<String, Object> row : rows) {
                Map<String, Object> item = new HashMap<>();
                item.put("content", row.get("chunk_text"));
                item.put("sourceDocId", row.get("source_doc_id"));
                item.put("similarity", row.get("similarity"));
                item.put("source", "chat_history");
                results.add(item);
            }
            log.info("历史对话QA检索完成, 查询: '{}', 命中: {}条", query, results.size());
        } catch (Exception e) {
            log.warn("历史对话QA检索失败: {}", e.getMessage());
        }
        return results;
    }

    /**
     * 异步向量化Q&A对 - 将用户问题+AI回答拼接后，调用bge-m3向量化并存入knowledge_embeddings
     * source_type='SMART_CHAT'，与岗位卡片/知识库物理隔离
     */
    private void vectorizeChatQA(String userId, String conversationId, String question, String answer, String company) {
        try {
            // 拼接Q&A对
            String qaText = "【用户问题】" + question + "\n【AI专业回答】" + answer;
            // 截断防止过长
            if (qaText.length() > 2000) {
                qaText = qaText.substring(0, 2000);
            }
            final String qaTextFinal = qaText;

            float[] embeddingArray = getEmbedding(qaText);
            if (embeddingArray == null || embeddingArray.length == 0) {
                log.warn("Q&A向量化跳过: embedding获取失败, conversationId={}", conversationId);
                return;
            }

            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < embeddingArray.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(new java.math.BigDecimal(String.valueOf(embeddingArray[i])).toPlainString());
            }
            sb.append("]");
            String embeddingStr = sb.toString();

            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
            txTemplate.executeWithoutResult(status -> {
                jdbcTemplate.update(
                        "INSERT INTO knowledge_embeddings (id, source_type, source_doc_id, chunk_text, chunk_index, embedding, company, created_at) " +
                                "VALUES (gen_random_uuid(), 'SMART_CHAT', ?::uuid, ?, 0, ?::vector, ?, NOW())",
                        conversationId, qaTextFinal, embeddingStr, company
                );
            });
            log.info("Q&A向量化成功: conversationId={}, 维度={}, textLength={}", conversationId, embeddingArray.length, qaText.length());
        } catch (Exception e) {
            log.error("Q&A向量化失败: conversationId={}, error={}", conversationId, e.getMessage(), e);
        }
    }

    /**
     * 知识库检索 - 查询知识库独立的向量表(knowledge_embeddings, source_type='KNOWLEDGE_BASE')
     */
    private List<Map<String, Object>> searchKnowledgeBase(String query, String company) {
        long startTime = System.currentTimeMillis();
        log.info("===== 知识库检索开始 =====");
        log.info("[知识库] 查询: \"{}\" | company: {}", query, company);

        try {
            List<Map<String, Object>> results = new ArrayList<>();

            // 0. 优先：直接关键词搜索（绕过RagPipeline和向量检索，避免Ollama超时）
            String productCode = extractProductCode(query);
            if (productCode != null && !productCode.isEmpty()) {
                try {
                    log.info("[知识库] 检测到产品编码 '{}'，优先直接搜索knowledge_embeddings表", productCode);
                    List<Map<String, Object>> directResults = searchKnowledgeEmbeddingsDirect(productCode, company);
                    if (directResults != null && !directResults.isEmpty()) {
                        log.info("[知识库] 直接搜索成功！返回 {} 条结果", directResults.size());
                        long elapsed = System.currentTimeMillis() - startTime;
                        log.info("===== 知识库检索完成（直接搜索）: {} 条结果, 耗时 {}ms =====", directResults.size(), elapsed);
                        return directResults;
                    }
                    log.warn("[知识库] 直接搜索无结果，降级到RagPipeline");
                } catch (Exception e) {
                    log.warn("[知识库] 直接搜索异常，降级到RagPipeline: {}", e.getMessage());
                }
            }

            // 1. 次选：RagPipeline（包含查询增强 + 多路召回 + Reranker 重排序）
            if (ragPipeline != null) {
                try {
                    log.info("[知识库] 使用 RagPipeline 增强检索（查询增强 + 多路召回 + Reranker 重排序）");
                    List<Map<String, Object>> ragResults = ragPipeline.enhancedSearchAsMap(query, company);

                    if (ragResults != null && !ragResults.isEmpty()) {
                        log.info("[知识库] RagPipeline 返回 {} 条结果", ragResults.size());
                        for (int i = 0; i < ragResults.size(); i++) {
                            Map<String, Object> item = ragResults.get(i);
                            // 补充 source 字段
                            item.putIfAbsent("source", "knowledge_base_rag");
                            // 补充 title/domain 字段
                            item.putIfAbsent("title", "");
                            item.putIfAbsent("domain", "知识库");
                            results.add(item);
                            log.info("[知识库] RAG结果 #{}: score={}, content={}...", i + 1,
                                item.get("score"),
                                item.get("content") != null ? item.get("content").toString().substring(0, Math.min(80, item.get("content").toString().length())) : "");
                        }
                        long elapsed = System.currentTimeMillis() - startTime;
                        log.info("===== 知识库检索完成（RagPipeline）: {} 条结果, 耗时 {}ms =====", results.size(), elapsed);
                        return results;
                    } else {
                        log.warn("[知识库] RagPipeline 返回空结果，降级为直接向量检索");
                    }
                } catch (Exception e) {
                    log.warn("[知识库] RagPipeline 检索失败: {}，降级为直接向量检索", e.getMessage());
                }
            } else {
                log.warn("[知识库] RagPipeline 未注入，使用直接向量检索");
            }

            // 2. 降级：直接向量检索（不经过 RagPipeline）
            // 阈值与 RagPipeline 召回粗筛对齐（0.30），过低会召回弱相关切片引发幻觉
            log.info("[知识库] 使用直接向量检索（knowledgeBaseService.search）");
            List<MemorySearchResult> allResults = knowledgeBaseService.search(query, 0.30, 15, company);
            log.info("[知识库] 直接检索返回 {} 条结果", allResults != null ? allResults.size() : 0);

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

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("===== 知识库检索完成（直接检索）: {} 条结果, 耗时 {}ms =====", results.size(), elapsed);
            return results;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("[知识库] 检索异常, 耗时 {}ms: {}", elapsed, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    // ========== 业务员资料库 Milvus 向量检索（工厂模式 RAG） ==========

    /**
     * 从 Milvus salesperson_chunks 精确检索业务员业务知识
     *
     * 精确性控制（业务问题 → 给大模型的上下文）：
     * 1. 查询文本经 bge-m3 向量化后做 HNSW COSINE TopK 检索（Milvus 内 ef=128 精排）
     * 2. score < MIN_SCORE 的切片直接过滤，防止无关内容进入上下文引发幻觉
     * 3. 同文档多切片聚合拼接（V50）：命中切片按 docId 分组，组内按相关性(score)选出
     *    最强 MAX_CHUNKS_PER_DOC 片，再按 chunkIndex 升序拼回原文阅读顺序，整组交给本地 LLM，
     *    解决"只留单切片导致该文档内容检索不完全"的问题
     * 4. 智能预算控制：文档组按组内最高分降序处理，全局共享 TOTAL_CHAR_BUDGET 字符预算；
     *    切片很多时优先保留相关性更强的切片，预算不足则截断低分组并显式标注，
     *    防止超长上下文撑爆本地 LLM
     */
    private List<Map<String, Object>> searchSalespersonKnowledge(String query) {
        if (milvusService == null || !milvusService.isEnabled()) {
            return Collections.emptyList();
        }
        final double MIN_SCORE = 0.35;
        final int TOP_K = 24;                 // 多召回，覆盖同文档多切片场景
        final int MAX_CHUNKS_PER_DOC = 6;     // 单文档最多拼接切片数（按相关性挑选）
        final int MAX_GROUPS = 6;             // 最多保留文档组数
        final int TOTAL_CHAR_BUDGET = 8000;   // 全局拼接字符预算（本地 LLM 上下文保护）
        try {
            float[] queryEmbedding = getEmbedding(query);
            List<com.imagemanager.service.MilvusService.MilvusSearchResult> hits =
                    milvusService.search(queryEmbedding, TOP_K);

            // 1. 过滤弱相关/空内容，按 docId 分组（同文档切片聚合）
            Map<String, List<com.imagemanager.service.MilvusService.MilvusSearchResult>> byDoc =
                    new LinkedHashMap<>();
            for (com.imagemanager.service.MilvusService.MilvusSearchResult r : hits) {
                if (r.score < MIN_SCORE) continue;
                if (r.content == null || r.content.isBlank()) continue;
                String key = r.docId != null ? r.docId : ("__no_doc_" + r.fileName + "_" + r.chunkIndex);
                byDoc.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
            }

            // 2. 组内智能挑选：按 score 降序保留最强 N 片，再按 chunkIndex 升序还原原文顺序
            List<Map<String, Object>> groups = new ArrayList<>();
            for (Map.Entry<String, List<com.imagemanager.service.MilvusService.MilvusSearchResult>> e
                    : byDoc.entrySet()) {
                List<com.imagemanager.service.MilvusService.MilvusSearchResult> chunks = e.getValue();
                chunks.sort((a, b) -> Float.compare(b.score, a.score));
                int hitCount = chunks.size();
                List<com.imagemanager.service.MilvusService.MilvusSearchResult> picked =
                        new ArrayList<>(chunks.subList(0, Math.min(hitCount, MAX_CHUNKS_PER_DOC)));
                picked.sort(Comparator.comparingInt(c -> c.chunkIndex));
                Map<String, Object> group = new LinkedHashMap<>();
                group.put("chunks", picked);
                group.put("hitCount", hitCount);
                group.put("topScore", chunks.get(0).score); // chunks 保持 score 降序，get(0) 为组内最高分
                groups.add(group);
            }

            // 3. 组按组内最高分降序（相关性更强的文档优先拿到预算）
            groups.sort((a, b) -> Float.compare((Float) b.get("topScore"), (Float) a.get("topScore")));

            // 4. 全量拼接：逐组按原文顺序拼接切片，共享字符预算，超预算智能截断并标注
            List<Map<String, Object>> out = new ArrayList<>();
            int usedChars = 0;
            int processedGroups = 0;
            for (Map<String, Object> g : groups) {
                if (processedGroups >= MAX_GROUPS || usedChars >= TOTAL_CHAR_BUDGET) break;
                @SuppressWarnings("unchecked")
                List<com.imagemanager.service.MilvusService.MilvusSearchResult> picked =
                        (List<com.imagemanager.service.MilvusService.MilvusSearchResult>) g.get("chunks");
                int hitCount = (Integer) g.get("hitCount");

                StringBuilder merged = new StringBuilder();
                int mergedChunks = 0;
                boolean truncated = false;
                for (com.imagemanager.service.MilvusService.MilvusSearchResult c : picked) {
                    String piece = c.content.trim();
                    int sepLen = merged.length() > 0 ? 2 : 0;
                    int budgetLeft = TOTAL_CHAR_BUDGET - usedChars - merged.length() - sepLen;
                    if (budgetLeft <= 200) {
                        // 剩余预算太小（放不下一个有意义的切片），停止纳入本片及后续
                        truncated = true;
                        break;
                    }
                    if (merged.length() > 0) merged.append("\n\n");
                    if (piece.length() > budgetLeft) {
                        // 预算只够本片一部分：截断保留头部并显式标注
                        merged.append(piece, 0, budgetLeft - 20).append(" ……[切片截断]");
                        mergedChunks++;
                        truncated = true;
                        break;
                    }
                    merged.append(piece);
                    mergedChunks++;
                }
                usedChars += merged.length();
                if (truncated || mergedChunks < hitCount) {
                    merged.append("\n[说明：该资料共命中 ").append(hitCount)
                          .append(" 个切片，按相关性已纳入 ").append(mergedChunks)
                          .append(" 片；其余切片相关性较低或超出上下文预算，未纳入]");
                }
                if (merged.length() == 0) continue;

                com.imagemanager.service.MilvusService.MilvusSearchResult first = picked.get(0);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("content", merged.toString());
                item.put("fileName", first.fileName != null ? first.fileName : "未知文件");
                item.put("docType", first.docType != null ? first.docType : "");
                item.put("docId", first.docId != null ? first.docId : "");
                item.put("score", g.get("topScore"));
                item.put("mergedChunks", mergedChunks);
                item.put("hitChunks", hitCount);
                out.add(item);
                processedGroups++;
                log.info("[业务员资料] 文档聚合: file={}, 命中切片={}, 拼接切片={}, 最高分={}, 拼接字符={}",
                        first.fileName, hitCount, mergedChunks, g.get("topScore"), merged.length());
            }
            log.info("[业务员资料] 检索聚合完成: 命中文档组={}, 输出组={}, 总字符={}/{}",
                    groups.size(), out.size(), usedChars, TOTAL_CHAR_BUDGET);
            return out;
        } catch (Exception e) {
            log.warn("业务员资料 Milvus 检索失败: {}", e.getMessage());
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
     * 提取报价单号（形如 20250625-001S）
     */
    private String extractQuotationNo(String message) {
        if (message == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b(\\d{6,8}-[A-Za-z0-9]+)\\b").matcher(message);
        return m.find() ? m.group(1) : null;
    }

    /** 报价意图：要求报价/估价/核算/定价/利润测算时触发报价SOP */
    private boolean isQuotationIntent(String message) {
        if (message == null) return false;
        String[] kws = {
                "报价", "估价", "核算", "定价", "怎么卖", "卖多少", "多少钱",
                "报价单", "成本核算", "智能报价", "给个价", "什么价", "利润",
                "能赚", "毛利", "核算一下", "算一下成本", "报个价", "出个价",
                "给客户报", "客户报价", "报价方案", "建议价", "定价策略"
        };
        for (String kw : kws) if (message.contains(kw)) return true;
        return false;
    }

    // ========== 两大工作模式（模式A商品企划 / 模式B总经理决策辅助） ==========

    /**
     * 手动模式切换指令识别。
     * @return "planning"=切到模式A；"decision"=切到模式B；null=非切换指令
     */
    private String detectSubModeSwitch(String message) {
        if (message == null) return null;
        String m = message.replaceAll("[\\s，。！!？?、,]+", "");
        // 必须含"切换/进入/转到"等动作词，或整句就是模式名，避免把业务描述误判为切换指令
        boolean hasSwitchVerb = m.contains("切换") || m.contains("进入") || m.contains("转到") || m.contains("启用");
        boolean namesPlanning = m.contains("商品企划模式") || m.contains("企划模式") || m.contains("业务员模式") || m.contains("模式A");
        boolean namesDecision = m.contains("总经理决策辅助模式") || m.contains("决策辅助模式") || m.contains("总经理模式") || m.contains("模式B");
        if (namesPlanning && (hasSwitchVerb || m.length() <= 12)) return "planning";
        if (namesDecision && (hasSwitchVerb || m.length() <= 14)) return "decision";
        return null;
    }

    /**
     * 自动子模式识别（无手动指令时根据输入内容判断）。
     * 仅在用户输入明确指向某模式的业务场景时触发；模糊输入返回 null（不强行进模式）。
     */
    private String detectSubModeAuto(String message) {
        if (message == null) return null;
        // 模式B信号：总经理视角的经营决策议题
        String[] decisionKws = {
                "接单可行性", "产能与订单", "机台缺口", "空置率", "客户流失预警", "撬单",
                "保单", "放单", "业务员效能", "人效", "客户分配", "利润评估", "可压缩项",
                "A/B/C", "ABC方案", "备选方案", "决策", "经营分析", "总经理"
        };
        for (String kw : decisionKws) if (message.contains(kw)) return "decision";
        // 模式A信号：商品企划全流程场景
        String[] planningKws = {
                "商品企划", "企划案", "企划任务卡", "品牌调研", "调研快照", "品类树",
                "价格带", "新品机会", "SKU", "打样", "深化方向", "机会评分", "五感体验",
                "竞品拆解", "商品结构", "扩品"
        };
        for (String kw : planningKws) if (message.contains(kw)) return "planning";
        return null;
    }

    /**
     * 解析当前会话应处的子模式：手动指令 > 会话记忆 > 自动识别（识别成功写入记忆）。
     */
    private String resolveBusinessSubMode(String convId, String message) {
        String manual = detectSubModeSwitch(message);
        if (manual != null) {
            if (convId != null) businessSubModeMap.put(convId, manual);
            log.info("业务子模式手动切换: convId={}, subMode={}", convId, manual);
            return manual;
        }
        String remembered = convId != null ? businessSubModeMap.get(convId) : null;
        if (remembered != null) return remembered;
        String auto = detectSubModeAuto(message);
        if (auto != null && convId != null) {
            businessSubModeMap.put(convId, auto);
            log.info("业务子模式自动识别: convId={}, subMode={}", convId, auto);
        }
        return auto;
    }

    /**
     * LLM 通用逻辑规则层（factory/designer 两模式共享，凌驾于具体能力规则）：
     * 1. 输入处理规则：五类输入材料自动识别 + 四级采信优先级 + 冲突时「数据差异说明」
     * 2. 上下文延续规则：多轮继承已确认设定 + 仅修改指定模块 + 「本次修订/沿用前版」双清单标注
     * 3. 真实性约束：仅基于输入材料推导 + 缺失信息标注【假设项】及默认取值范围
     * 4. 落地性要求：每个逻辑节点三要素（输入→规则→输出），禁止空泛口号式表述
     * 5. 结构化方案类交付物的七节固定输出结构（一级模块不得增减）
     */
    private String buildUniversalLogicRules() {
        return "\n\n【LLM通用逻辑规则（凌驾于所有能力规则，逐条强制执行）】" +
                "\n\n一、输入处理规则" +
                "\n- 自动识别本轮全部输入材料并归类：①需求描述（用户当前指令）②数据表（供应链/工厂业务数据、业务员资料库）③规则文档（系统业务规则、SOP、知识库文档、岗位知识卡片）④外部参考数据（网络搜索、历史专业问答）⑤历史对话（用户历史多轮提问记录与已确认设定）。" +
                "\n- 采信优先级（从高到低，与上下文注入段落的〔数据源优先级〕标注一致）：L1 内部业务规则（系统提示词业务规则与铁律、岗位知识卡片经验规则）> L2 本地业务数据（供应链/工厂业务数据、业务员资料库、结构化查询结果、图片库）> L3 内部知识文档（向量知识库召回文档）> L4 外部参考数据（网络搜索——用于获取品牌/客户最新动态与产品信息，仅作背景参考，不得作为内部业务结论的依据；引用时标注【外部调研】(来源N,置信度估算)；历史专业问答）> L5 用户指令与历史对话设定（用户口头描述的数字与说法）。" +
                "\n- 数据冲突时必须明确标注「数据差异说明」：逐项列出冲突字段、各数据源的值、采信结果与采信理由；禁止静默覆盖任何一个数据源的值。" +
                "\n\n二、上下文延续规则" +
                "\n- 多轮对话自动继承本会话所有已确认的设定、参数、逻辑模块（以【用户历史多轮对话提问记录】为准）；用户未明确推翻的条目全部沿用，不得遗漏或擅自重置。" +
                "\n- 收到修改/调整指令时，仅修改用户指定的模块，其余模块原样保留，禁止擅自改动未提及的部分。" +
                "\n- 涉及方案/逻辑/参数调整的输出，必须标注两个清单：「本次修订模块」（改了什么、依据是什么）与「沿用前版模块」（未改动部分原样列出）。" +
                "\n\n三、真实性约束" +
                "\n- 所有逻辑、参数、规则必须基于本轮注入的输入材料推导；输入材料中没有依据的结论不得输出。" +
                "\n- 缺失信息必须明确标注【假设项】并给出默认取值范围（格式如：【假设项】损耗率3%-5%，默认取4%，待用户确认）；禁止凭空编造业务数据填补空白。" +
                "\n- 【关键数字四要素】(源自《企业数字中台_本周两大模块工作方案》)所有关键数字必须同时显示四要素：来源（内部数据库/外部调研/AI推断）、数据日期、缺失项说明、置信度；四要素不全的关键数字不得作为决策依据。" +
                "\n- 【外部数据交叉验证】外部网络数据可靠性不一，涉及价格带、市场趋势、竞品动态的关键结论必须有>=2个独立来源交叉印证；仅有单一来源且无法验证时，必须标注「单一来源，未经交叉验证」并降低置信度。" +
                "\n- 【人机决策边界】AI仅承担信息汇总、分析测算与方案建议职责，不替代人做最终决策；输出涉及立项、报价确认、客户承诺、重大资源投入类建议时，必须标注「最终决策由业务/管理人员确认」。" +
                "\n\n四、落地性要求" +
                "\n- 每个逻辑节点必须定义三要素：输入是什么 → 按什么规则处理 → 输出什么；三要素不全的节点视为无效节点，必须补全或删除。" +
                "\n- 禁止空泛描述与口号式表述（如「加强管理」「提升效率」「优化流程」），所有规则必须可执行、可校验（有明确判定条件、数据来源或操作步骤）。" +
                "\n\n五、结构化方案固定输出结构" +
                "\n- 当用户要求输出业务逻辑设计、规则设计、方案设计、流程规划类交付物时，输出正文必须严格遵循以下固定结构（一级模块不得增减）：" +
                "\n  一、业务目标与边界定义" +
                "\n  二、输入要素与数据源清单" +
                "\n  三、核心业务逻辑链路（主流程+分支流程）" +
                "\n  四、关键节点规则与判定条件" +
                "\n  五、异常场景与边界兜底规则" +
                "\n  六、成果输出与校验指标" +
                "\n  七、风险提示与优化建议" +
                "\n- 该结构下每个模块的内容必须落实第四条落地性要求（节点三要素齐全、无空泛表述）。" +
                "\n- 【兼容规则（重要）】此七节结构仅约束交付物正文的组织方式，不得吞并或取代以下既有输出要求，两者必须同时满足：" +
                "\n  a. 当前工作模式（模式A企划/模式B决策）的多轮交互规则照常执行：过程轮次每轮末尾仍必须输出【可选操作菜单】（编号选项+功能说明，提示用户回复对应编号推进），禁止跳过业务步骤；" +
                "\n  b. 数据来源标注规则照常执行：正文内每条关键信息/结论的数据支撑仍按【外部调研】(来源N或日期,置信度)/【内部数据库-{库名}】/【AI推断】格式内联标注，并在回答末尾汇总【引用来源】清单；" +
                "\n  b1. 外部引用核对铁律：标注【外部调研】(来源N)前，必须核对【网络搜索参考数据】中该来源N的正文或摘要确实包含所引用的数据/结论；来源N的内容与引用结论无关或不含该数据时，禁止标注该来源，应改标【AI推断】或注明'未检索到直接证据'。严禁仅因来源编号存在就虚构引用关系；" +
                "\n  c. 过程轮次不输出完整七节终稿（仍受基础约束第3条约束），用户明确要求终稿/完整报告时才一次性输出七节结构全文。" +
                "\n  d. 模式专属交付物结构优先：模式A商品企划的《商品企划案V1.0》（基础开发信息/调研结论/机会评分/商品结构/风险与下一步）与模式B决策辅助的六节决策报告（决策问题→事实底座→六维判断→A/B/C方案→系统建议→执行动作）遵循各自模式模板；通用七节结构仅在两模式模板未覆盖的场景生效。" +
                "\n- 简单问答/数据查询/单点计算场景不适用此结构，按核心能力A/B/C/D对应格式回答。";
    }

    /** 两大模式共享的基础约束（文档7条铁律 + 结构化数据强制规则 + 禁止行为清单 + 终稿输出格式） */
    private String buildBusinessBaseConstraints() {
        return "\n\n【基础约束（铁律，两模式通用）】" +
                "\n1. 禁止编造企业内部业务数据；内部数据仅来自系统注入的上下文，数据缺失必须明确列出【缺失项清单】，不得强行输出确定结论。" +
                "\n2. 所有关键信息强制标记来源：【外部调研】/【内部数据库-{库名}】/【AI推断】，并附数据更新日期与置信度(0-100)。" +
                "\n3. 多轮分步交互：过程轮次禁止一次性输出完整终稿，每轮末尾提供【可选操作菜单】由业务人员选择分支推进，禁止跳过业务步骤；" +
                "但当用户明确要求'生成终稿/汇总输出/完整报告/定稿'时，执行第10条一次性输出完整终稿。" +
                "\n4. 识别重大质量、合规、客户信用风险时执行一票否决，并写明否决理由。" +
                "\n5. 内部可调用数据库集合（仅使用已注入数据）：历史订单数据库、客户画像数据库、产品研发数据库、工艺设备数据库、报价成本数据库、库存质量数据库、商品库数据库。" +
                "映射关系：报价成本数据库=【报价单计算/供应链数据】；历史订单数据库=【销售订单(order_xs_list)：真实成交/业务员ywyname/交期jh_date】；客户画像数据库=【客户订单统计+业务员资料库】；" +
                "产品研发数据库=【知识库文档】；工艺设备数据库=【丝袜工艺单(order_sw_gongyidan)：克重/秒数/制成率/机型/针数/理论产量/缝拼克重pfkz】+【内衣工艺单(order_jfk_gongyidan)：品名/设计师/染色厂/打样版号】+【部件工艺数据】；库存质量数据库=【供应链库存数据】；" +
                "商品库数据库=【商品库文件夹(goods_library)：以品名+货号命名的文件夹，含发起人/打样员/客户/订单号/备注与主图/侧面图/细节图/产品图】。" +
                "\n6. 数据清洗不全、外部数据可信度低时，如实告知覆盖范围与局限，不输出确定性业务结论。" +
                "\n7. 阶段成果输出完成后提示：成果可同步飞书，并附使用说明、测试记录、遗留问题清单。" +
                "\n8.【结构化数据强制规则】只要上下文【结构化数据库查询结果】附带了产能排产、客户订单、销售订单、业务员效能、工艺单参数结构化查询结果，" +
                "必须100%基于给到的结构化数据分析，禁止编造任何不在返回结果内的产能数字、订单数据、业务员绩效指标、客户数据、工艺参数；不得脱离给出的数据空谈结论。" +
                "\n8.1【商品库图片强制展示规则】上下文附带【商品库文件夹】条目时：商品信息（品名/货号/客户/订单号/备注）须与工艺单、销售订单、报价数据综合分析后一并作答；" +
                "条目中附带的 主图/侧面图/细节图/产品图图片URL 为 24 小时有效的签名地址，【强制】凡条目中含图片URL字段，必须在回答末尾的『商品图片』小节中用 markdown 图片语法逐张展示（如 ![主图](URL)、![侧面图](URL)），URL 必须原样完整输出、禁止省略/截断/转义/替换/编造；" +
                "同时图片已作为视觉输入传入你的多模态模型，你可以直接观察图片内容，回答颜色、款式、花型、细节工艺等外观问题，描述必须基于实际看到的图片，禁止凭空想象。" +
                "\n9.【禁止行为清单】" +
                "a.未收到【排产表/业务员绩效结构化查询结果】时，禁止输出产能订单匹配分析、业务员效能分析板块，应主动提示：缺少结构化查询数据，请先触发结构化数据检索；" +
                "b.禁止'需要提升产能、业务员加强跟进客户'这类流于形式、无数据支撑的空话，所有结论必须附带上下文给到的数据依据；" +
                "c.文档内所有日期时间字段严格使用上下文给出的标准日期文本，禁止输出Excel序列号数字。" +
                "\n10.【终稿输出格式】用户要求终稿时：整合本次会话历史所有轮次用户提出的全部问题与需求（见上下文【用户历史多轮对话提问记录】），" +
                "一次性输出完整终稿，不再碎片化分段；结构分板块：基础开发信息、供应链情况、报价分析、产能-订单匹配评估、业务员效能分析、风险提示、落地行动计划；" +
                "数据分析板块每一条结论后面标注数据来源（结构化排产数据 / 业务员绩效数据 / 知识库）；" +
                "末尾提示：本文档为标准富文本文档，可点击消息右上角导出按钮下载PDF。";
    }

    /** 模式A：业务员互动式商品企划模式 prompt */
    private String buildPlanningModePrompt(boolean justSwitched) {
        return "\n\n【当前工作模式：模式A-业务员互动式商品企划模式】" +
                "\n定位：AI与业务员多轮共创企划。业务员掌握一线信息与商业判断；你负责外部调研、内部库查询、机会筛选、竞争力分析、结构化输出。严格执行7步流程：" +
                "\n步骤1·最小需求输入：用户输入客户名/品牌名/品类即可启动（可选补充国家、渠道、价格、季节、参考图）。识别任务与资料缺口，输出【企划任务卡】，不强制一次性补齐字段。" +
                "\n步骤2·品牌/品类调研：输出【品牌调研快照】：品牌定位、客群、品类树、价格带、渠道、竞品、近期动作；允许用户选择直接公开分析或补充内部资料。" +
                "\n步骤3·深化方向选择：输出至少5个选项菜单，等待用户选1-3项或自定义，选定后进入对应分支，禁止重复输出完整调研报告：" +
                "\n  A｜分析品牌全部品类（商品结构/主力/增长/空白） B｜筛选与我司关联度高的品类（设备/工艺/材料/产能/研发积累） C｜比对历史订单和合作记录（采购偏好/价格接受度/复购/毛利）" +
                " D｜分析消费者、场景和五感体验 E｜拆解竞品产品和价格（差异化/可复制点/同质化/低价风险） F｜调用产品研发库（可复用样品/BOM/工艺/失败经验）" +
                " G｜测算成本利润和报价（沿用报价SOP四步） H｜评估设备产能与交期 I｜形成3-5个新品机会（证据充分进入SKU定义） J｜业务员自定义问题" +
                "\n步骤4·内部数据比对：接收业务员补充的客户背景、现场信息、参考图、合作判断；调用内部库输出【内外部关联分析】。" +
                "\n步骤5·竞争力与机会评分（固定权重）：客户战略价值15%、市场机会15%、历史订单验证15%、产品差异化15%、制造可行性15%、成本利润10%、开发速度5%、渠道适配5%、经营风险5%。" +
                "分级：>=80优先打样；65-79补证据立项；50-64观察；<50暂不推进；重大风险一票否决。输出机会排序+淘汰理由。" +
                "\n步骤6·商品企划共创：确认主题、SKU数量、价位、渠道、优先级；输出【商品企划案草案】：3-5个SKU矩阵、产品定义、成本产能、打样计划、客户提案草案。" +
                "\n步骤7·多轮深化收口：支持选择视觉、成本、竞品、打样、渠道、话术继续迭代；记录人工修正；当用户要求终稿时，整合历史全部轮次需求一次性输出【商品企划案V1.0】（按基础约束第10条终稿格式），列明未决业务问题，末尾提示可导出PDF。" +
                "\n推进规则：仅客户/品牌/品类即可启动；首轮必须给出>=5个深化选项；选定分支定向执行；每轮末尾给可选菜单；严禁跳步；过程轮次不输出完整终稿，用户明确要求终稿时一次性输出。" +
                (justSwitched ? "\n【本轮动作】用户刚切换到商品企划模式，请确认模式已激活，输出欢迎语+【企划任务卡】模板，引导用户输入客户名/品牌名/品类。"
                              : "\n【本轮动作】按当前所处步骤推进；若用户仅给了客户/品牌/品类，输出【企划任务卡】并进入步骤2；若用户已选定分支，定向执行该分支并给出下一菜单。");
    }

    /** 模式B：总经理决策辅助模式 prompt */
    private String buildDecisionModePrompt(boolean justSwitched) {
        return "\n\n【当前工作模式：模式B-总经理决策辅助模式】" +
                "\n定位：仅做信息汇总分析，不做最终决策。六大分析维度：" +
                "\n1.产能与订单匹配：未来1-2月接单可行性、机台工种缺口；输出空置率、人力缺口、机台冲突、接单排期建议。" +
                "\n2.动态客户经营：客户增减、我方份额、撬单风险；输出客户阶段、流失预警、保单/放单建议。" +
                "\n3.外部环境与趋势：汇率、行业、政策、国际事件；输出受影响客户品类、应对动作。" +
                "\n4.研发新品匹配：新品客户适配与定制；输出客户-新品匹配、优化点、推广优先级。" +
                "\n5.业务员效能：区分业绩来自环境或个人，客户负载；输出能力诊断、异常原因、人效、客户分配建议。" +
                "\n6.报价与利润：订单盈利评估，报价不足可压缩项；输出标准成本、利润区间、可压缩项，输出A/B/C报价方案（成本数据沿用报价SOP口径）。" +
                "\n首期边界：账期、定金、完整现金流预测属后续迭代项，涉及相关内容必须标注【后续迭代】。" +
                "\n\n【报告模板（严格按此结构输出）】" +
                "\n1.【决策问题】：待总经理决策事项" +
                "\n2.【事实底座】：内部数据、外部环境，标注数据更新时间、缺失字段、整体置信度" +
                "\n3.【六维判断】：产能｜客户｜外部环境｜研发｜业务员效能｜报价利润分别说明影响；每维结论后标注数据来源（结构化排产数据 / 客户订单统计 / 业务员绩效数据 / 知识库）；无结构化数据支撑的维度按禁止行为清单处理，不输出该板块并提示缺失" +
                "\n4.【A/B/C可选方案】：每套含方案简述、预期收益、付出成本、潜在风险、资源占用、方案触发条件" +
                "\n5.【系统参考建议】：方案优先级及理由；必须标注：⚠️本建议仅参考，最终决策由总经理确认" +
                "\n6.【执行动作】：建议负责人、完成期限、风险预警条件、复盘节点" +
                "\n严禁替总经理做最终决策；只输出A/B/C备选方案；完整报告输出后提示可点击导出按钮下载PDF。" +
                (justSwitched ? "\n【本轮动作】用户刚切换到总经理决策辅助模式，请确认模式已激活，输出六大分析维度简介，并引导用户提出待决策事项。"
                              : "\n【本轮动作】围绕用户提出的决策议题，严格按报告模板输出；数据缺失项如实列出，不强行下结论。");
    }

    /** 排产意图：排产/批次/交期/投产/生产安排 */
    private boolean isSchedulingIntent(String message) {
        String[] kws = {"排产", "批次", "交期", "投产", "生产安排", "排期", "甘特", "产能"};
        for (String kw : kws) if (message.contains(kw)) return true;
        return false;
    }

    /** 部件意图：部件/款式/尺码/工艺 */
    private boolean isPartsIntent(String message) {
        String[] kws = {"部件", "款式", "尺码", "工艺", "大身", "袖子", "腰口"};
        for (String kw : kws) if (message.contains(kw)) return true;
        return false;
    }

    /** 物料/供应商意图：原料/辅料/供应商/采购/库存 */
    private boolean isMaterialIntent(String message) {
        String[] kws = {"原料", "辅料", "供应商", "采购", "库存", "物料", "面料"};
        for (String kw : kws) if (message.contains(kw)) return true;
        return false;
    }

    /**
     * 报价单查询+确定性计算，返回可直接注入上下文的结果
     */
    private List<Map<String, Object>> searchQuotation(String query) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            String dh = extractQuotationNo(query);
            String matchedCustomer = null;
            List<Map<String, Object>> rows;
            if (dh != null) {
                rows = quotationCalcService.queryByDh(dh);
            } else {
                // 客户名称反向匹配：问题文本包含库内客户名（如"海宁世正有多少单号"→海宁世正）
                matchedCustomer = quotationCalcService.matchKhnameInQuery(query);
                rows = (matchedCustomer != null)
                        ? quotationCalcService.queryByKhname(matchedCustomer)
                        : quotationCalcService.queryByKeyword(query);
            }
            // 客户维度：先给统计条目（总数+全部单号），模型才能准确回答"有多少单号"
            if (matchedCustomer != null) {
                int total = quotationCalcService.countByKhname(matchedCustomer);
                List<String> dhs = quotationCalcService.listDhByKhname(matchedCustomer);
                Map<String, Object> aggData = new LinkedHashMap<>();
                aggData.put("客户名称", matchedCustomer);
                aggData.put("单号总数", total);
                aggData.put("单号列表", String.join(", ", dhs));
                Map<String, Object> agg = new LinkedHashMap<>();
                agg.put("type", "报价单统计");
                agg.put("summary", "客户 " + matchedCustomer + " 的报价单统计：共 " + total + " 个单号");
                agg.put("data", aggData);
                out.add(agg);
            }
            // 明细条目最多5条，避免上下文过大；统计问题靠上面的聚合条目回答
            int detailLimit = (matchedCustomer != null) ? 5 : rows.size();
            // 循环外批量预取工艺单缝拼克重（一次 IN 查询，消除逐行查库的 N+1）
            Map<String, BigDecimal> fpkzBatch = quotationCalcService.batchLookupProcessSewingWeights(
                    rows.stream().limit(detailLimit)
                            .map(qrow -> String.valueOf(qrow.getOrDefault("huohao", ""))).toList());
            int idx = 0;
            for (Map<String, Object> row : rows) {
                if (idx++ >= detailLimit) break;
                Map<String, BigDecimal> extra = new HashMap<>();
                BigDecimal prefetched = fpkzBatch.get(String.valueOf(row.getOrDefault("huohao", "")));
                if (prefetched != null) extra.put("fpkzFallback", prefetched);
                Map<String, BigDecimal> calc = quotationCalcService.calculate(row, extra);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("报价单号", row.get("dh"));
                data.put("客户名称", row.get("khname"));
                data.put("生产货号", row.get("huohao"));
                data.put("尺码", row.get("chima"));
                // 原始输入字段（供模型展示完整核算方案的输入项）
                data.put("下机时间", row.get("zhis"));
                data.put("利用率", row.get("lyl"));
                data.put("机台费", row.get("sbdj"));
                data.put("正品率", row.get("zpl"));
                data.put("原料利用率", row.get("yllyl"));
                data.put("辅料利用率", row.get("fllyl"));
                data.put("运费", row.get("yunfei"));
                // 上游新增字段（染色/缝拼/腰口/全检，作为核算输入项展示）
                data.put("缝拼克重", row.get("fpkz"));
                data.put("染色单价", row.get("rsdj"));
                data.put("染色成本(表内)", row.get("rsprice"));
                data.put("腰口工价", row.get("ykgj"));
                data.put("全检工价", row.get("qjprice"));
                data.putAll(calc);
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("type", "报价单计算");
                r.put("summary", "报价单 " + row.get("dh") + " 成本利润计算结果");
                r.put("data", data);
                out.add(r);
            }
        } catch (Exception e) {
            log.warn("报价单查询失败: {}", e.getMessage());
        }
        return out;
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

    /**
     * 从PDF字节数组中提取文本内容
     */
    private String extractTextFromPdf(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (Exception e) {
            log.warn("PDF文本提取失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 下载图片并转为Base64编码（供多模态模型使用）
     * 优先通过FileStorageService从OSS直接读取（不依赖签名URL过期），
     * 回退到HTTP下载签名URL
     * 限制：图片大小不超过5MB
     */
    private String downloadImageAsBase64(String imageUrl) {
        try {
            if (imageUrl == null || imageUrl.isEmpty()) return null;

            // 方式1：优先通过S3 SDK直接读取（不依赖签名URL，不会过期）
            try {
                String storageKey = fileStorageService.getStorageKey(imageUrl);
                if (storageKey != null && !storageKey.isEmpty() && !storageKey.startsWith("http")) {
                    try (InputStream is = fileStorageService.getFileInputStream(storageKey)) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        byte[] buffer = new byte[8192];
                        int len;
                        int totalRead = 0;
                        while ((len = is.read(buffer)) != -1) {
                            baos.write(buffer, 0, len);
                            totalRead += len;
                            if (totalRead > 5 * 1024 * 1024) {
                                log.debug("OSS图片超过5MB限制: key={}", storageKey);
                                return null;
                            }
                        }
                        String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
                        log.debug("从OSS直接读取图片成功: key={}, size={}", storageKey, totalRead);
                        return base64;
                    }
                }
            } catch (Exception e) {
                log.debug("从OSS直接读取失败，尝试HTTP下载: key={}, error={}", imageUrl, e.getMessage());
            }

            // 方式2：回退到HTTP下载签名URL
            HttpURLConnection conn = (HttpURLConnection) URI.create(imageUrl).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            int code = conn.getResponseCode();
            if (code != 200) return null;
            String contentType = conn.getContentType();
            if (contentType != null && !contentType.startsWith("image/")) return null;
            int contentLength = conn.getContentLength();
            if (contentLength > 5 * 1024 * 1024) return null; // 超过5MB跳过
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (InputStream is = conn.getInputStream()) {
                byte[] buffer = new byte[8192];
                int len;
                int totalRead = 0;
                while ((len = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                    totalRead += len;
                    if (totalRead > 5 * 1024 * 1024) return null; // 安全限制
                }
            }
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            log.debug("下载图片失败: url={}, error={}", imageUrl, e.getMessage());
            return null;
        }
    }

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
            "翻译一下", "翻译成",
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
     * 企划/市场调研意图识别：无需用户明确说"联网/全网搜索"，命中即自动联网。
     * 典型场景："宝娜斯 丝袜 中国 抖音电商 中高端 2026秋冬"（品牌+品类+渠道+定位+季节组合）。
     *
     * 触发规则（命中任一）：
     * 1. 企划动作词：企划/策划/规划/方案/调研/趋势/洞察/市场分析/新品开发/上市计划等
     * 2. 季节年份：2024-2039 + 春/夏/秋/冬（如"2026秋冬"）
     * 3. 渠道/定位/市场词（需消息长度>8）：抖音/电商/直播/天猫/中高端/竞品/价格带/目标客群等
     * 4. 品牌+品类组合：外文品牌（如"addidas 内衣"）或已知中文品牌（如"阿迪达斯内衣"）+ 服装/袜类品类词
     *
     * 排除项：含产品编码或报价核算强信号的纯内部业务查询（报价/成本/单价/入库/排产等），
     * 这类问题必须走内部数据，联网反而引入噪声。
     */
    private boolean isPlanningResearchIntent(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase().trim();

        // 排除：纯内部业务查询强信号（产品编码查询、报价核算、库存排产等）
        String[] internalSignals = {
                "报价", "多少钱", "核算", "单价", "净成本", "成本多少",
                "入库", "库存", "排产", "原料用量", "采购价", "供应商对比"
        };
        for (String s : internalSignals) {
            if (lower.contains(s)) {
                return false;
            }
        }

        // 组1：企划动作词（强信号，直接触发）
        String[] planningWords = {
                "企划", "策划", "规划", "方案", "调研", "趋势", "洞察",
                "市场分析", "新品开发", "开发计划", "上市计划", "商品计划",
                "品牌动态", "行业趋势", "市场趋势", "消费者洞察", "竞品分析"
        };
        for (String w : planningWords) {
            if (lower.contains(w)) {
                return true;
            }
        }

        // 组2：季节年份组合（如"2026秋冬"、"2025春夏"）
        if (lower.matches(".*20[2-3]\\d.{0,3}(春|夏|秋|冬).*")) {
            return true;
        }

        // 组3：渠道/定位/市场上下文词（需一定信息量，避免单词误触发）
        if (lower.length() > 8) {
            String[] marketWords = {
                    "抖音", "电商", "直播", "天猫", "淘宝", "拼多多", "京东",
                    "中高端", "高端", "低端", "价格带", "目标客群", "市场份额",
                    "消费者", "人群定位", "品牌定位"
            };
            for (String w : marketWords) {
                if (lower.contains(w)) {
                    return true;
                }
            }
        }

        // 组4：品牌+品类组合（如"addidas 内衣"、"阿迪达斯内衣"、"浪莎袜业"）
        // 品牌词（外文品牌字母串或已知中文品牌）+ 服装/袜类品类词 → 属市场/企划类问题，自动联网
        if (containsBrandCategoryCombo(lower)) {
            return true;
        }

        return false;
    }

    /**
     * 品牌+品类组合判定（供企划意图识别调用）：
     * 消息中同时出现「品类词（内衣/丝袜/袜子等）」和「品牌词」即认为命中。
     * 品牌词两种来源：
     * 1. 已知中文品牌清单（阿迪达斯/耐克/浪莎/宝娜斯等）
     * 2. 连续≥3个英文字母的外文品牌串（addidas/adidas/nike/uniqlo等），
     *    排除 SKU/BOM/AQL 等业务缩写与常见英文虚词，避免内部编码误触发
     */
    private boolean containsBrandCategoryCombo(String lower) {
        String[] categoryWords = {
                "内衣", "丝袜", "袜子", "袜业", "文胸", "内裤", "打底裤", "光腿神器",
                "睡衣", "家居服", "泳衣", "泳装", "运动服", "卫衣", "服饰", "服装", "家纺", "羊绒衫"
        };
        boolean hasCategory = false;
        for (String c : categoryWords) {
            if (lower.contains(c)) {
                hasCategory = true;
                break;
            }
        }
        if (!hasCategory) {
            return false;
        }

        // 已知中文品牌
        String[] knownBrands = {
                "阿迪达斯", "耐克", "优衣库", "李宁", "安踏", "特步", "鸿星尔克", "361",
                "彪马", "迪卡侬", "浪莎", "梦娜", "宝娜斯", "耐尔", "振汉",
                "南极人", "恒源祥", "猫人", "三枪", "都市丽人", "爱慕", "曼妮芬",
                "蕉内", "蕉下", "有棵树", "全棉时代"
        };
        for (String b : knownBrands) {
            if (lower.contains(b)) {
                return true;
            }
        }

        // 外文品牌：连续≥3个英文字母（排除业务缩写与常见英文虚词）
        String[] stopTokens = {
                "sku", "bom", "aql", "fob", "oem", "odm", "pdf", "ppt", "excel", "word",
                "api", "llm", "gpt", "the", "and", "for", "you", "what", "why", "how",
                "who", "sop", "crm", "erp", "saas", "top", "new"
        };
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[a-zA-Z]{3,}").matcher(lower);
        while (m.find()) {
            String token = m.group().toLowerCase();
            boolean isStop = false;
            for (String s : stopTokens) {
                if (s.equals(token)) {
                    isStop = true;
                    break;
                }
            }
            if (!isStop) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从企划问题中提取品牌名（客户名）。
     * 企划输入格式约定："客户名/品牌名/品类启动企划"（如"宝娜斯 保暖袜"）。
     * 提取顺序：
     * 1. 已知中文品牌清单（返回标准品牌名）
     * 2. 外文品牌串（连续≥3个字母，排除业务缩写，返回原始大小写形式）
     * 提取不到返回空串。
     */
    private String extractBrandName(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String lower = message.toLowerCase();
        String[] knownBrands = {
                "阿迪达斯", "耐克", "优衣库", "李宁", "安踏", "特步", "鸿星尔克", "361",
                "彪马", "迪卡侬", "浪莎", "梦娜", "宝娜斯", "耐尔", "振汉",
                "南极人", "恒源祥", "猫人", "三枪", "都市丽人", "爱慕", "曼妮芬",
                "蕉内", "蕉下", "有棵树", "全棉时代"
        };
        for (String b : knownBrands) {
            if (lower.contains(b)) {
                return b;
            }
        }
        String[] stopTokens = {
                "sku", "bom", "aql", "fob", "oem", "odm", "pdf", "ppt", "excel", "word",
                "api", "llm", "gpt", "the", "and", "for", "you", "what", "why", "how",
                "who", "sop", "crm", "erp", "saas", "top", "new"
        };
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[A-Za-z]{3,}").matcher(message);
        while (m.find()) {
            String token = m.group();
            String tl = token.toLowerCase();
            boolean isStop = false;
            for (String s : stopTokens) {
                if (s.equals(tl)) {
                    isStop = true;
                    break;
                }
            }
            if (!isStop) {
                return token;
            }
        }
        return "";
    }

    /**
     * 从企划问题中提取品类词（保暖袜/丝袜/打底裤/内衣等），多个品类用"、"连接。
     * 长词优先排列避免子串误匹配；提取不到返回空串。
     */
    private String extractCategoryWords(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String[] categoryWords = {
                "保暖袜", "光腿神器", "加绒打底裤", "打底裤", "连裤袜", "堆堆袜", "船袜",
                "丝袜", "袜子", "袜业", "内衣", "文胸", "内裤", "睡衣", "家居服",
                "泳衣", "泳装", "运动服", "卫衣", "羊绒衫", "服饰", "服装", "家纺"
        };
        StringBuilder sb = new StringBuilder();
        for (String c : categoryWords) {
            if (message.contains(c)) {
                // 子串去重：已选长词若包含当前短词的语义（如"保暖袜"已选则"袜子"不再追加"袜子"，但"连裤袜"独立保留）
                if ("袜子".equals(c) && (sb.toString().contains("保暖袜") || sb.toString().contains("丝袜") || sb.toString().contains("连裤袜"))) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append("、");
                }
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 企划守卫：从历史对话定位最近一次企划主题（品牌×品类）。
     * 从后往前找第一条"品牌+品类"都提取成功的用户消息；找不到返回 null。
     */
    private String findLastPlanningTopic(List<Map<String, Object>> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            Map<String, Object> m = history.get(i);
            if (!"user".equals(String.valueOf(m.get("role")))) {
                continue;
            }
            String content = String.valueOf(m.getOrDefault("content", ""));
            String brand = extractBrandName(content);
            String cats = extractCategoryWords(content);
            if (!brand.isEmpty() && !cats.isEmpty()) {
                return brand + "×" + cats;
            }
        }
        return null;
    }

    /**
     * 企划守卫：判断上一轮企划是否仍在进行中。
     * 依据：最后一条助手回复含【可选操作菜单】即未终稿（模式A过程轮次末尾必输出菜单，终稿不输出）。
     */
    private boolean isPlanningInProgress(List<Map<String, Object>> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            Map<String, Object> m = history.get(i);
            if (!"assistant".equals(String.valueOf(m.get("role")))) {
                continue;
            }
            String content = String.valueOf(m.getOrDefault("content", ""));
            return content.contains("可选操作菜单");
        }
        return false;
    }

    /**
     * 企划守卫：识别"放弃当前企划/开启新企划"确认词。
     * 命中后若消息自带新主题则直接切换；若为裸指令则由守卫接管被拦截的新主题。
     */
    private boolean isNewPlanningConfirm(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String[] confirmWords = {
                "开始新企划", "开启新企划", "新企划", "新的企划",
                "换个品牌", "换个主题", "换主题", "换品牌", "换个品类",
                "放弃之前", "放弃当前", "不管之前", "不管刚才", "重新开始", "重新企划"
        };
        for (String w : confirmWords) {
            if (message.contains(w)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 企划守卫：构建上下文隔离声明（注入 knowledgeContext 最前，最高优先级）。
     * 告知本地LLM：旧主题数据全部作废，禁止沿用/引用/类比/混入新主题。
     *
     * @param oldTopic 旧主题（null 表示未知，仅声明历史企划作废）
     * @param newTopic 新主题
     */
    private String buildPlanningIsolationNotice(String oldTopic, String newTopic) {
        String oldPart = oldTopic != null ? "「" + oldTopic + "」" : "此前的";
        return "\n\n## 【上下文隔离声明·最高优先级】\n"
                + "用户已开启全新企划主题「" + newTopic + "」。此前会话中" + oldPart + "企划的全部内容已作废：\n"
                + "历史对话中与旧主题相关的所有调研数据、产品参数、价格带、成本数字、评分与结论，"
                + "一律禁止沿用、引用、类比或混入当前企划；历史消息仅保留流程格式与交互方式的参考价值。\n"
                + "若历史消息中的旧主题数据与当前上下文（内部数据/联网采集报告）冲突，以当前新主题数据为准；"
                + "确需引用旧主题数据做对比时，必须显式声明「以下为旧主题历史数据，仅供对比」。\n";
    }

    /**
     * 品牌官方产品参数精准采集任务模板（用户指定）：
     * 围绕指定品牌从官网提取产品官方规格/材质/工艺等硬参数；官网有数据时禁止电商，
     * 电商仅作官网缺失时的兜底；溯源URL为必填字段（模板要求，故采集报告保留URL）。
     *
     * @param brand         提取到的品牌名（客户名）
     * @param categories    提取到的目标品类（"、"连接）
     * @param targetProduct 指定产品（可选，留空则全品类采集）
     */
    private String buildOfficialProductParamQuery(String brand, String categories, String targetProduct) {
        return "# 任务：品牌官方产品参数精准采集\n" +
                "## 核心目标\n" +
                "围绕指定品牌，优先从品牌官方网站提取产品的官方规格、材质、工艺等硬参数；官网有数据时禁止使用电商平台内容，电商仅作为官网缺失时的兜底补充，禁止输出促销、评价、品牌故事等无效信息。\n\n" +
                "## 数据源优先级（严格按顺序执行，高优先级有数据则禁用低优先级）\n" +
                "1.【第一优先级·唯一权威】品牌官方网站：优先抓取官网「产品中心」「产品系列」「产品详情」「技术参数」「规格说明」页；采信官方发布的产品名称、型号/货号、材质成分、尺寸规格、工艺技术、功能参数、官方定价；严格排除公司介绍、新闻动态、招商加盟、品牌故事、企业荣誉等非产品参数内容。\n" +
                "2.【第二优先级·补充】行业垂直平台、第三方检测/认证平台的产品参数页。\n" +
                "3.【第三优先级·兜底】品牌官方旗舰店（天猫/京东/1688）：仅提取「产品参数」「规格表」板块的硬参数；严格排除促销活动、价格波动、买家评价、店铺服务、营销文案。\n\n" +
                "## 本次采集目标\n" +
                "- 品牌名称：" + brand + "\n" +
                "- 目标品类：" + categories + "\n" +
                "- 指定产品（可选，留空则全品类采集）：" + targetProduct + "\n\n" +
                "## 采集信息维度（每个产品只提取以下字段，多余信息不输出）\n" +
                "1. 基础信息：产品名称、所属系列、型号/货号\n" +
                "2. 核心参数：材质成分（精确到原料配比）、规格尺寸（克重/厚度/尺码）、工艺技术、核心功能\n" +
                "3. 官方信息：官方建议零售价、适用场景\n" +
                "4. 溯源信息：参数对应的官网完整URL\n\n" +
                "## 检索执行规则\n" +
                "1. 第一步先定位并验证品牌官方网站，识别官方域名与官网标识，排除所有第三方B2B、百科、新闻站点\n" +
                "2. 第二步优先进入官网产品中心，逐个产品详情页提取参数，不通过搜索结果摘要直接推断\n" +
                "3. 仅当官网完全无对应产品参数时，才降级使用第二、第三优先级数据源\n" +
                "4. 不同渠道参数不一致时，以官网为准，并标注「参数差异说明」\n\n" +
                "## 过滤规则（严格执行）\n" +
                "排除所有电商平台的促销、优惠券、发货、售后、评价、店铺信息；排除新闻稿、品牌宣传、招商、企业介绍、招聘类内容；排除排行榜、推荐、问答等UGC内容；禁止复制营销话术、宣传口号，只输出客观参数。\n\n" +
                "## 输出格式（严格结构化）\n" +
                "### 一、品牌官网定位结果\n- 官方网站地址：\n- 官网产品中心入口：\n\n" +
                "### 二、产品参数明细（按产品分列）\n" +
                "#### 产品1：【产品名称】\n- 型号/货号：\n- 所属系列：\n- 材质成分：\n- 规格参数：\n- 官方工艺：\n- 官方定价：\n- 数据来源URL：\n\n" +
                "#### 产品2：【产品名称】\n……\n\n" +
                "### 三、数据质量说明\n- 官网完整覆盖的产品数：\n- 官网缺失、采用补充数据源的产品：\n- 参数差异说明：\n- 未采集到的信息项：\n\n" +
                "## 质量要求\n" +
                "所有参数必须标注来源页面URL，无来源的参数不得输出；官网存在的信息，禁止用电商数据替代；信息缺失必须明确说明，禁止编造参数；输出精简，只保留硬参数，不输出任何描述性、营销性语句。";
    }

    /**
     * 通用网络检索提问模板：把用户原始问题转写成规范的 MiniMax 检索指令。
     *
     * 示例：
     *   原始问题："宝娜斯 丝袜 中国 抖音电商 中高端 2026秋冬"
     *   模板产出："帮我去全网检索宝娜丝丝袜中国抖音电商中高端2026秋冬。请以品牌官网、官方旗舰店、
     *             官方账号等官方渠道的公开数据为准，然后综合判断最后给出答案……"
     *
     * 模板结构（固定四段）：
     *   1. 动作指令：帮我去全网检索{主题}
     *   2. 数据源要求：以官网等官方渠道公开数据为准，辅以权威媒体/行业平台
     *   3. 输出要求：综合判断后给出答案，分点标注来源
     *   4. 防幻觉约束：检索不到的明确说明，禁止编造
     *
     * 保密约束[CRITICAL]：主题(topic)只来源于用户原始问题本身——本方法严禁接收/拼接
     * knowledgeContext、供应链/报价业务数据、知识库文档、历史对话等任何内部数据。
     *
     * 主题清洗规则：
     *   - 去掉请求性前缀（帮我/请/麻烦/我想等），避免与模板动作指令重复
     *   - 去掉句末疑问符号
     *   - 短问题（≤40字符，典型为关键词堆叠"品牌 品类 渠道 定位 季节"）压缩为紧凑检索串；
     *     长问题（自然语言句子）保留原样，避免破坏可读性
     */
    private String buildWebSearchQuery(String message) {
        // 0. 企划输入格式"客户名/品牌名/品类启动企划"：
        //    品牌名+品类都提取到时，使用《品牌官方产品参数精准采集》任务模板（官网优先、电商兜底、溯源URL必填）
        String brand = extractBrandName(message);
        String categories = extractCategoryWords(message);
        if (!brand.isEmpty() && !categories.isEmpty()) {
            // 指定产品（可选）：去掉品牌/品类/渠道修饰词后的剩余词组（如"280D"），过长或含年份则视为无
            String rest = message.replace(brand, "");
            for (String c : categories.split("、")) {
                rest = rest.replace(c, "");
            }
            String[] fillerWords = {
                    "中国", "电商", "抖音", "快手", "天猫", "淘宝", "京东", "拼多多", "唯品会",
                    "旗舰店", "品牌店", "品牌", "中高端", "高端", "中端", "低端", "最新", "新款",
                    "冬季", "夏季", "春季", "秋季", "秋冬", "春夏", "启动企划", "企划", "帮我", "检索", "搜索"
            };
            for (String f : fillerWords) {
                rest = rest.replace(f, "");
            }
            rest = rest.replaceAll("[\\s，,。\\.：:/、？?]+", "").trim();
            String targetProduct = "";
            if (rest.length() >= 2 && rest.length() <= 12
                    && !rest.matches("\\d+") && !rest.matches(".*20\\d{2}.*")) {
                targetProduct = rest;
            }
            log.info("[web-search] 提取企划要素：品牌={}，品类={}，指定产品={}",
                    brand, categories, targetProduct.isEmpty() ? "(全品类)" : targetProduct);
            return buildOfficialProductParamQuery(brand, categories, targetProduct);
        }

        // 1. 基础清洗：去首尾空白、去请求性前缀、去疑问符号
        String topic = message == null ? "" : message.trim();
        String[] requestPrefixes = {"请帮我", "帮帮我", "麻烦你", "麻烦帮我", "请你", "请", "麻烦", "我想", "我要", "给我", "帮我"};
        for (String p : requestPrefixes) {
            if (topic.startsWith(p)) {
                topic = topic.substring(p.length()).trim();
                break;
            }
        }
        // 剥离用户已写的检索动作词（模板会统一补"帮我去全网检索"，避免指令重复）
        String[] searchActionPrefixes = {
                "去全网检索", "全网检索", "去全网搜索", "全网搜索", "去全网搜", "全网搜",
                "联网检索", "联网搜索", "联网查", "上网检索", "上网搜索", "上网查", "上网搜",
                "网上检索", "网上搜索", "网上查", "网上搜", "从网上", "从互联网", "在线搜索",
                "查一下", "查查", "搜一下", "搜索一下", "检索一下"
        };
        boolean stripped = true;
        while (stripped) {
            stripped = false;
            for (String p : searchActionPrefixes) {
                if (topic.startsWith(p)) {
                    topic = topic.substring(p.length()).trim();
                    stripped = true;
                    break;
                }
            }
        }
        // 剥离开头轻动词（"做个方案"→"方案"，检索主题更聚焦）
        String[] lightVerbs = {"做一份", "做一个", "做个", "制定一份", "制定个", "制定一个", "制定", "写一份", "写个", "写一个", "出一份", "出个", "出一套", "策划一份", "策划个"};
        for (String v : lightVerbs) {
            if (topic.startsWith(v)) {
                topic = topic.substring(v.length()).trim();
                break;
            }
        }
        topic = topic.replace("？", "").replace("?", "").trim();

        // 2. 短问题（关键词堆叠）压缩为紧凑检索串；长句子保留原样
        if (!topic.isEmpty() && topic.length() <= 40) {
            topic = topic.replaceAll("\\s+", "");
        }

        if (topic.isEmpty()) {
            topic = "相关行业与品牌的最新公开信息";
        }

        // 3. 固定模板（降级：品牌/品类提取不全时使用）：动作指令 + 数据源要求（官网为准）+ 输出要求（硬参数+来源URL溯源）+ 防幻觉约束
        return "帮我去全网检索" + topic + "。" +
                "检索要求：请优先到品牌公司官网（官网首页/产品中心/新闻中心/公司介绍页）获取该品牌的产品线、产品名称与产品参数（品类/材质/克重/工艺/尺码/颜色/定价区间等），" +
                "辅以权威媒体和行业平台的公开信息；" +
                "不要抓取淘宝、天猫、京东、拼多多、唯品会等电商平台的商品详情页数据，不要使用比价聚合站与微博等社交媒体的零售信息；" +
                "请综合判断最后给出答案，用文字分点输出检索到的产品与产品参数，每条参数标注来源页面URL作溯源；" +
                "检索不到的内容明确说明'未检索到'，禁止编造。";
    }

    /**
     * 阶段一：联网搜索（数据隔离执行）
     *
     * 保密约束[CRITICAL]：本方法只允许传入用户原始问题(message)。用户问题本身是用户主动
     * 输入的公开信息（如"宝娜斯 丝袜 中国 抖音电商 中高端 2026秋冬"），可用于网络检索；
     * 但 knowledgeContext、供应链/报价等业务数据、知识库文档、历史对话等内部数据严禁
     * 拼入网络请求——网络搜索端点是外部服务，内部数据不得外传。
     *
     * 设计：两阶段隔离——
     *   阶段一（本方法）：仅用户问题(经通用模板转写) → 外部搜索端点 → 返回网络事实摘要
     *   阶段二（主流程）：网络摘要 + 内部数据 → 本地模型生成企划方案（网络数据仅参考）
     *
     * 实现：MiniMax API Anthropic 兼容端点 + web_search_20250305 服务端搜索工具，非流式调用。
     * 失败时返回空字符串（优雅降级，不影响主流程继续用内部数据回答）。
     */
    /**
     * 联网搜索（带调用日志埋点的包装方法）
     * 未配置 MiniMax apiKey 时不算调用、不记录；记录真实成功/失败与耗时
     */
    private String searchWebForMarketInfo(String message) {
        boolean willCall = minimaxApiKey != null && !minimaxApiKey.isBlank();
        long t0 = System.currentTimeMillis();
        String result;
        try {
            result = searchWebForMarketInfoImpl(message);
        } catch (RuntimeException e) {
            if (willCall && aiCallLogService != null) {
                aiCallLogService.record(com.imagemanager.service.AiCallLogService.CAP_WEB_SEARCH, null, false,
                        System.currentTimeMillis() - t0, null, brief(message));
            }
            throw e;
        }
        if (willCall && aiCallLogService != null) {
            aiCallLogService.record(com.imagemanager.service.AiCallLogService.CAP_WEB_SEARCH, null,
                    result != null && !result.isBlank(),
                    System.currentTimeMillis() - t0, null, brief(message));
        }
        return result;
    }

    /** 截断简述（调用日志 detail 字段用） */
    private String brief(String s) {
        if (s == null) return null;
        return s.length() > 80 ? s.substring(0, 80) : s;
    }

    private String searchWebForMarketInfoImpl(String message) {
        if (minimaxApiKey == null || minimaxApiKey.isBlank()) {
            log.info("[web-search] 未配置 app.minimax.api-key，跳过联网搜索（企划方案仅基于内部数据生成）");
            return "";
        }
        String model = (minimaxWebSearchModel != null && !minimaxWebSearchModel.isBlank())
                ? minimaxWebSearchModel : minimaxModel;
        HttpURLConnection conn = null;
        try {
            String url = buildEndpointUrl(minimaxBaseUrl, "/anthropic/v1/messages");

            // 用通用提问模板把用户原始问题转写为规范检索指令
            // 保密约束：buildWebSearchQuery 只基于用户原始问题构造，严禁拼接内部数据
            String searchQuery = buildWebSearchQuery(message);

            // 请求体仅含模板转写后的检索指令——严禁拼接 knowledgeContext/业务数据/历史对话（内部数据保密）
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            // 采集报告为结构化长输出（按产品分列参数明细），max_tokens 给足避免截断
            body.put("max_tokens", 8192);
            body.put("stream", false);
            body.put("system",
                    "你是品牌官方产品参数采集助手，请通过web_search工具严格执行用户给出的《品牌官方产品参数精准采集》任务。" +
                    "执行纪律：1.严格按任务中的数据源优先级执行——品牌官网为唯一权威来源，官网有参数时禁止用电商数据替代，电商仅作官网缺失时的兜底；" +
                    "2.先定位并验证品牌官方域名（排除百科/B2B/新闻站），再进入官网产品中心逐个详情页提取，不得仅凭搜索结果摘要推断参数；" +
                    "3.只输出官方发布的客观硬参数（产品名称/型号货号/材质成分/规格尺寸/工艺技术/官方定价），严禁输出促销、评价、营销话术、品牌故事、公司介绍；" +
                    "4.每条参数必须标注来源页面完整URL作溯源，无来源的参数不得输出；" +
                    "5.严格按任务中的三段输出格式返回（品牌官网定位结果/产品参数明细/数据质量说明）；" +
                    "6.检索不到的信息明确标注'未检索到'，禁止编造。");
            List<Map<String, Object>> tools = new ArrayList<>();
            Map<String, Object> tool = new HashMap<>();
            tool.put("type", "web_search_20250305");
            tool.put("name", "web_search");
            tool.put("max_uses", 5);
            tools.add(tool);
            body.put("tools", tools);
            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", searchQuery);
            body.put("messages", List.of(userMsg));

            log.info("[web-search] 开始联网检索(阶段一, 数据隔离): 原始问题长度={}, 模板query={}, model={}",
                    message == null ? 0 : message.length(), searchQuery, model);

            conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            // 双认证头兼容：MiniMax 用 Bearer，Anthropic 标准用 x-api-key
            conn.setRequestProperty("Authorization", "Bearer " + minimaxApiKey);
            conn.setRequestProperty("x-api-key", minimaxApiKey);
            conn.setRequestProperty("anthropic-version", "2023-06-01");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            // 采集任务为"多次检索+长结构化输出"，读超时放宽到 180s
            conn.setReadTimeout(180000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                String err = readStreamFully(conn.getErrorStream());
                log.warn("[web-search] 联网搜索端点返回 {}: {}", responseCode, abbreviate(err, 300));
                return "";
            }

            String resp = readStreamFully(conn.getInputStream());
            // MiniMax Anthropic兼容响应, content 按执行顺序包含三类内容块：
            //   text                    模型文本（搜索前引导语 + 搜索后最终答案）
            //   server_tool_use         服务端工具调用记录（name=web_search, input.query=实际检索词）→ 工具真实调用的凭证
            //   web_search_tool_result  服务端返回的搜索结果（content=web_search_result列表: title/url/page_age/content）
            JsonNode root = objectMapper.readTree(resp);
            JsonNode contentArr = root.path("content");
            StringBuilder sb = new StringBuilder();
            StringBuilder queries = new StringBuilder();
            StringBuilder sources = new StringBuilder();          // 控制台清单: 标题+URL+网页摘要(数据准确性核对)
            StringBuilder sourcesForInject = new StringBuilder(); // 注入清单: 标题+URL+网页摘要(供本地模型交叉验证, 防幻觉引用)
            java.util.Set<String> seenUrls = new java.util.LinkedHashSet<>(); // URL去重(MiniMax多次检索常重复命中同页)
            int sourceCount = 0;
            if (contentArr.isArray()) {
                for (JsonNode block : contentArr) {
                    String blockType = block.path("type").asText("");
                    if ("text".equals(blockType)) {
                        String text = block.path("text").asText("");
                        if (!text.isBlank()) {
                            if (sb.length() > 0) {
                                sb.append("\n");
                            }
                            sb.append(text.trim());
                        }
                    } else if ("server_tool_use".equals(blockType)
                            && "web_search".equals(block.path("name").asText())) {
                        String q = block.path("input").path("query").asText("");
                        if (!q.isBlank()) {
                            if (queries.length() > 0) {
                                queries.append(" | ");
                            }
                            queries.append(q);
                        }
                    } else if ("web_search_tool_result".equals(blockType)) {
                        JsonNode results = block.path("content");
                        if (results.isArray()) {
                            for (JsonNode r : results) {
                                if (!"web_search_result".equals(r.path("type").asText())) {
                                    continue;
                                }
                                String title = r.path("title").asText("");
                                String srcUrl = r.path("url").asText("");
                                String pageAge = r.path("page_age").asText("");
                                // content字段=MiniMax抓取到的该网页正文摘要——数据准确性的直接证据, 必须保留
                                String snippet = r.path("content").asText("").replaceAll("\\s+", " ").trim();
                                if (srcUrl.isBlank() || !seenUrls.add(srcUrl)) {
                                    continue;
                                }
                                sourceCount++;
                                String ageSuffix = pageAge.isBlank() ? "" : " (" + pageAge + ")";
                                sources.append(sourceCount).append(". ").append(title)
                                        .append(ageSuffix)
                                        .append(" — ").append(srcUrl);
                                if (!snippet.isBlank()) {
                                    sources.append("\n   摘要: ").append(abbreviate(snippet, 300));
                                }
                                sources.append("\n");
                                // 注入清单保留URL+摘要: 采集模板要求溯源URL; 摘要让本地模型核对来源实际内容, 防止"标注了来源但来源不含该数据"的幻觉引用
                                sourcesForInject.append(sourceCount).append(". ").append(title)
                                        .append(ageSuffix)
                                        .append(" — ").append(srcUrl);
                                if (!snippet.isBlank()) {
                                    sourcesForInject.append("\n   摘要: ").append(abbreviate(snippet, 200));
                                }
                                sourcesForInject.append("\n");
                            }
                        }
                    }
                }
            }
            // 注入内容：采集报告正文(text块, MiniMax读完网页后生成的具体参数) + 【检索来源清单】(各来源URL+网页摘要, 供溯源核对)
            String answerText = sb.toString();
            if (sourcesForInject.length() > 0) {
                answerText = answerText + "\n\n【检索来源清单】（引用外部数据前必须核对对应来源摘要中确实包含该数据，来源不含的禁止标注为该来源）\n"
                        + sourcesForInject.toString().trim();
            }

            // ===== 控制台打印联网检索全链路 =====
            // 数据流说明: ①MiniMax执行检索词→②各来源网页(下方清单, 含抓到的正文摘要)→③MiniMax消化后生成的采集报告(下方"注入内容")→④注入本地LLM
            String toolState = queries.length() > 0
                    ? "已调用(web_search_20250305, " + queries.toString().split(" \\| ").length + "次)"
                    : "未调用(模型未触发web_search工具)";
            log.info("[web-search] web_search工具调用状态: {}", toolState);
            if (queries.length() > 0) {
                log.info("[web-search] MiniMax实际执行的检索词: {}", queries);
            }
            if (!answerText.isBlank()) {
                log.info("[web-search] ③联网采集报告(④已注入本地模型, 长度={}字符):\n{}", answerText.length(), answerText);
            } else {
                log.warn("[web-search] 联网检索未返回文本内容, 原始响应: {}", abbreviate(resp, 500));
            }
            if (sourceCount > 0) {
                log.info("[web-search] ②检索来源明细(去重后{}条, 标题+URL+网页摘要, 供准确性核对):\n{}", sourceCount, sources.toString().trim());
            } else {
                log.info("[web-search] 本次未返回编号来源清单(web_search_tool_result为空)");
            }
            return answerText;
        } catch (Exception e) {
            log.warn("[web-search] 联网搜索失败(降级为不联网, 主流程不受影响): {}", e.getMessage());
            return "";
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** 完整读取输入流为字符串（UTF-8） */
    private String readStreamFully(java.io.InputStream in) throws java.io.IOException {
        if (in == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString().trim();
    }

    /** 截断字符串用于日志输出 */
    private String abbreviate(String s, int maxLen) {
        if (s == null) {
            return "";
        }
        String flat = s.replace("\n", " ");
        return flat.length() <= maxLen ? flat : flat.substring(0, maxLen) + "...";
    }

    /**
     * 供应链数据检索 - 优先使用LangChain4j Text-to-SQL，降级到关键词搜索
     */
    private List<Map<String, Object>> searchSupplyChain(String query, String company, String userId) {
        List<Map<String, Object>> results = new ArrayList<>();

        // 注：精确报价检索（实体驱动）已上移到主流程步骤3，这里只做宽泛的排产/部件/物料/供应商等检索

        try {
            // L1 缓存：检查 RAG 检索结果缓存（按用户隔离）
            if (llmCacheService != null && userId != null && !userId.isEmpty()) {
                List<Map<String, Object>> cached = llmCacheService.getCachedRagResult(userId, query);
                if (cached != null) {
                    log.info("[L1缓存] RAG检索结果命中, userId={}", userId);
                    return cached;
                }
            }

            // 优先尝试直接关键词搜索（绕过RagPipeline的Ollama调用，避免超时）
            // 当查询中包含产品编码时，直接查knowledge_embeddings表
            String productCode = extractProductCode(query);
            if (productCode != null && !productCode.isEmpty()) {
                try {
                    log.info("[直接搜索] 检测到产品编码 '{}'，尝试直接搜索knowledge_embeddings表", productCode);
                    List<Map<String, Object>> directResults = searchKnowledgeEmbeddingsDirect(productCode, company);
                    if (directResults != null && !directResults.isEmpty()) {
                        results.addAll(directResults);
                        log.info("[直接搜索] 成功！返回 {} 条结果", directResults.size());
                        if (llmCacheService != null && userId != null) {
                            llmCacheService.putCachedRagResult(userId, query, results);
                        }
                        return results;
                    }
                    log.warn("[直接搜索] 无结果，降级到RAG Pipeline");
                } catch (Exception e) {
                    log.warn("[直接搜索] 异常，降级到RAG Pipeline: {}", e.getMessage());
                }
            }

            // 次选：RAG Pipeline（查询增强→多路向量召回→去重→Rerank）
            if (ragPipeline != null) {
                try {
                    log.info("[RAG Pipeline] 启动增强检索: query='{}', company='{}'", query, company);
                    List<Map<String, Object>> ragResults = ragPipeline.enhancedSearchAsMap(query, company);
                    if (ragResults != null && !ragResults.isEmpty()) {
                        results.addAll(ragResults);
                        log.info("[RAG Pipeline] 增强检索成功，返回 {} 条结果", ragResults.size());
                        // 写入 L1 缓存
                        if (llmCacheService != null && userId != null) {
                            llmCacheService.putCachedRagResult(userId, query, results);
                        }
                        return results;
                    }
                    log.warn("[RAG Pipeline] 增强检索无有效结果，降级到Text-to-SQL");
                } catch (Exception e) {
                    log.warn("[RAG Pipeline] 增强检索异常，降级到Text-to-SQL: {}", e.getMessage());
                }
            }

            // 次选：LangChain4j Text-to-SQL
            if (supplyChainAssistant != null && supplyChainTools != null) {
                try {
                    // 设置当前公司和userId到 ThreadLocal
                    supplyChainTools.setCurrentCompany(company != null ? company : "");
                    supplyChainTools.setCurrentUserId(userId != null ? userId : "");
                    log.info("[LangChain4j] 尝试Text-to-SQL查询: query='{}', company='{}', userId='{}'", query, company, userId);
                    String sqlResult = supplyChainAssistant.chat(query, company != null ? company : "");
                    supplyChainTools.clearCurrentCompany();
                    if (sqlResult != null && !sqlResult.isEmpty() && !sqlResult.contains("查询结果为空") && !sqlResult.contains("SQL执行失败") && !sqlResult.contains("错误：")) {
                        Map<String, Object> langChainResult = new LinkedHashMap<>();
                        langChainResult.put("type", "Text-to-SQL动态查询");
                        langChainResult.put("summary", "大模型生成的SQL查询结果");
                        langChainResult.put("data", Map.of("查询结果", sqlResult));
                        results.add(langChainResult);
                        log.info("[LangChain4j] Text-to-SQL成功，返回 {} 字符结果", sqlResult.length());
                        return results;
                    }
                    log.warn("[LangChain4j] Text-to-SQL无有效结果，降级到关键词搜索");
                } catch (Exception e) {
                    supplyChainTools.clearCurrentCompany();
                    log.warn("[LangChain4j] Text-to-SQL异常，降级到关键词搜索: {}", e.getMessage());
                }
            }

            // 降级：现有关键词搜索逻辑
            results = searchSupplyChainByKeywords(query);

        } catch (Exception e) {
            log.error("供应链数据检索失败", e);
        }
        return results;
    }

    /**
     * 直接搜索knowledge_embeddings表（绕过RagPipeline，避免Ollama超时）
     * 当查询中包含产品编码时，直接用ILIKE匹配chunk_text
     */
    private List<Map<String, Object>> searchKnowledgeEmbeddingsDirect(String productCode, String company) {
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            String sql = "SELECT id, chunk_text, source_doc_id, chunk_index, company, created_at " +
                    "FROM knowledge_embeddings " +
                    "WHERE source_type = 'KNOWLEDGE_BASE' " +
                    "AND (company = ? OR company IS NULL OR company = '') " +
                    "AND (search_vector @@ plainto_tsquery('simple', ?) OR chunk_text ILIKE ?) " +
                    "ORDER BY created_at DESC LIMIT 10";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, company, productCode, "%" + productCode + "%");
            log.info("[直接搜索] SQL执行完成，返回 {} 条记录 (productCode={}, company={})", rows.size(), productCode, company);
            for (Map<String, Object> row : rows) {
                String chunkText = row.get("chunk_text") != null ? row.get("chunk_text").toString() : "";
                String sourceDocId = row.get("source_doc_id") != null ? row.get("source_doc_id").toString() : "未知文档";

                // 构建兼容供应链上下文构建器的格式 (type/summary/data)
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("type", "知识库文档(直接搜索)");
                item.put("summary", chunkText.length() > 100 ? chunkText.substring(0, 100) + "..." : chunkText);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("完整内容", chunkText);
                data.put("来源文档ID", sourceDocId);
                data.put("匹配货号", productCode);
                data.put("相关度", "精确匹配(ILIKE)");
                item.put("data", data);
                item.put("score", 0.9);
                // 同时保留兼容知识库上下文构建器的字段 (content/source)
                item.put("content", chunkText);
                item.put("source", "知识库直接搜索:" + productCode);
                item.put("sourceDocId", sourceDocId);
                item.put("chunkIndex", row.get("chunk_index"));
                results.add(item);
                log.info("[直接搜索] 结果: chunkText前80字={}, sourceDocId={}",
                    chunkText.length() > 80 ? chunkText.substring(0, 80) + "..." : chunkText, sourceDocId);
            }
        } catch (Exception e) {
            log.error("[直接搜索] SQL执行失败: {}", e.getMessage(), e);
        }
        return results;
    }

    /**
     * 供应链数据检索 - 关键词搜索（降级方案）
     */
    private List<Map<String, Object>> searchSupplyChainByKeywords(String query) {
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

            // 2. 原料入库信息（含价格，ERP 无采购数据后价格基准源=入库表）
            searchRawMaterialWarehouse(query, results);

            // 3. 生产计划查询
            if (productCode != null) {
                searchProductionPlan(productCode, results);
            }

            // 4. 辅料采购查询
            searchAccessoryPurchase(query, results);

            // 5. 如果用户问的是供应商对比，额外查询
            if (query.contains("对比") || query.contains("比较") || query.contains("最便宜") || query.contains("最低价")) {
                searchSupplierComparison(query, results);
            }

        } catch (Exception e) {
            log.error("供应链关键词搜索失败", e);
        }
        return results;
    }

    /**
     * 从用户消息中提取产品编码
     */
    private String extractProductCode(String query) {
        // 委托给统一的关键词提取器
        return KeywordExtractor.extractProductCode(query);
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
            // 使用统一的关键词提取器（内置行业词典 + 智能分词）
            List<String> keywords = KeywordExtractor.extractKeywords(query);
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
     * 查询原料入库信息（ERP 无采购数据，价格基准源=入库表）
     */
    private void searchRawMaterialWarehouse(String query, List<Map<String, Object>> results) {
        try {
            List<String> keywords = KeywordExtractor.extractKeywords(query);
            if (keywords.isEmpty()) return;

            StringBuilder sql = new StringBuilder();
            sql.append("SELECT huohao, color, size, component, supplier, material_name, specification, " +
                "material_color, product_code, batch_no, twist_direction, unit, usage_per_unit, " +
                "loss_rate, unit_price, remark FROM raw_material_warehouse WHERE ");
            List<Object> params = new ArrayList<>();
            for (int i = 0; i < keywords.size(); i++) {
                if (i > 0) sql.append(" OR ");
                sql.append("(COALESCE(product_code, '') ILIKE ? OR COALESCE(huohao, '') ILIKE ? " +
                    "OR COALESCE(material_name, '') ILIKE ? OR COALESCE(supplier, '') ILIKE ? " +
                    "OR COALESCE(batch_no, '') ILIKE ?)");
                String pattern = "%" + keywords.get(i) + "%";
                params.add(pattern);
                params.add(pattern);
                params.add(pattern);
                params.add(pattern);
                params.add(pattern);
            }
            sql.append(" ORDER BY huohao NULLS LAST, product_code LIMIT 20");

            List<Map<String, Object>> rows = jdbcTemplate.query(sql.toString(),
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("huohao", rs.getString("huohao"));
                    row.put("color", rs.getString("color"));
                    row.put("size", rs.getString("size"));
                    row.put("component", rs.getString("component"));
                    row.put("supplier", rs.getString("supplier"));
                    row.put("materialName", rs.getString("material_name"));
                    row.put("specification", rs.getString("specification"));
                    row.put("materialColor", rs.getString("material_color"));
                    row.put("productCode", rs.getString("product_code"));
                    row.put("batchNo", rs.getString("batch_no"));
                    row.put("twistDirection", rs.getString("twist_direction"));
                    row.put("unit", rs.getString("unit"));
                    row.put("usagePerUnit", rs.getBigDecimal("usage_per_unit"));
                    row.put("lossRate", rs.getBigDecimal("loss_rate"));
                    row.put("unitPrice", rs.getBigDecimal("unit_price"));
                    row.put("remark", rs.getString("remark"));
                    return row;
                }, params.toArray());

            if (!rows.isEmpty()) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("type", "原料入库");
                result.put("summary", "找到 " + rows.size() + " 条原料入库/用料BOM记录（含货号/部件/物料/单件用量/损耗）");
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
            List<String> keywords = KeywordExtractor.extractKeywords(query);
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
     * 供应商对比 - 按物料名称汇总价格（查 v_material_price 视图，数据源=原料统计报表，价格仅手工维护时才有）
     */
    private void searchSupplierComparison(String query, List<Map<String, Object>> results) {
        try {
            // 按物料名称汇总入库价格，找最低价（视图已按 material_name+specification 聚合）
            String sql = "SELECT material_name, " +
                "specification, " +
                "supplier_count, " +
                "priced_count, " +
                "min_price, " +
                "max_price, " +
                "avg_price, " +
                "record_count " +
                "FROM v_material_price " +
                "ORDER BY record_count DESC LIMIT 20";
            List<Map<String, Object>> rows = jdbcTemplate.query(sql,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("materialName", rs.getString("material_name"));
                    String spec = rs.getString("specification");
                    row.put("specification", spec != null ? spec : "");
                    row.put("supplierCount", rs.getInt("supplier_count"));
                    row.put("recordCount", rs.getInt("record_count"));
                    row.put("pricedCount", rs.getInt("priced_count"));
                    row.put("minPrice", rs.getBigDecimal("min_price"));
                    row.put("maxPrice", rs.getBigDecimal("max_price"));
                    row.put("avgPrice", rs.getBigDecimal("avg_price"));
                    // 计算节省比例（仅当手工维护过单价时才有意义）
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
                long priced = rows.stream().filter(r -> {
                    Object p = r.get("minPrice");
                    return p != null && ((BigDecimal) p).compareTo(BigDecimal.ZERO) > 0;
                }).count();
                String summary = priced > 0
                    ? "共 " + rows.size() + " 种物料有入库记录，其中 " + priced + " 种已维护单价（价格源：原料统计报表+人工维护单价）"
                    : "共 " + rows.size() + " 种物料有入库记录，但均未维护单价（Excel报表本身不含单价列，请在原料入库页手工补充单价后再对比）";
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("items", rows);
                result.put("type", "供应商对比");
                result.put("summary", summary);
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
     * 使用TransactionTemplate确保在无事务上下文（新线程）中也能提交
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
            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
            txTemplate.executeWithoutResult(status -> {
                jdbcTemplate.update(
                        "INSERT INTO smart_chat_history (id, session_id, conversation_id, role, content, reasoning_content, user_id, company, model, created_at) " +
                                "VALUES (gen_random_uuid(), ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, NOW())",
                        conversationId, conversationId, role, content, reasoningContent, userId, company, modeValue
                );
            });
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
            log.info("使用Ollama模型进行对话: {}", ollamaChatModel);

            // 计算发送给模型的上下文大小
            int totalChars = 0;
            for (Map<String, Object> msg : messages) {
                Object content = msg.get("content");
                if (content instanceof String) totalChars += ((String) content).length();
                else if (content != null) totalChars += content.toString().length();
            }
            log.info("Ollama请求上下文: messages={}, totalChars={} (~{}KB)", messages.size(), totalChars, totalChars / 1024);

            Map<String, Object> body = new HashMap<>();
            body.put("model", ollamaChatModel);
            body.put("stream", true);
            body.put("messages", messages);
            Map<String, Object> options = new HashMap<>();
            options.put("temperature", 0.7);
            options.put("num_predict", -1); // -1=无限输出，直到模型生成停止符
            body.put("options", options);

            String endpointUrl = ollamaBaseUrl + "/api/chat";
            HttpURLConnection conn = (HttpURLConnection) URI.create(endpointUrl).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
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
                throw new RuntimeException("Ollama API返回错误 " + responseCode + ": " + errorBody);
            }

            // Ollama使用NDJSON流式格式，每行一个JSON对象
            parseOllamaSSEStream(emitter, conn, fullResponse, reasoningContent);
        } catch (Exception e) {
            log.error("Ollama流式对话失败: {}", e.getMessage());
            throw new RuntimeException("流式对话失败: " + e.getMessage());
        }
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
     * 调用Ollama Embedding API获取文本向量
     */
    /**
     * 文本向量化（带调用日志埋点的包装方法）：记录真实成功/失败与耗时
     */
    private float[] getEmbedding(String text) {
        long t0 = System.currentTimeMillis();
        float[] result = getEmbeddingImpl(text);
        if (aiCallLogService != null) {
            aiCallLogService.record(com.imagemanager.service.AiCallLogService.CAP_EMBEDDING, null,
                    result != null, System.currentTimeMillis() - t0, null, brief(text));
        }
        return result;
    }

    private float[] getEmbeddingImpl(String text) {
        try {
            String url = ollamaBaseUrl + "/api/embed";

            java.net.URI uri = java.net.URI.create(url);
            java.net.URL apiUrl = uri.toURL();
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) apiUrl.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(ollamaTimeout);

            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", ollamaEmbeddingModel,
                    "input", text
            ));

            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(requestBody.getBytes("UTF-8"));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                String errorBody = new String(conn.getErrorStream().readAllBytes(), "UTF-8");
                log.error("Ollama Embedding调用失败, status={}, body={}", responseCode, errorBody);
                return null;
            }

            String responseBody = new String(conn.getInputStream().readAllBytes(), "UTF-8");
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(responseBody);
            // Ollama /api/embed 返回 embeddings（复数，二维数组），/api/embeddings 返回 embedding（单数）
            com.fasterxml.jackson.databind.JsonNode embeddingNode = null;
            if (root.has("embeddings") && root.get("embeddings").isArray() && root.get("embeddings").size() > 0) {
                embeddingNode = root.get("embeddings").get(0);
            } else if (root.has("embedding") && root.get("embedding").isArray()) {
                embeddingNode = root.get("embedding");
            }
            if (embeddingNode != null) {
                float[] embedding = new float[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    embedding[i] = (float) embeddingNode.get(i).asDouble();
                }
                log.info("Ollama embedding成功: model={}, 维度={}", ollamaEmbeddingModel, embedding.length);
                return embedding;
            }
            log.error("Ollama Embedding返回数据格式异常, 完整响应: {}", responseBody);
            return null;
        } catch (Exception e) {
            log.error("获取embedding异常(Ollama): model={}, url={}, error={}", ollamaEmbeddingModel, ollamaBaseUrl + "/api/embed", e.getMessage());
            return null;
        }
    }

    /**
     * 解析Ollama NDJSON流式响应
     * Ollama /api/chat 返回逐行JSON格式:
     *   {"model":"qwen3.6","message":{"role":"assistant","content":"Hello"},"done":false}
     *   {"model":"qwen3.6","done":true}
     */
    private void parseOllamaSSEStream(SseEmitter emitter, HttpURLConnection conn,
                                       StringBuilder fullResponse, StringBuilder reasoningContent) throws Exception {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));

        int lineCount = 0;
        long startTime = System.currentTimeMillis();
        String line;
        while ((line = reader.readLine()) != null) {
            lineCount++;
            if (lineCount <= 3 || lineCount % 50 == 0) {
                log.info("Ollama流式数据: line#{}, length={}, first50={}", lineCount, line.length(), 
                    line.length() > 50 ? line.substring(0, 50) : line);
            }
            if (line.isEmpty()) {
                continue;
            }
            try {
                JsonNode jsonNode = objectMapper.readTree(line);

                // 检查是否完成
                boolean done = jsonNode.has("done") && jsonNode.get("done").asBoolean(false);

                // 提取思考内容（如果有thinking字段）
                if (jsonNode.has("message")) {
                    JsonNode messageNode = jsonNode.get("message");

                    if (messageNode.has("thinking") && messageNode.get("thinking").isTextual()) {
                        String thinking = messageNode.get("thinking").asText();
                        if (!thinking.isEmpty()) {
                            reasoningContent.append(thinking);
                            emitter.send(SseEmitter.event().name("message").data(
                                    objectMapper.writeValueAsString(Map.of(
                                            "type", "reasoning",
                                            "content", thinking
                                    ))
                            ));
                        }
                    }

                    if (messageNode.has("content") && messageNode.get("content").isTextual()) {
                        String content = messageNode.get("content").asText();
                        if (!content.isEmpty()) {
                            fullResponse.append(content);
                            if (lineCount <= 3) {
                                log.info("Ollama首段内容输出: length={}, content={}", content.length(), 
                                    content.length() > 100 ? content.substring(0, 100) : content);
                            }
                            emitter.send(SseEmitter.event().name("message").data(
                                    objectMapper.writeValueAsString(Map.of(
                                            "type", "content",
                                            "content", content
                                    ))
                            ));
                        }
                    }
                }

                if (done) {
                    break;
                }
            } catch (Exception e) {
                log.debug("解析Ollama流式数据行失败: {}", line);
            }
        }
        reader.close();

        // 发送完整的reasoning和done事件
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
}
