package com.imagemanager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.postgresql.util.PGobject;

import java.util.*;

/**
 * 数据模型服务 - 元数据驱动
 */
@Service
public class DataModelService {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    // ==================== 模型管理 ====================

    public Map<String, Object> listModels(String category, String status, String keyword, int page, int size) {
        StringBuilder sql = new StringBuilder("SELECT * FROM data_models WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (category != null && !category.isEmpty()) { sql.append(" AND category = ?"); params.add(category); }
        if (status != null && !status.isEmpty()) { sql.append(" AND status = ?"); params.add(status); }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append(" AND (name ILIKE ? OR code ILIKE ? OR description ILIKE ?)");
            String kw = "%" + keyword + "%";
            params.add(kw); params.add(kw); params.add(kw);
        }

        // count
        String countSql = "SELECT COUNT(*) FROM data_models WHERE 1=1"
                + (category != null && !category.isEmpty() ? " AND category = '" + category + "'" : "")
                + (status != null && !status.isEmpty() ? " AND status = '" + status + "'" : "")
                + (keyword != null && !keyword.isEmpty() ? " AND (name ILIKE '%" + keyword + "%' OR code ILIKE '%" + keyword + "%')" : "");
        long total = jdbc.queryForObject(countSql, Long.class);

        sql.append(" ORDER BY sort_order, created_at DESC LIMIT ? OFFSET ?");
        params.add(size);
        params.add((page - 1) * size);

        List<Map<String, Object>> models = jdbc.queryForList(sql.toString(), params.toArray());
        // 为每个模型附加字段数和记录数，并解析 JSONB
        for (Map<String, Object> m : models) {
            parseJsonbFields(m);
            Object modelId = m.get("id");
            Long fieldCount = jdbc.queryForObject("SELECT COUNT(*) FROM data_model_fields WHERE model_id = ?", Long.class, modelId);
            Long recordCount = jdbc.queryForObject("SELECT COUNT(*) FROM data_model_records WHERE model_id = ?", Long.class, modelId);
            m.put("fieldCount", fieldCount);
            m.put("recordCount", recordCount);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("data", models);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    public Map<String, Object> getModelDetail(UUID id) {
        Map<String, Object> model = jdbc.queryForMap("SELECT * FROM data_models WHERE id = ?", id);
        parseJsonbFields(model); // 解析模型中的 JSONB 字段
        List<Map<String, Object>> fields = jdbc.queryForList("SELECT * FROM data_model_fields WHERE model_id = ? ORDER BY sort_order, created_at", id);
        for (Map<String, Object> f : fields) {
            parseJsonbFields(f); // 解析字段定义中的 JSONB（options/validation 等）
        }
        Long recordCount = jdbc.queryForObject("SELECT COUNT(*) FROM data_model_records WHERE model_id = ?", Long.class, id);
        model.put("fields", fields);
        model.put("recordCount", recordCount);
        model.put("fieldCount", fields.size());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("data", model);
        return result;
    }

    public Map<String, Object> createModel(Map<String, Object> body) {
        String name = (String) body.getOrDefault("name", "新模型");
        String code = (String) body.getOrDefault("code", "model_" + System.currentTimeMillis());
        String description = (String) body.getOrDefault("description", "");
        String icon = (String) body.getOrDefault("icon", "Database");
        String category = (String) body.getOrDefault("category", "");
        String createdBy = (String) body.getOrDefault("createdBy", "admin");

        jdbc.update("INSERT INTO data_models (name, code, description, icon, category, created_by) VALUES (?, ?, ?, ?, ?, ?)",
                name, code, description, icon, category, createdBy);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "模型创建成功");
        return result;
    }

    public Map<String, Object> updateModel(UUID id, Map<String, Object> body) {
        StringBuilder sql = new StringBuilder("UPDATE data_models SET updated_at = NOW()");
        List<Object> params = new ArrayList<>();

        if (body.containsKey("name")) { sql.append(", name = ?"); params.add(body.get("name")); }
        if (body.containsKey("description")) { sql.append(", description = ?"); params.add(body.get("description")); }
        if (body.containsKey("icon")) { sql.append(", icon = ?"); params.add(body.get("icon")); }
        if (body.containsKey("category")) { sql.append(", category = ?"); params.add(body.get("category")); }
        if (body.containsKey("status")) { sql.append(", status = ?"); params.add(body.get("status")); }
        if (body.containsKey("sortOrder")) { sql.append(", sort_order = ?"); params.add(body.get("sortOrder")); }
        if (body.containsKey("config")) { sql.append(", config = ?::jsonb"); params.add(body.get("config").toString()); }

        sql.append(" WHERE id = ?");
        params.add(id);
        jdbc.update(sql.toString(), params.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "模型更新成功");
        return result;
    }

    public Map<String, Object> deleteModel(UUID id) {
        // 检查是否系统内置
        Boolean isSystem = jdbc.queryForObject("SELECT is_system FROM data_models WHERE id = ?", Boolean.class, id);
        if (Boolean.TRUE.equals(isSystem)) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", false);
            result.put("message", "系统内置模型不可删除");
            return result;
        }
        jdbc.update("DELETE FROM data_models WHERE id = ?", id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "模型已删除");
        return result;
    }

