package com.imagemanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 图片管理系统主应用
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@SpringBootApplication
public class ImageManagerApplication {

    public static void main(String[] args) {
        // PDFBox: 跳过系统字体扫描，避免损坏字体导致 'head' table is mandatory 错误
        System.setProperty("org.apache.pdfbox.font.autoload", "false");
        SpringApplication.run(ImageManagerApplication.class, args);
        System.out.println("========================================");
        System.out.println("  图片管理系统后端服务启动成功！");
        System.out.println("  访问地址: http://localhost:8080");
        System.out.println("  API文档: http://localhost:8080/swagger-ui.html");
        System.out.println("========================================");
    }
}
