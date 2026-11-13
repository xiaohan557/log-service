package com.example.logservice.model;

public class LogEntry {
    private String id;      // 全局唯一 id，格式：<日期>-<8位随机hex>，例 20261113-3f2a9b1c
    private long ts;        // 日志产生时间，Unix 毫秒时间戳
    private String level;   // 级别：DEBUG / INFO / WARN / ERROR
    private String tag;     // 标签，可空
    private String source;  // 来源主机/应用，可空
    private String message; // 日志正文

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public long getTs() { return ts; }
    public void setTs(long ts) { this.ts = ts; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
