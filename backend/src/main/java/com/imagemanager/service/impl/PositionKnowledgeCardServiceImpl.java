package com.imagemanager.service.impl;

import com.imagemanager.entity.PositionKnowledgeCard;
import com.imagemanager.repository.PositionKnowledgeCardRepository;
import com.imagemanager.service.PositionKnowledgeCardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class PositionKnowledgeCardServiceImpl implements PositionKnowledgeCardService {

    private final PositionKnowledgeCardRepository cardRepository;

    @Override
    @Transactional
    public PositionKnowledgeCard createCard(PositionKnowledgeCard card, String userId, String company) {
        // 校验必填字段
        if (card.getPositionName() == null || card.getPositionName().isBlank()) {
            throw new IllegalArgumentException("岗位名称不能为空");
        }
        if (card.getCoreDuties() == null || card.getCoreDuties().isBlank()) {
            throw new IllegalArgumentException("核心职责不能为空");
        }

        // 自动生成卡片编号
        if (card.getCardCode() == null || card.getCardCode().isBlank()) {
            card.setCardCode(generateCardCode(company));
        }

        // 自动生成提交日期
        if (card.getSubmitDate() == null || card.getSubmitDate().isBlank()) {
            card.setSubmitDate(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")));
        }

        card.setUserId(userId);
        card.setCompany(company);
        card.setCreatedAt(LocalDateTime.now());
        card.setUpdatedAt(LocalDateTime.now());

        PositionKnowledgeCard saved = cardRepository.save(card);
        log.info("创建岗位知识卡片成功: id={}, code={}, position={}", saved.getId(), saved.getCardCode(), saved.getPositionName());
        return saved;
    }

    @Override
    @Transactional
    public PositionKnowledgeCard updateCard(String id, PositionKnowledgeCard card, String userId, String company) {
        PositionKnowledgeCard existing = (company != null && !company.isEmpty())
                ? cardRepository.findByIdAndCompany(id, company).orElse(null)
                : cardRepository.findById(id).orElse(null);
        if (existing == null) throw new IllegalArgumentException("卡片不存在或无权访问");

        // 校验必填字段
        if (card.getPositionName() != null && card.getPositionName().isBlank()) {
            throw new IllegalArgumentException("岗位名称不能为空");
        }
        if (card.getCoreDuties() != null && card.getCoreDuties().isBlank()) {
            throw new IllegalArgumentException("核心职责不能为空");
        }

        // 更新所有字段
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

        PositionKnowledgeCard saved = cardRepository.save(existing);
        log.info("更新岗位知识卡片成功: id={}, position={}", saved.getId(), saved.getPositionName());
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

        // 如果有关键词过滤，在内存中过滤（数据量不大时可行）
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
}
