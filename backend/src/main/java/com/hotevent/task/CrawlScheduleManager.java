package com.hotevent.task;

import com.hotevent.service.CrawlerService;
import com.hotevent.service.HotEventService;
import com.hotevent.service.SysConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class CrawlScheduleManager {

    @Autowired
    private CrawlerService crawlerService;

    @Autowired
    private SysConfigService sysConfigService;

    @Autowired
    private HotEventService hotEventService;

    @Value("${hot-event.crawler.enabled:true}")
    private boolean crawlerEnabled;

    private ThreadPoolTaskScheduler taskScheduler;
    private ScheduledFuture<?> scheduledFuture;
    private int currentIntervalMinutes;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    @PostConstruct
    public void init() {
        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix("crawl-scheduler-");
        taskScheduler.setDaemon(true);
        taskScheduler.initialize();

        if (crawlerEnabled) {
            currentIntervalMinutes = sysConfigService.getCrawlIntervalMinutes();
            log.info("初始化定时抓取任务，间隔: {}分钟", currentIntervalMinutes);
            scheduleCrawlTask();
        } else {
            log.info("爬虫功能已禁用，不启动定时抓取");
        }
    }

    private void scheduleCrawlTask() {
        if (scheduledFuture != null && !scheduledFuture.isDone()) {
            scheduledFuture.cancel(false);
            log.info("已取消旧的定时抓取任务");
        }

        long intervalMs = TimeUnit.MINUTES.toMillis(currentIntervalMinutes);
        scheduledFuture = taskScheduler.scheduleAtFixedRate(() -> {
            if (!isRunning.compareAndSet(false, true)) {
                log.warn("上一次定时抓取任务尚未完成，跳过本次执行");
                return;
            }

            try {
                log.info("========== 开始执行定时热点事件抓取任务 ==========");
                long startTime = System.currentTimeMillis();

                crawlerService.crawlAllSources();

                log.info("抓取完成，开始执行全平台排名重排...");
                int rerankCount = hotEventService.rerankAllSources();
                log.info("排名重排完成，共更新{}条记录的排名", rerankCount);

                long costTime = System.currentTimeMillis() - startTime;
                log.info("========== 定时热点事件抓取任务执行完成，耗时{}ms ==========", costTime);
            } catch (Exception e) {
                log.error("定时热点事件抓取任务执行失败", e);
            } finally {
                isRunning.set(false);
            }
        }, new Date(System.currentTimeMillis() + intervalMs), intervalMs);

        log.info("已调度定时抓取任务，间隔: {}分钟", currentIntervalMinutes);
    }

    public synchronized void reschedule() {
        if (!crawlerEnabled) {
            log.info("爬虫功能已禁用，跳过重新调度");
            return;
        }

        int newInterval = sysConfigService.getCrawlIntervalMinutes();
        if (newInterval == currentIntervalMinutes) {
            log.info("抓取间隔未变化 ({}分钟)，无需重新调度", currentIntervalMinutes);
            return;
        }

        log.info("抓取间隔变化: {}分钟 -> {}分钟，重新调度定时任务", currentIntervalMinutes, newInterval);
        currentIntervalMinutes = newInterval;
        scheduleCrawlTask();
    }

    public int getCurrentIntervalMinutes() {
        return currentIntervalMinutes;
    }

    @PreDestroy
    public void destroy() {
        if (scheduledFuture != null && !scheduledFuture.isDone()) {
            scheduledFuture.cancel(false);
        }
        if (taskScheduler != null) {
            taskScheduler.shutdown();
        }
        log.info("定时抓取调度器已关闭");
    }
}
