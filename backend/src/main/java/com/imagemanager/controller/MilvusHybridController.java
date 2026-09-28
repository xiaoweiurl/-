package com.imagemanager.controller;

import com.imagemanager.service.MilvusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 混合检索重建入口。挂在 /admin 下，沿用管理员角色保护。
 * 可重复调用：每次都会删掉混合集合再从稠密集合回填。
 */
@RestController
@RequestMapping("/admin/milvus/hybrid")
public class MilvusHybridController {

    private final MilvusService milvusService;

    public MilvusHybridController(MilvusService milvusService) {
        this.milvusService = milvusService;
    }

    @GetMapping
    public Map<String, Object> status() {
        Map<String, Object> status = milvusService.hybridStatus();
        status.put("success", true);
        return status;
    }

    @PostMapping("/rebuild")
    public Map<String, Object> rebuild() {
        return milvusService.startHybridRebuild();
    }
}
