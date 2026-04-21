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
        SpringApplication.run(ImageManagerApplication.class, args);
        System.out.println("========================================");
        System.out.println("  图片管理系统后端服务启动成功！");
        System.out.println("  访问地址: http://localhost:8080");
        System.out.println("  API文档: http://localhost:8080/swagger-ui.html");
        System.out.println("========================================");
    }
}
