package com.imagemanager.controller;

import com.imagemanager.service.DataModelService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * 数据模型控制器 - 元数据驱动：动态字段、自定义表单、可配置数据模型
 */
@RestController
@RequestMapping("/data-models")
public class DataModelController {

    @Autowired
    private DataModelService service;

    // ==================== 模型管理 ====================

    /** 获取模型列表 */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listModels(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        return ResponseEntity.ok(service.listModels(category, status, keyword, page, size));
    }

    /** 获取模型详情（含字段列表） */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getModel(@PathVariable UUID id, HttpServletRequest request) {
        return ResponseEntity.ok(service.getModelDetail(id));
    }

    /** 创建模型 */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createModel(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ResponseEntity.ok(service.createModel(body));
    }

    /** 更新模型 */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateModel(@PathVariable UUID id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ResponseEntity.ok(service.updateModel(id, body));
    }

    /** 删除模型 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteModel(@PathVariable UUID id, HttpServletRequest request) {
        return ResponseEntity.ok(service.deleteModel(id));
    }

    // ==================== 字段管理 ====================

    /** 获取模型字段列表 */
    @GetMapping("/{id}/fields")
    public ResponseEntity<Map<String, Object>> listFields(@PathVariable UUID id, HttpServletRequest request) {
        return ResponseEntity.ok(service.listFields(id));
    }

    /** 添加字段 */
    @PostMapping("/{id}/fields")
    public ResponseEntity<Map<String, Object>> addField(@PathVariable UUID id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ResponseEntity.ok(service.addField(id, body));
    }

    /** 更新字段 */
    @PutMapping("/{id}/fields/{fieldId}")
    public ResponseEntity<Map<String, Object>> updateField(@PathVariable UUID id, @PathVariable UUID fieldId, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ResponseEntity.ok(service.updateField(id, fieldId, body));
    }

    /** 删除字段 */
    @DeleteMapping("/{id}/fields/{fieldId}")
    public ResponseEntity<Map<String, Object>> deleteField(@PathVariable UUID id, @PathVariable UUID fieldId, HttpServletRequest request) {
        return ResponseEntity.ok(service.deleteField(id, fieldId));
    }

    /** 批量更新字段排序 */
    @PutMapping("/{id}/fields/sort")
    public ResponseEntity<Map<String, Object>> sortFields(@PathVariable UUID id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ResponseEntity.ok(service.sortFields(id, body));
    }

    // ==================== 数据记录 ====================

    /** 获取模型数据列表 */
    @GetMapping("/{id}/records")
    public ResponseEntity<Map<String, Object>> listRecords(
            @PathVariable UUID id,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        return ResponseEntity.ok(service.listRecords(id, keyword, status, page, size));
    }

    /** 获取单条记录 */
    @GetMapping("/{id}/records/{recordId}")
    public ResponseEntity<Map<String, Object>> getRecord(@PathVariable UUID id, @PathVariable UUID recordId, HttpServletRequest request) {
        return ResponseEntity.ok(service.getRecord(id, recordId));
    }

    /** 创建记录 */
    @PostMapping("/{id}/records")
    public ResponseEntity<Map<String, Object>> createRecord(@PathVariable UUID id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ResponseEntity.ok(service.createRecord(id, body));
    }

    /** 更新记录 */
    @PutMapping("/{id}/records/{recordId}")
    public ResponseEntity<Map<String, Object>> updateRecord(@PathVariable UUID id, @PathVariable UUID recordId, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ResponseEntity.ok(service.updateRecord(id, recordId, body));
    }

    /** 删除记录 */
    @DeleteMapping("/{id}/records/{recordId}")
    public ResponseEntity<Map<String, Object>> deleteRecord(@PathVariable UUID id, @PathVariable UUID recordId, HttpServletRequest request) {
        return ResponseEntity.ok(service.deleteRecord(id, recordId));
    }

    /** 批量删除记录 */
    @DeleteMapping("/{id}/records/batch")
    public ResponseEntity<Map<String, Object>> batchDeleteRecords(@PathVariable UUID id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ResponseEntity.ok(service.batchDeleteRecords(id, body));
    }
}
