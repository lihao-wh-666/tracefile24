package com.hotevent.task;

import com.hotevent.service.LogArchiveService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LogArchiveScheduledTask {

    @Autowired
    private LogArchiveService logArchiveService;

    @Value("${hot-event.log-archive.enabled:true}")
    private boolean archiveEnabled;

    @Value("${hot-event.log-archive.auto-archive-enabled:true}")
    private boolean autoArchiveEnabled;

    @Scheduled(cron = "${hot-event.log-archive.cron:0 0 2 * * ?}")
    public void autoArchiveLogs() {
        if (!archiveEnabled || !autoArchiveEnabled) {
            log.debug("日志自动归档未启用，跳过");
            return;
        }

        log.info("开始执行定时日志归档任务...");
        try {
            logArchiveService.executeAutoArchive();
            log.info("定时日志归档任务执行完成");
        } catch (Exception e) {
            log.error("定时日志归档任务执行失败", e);
        }
    }
}
