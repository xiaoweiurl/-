package com.imagemanager.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 文档服务接口
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
public interface DocumentService {
    
    /**
     * 上传文档
     * 
     * @param file 文档文件
     * @param fileName 文件名
     * @return 上传结果，包含URL等信息
     */
    Map<String, Object> uploadDocument(MultipartFile file, String fileName);
    
    /**
     * 上传文档（带分类）
     * 
     * @param file 文档文件
     * @param fileName 文件名
     * @param category 文档分类
     * @return 上传结果，包含URL等信息
     */
    Map<String, Object> uploadDocument(MultipartFile file, String fileName, String category);
    
    /**
     * 批量上传文档
     * 
     * @param files 文档文件列表
     * @return 上传结果列表
     */
    List<Map<String, Object>> batchUploadDocuments(List<MultipartFile> files);
    
    /**
     * 删除文档
     * 
     * @param id 文档ID
     */
    void deleteDocument(String id);
    
    /**
     * 批量删除文档（移入回收站）
     * 
     * @param ids 文档ID列表
     * @return 删除结果
     */
    Map<String, Object> batchDeleteDocuments(List<String> ids);
    
    /**
     * 恢复文档
     * 
     * @param id 文档ID
     */
    void restoreDocument(String id);
    
    /**
     * 永久删除文档（直接从数据库和存储中删除，无法恢复）
     * 
     * @param id 文档ID
     */
    void permanentDeleteDocument(String id);
    
    /**
     * 批量永久删除文档
     * 
     * @param ids 文档ID列表
     * @return 恢复结果
     */
    Map<String, Object> batchRestoreDocuments(List<String> ids);
    
    /**
     * 获取文档信息
     * 
     * @param id 文档ID
     * @return 文档信息
     */
    Map<String, Object> getDocumentById(String id);
    
    /**
     * 获取文档下载链接
     * 
     * @param id 文档ID
     * @return 下载URL
     */
    String getDownloadUrl(String id);
    
    /**
     * 获取文档列表
     * 
     * @param page 页码
     * @param pageSize 每页数量
     * @return 文档列表和分页信息
     */
    Map<String, Object> getDocuments(int page, int pageSize);
    
    /**
     * 按分类获取文档列表
     * 
     * @param page 页码
     * @param pageSize 每页数量
     * @param category 文档分类（pdf/word/excel/ppt/zip/other/all）
     * @return 文档列表和分页信息
     */
    Map<String, Object> getDocumentsByCategory(int page, int pageSize, String category);
    
    /**
     * 获取文档统计信息
     * 
     * @return 各分类的文档数量统计
     */
    Map<String, Integer> getDocumentStats();
}
