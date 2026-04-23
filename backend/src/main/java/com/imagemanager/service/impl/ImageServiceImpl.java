package com.imagemanager.service.impl;

import com.imagemanager.dto.BatchDownloadRequest;
import com.imagemanager.dto.BatchDownloadResponse;
import com.imagemanager.dto.ImageQueryRequest;
import com.imagemanager.dto.PageResponse;
import com.imagemanager.entity.Album;
import com.imagemanager.entity.Image;
import com.imagemanager.entity.Product;
import com.imagemanager.repository.AlbumRepository;
import com.imagemanager.repository.ImageRepository;
import com.imagemanager.repository.ProductRepository;
import com.imagemanager.service.AIRecognitionService;
import com.imagemanager.service.AlbumService;
import com.imagemanager.service.ImageService;
import com.imagemanager.service.StorageService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
    
    @Value("${app.upload.path:./uploads}")
    private String uploadPath;
    
    @Autowired(required = false)
    private StorageService storageService;
    
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
    
    /**
     * 初始化预置图片数据
     */
    @PostConstruct
    public void initDefaultImages() {
        // 检查是否已有图片
        if (imageRepository.count() > 0) {
            log.info("图片数据已存在，检查是否需要更新...");

            // 检查是否有图片缺少 isMainImage 或 productId 字段
            List<Image> imagesWithoutFields = imageRepository.findByIsMainImageNullOrProductIdNull();
            if (!imagesWithoutFields.isEmpty()) {
                log.info("发现 {} 张图片缺少 isMainImage 或 productId 字段，开始更新...", imagesWithoutFields.size());

                // 先创建对应的 Product 记录
                for (Image img : imagesWithoutFields) {
                    String productId = null;

                    // 根据 albumId 创建对应的 Product
                    if (img.getAlbumId() != null) {
                        String productName = img.getTitle();
                        String category = img.getAlbumName();

                        // 检查是否已存在该商品
                        List<Product> existingProducts = productRepository.findByAlbumIdAndUserId(img.getAlbumId(), img.getUserId());
                        if (!existingProducts.isEmpty()) {
                            // 使用第一个匹配的商品
                            productId = existingProducts.get(0).getId();
                        } else {
                            // 创建新商品
                            Product product = Product.builder()
                                    .id("product-" + img.getId())
                                    .name(productName)
                                    .category(category)
                                    .albumId(img.getAlbumId())
                                    .userId(img.getUserId())
                                    .coverImageId(img.getId())
                                    .imageCount(1)
                                    .createdAt(img.getCreatedAt())
                                    .updatedAt(img.getUpdatedAt())
                                    .build();
                            productRepository.save(product);
                            productId = product.getId();
                            log.info("创建新商品: {}", productId);
                        }
                    }

                    // 更新图片的 isMainImage 和 productId
                    img.setIsMainImage(true);
                    img.setDisplayOrder(0);
                    if (productId != null) {
                        img.setProductId(productId);
                    }
                    imageRepository.save(img);
                }

                log.info("图片数据更新完成！");
            } else {
                log.info("所有图片数据完整，跳过更新");
            }

            // 使用JPA查询抽样图片，检查tags和aiTags字段（EAGER fetch自动加载）
            List<Image> sampleImages = imageRepository.findByDeletedFalse().stream().limit(5).toList();
            log.info("JPA抽样检查前{}个图片的tags和aiTags字段:", sampleImages.size());
            sampleImages.forEach(img -> {
                log.info("  - Image[id={}, title={}, tags={}, aiTags={}]",
                    img.getId(), img.getTitle(),
                    img.getTags() != null ? img.getTags().toString() : "null",
                    img.getAiTags() != null ? img.getAiTags().toString() : "null");
            });

            // 检查是否有图片包含tags或aiTags（EAGER fetch自动加载）
            List<Image> imagesWithTags = imageRepository.findByDeletedFalse().stream()
                .filter(img -> (img.getTags() != null && !img.getTags().isEmpty()) ||
                              (img.getAiTags() != null && !img.getAiTags().isEmpty()))
                .limit(10)
                .toList();
            log.info("数据库中有tags或aiTags的图片数量（前10条）: {}", imagesWithTags.size());
            imagesWithTags.forEach(img -> {
                log.info("  - Image[id={}, tags={}, aiTags={}]",
                    img.getId(),
                    img.getTags() != null ? img.getTags().toString() : "null",
                    img.getAiTags() != null ? img.getAiTags().toString() : "null");
            });

            return;
        }
        
        log.info("初始化预置户外服装图片数据...");

        // ============================================
        // 创建 Product 记录
        // ============================================

        // 抓绒衣
        Product product1 = Product.builder()
                .id("product-1")
                .name("Patagonia R1 AIR 抓绒衣")
                .description("轻量级抓绒衣，透气排汗保暖速干")
                .category("抓绒衣")
                .albumId("album-fleece")
                .userId("user-1")
                .coverImageId("1")
                .imageCount(1)
                .createdAt(LocalDateTime.now().minusDays(5))
                .updatedAt(LocalDateTime.now().minusDays(5))
                .build();
        productRepository.save(product1);

        // 内衣
        Product product2 = Product.builder()
                .id("product-2")
                .name("Icebreaker 美利奴羊毛内衣")
                .description("美利奴羊毛吸湿长袖T恤，保暖透气")
                .category("内衣")
                .albumId("album-underwear")
                .userId("user-1")
                .coverImageId("2")
                .imageCount(1)
                .createdAt(LocalDateTime.now().minusDays(4))
                .updatedAt(LocalDateTime.now().minusDays(4))
                .build();
        productRepository.save(product2);

        // 软壳
        Product product3 = Product.builder()
                .id("product-3")
                .name("HELLY HANSEN 软壳外套")
                .description("户外软壳防泼水保暖登山服")
                .category("软壳")
                .albumId("album-softshell")
                .userId("user-1")
                .coverImageId("3")
                .imageCount(1)
                .createdAt(LocalDateTime.now().minusDays(3))
                .updatedAt(LocalDateTime.now().minusDays(3))
                .build();
        productRepository.save(product3);

        // T恤
        Product product4 = Product.builder()
                .id("product-4")
                .name("HELLY HANSEN 长袖T恤")
                .description("吸湿速干轻户外都市休闲长袖T恤")
                .category("T恤")
                .albumId("album-tshirt")
                .userId("user-1")
                .coverImageId("4")
                .imageCount(1)
                .createdAt(LocalDateTime.now().minusDays(2))
                .updatedAt(LocalDateTime.now().minusDays(2))
                .build();
        productRepository.save(product4);

        // 冲锋衣
        Product product5 = Product.builder()
                .id("product-5")
                .name("HELLY HANSEN 专业冲锋衣")
                .description("专业Ⅰ级登山3L防风防雨冲锋衣")
                .category("冲锋衣")
                .albumId("album-jacket")
                .userId("user-1")
                .coverImageId("5")
                .imageCount(1)
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();
        productRepository.save(product5);

        log.info("Product 记录创建完成，共 {} 个商品", productRepository.count());

        // ============================================
        // 创建 Image 记录
        // ============================================

        // 抓绒衣
        Image fleece = Image.builder()
                .id("1")
                .title("Patagonia R1 AIR 抓绒衣")
                .url("/assets/「折扣」patagonia巴塔R1AIR抓绒衣男女户外透气排汗保暖速干圆领_619.png")
                .thumbnailUrl("/assets/「折扣」patagonia巴塔R1AIR抓绒衣男女户外透气排汗保暖速干圆领_619.png")
                .size(2400000L)
                .sizeFormatted("2.4 MB")
                .width(800)
                .height(800)
                .resolution("800×800")
                .fileType("png")
                .albumId("album-fleece")
                .albumName("抓绒衣")
                .favorite(true)
                .tags(new java.util.ArrayList<>(Arrays.asList("抓绒衣", "保暖", "户外", "透气", "速干")))
                .isMainImage(true)
                .displayOrder(0)
                .productId("product-1")
                .classifyMethod("filename")
                .createdAt(LocalDateTime.now().minusDays(5))
                .updatedAt(LocalDateTime.now().minusDays(5))
                .userId("user-1")
                .deleted(false)
                .viewCount(0)
                .downloadCount(0)
                .build();
        imageRepository.save(fleece);
        
        // 内衣
        Image underwear = Image.builder()
                .id("2")
                .title("Icebreaker 美利奴羊毛内衣")
                .url("/assets/【单依纯同款】icebreaker美利奴羊毛女200 Oasis吸湿长袖T恤徒步_4.png")
                .thumbnailUrl("/assets/【单依纯同款】icebreaker美利奴羊毛女200 Oasis吸湿长袖T恤徒步_4.png")
                .size(3100000L)
                .sizeFormatted("3.1 MB")
                .width(800)
                .height(800)
                .resolution("800×800")
                .fileType("png")
                .albumId("album-underwear")
                .albumName("内衣")
                .favorite(false)
                .tags(new java.util.ArrayList<>(Arrays.asList("内衣", "美利奴羊毛", "保暖")))
                .isMainImage(true)
                .displayOrder(0)
                .productId("product-2")
                .classifyMethod("filename")
                .createdAt(LocalDateTime.now().minusDays(4))
                .updatedAt(LocalDateTime.now().minusDays(4))
                .userId("user-1")
                .deleted(false)
                .viewCount(0)
                .downloadCount(0)
                .build();
        imageRepository.save(underwear);
        
        // 软壳
        Image softshell = Image.builder()
                .id("3")
                .title("HELLY HANSEN 软壳外套")
                .url("/assets/【经典CREW】 HELLY HANSEN_HH男款户外软壳防泼水保暖登山服抓绒_98.png")
                .thumbnailUrl("/assets/【经典CREW】 HELLY HANSEN_HH男款户外软壳防泼水保暖登山服抓绒_98.png")
                .size(2800000L)
                .sizeFormatted("2.8 MB")
                .width(800)
                .height(800)
                .resolution("800×800")
                .fileType("png")
                .albumId("album-softshell")
                .albumName("软壳")
                .favorite(true)
                .tags(new java.util.ArrayList<>(Arrays.asList("软壳", "防泼水", "保暖", "户外")))
                .isMainImage(true)
                .displayOrder(0)
                .productId("product-3")
                .classifyMethod("filename")
                .createdAt(LocalDateTime.now().minusDays(3))
                .updatedAt(LocalDateTime.now().minusDays(3))
                .userId("user-1")
                .deleted(false)
                .viewCount(0)
                .downloadCount(0)
                .build();
        imageRepository.save(softshell);
        
        // T恤
        Image tshirt = Image.builder()
                .id("4")
                .title("HELLY HANSEN 长袖T恤")
                .url("/assets/【经典款】HELLYHANSEN_HH 男款吸湿速干轻户外都市休闲长袖T恤_372.png")
                .thumbnailUrl("/assets/【经典款】HELLYHANSEN_HH 男款吸湿速干轻户外都市休闲长袖T恤_372.png")
                .size(1900000L)
                .sizeFormatted("1.9 MB")
                .width(800)
                .height(800)
                .resolution("800×800")
                .fileType("png")
                .albumId("album-tshirt")
                .albumName("T恤")
                .favorite(false)
                .tags(new java.util.ArrayList<>(Arrays.asList("T恤", "速干", "休闲", "户外")))
                .isMainImage(true)
                .displayOrder(0)
                .productId("product-4")
                .classifyMethod("filename")
                .createdAt(LocalDateTime.now().minusDays(2))
                .updatedAt(LocalDateTime.now().minusDays(2))
                .userId("user-1")
                .deleted(false)
                .viewCount(0)
                .downloadCount(0)
                .build();
        imageRepository.save(tshirt);
        
        // 冲锋衣
        Image jacket = Image.builder()
                .id("5")
                .title("HELLY HANSEN 专业冲锋衣")
                .url("/assets/【王一博同款】HELLY HANSEN_HH 专业Ⅰ级登山3L防风防雨冲锋衣_371.png")
                .thumbnailUrl("/assets/【王一博同款】HELLY HANSEN_HH 专业Ⅰ级登山3L防风防雨冲锋衣_371.png")
                .size(4200000L)
                .sizeFormatted("4.2 MB")
                .width(800)
                .height(800)
                .resolution("800×800")
                .fileType("png")
                .albumId("album-jacket")
                .albumName("冲锋衣")
                .favorite(true)
                .tags(new java.util.ArrayList<>(Arrays.asList("冲锋衣", "防风", "防水", "专业", "登山")))
                .isMainImage(true)
                .displayOrder(0)
                .productId("product-5")
                .classifyMethod("filename")
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .userId("user-1")
                .deleted(false)
                .viewCount(0)
                .downloadCount(0)
                .build();
        imageRepository.save(jacket);

        // 更新相册图片数量（只统计主图，即商品数量）
        albumRepository.findAll().forEach(album -> {
            long count = imageRepository.countMainImagesByAlbumId(album.getId());
            album.setImageCount((int) count);
            album.setUpdatedAt(LocalDateTime.now());
            albumRepository.save(album);
        });

        log.info("预置图片初始化完成，共 {} 张（包括主图和详情图）", imageRepository.count());
        log.info("相册统计（只统计主图）：");
        albumRepository.findAll().forEach(album -> {
            log.info("  - {}: {} 个商品", album.getName(), album.getImageCount());
        });
    }
    
    @Override
    public PageResponse<Image> queryImages(ImageQueryRequest request) {
        log.info("查询图片列表，参数：{}", request);

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
            (request.getTags() != null && !request.getTags().isEmpty()) ||
            (request.getStartDate() != null && !request.getStartDate().isEmpty()) ||
            (request.getEndDate() != null && !request.getEndDate().isEmpty()) ||
            (request.getFileType() != null && !request.getFileType().isEmpty()) ||
            (request.getAlbumId() != null && !request.getAlbumId().isEmpty()) ||
            request.getFavorite() != null ||
            request.getOnlyMainImage() != null;

        if (hasAdvancedFilters) {
            // 使用安全的 Stream 过滤进行高级搜索
            log.info("使用Stream过滤进行高级搜索，参数: {}", request);
            
            // 1. 先获取所有未删除的主图（只查主图，不查详情图），EAGER fetch自动加载tags
            List<Image> allImages = imageRepository.findByDeletedFalseAndIsMainImageTrue();
            log.info("从数据库获取主图总数: {}", allImages.size());
            
            // 2. 处理日期范围
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
            
            // 3. 处理文件类型（支持多个）
            List<String> fileTypes = null;
            if (request.getFileType() != null && !request.getFileType().isEmpty()) {
                fileTypes = java.util.Arrays.asList(request.getFileType().split(","));
                log.info("文件类型筛选: {}", fileTypes);
            }
            
            // 4. 处理相册ID（支持多个，兼容单个的情况）
            // 如果传入的是父相册ID，需要查询该父相册下所有子相册的ID
            List<String> albumIds = null;
            if (request.getAlbumId() != null && !request.getAlbumId().isEmpty()) {
                List<String> targetAlbumIds = new java.util.ArrayList<>();
                
                if (request.getAlbumId().contains(",")) {
                    // 多个相册ID
                    for (String albumId : request.getAlbumId().split(",")) {
                        targetAlbumIds.add(albumId.trim());
                    }
                } else {
                    // 单个相册ID
                    targetAlbumIds.add(request.getAlbumId());
                }
                
                // 查询每个目标相册的子相册ID
                for (String albumId : targetAlbumIds) {
                    // 把自己加进去
                    albumIds = albumIds == null ? new java.util.ArrayList<>() : albumIds;
                    if (!albumIds.contains(albumId)) {
                        albumIds.add(albumId);
                    }
                    // 查询子相册
                    List<Album> childAlbums = albumRepository.findByUserIdAndParentIdOrderBySortOrderAsc(
                        request.getUserId() != null ? request.getUserId() : "default", albumId);
                    for (Album child : childAlbums) {
                        if (!albumIds.contains(child.getId())) {
                            albumIds.add(child.getId());
                        }
                    }
                }
                
                log.info("相册ID筛选（含子相册）: {}", albumIds);
            }
            
            // 5. 处理标签
            List<String> tags = request.getTags();
            if (tags != null && !tags.isEmpty()) {
                log.info("标签筛选: {}", tags);
            }
            
            // 6. 为lambda表达式准备final变量
            final String finalKeyword = request.getKeyword();
            final List<String> finalTags = tags;
            final LocalDateTime finalStartDate = startDate;
            final LocalDateTime finalEndDate = endDate;
            final List<String> finalFileTypes = fileTypes;
            final List<String> finalAlbumIds = albumIds;
            final Boolean finalFavorite = request.getFavorite();
            final Boolean finalOnlyMainImage = request.getOnlyMainImage();
            final String finalSortBy = request.getSortBy();
            final String finalSortOrder = request.getSortOrder();
            
            // 7. 使用 Stream 进行过滤
            List<Image> filteredImages = allImages.stream()
                .filter(img -> {
                    // 关键词筛选
                    if (finalKeyword != null && !finalKeyword.isEmpty()) {
                        String lowerKeyword = finalKeyword.toLowerCase();
                        boolean titleMatch = img.getTitle() != null && img.getTitle().toLowerCase().contains(lowerKeyword);
                        boolean descMatch = img.getDescription() != null && img.getDescription().toLowerCase().contains(lowerKeyword);
                        if (!titleMatch && !descMatch) return false;
                    }
                    
                    // 标签筛选
                    if (finalTags != null && !finalTags.isEmpty()) {
                        if (img.getTags() == null || img.getTags().isEmpty()) return false;
                        boolean tagMatch = finalTags.stream().anyMatch(tag -> img.getTags().contains(tag));
                        if (!tagMatch) return false;
                    }
                    
                    // 日期范围筛选
                    if (finalStartDate != null && img.getCreatedAt().isBefore(finalStartDate)) return false;
                    if (finalEndDate != null && img.getCreatedAt().isAfter(finalEndDate)) return false;
                    
                    // 文件类型筛选
                    if (finalFileTypes != null && !finalFileTypes.isEmpty()) {
                        if (img.getFileType() == null) return false;
                        boolean typeMatch = finalFileTypes.stream().anyMatch(ft -> ft.equalsIgnoreCase(img.getFileType()));
                        if (!typeMatch) return false;
                    }
                    
                    // 相册ID筛选
                    if (finalAlbumIds != null && !finalAlbumIds.isEmpty()) {
                        if (!finalAlbumIds.contains(img.getAlbumId())) return false;
                    }
                    
                    // 收藏筛选
                    if (finalFavorite != null && !finalFavorite.equals(img.getFavorite())) return false;
                    
                    // 主图筛选
                    if (finalOnlyMainImage != null && !finalOnlyMainImage.equals(img.getIsMainImage())) return false;
                    
                    return true;
                })
                // 排序
                .sorted((a, b) -> {
                    String sortByField = finalSortBy != null ? finalSortBy : "createdAt";
                    boolean isAsc = "asc".equalsIgnoreCase(finalSortOrder);
                    
                    int comparison = 0;
                    if ("date".equals(sortByField) || "createdAt".equals(sortByField)) {
                        LocalDateTime dateA = a.getCreatedAt();
                        LocalDateTime dateB = b.getCreatedAt();
                        if (dateA != null && dateB != null) {
                            comparison = dateA.compareTo(dateB);
                        } else if (dateA != null) {
                            comparison = 1;
                        } else if (dateB != null) {
                            comparison = -1;
                        }
                    } else if ("name".equals(sortByField)) {
                        String titleA = a.getTitle() != null ? a.getTitle() : "";
                        String titleB = b.getTitle() != null ? b.getTitle() : "";
                        comparison = titleA.compareTo(titleB);
                    } else if ("size".equals(sortByField)) {
                        Long sizeA = a.getSize() != null ? a.getSize() : 0L;
                        Long sizeB = b.getSize() != null ? b.getSize() : 0L;
                        comparison = sizeA.compareTo(sizeB);
                    }
                    
                    return isAsc ? comparison : -comparison;
                })
                .toList();
            
            log.info("Stream过滤完成，筛选后数量: {}", filteredImages.size());
            
            // 8. 手动分页（复用已定义的pageSize变量）
            page = request.getPage() != null ? request.getPage() - 1 : 0;
            pageSize = request.getPageSize() != null ? request.getPageSize() : 20;
            int totalElements = filteredImages.size();
            int start = page * pageSize;
            int end = Math.min(start + pageSize, totalElements);
            
            List<Image> pageContent = start < end 
                ? filteredImages.subList(start, end) 
                : new java.util.ArrayList<>();
            
            imagePage = new org.springframework.data.domain.PageImpl<>(pageContent, pageable, totalElements);
            
            log.info("分页结果: 第{}页, 每页{}条, 当前页{}条, 总计{}条", 
                page + 1, pageSize, pageContent.size(), totalElements);
        } else if (request.getKeyword() != null && !request.getKeyword().isEmpty()) {
            // 简单关键词搜索
            if (request.getAlbumId() != null) {
                imagePage = imageRepository.searchByAlbumAndKeyword(
                        request.getAlbumId(), request.getKeyword(), pageable);
            } else {
                imagePage = imageRepository.searchByKeyword(request.getKeyword(), pageable);
            }
        } else if (request.getAlbumId() != null) {
            // 相册查询：直接查询该相册下的图片（不通过products表）
            String albumId = request.getAlbumId();
            log.info("查询相册，albumId={}", albumId);

            // 如果 onlyMainImage 为 true，只查询主图
            if (request.getOnlyMainImage() != null && request.getOnlyMainImage()) {
                log.info("只查询主图，albumId={}", albumId);
                imagePage = imageRepository.findByAlbumIdAndIsMainImageAndDeleted(albumId, true, false, pageable);
                log.info("查询到的主图数量：{}", imagePage.getContent().size());
            } else {
                // 查询该相册的所有图片
                log.info("查询所有图片（包括主图和详情图），albumId={}", albumId);
                imagePage = imageRepository.findByAlbumIdAndDeleted(albumId, false, pageable);
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
            if (storageService != null) {
                imageUrl = storageService.uploadFile(file, "images");
                fileKey = storageService.getStorageKey(imageUrl);
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
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .userId("user-1")
                    .deleted(false)
                    .viewCount(0)
                    .downloadCount(0)
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
        image.setUpdatedAt(LocalDateTime.now());
        
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
                relatedImage.setDeletedAt(LocalDateTime.now());
                imageRepository.save(relatedImage);
            }
            log.info("同时删除了 {} 张关联的详情图", relatedImages.size());
        }
        
        image.setDeleted(true);
        image.setDeletedAt(LocalDateTime.now());
        imageRepository.save(image);
        
        // 更新相册图片数量
        if (image.getAlbumId() != null) {
            updateAlbumImageCount(image.getAlbumId());
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
                    imageRepository.delete(relatedImage);
                    deletedCount++;
                    log.debug("永久删除详情图：{}", relatedImage.getId());
                }
                log.info("同时永久删除了 {} 张关联的详情图", relatedImages.size());
            }
            
            // 从存储中删除文件
            deleteImageFile(image);
            
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
                restoredCount++;
                log.debug("恢复详情图：{}", relatedImage.getId());
            }
            log.info("同时恢复了 {} 张关联的详情图", relatedImages.size());
        }
        
        // 恢复主图本身
        image.setDeleted(false);
        image.setDeletedAt(null);
        imageRepository.save(image);
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
        image.setUpdatedAt(LocalDateTime.now());
        
        return imageRepository.save(image);
    }
    
    @Override
    public void batchFavorite(List<String> ids) {
        log.info("批量收藏图片，数量：{}", ids.size());
        ids.forEach(id -> {
            imageRepository.findById(id).ifPresent(image -> {
                image.setFavorite(true);
                image.setUpdatedAt(LocalDateTime.now());
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
                image.setUpdatedAt(LocalDateTime.now());
                imageRepository.save(image);
                
                // 如果是主图，同时移动所有关联的详情图
                if (Boolean.TRUE.equals(image.getIsMainImage()) && image.getProductId() != null) {
                    List<Image> relatedImages = imageRepository.findByProductIdAndDeletedOrderByDisplayOrderAsc(image.getProductId(), false);
                    for (Image relatedImage : relatedImages) {
                        relatedImage.setAlbumId(albumId);
                        relatedImage.setAlbumName(albumName);
                        relatedImage.setUpdatedAt(LocalDateTime.now());
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
    public PageResponse<Image> getTrash(Integer page, Integer pageSize) {
        log.info("获取回收站图片列表");
        
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
    public PageResponse<Image> getRecent(Integer page, Integer pageSize) {
        log.info("获取最近上传图片列表（只返回主图）");
        
        // 计算7天前的日期
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        
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
        log.info("获取回收站主图数量");
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
                    imageRepository.delete(relatedImage);
                    totalDeleted++;
                    log.debug("清空回收站 - 永久删除详情图：{}", relatedImage.getId());
                }
            }
            
            // 从存储中删除主图文件
            deleteImageFile(mainImage);
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
            if (storageService != null) {
                imageUrl = storageService.uploadFile(file, "images");
                fileKey = storageService.getStorageKey(imageUrl);
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
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .userId("user-1")
                    .deleted(false)
                    .viewCount(0)
                    .downloadCount(0)
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
            album.setUpdatedAt(LocalDateTime.now());
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
            
            // 如果指定了分类，查找或创建对应的相册（支持层级目录）
            String albumId = null;
            String albumName = null;
            if (item.getCategory() != null && !item.getCategory().isEmpty()) {
                String category = item.getCategory().trim();
                log.info("Excel导入 - 处理分类: {}, 父相册: {}", category, parentAlbumName);
                
                // 构建完整层级路径：如果有父相册名称，分类在父相册下创建
                String fullCategoryPath = category;
                if (parentAlbumName != null && !parentAlbumName.isEmpty()) {
                    // 移除文件名中的扩展名（如 .xlsx）
                    String cleanParentName = parentAlbumName;
                    int dotIndex = cleanParentName.lastIndexOf('.');
                    if (dotIndex > 0) {
                        cleanParentName = cleanParentName.substring(0, dotIndex);
                    }
                    fullCategoryPath = cleanParentName + "/" + category;
                    log.info("Excel导入 - 构建层级路径: {}", fullCategoryPath);
                }
                
                // 首先尝试匹配已有相册（精确匹配或包含匹配）
                for (Album album : albums) {
                    String albumPath = album.getPath() != null ? album.getPath() : album.getName();
                    // 尝试匹配原始分类名或完整层级路径
                    if (albumPath.equals(fullCategoryPath) || albumPath.equals(category) || 
                        album.getName().equals(category) || album.getName().contains(category)) {
                        albumId = album.getId();
                        albumName = album.getFullName() != null ? album.getFullName() : album.getName();
                        log.info("Excel导入 - 匹配到已有相册: ID={}, 名称={}", albumId, albumName);
                        break;
                    }
                }
                
                // 如果没有匹配到，尝试创建/获取层级相册
                if (albumId == null) {
                    try {
                        // 尝试解析层级结构并创建相册
                        Album hierarchyAlbum = albumService.getOrCreateAlbumByPath(fullCategoryPath);
                        if (hierarchyAlbum != null) {
                            albumId = hierarchyAlbum.getId();
                            albumName = hierarchyAlbum.getFullName() != null ? hierarchyAlbum.getFullName() : hierarchyAlbum.getName();
                            // 刷新相册列表
                            albums = albumService.getAllAlbums();
                            log.info("Excel导入 - 创建/获取层级相册: ID={}, 名称={}", albumId, albumName);
                        }
                    } catch (Exception e) {
                        log.warn("Excel导入 - 创建层级相册失败: {}", e.getMessage());
                    }
                }
            }
            
            // 如果没有指定分类但有父相册名称，创建父相册（作为根相册）
            if (albumId == null && parentAlbumName != null && !parentAlbumName.isEmpty()) {
                String cleanParentName = parentAlbumName;
                int dotIndex = cleanParentName.lastIndexOf('.');
                if (dotIndex > 0) {
                    cleanParentName = cleanParentName.substring(0, dotIndex);
                }
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
            if (product == null) {
                // 商品不存在，创建新记录
                product = new Product();
                product.setId(productId);
                product.setName(item.getProductName());
                product.setDescription(item.getDescription());
                product.setCategory(item.getCategory());
                product.setAlbumId(albumId);
                product.setUserId("user-1"); // 默认用户
                product.setImageCount(0);
                product = productRepository.save(product);
                log.info("创建商品记录: ID={}, 名称={}, 分类={}", productId, item.getProductName(), item.getCategory());
            } else {
                // 商品存在但图片已删除，更新分类等信息
                product.setDescription(item.getDescription());
                product.setCategory(item.getCategory());
                product.setAlbumId(albumId);
                product.setImageCount(0);
                product = productRepository.save(product);
                log.info("复用商品记录重新导入图片: ID={}, 名称={}", product.getId(), item.getProductName());
            }

            // 合并所有需要下载的URL（主图 + 详情图）
            List<String> allUrls = new ArrayList<>();
            if (item.getMainImageUrl() != null && !item.getMainImageUrl().isEmpty() && !item.getMainImageUrl().trim().isEmpty()) {
                allUrls.add(item.getMainImageUrl().trim());
            }
            if (item.getDetailImageUrls() != null && !item.getDetailImageUrls().isEmpty()) {
                // 过滤空URL和无效URL
                item.getDetailImageUrls().stream()
                    .filter(url -> url != null && !url.isEmpty() && !url.trim().isEmpty())
                    .map(String::trim)
                    .forEach(allUrls::add);
            }

            // 检查是否有有效的URL
            if (allUrls.isEmpty()) {
                log.warn("商品 {} 没有有效的图片URL，跳过", item.getProductName());
                com.imagemanager.dto.BatchDownloadResponse emptyResponse = new com.imagemanager.dto.BatchDownloadResponse();
                emptyResponse.setSuccess(false);
                emptyResponse.setError("没有有效的图片URL");
                results.add(emptyResponse);
                continue;
            }

            int totalImages = allUrls.size();
            int successCount = 0;
            String mainImageId = null; // 记录主图ID

            // 下载所有图片
            for (int i = 0; i < allUrls.size(); i++) {
                String imageUrl = allUrls.get(i);
                com.imagemanager.dto.BatchDownloadResponse response = new com.imagemanager.dto.BatchDownloadResponse();
                response.setOriginalUrl(imageUrl);
                
                boolean isMainImage = (i == 0); // 第一张是主图
                
                try {
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
                            response.setError("下载的图片数据为空");
                            results.add(response);
                            continue;
                        }

                        // 验证图片文件头
                        if (!isValidImageData(imageData)) {
                            log.error("下载的数据不是有效的图片格式: {}, 文件头: {}",
                                imageUrl, bytesToHex(imageData, 0, Math.min(16, imageData.length)));
                            response.setSuccess(false);
                            response.setError("下载的数据不是有效的图片格式");
                            results.add(response);
                            continue;
                        }

                        // 创建文件名：商品名称 + (序号) + 扩展名
                        String extension = getFileExtensionFromUrl(imageUrl);
                        String fileName = sanitizeFileName(item.getProductName());
                        if (totalImages > 1) {
                            fileName += (isMainImage ? "" : "_" + i);
                        }
                        fileName += extension;

                        // 创建MultipartFile
                        com.imagemanager.util.ByteArrayMultipartFile file =
                            new com.imagemanager.util.ByteArrayMultipartFile(
                                imageData,      // 第1个参数：byte[] content - 文件字节内容
                                "file",         // 第2个参数：String name - 表单字段名
                                fileName,       // 第3个参数：String originalFilename - 文件原始名称
                                contentType     // 第4个参数：String contentType - MIME类型
                            );

                        // 保存图片
                        Image image = uploadSingleImageWithAI(file, albums, fileName, albumId, null);
                        
                        // 更新商品相关信息
                        if (image != null) {
                            image.setTitle(item.getProductName());
                            image.setProductId(productId);
                            image.setIsMainImage(isMainImage);
                            image.setDisplayOrder(isMainImage ? 0 : i); // 主图在前，详情图按顺序
                            if (item.getDescription() != null) {
                                image.setDescription(item.getDescription());
                            }
                            image = imageRepository.save(image);

                            successCount++;
                            response.setSuccess(true);
                            response.setImageId(image.getId());

                            // 记录主图ID
                            if (isMainImage) {
                                mainImageId = image.getId();
                            }
                        } else {
                            response.setSuccess(false);
                            response.setError("上传失败");
                        }
                    }
                } catch (Exception e) {
                    log.error("下载图片失败: {}, 错误: {}", imageUrl, e.getMessage());
                    response.setSuccess(false);
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

            log.info("商品 {} 下载完成：成功 {}/{}",
                item.getProductName(), successCount, totalImages);
        }
        
        // 更新受影响的相册图片数量
        Set<String> affectedAlbumIds = new HashSet<>();
        for (com.imagemanager.dto.BatchDownloadResponse resp : results) {
            if (resp.getSuccess() && resp.getImageId() != null) {
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
        
        log.info("批量下载完成，成功：{} 张，失败：{} 张", 
            results.stream().filter(r -> r.getSuccess()).count(),
            results.stream().filter(r -> !r.getSuccess()).count());
        
        return results;
    }
    
    /**
     * 上传单张图片并使用AI分析（带自定义参数）
     */
    private Image uploadSingleImageWithAI(MultipartFile file, List<Album> albums, 
            String originalFilename, String albumId, List<String> tags) {
        try {
            String imageUrl;
            String fileKey = null;
            
            // 使用存储服务上传文件
            if (storageService != null) {
                imageUrl = storageService.uploadFile(file, "images");
                fileKey = storageService.getStorageKey(imageUrl);
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
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .userId("user-1")
                    .deleted(false)
                    .viewCount(0)
                    .downloadCount(0)
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
    public byte[] exportAlbumImages(String albumId) throws Exception {
        log.info("导出相册图片：{}", albumId);
        
        // 获取相册信息
        Album album = albumService.getAlbumById(albumId);
        String albumName = album != null ? album.getName() : "unknown";
        
        // 获取相册下的所有图片
        ImageQueryRequest request = new ImageQueryRequest();
        request.setAlbumId(albumId);
        request.setPage(1);
        request.setPageSize(10000);
        Page<Image> imagePage = imageRepository.findByAlbumIdAndDeleted(albumId, false, PageRequest.of(0, 10000));
        
        List<Image> images = imagePage.getContent();
        if (images.isEmpty()) {
            throw new RuntimeException("相册中没有图片");
        }
        
        // 按商品ID分组
        Map<String, List<Image>> productImages = images.stream()
                .collect(Collectors.groupingBy(img -> img.getProductId() != null ? img.getProductId() : img.getId()));
        
        // 创建ZIP文件
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, List<Image>> entry : productImages.entrySet()) {
                String productId = entry.getKey();
                List<Image> productImageList = entry.getValue();
                
                // 商品文件夹名称
                String productFolderName = sanitizeFileName(productId);
                
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
                    detailImages = productImageList.subList(1, productImageList.size());
                }
                
                // 添加主图
                int detailIndex = 1;
                for (Image img : mainImages) {
                    addImageToZip(zos, img, productFolderName, "主图", null);
                }
                
                // 添加详情图
                for (Image img : detailImages) {
                    addImageToZip(zos, img, productFolderName, "详情图", detailIndex++);
                }
            }
        }
        
        return baos.toByteArray();
    }
    
    @Override
    public byte[] exportMultipleAlbums(List<String> albumIds) throws Exception {
        log.info("批量导出多个相册，数量：{}", albumIds.size());
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (String albumId : albumIds) {
                // 获取相册信息
                Album album = albumService.getAlbumById(albumId);
                String albumName = album != null ? sanitizeFileName(album.getName()) : "unknown";
                
                // 获取相册下的所有图片
                Page<Image> imagePage = imageRepository.findByAlbumIdAndDeleted(albumId, false, PageRequest.of(0, 10000));
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
                    
                    // 商品文件夹名称（在相册子目录下）
                    String productFolderName = albumName + "/" + sanitizeFileName(productId);
                    
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
                    
                    // 添加主图
                    int detailIndex = 1;
                    for (Image img : mainImages) {
                        addImageToZip(zos, img, productFolderName, "主图", null);
                    }
                    
                    // 添加详情图
                    for (Image img : detailImages) {
                        addImageToZip(zos, img, productFolderName, "详情图", detailIndex++);
                    }
                }
            }
        }
        
        return baos.toByteArray();
    }
    
    /**
     * 添加图片到ZIP文件
     */
    private void addImageToZip(ZipOutputStream zos, Image image, String folderName, String prefix, Integer detailIndex) {
        try {
            if (image.getFileKey() == null || image.getFileKey().isEmpty()) {
                log.warn("图片{}没有fileKey，跳过", image.getId());
                return;
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
            
            // 读取图片数据
            byte[] imageData = storageService.getFileInputStream(image.getFileKey()).readAllBytes();
            
            // 添加到ZIP
            ZipEntry entry = new ZipEntry(fileName);
            zos.putNextEntry(entry);
            zos.write(imageData);
            zos.closeEntry();
            
            log.debug("添加图片到ZIP：{}", fileName);
        } catch (Exception e) {
            log.error("添加图片到ZIP失败：{}", image.getId(), e);
        }
    }
    
    /**
     * 从存储中删除图片文件
     */
    private void deleteImageFile(Image image) {
        if (image.getFileKey() != null && !image.getFileKey().isEmpty()) {
            try {
                if (storageService != null) {
                    storageService.deleteFile(image.getFileKey());
                    log.info("从存储删除图片文件: {}", image.getFileKey());
                }
            } catch (Exception e) {
                log.error("从存储删除图片文件失败: {}", image.getFileKey(), e);
            }
        }
    }
}
