package com.example.logservice.agent;

import com.example.logservice.model.LogEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** 采集 Agent：支持 push（推送）和 pull（拉取）两种模式 */
public class Agent {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    public static void run(String[] args) throws Exception {
        String mode = arg(args, "--mode", "push");
        String server = arg(args, "--server", "http://localhost:8080");
        String file = arg(args, "--file", null);
        long interval = Long.parseLong(arg(args, "--interval", "5000"));
        String level = arg(args, "--level", "INFO");
        String tag = arg(args, "--tag", "agent");

        if ("pull".equals(mode)) {
            pullLoop(server, file == null ? "data/pulled.logs" : file, interval);
        } else {
            push(server, file, interval, level, tag);
        }
    }

    // ---------- push ----------

    private static void push(String server, String file, long interval, String level, String tag) throws Exception {
        System.out.println("Agent 推送模式：server=" + server);
        // 指定了文件：读文件内容一次性推送后退出
        if (file != null) {
            List<LogEntry> batch = readFileBatch(file, level, tag);
            HttpResponse<String> resp = post(server + "/api/logs/batch", MAPPER.writeValueAsString(batch));
            System.out.println("从文件 " + file + " 推送 " + batch.size() + " 条 -> HTTP " + resp.statusCode());
            return;
        }
        // 没指定文件：持续生成随机日志推送
        while (true) {
            List<LogEntry> batch = generateBatch(5, level, tag);
            HttpResponse<String> resp = post(server + "/api/logs/batch", MAPPER.writeValueAsString(batch));
            System.out.println("推送 " + batch.size() + " 条 -> HTTP " + resp.statusCode());
            Thread.sleep(interval);
        }
    }

    // ---------- pull ----------

    private static void pullLoop(String server, String outFile, long interval) throws Exception {
        Path out = Paths.get(outFile).toAbsolutePath();
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        System.out.println("Agent 拉取模式：server=" + server + "，输出到 " + out);
        long lastTs = System.currentTimeMillis() - 60_000L; // 从最近 1 分钟开始拉
        while (true) {
            String url = server + "/api/logs?startTs=" + lastTs + "&limit=100";
            JsonNode data = MAPPER.readTree(get(url).body()).get("data");
            List<String> lines = new ArrayList<>();
            for (JsonNode n : data) {
                long ts = n.get("ts").asLong();
                lastTs = Math.max(lastTs, ts);
                String tag = n.get("tag") == null || n.get("tag").isNull() ? "" : n.get("tag").asText();
                lines.add(n.get("level").asText() + " [" + tag + "] " + n.get("message").asText());
            }
            if (!lines.isEmpty()) {
                Files.write(out, lines, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                System.out.println("拉取并写入 " + lines.size() + " 条到 " + out);
            } else {
                System.out.println("暂无新日志，等待...");
            }
            Thread.sleep(interval);
        }
    }

    // ---------- 工具方法 ----------

    private static List<LogEntry> generateBatch(int n, String level, String tag) {
        String[] samples = {"用户登录成功", "查询订单列表", "数据库连接池告警", "支付回调处理", "缓存未命中", "定时任务执行"};
        Random r = new Random();
        List<LogEntry> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            LogEntry e = new LogEntry();
            e.setTs(System.currentTimeMillis());
            e.setLevel(level);
            e.setTag(tag);
            e.setSource("agent-" + (r.nextInt(3) + 1));
            e.setMessage(samples[r.nextInt(samples.length)] + " #" + r.nextInt(10000));
            list.add(e);
        }
        return list;
    }

    private static List<LogEntry> readFileBatch(String file, String level, String tag) throws Exception {
        List<LogEntry> list = new ArrayList<>();
        for (String line : Files.readAllLines(Paths.get(file))) {
            if (line.isBlank()) continue;
            LogEntry e = new LogEntry();
            e.setTs(System.currentTimeMillis());
            e.setLevel(level);
            e.setTag(tag);
            e.setSource("file");
            e.setMessage(line);
            list.add(e);
        }
        return list;
    }

    private static String arg(String[] args, String key, String def) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(key)) return args[i + 1];
        }
        return def;
    }

    private static HttpResponse<String> get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10)).GET().build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(String url, String json) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }
}