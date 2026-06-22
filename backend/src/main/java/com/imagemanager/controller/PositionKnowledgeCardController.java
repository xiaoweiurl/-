package com.imagemanager.controller;

import com.imagemanager.dto.ApiResponse;
import com.imagemanager.service.PositionKnowledgeCardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/knowledge/position-cards")
public class PositionKnowledgeCardController {

    private static final Logger log = LoggerFactory.getLogger(PositionKnowledgeCardController.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PositionKnowledgeCardService cardService;

    /**
     * 获取岗位知识卡片列表
     */
    @GetMapping
    public ResponseEntity<ApiResponse> listCards(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String team,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {

        try {
            String userId = (String) request.getAttribute("userId");
            String company = (String) request.getAttribute("company");

            List<Map<String, Object>> cards = cardService.listCards(userId, company, keyword, department, team, status, page, size);
            int total = cardService.countCards(userId, company, keyword, department, team, status);

            return ResponseEntity.ok(ApiResponse.success("获取成功", Map.of(
                    "data", cards,
                    "total", total,
                    "page", page,
                    "size", size
            )));
        } catch (Exception e) {
            log.error("获取岗位知识卡片列表失败", e);
            return ResponseEntity.ok(ApiResponse.error("获取失败: " + e.getMessage()));
        }
    }

    /**
     * 创建岗位知识卡片
     */
    @PostMapping
    public ResponseEntity<ApiResponse> createCard(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            String userId = (String) request.getAttribute("userId");
            String company = (String) request.getAttribute("company");

            // 必填校验
            String positionName = (String) body.get("positionName");
            if (positionName == null || positionName.trim().isEmpty()) {
                return ResponseEntity.ok(ApiResponse.error("岗位名称不能为空"));
            }

            Map<String, Object> card = cardService.createCard(userId, company, body);
            return ResponseEntity.ok(ApiResponse.success("创建成功", card));
        } catch (Exception e) {
            log.error("创建岗位知识卡片失败", e);
            return ResponseEntity.ok(ApiResponse.error("创建失败: " + e.getMessage()));
        }
    }

    /**
     * 更新岗位知识卡片
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse> updateCard(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        try {
            String userId = (String) request.getAttribute("userId");
            String company = (String) request.getAttribute("company");

            Map<String, Object> card = cardService.updateCard(id, userId, company, body);
            return ResponseEntity.ok(ApiResponse.success("更新成功", card));
        } catch (Exception e) {
            log.error("更新岗位知识卡片失败", e);
            return ResponseEntity.ok(ApiResponse.error("更新失败: " + e.getMessage()));
        }
    }

    /**
     * 删除岗位知识卡片
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteCard(@PathVariable String id, HttpServletRequest request) {
        try {
            String userId = (String) request.getAttribute("userId");
            String company = (String) request.getAttribute("company");

            cardService.deleteCard(id, userId, company);
            return ResponseEntity.ok(ApiResponse.success("删除成功"));
        } catch (Exception e) {
            log.error("删除岗位知识卡片失败", e);
            return ResponseEntity.ok(ApiResponse.error("删除失败: " + e.getMessage()));
        }
    }

    /**
     * 获取卡片详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse> getCard(@PathVariable String id, HttpServletRequest request) {
        try {
            String userId = (String) request.getAttribute("userId");
            String company = (String) request.getAttribute("company");

            Map<String, Object> card = cardService.getCard(id, userId, company);
            if (card == null) {
                return ResponseEntity.ok(ApiResponse.error("卡片不存在"));
            }
            return ResponseEntity.ok(ApiResponse.success("获取成功", card));
        } catch (Exception e) {
            log.error("获取岗位知识卡片详情失败", e);
            return ResponseEntity.ok(ApiResponse.error("获取失败: " + e.getMessage()));
        }
    }
}
