package com.hotevent.controller;

import com.hotevent.common.Result;
import com.hotevent.entity.CrawlRecord;
import com.hotevent.service.CrawlerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/crawler")
public class CrawlerController {

    @Autowired
    private CrawlerService crawlerService;

    @PostMapping("/crawl-all")
    public Result<String> crawlAllSources() {
        try {
            log.info("收到手动全量抓取请求");
            crawlerService.crawlAllSources();
            return Result.success("抓取任务已完成");
        } catch (Exception e) {
            log.error("全量抓取失败", e);
            return Result.error("抓取失败: " + e.getMessage());
        }
    }

    @PostMapping("/crawl/{source}")
    public Result<CrawlRecord> crawlSource(@PathVariable String source) {
        try {
            log.info("收到手动抓取请求: 数据源={}", source);
            CrawlRecord record = crawlerService.crawlSourceByName(source);
            return Result.success(record);
        } catch (Exception e) {
            log.error("抓取数据源 [{}] 失败", source, e);
            return Result.error("抓取失败: " + e.getMessage());
        }
    }

    @GetMapping("/sources")
    public Result<List<String>> getAvailableSources() {
        List<String> sources = crawlerService.getAvailableSources();
        return Result.success(sources);
    }

    @GetMapping("/status")
    public Result<Map<String, Object>> getCrawlerStatus() {
        try {
            Map<String, Object> status = crawlerService.getCrawlerStatus();
            return Result.success(status);
        } catch (Exception e) {
            log.error("获取爬虫状态失败", e);
            return Result.error("获取状态失败: " + e.getMessage());
        }
    }
}
