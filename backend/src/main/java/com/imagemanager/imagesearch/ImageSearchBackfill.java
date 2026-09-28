package com.imagemanager.imagesearch;

import com.imagemanager.ImageManagerApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 回填入口。用 scripts/image-search-backfill.ps1 启动，避免 PowerShell 拆开 -Dexec.args。
 * 本进程会把 image-search.enabled 打开，不影响 IDEA 里正在跑的服务。
 */
public final class ImageSearchBackfill {

    private ImageSearchBackfill() {
    }

    public static void main(String[] args) {
        System.setProperty("spring.main.web-application-type", "none");
        System.setProperty("image-search.enabled", "true");
        SpringApplication application = new SpringApplication(ImageManagerApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        int code;
        try (ConfigurableApplicationContext context = application.run(new String[0])) {
            code = context.getBean(ImageSearchBackfillService.class).runAndPrint(args);
        }
        System.exit(code);
    }
}
