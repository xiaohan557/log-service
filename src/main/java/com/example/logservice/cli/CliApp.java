package com.example.logservice.cli;

import com.example.logservice.agent.Agent;
import com.example.logservice.model.LogEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 命令行工具：report / query / storage / agent */
public class CliApp {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    public static void main(String[] args) {
        if (args.length == 0) { printUsage(); return; }
        String cmd = args[0];
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        try {
            switch (cmd) {
                case "report"  -> report(rest);
                case "query"   -> query(rest);
                case "storage" -> storage(rest);
                case "agent"   -> Agent.run(rest);
                default        -> printUsage();
            }
        } catch (Exception e) {
            System.err.println("[错误] " + e.getMessage());
        }
    }

    // ---------- report ----------

    private static void report(String[] args) throws Exception {
        String server = arg(args, "--server", "http://localhost:8080");
        int count = Integer.parseInt(arg(args, "--count", "10"));
        String level = arg(args, "--level", "INFO");
        String tag = arg(args, "--tag", "demo");
        String source = arg(args, "--source", "cli");
        String message = arg(args, "--message", "这是一条测试日志");

        List<LogEntry> logs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LogEntry e = new LogEntry();
            e.setTs(System.currentTimeMillis());
            e.setLevel(level);
            e.setTag(tag);
            e.setSource(source);
            e.setMessage(message + " #" + i);
            logs.add(e);
        }
        HttpResponse<String> resp = post(server + "/api/logs/batch", MAPPER.writeValueAsString(logs));
        System.out.println("已上报 " + count + " 条日志，HTTP " + resp.statusCode());
        System.out.println(pretty(resp.body()));
    }

    // ---------- query ----------

    private static void query(String[] args) throws Exception {
        String server = arg(args, "--server", "http://localhost:8080");
        String url = buildQuery(server + "/api/logs",
                "level", arg(args, "--level", null),
                "tag", arg(args, "--tag", null),
                "keyword", arg(args, "--keyword", null),
                "startTs", arg(args, "--start", null),
                "endTs", arg(args, "--end", null),
                "limit", arg(args, "--limit", "50"));
        JsonNode data = MAPPER.readTree(get(url).body()).get("data");
        System.out.println("共 " + data.size() + " 条日志：");
        for (JsonNode n : data) {
            String ts = fmtTs(n.get("ts").asLong());
            String level = n.get("level").asText();
            String tag = n.get("tag") == null || n.get("tag").isNull() ? "" : n.get("tag").asText();
            String msg = n.get("message").asText();
            System.out.println(ts + "  " + pad(level) + "  [" + tag + "]  " + msg);
        }
    }

    // ---------- storage ----------

    private static void storage(String[] args) throws Exception {
        String server = arg(args, "--server", "http://localhost:8080");
        System.out.println(pretty(get(server + "/api/storage/stats").body()));
    }

    // ---------- 工具方法 ----------

    private static String buildQuery(String base, String... kv) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < kv.length; i += 2) {
            String k = kv[i], v = kv[i + 1];
            if (v != null && !v.isBlank()) {
                parts.add(k + "=" + URLEncoder.encode(v, StandardCharsets.UTF_8));
            }
        }
        return parts.isEmpty() ? base : base + "?" + String.join("&", parts);
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

    private static String pretty(String json) throws Exception {
        return MAPPER.readTree(json).toPrettyString();
    }

    private static String fmtTs(long ts) {
        return Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static String pad(String s) {
        return String.format("%-5s", s);
    }

    private static void printUsage() {
        System.out.println("""
                用法: mvn exec:java -Dexec.mainClass=com.example.logservice.cli.CliApp -Dexec.args="<命令> [参数]"

                命令:
                  report   上报测试日志    --server URL --count N --level X --tag T --source S --message M
                  query    查询日志        --server URL --level X --tag T --keyword K --start TS --end TS --limit N
                  storage  查看存储状态    --server URL
                  agent    启动采集 Agent  --mode push|pull --server URL --file F --interval MS --level X --tag T
                """);
    }
}