    // ==================== 字段管理 ====================

    public Map<String, Object> listFields(UUID modelId) {
        List<Map<String, Object>> fields = jdbc.queryForList("SELECT * FROM data_model_fields WHERE model_id = ? ORDER BY sort_order, created_at", modelId);
        for (Map<String, Object> f : fields) {
            parseJsonbFields(f);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("data", fields);
        return result;
    }

    public Map<String, Object> addField(UUID modelId, Map<String, Object> body) {
        String name = (String) body.getOrDefault("name", "新字段");
        String code = (String) body.getOrDefault("code", "field_" + System.currentTimeMillis());
        String fieldType = (String) body.getOrDefault("fieldType", "text");
        boolean required = Boolean.TRUE.equals(body.get("required"));
        boolean showInList = !body.containsKey("showInList") || Boolean.TRUE.equals(body.get("showInList"));
        boolean searchable = !body.containsKey("searchable") || Boolean.TRUE.equals(body.get("searchable"));
        String groupName = (String) body.getOrDefault("groupName", "");
        String defaultValue = body.get("defaultValue") != null ? body.get("defaultValue").toString() : null;
        String options = body.get("options") != null ? body.get("options").toString() : null;
        String validation = body.get("validation") != null ? body.get("validation").toString() : null;

        int maxSort = Optional.ofNullable(jdbc.queryForObject(
                "SELECT COALESCE(MAX(sort_order), 0) FROM data_model_fields WHERE model_id = ?", Integer.class, modelId)).orElse(0);

        jdbc.update("INSERT INTO data_model_fields (model_id, name, code, field_type, required, show_in_list, searchable, sort_order, group_name, default_value, options, validation) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb)",
                modelId, name, code, fieldType, required, showInList, searchable, maxSort + 1, groupName, defaultValue, options, validation);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "字段添加成功");
        return result;
    }

    public Map<String, Object> updateField(UUID modelId, UUID fieldId, Map<String, Object> body) {
        StringBuilder sql = new StringBuilder("UPDATE data_model_fields SET updated_at = NOW()");
        List<Object> params = new ArrayList<>();

        if (body.containsKey("name")) { sql.append(", name = ?"); params.add(body.get("name")); }
        if (body.containsKey("fieldType")) { sql.append(", field_type = ?"); params.add(body.get("fieldType")); }
        if (body.containsKey("required")) { sql.append(", required = ?"); params.add(body.get("required")); }
        if (body.containsKey("uniqueField")) { sql.append(", unique_field = ?"); params.add(body.get("uniqueField")); }
        if (body.containsKey("showInList")) { sql.append(", show_in_list = ?"); params.add(body.get("showInList")); }
        if (body.containsKey("searchable")) { sql.append(", searchable = ?"); params.add(body.get("searchable")); }
        if (body.containsKey("sortOrder")) { sql.append(", sort_order = ?"); params.add(body.get("sortOrder")); }
        if (body.containsKey("defaultValue")) { sql.append(", default_value = ?"); params.add(body.get("defaultValue")); }
        if (body.containsKey("placeholder")) { sql.append(", placeholder = ?"); params.add(body.get("placeholder")); }
        if (body.containsKey("groupName")) { sql.append(", group_name = ?"); params.add(body.get("groupName")); }
        if (body.containsKey("description")) { sql.append(", description = ?"); params.add(body.get("description")); }
        if (body.containsKey("options")) { sql.append(", options = ?::jsonb"); params.add(body.get("options").toString()); }
        if (body.containsKey("validation")) { sql.append(", validation = ?::jsonb"); params.add(body.get("validation").toString()); }
        if (body.containsKey("relationConfig")) { sql.append(", relation_config = ?::jsonb"); params.add(body.get("relationConfig").toString()); }

        sql.append(" WHERE id = ? AND model_id = ?");
        params.add(fieldId);
        params.add(modelId);
        jdbc.update(sql.toString(), params.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "字段更新成功");
        return result;
    }

    public Map<String, Object> deleteField(UUID modelId, UUID fieldId) {
        jdbc.update("DELETE FROM data_model_fields WHERE id = ? AND model_id = ?", fieldId, modelId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "字段已删除");
        return result;
    }

