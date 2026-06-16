package com.hotevent.controller;

import com.hotevent.common.Result;
import com.hotevent.config.AsyncTaskExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/async-monitor")
public class AsyncMonitorController {

    @Autowired
    private AsyncTaskExecutor asyncTaskExecutor;

    @GetMapping("/thread-pools")
    public Result<Map<String, Object>> getAllThreadPoolMetrics() {
        try {
            Map<String, Object> metrics = asyncTaskExecutor.getAllPoolMetrics();
            return Result.success(metrics);
        } catch (Exception e) {
            log.error("获取线程池监控指标失败", e);
            return Result.error("获取监控指标失败: " + e.getMessage());
        }
    }

    @GetMapping("/thread-pools/cpu-intensive")
    public Result<Map<String, Object>> getCpuThreadPoolMetrics() {
        try {
            Map<String, Object> metrics = asyncTaskExecutor.getCpuPoolMetrics();
            return Result.success(metrics);
        } catch (Exception e) {
            log.error("获取CPU密集型线程池监控指标失败", e);
            return Result.error("获取监控指标失败: " + e.getMessage());
        }
    }

    @GetMapping("/thread-pools/io-intensive")
    public Result<Map<String, Object>> getIoThreadPoolMetrics() {
        try {
            Map<String, Object> metrics = asyncTaskExecutor.getIoPoolMetrics();
            return Result.success(metrics);
        } catch (Exception e) {
            log.error("获取IO密集型线程池监控指标失败", e);
            return Result.error("获取监控指标失败: " + e.getMessage());
        }
    }

    @GetMapping("/overview")
    public Result<Map<String, Object>> getAsyncOverview() {
        try {
            Map<String, Object> overview = new LinkedHashMap<>();

            Map<String, Object> cpuMetrics = asyncTaskExecutor.getCpuPoolMetrics();
            Map<String, Object> ioMetrics = asyncTaskExecutor.getIoPoolMetrics();

            overview.put("systemInfo", Map.of(
                    "availableProcessors", Runtime.getRuntime().availableProcessors(),
                    "freeMemory", Runtime.getRuntime().freeMemory(),
                    "totalMemory", Runtime.getRuntime().totalMemory(),
                    "maxMemory", Runtime.getRuntime().maxMemory()
            ));

            int totalActiveThreads = 0;
            long totalCompletedTasks = 0;
            long totalTasks = 0;
            int totalQueueSize = 0;

            if (cpuMetrics != null) {
                totalActiveThreads += (int) cpuMetrics.getOrDefault("activeThreadCount", 0);
                totalCompletedTasks += (long) cpuMetrics.getOrDefault("completedTaskCount", 0L);
                totalTasks += (long) cpuMetrics.getOrDefault("totalTaskCount", 0L);
                totalQueueSize += (int) cpuMetrics.getOrDefault("queueSize", 0);
            }
            if (ioMetrics != null) {
                totalActiveThreads += (int) ioMetrics.getOrDefault("activeThreadCount", 0);
                totalCompletedTasks += (long) ioMetrics.getOrDefault("completedTaskCount", 0L);
                totalTasks += (long) ioMetrics.getOrDefault("totalTaskCount", 0L);
                totalQueueSize += (int) ioMetrics.getOrDefault("queueSize", 0);
            }

            overview.put("summary", Map.of(
                    "totalActiveThreads", totalActiveThreads,
                    "totalCompletedTasks", totalCompletedTasks,
                    "totalTasks", totalTasks,
                    "totalQueueSize", totalQueueSize,
                    "overallCompletionRate", totalTasks > 0
                            ? String.format("%.2f%%", (double) totalCompletedTasks / totalTasks * 100)
                            : "100.00%"
            ));

            overview.put("cpuIntensive", cpuMetrics);
            overview.put("ioIntensive", ioMetrics);

            return Result.success(overview);
        } catch (Exception e) {
            log.error("获取异步概览失败", e);
            return Result.error("获取概览失败: " + e.getMessage());
        }
    }
}
