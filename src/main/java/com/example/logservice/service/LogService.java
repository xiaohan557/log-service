package com.example.logservice.service;

import com.example.logservice.model.LogEntry;
import com.example.logservice.model.QueryParams;
import com.example.logservice.model.StorageStats;
import com.example.logservice.storage.LogStorage;
import org.springframework.stereotype.Service;

import java.util.List;

/** 业务服务层：负责业务逻辑，具体存储交给 LogStorage */
@Service
public class LogService {

    private final LogStorage storage;

    public LogService(LogStorage storage) {
        this.storage = storage;
    }

    public LogEntry report(LogEntry entry) {
        return storage.insert(entry);
    }

    public List<LogEntry> reportBatch(List<LogEntry> entries) {
        if (entries == null || entries.isEmpty()) return List.of();
        return storage.insertBatch(entries);
    }

    public List<LogEntry> query(QueryParams params) {
        return storage.query(params);
    }

    public int deleteById(String id) {
        return storage.deleteById(id);
    }

    public int deleteByCondition(QueryParams params) {
        return storage.deleteByCondition(params);
    }

    public int cleanupExpired() {
        return storage.cleanupExpired();
    }

    public StorageStats getStats() {
        return storage.getStats();
    }
}