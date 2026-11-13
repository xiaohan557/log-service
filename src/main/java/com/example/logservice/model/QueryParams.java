package com.example.logservice.model;

public class QueryParams {
    private String level;   // 级别精确匹配
    private String tag;     // 标签精确匹配
    private String keyword; // 正文模糊关键词
    private Long startTs;   // 起始时间戳（含）
    private Long endTs;     // 结束时间戳（含）
    private int limit = 100; // 最多返回条数
    private int offset = 0;  // 偏移量

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public Long getStartTs() { return startTs; }
    public void setStartTs(Long startTs) { this.startTs = startTs; }
    public Long getEndTs() { return endTs; }
    public void setEndTs(Long endTs) { this.endTs = endTs; }
    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = limit; }
    public int getOffset() { return offset; }
    public void setOffset(int offset) { this.offset = offset; }
}
