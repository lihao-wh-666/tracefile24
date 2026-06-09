package com.hotevent.controller;

import com.hotevent.common.Result;
import com.hotevent.entity.CrawlRecord;
import com.hotevent.service.CrawlerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/crawler")
public class CrawlerController {

    @Autowired
    private CrawlerService crawlerService;

    @PostMapping("/crawl-all")
    public Result<String> crawlAllSources() {
        try {
            crawlerService.crawlAllSources();
            return Result.success("抓取任务已启动");
        } catch (Exception e) {
            return Result.error("抓取失败: " + e.getMessage());
        }
    }

    @PostMapping("/crawl/{source}")
    public Result<CrawlRecord> crawlSource(@PathVariable String source) {
        try {
            CrawlRecord record = crawlerService.crawlSourceByName(source);
            return Result.success(record);
        } catch (Exception e) {
            return Result.error("抓取失败: " + e.getMessage());
        }
    }

    @GetMapping("/sources")
    public Result<List<String>> getAvailableSources() {
        List<String> sources = crawlerService.getAvailableSources();
        return Result.success(sources);
    }
}
