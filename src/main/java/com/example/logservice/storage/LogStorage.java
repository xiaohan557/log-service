package com.example.logservice.storage;

import com.example.logservice.model.LogEntry;
import com.example.logservice.model.PartitionStat;
import com.example.logservice.model.QueryParams;
import com.example.logservice.model.StorageStats;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * SQLite 存储层：按天分区的日志表。
 * 分区表命名：logs_yyyyMMdd
 */
@Component
public class LogStorage {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd

    private final String dbPath;
    private final int retentionDays;
    private final String url;

    public LogStorage(@Value("${log.db.path}") String dbPath,
                      @Value("${log.retention.days}") int retentionDays) {
        this.dbPath = dbPath;
        this.retentionDays = retentionDays;
        this.url = "jdbc:sqlite:" + dbPath;
        initOnce();
    }

    // ==================== 初始化 ====================

    private void initOnce() {
        // 确保数据目录存在
        File f = new File(dbPath);
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (Connection c = connect()) {
            try (Statement st = c.createStatement()) {
                // WAL 模式：提升并发、崩溃可恢复
                st.execute("PRAGMA journal_mode=WAL;");
                st.execute("PRAGMA synchronous=NORMAL;");
            }
        } catch (SQLException e) {
            throw new RuntimeException("初始化数据库失败", e);
        }
        // 确保今天的分区表存在
        ensurePartition(today());
    }

