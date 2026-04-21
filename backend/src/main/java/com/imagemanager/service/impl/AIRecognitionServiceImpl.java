package com.imagemanager.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.imagemanager.dto.MatchingConfig;
import com.imagemanager.entity.Album;
import com.imagemanager.service.AIRecognitionService;
import com.imagemanager.util.MatchingEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI识别服务实现
 * 支持基于关键词的自动分类和豆包Vision API的图片识别
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class AIRecognitionServiceImpl implements AIRecognitionService {
    
    @Value("${app.ai.enabled:false}")
    private boolean aiEnabled;
    
    @Value("${app.ai.api-key:}")
    private String apiKey;
    
    @Value("${app.ai.base-url:https://api.coze.cn}")
    private String baseUrl;
    
    @Value("${app.ai.model:doubao-seed-1-6-vision-250815}")
    private String model;
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 标签生成规则
     */
    private static final Map<String, String[]> TAG_RULES = new LinkedHashMap<>();
    
    static {
        // 服装类型
        TAG_RULES.put("T恤", new String[]{"T恤", "t恤", "短袖", "长袖"});
        TAG_RULES.put("内衣", new String[]{"内衣", "打底"});
        TAG_RULES.put("抓绒衣", new String[]{"抓绒衣", "抓绒"});
        TAG_RULES.put("冲锋衣", new String[]{"冲锋衣"});
        TAG_RULES.put("软壳", new String[]{"软壳"});
        TAG_RULES.put("外套", new String[]{"外套", "夹克"});
        TAG_RULES.put("羽绒服", new String[]{"羽绒服", "羽绒"});
        TAG_RULES.put("裤子", new String[]{"裤子", "长裤", "短裤"});
        
        // 功能特性
        TAG_RULES.put("防风", new String[]{"防风"});
        TAG_RULES.put("防水", new String[]{"防水", "防雨", "防泼水"});
        TAG_RULES.put("保暖", new String[]{"保暖"});
        TAG_RULES.put("透气", new String[]{"透气"});
        TAG_RULES.put("速干", new String[]{"速干"});
        
        // 材质
        TAG_RULES.put("美利奴羊毛", new String[]{"美利奴", "羊毛"});
        TAG_RULES.put("棉", new String[]{"棉", "纯棉"});
        TAG_RULES.put("聚酯纤维", new String[]{"聚酯", "涤纶"});
        
        // 场景
        TAG_RULES.put("户外", new String[]{"户外"});
        TAG_RULES.put("登山", new String[]{"登山", "徒步"});
        TAG_RULES.put("休闲", new String[]{"休闲"});
        TAG_RULES.put("专业", new String[]{"专业"});
        TAG_RULES.put("运动", new String[]{"运动", "健身"});
    }
    
    @Override
    public AIRecognitionResult analyzeImage(String imageUrl, String fileName, List<Album> albums) {
        log.info("分析图片: {}, 文件名: {}", imageUrl, fileName);

        // 1. 首先尝试根据文件名自动分类
        AIRecognitionResult result = classifyByFileName(fileName, albums);
        if (result != null && result.getAlbumId() != null) {
            log.info("通过文件名匹配分类成功: {}", result.getAlbumName());
            return result;
        }

        // 2. 如果没有匹配到相册，从文件名中提取建议的分类名称
        String suggestedAlbumName = extractCategoryFromFileName(fileName);
        if (suggestedAlbumName != null) {
            log.info("未匹配到现有相册，建议创建新相册: {}", suggestedAlbumName);
        }

        // 3. 生成功能特性标签
        List<String> tags = generateTagsFromFileName(fileName);

        // 4. 返回结果（包含建议创建新相册的信息）
        AIRecognitionResult fallbackResult = new AIRecognitionResult(
            null, null, tags, 0.0, "fallback"
        );
        fallbackResult.setSuggestedAlbumName(suggestedAlbumName);
        return fallbackResult;
    }
    
    @Override
    public AIRecognitionResult analyzeImageWithAI(String imageBase64, String fileName, List<Album> albums) {
        log.info("使用AI分析图片: {}", fileName);

        // 1. 首先尝试根据文件名自动分类
        AIRecognitionResult result = classifyByFileName(fileName, albums);
        if (result != null && result.getAlbumId() != null) {
            log.info("通过文件名匹配分类成功: {}", result.getAlbumName());
            return result;
        }

        // 2. 如果启用了AI，尝试调用豆包Vision API
        if (aiEnabled && apiKey != null && !apiKey.isEmpty()) {
            try {
                result = analyzeWithDoubaoVision(imageBase64, fileName, albums);
                if (result != null && result.getAlbumId() != null) {
                    log.info("通过AI分析分类成功: {}, 置信度: {}", result.getAlbumName(), result.getConfidence());
                    return result;
                }
            } catch (Exception e) {
                log.error("AI分析失败: {}", e.getMessage(), e);
            }
        } else {
            log.info("AI未启用或API Key未配置，跳过AI分析");
        }

        // 3. 如果AI也未识别出分类，从文件名中提取建议的分类名称
        String suggestedAlbumName = extractCategoryFromFileName(fileName);
        if (suggestedAlbumName != null) {
            log.info("AI未识别出分类，从文件名提取建议的相册: {}", suggestedAlbumName);
        }

        // 4. 生成功能特性标签
        List<String> tags = generateTagsFromFileName(fileName);

        // 5. 返回结果（包含建议创建新相册的信息）
        AIRecognitionResult fallbackResult = new AIRecognitionResult(
            null, null, tags, 0.0, "fallback"
        );
        fallbackResult.setSuggestedAlbumName(suggestedAlbumName);
        return fallbackResult;
    }
    
    /**
     * 根据文件名自动分类（配置化匹配）
     */
    private AIRecognitionResult classifyByFileName(String fileName, List<Album> albums) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }

        for (Album album : albums) {
            if (album.getKeywords() == null || album.getKeywords().isEmpty()) {
                continue;
            }
            
            // 获取相册的匹配配置
            MatchingConfig config = parseMatchingConfig(album.getMatchingConfig());
            
            // 使用配置化的匹配引擎
            MatchingEngine.MatchResult matchResult = MatchingEngine.match(
                fileName, 
                album.getKeywords(), 
                config
            );
            
            if (matchResult.isMatched()) {
                log.info("文件 {} 通过 {} 模式匹配到相册 {} (关键词: {})", 
                         fileName, config.getMode(), album.getName(), matchResult.getMatchedKeyword());
                
                // 生成标签时，过滤掉相册名称
                List<String> tags = generateTagsFromFileName(fileName);

                // 从tags中移除相册名称（如果存在）
                tags.removeIf(tag -> tag.equals(album.getName()));

                return new AIRecognitionResult(
                    album.getId(),
                    album.getName(),
                    tags,
                    0.9,
                    "filename:" + config.getMode()
                );
            }
        }

        return null;
    }
    
    /**
     * 解析匹配配置
     */
    private MatchingConfig parseMatchingConfig(String configJson) {
        if (configJson == null || configJson.isEmpty()) {
            return MatchingEngine.createDefaultConfig();
        }
        
        try {
            // 尝试解析 JSON 配置
            JsonNode node = objectMapper.readTree(configJson);
            MatchingConfig config = new MatchingConfig();
            
            if (node.has("mode")) {
                config.setMode(node.get("mode").asText("contains"));
            } else {
                config.setMode("contains");
            }
            
            if (node.has("caseSensitive")) {
                config.setCaseSensitive(node.get("caseSensitive").asBoolean(false));
            } else {
                config.setCaseSensitive(false);
            }
            
            // 解析同义词配置
            if (node.has("synonyms") && node.get("synonyms").isArray()) {
                List<MatchingConfig.SynonymGroup> synonyms = new ArrayList<>();
                for (JsonNode synonymNode : node.get("synonyms")) {
                    MatchingConfig.SynonymGroup group = new MatchingConfig.SynonymGroup();
                    List<String> keywords = new ArrayList<>();
                    if (synonymNode.has("keywords") && synonymNode.get("keywords").isArray()) {
                        for (JsonNode kw : synonymNode.get("keywords")) {
                            keywords.add(kw.asText());
                        }
                    }
                    group.setKeywords(keywords);
                    if (synonymNode.has("targetKeyword")) {
                        group.setTargetKeyword(synonymNode.get("targetKeyword").asText());
                    }
                    synonyms.add(group);
                }
                config.setSynonyms(synonyms);
            }
            
            return config;
        } catch (Exception e) {
            log.warn("解析匹配配置失败，使用默认配置: {}, 错误: {}", configJson, e.getMessage());
            return MatchingEngine.createDefaultConfig();
        }
    }

    /**
     * 从文件名中提取分类名称
     * 用于在没有匹配到现有相册时，建议创建新相册
     *
     * @param fileName 文件名
     * @return 提取的分类名称，如果无法提取则返回null
     */
    private String extractCategoryFromFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }

        // 移除文件扩展名
        String nameWithoutExt = fileName;
        if (fileName.contains(".")) {
            nameWithoutExt = fileName.substring(0, fileName.lastIndexOf("."));
        }

        // 移除常见的数字、品牌、型号等非分类词汇
        String cleanedName = nameWithoutExt
            .replaceAll("\\d+", "") // 移除数字
            .replaceAll("%", "")    // 移除百分号
            .replaceAll("_", " ")   // 下划线替换为空格
            .replaceAll("-", " ")   // 连字符替换为空格
            .replaceAll("\\s+", " ") // 多个空格合并为一个
            .trim();

        // 如果清理后为空，返回null
        if (cleanedName.isEmpty()) {
            return null;
        }

        // 策略1：优先检查是否包含已知的服装类型关键词
        // 按长度从长到短排序，优先匹配更长的关键词
        String[] clothingCategories = {
            "冲锋衣", "抓绒衣", "羽绒服", "运动鞋", "软壳外套",
            "T恤", "内衣", "软壳", "外套", "裤子",
            "卫衣", "夹克", "背心", "短裤", "裙子"
        };

        for (String category : clothingCategories) {
            if (cleanedName.contains(category)) {
                return category;
            }
        }

        // 策略2：使用正则表达式提取中文词语
        // 优先匹配更长的词语（4-6个中文字符），再匹配短的（2-3个）
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("[\\u4e00-\\u9fa5]{4,6}");
        java.util.regex.Matcher matcher = pattern.matcher(cleanedName);

        if (matcher.find()) {
            String potentialCategory = matcher.group();

            // 过滤掉一些常见的非分类词汇
            Set<String> excludedWords = new HashSet<>(Arrays.asList(
                "破冰者", "美利奴", "速干衣", "防雨衣", "防风衣",
                "男款女款", "男女同款", "儿童款", "成人款",
                "图片照片", "正面背面", "侧面角度",
                "热卖推荐", "特价折扣", "新款上市",
                "全季节", "四季通用", "春夏秋冬"
            ));

            if (!excludedWords.contains(potentialCategory)) {
                return potentialCategory;
            }
        }

        // 如果没有找到长词，尝试提取2-3个中文字符
        pattern = java.util.regex.Pattern.compile("[\\u4e00-\\u9fa5]{2,3}");
        matcher = pattern.matcher(cleanedName);

        Set<String> excludedWords = new HashSet<>(Arrays.asList(
            "图片", "照片", "原图", "展示", "模特",
            "正面", "背面", "侧图", "侧面",
            "男款", "女款", "儿童", "成人", "均码",
            "新款", "热卖", "推荐", "特价", "折扣",
            "全季", "四季", "春款", "夏款", "秋款", "冬款"
        ));

        while (matcher.find()) {
            String potentialCategory = matcher.group();
            if (!excludedWords.contains(potentialCategory)) {
                return potentialCategory;
            }
        }

        // 策略3：如果没有匹配到中文，尝试提取第一个英文单词
        String[] words = cleanedName.split("\\s+");
        for (String word : words) {
            if (word.length() >= 3 && word.length() <= 15) {
                // 过滤掉常见的非分类词汇
                Set<String> excludedEnglishWords = new HashSet<>(Arrays.asList(
                    "photo", "image", "pic", "img", "front", "back", "side",
                    "men", "women", "kids", "child", "adult", "unisex",
                    "new", "hot", "sale", "promo", "discount", "season"
                ));

                if (!excludedEnglishWords.contains(word.toLowerCase())) {
                    // 首字母大写
                    return word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase();
                }
            }
        }

        // 如果所有策略都失败，返回null
        return null;
    }

    /**
     * 根据文件名生成标签（过滤相册名称，只保留功能特性标签）
     */
    private List<String> generateTagsFromFileName(String fileName) {
        List<String> tags = new ArrayList<>();

        if (fileName == null || fileName.isEmpty()) {
            return tags;
        }

        // 提取功能特性标签（不包括服装类型）
        // 服装类型标签：T恤、内衣、抓绒衣、冲锋衣、软壳、外套、羽绒服、裤子
        // 功能特性标签：防风、防水、保暖、透气、速干、美利奴羊毛、棉、聚酯纤维、户外、登山、休闲、专业、运动

        Set<String> excludedTags = new HashSet<>(Arrays.asList(
            "T恤", "内衣", "抓绒衣", "冲锋衣", "软壳", "外套", "羽绒服", "裤子"
        ));

        for (Map.Entry<String, String[]> entry : TAG_RULES.entrySet()) {
            String tag = entry.getKey();
            String[] keywords = entry.getValue();

            // 跳过服装类型标签，只保留功能特性标签
            if (excludedTags.contains(tag)) {
                continue;
            }

            for (String keyword : keywords) {
                if (fileName.toLowerCase().contains(keyword.toLowerCase())) {
                    tags.add(tag);
                    break;
                }
            }
        }

        return tags.stream().distinct().collect(Collectors.toList());
    }
    
    /**
     * 使用豆包Vision API分析图片
     */
    private AIRecognitionResult analyzeWithDoubaoVision(String imageBase64, String fileName, List<Album> albums) {
        try {
            // 构建请求体
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            
            // 构建消息
            ArrayNode messages = requestBody.putArray("messages");
            ObjectNode message = messages.addObject();
            message.put("role", "user");
            
            // 构建内容
            ArrayNode content = message.putArray("content");
            
            // 添加文本提示
            ObjectNode textPart = content.addObject();
            textPart.put("type", "text");
            
            // 构建相册选项提示
            StringBuilder albumOptions = new StringBuilder();
            albumOptions.append("请分析这张户外服装图片，识别服装类型并生成标签。\n\n");
            albumOptions.append("可选的相册分类：\n");
            for (Album album : albums) {
                albumOptions.append("- ").append(album.getName());
                if (album.getKeywords() != null && !album.getKeywords().isEmpty()) {
                    albumOptions.append("（关键词：").append(String.join("、", album.getKeywords())).append("）");
                }
                albumOptions.append("\n");
            }
            albumOptions.append("\n请按以下JSON格式回复：\n");
            albumOptions.append("{\n");
            albumOptions.append("  \"category\": \"相册名称\",\n");
            albumOptions.append("  \"tags\": [\"标签1\", \"标签2\"],\n");
            albumOptions.append("  \"description\": \"图片描述\",\n");
            albumOptions.append("  \"confidence\": 0.85\n");
            albumOptions.append("}\n\n");
            albumOptions.append("如果无法确定具体分类，category填null。只返回JSON，不要其他说明文字。");
            
            textPart.put("text", albumOptions.toString());
            
            // 添加图片
            ObjectNode imagePart = content.addObject();
            imagePart.put("type", "image_url");
            ObjectNode imageUrl = imagePart.putObject("image_url");
            
            // 处理 Base64 格式
            String base64Data = imageBase64;
            String mimeType = "image/jpeg";
            
            // 如果是 data:image/xxx;base64, 格式，提取数据
            if (imageBase64.startsWith("data:")) {
                String[] parts = imageBase64.split(",");
                if (parts.length == 2) {
                    String header = parts[0];
                    base64Data = parts[1];
                    // 提取 MIME 类型
                    Pattern pattern = Pattern.compile("data:(image/[^;]+)");
                    Matcher matcher = pattern.matcher(header);
                    if (matcher.find()) {
                        mimeType = matcher.group(1);
                    }
                }
            }
            
            // 根据文件扩展名判断 MIME 类型
            if (fileName != null) {
                String lowerName = fileName.toLowerCase();
                if (lowerName.endsWith(".png")) mimeType = "image/png";
                else if (lowerName.endsWith(".gif")) mimeType = "image/gif";
                else if (lowerName.endsWith(".webp")) mimeType = "image/webp";
            }
            
            imageUrl.put("url", "data:" + mimeType + ";base64," + base64Data);
            
            log.debug("调用豆包Vision API, 模型: {}", model);
            
            // 发送请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/v1/chat/completions",
                HttpMethod.POST,
                entity,
                String.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseVisionResponse(response.getBody(), albums);
            }
            
        } catch (Exception e) {
            log.error("调用豆包Vision API失败: {}", e.getMessage(), e);
        }
        
        return null;
    }
    
    /**
     * 解析Vision API响应
     */
    private AIRecognitionResult parseVisionResponse(String responseBody, List<Album> albums) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");

            if (choices.isArray() && choices.size() > 0) {
                String content = choices.get(0).path("message").path("content").asText();

                log.debug("AI响应内容: {}", content);

                // 尝试解析JSON响应
                content = content.trim();
                if (content.startsWith("```json")) {
                    content = content.substring(7);
                }
                if (content.startsWith("```")) {
                    content = content.substring(3);
                }
                if (content.endsWith("```")) {
                    content = content.substring(0, content.length() - 3);
                }
                content = content.trim();

                JsonNode result = objectMapper.readTree(content);

                String category = result.path("category").asText(null);
                List<String> tags = new ArrayList<>();
                JsonNode tagsNode = result.path("tags");
                if (tagsNode.isArray()) {
                    for (JsonNode tag : tagsNode) {
                        tags.add(tag.asText());
                    }
                }
                String description = result.path("description").asText("");
                double confidence = result.path("confidence").asDouble(0.5);

                // 查找匹配的相册
                String albumId = null;
                String albumName = null;
                if (category != null && !category.equals("null") && !category.isEmpty()) {
                    for (Album album : albums) {
                        if (album.getName().equals(category)) {
                            albumId = album.getId();
                            albumName = album.getName();
                            break;
                        }
                    }
                }

                // 从tags中移除相册名称
                if (albumName != null) {
                    // 创建final副本供lambda表达式使用
                    final String finalAlbumName = albumName;
                    tags.removeIf(tag -> tag.equals(finalAlbumName));
                }

                AIRecognitionResult recognitionResult = new AIRecognitionResult(
                    albumId, albumName, tags, confidence, "llm"
                );
                recognitionResult.setDescription(description);

                return recognitionResult;
            }

        } catch (Exception e) {
            log.error("解析Vision API响应失败: {}", e.getMessage(), e);
        }

        return null;
    }
}
