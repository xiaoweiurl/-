package com.imagemanager.imagesearch;

import com.imagemanager.ImageManagerApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 只读评测入口。不写数据库，不写 Milvus。
 */
public final class ImageSearchEval {

    private ImageSearchEval() {
    }

    public static void main(String[] args) {
        System.setProperty("spring.main.web-application-type", "none");
        System.setProperty("image-search.enabled", "true");
        SpringApplication application = new SpringApplication(ImageManagerApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        int code;
        try (ConfigurableApplicationContext context = application.run(new String[0])) {
            code = context.getBean(ImageSearchEvalService.class).runAndPrint(args);
        }
        System.exit(code);
    }
}
