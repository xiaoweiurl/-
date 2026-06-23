package com.imagemanager.service.impl;

import com.imagemanager.dto.BatchDownloadRequest;
import com.imagemanager.dto.BatchDownloadResponse;
import com.imagemanager.dto.ImageQueryRequest;
import com.imagemanager.dto.PageResponse;
import com.imagemanager.entity.Album;
import com.imagemanager.entity.Image;
import com.imagemanager.entity.Product;
import com.imagemanager.entity.User;
import com.imagemanager.repository.AlbumRepository;
import com.imagemanager.repository.ImageDynamicRepository;
import com.imagemanager.repository.ImageRepository;
import com.imagemanager.repository.ProductRepository;
import com.imagemanager.service.AIRecognitionService;
import com.imagemanager.service.AlbumService;
import com.imagemanager.service.ImageEnhancementService;
import com.imagemanager.service.ImageTableService;
import com.imagemanager.service.FileStorageService;
import com.imagemanager.service.ImageService;
import com.imagemanager.util.CharsetUtil;
import com.imagemanager.util.SessionUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.awt.image.BufferedImage;

/**
 * 图片服务实现类
 * 预置户外服装图片数据，支持自动分类
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class ImageServiceImpl implements ImageService {
    
    // 使用北京时区（与数据库保持一致）
    private static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");
    
    @Value("${app.upload.path:./uploads}")
    private String uploadPath;
    
    @Autowired(required = false)
    private FileStorageService fileStorageService;
    
    @Autowired
    private AIRecognitionService aiRecognitionService;
    
    @Autowired
    private AlbumService albumService;
    
    @Autowired
    private ImageRepository imageRepository;

    @Autowired
    private AlbumRepository albumRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ImageEnhancementService imageEnhancementService;

    @Autowired(required = false)
    private UserService userService;
    
    @Autowired
    private ImageTableService imageTableService;
    
    @Autowired
    private ImageDynamicRepository imageDynamicRepository;

    @Value("${app.image.enhance:true}")
    private boolean enableImageEnhance;

    @Value("${app.image.super-resolution:false}")
    private boolean enableSuperResolution;

    /**
     * 根据图片中的 userId 获取对应用户名（用于动态表命名）
     */
    private String getUsernameForTable(Image image) {
        if (image == null || image.getUserId() == null) return null;
        try {
            if (userService != null) {
                User user = userService.getUserById(image.getUserId());
                if (user != null && user.getUsername() != null) {
                    return user.getUsername();
                }
            }
        } catch (Exception e) {
            log.warn("获取用户名失败，fallback 使用 userId: {}", image.getUserId());
        }
        return image.getUserId();
    }

    /**
     * 获取当前登录用户的用户名（用于动态表命名）
     * 优先从 SessionUtil 获取，失败时通过 userId 查询数据库兜底
     */
    private String getCurrentUsernameForTable() {
        // 1. 优先从 Session 获取用户名
        String username = SessionUtil.getCurrentUsername();
        if (username != null && !username.isEmpty()) {
            log.debug("getCurrentUsernameForTable: 从 Session 获取用户名={}", username);
            return username;
        }
        // 2. fallback: 通过 userId 查数据库获取用户名
        String userId = SessionUtil.getCurrentUserId();
        log.info("getCurrentUsernameForTable: Session 无用户名, 尝试通过 userId={} 查数据库", userId);
        if (userId != null && userService != null) {
            try {
                User user = userService.getUserById(userId);
                if (user != null && user.getUsername() != null) {
                    log.info("getCurrentUsernameForTable: 从数据库获取用户名={}", user.getUsername());
                    return user.getUsername();
                }
            } catch (Exception e) {
                log.warn("通过 userId 查询用户名失败: {}", userId);
            }
        }
        // 3. 最后 fallback: 使用 userId（UUID格式）
        log.warn("getCurrentUsernameForTable: 所有方式均失败, fallback userId={}", userId);
        return userId;
    }

    /**
     * 同步图片数据到动态表
     */
    private void syncToDynamicTable(Image image) {
        if (image == null || image.getUserId() == null) return;
        String tableKey = getUsernameForTable(image);
        try {
            imageTableService.ensureUserImageTable(tableKey);
            imageDynamicRepository.update(image, tableKey);
            log.debug("同步到动态表成功, tableKey={}, imageId={}", tableKey, image.getId());
        } catch (Exception e) {
            log.error("同步到动态表失败: tableKey={}, imageId={}, error={}", tableKey, image.getId(), e.getMessage(), e);
        }
    }

    /**
     * 从动态表中软删除图片（标记 deleted=true）
     */
    private void deleteFromDynamicTable(Image image) {
        if (image == null || image.getUserId() == null) return;
        String tableKey = getUsernameForTable(image);
        try {
            imageDynamicRepository.softDelete(image.getId(), tableKey);
            log.debug("动态表软删除成功, tableKey={}, imageId={}", tableKey, image.getId());
        } catch (Exception e) {
            log.error("动态表软删除失败: imageId={}, error={}", image.getId(), e.getMessage(), e);
        }
    }

    /**
     * 从动态表中硬删除图片（永久删除）
     */
    private void hardDeleteFromDynamicTable(Image image) {
        if (image == null || image.getUserId() == null) return;
        String tableKey = getUsernameForTable(image);
        try {
            imageDynamicRepository.hardDelete(image.getId(), tableKey);
            log.debug("动态表硬删除成功, tableKey={}, imageId={}", tableKey, image.getId());
        } catch (Exception e) {
            log.error("动态表硬删除失败: imageId={}, error={}", image.getId(), e.getMessage(), e);
        }
    }

    /**
     * 从动态表中恢复图片
     */
    private void restoreInDynamicTable(Image image) {
        if (image == null || image.getUserId() == null) return;
        String tableKey = getUsernameForTable(image);
        try {
            imageDynamicRepository.restore(image.getId(), tableKey);
            log.debug("动态表恢复成功, tableKey={}, imageId={}", tableKey, image.getId());
        } catch (Exception e) {
            log.error("动态表恢复失败: imageId={}, error={}", image.getId(), e.getMessage(), e);
        }
    }

    /**
     * 创建通知（如果有UserService）
     */
    private void createNotificationSafe(String title, String content, String type) {
        try {
            String currentUserId = SessionUtil.getCurrentUserId();
            if (userService != null && currentUserId != null) {
                com.imagemanager.dto.CreateNotificationRequest request = new com.imagemanager.dto.CreateNotificationRequest();
                request.setTitle(title);
                request.setContent(content);
                request.setType(type);
                userService.createNotification(request);
            }
        } catch (Exception e) {
            // 通知创建失败不影响主流程
            org.slf4j.LoggerFactory.getLogger(ImageServiceImpl.class).warn("创建通知失败: {}", e.getMessage());
        }
    }
    
    
    @Override
    public PageResponse<Image> queryImages(ImageQueryRequest request) {
        log.info("查询图片列表，参数：{}", request);

        // 获取当前用户ID（final，lambda 需要捕获）
        final String currentUserId = SessionUtil.getCurrentUserId();
        // 获取当前用户名（用于动态表命名）
        String currentUsername = getCurrentUsernameForTable();
        if (currentUsername == null) {
            currentUsername = currentUserId;
        }
        // 设置公司过滤
        if (request.getCompany() == null || request.getCompany().isEmpty()) {
            request.setCompany(SessionUtil.getCurrentCompany());
        }
        log.info("数据隔离检查：currentUserId={}, currentUsername={}, company={}, onlyMine={}", currentUserId, currentUsername, request.getCompany(), request.getOnlyMine());

        // 动态表查询模式
        if (currentUserId != null) {
            // 确保用户动态表存在
            try {
                imageTableService.ensureUserImageTable(currentUsername);
            } catch (Exception e) {
                log.warn("确保用户表存在失败: {}", e.getMessage());
            }

            // 我的知识库 - 查当前用户动态表
            if (request.getOnlyMine() != null && request.getOnlyMine()) {
                log.info("查询【我的知识库】(动态表), userId={}, username={}", currentUserId, currentUsername);
                try {
                    PageResponse<Image> result = imageDynamicRepository.queryMyImages(request, currentUsername);
                    log.info("我的知识库查询结果: {} 张", result.getTotal());
                    return result;
                } catch (Exception e) {
                    log.warn("动态表查询失败，返回空数据: {}", e.getMessage());
                    return PageResponse.of(Collections.<Image>emptyList(), 0L, request.getPage(), request.getPageSize());
                }
            }

            // 二创中心 - 查其他用户动态表 UNION ALL（不降级主表）
            if (request.getOtherUsers() != null && request.getOtherUsers()) {
                log.info("查询【二创中心】(其他用户动态表), currentUserId={}, currentUsername={}", currentUserId, currentUsername);
                try {
                    PageResponse<Image> result = imageDynamicRepository.queryOtherUsersImages(request, currentUsername);
                    log.info("二创中心查询结果: {} 张", result.getTotal());
                    return result;
                } catch (Exception e) {
                    log.warn("动态表查询失败，返回空数据: {}", e.getMessage());
                    return PageResponse.of(Collections.<Image>emptyList(), 0L, request.getPage(), request.getPageSize());
                }
            }

            // 收藏夹 - 查当前用户动态表（不降级主表）
            if (request.getFavorite() != null && request.getFavorite()) {
                log.info("查询【收藏夹】(动态表), userId={}, username={}", currentUserId, currentUsername);
                try {
                    PageResponse<Image> result = imageDynamicRepository.queryFavorites(request, currentUsername);
                    log.info("收藏夹查询结果: {} 张", result.getTotal());
                    return result;
                } catch (Exception e) {
                    log.warn("动态表查询失败，返回空数据: {}", e.getMessage());
                    return PageResponse.of(Collections.<Image>emptyList(), 0L, request.getPage(), request.getPageSize());
                }
            }

            // 回收站 - 查当前用户动态表（不降级主表）
            if (request.getDeleted() != null && request.getDeleted()) {
                log.info("查询【回收站】(动态表), userId={}, username={}", currentUserId, currentUsername);
                try {
                    PageResponse<Image> result = imageDynamicRepository.queryTrash(request, currentUsername);
                    log.info("回收站查询结果: {} 张", result.getTotal());
                    return result;
                } catch (Exception e) {
                    log.warn("动态表查询失败，返回空数据: {}", e.getMessage());
                    return PageResponse.of(Collections.<Image>emptyList(), 0L, request.getPage(), request.getPageSize());
                }
            }

            // 相册查询 - 数据是共享的，走主表 JPA 查询
            if (request.getAlbumId() != null && !request.getAlbumId().isEmpty()) {
                log.info("查询【相册图片】(主表共享数据), albumId={}", request.getAlbumId());
                // fall through 到 JPA 主表查询
            }

            // 全部知识 - 查询主表（共享数据）
            log.info("查询【全部知识】(主表), userId={}", currentUserId);
            // fall through 到 JPA 主表查询
        }

        // JPA 主表查询（全部知识 / 无用户ID降级）
        log.info("使用 JPA 查询主表数据");

        // 构建排序
        String sortBy = request.getSortBy() != null ? request.getSortBy() : "createdAt";
        String sortOrder = request.getSortOrder() != null ? request.getSortOrder() : "desc";
        Sort sort = sortOrder.equals("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        // 构建分页
        int page = request.getPage() != null ? request.getPage() - 1 : 0;
        int pageSize = request.getPageSize() != null ? request.getPageSize() : 20;
        Pageable pageable = PageRequest.of(page, pageSize, sort);

        // 先检查数据库中的数据
        long totalProducts = productRepository.count();
        long totalImages = imageRepository.count();
        long totalImagesWithProduct = imageRepository.countByProductIdIsNotNull();
        long totalMainImages = imageRepository.countByIsMainImageAndDeleted(true, false);
        log.info("数据库状态：Product总数={}, Image总数={}, Image有Product关联={}, 主图数量={}",
            totalProducts, totalImages, totalImagesWithProduct, totalMainImages);

        // 查询图片
        Page<Image> imagePage;
        
        // 检查是否有高级搜索参数
        boolean hasAdvancedFilters = 
            (request.getKeyword() != null && !request.getKeyword().isEmpty()) ||
            (request.getTags() != null && !request.getTags().isEmpty()) ||
            (request.getStartDate() != null && !request.getStartDate().isEmpty()) ||
            (request.getEndDate() != null && !request.getEndDate().isEmpty()) ||
            (request.getFileType() != null && !request.getFileType().isEmpty()) ||
            (request.getAlbumId() != null && !request.getAlbumId().isEmpty()) ||
            request.getFavorite() != null ||
            request.getOnlyMainImage() != null ||
            request.getOnlyMine() != null ||
            (request.getUserId() != null && !request.getUserId().isEmpty()) ||
            (request.getOtherUsersUserId() != null && !request.getOtherUsersUserId().isEmpty());

        if (hasAdvancedFilters) {
            // 使用数据库分页查询（真正的数据库层面分页）
            log.info("使用数据库分页查询，参数: {}", request);
            
            // 处理日期范围
            LocalDateTime startDate = null;
            LocalDateTime endDate = null;
            
            if (request.getStartDate() != null && !request.getStartDate().isEmpty()) {
                try {
                    startDate = LocalDateTime.parse(request.getStartDate() + "T00:00:00");
                } catch (Exception e) {
                    log.warn("解析开始日期失败: {}", request.getStartDate());
                }
            }
            
            if (request.getEndDate() != null && !request.getEndDate().isEmpty()) {
                try {
                    endDate = LocalDateTime.parse(request.getEndDate() + "T23:59:59");
                } catch (Exception e) {
                    log.warn("解析结束日期失败: {}", request.getEndDate());
                }
            }
            
            // 处理文件类型（支持多个）
            List<String> fileTypes = null;
            if (request.getFileType() != null && !request.getFileType().isEmpty()) {
                fileTypes = java.util.Arrays.asList(request.getFileType().split(","));
                log.info("文件类型筛选: {}", fileTypes);
            }
            
            // 处理相册ID（支持多个，兼容单个的情况）
            // 支持层级相册：点击父相册时显示所有子相册的图片
            List<String> albumIds = null;
            if (request.getAlbumId() != null && !request.getAlbumId().isEmpty()) {
                albumIds = new java.util.ArrayList<>();
                
                // 解析传入的相册ID（可能是逗号分隔的多个ID）
                List<String> targetAlbumIds = new java.util.ArrayList<>();
                if (request.getAlbumId().contains(",")) {
                    for (String albumId : request.getAlbumId().split(",")) {
                        targetAlbumIds.add(albumId.trim());
                    }
                } else {
                    targetAlbumIds.add(request.getAlbumId());
                }
                
                // 递归获取每个目标相册及其所有子相册ID
                for (String albumId : targetAlbumIds) {
                    collectAllAlbumIds(albumId, albumIds);
                }
                
                log.info("相册ID筛选（含所有子相册）: {}", albumIds);
            }
            
            // 处理标签
            List<String> tags = request.getTags();
            
            // 使用 JPA Specification 进行动态条件查询
            final List<String> finalAlbumIds = albumIds;
            final List<String> finalFileTypes = fileTypes;
            final List<String> finalTags = tags;
            final LocalDateTime finalStartDate = startDate;
            final LocalDateTime finalEndDate = endDate;
            final String finalKeyword = request.getKeyword();
            final Boolean finalFavorite = request.getFavorite();
            
            // 数据隔离：使用方法开头已声明的 currentUserId
            final Boolean finalOnlyMine = request.getOnlyMine();
            log.info("数据隔离检查：currentUserId={}, onlyMine={}", currentUserId, finalOnlyMine);
            
            org.springframework.data.jpa.domain.Specification<Image> spec = (root, query, cb) -> {
                List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
                
                // 基础条件：未删除的主图
                predicates.add(cb.equal(root.get("deleted"), false));
                predicates.add(cb.equal(root.get("isMainImage"), true));
                
                // 数据隔离：按用户ID过滤（我的知识库 / 收藏夹 / 回收站）
                if (request.getUserId() != null && !request.getUserId().isEmpty()) {
                    predicates.add(cb.equal(root.get("userId"), request.getUserId()));
                }
                
                // 二创中心：排除当前用户，查询其他用户上传的图片
                if (request.getOtherUsersUserId() != null && !request.getOtherUsersUserId().isEmpty()) {
                    predicates.add(cb.notEqual(root.get("userId"), request.getOtherUsersUserId()));
                }
                
                // 关键词筛选
                if (finalKeyword != null && !finalKeyword.isEmpty()) {
                    jakarta.persistence.criteria.Predicate titleLike = cb.like(
                        cb.lower(root.get("title")), "%" + finalKeyword.toLowerCase() + "%");
                    jakarta.persistence.criteria.Predicate descLike = cb.like(
                        cb.lower(root.get("description")), "%" + finalKeyword.toLowerCase() + "%");
                    predicates.add(cb.or(titleLike, descLike));
                }
                
                // 相册ID筛选
                if (finalAlbumIds != null && !finalAlbumIds.isEmpty()) {
                    predicates.add(root.get("albumId").in(finalAlbumIds));
                }
                
                // 收藏筛选
                if (finalFavorite != null) {
                    predicates.add(cb.equal(root.get("favorite"), finalFavorite));
                }
                
                // 文件类型筛选
                if (finalFileTypes != null && !finalFileTypes.isEmpty()) {
                    predicates.add(root.get("fileType").in(finalFileTypes));
                }
                
                // 日期范围筛选
                if (finalStartDate != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), finalStartDate));
                }
                if (finalEndDate != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), finalEndDate));
                }
                
                // 标签筛选（支持多标签，包含任意一个即可）
                if (finalTags != null && !finalTags.isEmpty()) {
                    jakarta.persistence.criteria.Join<Object, Object> tagsJoin = root.join("tags");
                    predicates.add(tagsJoin.in(finalTags));
                }
                
                return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
            };
            
            // 执行分页查询
            imagePage = imageRepository.findAll(spec, pageable);
            
            log.info("数据库分页查询完成: 第{}页, 每页{}条, 当前页{}条, 总计{}条", 
                request.getPage(), request.getPageSize(), imagePage.getContent().size(), imagePage.getTotalElements());
        } else if (request.getKeyword() != null && !request.getKeyword().isEmpty()) {
            // 简单关键词搜索
            if (request.getAlbumId() != null) {
                imagePage = imageRepository.searchByAlbumAndKeyword(
                        request.getAlbumId(), request.getKeyword(), pageable);
            } else {
                imagePage = imageRepository.searchByKeyword(request.getKeyword(), pageable);
            }
        } else if (request.getAlbumId() != null) {
            // 相册查询：支持层级查询（递归获取所有子相册）
            String albumId = request.getAlbumId();
            log.info("查询相册，albumId={}", albumId);
            
            // 递归获取所有子相册ID
            List<String> allAlbumIds = new ArrayList<>();
            allAlbumIds.add(albumId);  // 先添加当前相册
            collectAllAlbumIds(albumId, allAlbumIds);  // 递归收集子相册
            log.info("层级查询：找到 {} 个相册（包含子相册）", allAlbumIds.size());

            // 如果 onlyMainImage 为 true，只查询主图
            if (request.getOnlyMainImage() != null && request.getOnlyMainImage()) {
                log.info("只查询主图，albumIds={}", allAlbumIds);
                imagePage = imageRepository.findByAlbumIdInAndIsMainImageAndDeleted(allAlbumIds, true, false, pageable);
                log.info("查询到的主图数量：{}", imagePage.getContent().size());
            } else {
                // 查询该相册的所有图片
                log.info("查询所有图片（包括主图和详情图），albumIds={}", allAlbumIds);
                imagePage = imageRepository.findByAlbumIdInAndDeleted(allAlbumIds, false, pageable);
                log.info("查询到的图片数量：{}", imagePage.getContent().size());
            }

            // 打印Image的详细信息
            if (!imagePage.getContent().isEmpty()) {
                imagePage.getContent().subList(0, Math.min(3, imagePage.getContent().size())).forEach(img -> {
                    log.info("Image详情: id={}, productId={}, isMainImage={}, deleted={}, albumId={}, albumName={}, url={}",
                        img.getId(), img.getProductId(), img.getIsMainImage(), img.getDeleted(),
                        img.getAlbumId(), img.getAlbumName(), img.getUrl());
                });
            }
        } else if (request.getFavorite() != null && request.getFavorite()) {
            imagePage = imageRepository.findByFavoriteTrueAndDeletedFalseAndIsMainImageTrue(pageable);
        } else if (request.getFileType() != null) {
            imagePage = imageRepository.findByFileTypeAndDeletedFalseAndIsMainImageTrue(request.getFileType(), pageable);
        } else {
            imagePage = imageRepository.findByDeletedFalseAndIsMainImageTrue(pageable);
        }
        
        log.info("查询完成，返回 {} 条记录，总计 {} 条", 
            imagePage.getContent().size(), imagePage.getTotalElements());
        
        return PageResponse.of(
                imagePage.getContent(),
                imagePage.getTotalElements(),
                imagePage.getNumber() + 1,
                imagePage.getSize()
        );
    }
    
    @Override
    public Image getImageById(String id) {
        log.info("获取图片详情，ID：{}", id);
        return imageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("图片不存在"));
    }
    
    @Override
    public Image uploadImage(MultipartFile file, String title, String albumId, List<String> tags) {
        log.info("上传图片：{}", file.getOriginalFilename());
        
        try {
            String originalFilename = file.getOriginalFilename();
            String imageUrl;
            String fileKey = null;
            
            // 使用存储服务上传文件
            if (fileStorageService != null) {
                imageUrl = fileStorageService.uploadFile(file, "images");
                fileKey = fileStorageService.getStorageKey(imageUrl);
            } else {
                // 本地存储（开发模式）
                imageUrl = "/uploads/" + UUID.randomUUID() + getFileExtension(originalFilename);
            }
            
            // 自动分类
            String finalAlbumId = albumId;
            String finalAlbumName = null;
            List<String> finalTags = tags;
            String classifyMethod = "user";

            // 1. 首先检查文件名是否包含层级目录（如 "松野湃-速干T恤"）
            if (finalAlbumId == null && originalFilename != null) {
                String pathFromFilename = parseHierarchyFromFilename(originalFilename);
                if (pathFromFilename != null) {
                    log.info("从文件名中解析出层级路径: {}", pathFromFilename);
                    try {
                        Album hierarchyAlbum = albumService.getOrCreateAlbumByPath(pathFromFilename);
                        if (hierarchyAlbum != null) {
                            finalAlbumId = hierarchyAlbum.getId();
                            finalAlbumName = hierarchyAlbum.getFullName();
                            classifyMethod = "filename-hierarchy";
                            log.info("根据文件名自动创建/获取层级相册: ID={}, 名称={}", finalAlbumId, finalAlbumName);
                        }
                    } catch (Exception e) {
                        log.warn("根据文件名创建层级相册失败: {}", e.getMessage());
                    }
                }
            }

            // 2. 如果没有从文件名中提取到目录，使用 AI 服务分析
            if (finalAlbumId == null) {
                if (albumId == null || tags == null || tags.isEmpty()) {
                    // 使用AI服务分析图片
                    List<Album> albums = albumService.getAllAlbums();
                    AIRecognitionService.AIRecognitionResult result = aiRecognitionService.analyzeImage(
                            imageUrl, originalFilename, albums);

                    if (albumId == null && result.getAlbumId() != null) {
                        finalAlbumId = result.getAlbumId();
                        finalAlbumName = result.getAlbumName();
                    } else if (albumId == null && result.shouldCreateNewAlbum()) {
                        // 如果没有匹配到相册，尝试根据名称匹配已有相册
                        log.info("尝试根据名称匹配已有相册: {}", result.getSuggestedAlbumName());
                        Album matchedAlbum = findOrMatchAlbum(result.getSuggestedAlbumName());
                        if (matchedAlbum != null) {
                            finalAlbumId = matchedAlbum.getId();
                            finalAlbumName = matchedAlbum.getName();
                            classifyMethod = "auto-matched";
                            log.info("成功匹配到已有相册: ID={}, 名称={}", finalAlbumId, finalAlbumName);
                        } else {
                            log.warn("未找到匹配的相册，跳过相册分配: {}", result.getSuggestedAlbumName());
                            // 不创建新相册，也不分配相册
                            classifyMethod = "unmatched";
                        }
                    }

                    if (tags == null || tags.isEmpty()) {
                        finalTags = result.getTags();
                    }

                    // 如果没有设置分类方法，使用AI结果的方法
                    if ("user".equals(classifyMethod) && result.getMethod() != null) {
                        classifyMethod = result.getMethod();
                    }
                }
            }
            
            // 如果有相册ID但没有相册名称，查找相册名称
            String albumName = finalAlbumName;
            if (finalAlbumId != null && albumName == null) {
                albumName = albumRepository.findById(finalAlbumId)
                        .map(Album::getName)
                        .orElse(null);
            }
            
            // 获取当前用户ID（数据隔离）
            String currentUserId = SessionUtil.getCurrentUserId();
            if (currentUserId == null) {
                currentUserId = "user-1"; // 降级默认
            }
            // 获取当前用户名（用于动态表命名）
            String currentUsername = getCurrentUsernameForTable();
            if (currentUsername == null) {
                currentUsername = currentUserId;
            }
            log.info("上传图片，用户ID：{}, 用户名：{}", currentUserId, currentUsername);
            
            // 创建图片记录
            Image image = Image.builder()
                    .id(UUID.randomUUID().toString())
                    .title(title != null ? title : removeFileExtension(originalFilename))
                    .originalName(originalFilename)  // 保存原始文件名
                    .url(imageUrl)
                    .thumbnailUrl(imageUrl)
                    .fileKey(fileKey)
                    .size(file.getSize())
                    .sizeFormatted(formatFileSize(file.getSize()))
                    .fileType(getFileType(file.getContentType()))
                    .albumId(finalAlbumId)
                    .albumName(albumName)
                    .tags(finalTags != null ? new java.util.ArrayList<>(finalTags) : new java.util.ArrayList<>())
                    .classifyMethod(classifyMethod)
                    .favorite(false)
                    .isMainImage(true)  // 单独上传的图片默认为主图
                    .displayOrder(0)
                    .createdAt(LocalDateTime.now(BEIJING_ZONE))
                    .updatedAt(LocalDateTime.now(BEIJING_ZONE))
                    .userId(currentUserId)  // 使用当前用户ID
                    .company(SessionUtil.getCurrentCompany())  // 按公司隔离
                    .deleted(false)
                    .viewCount(0)
                    .downloadCount(0)
                    .originalUrl(imageUrl)
                    .build();
            
            image = imageRepository.save(image);
            
            // 同时保存到用户动态表（方案A：物理隔离）
            try {
                imageTableService.ensureUserImageTable(currentUsername);
                imageDynamicRepository.save(image, currentUsername);
                log.info("图片已保存到用户动态表: username={}", currentUsername);
            } catch (Exception e) {
                log.error("保存到用户动态表失败: userId={}, username={}, error={}", 
                    currentUserId, currentUsername, e.getMessage(), e);
            }
            
            // 更新相册图片数量
            if (finalAlbumId != null) {
                updateAlbumImageCount(finalAlbumId);
            }
            
            log.info("图片上传成功，自动分类: {}, 标签: {}", albumName, finalTags);
            
            return image;
        } catch (Exception e) {
            log.error("上传图片失败", e);
            throw new RuntimeException("上传图片失败：" + e.getMessage());
        }
    }
    
    @Override
    public Image updateImage(String id, String title, String albumId, List<String> tags, String description) {
        log.info("更新图片信息，ID：{}", id);
        
        Image image = imageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("图片不存在"));
        
        String oldAlbumId = image.getAlbumId();
        
        if (title != null) image.setTitle(title);
        if (albumId != null) {
            image.setAlbumId(albumId);
            // 更新相册名称
            String albumName = albumRepository.findById(albumId)
                    .map(Album::getName)
                    .orElse(null);
            if (albumName != null) {
                image.setAlbumName(albumName);
            }
        }
        if (tags != null) image.setTags(new java.util.ArrayList<>(tags));
        if (description != null) image.setDescription(description);
        image.setUpdatedAt(LocalDateTime.now(BEIJING_ZONE));
        
        image = imageRepository.save(image);
        
        // 更新相册图片数量
        if (oldAlbumId != null && !oldAlbumId.equals(albumId)) {
            updateAlbumImageCount(oldAlbumId);
        }
        if (albumId != null) {
            updateAlbumImageCount(albumId);
        }
        
        return image;
    }
    
    @Override
    public void deleteImage(String id) {
        log.info("删除图片，ID：{}", id);
        
        Image image = imageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("图片不存在"));
        
        // 如果是主图，同时删除所有关联的详情图
        if (Boolean.TRUE.equals(image.getIsMainImage()) && image.getProductId() != null) {
            List<Image> relatedImages = imageRepository.findByProductIdAndDeleted(image.getProductId(), false);
            for (Image relatedImage : relatedImages) {
                relatedImage.setDeleted(true);
                relatedImage.setDeletedAt(LocalDateTime.now(BEIJING_ZONE));
                imageRepository.save(relatedImage);
                // 软删除动态表中的关联详情图
                deleteFromDynamicTable(relatedImage);
            }
            log.info("同时删除了 {} 张关联的详情图", relatedImages.size());
        }
        
        image.setDeleted(true);
        image.setDeletedAt(LocalDateTime.now(BEIJING_ZONE));
        imageRepository.save(image);
        
        // 软删除动态表中的图片
        deleteFromDynamicTable(image);
        
        // 更新相册图片数量
        if (image.getAlbumId() != null) {
            updateAlbumImageCount(image.getAlbumId());
        }
    }

    @Override
    public void recordView(String id) {
        Image image = imageRepository.findById(id).orElse(null);
        if (image != null) {
            int currentViews = image.getViewCount() != null ? image.getViewCount() : 0;
            image.setViewCount(currentViews + 1);
            imageRepository.save(image);
            log.info("图片 {} 预览次数 +1，当前: {}", id, image.getViewCount());
        }
    }
    
    @Override
    public void batchDelete(List<String> ids) {
        log.info("批量删除图片，数量：{}", ids.size());
        ids.forEach(this::deleteImage);
    }
    
    @Override
    public int permanentDelete(String id) {
        log.info("永久删除图片，ID：{}", id);
        
        int deletedCount = 0;
        
        Image image = imageRepository.findById(id).orElse(null);
        if (image != null) {
            // 如果是主图，同时永久删除所有关联的详情图
            if (Boolean.TRUE.equals(image.getIsMainImage()) && image.getProductId() != null) {
                List<Image> relatedImages = imageRepository.findByProductIdAndDeleted(image.getProductId(), true);
                for (Image relatedImage : relatedImages) {
                    // 从存储中删除文件
                    deleteImageFile(relatedImage);
                    // 从动态表中硬删除
                    hardDeleteFromDynamicTable(relatedImage);
                    imageRepository.delete(relatedImage);
                    deletedCount++;
                    log.debug("永久删除详情图：{}", relatedImage.getId());
                }
                log.info("同时永久删除了 {} 张关联的详情图", relatedImages.size());
            }
            
            // 从存储中删除文件
            deleteImageFile(image);
            
            // 从动态表中硬删除
            hardDeleteFromDynamicTable(image);
            
            // 更新相册图片数量
            if (image.getAlbumId() != null) {
                updateAlbumImageCount(image.getAlbumId());
            }
            
            imageRepository.delete(image);
            deletedCount++; // 加上主图本身
        }
        
        log.info("永久删除完成，共删除 {} 张图片", deletedCount);
        return deletedCount;
    }
    
    @Override
    public int restoreImage(String id) {
        log.info("恢复图片，ID：{}", id);
        
        int restoredCount = 0;
        
        Image image = imageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("图片不存在"));
        
        // 如果是主图，同时恢复所有关联的详情图
        if (Boolean.TRUE.equals(image.getIsMainImage()) && image.getProductId() != null) {
            List<Image> relatedImages = imageRepository.findByProductIdAndDeleted(image.getProductId(), true);
            for (Image relatedImage : relatedImages) {
                relatedImage.setDeleted(false);
                relatedImage.setDeletedAt(null);
                imageRepository.save(relatedImage);
                // 恢复动态表中的数据
                restoreInDynamicTable(relatedImage);
                restoredCount++;
                log.debug("恢复详情图：{}", relatedImage.getId());
            }
            log.info("同时恢复了 {} 张关联的详情图", relatedImages.size());
        }
        
        // 恢复主图本身
        image.setDeleted(false);
        image.setDeletedAt(null);
        imageRepository.save(image);
        // 恢复动态表中的数据
        restoreInDynamicTable(image);
        restoredCount++;
        
        // 更新相册图片数量
        if (image.getAlbumId() != null) {
            updateAlbumImageCount(image.getAlbumId());
        }
        
        log.info("恢复完成，共恢复 {} 张图片", restoredCount);
        return restoredCount;
    }
    
    @Override
    public int batchRestore(List<String> ids) {
        log.info("批量恢复图片，数量：{}", ids.size());
        
        int totalRestored = 0;
        Set<String> affectedAlbumIds = new HashSet<>();
        
        for (String id : ids) {
            totalRestored += restoreImage(id);
            // 从已恢复的图片中收集受影响的相册ID
            imageRepository.findById(id).ifPresent(image -> {
                if (image.getAlbumId() != null) {
                    affectedAlbumIds.add(image.getAlbumId());
                }
            });
        }
        
        // 更新受影响的相册图片数量
        for (String albumId : affectedAlbumIds) {
            updateAlbumImageCount(albumId);
        }
        
        log.info("批量恢复完成，共恢复 {} 张图片", totalRestored);
        return totalRestored;
    }
    
    @Override
    public Image toggleFavorite(String id) {
        log.info("切换收藏状态，ID：{}", id);
        
        Image image = imageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("图片不存在"));
        
        image.setFavorite(!image.getFavorite());
        image.setUpdatedAt(LocalDateTime.now(BEIJING_ZONE));
        
        image = imageRepository.save(image);
        
        // 同步收藏状态到动态表
        if (image.getUserId() != null) {
            try {
                String tableKey = getUsernameForTable(image);
                imageDynamicRepository.toggleFavorite(image.getId(), tableKey);
                log.debug("动态表收藏状态同步成功, imageId={}, tableKey={}", image.getId(), tableKey);
            } catch (Exception e) {
                log.error("动态表收藏状态同步失败: imageId={}, error={}", image.getId(), e.getMessage(), e);
            }
        }
        
        return image;
    }
    
    @Override
    public Image setMainImage(String id) {
        log.info("设为主图，ID：{}", id);
        
        // 获取当前图片
        Image newMainImage = imageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("图片不存在"));
        
        // 如果已经是主图，直接返回
        if (Boolean.TRUE.equals(newMainImage.getIsMainImage())) {
            log.info("图片 {} 已经是主图", id);
            return newMainImage;
        }
        
        String productId = newMainImage.getProductId();
        String albumId = newMainImage.getAlbumId();
        
        // 查找同一商品的原主图，将其设为详情图
        if (productId != null) {
            // 通过 productId 查找原主图
            List<Image> productImages = imageRepository.findByProductIdAndDeletedOrderByDisplayOrderAsc(productId, false);
            for (Image img : productImages) {
                if (Boolean.TRUE.equals(img.getIsMainImage())) {
                    img.setIsMainImage(false);
                    img.setDisplayOrder(1); // 设为第一张详情图
                    img.setUpdatedAt(LocalDateTime.now(BEIJING_ZONE));
                    imageRepository.save(img);
                    log.info("原主图 {} 已变为详情图", img.getId());
                }
            }
        } else if (albumId != null) {
            // 如果没有 productId，通过 albumId 查找原主图
            List<Image> albumImages = imageRepository.findByAlbumIdAndDeletedFalse(albumId);
            for (Image img : albumImages) {
                if (Boolean.TRUE.equals(img.getIsMainImage())) {
                    img.setIsMainImage(false);
                    img.setDisplayOrder(1);
                    img.setUpdatedAt(LocalDateTime.now(BEIJING_ZONE));
                    imageRepository.save(img);
                    log.info("原主图 {} 已变为详情图", img.getId());
                }
            }
        }
        
        // 将当前图片设为主图
        newMainImage.setIsMainImage(true);
        newMainImage.setDisplayOrder(0); // 主图排序为 0
        newMainImage.setUpdatedAt(LocalDateTime.now(BEIJING_ZONE));
        
        return imageRepository.save(newMainImage);
    }
    
    @Override
    public int batchSetFirstDetailAsMainImage() {
        log.info("========== 批量设置第一张详情图为主图 ==========");
        
        // 查找所有 displayOrder=1 且 isMainImage=false 的详情图
        List<Image> firstDetailImages = imageRepository.findByDisplayOrderAndIsMainImageAndDeleted(1, false, false);
        log.info("找到 {} 张顺序为1的详情图", firstDetailImages.size());
        
        int successCount = 0;
        int skipCount = 0;
        
        for (Image detailImage : firstDetailImages) {
            String productId = detailImage.getProductId();
            if (productId == null) {
                skipCount++;
                continue;
            }
            
            // 查找该商品的原主图
            List<Image> productImages = imageRepository.findByProductIdAndDeletedOrderByDisplayOrderAsc(productId, false);
            Image oldMainImage = null;
            for (Image img : productImages) {
                if (Boolean.TRUE.equals(img.getIsMainImage())) {
                    oldMainImage = img;
                    break;
                }
            }
            
            // 将原主图改为详情图
            if (oldMainImage != null) {
                oldMainImage.setIsMainImage(false);
                oldMainImage.setDisplayOrder(findNextDisplayOrder(productImages, detailImage.getId()));
                oldMainImage.setUpdatedAt(LocalDateTime.now(BEIJING_ZONE));
                imageRepository.save(oldMainImage);
                log.info("原主图 {} 已变为详情图", oldMainImage.getId());
            }
            
            // 将当前详情图设为主图
            detailImage.setIsMainImage(true);
            detailImage.setDisplayOrder(0);
            detailImage.setUpdatedAt(LocalDateTime.now(BEIJING_ZONE));
            imageRepository.save(detailImage);
            
            successCount++;
            log.info("详情图 {} 已设为商品 {} 的主图", detailImage.getId(), productId);
        }
        
        log.info("========== 批量设置完成：成功 {}，跳过 {} ==========", successCount, skipCount);
        return successCount;
    }
    
    @Override
    public Map<String, Object> batchReplaceMainImage(Integer displayOrder) {
        log.info("========== 批量替换主图 ==========");
        log.info("查找 displayOrder={} 的详情图", displayOrder);
        
        // 查找所有指定 displayOrder 且 isMainImage=false 的详情图
        List<Image> detailImages = imageRepository.findByDisplayOrderAndIsMainImageAndDeleted(displayOrder, false, false);
        log.info("找到 {} 张顺序为{}的详情图", detailImages.size(), displayOrder);
        
        int successCount = 0;
        int skipCount = 0;
        int errorCount = 0;
        
        for (Image detailImage : detailImages) {
            String productId = detailImage.getProductId();
            if (productId == null) {
                skipCount++;
                log.info("跳过：图片 {} 没有 productId", detailImage.getId());
                continue;
            }
            
            try {
                // 查找该商品的原主图
                List<Image> productImages = imageRepository.findByProductIdAndDeletedOrderByDisplayOrderAsc(productId, false);
                Image oldMainImage = null;
                for (Image img : productImages) {
                    if (Boolean.TRUE.equals(img.getIsMainImage())) {
                        oldMainImage = img;
                        break;
                    }
                }
                
                // 将原主图改为详情图
                if (oldMainImage != null) {
                    oldMainImage.setIsMainImage(false);
                    oldMainImage.setDisplayOrder(findNextDisplayOrder(productImages, detailImage.getId()));
                    oldMainImage.setUpdatedAt(LocalDateTime.now(BEIJING_ZONE));
                    imageRepository.save(oldMainImage);
                    log.info("原主图 {} 已变为详情图", oldMainImage.getId());
                }
                
                // 将当前详情图设为主图
                detailImage.setIsMainImage(true);
                detailImage.setDisplayOrder(0);
                detailImage.setUpdatedAt(LocalDateTime.now(BEIJING_ZONE));
                imageRepository.save(detailImage);
                
                successCount++;
                log.info("详情图 {} 已设为商品 {} 的主图", detailImage.getId(), productId);
            } catch (Exception e) {
                errorCount++;
                log.error("处理图片 {} 时出错：{}", detailImage.getId(), e.getMessage());
            }
        }
        
        log.info("========== 批量替换完成：成功 {}，跳过 {}，失败 {} ==========", successCount, skipCount, errorCount);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", String.format("成功替换 %d 张主图，跳过 %d 张，失败 %d 张", successCount, skipCount, errorCount));
        result.put("successCount", successCount);
        result.put("skipCount", skipCount);
        result.put("errorCount", errorCount);
        return result;
    }
    
    @Override
    public Map<String, Object> batchReplaceMainImageByImageIds(List<String> imageIds) {
        log.info("========== 根据图片ID批量替换主图 ==========");
        log.info("选中的图片数量: {}", imageIds.size());
        
        if (imageIds == null || imageIds.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "没有选择图片，无需处理");
            result.put("successCount", 0);
            result.put("skipCount", 0);
            result.put("errorCount", 0);
            return result;
        }
        
        int successCount = 0;
        int skipCount = 0;
        int errorCount = 0;
        
        // 对每个选中的图片ID进行处理
        for (String newMainImageId : imageIds) {
            try {
                // 查找选中的图片
                Optional<Image> newMainImageOpt = imageRepository.findById(newMainImageId);
                if (!newMainImageOpt.isPresent()) {
                    skipCount++;
                    log.info("图片 {} 不存在，跳过", newMainImageId);
                    continue;
                }
                
                Image newMainImage = newMainImageOpt.get();
                
                // 如果已经是主图，跳过
                if (Boolean.TRUE.equals(newMainImage.getIsMainImage())) {
                    skipCount++;
                    log.info("图片 {} 已经是主图，跳过", newMainImageId);
                    continue;
                }
                
                String productId = newMainImage.getProductId();
                if (productId == null || productId.isEmpty()) {
                    skipCount++;
                    log.info("图片 {} 没有关联商品，跳过", newMainImageId);
                    continue;
                }
                
                // 获取该商品的所有图片
                List<Image> productImages = imageRepository.findByProductIdAndDeletedOrderByDisplayOrderAsc(productId, false);
                
                // 找到当前主图
                Image oldMainImage = null;
                for (Image img : productImages) {
                    if (Boolean.TRUE.equals(img.getIsMainImage())) {
                        oldMainImage = img;
                        break;
                    }
                }
                
                // 保存新主图原本的顺序，用于交换
                int originalDisplayOrder = newMainImage.getDisplayOrder() != null ? newMainImage.getDisplayOrder() : 1;
                
                // 将原主图改为详情图，继承新主图原本的顺序
                if (oldMainImage != null) {
                    oldMainImage.setIsMainImage(false);
                    oldMainImage.setDisplayOrder(originalDisplayOrder);  // 继承新主图原本的顺序
                    oldMainImage.setUpdatedAt(LocalDateTime.now(BEIJING_ZONE));
                    imageRepository.save(oldMainImage);
                    log.info("原主图 {} 已变为详情图，顺序={}", oldMainImage.getId(), originalDisplayOrder);
                }
                
                // 将选中的详情图设为主图
                newMainImage.setIsMainImage(true);
                newMainImage.setDisplayOrder(0);  // 主图统一为顺序0
                newMainImage.setUpdatedAt(LocalDateTime.now(BEIJING_ZONE));
                imageRepository.save(newMainImage);
                
                successCount++;
                log.info("商品 {} 的详情图 {} 已设为主图", productId, newMainImageId);
                
            } catch (Exception e) {
                errorCount++;
                log.error("处理图片 {} 时出错: {}", newMainImageId, e.getMessage());
            }
        }
        
        log.info("========== 批量替换完成：成功 {}，跳过 {}，失败 {} ==========", successCount, skipCount, errorCount);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", String.format("成功替换 %d 个商品的主图，跳过 %d 个，失败 %d 个", successCount, skipCount, errorCount));
        result.put("successCount", successCount);
        result.put("skipCount", skipCount);
        result.put("errorCount", errorCount);
        return result;
    }
    
    /**
     * 找到下一个可用的 displayOrder
     */
    private int findNextDisplayOrder(List<Image> images, String excludeId) {
        int maxOrder = 0;
        for (Image img : images) {
            if (!img.getId().equals(excludeId) && img.getDisplayOrder() != null && img.getDisplayOrder() > maxOrder) {
                maxOrder = img.getDisplayOrder();
            }
        }
        return maxOrder + 1;
    }
    
    @Override
    public List<Image> getImagesByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<Image> images = new ArrayList<>();
        for (String id : ids) {
            imageRepository.findById(id).ifPresent(images::add);
        }
        return images;
    }
    
    @Override
    public List<Image> getImagesByProductId(String productId) {
        if (productId == null || productId.isEmpty()) {
            return Collections.emptyList();
        }
        return imageRepository.findByProductIdAndDeletedOrderByDisplayOrderAsc(productId, false);
    }
    
    @Override
    public void batchFavorite(List<String> ids) {
        log.info("批量收藏图片，数量：{}", ids.size());
        ids.forEach(id -> {
            imageRepository.findById(id).ifPresent(image -> {
                image.setFavorite(true);
                image.setUpdatedAt(LocalDateTime.now(BEIJING_ZONE));
                imageRepository.save(image);
            });
        });
    }
    
    @Override
    public void moveToAlbum(List<String> ids, String albumId) {
        log.info("移动图片到相册，数量：{}，相册ID：{}", ids.size(), albumId);
        
        // 获取相册名称
        String albumName = albumRepository.findById(albumId)
                .map(Album::getName)
                .orElse(null);
        
        for (String id : ids) {
            imageRepository.findById(id).ifPresent(image -> {
                String oldAlbumId = image.getAlbumId();
                image.setAlbumId(albumId);
                image.setAlbumName(albumName);
                image.setUpdatedAt(LocalDateTime.now(BEIJING_ZONE));
                imageRepository.save(image);
                
                // 如果是主图，同时移动所有关联的详情图
                if (Boolean.TRUE.equals(image.getIsMainImage()) && image.getProductId() != null) {
                    List<Image> relatedImages = imageRepository.findByProductIdAndDeletedOrderByDisplayOrderAsc(image.getProductId(), false);
                    for (Image relatedImage : relatedImages) {
                        relatedImage.setAlbumId(albumId);
                        relatedImage.setAlbumName(albumName);
                        relatedImage.setUpdatedAt(LocalDateTime.now(BEIJING_ZONE));
                        imageRepository.save(relatedImage);
                    }
                    log.info("同时移动了 {} 张关联的详情图", relatedImages.size());
                }
                
                // 更新旧相册图片数量
                if (oldAlbumId != null && !oldAlbumId.equals(albumId)) {
                    updateAlbumImageCount(oldAlbumId);
                }
            });
        }
        
        // 更新新相册图片数量
        updateAlbumImageCount(albumId);
    }
    
    @Override
    public PageResponse<Image> getFavorites(Integer page, Integer pageSize) {
        log.info("获取收藏图片列表（只返回主图）");
        
        // 尝试使用动态表查询
        String currentUserId = SessionUtil.getCurrentUserId();
        if (currentUserId != null) {
            try {
                String currentUsername = getCurrentUsernameForTable();
                if (currentUsername == null) currentUsername = currentUserId;
                ImageQueryRequest request = new ImageQueryRequest();
                request.setFavorite(true);
                request.setPage(page);
                request.setPageSize(pageSize);
                // 不限制 onlyMainImage，动态表中所有收藏的图片都应显示
                request.setUserId(currentUserId);
                return imageDynamicRepository.queryFavorites(request, currentUsername);
            } catch (Exception e) {
                log.warn("动态表查询收藏夹失败，降级到JPA: {}", e.getMessage());
            }
        }
        
        Pageable pageable = PageRequest.of(page - 1, pageSize, 
                Sort.by("updatedAt").descending());
        
        Page<Image> imagePage = imageRepository.findByFavoriteTrueAndDeletedFalseAndIsMainImageTrue(pageable);
        
        return PageResponse.of(
                imagePage.getContent(),
                imagePage.getTotalElements(),
                imagePage.getNumber() + 1,
                imagePage.getSize()
        );
    }
    
    @Override
    public PageResponse<Image> getTrash(Integer page, Integer pageSize, String keyword) {
        log.info("获取回收站图片列表, keyword={}", keyword);
        
        // 尝试使用动态表查询
        String currentUserId = SessionUtil.getCurrentUserId();
        if (currentUserId != null) {
            try {
                String currentUsername = getCurrentUsernameForTable();
                if (currentUsername == null) currentUsername = currentUserId;
                ImageQueryRequest request = new ImageQueryRequest();
                request.setDeleted(true);
                request.setIncludeDeleted(true);
                request.setPage(page);
                request.setPageSize(pageSize);
                if (keyword != null && !keyword.trim().isEmpty()) {
                    request.setKeyword(keyword.trim());
                }
                // 不限制 onlyMainImage，动态表中所有删除的图片都应显示
                request.setUserId(currentUserId);
                return imageDynamicRepository.queryTrash(request, currentUsername);
            } catch (Exception e) {
                log.warn("动态表查询回收站失败，降级到JPA: {}", e.getMessage());
            }
        }
        
        Pageable pageable = PageRequest.of(page - 1, pageSize, 
                Sort.by("deletedAt").descending());
        
        Page<Image> imagePage = imageRepository.findByDeletedTrueAndIsMainImageTrue(pageable);
        
        return PageResponse.of(
                imagePage.getContent(),
                imagePage.getTotalElements(),
                imagePage.getNumber() + 1,
                imagePage.getSize()
        );
    }
    
    @Override
    public PageResponse<Image> getRecent(Integer page, Integer pageSize, String keyword) {
        log.info("获取最近上传图片列表, keyword={}", keyword);
        
        // 尝试使用动态表查询
        String currentUserId = SessionUtil.getCurrentUserId();
        if (currentUserId != null) {
            try {
                String currentUsername = getCurrentUsernameForTable();
                if (currentUsername == null) currentUsername = currentUserId;
                ImageQueryRequest request = new ImageQueryRequest();
                request.setPage(page);
                request.setPageSize(pageSize);
                if (keyword != null && !keyword.trim().isEmpty()) {
                    request.setKeyword(keyword.trim());
                }
                // 不限制 onlyMainImage，动态表中所有最近图片都应显示
                request.setSortBy("created_at");
                request.setSortOrder("desc");
                request.setUserId(currentUserId);
                return imageDynamicRepository.queryMyImages(request, currentUsername);
            } catch (Exception e) {
                log.warn("动态表查询最近图片失败，降级到JPA: {}", e.getMessage());
            }
        }
        
        // 计算7天前的日期
        LocalDateTime sevenDaysAgo = LocalDateTime.now(BEIJING_ZONE).minusDays(7);
        
        Pageable pageable = PageRequest.of(page - 1, pageSize, 
                Sort.by("createdAt").descending());
        
        Page<Image> imagePage = imageRepository.findByCreatedAtAfterAndDeletedFalseAndIsMainImageTrue(sevenDaysAgo, pageable);
        
        return PageResponse.of(
                imagePage.getContent(),
                imagePage.getTotalElements(),
                imagePage.getNumber() + 1,
                imagePage.getSize()
        );
    }
    
    @Override
    public long getTrashCount() {
        log.info("获取回收站图片数量");
        // 优先从动态表查询
        String currentUserId = SessionUtil.getCurrentUserId();
        if (currentUserId != null) {
            try {
                String currentUsername = getCurrentUsernameForTable();
                if (currentUsername == null) currentUsername = currentUserId;
                return imageDynamicRepository.countDeleted(currentUsername);
            } catch (Exception e) {
                log.warn("动态表查询回收站数量失败，降级到JPA: {}", e.getMessage());
            }
        }
        return imageRepository.countByDeletedTrueAndIsMainImageTrue();
    }
    
    @Override
    public int clearTrash() {
        log.info("清空回收站");
        int totalDeleted = 0;
        
        // 获取所有回收站中的主图
        List<Image> deletedMainImages = imageRepository.findByDeletedTrueAndIsMainImageTrueList();
        
        for (Image mainImage : deletedMainImages) {
            // 连带删除详情图
            if (mainImage.getProductId() != null) {
                List<Image> relatedImages = imageRepository.findByProductIdAndDeleted(mainImage.getProductId(), true);
                for (Image relatedImage : relatedImages) {
                    // 从存储中删除文件
                    deleteImageFile(relatedImage);
                    // 从动态表中硬删除
                    hardDeleteFromDynamicTable(relatedImage);
                    imageRepository.delete(relatedImage);
                    totalDeleted++;
                    log.debug("清空回收站 - 永久删除详情图：{}", relatedImage.getId());
                }
            }
            
            // 从存储中删除主图文件
            deleteImageFile(mainImage);
            // 从动态表中硬删除
            hardDeleteFromDynamicTable(mainImage);
            imageRepository.delete(mainImage);
            totalDeleted++;
        }
        
        log.info("清空回收站完成，共删除 {} 张图片（主图 + 详情图）", totalDeleted);
        return totalDeleted;
    }
    
    @Override
    public List<Image> batchUploadImages(List<MultipartFile> files) {
        log.info("批量上传图片，数量：{}", files.size());
        
        List<Image> uploadedImages = new ArrayList<>();
        List<Album> albums = albumService.getAllAlbums();
        
        for (MultipartFile file : files) {
            try {
                Image image = uploadSingleImageWithAI(file, albums);
                if (image != null) {
                    uploadedImages.add(image);
                }
            } catch (Exception e) {
                log.error("上传图片失败: {}, 错误: {}", file.getOriginalFilename(), e.getMessage());
            }
        }
        
        // 更新所有相关相册的图片数量
        Set<String> affectedAlbumIds = uploadedImages.stream()
                .map(Image::getAlbumId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        
        for (String albumId : affectedAlbumIds) {
            updateAlbumImageCount(albumId);
        }
        
        log.info("批量上传完成，成功：{} 张", uploadedImages.size());
        return uploadedImages;
    }
    
    /**
     * 上传单张图片并使用AI分析
     */
    private Image uploadSingleImageWithAI(MultipartFile file, List<Album> albums) {
        try {
            String originalFilename = file.getOriginalFilename();
            String imageUrl;
            String fileKey = null;
            
            // 使用存储服务上传文件
            if (fileStorageService != null) {
                imageUrl = fileStorageService.uploadFile(file, "images");
                fileKey = fileStorageService.getStorageKey(imageUrl);
            } else {
                // 本地存储（开发模式）
                imageUrl = "/uploads/" + UUID.randomUUID() + getFileExtension(originalFilename);
            }
            
            // 将图片转换为Base64用于AI分析
            String imageBase64 = Base64.getEncoder().encodeToString(file.getBytes());
            
            // 使用AI服务分析图片
            AIRecognitionService.AIRecognitionResult result = aiRecognitionService.analyzeImageWithAI(
                    imageBase64, originalFilename, albums);
            
            String albumId = result.getAlbumId();
            String albumName = result.getAlbumName();
            List<String> tags = result.getTags();
            String classifyMethod = result.getMethod();
            
            // 如果有相册ID但没有相册名称，查找相册名称
            if (albumId != null && albumName == null) {
                albumName = albumRepository.findById(albumId)
                        .map(Album::getName)
                        .orElse(null);
            }
            
            // 获取当前用户ID（数据隔离）
            String currentUserId = SessionUtil.getCurrentUserId();
            if (currentUserId == null) {
                currentUserId = "user-1"; // 降级默认
            }
            
            // 创建图片记录
            Image image = Image.builder()
                    .id(UUID.randomUUID().toString())
                    .title(removeFileExtension(originalFilename))
                    .originalName(originalFilename)  // 保存原始文件名
                    .url(imageUrl)
                    .thumbnailUrl(imageUrl)
                    .fileKey(fileKey)
                    .size(file.getSize())
                    .sizeFormatted(formatFileSize(file.getSize()))
                    .fileType(getFileType(file.getContentType()))
                    .albumId(albumId)
                    .albumName(albumName)
                    .aiTags(tags != null ? new java.util.ArrayList<>(tags) : new java.util.ArrayList<>())
                    .aiTags(tags) // AI识别的标签
                    .aiConfidence(result.getConfidence())
                    .classifyMethod(classifyMethod)
                    .favorite(false)
                    .createdAt(LocalDateTime.now(BEIJING_ZONE))
                    .updatedAt(LocalDateTime.now(BEIJING_ZONE))
                    .userId(currentUserId)  // 使用当前用户ID
                    .company(SessionUtil.getCurrentCompany())  // 按公司隔离
                    .deleted(false)
                    .viewCount(0)
                    .downloadCount(0)
                    .originalUrl(imageUrl)
                    .build();
            
            image = imageRepository.save(image);
            
            log.info("图片上传成功: {}, 分类: {}, 方法: {}", 
                    originalFilename, albumName, classifyMethod);
            
            return image;
            
        } catch (Exception e) {
            log.error("上传图片失败", e);
            throw new RuntimeException("上传图片失败：" + e.getMessage());
        }
    }
    
    /**
     * 更新相册图片数量（只统计主图，即商品数量）
     */
    private void updateAlbumImageCount(String albumId) {
        albumRepository.findById(albumId).ifPresent(album -> {
            // 只统计主图数量（商品数量）
            long count = imageRepository.countMainImagesByAlbumId(albumId);
            album.setImageCount((int) count);
            album.setUpdatedAt(LocalDateTime.now(BEIJING_ZONE));
            albumRepository.save(album);
            log.debug("更新相册图片数量: albumId={}, count={} (主图数量)", albumId, count);
        });
    }

    /**
     * 根据相册名称匹配已有相册
     *
     * @param albumName 相册名称
     * @return 匹配的相册，如果没有匹配到则返回 null
     */
    private Album findOrMatchAlbum(String albumName) {
        if (albumName == null || albumName.trim().isEmpty()) {
            log.warn("相册名称为空，无法匹配");
            return null;
        }

        log.info("尝试匹配已有相册: {}", albumName);

        // 1. 精确匹配相册名称
        Album exactMatch = albumRepository.findByName(albumName).orElse(null);
        if (exactMatch != null) {
            log.info("精确匹配到相册: ID={}, 名称={}", exactMatch.getId(), exactMatch.getName());
            return exactMatch;
        }

        // 2. 模糊匹配：检查相册名称是否包含关键词
        List<Album> allAlbums = albumRepository.findAll();
        for (Album album : allAlbums) {
            if (album.getName() != null && album.getName().contains(albumName)) {
                log.info("模糊匹配到相册: ID={}, 原名称={}, 匹配名称={}", album.getId(), album.getName(), albumName);
                return album;
            }
            // 检查相册名称是否被关键词包含
            if (albumName.contains(album.getName())) {
                log.info("反向模糊匹配到相册: ID={}, 原名称={}, 匹配名称={}", album.getId(), album.getName(), albumName);
                return album;
            }
        }

        log.warn("未找到匹配的相册: {}", albumName);
        return null;
    }

    /**
     * 获取下一个相册排序号
     *
     * @return 下一个排序号
     */
    private int getNextAlbumSortOrder() {
        Integer maxSortOrder = albumRepository.findAll().stream()
                .map(Album::getSortOrder)
                .max(Integer::compareTo)
                .orElse(0);
        return maxSortOrder + 1;
    }

    /**
     * 格式化文件大小
     */
    private String formatFileSize(Long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
        }
    }
    
    /**
     * 从文件名中解析层级目录结构
     * 只保留两级目录：品牌/产品类别
     * 支持的格式：
     * - "松野湃/速干T恤/蓝色款.jpg" -> "松野湃/速干T恤"（只取前两级）
     * - "松野湃-速干T恤.jpg" -> "松野湃/速干T恤"
     * - "松野湃_速干T恤.jpg" -> "松野湃/速干T恤"
     * - "松野湃.速干T恤.jpg" -> "松野湃/速干T恤"
     * 
     * @param filename 文件名
     * @return 层级路径，如果不符合规则返回 null
     */
    private String parseHierarchyFromFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return null;
        }
        
        // 尝试检测并转换 GB2312/GBK/GB18030 编码的中文字符
        String convertedFilename = CharsetUtil.convertToUtf8(filename);
        if (!convertedFilename.equals(filename)) {
            log.info("文件名编码转换: {} -> {}", filename, convertedFilename);
            filename = convertedFilename;
        }
        
        // 移除文件扩展名
        String nameWithoutExt = removeFileExtension(filename);
        
        // 统一使用 "/" 作为分隔符，便于后续处理
        String normalizedName = nameWithoutExt;
        
        // 1. 首先检查是否包含斜杠分隔符
        if (normalizedName.contains("/")) {
            String[] parts = normalizedName.split("/");
            // 过滤掉空的部分
            List<String> validParts = new ArrayList<>();
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty() && trimmed.length() >= 1) {
                    validParts.add(trimmed);
                }
            }
            
            // 只取前两级：品牌/产品类别
            if (validParts.size() >= 2) {
                String brand = validParts.get(0);
                String category = validParts.get(1);
                
                // 构建两级路径
                String path = brand + "/" + category;
                log.info("从文件名中解析出层级路径（两级）: {}", path);
                return path;
            }
        }
        
        // 2. 尝试不同的分隔符（-, _, .）
        String[] separators = {"-", "_", "."};
        
        for (String separator : separators) {
            // 检查是否包含分隔符
            if (nameWithoutExt.contains(separator)) {
                // 分割检查是否有层级结构
                String[] parts = nameWithoutExt.split("[" + Pattern.quote(separator) + "]+");
                
                // 如果有多个部分，且第一部分是品牌/大类，第二部分是子类
                if (parts.length >= 2) {
                    String firstPart = parts[0].trim();
                    String secondPart = parts[1].trim();
                    
                    // 第一部分不能太短（至少2个字符）
                    // 第二部分不能包含常见的后缀
                    if (firstPart.length() >= 2 && 
                        !secondPart.toLowerCase().contains("copy") &&
                        !secondPart.toLowerCase().contains("备份") &&
                        !secondPart.toLowerCase().contains("backup") &&
                        !secondPart.matches("^\\d+$")) { // 第二部分不应该主要是数字
                        
                        // 构建两级层级路径
                        return firstPart + "/" + secondPart;
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".jpg";
        }
        return filename.substring(filename.lastIndexOf("."));
    }
    
    /**
     * 移除文件扩展名
     */
    private String removeFileExtension(String filename) {
        if (filename == null) {
            return "未命名";
        }
        int lastDot = filename.lastIndexOf(".");
        return lastDot > 0 ? filename.substring(0, lastDot) : filename;
    }
    
    /**
     * 根据MIME类型获取文件类型
     */
    private String getFileType(String mimeType) {
        if (mimeType == null) return "jpg";
        if (mimeType.contains("png")) return "png";
        if (mimeType.contains("gif")) return "gif";
        if (mimeType.contains("webp")) return "webp";
        return "jpg";
    }
    
    @Override
    public List<com.imagemanager.dto.BatchDownloadResponse> batchDownloadImages(
            com.imagemanager.dto.BatchDownloadRequest request) {
        log.info("批量下载网络图片，数量：{}", request.getImages().size());
        
        List<com.imagemanager.dto.BatchDownloadResponse> results = new ArrayList<>();
        List<Album> albums = albumService.getAllAlbums();
        
        for (com.imagemanager.dto.BatchDownloadRequest.ImageToDownload item : request.getImages()) {
            // 验证商品名称
            if (item.getProductName() == null || item.getProductName().trim().isEmpty()) {
                log.warn("商品名称为空，跳过");
                com.imagemanager.dto.BatchDownloadResponse emptyResponse = new com.imagemanager.dto.BatchDownloadResponse();
                emptyResponse.setSuccess(false);
                emptyResponse.setSkipped(false);
                emptyResponse.setError("商品名称不能为空");
                results.add(emptyResponse);
                continue;
            }

            // 检查商品名称是否已存在（避免重复导入）
            Optional<Product> existingProduct = productRepository.findByName(item.getProductName().trim());
            if (existingProduct.isPresent()) {
                // 检查该商品是否还有有效图片（未删除的）
                List<Image> existingImages = imageRepository.findByProductIdAndDeleted(existingProduct.get().getId(), false);
                if (!existingImages.isEmpty()) {
                    // 商品存在且有有效图片，跳过导入
                    log.info("商品 [{}] 已存在且有有效图片，跳过导入", item.getProductName());
                    com.imagemanager.dto.BatchDownloadResponse skipResponse = new com.imagemanager.dto.BatchDownloadResponse();
                    skipResponse.setSuccess(false);
                    skipResponse.setSkipped(true);
                    skipResponse.setError("商品已存在，跳过导入");
                    // 主图URL
                    if (item.getMainImageUrl() != null && !item.getMainImageUrl().trim().isEmpty()) {
                        skipResponse.setOriginalUrl(item.getMainImageUrl());
                        results.add(skipResponse);
                    }
                    // 详情图URL
                    if (item.getDetailImageUrls() != null) {
                        for (String detailUrl : item.getDetailImageUrls()) {
                            if (detailUrl != null && !detailUrl.trim().isEmpty()) {
                                com.imagemanager.dto.BatchDownloadResponse detailSkip = new com.imagemanager.dto.BatchDownloadResponse();
                                detailSkip.setSuccess(false);
                                detailSkip.setSkipped(true);
                                detailSkip.setError("商品已存在，跳过导入");
                                detailSkip.setOriginalUrl(detailUrl);
                                results.add(detailSkip);
                            }
                        }
                    }
                    continue;
                }
                // 商品存在但所有图片都已删除（包括永久删除的），复用该商品记录重新导入图片
                log.info("商品 [{}] 已存在但图片已全部删除，将重新导入图片", item.getProductName());
            }

            // 生成商品ID（用于关联主图和详情图）
            String productId = existingProduct.map(Product::getId).orElse("product-" + UUID.randomUUID().toString().substring(0, 8));

            // 获取父相册名称（来自 Excel 文件名）
            String parentAlbumName = request.getParentAlbumName();

            // 处理父相册名称
            String cleanParentName = null;
            if (parentAlbumName != null && !parentAlbumName.isEmpty()) {
                // 移除文件名中的扩展名（如 .xlsx）
                cleanParentName = parentAlbumName;
                int dotIndex = cleanParentName.lastIndexOf('.');
                if (dotIndex > 0) {
                    cleanParentName = cleanParentName.substring(0, dotIndex);
                }
                // 移除可能的 assets/ 或 assets\ 前缀
                if (cleanParentName.startsWith("assets/") || cleanParentName.startsWith("assets\\")) {
                    cleanParentName = cleanParentName.substring(7);
                }
                // 处理父相册名称的 URL 编码
                cleanParentName = CharsetUtil.convertToUtf8(cleanParentName);
            }

            // 如果指定了分类，查找或创建对应的相册（支持层级目录）
            String albumId = null;
            String albumName = null;
            String category = item.getCategory();
            String subCategory = item.getSubCategory();

            // 只要有 category 或 subCategory 其中一个，就创建相册
            if ((category != null && !category.isEmpty()) || (subCategory != null && !subCategory.isEmpty())) {
                // 尝试解析 URL 编码的中文字符（如 %CC%F9%C9%ED 格式）
                String decodedCategory = category != null ? CharsetUtil.convertToUtf8(category.trim()) : null;
                log.info("Excel导入 - 原始分类: '{}', 解码后: '{}'", category, decodedCategory);
                log.info("Excel导入 - 原始子分类: '{}'", subCategory);
                log.info("Excel导入 - 处理分类: {}, 父相册: {}", decodedCategory, parentAlbumName);

                // 使用新的方法：支持三级相册层级
                // 分类格式: 羽绒服_女士专区_
                // 第一层: 文件名 (X-BIONIC)
                // 第二层: subCategory (女士专区) 或 category（只有单层时）
                // 第三层: category (羽绒服)
                try {
                    String secondLevelName = null; // 第二层级名称
                    String thirdLevelName = null;  // 第三层级名称
                    Album targetAlbum = null;

                    // 统一处理分类层级逻辑
                    if (decodedCategory != null && !decodedCategory.isEmpty()) {
                        // 有第三层分类
                        if (subCategory != null && !subCategory.isEmpty()) {
                            // 三级分类：X-BIONIC -> subCategory -> category
                            secondLevelName = subCategory;
                            thirdLevelName = decodedCategory;
                        } else {
                            // 只有一层分类：X-BIONIC -> category（把category作为第二层）
                            secondLevelName = decodedCategory;
                            thirdLevelName = null;
                        }
                    } else if (subCategory != null && !subCategory.isEmpty()) {
                        // 只有subCategory没有category：X-BIONIC -> subCategory
                        secondLevelName = subCategory;
                        thirdLevelName = null;
                    }

                    // 根据层级名称创建相册
                    if (secondLevelName != null && !secondLevelName.isEmpty()) {
                        if (thirdLevelName != null && !thirdLevelName.isEmpty()) {
                            // 三级层级：X-BIONIC -> secondLevelName -> thirdLevelName
                            Album secondLevelAlbum = albumService.getOrCreateAlbumByParentAndName(
                                    cleanParentName != null ? cleanParentName : "",
                                    secondLevelName,
                                    "user-1"
                            );
                            // 使用父相册ID避免名称歧义
                            targetAlbum = albumService.getOrCreateAlbumByParentIdAndName(
                                    secondLevelAlbum.getId(),
                                    thirdLevelName,
                                    "user-1"
                            );
                            log.info("Excel导入 - 三级相册: 第一层={}, 第二层={}, 第三层={}",
                                    cleanParentName, secondLevelName, thirdLevelName);
                        } else {
                            // 两级层级：X-BIONIC -> secondLevelName（只有一层子分类）
                            targetAlbum = albumService.getOrCreateAlbumByParentAndName(
                                    cleanParentName != null ? cleanParentName : "",
                                    secondLevelName,
                                    "user-1"
                            );
                            log.info("Excel导入 - 两级相册: 第一层={}, 第二层={}", cleanParentName, secondLevelName);
                        }
                    }
                    
                    if (targetAlbum != null) {
                        albumId = targetAlbum.getId();
                        albumName = targetAlbum.getFullName() != null ? targetAlbum.getFullName() : targetAlbum.getName();
                        // 刷新相册列表
                        albums = albumService.getAllAlbums();
                        log.info("Excel导入 - 获取/创建相册成功: ID={}, 名称={}", albumId, albumName);
                    }
                } catch (Exception e) {
                    log.error("Excel导入 - 获取/创建相册失败: {}", e.getMessage(), e);
                }
            }

            // 如果没有指定分类但有父相册名称，创建父相册（作为根相册）
            if (albumId == null && parentAlbumName != null && !parentAlbumName.isEmpty()) {
                try {
                    Album parentAlbum = albumService.getOrCreateAlbumByPath(cleanParentName);
                    if (parentAlbum != null) {
                        albumId = parentAlbum.getId();
                        albumName = parentAlbum.getFullName() != null ? parentAlbum.getFullName() : parentAlbum.getName();
                        albums = albumService.getAllAlbums();
                        log.info("Excel导入 - 创建/获取父相册: ID={}, 名称={}", albumId, albumName);
                    }
                } catch (Exception e) {
                    log.warn("Excel导入 - 创建父相册失败: {}", e.getMessage());
                }
            }

            // 获取或创建商品记录
            Product product = existingProduct.orElse(null);
            // 处理商品名称的 URL 编码（提前定义，供后续使用）
            String productName = CharsetUtil.convertToUtf8(item.getProductName());
            if (product == null) {
                // 商品不存在，创建新记录
                product = new Product();
                product.setId(productId);
                product.setName(productName);
                product.setDescription(item.getDescription() != null ? CharsetUtil.convertToUtf8(item.getDescription()) : null);
                product.setCategory(item.getCategory() != null ? CharsetUtil.convertToUtf8(item.getCategory()) : null);
                product.setAlbumId(albumId);
                product.setUserId("user-1"); // 默认用户
                product.setImageCount(0);
                product = productRepository.save(product);
                log.info("创建商品记录: ID={}, 名称={}, 分类={}", productId, productName, item.getCategory());
            } else {
                // 商品存在但图片已删除，更新分类等信息
                product.setDescription(item.getDescription() != null ? CharsetUtil.convertToUtf8(item.getDescription()) : null);
                product.setCategory(item.getCategory() != null ? CharsetUtil.convertToUtf8(item.getCategory()) : null);
                product.setAlbumId(albumId);
                product.setImageCount(0);
                product = productRepository.save(product);
                log.info("复用商品记录重新导入图片: ID={}, 名称={}", product.getId(), productName);
            }

            // 合并所有需要下载的URL（主图 + 详情图），并去重
            List<String> allUrls = new ArrayList<>();
            Set<String> urlSet = new java.util.LinkedHashSet<>(); // 使用 LinkedHashSet 保持顺序并去重
            
            // 辅助方法：规范化URL，添加协议头并优化获取高质量图片
            java.util.function.Function<String, String> normalizeUrl = url -> {
                String normalized = url.trim();
                // 如果URL以 // 开头，添加 https:
                if (normalized.startsWith("//")) {
                    normalized = "https:" + normalized;
                }
                
                // 京东图片优化：移除尺寸限制后缀，获取原始高质量图片
                // 京东图片URL格式: /n7/jfs/.../xxx.jpg!cc_200x200.png
                // 尺寸标识: !cc_xxx, !mq_xxx, !cr_xxx 等
                // 移除这些后缀后可以获取更大尺寸的图片
                if (normalized.contains("360buyimg.com") || normalized.contains("jd.com")) {
                    // 将京东图片的 n5 尺寸替换为 n8（更大尺寸）
                    normalized = normalized.replace("/n5/", "/n8/");
                    
                    // 移除京东图片的尺寸后缀 (!cc_200x200.png, !mq130.jpg 等)
                    normalized = normalized.replaceAll("!cc_\\d+x\\d+_pr_asp\\.jpg", ".jpg");
                    normalized = normalized.replaceAll("!cc_\\d+x\\d+_pr_asp\\.png", ".png");
                    normalized = normalized.replaceAll("!cr_\\d+x\\d+_pr_asp\\.jpg", ".jpg");
                    normalized = normalized.replaceAll("!mq\\d+\\.jpg", ".jpg");
                    normalized = normalized.replaceAll("!n\\d+\\.jpg", ".jpg");
                    normalized = normalized.replaceAll("!wq\\d+\\.jpg", ".jpg");
                    normalized = normalized.replaceAll("\\.jpg!\\w+\\.jpg", ".jpg");
                    normalized = normalized.replaceAll("\\.png!\\w+\\.png", ".png");
                }
                
                // 如果是京东的 .avif 格式，尝试获取更高质量的版本
                // 京东的 .avif 图片可以用 .jpg 替代，质量更好
                if (normalized.contains(".avif")) {
                    normalized = normalized.replace(".avif", ".jpg");
                }
                
                return normalized;
            };
            
            if (item.getMainImageUrl() != null && !item.getMainImageUrl().isEmpty() && !item.getMainImageUrl().trim().isEmpty()) {
                urlSet.add(normalizeUrl.apply(item.getMainImageUrl()));
            }
            if (item.getDetailImageUrls() != null && !item.getDetailImageUrls().isEmpty()) {
                // 过滤空URL和无效URL
                item.getDetailImageUrls().stream()
                    .filter(url -> url != null && !url.isEmpty() && !url.trim().isEmpty())
                    .map(normalizeUrl)
                    .forEach(urlSet::add);
            }
            allUrls.addAll(urlSet);

            // 检查是否有有效的URL
            if (allUrls.isEmpty()) {
                log.warn("商品 {} 没有有效的图片URL，跳过", item.getProductName());
                com.imagemanager.dto.BatchDownloadResponse emptyResponse = new com.imagemanager.dto.BatchDownloadResponse();
                emptyResponse.setSuccess(false);
                emptyResponse.setSkipped(false);
                emptyResponse.setError("没有有效的图片URL");
                results.add(emptyResponse);
                continue;
            }

            int totalImages = allUrls.size();
            int successCount = 0;
            int skippedCount = 0; // 统计跳过的图片数量
            String mainImageId = null; // 记录主图ID

            // 下载所有图片
            for (int i = 0; i < allUrls.size(); i++) {
                String imageUrl = allUrls.get(i);
                log.info("开始下载图片 {}/{}: {}", i + 1, totalImages, imageUrl);
                com.imagemanager.dto.BatchDownloadResponse response = new com.imagemanager.dto.BatchDownloadResponse();
                response.setOriginalUrl(imageUrl);
                
                boolean isMainImage = (i == 0); // 第一张是主图
                
                // 从URL提取文件名（用于检查重复）
                String urlFileName = null;
                try {
                    String path = new java.net.URL(imageUrl).getPath();
                    if (path != null && path.contains("/")) {
                        urlFileName = path.substring(path.lastIndexOf("/") + 1);
                    }
                } catch (Exception e) {
                    log.warn("无法从URL提取文件名: {}", imageUrl);
                }
                
                try {
                    // 检查图片URL和文件名是否已存在，避免重复下载
                    // 只有当 originalUrl 和 文件名 都相同时才认为是重复图片
                    boolean isDuplicate = false;
                    if (urlFileName != null && !urlFileName.isEmpty()) {
                        isDuplicate = imageRepository.existsByOriginalUrlAndTitleAndDeletedFalse(imageUrl, urlFileName);
                        if (isDuplicate) {
                            log.info("图片已存在于数据库（URL+文件名匹配），跳过: {}, 文件名: {}", imageUrl, urlFileName);
                            response.setSuccess(true);
                            response.setSkipped(true);
                            response.setError("图片已存在（URL+文件名匹配），跳过");
                        }
                    }
                    // 如果无法提取文件名，回退到只检查URL
                    if (!isDuplicate && (urlFileName == null || urlFileName.isEmpty())) {
                        isDuplicate = imageRepository.existsByOriginalUrlAndDeletedFalse(imageUrl);
                        if (isDuplicate) {
                            log.info("图片URL已存在于数据库，跳过: {}", imageUrl);
                            response.setSuccess(true);
                            response.setSkipped(true);
                            response.setError("图片已存在于数据库，跳过");
                        }
                    }
                    
                    if (isDuplicate) {
                        successCount++;
                        skippedCount++;
                        results.add(response);
                        continue;
                    }
                    
                    // 从URL下载图片
                    java.net.URL url = new java.net.URL(imageUrl);
                    java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(30000);

                    int responseCode = connection.getResponseCode();
                    if (responseCode != 200) {
                        log.error("下载图片失败: {}, HTTP状态码: {}", imageUrl, responseCode);
                        response.setSuccess(false);
                        response.setSkipped(false);
                        response.setError("HTTP " + responseCode);
                        results.add(response);
                        continue;
                    }

                    // 从响应头获取Content-Type
                    String contentType = connection.getContentType();
                    log.debug("从响应头获取的Content-Type: {}", contentType);

                    if (contentType == null || contentType.isEmpty()) {
                        // 如果响应头中没有Content-Type，从URL推断
                        String extension = getFileExtensionFromUrl(imageUrl);
                        contentType = getContentType(extension);
                        log.debug("从URL推断的Content-Type: {}", contentType);
                    } else {
                        // 清理Content-Type（移除可能的参数，如charset）
                        if (contentType.contains(";")) {
                            contentType = contentType.split(";")[0].trim();
                        }
                    }

                    // 读取图片数据
                    try (java.io.InputStream inputStream = connection.getInputStream()) {
                        byte[] imageData = inputStream.readAllBytes();

                        log.info("下载图片成功: {}, 大小: {} bytes, Content-Type: {}",
                            imageUrl, imageData.length, contentType);

                        // 验证下载的数据是否为有效的图片
                        if (imageData.length == 0) {
                            log.error("下载的图片数据为空: {}", imageUrl);
                            response.setSuccess(false);
                            response.setSkipped(false);
                            response.setError("下载的图片数据为空");
                            results.add(response);
                            continue;
                        }

                        // 验证图片文件头
                        if (!isValidImageData(imageData)) {
                            log.error("下载的数据不是有效的图片格式: {}, 文件头: {}",
                                imageUrl, bytesToHex(imageData, 0, Math.min(16, imageData.length)));
                            response.setSuccess(false);
                            response.setSkipped(false);
                            response.setError("下载的数据不是有效的图片格式");
                            results.add(response);
                            continue;
                        }

                        // 创建文件名：商品名称 + (序号) + 扩展名
                        String extension = getFileExtensionFromUrl(imageUrl);
                        String fileName = sanitizeFileName(productName);
                        if (totalImages > 1) {
                            fileName += (isMainImage ? "" : "_" + i);
                        }
                        fileName += extension;

                        // 图片增强处理（如果启用）
                        if (enableImageEnhance && imageData.length > 0) {
                            try {
                                java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(imageData);
                                BufferedImage originalImage = javax.imageio.ImageIO.read(bais);
                                if (originalImage != null) {
                                    BufferedImage enhancedImage = imageEnhancementService.enhance(originalImage, enableSuperResolution);
                                    if (enhancedImage != null) {
                                        imageData = imageEnhancementService.toByteArray(enhancedImage, "jpg");
                                        log.info("图片增强完成, 原尺寸: {}x{}, 新尺寸: {}x{}, 大小: {} bytes",
                                                originalImage.getWidth(), originalImage.getHeight(),
                                                enhancedImage.getWidth(), enhancedImage.getHeight(),
                                                imageData.length);
                                    }
                                }
                            } catch (Exception e) {
                                log.warn("图片增强失败，使用原图: {}", e.getMessage());
                            }
                        }

                        // 创建MultipartFile
                        com.imagemanager.util.ByteArrayMultipartFile file =
                            new com.imagemanager.util.ByteArrayMultipartFile(
                                imageData,      // 第1个参数：byte[] content - 文件字节内容
                                "file",         // 第2个参数：String name - 表单字段名
                                fileName,       // 第3个参数：String originalFilename - 文件原始名称
                                contentType     // 第4个参数：String contentType - MIME类型
                            );

                        // 保存图片
                        Image image = uploadSingleImageWithAI(file, albums, fileName, albumId, null, imageUrl);
                        
                        // 更新商品相关信息
                        if (image != null) {
                            image.setTitle(productName);
                            image.setProductId(productId);
                            image.setIsMainImage(isMainImage);
                            image.setDisplayOrder(isMainImage ? 0 : i); // 主图在前，详情图按顺序
                            if (item.getDescription() != null) {
                                image.setDescription(item.getDescription());
                            }
                            image = imageRepository.save(image);
                            
                            log.info("保存图片成功: id={}, title={}, productId={}, isMainImage={}", 
                                image.getId(), image.getTitle(), image.getProductId(), image.getIsMainImage());

                            successCount++;
                            response.setSuccess(true);
                            response.setImageId(image.getId());

                            // 记录主图ID
                            if (isMainImage) {
                                mainImageId = image.getId();
                            }
                        } else {
                            response.setSuccess(false);
                            response.setSkipped(false);
                            response.setError("上传失败");
                        }
                    }
                } catch (Exception e) {
                    log.error("下载图片失败: {}, 错误: {}", imageUrl, e.getMessage());
                    response.setSuccess(false);
                    response.setSkipped(false);
                    response.setError(e.getMessage());
                }
                
                results.add(response);
            }

            // 更新商品的图片数量和封面图
            if (successCount > 0) {
                product.setImageCount(successCount);
                product.setCoverImageId(mainImageId); // 直接使用记录的主图ID

                product = productRepository.save(product);
                log.info("更新商品记录: ID={}, 图片数量={}, 封面图ID={}",
                    productId, product.getImageCount(), product.getCoverImageId());
            } else {
                // 如果没有下载成功任何图片，删除商品记录
                productRepository.delete(product);
                log.warn("删除无效商品记录: ID={}, 原因: 没有下载成功任何图片", productId);
            }

            log.info("商品 {} 下载完成：成功 {}/{}，跳过 {}", 
                item.getProductName(), successCount, totalImages, skippedCount);
        }
        
        // 更新受影响的相册图片数量
        Set<String> affectedAlbumIds = new HashSet<>();
        for (com.imagemanager.dto.BatchDownloadResponse resp : results) {
            if (resp.isSuccess() && resp.getImageId() != null) {
                imageRepository.findById(resp.getImageId()).ifPresent(img -> {
                    if (img.getAlbumId() != null) {
                        affectedAlbumIds.add(img.getAlbumId());
                    }
                });
            }
        }
        
        for (String albumId : affectedAlbumIds) {
            updateAlbumImageCount(albumId);
        }
        
        long successCountTotal = results.stream().filter(r -> Boolean.TRUE.equals(r.isSuccess()) && !Boolean.TRUE.equals(r.isSkipped())).count();
        long skippedCount = results.stream().filter(r -> Boolean.TRUE.equals(r.isSkipped())).count();
        long failedCount = results.stream().filter(r -> !Boolean.TRUE.equals(r.isSuccess())).count();
        log.info("批量下载完成，成功：{} 张，跳过（已存在）：{} 张，失败：{} 张", 
            successCountTotal, skippedCount, failedCount);
        
        // 如果有失败，打印失败原因
        if (failedCount > 0) {
            for (var resp : results) {
                if (!resp.isSuccess() && !resp.isSkipped()) {
                    log.warn("下载失败 - URL: {}, 错误: {}", resp.getOriginalUrl(), resp.getError());
                }
            }
        }
        
        return results;
    }

    /**
     * 批量下载网络图片（同步版本，用于异步任务调用）
     */
    @Override
    public List<com.imagemanager.dto.BatchDownloadResponse> batchDownloadImagesSync(
            com.imagemanager.dto.BatchDownloadRequest request) {
        // 直接调用原有逻辑
        return batchDownloadImages(request);
    }

    /**
     * 上传单张图片并使用AI分析（带自定义参数）
     */
    private Image uploadSingleImageWithAI(MultipartFile file, List<Album> albums, 
            String originalFilename, String albumId, List<String> tags, String originalUrl) {
        try {
            String imageUrl;
            String fileKey = null;
            
            // 使用存储服务上传文件
            if (fileStorageService != null) {
                imageUrl = fileStorageService.uploadFile(file, "images");
                fileKey = fileStorageService.getStorageKey(imageUrl);
            } else {
                // 本地存储（开发模式）
                imageUrl = "/uploads/" + UUID.randomUUID() + getFileExtension(originalFilename);
            }
            
            // 自动分类
            String finalAlbumId = albumId;
            String finalAlbumName = null;
            List<String> finalTags = tags;
            String classifyMethod = "user";

            // 1. 首先检查文件名是否包含层级目录（如 "松野湃-速干T恤"）
            if (finalAlbumId == null && originalFilename != null) {
                String pathFromFilename = parseHierarchyFromFilename(originalFilename);
                if (pathFromFilename != null) {
                    log.info("批量上传 - 从文件名中解析出层级路径: {}", pathFromFilename);
                    try {
                        Album hierarchyAlbum = albumService.getOrCreateAlbumByPath(pathFromFilename);
                        if (hierarchyAlbum != null) {
                            finalAlbumId = hierarchyAlbum.getId();
                            finalAlbumName = hierarchyAlbum.getFullName();
                            classifyMethod = "filename-hierarchy";
                            log.info("批量上传 - 根据文件名自动创建/获取层级相册: ID={}, 名称={}", finalAlbumId, finalAlbumName);
                        }
                    } catch (Exception e) {
                        log.warn("批量上传 - 根据文件名创建层级相册失败: {}", e.getMessage());
                    }
                }
            }

            // 2. 如果没有从文件名中提取到目录，使用 AI 服务分析
            if (finalAlbumId == null) {
                if (albumId == null || tags == null || tags.isEmpty()) {
                    // 使用AI服务分析图片
                    AIRecognitionService.AIRecognitionResult result = aiRecognitionService.analyzeImage(
                            imageUrl, originalFilename, albums);

                    if (albumId == null && result.getAlbumId() != null) {
                        finalAlbumId = result.getAlbumId();
                        finalAlbumName = result.getAlbumName();
                    } else if (albumId == null && result.shouldCreateNewAlbum()) {
                        // 如果没有匹配到相册，尝试根据名称匹配已有相册
                        log.info("批量上传 - 尝试根据名称匹配已有相册: {}", result.getSuggestedAlbumName());
                        Album matchedAlbum = findOrMatchAlbum(result.getSuggestedAlbumName());
                        if (matchedAlbum != null) {
                            finalAlbumId = matchedAlbum.getId();
                            finalAlbumName = matchedAlbum.getName();
                            classifyMethod = "auto-matched";
                            log.info("批量上传 - 成功匹配到已有相册: ID={}, 名称={}", finalAlbumId, finalAlbumName);
                        } else {
                            log.warn("批量上传 - 未找到匹配的相册，跳过相册分配: {}", result.getSuggestedAlbumName());
                            // 不创建新相册，也不分配相册
                            classifyMethod = "unmatched";
                        }
                    }

                    if (tags == null || tags.isEmpty()) {
                        finalTags = result.getTags();
                    }

                    // 如果没有设置分类方法，使用AI结果的方法
                    if ("user".equals(classifyMethod) && result.getMethod() != null) {
                        classifyMethod = result.getMethod();
                    }
                }
            }
            
            // 如果有相册ID但没有相册名称，查找相册名称
            String albumName = finalAlbumName;
            if (finalAlbumId != null && albumName == null) {
                albumName = albumRepository.findById(finalAlbumId)
                        .map(Album::getName)
                        .orElse(null);
            }
            
            // 获取当前用户ID（数据隔离）
            String currentUserId = SessionUtil.getCurrentUserId();
            if (currentUserId == null) {
                currentUserId = "user-1"; // 降级默认
            }
            
            // 创建图片记录
            Image image = Image.builder()
                    .id(UUID.randomUUID().toString())
                    .title(removeFileExtension(originalFilename))
                    .originalName(originalFilename)
                    .url(imageUrl)
                    .thumbnailUrl(imageUrl)
                    .fileKey(fileKey)
                    .size((long) file.getBytes().length)
                    .sizeFormatted(formatFileSize((long) file.getBytes().length))
                    .fileType(getFileType(file.getContentType()))
                    .albumId(finalAlbumId)
                    .albumName(albumName)
                    .tags(finalTags != null ? new java.util.ArrayList<>(finalTags) : new java.util.ArrayList<>())
                    .classifyMethod(classifyMethod)
                    .favorite(false)
                    .createdAt(LocalDateTime.now(BEIJING_ZONE))
                    .updatedAt(LocalDateTime.now(BEIJING_ZONE))
                    .userId(currentUserId)  // 使用当前用户ID
                    .company(SessionUtil.getCurrentCompany())  // 按公司隔离
                    .deleted(false)
                    .viewCount(0)
                    .downloadCount(0)
                    .originalUrl(originalUrl != null ? originalUrl : imageUrl)
                    .build();
            
            image = imageRepository.save(image);
            
            // 更新相册图片数量
            if (finalAlbumId != null) {
                updateAlbumImageCount(finalAlbumId);
            }
            
            log.info("图片上传成功，自动分类: {}, 标签: {}", albumName, finalTags);
            
            return image;
        } catch (Exception e) {
            log.error("上传图片失败", e);
            throw new RuntimeException("上传图片失败：" + e.getMessage());
        }
    }
    
    /**
     * 从URL获取文件扩展名
     */
    private String getFileExtensionFromUrl(String url) {
        if (url == null) return ".jpg";
        try {
            java.net.URL urlObj = new java.net.URL(url);

            // 先从URL路径中提取扩展名
            String path = urlObj.getPath();
            if (path != null && path.contains(".")) {
                String ext = path.substring(path.lastIndexOf("."));
                // 验证是否为有效的图片扩展名（不区分大小写）
                if (ext.toLowerCase().matches("\\.(jpg|jpeg|png|gif|webp)$")) {
                    return ext;
                }
            }

            // 如果路径中没有找到，尝试从query参数中提取
            String query = urlObj.getQuery();
            if (query != null) {
                // 解析file_path参数
                String[] params = query.split("&");
                for (String param : params) {
                    if (param.startsWith("file_path=")) {
                        String filePath = java.net.URLDecoder.decode(param.substring("file_path=".length()), "UTF-8");
                        if (filePath.contains(".")) {
                            String ext = filePath.substring(filePath.lastIndexOf("."));
                            // 验证是否为有效的图片扩展名（不区分大小写）
                            if (ext.toLowerCase().matches("\\.(jpg|jpeg|png|gif|webp)$")) {
                                return ext;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("无法从URL解析扩展名: {}", url, e);
        }
        return ".jpg";
    }
    
    /**
     * 获取Content Type
     */
    private String getContentType(String extension) {
        if (extension == null) return "image/jpeg";
        String ext = extension.toLowerCase();
        if (ext.equals(".png")) return "image/png";
        if (ext.equals(".gif")) return "image/gif";
        if (ext.equals(".webp")) return "image/webp";
        return "image/jpeg";
    }
    
    /**
     * 清理文件名，移除非法字符
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "untitled";
        }
        // 移除非法字符：\ / : * ? " < > |
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    /**
     * 验证图片数据是否有效
     */
    private boolean isValidImageData(byte[] imageData) {
        if (imageData == null || imageData.length < 8) {
            return false;
        }

        // 检查常见的图片文件头
        // JPEG: FF D8 FF
        if (imageData[0] == (byte) 0xFF && imageData[1] == (byte) 0xD8 && imageData[2] == (byte) 0xFF) {
            return true;
        }

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (imageData[0] == (byte) 0x89 && imageData[1] == 0x50 && imageData[2] == 0x4E &&
            imageData[3] == 0x47 && imageData[4] == 0x0D && imageData[5] == 0x0A &&
            imageData[6] == 0x1A && imageData[7] == 0x0A) {
            return true;
        }

        // GIF: 47 49 46 38 (GIF8)
        if (imageData[0] == 0x47 && imageData[1] == 0x49 && imageData[2] == 0x46 && imageData[3] == 0x38) {
            return true;
        }

        // WebP: RIFF....WEBP
        if (imageData.length >= 12 &&
            imageData[0] == 0x52 && imageData[1] == 0x49 && imageData[2] == 0x46 && imageData[3] == 0x46 &&
            imageData[8] == 0x57 && imageData[9] == 0x45 && imageData[10] == 0x42 && imageData[11] == 0x50) {
            return true;
        }

        // BMP: 42 4D (BM)
        if (imageData[0] == 0x42 && imageData[1] == 0x4D) {
            return true;
        }

        // TIFF: 49 49 2A 00 (小端) 或 4D 4D 00 2A (大端)
        if (imageData.length >= 4 &&
            ((imageData[0] == 0x49 && imageData[1] == 0x49 && imageData[2] == 0x2A && imageData[3] == 0x00) ||
             (imageData[0] == 0x4D && imageData[1] == 0x4D && imageData[2] == 0x00 && imageData[3] == 0x2A))) {
            return true;
        }

        // 如果文件大小足够大（>10KB），也认为是有效的图片
        // 某些图片可能文件头被压缩或特殊处理
        if (imageData.length > 10000) {
            log.debug("图片数据大小超过10KB，放行: {} bytes", imageData.length);
            return true;
        }

        return false;
    }

    /**
     * 将字节数组转换为十六进制字符串（用于调试）
     */
    private String bytesToHex(byte[] bytes, int offset, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length && (offset + i) < bytes.length; i++) {
            sb.append(String.format("%02X ", bytes[offset + i]));
        }
        return sb.toString().trim();
    }

    @Override
    public void exportAlbumImages(String albumId, org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream zos) throws Exception {
        log.info("导出相册图片：{}", albumId);
        
        // 获取相册信息
        Album album = albumService.getAlbumById(albumId);
        String albumName = album != null ? album.getName() : "unknown";
        
        // 递归获取所有子相册ID（包含当前相册）
        List<String> allAlbumIds = new ArrayList<>();
        collectAllAlbumIds(albumId, allAlbumIds);
        
        int totalImages = 0;
        int errorCount = 0;
        
        for (String currentAlbumId : allAlbumIds) {
            Album currentAlbum = albumService.getAlbumById(currentAlbumId);
            String currentAlbumName = currentAlbum != null ? sanitizeFileName(currentAlbum.getName()) : "unknown";
            
            // 获取当前相册下的所有图片
            Page<Image> imagePage = imageRepository.findByAlbumIdAndDeleted(currentAlbumId, false, PageRequest.of(0, 10000));
            List<Image> images = imagePage.getContent();
            
            if (images.isEmpty()) {
                continue;
            }
            
            // 按商品ID分组
            Map<String, List<Image>> productImages = images.stream()
                    .collect(Collectors.groupingBy(img -> img.getProductId() != null ? img.getProductId() : img.getId()));
            
            for (Map.Entry<String, List<Image>> entry : productImages.entrySet()) {
                String productId = entry.getKey();
                List<Image> productImageList = entry.getValue();
                
                // 获取商品名称（使用主图的title）
                String productName = getProductTitle(productImageList, productId);
                
                // 文件夹结构：父相册/子相册/商品名称
                String productFolderName = currentAlbumName + "/" + sanitizeFileName(productName);
                
                // 导出该商品的图片
                ExportResult result = exportProductImages(zos, productImageList, productFolderName);
                totalImages += result.successCount;
                errorCount += result.errorCount;
            }
        }
        
        if (totalImages == 0) {
            throw new RuntimeException("相册及其子相册中没有图片");
        }
        
        log.info("导出完成，共导出 {} 张图片，失败 {} 张", totalImages, errorCount);
    }
    
    @Override
    public void exportMultipleAlbums(List<String> albumIds, org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream zos) throws Exception {
        log.info("批量导出多个相册，数量：{}", albumIds.size());
        
        int totalImages = 0;
        int errorCount = 0;
        
        for (String albumId : albumIds) {
            // 获取相册信息
            Album album = albumService.getAlbumById(albumId);
            String albumName = album != null ? sanitizeFileName(album.getName()) : "unknown";
            
            // 递归获取所有子相册ID（包含当前相册）
            List<String> allSubAlbumIds = new ArrayList<>();
            collectAllAlbumIds(albumId, allSubAlbumIds);
            
            for (String subAlbumId : allSubAlbumIds) {
                Album subAlbum = albumService.getAlbumById(subAlbumId);
                String subAlbumName = subAlbum != null ? sanitizeFileName(subAlbum.getName()) : "unknown";
                
                // 获取相册下的所有图片
                Page<Image> imagePage = imageRepository.findByAlbumIdAndDeleted(subAlbumId, false, PageRequest.of(0, 10000));
                List<Image> images = imagePage.getContent();
                
                if (images.isEmpty()) {
                    continue;
                }
                
                // 按商品ID分组
                Map<String, List<Image>> productImages = images.stream()
                        .collect(Collectors.groupingBy(img -> img.getProductId() != null ? img.getProductId() : img.getId()));
                
                for (Map.Entry<String, List<Image>> entry : productImages.entrySet()) {
                    String productId = entry.getKey();
                    List<Image> productImageList = entry.getValue();
                    
                    // 获取商品名称（使用主图的title）
                    String productName = getProductTitle(productImageList, productId);
                    
                    // 文件夹结构：父相册/子相册/商品名称
                    String productFolderName = albumName + "/" + subAlbumName + "/" + sanitizeFileName(productName);
                    
                    // 导出该商品的图片
                    ExportResult result = exportProductImages(zos, productImageList, productFolderName);
                    totalImages += result.successCount;
                    errorCount += result.errorCount;
                }
            }
        }
        
        log.info("批量导出完成，共导出 {} 张图片，失败 {} 张", totalImages, errorCount);
    }
    
    /**
     * 导出结果
     */
    private static class ExportResult {
        int successCount;
        int errorCount;
        
        ExportResult(int successCount, int errorCount) {
            this.successCount = successCount;
            this.errorCount = errorCount;
        }
    }
    
    /**
     * 获取商品名称（优先使用主图的title）
     */
    private String getProductTitle(List<Image> productImageList, String productId) {
        // 优先使用主图的title
        for (Image img : productImageList) {
            if (Boolean.TRUE.equals(img.getIsMainImage()) && img.getTitle() != null && !img.getTitle().isEmpty()) {
                return img.getTitle();
            }
        }
        // 如果没有主图title，使用第一张有title的图片
        for (Image img : productImageList) {
            if (img.getTitle() != null && !img.getTitle().isEmpty()) {
                return img.getTitle();
            }
        }
        // 都没有，使用productId
        return productId;
    }
    
    /**
     * 导出单个商品的图片到ZIP
     * @return 导出结果（成功数和失败数）
     */
    private ExportResult exportProductImages(org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream zos, List<Image> productImageList, String productFolderName) throws Exception {
        // 按主图和详情图分组
        List<Image> mainImages = productImageList.stream()
                .filter(img -> Boolean.TRUE.equals(img.getIsMainImage()))
                .collect(Collectors.toList());
        List<Image> detailImages = productImageList.stream()
                .filter(img -> !Boolean.TRUE.equals(img.getIsMainImage()))
                .sorted(Comparator.comparing(img -> img.getDisplayOrder() != null ? img.getDisplayOrder() : 0))
                .collect(Collectors.toList());
        
        // 如果没有主图，使用第一张详情图作为主图
        if (mainImages.isEmpty() && !productImageList.isEmpty()) {
            mainImages.add(productImageList.get(0));
            if (productImageList.size() > 1) {
                detailImages = productImageList.subList(1, productImageList.size());
            } else {
                detailImages = Collections.emptyList();
            }
        }
        
        int successCount = 0;
        int errorCount = 0;
        
        // 添加主图
        for (Image img : mainImages) {
            if (addImageToZip(zos, img, productFolderName, "主图", null)) {
                successCount++;
            } else {
                errorCount++;
            }
        }
        
        // 添加详情图
        int detailIndex = 1;
        for (Image img : detailImages) {
            if (addImageToZip(zos, img, productFolderName, "详情图", detailIndex++)) {
                successCount++;
            } else {
                errorCount++;
            }
        }
        
        return new ExportResult(successCount, errorCount);
    }
    
    /**
     * 添加图片到ZIP文件
     * 支持从本地存储(fileKey)或URL下载图片
     * @return 是否成功添加
     */
    private boolean addImageToZip(org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream zos, Image image, String folderName, String prefix, Integer detailIndex) {
        byte[] imageData = null;
        
        // 方式1：从本地存储读取（thumbnailUrl存储了完整URL或本地路径）
        if (image.getThumbnailUrl() != null && !image.getThumbnailUrl().isEmpty()) {
            try {
                String localPath = image.getThumbnailUrl();
                
                // 如果是完整URL（如 http://localhost:8080/api/uploads/images/xxx.jpg）
                // 提取路径部分
                if (localPath.startsWith("http://") || localPath.startsWith("https://")) {
                    java.net.URL url = new java.net.URL(localPath);
                    localPath = url.getPath(); // 提取 /api/uploads/images/xxx.jpg
                }
                
                // 去掉开头的 /uploads/ 或 /api/uploads/ 前缀，获取相对路径
                if (localPath.startsWith("/api/uploads/")) {
                    localPath = localPath.substring("/api/uploads/".length());
                } else if (localPath.startsWith("/uploads/")) {
                    localPath = localPath.substring("/uploads/".length());
                }
                
                log.debug("尝试从本地存储读取图片：thumbnailUrl={}, 提取路径={}", image.getThumbnailUrl(), localPath);
                java.io.InputStream inputStream = fileStorageService.getFileInputStream(localPath);
                if (inputStream != null) {
                    imageData = inputStream.readAllBytes();
                    inputStream.close();
                }
            } catch (Exception e) {
                log.warn("从本地存储读取失败，尝试从URL下载：{} - {}", image.getThumbnailUrl(), e.getMessage());
            }
        }
        
        // 方式2：从URL下载（如果本地存储失败）
        if ((imageData == null || imageData.length == 0) && image.getUrl() != null && !image.getUrl().isEmpty()) {
            try {
                imageData = downloadImageFromUrl(image.getUrl());
                log.debug("从URL下载图片成功：{}", image.getUrl());
            } catch (Exception e) {
                log.error("从URL下载图片失败：{} - {}", image.getUrl(), e.getMessage());
            }
        }
        
        // 检查是否成功获取图片数据
        if (imageData == null || imageData.length == 0) {
            log.warn("无法获取图片数据，跳过：{} (fileKey={}, url={})", 
                image.getId(), image.getFileKey(), image.getUrl());
            return false;
        }
        
        // 获取文件扩展名
        String originalName = image.getOriginalName();
        String extension = ".jpg";
        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf("."));
        } else if (image.getFileType() != null) {
            extension = "." + image.getFileType().toLowerCase();
        }
        
        // 获取原始文件名（去掉扩展名）
        String baseName = "未命名";
        if (originalName != null && originalName.contains(".")) {
            baseName = originalName.substring(0, originalName.lastIndexOf("."));
        } else if (image.getTitle() != null && !image.getTitle().isEmpty()) {
            baseName = image.getTitle();
        }
        
        // 构建文件名：主图_原始名称.png 或 详情图_1_原始名称.png
        String fileName;
        if (detailIndex != null) {
            fileName = String.format("%s/详情图_%d_%s%s", folderName, detailIndex, baseName, extension);
        } else {
            fileName = String.format("%s/主图_%s%s", folderName, baseName, extension);
        }
        
        // 使用 Apache Commons Compress 的 ZipArchiveEntry
        org.apache.commons.compress.archivers.zip.ZipArchiveEntry entry = 
            new org.apache.commons.compress.archivers.zip.ZipArchiveEntry(fileName);
        entry.setSize(imageData.length);
        // 设置压缩方法
        entry.setMethod(org.apache.commons.compress.archivers.zip.ZipArchiveEntry.DEFLATED);
        
        try {
            zos.putArchiveEntry(entry);
            zos.write(imageData);
            zos.closeArchiveEntry();
            log.debug("添加图片到ZIP：{} ({} bytes)", fileName, imageData.length);
            return true;
        } catch (Exception e) {
            log.error("添加图片到ZIP失败：{}", image.getId(), e);
            // 尝试关闭当前条目以避免损坏ZIP
            try {
                zos.closeArchiveEntry();
            } catch (Exception ignored) {}
            return false;
        }
    }
    
    /**
     * 从URL下载图片数据
     */
    private byte[] downloadImageFromUrl(String imageUrl) throws Exception {
        java.net.URL url = new java.net.URL(imageUrl);
        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        
        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new RuntimeException("HTTP " + responseCode);
        }
        
        try (java.io.InputStream inputStream = connection.getInputStream()) {
            return inputStream.readAllBytes();
        } finally {
            connection.disconnect();
        }
    }
    
    /**
     * 从存储中删除图片文件
     */
    private void deleteImageFile(Image image) {
        if (image.getFileKey() != null && !image.getFileKey().isEmpty()) {
            try {
                if (fileStorageService != null) {
                    fileStorageService.deleteFile(image.getFileKey());
                    log.info("从存储删除图片文件: {}", image.getFileKey());
                }
            } catch (Exception e) {
                log.error("从存储删除图片文件失败: {}", image.getFileKey(), e);
            }
        }
    }
    
    /**
     * 递归获取相册及其所有子相册的ID
     * 用于层级相册查询：点击父相册时显示所有子相册的图片
     * 
     * @param albumId 当前相册ID
     * @param collectedIds 已收集的相册ID列表
     */
    private void collectAllAlbumIds(String albumId, List<String> collectedIds) {
        if (albumId == null || collectedIds == null) {
            return;
        }
        
        // 避免重复添加
        if (!collectedIds.contains(albumId)) {
            collectedIds.add(albumId);
        }
        
        // 查询直接子相册
        List<Album> childAlbums = albumRepository.findByParentIdOrderBySortOrderAsc(albumId);
        
        // 递归处理每个子相册
        for (Album child : childAlbums) {
            if (!collectedIds.contains(child.getId())) {
                collectAllAlbumIds(child.getId(), collectedIds);
            }
        }
    }
}
