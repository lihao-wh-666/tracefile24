package com.hotevent.task;

import com.hotevent.service.CrawlerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class HotEventScheduledTask {

    @Autowired
    private CrawlerService crawlerService;

    public void crawlHotEvents() {
        log.info("手动触发热点事件抓取任务...");
        try {
            crawlerService.crawlAllSources();
            log.info("热点事件抓取任务执行完成");
        } catch (Exception e) {
            log.error("热点事件抓取任务执行失败", e);
        }
    }
}
