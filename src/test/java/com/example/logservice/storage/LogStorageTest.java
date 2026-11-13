package com.example.logservice.storage;

import com.example.logservice.model.LogEntry;
import com.example.logservice.model.QueryParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LogStorageTest {

    @TempDir
    Path tmp; // JUnit 自动创建临时目录，测试结束自动删除

    private LogStorage newStorage(String name, int retentionDays) {
        return new LogStorage(tmp.resolve(name).toString(), retentionDays);
    }

    private LogEntry entry(String level, String tag, String msg, long ts) {
        LogEntry e = new LogEntry();
        e.setTs(ts);
        e.setLevel(level);
        e.setTag(tag);
        e.setSource("test");
        e.setMessage(msg);
        return e;
    }

    /** 1. 日志写入 + 查询 */
    @Test
    void testInsertAndQuery() {
        LogStorage s = newStorage("t1.db", 30);
        s.insert(entry("INFO", "app", "hello", System.currentTimeMillis()));
        s.insert(entry("ERROR", "app", "boom", System.currentTimeMillis()));

        assertEquals(2, s.query(new QueryParams()).size());
    }

    /** 2. 过期清理 */
    @Test
    void testCleanupExpired() {
        LogStorage s = newStorage("t2.db", 7); // 保留 7 天
        long oldTs = System.currentTimeMillis() - 10L * 24 * 3600 * 1000; // 10 天前
        s.insert(entry("INFO", "old", "old log", oldTs));
        s.insert(entry("INFO", "new", "new log", System.currentTimeMillis()));

        assertTrue(s.cleanupExpired() >= 1);

        List<LogEntry> all = s.query(new QueryParams());
        assertEquals(1, all.size());
        assertEquals("new log", all.get(0).getMessage());
    }

    /** 3. 多并发上报 */
    @Test
    void testConcurrentInsert() throws InterruptedException {
        LogStorage s = newStorage("t3.db", 30);
        int threads = 8, perThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger();

        for (int t = 0; t < threads; t++) {
            final int tid = t;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < perThread; i++) {
                        s.insert(entry("INFO", "tag" + tid, "msg-" + tid + "-" + i, System.currentTimeMillis()));
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        pool.shutdown();

        assertEquals(0, errors.get());
        QueryParams q = new QueryParams();
        q.setLimit(threads * perThread); // 默认 limit=100 会截断结果，需显式放大
        assertEquals(threads * perThread, s.query(q).size());
    }

    /** 4. 条件查询 */
    @Test
    void testConditionalQuery() {
        LogStorage s = newStorage("t4.db", 30);
        long now = System.currentTimeMillis();
        long hour = 3600_000L;
        s.insert(entry("ERROR", "order", "支付失败", now));
        s.insert(entry("ERROR", "order", "库存不足", now - hour));
        s.insert(entry("INFO", "user", "登录成功", now - 2 * hour));

        QueryParams q1 = new QueryParams();
        q1.setLevel("ERROR");
        assertEquals(2, s.query(q1).size());

        QueryParams q2 = new QueryParams();
        q2.setTag("order");
        assertEquals(2, s.query(q2).size());

        QueryParams q3 = new QueryParams();
        q3.setKeyword("支付");
        assertEquals(1, s.query(q3).size());

        QueryParams q4 = new QueryParams();
        q4.setStartTs(now - hour);
        assertEquals(2, s.query(q4).size());
    }

    /** 5. 删除 */
    @Test
    void testDelete() {
        LogStorage s = newStorage("t5.db", 30);
        LogEntry e = s.insert(entry("INFO", "x", "to delete", System.currentTimeMillis()));
        assertEquals(1, s.deleteById(e.getId()));
        assertEquals(0, s.query(new QueryParams()).size());
    }
}