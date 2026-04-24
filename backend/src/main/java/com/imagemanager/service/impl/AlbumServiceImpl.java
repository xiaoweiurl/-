package com.imagemanager.service.impl;

import com.imagemanager.entity.Album;
import com.imagemanager.repository.AlbumRepository;
import com.imagemanager.repository.ImageRepository;
import com.imagemanager.service.AlbumService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 相册服务实现类
 * 预置户外服装分类相册
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class AlbumServiceImpl implements AlbumService {
    
    @Autowired
    private AlbumRepository albumRepository;
    
    @Autowired
    private ImageRepository imageRepository;
    
    /**
     * 初始化预置相册
     */
    @PostConstruct
    public void initDefaultAlbums() {
        // 检查是否已有系统预置相册
        List<Album> systemAlbums = albumRepository.findByIsSystemTrue();
        if (!systemAlbums.isEmpty()) {
            log.info("系统预置相册已存在，跳过初始化");
            return;
        }
        
        log.info("初始化预置户外服装分类相册...");
        
        // T恤
        Album tshirt = Album.builder()
                .id("album-tshirt")
                .name("T恤")
                .description("短袖/长袖T恤、速干衣")
                .coverUrl("/assets/【经典款】HELLYHANSEN_HH 男款吸湿速干轻户外都市休闲长袖T恤_372.png")
                .keywords(Arrays.asList("T恤", "t恤"))
                .isSystem(true)
                .imageCount(0)
                .sortOrder(1)
                .createdAt(LocalDateTime.now().minusDays(10))
                .updatedAt(LocalDateTime.now().minusDays(5))
                .userId("system")
                .build();
        albumRepository.save(tshirt);
        
        // 内衣
        Album underwear = Album.builder()
                .id("album-underwear")
                .name("内衣")
                .description("贴身内衣、打底衣")
                .coverUrl("/assets/【单依纯同款】icebreaker美利奴羊毛女200 Oasis吸湿长袖T恤徒步_4.png")
                .keywords(Arrays.asList("内衣"))
                .isSystem(true)
                .imageCount(0)
                .sortOrder(2)
                .createdAt(LocalDateTime.now().minusDays(10))
                .updatedAt(LocalDateTime.now().minusDays(5))
                .userId("system")
                .build();
        albumRepository.save(underwear);
        
        // 抓绒衣
        Album fleece = Album.builder()
                .id("album-fleece")
                .name("抓绒衣")
                .description("抓绒衣、保暖中层")
                .coverUrl("/assets/「折扣」patagonia巴塔R1AIR抓绒衣男女户外透气排汗保暖速干圆领_619.png")
                .keywords(Arrays.asList("抓绒衣", "抓绒"))
                .isSystem(true)
                .imageCount(0)
                .sortOrder(3)
                .createdAt(LocalDateTime.now().minusDays(10))
                .updatedAt(LocalDateTime.now().minusDays(5))
                .userId("system")
                .build();
        albumRepository.save(fleece);
        
        // 冲锋衣
        Album jacket = Album.builder()
                .id("album-jacket")
                .name("冲锋衣")
                .description("防风防雨冲锋衣、硬壳外套")
                .coverUrl("/assets/【王一博同款】HELLY HANSEN_HH 专业Ⅰ级登山3L防风防雨冲锋衣_371.png")
                .keywords(Arrays.asList("冲锋衣"))
                .isSystem(true)
                .imageCount(0)
                .sortOrder(4)
                .createdAt(LocalDateTime.now().minusDays(10))
                .updatedAt(LocalDateTime.now().minusDays(5))
                .userId("system")
                .build();
        albumRepository.save(jacket);
        
        // 软壳
        Album softshell = Album.builder()
                .id("album-softshell")
                .name("软壳")
                .description("软壳外套、防泼水外套")
                .coverUrl("/assets/【经典CREW】 HELLY HANSEN_HH男款户外软壳防泼水保暖登山服抓绒_98.png")
                .keywords(Arrays.asList("软壳"))
                .isSystem(true)
                .imageCount(0)
                .sortOrder(5)
                .createdAt(LocalDateTime.now().minusDays(10))
                .updatedAt(LocalDateTime.now().minusDays(5))
                .userId("system")
                .build();
        albumRepository.save(softshell);
        
        log.info("预置相册初始化完成，共 5 个");
    }
    
    @Override
    public List<Album> getAllAlbums() {
        log.info("获取所有相册");
        return albumRepository.findAllByOrderBySortOrderAsc();
    }
    
    @Override
    public Album getAlbumById(String id) {
        log.info("获取相册详情：{}", id);
        return albumRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("相册不存在"));
    }
    
    @Override
    public Album createAlbum(String name, String description) {
        return createAlbum(name, description, null, null);
    }
    
    @Override
    public Album createAlbum(String name, String description, List<String> keywords) {
        return createAlbum(name, description, keywords, null);
    }
    
    @Override
    public Album createAlbum(String name, String description, List<String> keywords, String matchingConfig) {
        log.info("创建相册：{}，关键词：{}，匹配配置：{}", name, keywords, matchingConfig);
        
        long count = albumRepository.count();
        
        // 处理关键词：如果为空则初始化空列表，如果字符串则转换为列表
        List<String> processedKeywords = new ArrayList<>();
        if (keywords != null) {
            for (String keyword : keywords) {
                if (keyword != null && !keyword.trim().isEmpty()) {
                    processedKeywords.add(keyword.trim());
                }
            }
        }
        // 如果关键词为空，至少添加相册名称作为关键词
        if (processedKeywords.isEmpty() && name != null && !name.isEmpty()) {
            processedKeywords.add(name);
        }
        
        Album album = Album.builder()
                .id("album-" + UUID.randomUUID().toString().substring(0, 8))
                .name(name)
                .description(description)
                .keywords(processedKeywords)
                .matchingConfig(matchingConfig)
                .isSystem(false)
                .imageCount(0)
                .sortOrder((int) count)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .userId("user-1")
                .build();
        
        Album saved = albumRepository.save(album);
        log.info("相册创建成功，ID：{}，关键词：{}，匹配配置：{}", 
                 saved.getId(), saved.getKeywords(), saved.getMatchingConfig());
        
        return saved;
    }
    
    @Override
    public Album updateAlbum(String id, String name, String description) {
        return updateAlbum(id, name, description, null);
    }
    
    @Override
    public Album updateAlbum(String id, String name, String description, String matchingConfig) {
        log.info("更新相册：{}", id);
        
        Album album = albumRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("相册不存在"));
        
        // 系统预置相册不允许修改名称
        if (album.getIsSystem() && name != null && !name.equals(album.getName())) {
            throw new RuntimeException("系统预置相册不允许修改名称");
        }
        
        if (name != null) album.setName(name);
        if (description != null) album.setDescription(description);
        if (matchingConfig != null) album.setMatchingConfig(matchingConfig);
        album.setUpdatedAt(LocalDateTime.now());
        
        return albumRepository.save(album);
    }
    
    @Override
    public void deleteAlbum(String id) {
        log.info("删除相册：{}", id);
        
        Album album = albumRepository.findById(id).orElse(null);
        if (album == null) {
            throw new RuntimeException("相册不存在");
        }
        
        // 检查是否有子相册
        List<Album> childAlbums = albumRepository.findByParentIdOrderBySortOrderAsc(id);
        if (!childAlbums.isEmpty()) {
            throw new RuntimeException("该相册下还有 " + childAlbums.size() + " 个子相册，请先删除子相册后再删除当前相册");
        }
        
        // 检查相册下是否有图片
        int imageCount = imageRepository.countByAlbumIdAndDeletedFalse(id);
        if (imageCount > 0) {
            throw new RuntimeException("相册下仍有 " + imageCount + " 张图片，请先删除图片后再删除相册");
        }
        
        albumRepository.deleteById(id);
    }
    
    @Override
    public Integer getImageCount(String albumId) {
        return (int) imageRepository.countByAlbumIdAndDeletedFalse(albumId);
    }
    
    /**
     * 更新相册图片数量
     */
    public void updateImageCount(String albumId) {
        albumRepository.findById(albumId).ifPresent(album -> {
            int count = (int) imageRepository.countByAlbumIdAndDeletedFalse(albumId);
            album.setImageCount(count);
            album.setUpdatedAt(LocalDateTime.now());
            albumRepository.save(album);
        });
    }
    
    /**
     * 根据关键词查找匹配的相册
     */
    public Optional<Album> findByKeyword(String keyword) {
        List<Album> albums = albumRepository.findAll();
        return albums.stream()
                .filter(album -> album.getKeywords() != null)
                .filter(album -> album.getKeywords().stream()
                        .anyMatch(k -> keyword.toLowerCase().contains(k.toLowerCase())))
                .findFirst();
    }
    
    @Override
    public int batchUpdateMatchingMode(String mode) {
        log.info("批量更新相册匹配模式为：{}", mode);
        
        // 验证模式是否有效
        String[] validModes = {"contains", "exact", "startsWith", "endsWith", "regex", "fuzzy"};
        boolean isValidMode = false;
        for (String validMode : validModes) {
            if (validMode.equals(mode)) {
                isValidMode = true;
                break;
            }
        }
        
        if (!isValidMode) {
            throw new IllegalArgumentException("无效的匹配模式：" + mode + "，有效值为：contains, exact, startsWith, endsWith, regex, fuzzy");
        }
        
        // 构建匹配配置 JSON
        String matchingConfig = String.format("{\"mode\":\"%s\",\"caseSensitive\":false}", mode);
        
        // 获取所有相册并更新
        List<Album> albums = albumRepository.findAll();
        int updatedCount = 0;
        
        for (Album album : albums) {
            album.setMatchingConfig(matchingConfig);
            album.setUpdatedAt(LocalDateTime.now());
            albumRepository.save(album);
            updatedCount++;
        }
        
        log.info("批量更新完成，共更新 {} 个相册的匹配模式为 {}", updatedCount, mode);
        return updatedCount;
    }
    
    @Override
    public int resetAllMatchingConfig() {
        log.info("重置所有相册的匹配配置为默认值（包含匹配）");
        return batchUpdateMatchingMode("contains");
    }
    
    @Override
    public Album createAlbumWithParent(String name, String parentId, String description, List<String> keywords) {
        log.info("创建层级相册：{}，父级：{}", name, parentId);
        
        String userId = "user-1";
        String path;
        String fullName;
        
        if (parentId != null) {
            // 获取父相册
            Album parent = albumRepository.findById(parentId)
                    .orElseThrow(() -> new RuntimeException("父相册不存在"));
            path = parent.getPath() != null ? parent.getPath() + "/" + name : name;
            fullName = parent.getName() + "-" + name;
        } else {
            path = name;
            fullName = name;
        }
        
        // 检查是否已存在
        Optional<Album> existing = albumRepository.findByUserIdAndPath(userId, path);
        if (existing.isPresent()) {
            log.info("相册已存在：{}", path);
            return existing.get();
        }
        
        long count = albumRepository.count();
        
        Album album = Album.builder()
                .id("album-" + UUID.randomUUID().toString().substring(0, 8))
                .name(name)
                .fullName(fullName)
                .parentId(parentId)
                .path(path)
                .description(description)
                .keywords(keywords != null ? keywords : Arrays.asList(name))
                .isSystem(false)
                .imageCount(0)
                .sortOrder((int) count)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .userId(userId)
                .build();
        
        return albumRepository.save(album);
    }
    
    @Override
    public Album getOrCreateAlbumByPath(String fullPath) {
        log.info("根据路径获取或创建相册：{}", fullPath);
        
        String userId = "user-1";
        
        // 检查是否已存在
        Optional<Album> existing = albumRepository.findByUserIdAndPath(userId, fullPath);
        if (existing.isPresent()) {
            return existing.get();
        }
        
        // 精确匹配失败，尝试模糊匹配（处理文件名包含父相册名称的情况）
        // 例如：文件名"松野湃（2）"应该匹配已存在的"松野湃"相册
        String normalizedPath = fullPath.replaceAll("[（(][0-9]+[)）]$", "").replaceAll("-[0-9]+$", "").trim();
        
        if (!normalizedPath.equals(fullPath)) {
            // 尝试用规范化后的名称查找
            Optional<Album> fuzzyMatch = albumRepository.findByUserIdAndPath(userId, normalizedPath);
            if (fuzzyMatch.isPresent()) {
                log.info("模糊匹配到已有相册：{} -> {}", fullPath, normalizedPath);
                return fuzzyMatch.get();
            }
            
            // 尝试查找路径中包含该名称的相册
            List<Album> matches = albumRepository.findByUserIdAndPathContaining(userId, normalizedPath);
            if (!matches.isEmpty()) {
                log.info("从 {} 个匹配中找到相册：{}", matches.size(), matches.get(0).getPath());
                return matches.get(0);
            }
        }
        
        // 解析路径创建层级相册
        String[] parts = fullPath.split("/");
        String parentId = null;
        Album parent = null;
        
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) continue;
            
            String currentPath = String.join("/", Arrays.copyOf(parts, i + 1));
            Optional<Album> current = albumRepository.findByUserIdAndPath(userId, currentPath);
            
            if (current.isPresent()) {
                parent = current.get();
                parentId = parent.getId();
            } else {
                // 创建新的相册
                // fullName 只显示当前相册的名称，不包含父级名称
                // 层级关系通过 parentId 和 path 体现
                String fullName = part;
                
                Album album = Album.builder()
                        .id("album-" + UUID.randomUUID().toString().substring(0, 8))
                        .name(part)
                        .fullName(fullName)
                        .parentId(parentId)
                        .path(currentPath)
                        .keywords(Arrays.asList(part))
                        .isSystem(false)
                        .imageCount(0)
                        .sortOrder((int) albumRepository.count())
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .userId(userId)
                        .build();
                
                parent = albumRepository.save(album);
                parentId = parent.getId();
                log.info("创建相册：{}，路径：{}", part, currentPath);
            }
        }
        
        return parent;
    }
    
    @Override
    public List<Album> getAlbumTree(String userId) {
        log.info("获取用户层级相册树：{}", userId);
        return albumRepository.findByUserIdAndParentIdIsNullOrderBySortOrderAsc(userId);
    }
    
    @Override
    public List<Album> getChildAlbums(String parentId) {
        return albumRepository.findByUserIdAndParentIdOrderBySortOrderAsc("user-1", parentId);
    }
}
