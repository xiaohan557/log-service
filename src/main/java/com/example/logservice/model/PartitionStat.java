package com.example.logservice.model;

public class PartitionStat {
    private String table; // 表名，如 logs_20261113
    private long rows;    // 行数

    public String getTable() { return table; }
    public void setTable(String table) { this.table = table; }
    public long getRows() { return rows; }
    public void setRows(long rows) { this.rows = rows; }
}
