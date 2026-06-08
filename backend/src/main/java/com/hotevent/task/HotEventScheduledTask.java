package com.hotevent.task;

import com.hotevent.service.CrawlerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class HotEventScheduledTask {

    @Autowired
    private CrawlerService crawlerService;

    @Value("${hot-event.crawler.enabled:true}")
    private boolean crawlerEnabled;

    @Scheduled(cron = "${hot-event.crawler.cron:0 */30 * * * ?}")
    public void crawlHotEvents() {
        if (!crawlerEnabled) {
            log.info("爬虫功能已禁用，跳过定时抓取");
            return;
        }

        log.info("开始执行定时热点事件抓取任务...");
        try {
            crawlerService.crawlAllSources();
            log.info("定时热点事件抓取任务执行完成");
        } catch (Exception e) {
            log.error("定时热点事件抓取任务执行失败", e);
        }
    }
}
