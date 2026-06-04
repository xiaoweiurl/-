package com.imagemanager.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * 图片查询请求参数
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageQueryRequest {
    
    /**
     * 搜索关键词
     */
    private String keyword;
    
    /**
     * 相册ID
     */
    private String albumId;
    
    /**
     * 文件类型（jpg, png, gif等）
     */
    private String fileType;
    
    /**
     * 是否收藏
     */
    private Boolean favorite;
    
    /**
     * 是否已删除（回收站）
     */
    private Boolean deleted;

    /**
     * 是否只返回主图
     */
    private Boolean onlyMainImage;

    /**
     * 开始日期
     */
    private String startDate;
    
    /**
     * 结束日期
     */
    private String endDate;
    
    /**
     * 标签列表
     */
    private List<String> tags;
    
    /**
     * 排序字段（date, name, size）
     */
    private String sortBy;
    
    /**
     * 排序方式（asc, desc）
     */
    private String sortOrder;
    
    /**
     * 当前页码
     */
    private Integer page = 1;
    
    /**
     * 每页大小
     */
    private Integer pageSize = 20;

    /**
     * 是否只查询当前用户的图片（数据隔离）
     */
    private Boolean onlyMine;
}
