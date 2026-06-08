package com.hotevent.config;

import com.hotevent.service.CrawlerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private CrawlerService crawlerService;

    @Override
    public void run(String... args) {
        log.info("应用启动，开始执行初始数据抓取...");
        try {
            crawlerService.crawlAllSources();
            log.info("初始数据抓取完成");
        } catch (Exception e) {
            log.error("初始数据抓取失败", e);
        }
    }
}
