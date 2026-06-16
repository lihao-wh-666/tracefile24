package com.hotevent.controller;

import com.hotevent.common.Result;
import com.hotevent.entity.CrawlRecord;
import com.hotevent.service.CrawlerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/crawler")
public class CrawlerController {

    @Autowired
    private CrawlerService crawlerService;

    @PostMapping("/crawl-all")
    public Result<String> crawlAllSources(
            @RequestParam(defaultValue = "false") boolean async) {
        try {
            log.info("收到手动全量抓取请求, async={}", async);
            if (async) {
                CompletableFuture<Map<String, Object>> future = crawlerService.crawlAllSourcesAsync();
                future.thenAccept(result -> {
                    log.info("异步全量抓取任务完成: cost={}ms, success={}, failed={}",
                            result.get("totalCostTimeMs"),
                            result.get("successSources"),
                            result.get("failedSources"));
                }).exceptionally(ex -> {
                    log.error("异步全量抓取任务异常", ex);
                    return null;
                });
                return Result.success("异步抓取任务已提交，后台执行中");
            } else {
                crawlerService.crawlAllSources();
                return Result.success("抓取任务已完成");
            }
        } catch (Exception e) {
            log.error("全量抓取失败", e);
            return Result.error("抓取失败: " + e.getMessage());
        }
    }

    @PostMapping("/crawl/{source}")
    public Result<Object> crawlSource(@PathVariable String source,
                                       @RequestParam(defaultValue = "false") boolean async) {
        try {
            log.info("收到手动抓取请求: 数据源={}, async={}", source, async);
            if (async) {
                CompletableFuture<CrawlRecord> future = crawlerService.crawlSourceAsyncByName(source);
                future.thenAccept(record -> {
                    log.info("异步抓取任务完成: 数据源={}, 状态={}, 耗时={}ms",
                            source, record.getStatus(), record.getCostTimeMs());
                }).exceptionally(ex -> {
                    log.error("异步抓取任务异常: 数据源={}", source, ex);
                    return null;
                });
                Map<String, Object> resp = new java.util.HashMap<>();
                resp.put("message", "异步抓取任务已提交");
                resp.put("source", source);
                resp.put("async", true);
                return Result.success(resp);
            } else {
                CrawlRecord record = crawlerService.crawlSourceByName(source);
                return Result.success(record);
            }
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
