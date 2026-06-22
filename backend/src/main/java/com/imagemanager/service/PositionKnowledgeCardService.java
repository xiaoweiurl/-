package com.imagemanager.service;

import com.imagemanager.entity.PositionKnowledgeCard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PositionKnowledgeCardService {

    PositionKnowledgeCard createCard(PositionKnowledgeCard card, String userId, String company);

    PositionKnowledgeCard updateCard(String id, PositionKnowledgeCard card, String userId, String company);

    PositionKnowledgeCard getCardDetail(String id, String company);

    Page<PositionKnowledgeCard> getCards(String company, String userId, String keyword, String department, Pageable pageable);

    void deleteCard(String id, String company, String userId);

    long countCards(String company);

    String generateCardCode(String company);
}
