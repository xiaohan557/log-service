package com.example.logservice.model;

import java.util.List;

public class StorageStats {
    private String dbPath;               // 数据库文件路径
    private long dbSizeBytes;            // 文件大小（字节）
    private List<PartitionStat> partitions; // 各分区统计

    public String getDbPath() { return dbPath; }
    public void setDbPath(String dbPath) { this.dbPath = dbPath; }
    public long getDbSizeBytes() { return dbSizeBytes; }
    public void setDbSizeBytes(long dbSizeBytes) { this.dbSizeBytes = dbSizeBytes; }
    public List<PartitionStat> getPartitions() { return partitions; }
    public void setPartitions(List<PartitionStat> partitions) { this.partitions = partitions; }
}
