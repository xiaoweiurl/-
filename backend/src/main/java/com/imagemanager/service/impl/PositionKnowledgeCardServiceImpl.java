package com.imagemanager.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imagemanager.entity.PositionKnowledgeCard;
import com.imagemanager.repository.PositionKnowledgeCardRepository;
import com.imagemanager.service.PositionKnowledgeCardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PositionKnowledgeCardServiceImpl implements PositionKnowledgeCardService {

    private final PositionKnowledgeCardRepository cardRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.minimax.api-key:}")
    private String minimaxApiKey;

    @Value("${app.minimax.embedding.base-url:https://api.minimaxi.com/v1/embeddings}")
    private String minimaxEmbeddingUrl;

    @Value("${app.minimax.embedding.model:embo-01}")
    private String minimaxEmbeddingModel;

    @Override
    @Transactional
    public PositionKnowledgeCard createCard(PositionKnowledgeCard card, String userId, String company) {
        validateAllFields(card);

        if (card.getCardCode() == null || card.getCardCode().isBlank()) {
            card.setCardCode(generateCardCode(company));
        }
        if (card.getSubmitDate() == null || card.getSubmitDate().isBlank()) {
            card.setSubmitDate(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")));
        }

        card.setUserId(userId);
        card.setCompany(company);
        card.setCreatedAt(LocalDateTime.now());
        card.setUpdatedAt(LocalDateTime.now());
        card.setEmbeddingStatus("PENDING");

        PositionKnowledgeCard saved = cardRepository.save(card);
        log.info("创建岗位知识卡片成功: id={}, code={}, position={}", saved.getId(), saved.getCardCode(), saved.getPositionName());

        // 事务提交后再向量化，避免向量化失败导致整个事务回滚
        final String savedId = saved.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    PositionKnowledgeCard fresh = cardRepository.findById(savedId).orElse(null);
                    if (fresh != null) {
                        fresh.setEmbeddingStatus("PROCESSING");
                        cardRepository.save(fresh);
                        vectorizeCard(fresh);
                        fresh.setEmbeddingStatus("COMPLETED");
                        cardRepository.save(fresh);
                        log.info("岗位卡片向量化状态更新为COMPLETED: id={}", savedId);
                    }
                } catch (Exception e) {
                    log.warn("岗位卡片向量化失败，不影响保存: {}", e.getMessage());
                    try {
                        PositionKnowledgeCard fresh = cardRepository.findById(savedId).orElse(null);
                        if (fresh != null) {
                            fresh.setEmbeddingStatus("FAILED");
                            cardRepository.save(fresh);
                            log.info("岗位卡片向量化状态更新为FAILED: id={}", savedId);
                        }
                    } catch (Exception ex) {
                        log.warn("更新向量化状态FAILED也失败: {}", ex.getMessage());
                    }
                }
            }
        });

        return saved;
    }

    @Override
    @Transactional
    public PositionKnowledgeCard updateCard(String id, PositionKnowledgeCard card, String userId, String company) {
        PositionKnowledgeCard existing = (company != null && !company.isEmpty())
                ? cardRepository.findByIdAndCompany(id, company).orElse(null)
                : cardRepository.findById(id).orElse(null);
        if (existing == null) throw new IllegalArgumentException("卡片不存在或无权访问");

        validateAllFields(card);

        if (card.getCardCode() != null) existing.setCardCode(card.getCardCode());
        if (card.getSubmitDate() != null) existing.setSubmitDate(card.getSubmitDate());
        if (card.getDepartment() != null) existing.setDepartment(card.getDepartment());
        if (card.getPositionName() != null) existing.setPositionName(card.getPositionName());
        if (card.getOnDutyPerson() != null) existing.setOnDutyPerson(card.getOnDutyPerson());
        if (card.getReportTo() != null) existing.setReportTo(card.getReportTo());
        if (card.getTeam() != null) existing.setTeam(card.getTeam());
        if (card.getPositionNature() != null) existing.setPositionNature(card.getPositionNature());
        if (card.getCoreDuties() != null) existing.setCoreDuties(card.getCoreDuties());
        if (card.getAuxiliaryDuties() != null) existing.setAuxiliaryDuties(card.getAuxiliaryDuties());
        if (card.getKeyOutputs() != null) existing.setKeyOutputs(card.getKeyOutputs());
        if (card.getHardSkills() != null) existing.setHardSkills(card.getHardSkills());
        if (card.getSoftSkills() != null) existing.setSoftSkills(card.getSoftSkills());
        if (card.getUpstreamInputs() != null) existing.setUpstreamInputs(card.getUpstreamInputs());
        if (card.getDownstreamOutputs() != null) existing.setDownstreamOutputs(card.getDownstreamOutputs());
        if (card.getCompletedWork() != null) existing.setCompletedWork(card.getCompletedWork());
        if (card.getInProgress() != null) existing.setInProgress(card.getInProgress());
        if (card.getBottlenecks() != null) existing.setBottlenecks(card.getBottlenecks());
        if (card.getSupportNeeded() != null) existing.setSupportNeeded(card.getSupportNeeded());
        if (card.getImprovementDirection() != null) existing.setImprovementDirection(card.getImprovementDirection());
        if (card.getProcessOptimization() != null) existing.setProcessOptimization(card.getProcessOptimization());
        if (card.getToolResourceNeeds() != null) existing.setToolResourceNeeds(card.getToolResourceNeeds());
        if (card.getAdditionalNotes() != null) existing.setAdditionalNotes(card.getAdditionalNotes());

        existing.setUpdatedAt(LocalDateTime.now());
        existing.setEmbeddingStatus("PENDING");

        PositionKnowledgeCard saved = cardRepository.save(existing);
        log.info("更新岗位知识卡片成功: id={}, position={}", saved.getId(), saved.getPositionName());

        // 事务提交后再重新向量化，避免向量化失败导致整个事务回滚
        final String savedId = saved.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    deleteCardVectors(savedId);
                    PositionKnowledgeCard fresh = cardRepository.findById(savedId).orElse(null);
                    if (fresh != null) {
                        fresh.setEmbeddingStatus("PROCESSING");
                        cardRepository.save(fresh);
                        vectorizeCard(fresh);
                        fresh.setEmbeddingStatus("COMPLETED");
                        cardRepository.save(fresh);
                        log.info("岗位卡片重新向量化状态更新为COMPLETED: id={}", savedId);
                    }
                } catch (Exception e) {
                    log.warn("岗位卡片重新向量化失败: {}", e.getMessage());
                    try {
                        PositionKnowledgeCard fresh = cardRepository.findById(savedId).orElse(null);
                        if (fresh != null) {
                            fresh.setEmbeddingStatus("FAILED");
                            cardRepository.save(fresh);
                            log.info("岗位卡片向量化状态更新为FAILED: id={}", savedId);
                        }
                    } catch (Exception ex) {
                        log.warn("更新向量化状态FAILED也失败: {}", ex.getMessage());
                    }
                }
            }
        });

        return saved;
    }

    @Override
    public PositionKnowledgeCard getCardDetail(String id, String company) {
        if (company != null && !company.isBlank()) {
            return cardRepository.findByIdAndCompany(id, company)
                    .orElseThrow(() -> new IllegalArgumentException("卡片不存在或无权访问"));
        }
        return cardRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("卡片不存在"));
    }

    @Override
    public Page<PositionKnowledgeCard> getCards(String company, String userId, String keyword, String department, Pageable pageable) {
        Page<PositionKnowledgeCard> page;
        if (company != null && !company.isBlank()) {
            page = cardRepository.findByCompany(company, pageable);
        } else {
            page = cardRepository.findAll(pageable);
        }

        if ((keyword != null && !keyword.isBlank()) || (department != null && !department.isBlank())) {
            List<PositionKnowledgeCard> filtered = page.getContent().stream()
                    .filter(card -> {
                        boolean match = true;
                        if (keyword != null && !keyword.isBlank()) {
                            String kw = keyword.toLowerCase();
                            match = (card.getPositionName() != null && card.getPositionName().toLowerCase().contains(kw))
                                    || (card.getOnDutyPerson() != null && card.getOnDutyPerson().toLowerCase().contains(kw))
                                    || (card.getDepartment() != null && card.getDepartment().toLowerCase().contains(kw))
                                    || (card.getCoreDuties() != null && card.getCoreDuties().toLowerCase().contains(kw));
                        }
                        if (department != null && !department.isBlank()) {
                            match = match && department.equals(card.getDepartment());
                        }
                        return match;
                    })
                    .toList();
            return new org.springframework.data.domain.PageImpl<>(filtered, pageable, filtered.size());
        }

        return page;
    }

    @Override
    @Transactional
    public void deleteCard(String id, String company, String userId) {
        PositionKnowledgeCard card;
        if (company != null && !company.isBlank()) {
            card = cardRepository.findByIdAndCompany(id, company)
                    .orElseThrow(() -> new IllegalArgumentException("卡片不存在或无权访问"));
        } else {
            card = cardRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("卡片不存在"));
        }
        cardRepository.delete(card);

        // 删除向量记录
        try {
            deleteCardVectors(id);
        } catch (Exception e) {
            log.warn("删除岗位卡片向量失败: {}", e.getMessage());
        }

        log.info("删除岗位知识卡片: id={}, position={}", id, card.getPositionName());
    }

    @Override
    public long countCards(String company) {
        if (company != null && !company.isBlank()) {
            return cardRepository.countByCompany(company);
        }
        return cardRepository.count();
    }

    @Override
    public String generateCardCode(String company) {
        long count = cardRepository.countByCompany(company);
        return String.format("KC-%06d", count + 1);
    }

    private void validateAllFields(PositionKnowledgeCard card) {
        String[] requiredFields = {
            card.getPositionName(), "岗位名称",
            card.getDepartment(), "所属部门",
            card.getTeam(), "所属团队",
            card.getCoreDuties(), "核心职责",
            card.getAuxiliaryDuties(), "辅助职责",
            card.getKeyOutputs(), "关键产出物",
            card.getHardSkills(), "硬技能",
            card.getSoftSkills(), "软技能",
            card.getUpstreamInputs(), "上游输入",
            card.getDownstreamOutputs(), "下游输出",
            card.getCompletedWork(), "已完成的主要工作",
            card.getInProgress(), "当前进行中",
            card.getBottlenecks(), "卡点和瓶颈",
            card.getSupportNeeded(), "需要的支持",
            card.getImprovementDirection(), "本人提升方向",
            card.getProcessOptimization(), "流程优化建议",
            card.getToolResourceNeeds(), "工具/资源需求",
            card.getAdditionalNotes(), "补充说明"
        };
        for (int i = 0; i < requiredFields.length; i += 2) {
            String value = requiredFields[i];
            String name = requiredFields[i + 1];
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + "不能为空");
            }
        }
    }

    // ========== 向量化 ==========

    /**
     * 将岗位卡片的文本内容拼接、切片、向量化并存入 knowledge_embeddings
     * source_type = 'POSITION_CARD', source_doc_id = card.id
     */
    private void vectorizeCard(PositionKnowledgeCard card) {
        String fullText = buildCardText(card);
        if (fullText.isBlank()) {
            log.info("岗位卡片内容为空，跳过向量化: id={}", card.getId());
            return;
        }

        // 切片：800字符/片，100字符重叠
        List<String> chunks = splitText(fullText, 800, 100);
        log.info("岗位卡片向量化: id={}, 切片数={}", card.getId(), chunks.size());

        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            try {
                float[] embedding = getEmbedding(chunk);
                if (embedding == null || embedding.length == 0) {
                    log.warn("获取embedding失败，跳过切片 {}: cardId={}", i, card.getId());
                    continue;
                }
                String vectorStr = arrayToVectorString(embedding);
                jdbcTemplate.update(
                    "INSERT INTO knowledge_embeddings (id, card_id, embedding, embedding_model, chunk_text, chunk_index, source_type, source_doc_id, company, created_at) " +
                    "VALUES (?::uuid, NULL, CAST(? AS vector), ?, ?, ?, ?, ?, ?, NOW())",
                    UUID.randomUUID().toString(), vectorStr, minimaxEmbeddingModel, chunk, i,
                    "POSITION_CARD", card.getId(), card.getCompany()
                );
            } catch (Exception e) {
                log.warn("岗位卡片切片向量化失败: cardId={}, chunk={}, error={}", card.getId(), i, e.getMessage());
            }
        }
        log.info("岗位卡片向量化完成: id={}, 切片数={}", card.getId(), chunks.size());
    }

    /**
     * 拼接岗位卡片的文本密集字段
     */
    private String buildCardText(PositionKnowledgeCard card) {
        StringBuilder sb = new StringBuilder();
        sb.append("【岗位基本信息】\n");
        sb.append("岗位名称：").append(nullSafe(card.getPositionName())).append("\n");
        sb.append("在岗人员：").append(nullSafe(card.getOnDutyPerson())).append("\n");
        sb.append("汇报上级：").append(nullSafe(card.getReportTo())).append("\n");
        sb.append("所属部门：").append(nullSafe(card.getDepartment())).append("\n");
        sb.append("所属团队：").append(nullSafe(card.getTeam())).append("\n");
        sb.append("岗位性质：").append(nullSafe(card.getPositionNature())).append("\n\n");

        sb.append("【岗位职责】\n");
        sb.append("核心职责：").append(nullSafe(card.getCoreDuties())).append("\n");
        sb.append("辅助职责：").append(nullSafe(card.getAuxiliaryDuties())).append("\n\n");

        sb.append("【关键产出物】\n");
        sb.append(nullSafe(card.getKeyOutputs())).append("\n\n");

        sb.append("【能力要求】\n");
        sb.append("硬技能：").append(nullSafe(card.getHardSkills())).append("\n");
        sb.append("软技能：").append(nullSafe(card.getSoftSkills())).append("\n\n");

        sb.append("【协作关系】\n");
        sb.append("上游输入：").append(nullSafe(card.getUpstreamInputs())).append("\n");
        sb.append("下游输出：").append(nullSafe(card.getDownstreamOutputs())).append("\n\n");

        sb.append("【当前状态】\n");
        sb.append("已完成的主要工作：").append(nullSafe(card.getCompletedWork())).append("\n");
        sb.append("当前进行中：").append(nullSafe(card.getInProgress())).append("\n");
        sb.append("卡点和瓶颈：").append(nullSafe(card.getBottlenecks())).append("\n");
        sb.append("需要的支持：").append(nullSafe(card.getSupportNeeded())).append("\n\n");

        sb.append("【改进计划】\n");
        sb.append("本人提升方向：").append(nullSafe(card.getImprovementDirection())).append("\n");
        sb.append("流程优化建议：").append(nullSafe(card.getProcessOptimization())).append("\n");
        sb.append("工具/资源需求：").append(nullSafe(card.getToolResourceNeeds())).append("\n\n");

        sb.append("【补充说明】\n");
        sb.append(nullSafe(card.getAdditionalNotes()));

        return sb.toString();
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }

    /**
     * 删除岗位卡片对应的向量记录
     */
    private void deleteCardVectors(String cardId) {
        int deleted = jdbcTemplate.update(
            "DELETE FROM knowledge_embeddings WHERE source_type = 'POSITION_CARD' AND source_doc_id = ?",
            cardId
        );
        log.info("删除岗位卡片向量: cardId={}, 删除条数={}", cardId, deleted);
    }

    // ========== 文本切片 ==========

    private List<String> splitText(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            start += chunkSize - overlap;
            if (start >= text.length()) break;
            if (end == text.length()) break;
        }
        return chunks;
    }

    // ========== MiniMax Embedding ==========

    private float[] getEmbedding(String text) {
        try {
            String apiKey = minimaxApiKey;
            if (apiKey == null || apiKey.isEmpty()) {
                apiKey = System.getenv("MINIMAX_API_KEY");
            }
            if (apiKey == null || apiKey.isEmpty()) {
                log.warn("未配置MiniMax API密钥，跳过岗位卡片向量化");
                return null;
            }

            String url = minimaxEmbeddingUrl;
            Map<String, Object> body = new HashMap<>();
            body.put("model", minimaxEmbeddingModel);
            body.put("texts", new String[]{text});
            body.put("type", "db");

            String jsonBody = objectMapper.writeValueAsString(body);
            String response = doPost(url, jsonBody, apiKey);
            JsonNode root = objectMapper.readTree(response);

            // MiniMax 格式
            if (root.has("vectors") && root.get("vectors").isArray() && root.get("vectors").size() > 0) {
                JsonNode embeddingNode = root.get("vectors").get(0);
                if (embeddingNode != null && embeddingNode.isArray()) {
                    float[] embedding = new float[embeddingNode.size()];
                    for (int i = 0; i < embeddingNode.size(); i++) {
                        embedding[i] = (float) embeddingNode.get(i).asDouble();
                    }
                    return embedding;
                }
            }

            // OpenAI 兼容格式
            if (root.has("data") && root.get("data").isArray() && root.get("data").size() > 0) {
                JsonNode embeddingNode = root.get("data").get(0).get("embedding");
                if (embeddingNode != null && embeddingNode.isArray()) {
                    float[] embedding = new float[embeddingNode.size()];
                    for (int i = 0; i < embeddingNode.size(); i++) {
                        embedding[i] = (float) embeddingNode.get(i).asDouble();
                    }
                    return embedding;
                }
            }

            log.warn("获取embedding返回格式异常: {}", response.substring(0, Math.min(200, response.length())));
            return null;
        } catch (Exception e) {
            log.error("获取embedding异常: {}", e.getMessage());
            return null;
        }
    }

    private String doPost(String url, String jsonBody, String apiKey) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Embedding API返回错误: " + response.statusCode() + " " + response.body());
        }
        return response.body();
    }

    private String arrayToVectorString(float[] arr) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(arr[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
