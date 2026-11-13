package com.example.logservice.controller;

import com.example.logservice.common.ApiResult;
import com.example.logservice.model.LogEntry;
import com.example.logservice.model.QueryParams;
import com.example.logservice.service.LogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 日志相关接口 */
@RestController
@RequestMapping("/api/logs")
public class LogController {

    private final LogService logService;

    public LogController(LogService logService) {
        this.logService = logService;
    }

    /** 单条上报 */
    @PostMapping
    public ApiResult<LogEntry> report(@RequestBody LogEntry entry) {
        return ApiResult.ok(logService.report(entry));
    }

    /** 批量上报 */
    @PostMapping("/batch")
    public ApiResult<List<LogEntry>> reportBatch(@RequestBody List<LogEntry> entries) {
        return ApiResult.ok(logService.reportBatch(entries));
    }

    /** 按条件查询 */
    @GetMapping
    public ApiResult<List<LogEntry>> query(QueryParams params) {
        return ApiResult.ok(logService.query(params));
    }

    /** 按 id 删除 */
    @DeleteMapping("/{id}")
    public ApiResult<Map<String, Integer>> deleteById(@PathVariable String id) {
        return ApiResult.ok(Map.of("deleted", logService.deleteById(id)));
    }

    /** 按条件删除 */
    @DeleteMapping
    public ApiResult<Map<String, Integer>> deleteByCondition(QueryParams params) {
        return ApiResult.ok(Map.of("deleted", logService.deleteByCondition(params)));
    }
}