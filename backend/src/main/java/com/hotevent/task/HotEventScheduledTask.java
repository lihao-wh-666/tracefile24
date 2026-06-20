package com.hotevent.task;

import com.hotevent.service.CrawlerService;
import com.hotevent.service.HotEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class HotEventScheduledTask {

    @Autowired
    private CrawlerService crawlerService;

    @Autowired
    private HotEventService hotEventService;

    @Value("${hot-event.crawler.scheduled-task-enabled:false}")
    private boolean scheduledTaskEnabled;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    @Scheduled(cron = "${hot-event.crawler.cron:0 */30 * * * ?}")
    public void scheduledCrawlHotEvents() {
        if (!scheduledTaskEnabled) {
            log.debug("Scheduled定时抓取任务未启用，跳过");
            return;
        }

        if (!isRunning.compareAndSet(false, true)) {
            log.warn("上一次定时抓取任务尚未完成，跳过本次执行");
            return;
        }

        log.info("========== 开始执行定时热点事件抓取任务 ==========");
        long startTime = System.currentTimeMillis();
        try {
            crawlerService.crawlAllSources();

            log.info("抓取完成，开始执行排名重排...");
            int rerankCount = hotEventService.rerankAllSources();
            log.info("排名重排完成，共更新{}条记录的排名", rerankCount);

            long costTime = System.currentTimeMillis() - startTime;
            log.info("========== 定时热点事件抓取任务执行完成，耗时{}ms ==========", costTime);
        } catch (Exception e) {
            log.error("定时热点事件抓取任务执行失败", e);
        } finally {
            isRunning.set(false);
        }
    }

    public void crawlHotEvents() {
        log.info("手动触发热点事件抓取任务...");
        try {
            crawlerService.crawlAllSources();

            log.info("抓取完成，开始执行排名重排...");
            int rerankCount = hotEventService.rerankAllSources();
            log.info("排名重排完成，共更新{}条记录的排名", rerankCount);

            log.info("热点事件抓取任务执行完成");
        } catch (Exception e) {
            log.error("热点事件抓取任务执行失败", e);
        }
    }

    public boolean isRunning() {
        return isRunning.get();
    }
}
