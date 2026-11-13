package com.example.logservice.controller;

import com.example.logservice.common.ApiResult;
import com.example.logservice.model.StorageStats;
import com.example.logservice.service.LogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 存储状态接口 */
@RestController
@RequestMapping("/api/storage")
public class StorageController {

    private final LogService logService;

    public StorageController(LogService logService) {
        this.logService = logService;
    }

    @GetMapping("/stats")
    public ApiResult<StorageStats> stats() {
        return ApiResult.ok(logService.getStats());
    }
}