    /** 每次连接都设置 busy_timeout，避免并发写时直接报错 */
    private Connection connect() throws SQLException {
        Connection c = DriverManager.getConnection(url);
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA busy_timeout=5000;");
        }
        return c;
    }

    private String today() {
        return LocalDate.now().format(DATE_FMT);
    }

    /** 根据时间戳算出它属于哪一天（本地时区） */
    private String partitionOf(long ts) {
        return Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault())
                .toLocalDate().format(DATE_FMT);
    }

    // ==================== 建表 ====================

    private void ensurePartition(String date) {
        String table = "logs_" + date;
        String ddl = "CREATE TABLE IF NOT EXISTS " + table + " (" +
                "id TEXT PRIMARY KEY, " +
                "ts INTEGER NOT NULL, " +
                "level TEXT NOT NULL, " +
                "tag TEXT, " +
                "source TEXT, " +
                "message TEXT NOT NULL)";
        String idxTs = "CREATE INDEX IF NOT EXISTS idx_" + table + "_ts ON " + table + "(ts)";
        String idxLevel = "CREATE INDEX IF NOT EXISTS idx_" + table + "_level ON " + table + "(level)";
        String idxTag = "CREATE INDEX IF NOT EXISTS idx_" + table + "_tag ON " + table + "(tag)";
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute(ddl);
            st.execute(idxTs);
            st.execute(idxLevel);
            st.execute(idxTag);
        } catch (SQLException e) {
            throw new RuntimeException("创建分区表失败: " + table, e);
        }
    }

    // ==================== 写入 ====================

    public LogEntry insert(LogEntry entry) {
        fillDefaults(entry);
        String date = partitionOf(entry.getTs());
        ensurePartition(date);
        entry.setId(date + "-" + randomHex(8));
        String sql = "INSERT INTO " + "logs_" + date + " (id, ts, level, tag, source, message) VALUES (?,?,?,?,?,?)";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, entry.getId());
            ps.setLong(2, entry.getTs());
            ps.setString(3, entry.getLevel());
            ps.setString(4, entry.getTag());
            ps.setString(5, entry.getSource());
            ps.setString(6, entry.getMessage());
            ps.executeUpdate();
            return entry;
        } catch (SQLException e) {
            throw new RuntimeException("写入日志失败", e);
        }
    }

    /** 批量写入：按分区分组，每个分区一个事务，保证整批要么全成功要么全失败 */
    public List<LogEntry> insertBatch(List<LogEntry> entries) {
        Map<String, List<LogEntry>> grouped = new LinkedHashMap<>();
        for (LogEntry e : entries) {
            fillDefaults(e);
            String date = partitionOf(e.getTs());
            ensurePartition(date);
            e.setId(date + "-" + randomHex(8));
            grouped.computeIfAbsent(date, k -> new ArrayList<>()).add(e);
        }
        for (Map.Entry<String, List<LogEntry>> g : grouped.entrySet()) {
            String sql = "INSERT INTO " + "logs_" + g.getKey() + " (id, ts, level, tag, source, message) VALUES (?,?,?,?,?,?)";
            try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
                c.setAutoCommit(false);
                for (LogEntry e : g.getValue()) {
                    ps.setString(1, e.getId());
                    ps.setLong(2, e.getTs());
                    ps.setString(3, e.getLevel());
                    ps.setString(4, e.getTag());
                    ps.setString(5, e.getSource());
                    ps.setString(6, e.getMessage());
                    ps.addBatch();
                }
                ps.executeBatch();
                c.commit();
            } catch (SQLException e) {
                throw new RuntimeException("批量写入日志失败", e);
            }
        }
        return entries;
    }

    private void fillDefaults(LogEntry e) {
        if (e.getTs() <= 0) e.setTs(System.currentTimeMillis());
        if (e.getLevel() == null || e.getLevel().isBlank()) e.setLevel("INFO");
    }

    private static String randomHex(int len) {
        String hex = "0123456789abcdef";
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append(hex.charAt(r.nextInt(16)));
        return sb.toString();
    }

    // ==================== 查询 ====================

    public List<LogEntry> query(QueryParams q) {
        if (q == null) q = new QueryParams();
        List<LogEntry> all = new ArrayList<>();
        for (String table : partitionTablesInRange(q.getStartTs(), q.getEndTs())) {
            StringBuilder sql = new StringBuilder("SELECT id, ts, level, tag, source, message FROM " + table + " WHERE 1=1");
            List<Object> params = new ArrayList<>();
            appendConditions(sql, params, q);
            sql.append(" ORDER BY ts DESC");
            try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) all.add(mapRow(rs));
                }
            } catch (SQLException e) {
                throw new RuntimeException("查询日志失败", e);
            }
        }
        // 全局按时间倒序排序，再应用 offset/limit
        all.sort(Comparator.comparingLong(LogEntry::getTs).reversed());
        int from = Math.min(q.getOffset(), all.size());
        int to = Math.min(from + q.getLimit(), all.size());
        return new ArrayList<>(all.subList(from, to));
    }

    // ==================== 删除 ====================

    /** 按 id 删除：id 前 8 位是日期，能直接定位到分区表 */
    public int deleteById(String id) {
        if (id == null || id.length() < 9 || id.charAt(8) != '-') return 0;
        String table = "logs_" + id.substring(0, 8);
        if (!tableExists(table)) return 0;
        String sql = "DELETE FROM " + table + " WHERE id = ?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("删除日志失败", e);
        }
    }

    /** 按条件删除，返回删除条数 */
    public int deleteByCondition(QueryParams q) {
        if (q == null) q = new QueryParams();
        int deleted = 0;
        for (String table : partitionTablesInRange(q.getStartTs(), q.getEndTs())) {
            StringBuilder sql = new StringBuilder("DELETE FROM " + table + " WHERE 1=1");
            List<Object> params = new ArrayList<>();
            appendConditions(sql, params, q);
            try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
                deleted += ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("删除日志失败", e);
            }
        }
        return deleted;
    }

    // ==================== 过期清理 ====================

    /** 删除所有日期早于 (今天 - retentionDays) 的分区表，返回删除的表数量 */
    public int cleanupExpired() {
        LocalDate cutoff = LocalDate.now().minusDays(retentionDays);
        int dropped = 0;
        for (String table : listPartitionTables()) {
            LocalDate d = parseTableDate(table);
            if (d != null && d.isBefore(cutoff)) {
                dropTable(table);
                dropped++;
            }
        }
        return dropped;
    }

    private void dropTable(String table) {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("DROP TABLE IF EXISTS " + table);
        } catch (SQLException e) {
            throw new RuntimeException("删除分区表失败: " + table, e);
        }
    }

    // ==================== 存储状态 ====================

    public StorageStats getStats() {
        StorageStats stats = new StorageStats();
        stats.setDbPath(dbPath);
        File f = new File(dbPath);
        if (f.exists()) stats.setDbSizeBytes(f.length());
        List<PartitionStat> parts = new ArrayList<>();
        for (String table : listPartitionTables()) {
            PartitionStat p = new PartitionStat();
            p.setTable(table);
            p.setRows(countRows(table));
            parts.add(p);
        }
        stats.setPartitions(parts);
        return stats;
    }

    // ==================== 私有工具 ====================

    private void appendConditions(StringBuilder sql, List<Object> params, QueryParams q) {
        if (q.getLevel() != null && !q.getLevel().isBlank()) { sql.append(" AND level = ?"); params.add(q.getLevel()); }
        if (q.getTag() != null && !q.getTag().isBlank()) { sql.append(" AND tag = ?"); params.add(q.getTag()); }
        if (q.getStartTs() != null) { sql.append(" AND ts >= ?"); params.add(q.getStartTs()); }
        if (q.getEndTs() != null) { sql.append(" AND ts <= ?"); params.add(q.getEndTs()); }
        if (q.getKeyword() != null && !q.getKeyword().isBlank()) { sql.append(" AND message LIKE ?"); params.add("%" + q.getKeyword() + "%"); }
    }

    /** 列出所有 logs_ 开头的分区表 */
    private List<String> listPartitionTables() {
        List<String> tables = new ArrayList<>();
        String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'logs_%'";
        try (Connection c = connect(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) tables.add(rs.getString(1));
        } catch (SQLException e) {
            throw new RuntimeException("读取分区表列表失败", e);
        }
        return tables;
    }

    /** 时间范围对应的分区表；无时间条件则返回所有分区表 */
    private List<String> partitionTablesInRange(Long startTs, Long endTs) {
        if (startTs == null && endTs == null) return listPartitionTables();
        LocalDate s = startTs != null ? LocalDate.ofInstant(Instant.ofEpochMilli(startTs), ZoneId.systemDefault()) : null;
        LocalDate e = endTs != null ? LocalDate.ofInstant(Instant.ofEpochMilli(endTs), ZoneId.systemDefault()) : null;
        List<String> result = new ArrayList<>();
        for (String table : listPartitionTables()) {
            LocalDate d = parseTableDate(table);
            if (d == null) continue;
            if (s != null && d.isBefore(s)) continue;
            if (e != null && d.isAfter(e)) continue;
            result.add(table);
        }
        return result;
    }

    private LocalDate parseTableDate(String table) {
        try {
            return LocalDate.parse(table.substring("logs_".length()), DATE_FMT);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean tableExists(String table) {
        String sql = "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private long countRows(String table) {
        String sql = "SELECT COUNT(*) FROM " + table;
        try (Connection c = connect(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private LogEntry mapRow(ResultSet rs) throws SQLException {
        LogEntry e = new LogEntry();
        e.setId(rs.getString("id"));
        e.setTs(rs.getLong("ts"));
        e.setLevel(rs.getString("level"));
        e.setTag(rs.getString("tag"));
        e.setSource(rs.getString("source"));
        e.setMessage(rs.getString("message"));
        return e;
    }
}