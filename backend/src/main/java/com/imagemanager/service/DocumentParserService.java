package com.imagemanager.service;

import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface DocumentParserService {
    /**
     * 解析上传的文档，提取文本内容
     */
    String parseDocument(MultipartFile file) throws Exception;

    /**
     * 将文本按chunkSize切片
     */
    List<String> chunkText(String text, int chunkSize, int overlap);
}
