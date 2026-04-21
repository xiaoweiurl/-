package com.imagemanager.service;

import com.imagemanager.dto.BatchDownloadResponse;
import com.imagemanager.dto.BatchDownloadRequest;
import com.imagemanager.dto.ImageQueryRequest;
import com.imagemanager.dto.PageResponse;
import com.imagemanager.entity.Image;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 图片服务接口
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
public interface ImageService {
    
    /**
     * 查询图片列表（分页）
     */
    PageResponse<Image> queryImages(ImageQueryRequest request);
    
    /**
     * 根据ID获取图片详情
     */
    Image getImageById(String id);
    
    /**
     * 上传图片
     */
    Image uploadImage(MultipartFile file, String title, String albumId, List<String> tags);
    
    /**
     * 更新图片信息
     */
    Image updateImage(String id, String title, String albumId, List<String> tags, String description);
    
    /**
     * 删除图片（移至回收站）
     */
    void deleteImage(String id);
    
    /**
     * 批量删除图片
     */
    void batchDelete(List<String> ids);
    
    /**
     * 永久删除图片
     * @param id 图片ID
     * @return 删除的图片数量（主图+详情图）
     */
    int permanentDelete(String id);
    
    /**
     * 恢复图片（从回收站）
     * @return 恢复的图片数量（主图+详情图）
     */
    int restoreImage(String id);
    
    /**
     * 批量恢复图片
     * @return 恢复的图片数量（主图+详情图）
     */
    int batchRestore(List<String> ids);
    
    /**
     * 切换收藏状态
     */
    Image toggleFavorite(String id);
    
    /**
     * 批量收藏
     */
    void batchFavorite(List<String> ids);
    
    /**
     * 移动图片到相册
     */
    void moveToAlbum(List<String> ids, String albumId);
    
    /**
     * 获取收藏的图片
     */
    PageResponse<Image> getFavorites(Integer page, Integer pageSize);
    
    /**
     * 获取回收站图片
     */
    PageResponse<Image> getTrash(Integer page, Integer pageSize);
    
    /**
     * 获取最近上传的图片（7天内）
     */
    PageResponse<Image> getRecent(Integer page, Integer pageSize);
    
    /**
     * 清空回收站
     * @return 删除的图片数量（主图+详情图）
     */
    int clearTrash();
    
    /**
     * 获取回收站主图数量
     * @return 回收站中主图的数量
     */
    long getTrashCount();
    
    /**
     * 批量上传图片
     * @param files 图片文件列表
     * @return 上传成功的图片列表
     */
    List<Image> batchUploadImages(List<MultipartFile> files);
    
    /**
     * 批量下载网络图片
     * @param request 批量下载请求
     * @return 下载结果列表
     */
    List<BatchDownloadResponse> batchDownloadImages(BatchDownloadRequest request);
    
    /**
     * 导出单个相册的所有图片
     * @param albumId 相册ID
     * @return ZIP文件的字节数组
     */
    byte[] exportAlbumImages(String albumId) throws Exception;
    
    /**
     * 导出多个相册的图片
     * @param albumIds 相册ID列表
     * @return ZIP文件的字节数组
     */
    byte[] exportMultipleAlbums(List<String> albumIds) throws Exception;
}
