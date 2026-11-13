package com.example.logservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定时任务：周期性执行过期日志清理 */
@Component
public class CleanupTask {

    private static final Logger log = LoggerFactory.getLogger(CleanupTask.class);

    private final LogService logService;

    public CleanupTask(LogService logService) {
        this.logService = logService;
    }

    // 启动 60 秒后第一次执行，之后每 log.cleanup.interval.ms 执行一次
    @Scheduled(fixedDelayString = "${log.cleanup.interval.ms:86400000}", initialDelay = 60000)
    public void cleanup() {
        try {
            int dropped = logService.cleanupExpired();
            if (dropped > 0) {
                log.info("过期清理完成，删除 {} 张过期分区表", dropped);
            }
        } catch (Exception e) {
            log.error("过期清理失败", e);
        }
    }
}