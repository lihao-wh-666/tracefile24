package com.hotevent.controller;

import com.hotevent.common.Result;
import com.hotevent.crawler.compliance.RobotsComplianceManager;
import com.hotevent.crawler.core.CrawlTask;
import com.hotevent.crawler.datasource.CrawlScheduler;
import com.hotevent.crawler.datasource.DataSourceManager;
import com.hotevent.crawler.monitor.CrawlMonitor;
import com.hotevent.crawler.storage.IncrementalCrawlManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/multi-crawler")
public class MultiPlatformCrawlerController {

    @Autowired
    private CrawlScheduler crawlScheduler;

    @Autowired
    private DataSourceManager dataSourceManager;

    @Autowired
    private CrawlMonitor crawlMonitor;

    @Autowired
    private IncrementalCrawlManager incrementalManager;

    @Autowired
    private RobotsComplianceManager robotsManager;

    @GetMapping("/sources")
    public Result<Map<String, Object>> getSources() {
        try {
            return Result.success(dataSourceManager.getSourceStatus());
        } catch (Exception e) {
            return Result.error("获取数据源列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/sources/{code}")
    public Result<Map<String, Object>> getSourceDetail(@PathVariable String code) {
        try {
            var adapter = dataSourceManager.getAdapter(code);
            var config = dataSourceManager.getConfig(code);
            if (adapter == null) {
                return Result.error("未找到数据源: " + code);
            }
            Map<String, Object> detail = new HashMap<>();
            detail.put("code", code);
            detail.put("name", adapter.getPlatformName());
            detail.put("type", adapter.getPlatformType());
            detail.put("baseUrl", adapter.getBaseUrl());
            detail.put("enabled", dataSourceManager.isSourceEnabled(code));
            detail.put("needsLogin", adapter.needsLogin());
            detail.put("loggedIn", adapter.isLoggedIn());
            detail.put("authConfig", adapter.getAuthConfig());
            detail.put("antiCrawlConfig", adapter.getAntiCrawlConfig());
            detail.put("config", config);
            return Result.success(detail);
        } catch (Exception e) {
            return Result.error("获取数据源详情失败: " + e.getMessage());
        }
    }

    @PostMapping("/sources/{code}/enable")
    public Result<String> enableSource(@PathVariable String code) {
        try {
            dataSourceManager.enableSource(code);
            return Result.success("已启用数据源: " + code);
        } catch (Exception e) {
            return Result.error("启用失败: " + e.getMessage());
        }
    }

    @PostMapping("/sources/{code}/disable")
    public Result<String> disableSource(@PathVariable String code) {
        try {
            dataSourceManager.disableSource(code);
            return Result.success("已禁用数据源: " + code);
        } catch (Exception e) {
            return Result.error("禁用失败: " + e.getMessage());
        }
    }

    @PostMapping("/sources/{code}/login")
    public Result<String> loginPlatform(@PathVariable String code, @RequestBody Map<String, String> credentials) {
        try {
            boolean success = dataSourceManager.loginPlatform(code, credentials);
            return success ? Result.success("登录成功") : Result.error("登录失败");
        } catch (Exception e) {
            return Result.error("登录异常: " + e.getMessage());
        }
    }

    @PostMapping("/sources/{code}/logout")
    public Result<String> logoutPlatform(@PathVariable String code) {
        try {
            dataSourceManager.logoutPlatform(code);
            return Result.success("已退出登录");
        } catch (Exception e) {
            return Result.error("退出异常: " + e.getMessage());
        }
    }

    @PostMapping("/crawl/{code}")
    public Result<Object> executeCrawl(@PathVariable String code,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(defaultValue = "false") boolean async) {
        try {
            if (async) {
                CompletableFuture<CrawlTask> future = keyword != null && !keyword.isEmpty()
                        ? crawlScheduler.executeSourceAsync(code, keyword)
                        : crawlScheduler.executeSourceAsync(code);
                Map<String, Object> resp = new HashMap<>();
                resp.put("message", "异步任务已提交");
                resp.put("async", true);
                return Result.success(resp);
            } else {
                CrawlTask task = keyword != null && !keyword.isEmpty()
                        ? crawlScheduler.executeSource(code, keyword)
                        : crawlScheduler.executeSource(code);
                Map<String, Object> resp = new HashMap<>();
                resp.put("taskId", task.getTaskId());
                resp.put("platform", task.getPlatform());
                resp.put("status", task.getStatus());
                resp.put("itemsCount", task.getCollectedCount());
                resp.put("successRequests", task.getSuccessCount() != null ? task.getSuccessCount().get() : 0);
                resp.put("failRequests", task.getFailCount() != null ? task.getFailCount().get() : 0);
                resp.put("durationMs", task.getTotalDurationMs());
                return Result.success(resp);
            }
        } catch (Exception e) {
            log.error("执行采集任务异常: {}", e.getMessage(), e);
            return Result.error("采集失败: " + e.getMessage());
        }
    }

    @PostMapping("/crawl/all")
    public Result<Object> executeAllCrawl(@RequestParam(defaultValue = "true") boolean async) {
        try {
            if (async) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("message", "全平台异步采集已启动");
                return Result.success(resp);
            }
            return Result.success(crawlScheduler.executeAllSources());
        } catch (Exception e) {
            return Result.error("执行全平台采集失败: " + e.getMessage());
        }
    }

    @GetMapping("/tasks/running")
    public Result<Collection<CrawlTask>> getRunningTasks() {
        return Result.success(crawlScheduler.getAllRunningTasks());
    }

    @GetMapping("/tasks/{taskId}")
    public Result<CrawlTask> getTaskStatus(@PathVariable String taskId) {
        CrawlTask task = crawlScheduler.getRunningTask(taskId);
        if (task == null) {
            return Result.error("未找到运行中的任务: " + taskId);
        }
        return Result.success(task);
    }

    @PostMapping("/tasks/{taskId}/cancel")
    public Result<String> cancelTask(@PathVariable String taskId) {
        crawlScheduler.cancelTask(taskId);
        return Result.success("取消请求已发送");
    }

    @GetMapping("/monitor")
    public Result<Map<String, Object>> getMonitorData() {
        try {
            return Result.success(crawlMonitor.getMonitorData());
        } catch (Exception e) {
            return Result.error("获取监控数据失败: " + e.getMessage());
        }
    }

    @GetMapping("/incremental/stats")
    public Result<Map<String, Object>> getIncrementalStats() {
        return Result.success(incrementalManager.getStats());
    }

    @DeleteMapping("/incremental/{platform}")
    public Result<String> clearIncrementalState(@PathVariable String platform,
                                                @RequestParam(required = false) String keyword) {
        incrementalManager.clearState(platform, keyword);
        return Result.success("已清除增量状态");
    }

    @DeleteMapping("/incremental/all")
    public Result<String> clearAllIncrementalStates() {
        incrementalManager.clearAllStates();
        return Result.success("已清除全部增量状态");
    }

    @GetMapping("/robots/check")
    public Result<Map<String, Object>> checkRobots(@RequestParam String url,
                                                   @RequestParam(defaultValue = "default") String platform) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("url", url);
        resp.put("allowed", robotsManager.isAllowed(platform, url));
        resp.put("crawlDelaySeconds", robotsManager.getCrawlDelay(url));
        return Result.success(resp);
    }

    @GetMapping("/robots/stats")
    public Result<Map<String, Object>> getRobotsCacheStats() {
        return Result.success(robotsManager.getCacheStats());
    }

    @DeleteMapping("/robots/cache/{domain}")
    public Result<String> clearRobotsCache(@PathVariable String domain) {
        robotsManager.clearCache(domain);
        return Result.success("已清除robots缓存");
    }

    @DeleteMapping("/robots/cache/all")
    public Result<String> clearAllRobotsCache() {
        robotsManager.clearAllCache();
        return Result.success("已清除全部robots缓存");
    }

    @GetMapping("/sitemap/parse")
    public Result<Object> parseSitemap(@RequestParam String url) {
        return Result.success(robotsManager.parseSitemap(url));
    }

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> getDashboard() {
        try {
            Map<String, Object> dashboard = new HashMap<>();
            dashboard.put("sources", dataSourceManager.getSourceStatus());
            dashboard.put("monitor", crawlMonitor.getMonitorData());
            dashboard.put("incremental", incrementalManager.getStats());
            dashboard.put("robots", robotsManager.getCacheStats());
            return Result.success(dashboard);
        } catch (Exception e) {
            return Result.error("获取仪表盘数据失败: " + e.getMessage());
        }
    }
}
