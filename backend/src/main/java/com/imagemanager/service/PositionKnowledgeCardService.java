package com.imagemanager.service;

import java.util.List;
import java.util.Map;

public interface PositionKnowledgeCardService {
    List<Map<String, Object>> listCards(String userId, String company, String keyword, String department, String team, String status, int page, int size);
    int countCards(String userId, String company, String keyword, String department, String team, String status);
    Map<String, Object> createCard(String userId, String company, Map<String, Object> body);
    Map<String, Object> updateCard(String id, String userId, String company, Map<String, Object> body);
    void deleteCard(String id, String userId, String company);
    Map<String, Object> getCard(String id, String userId, String company);
}