    public Map<String, Object> sortFields(UUID modelId, Map<String, Object> body) {
        Object ordersObj = body.get("orders");
        if (ordersObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> orders = (List<Object>) ordersObj;
            for (int i = 0; i < orders.size(); i++) {
                if (orders.get(i) instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> item = (Map<String, Object>) orders.get(i);
                    String fieldId = item.get("id").toString();
                    int sortOrder = i + 1;
                    jdbc.update("UPDATE data_model_fields SET sort_order = ? WHERE id = ? AND model_id = ?", sortOrder, fieldId, modelId);
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "排序更新成功");
        return result;
    }

    // ==================== 数据记录 ====================

    /** 解析 JSONB 类型的 data 字段，PGobject → Map */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonbData(Object dataObj) {
        if (dataObj == null) return new LinkedHashMap<>();
        if (dataObj instanceof Map) return (Map<String, Object>) dataObj;
        try {
            String jsonStr;
            if (dataObj instanceof PGobject) {
                jsonStr = ((PGobject) dataObj).getValue();
            } else {
                jsonStr = dataObj.toString();
            }
            if (jsonStr == null || jsonStr.trim().isEmpty()) return new LinkedHashMap<>();
            return objectMapper.readValue(jsonStr, Map.class);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    /** 解析 Map 中所有 JSONB 类型字段（PGobject → 真实对象） */
    private void parseJsonbFields(Map<String, Object> map, String... jsonbKeys) {
        Set<String> keys = jsonbKeys.length > 0 ? new HashSet<>(Arrays.asList(jsonbKeys)) : null;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object val = entry.getValue();
            if (val instanceof PGobject) {
                String jsonStr = ((PGobject) val).getValue();
                if (jsonStr == null || jsonStr.trim().isEmpty()) {
                    entry.setValue(null);
                    continue;
                }
                try {
                    entry.setValue(objectMapper.readValue(jsonStr, Object.class));
                } catch (Exception e) {
                    entry.setValue(jsonStr);
                }
            }
        }
    }

    public Map<String, Object> listRecords(UUID modelId, String keyword, String status, int page, int size) {
        StringBuilder sql = new StringBuilder("SELECT * FROM data_model_records WHERE model_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(modelId);

        if (status != null && !status.isEmpty()) { sql.append(" AND status = ?"); params.add(status); }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append(" AND data::text ILIKE ?");
            params.add("%" + keyword + "%");
        }

        String countSql = "SELECT COUNT(*) FROM data_model_records WHERE model_id = ?"
                + (status != null && !status.isEmpty() ? " AND status = '" + status + "'" : "")
                + (keyword != null && !keyword.isEmpty() ? " AND data::text ILIKE '%" + keyword + "%'" : "");
        long total = jdbc.queryForObject(countSql, Long.class, modelId);

        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        params.add(size);
        params.add((page - 1) * size);

        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), params.toArray());
        // 解析每条记录的 JSONB data 字段为真实 Map
        for (Map<String, Object> r : records) {
            Object dataObj = r.get("data");
            if (dataObj != null) {
                r.put("data", parseJsonbData(dataObj));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("data", records);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    public Map<String, Object> getRecord(UUID modelId, UUID recordId) {
        Map<String, Object> record = jdbc.queryForMap("SELECT * FROM data_model_records WHERE id = ? AND model_id = ?", recordId, modelId);
        // 解析 JSONB data 字段
        Object dataObj = record.get("data");
        if (dataObj != null) {
            record.put("data", parseJsonbData(dataObj));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("data", record);
        return result;
    }

    public Map<String, Object> createRecord(UUID modelId, Map<String, Object> body) {
        Object dataObj = body.getOrDefault("data", new LinkedHashMap<>());
        String dataJson;
        try { dataJson = objectMapper.writeValueAsString(dataObj); } catch (Exception e) { dataJson = "{}"; }
        String createdBy = (String) body.getOrDefault("createdBy", "admin");
        String status = (String) body.getOrDefault("status", "active");

        jdbc.update("INSERT INTO data_model_records (model_id, data, created_by, status) VALUES (?::uuid, ?::jsonb, ?, ?)",
                modelId.toString(), dataJson, createdBy, status);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "记录创建成功");
        return result;
    }

    public Map<String, Object> updateRecord(UUID modelId, UUID recordId, Map<String, Object> body) {
        StringBuilder sql = new StringBuilder("UPDATE data_model_records SET updated_at = NOW()");
        List<Object> params = new ArrayList<>();

        if (body.containsKey("data")) {
            sql.append(", data = ?::jsonb");
            try { params.add(objectMapper.writeValueAsString(body.get("data"))); } catch (Exception e) { params.add("{}"); }
        }
        if (body.containsKey("status")) { sql.append(", status = ?"); params.add(body.get("status")); }
        if (body.containsKey("updatedBy")) { sql.append(", updated_by = ?"); params.add(body.get("updatedBy")); }

        sql.append(" WHERE id = ? AND model_id = ?");
        params.add(recordId);
        params.add(modelId);
        jdbc.update(sql.toString(), params.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "记录更新成功");
        return result;
    }

    public Map<String, Object> deleteRecord(UUID modelId, UUID recordId) {
        jdbc.update("DELETE FROM data_model_records WHERE id = ? AND model_id = ?", recordId, modelId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "记录已删除");
        return result;
    }

    public Map<String, Object> batchDeleteRecords(UUID modelId, Map<String, Object> body) {
        Object idsObj = body.get("ids");
        int count = 0;
        if (idsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> ids = (List<Object>) idsObj;
            for (Object id : ids) {
                count += jdbc.update("DELETE FROM data_model_records WHERE id = ? AND model_id = ?", id, modelId);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "已删除 " + count + " 条记录");
        return result;
    }
}
