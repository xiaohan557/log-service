package com.example.logservice.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** 简易 Web 控制台：浏览器打开 / 即可查看和过滤日志 */
@Controller
public class WebConsoleController {

    @GetMapping("/")
    @ResponseBody // 直接把返回的字符串作为响应体输出，而不是当作视图名
    public String index() {
        // 返回一个内嵌 JS 的 HTML，前端 fetch 调 /api/logs 展示
        return """
                <!DOCTYPE html>
                <html><head><meta charset="UTF-8"><title>日志控制台</title>
                <style>body{font-family:monospace;margin:20px}#logs{white-space:pre-wrap}</style></head>
                <body>
                <h2>日志控制台</h2>
                <input id="kw" placeholder="关键词过滤" />
                <button onclick="load()">查询</button>
                <button onclick="loadStats()">存储统计</button>
                <pre id="logs"></pre>
                <script>
                async function load(){
                  const kw = document.getElementById('kw').value;
                  const url = '/api/logs?limit=100' + (kw ? '&keyword=' + encodeURIComponent(kw) : '');
                  const r = await fetch(url);
                  const j = await r.json();
                  document.getElementById('logs').textContent = j.data.map(x =>
                    new Date(x.ts).toLocaleString() + ' ' + x.level + ' [' + (x.tag||'') + '] ' + x.message
                  ).join('\\n');
                }
                async function loadStats(){
                  const r = await fetch('/api/storage/stats');
                  const j = await r.json();
                  document.getElementById('logs').textContent = JSON.stringify(j.data, null, 2);
                }
                load();
                </script>
                </body></html>
                """;
    }
}