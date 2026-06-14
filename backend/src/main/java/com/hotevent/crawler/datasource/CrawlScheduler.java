package com.hotevent.crawler.datasource;

import com.hotevent.crawler.adapter.PlatformAdapter;
import com.hotevent.crawler.core.*;
import com.hotevent.crawler.monitor.CrawlMonitor;
import com.hotevent.crawler.storage.IncrementalCrawlManager;
import com.hotevent.crawler.storage.DataStorageService;
import com.hotevent.crawler.compliance.RobotsComplianceManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class CrawlScheduler {

    @Autowired
    private DataSourceManager dataSourceManager;

    @Autowired
    private CrawlerClient crawlerClient;

    @Autowired
    private CrawlMonitor crawlMonitor;

    @Autowired
    private IncrementalCrawlManager incrementalManager;

    @Autowired
    private DataStorageService storageService;

    @Autowired
    private RobotsComplianceManager robotsManager;

    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final Map<String, CrawlTask> runningTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private ExecutorService crawlExecutor;

    public void start() {
        dataSourceManager.init();
        int poolSize = calculateThreadPoolSize();
        this.crawlExecutor = new ThreadPoolExecutor(
                poolSize / 2, poolSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        crawlMonitor.start();
        scheduleAutoCrawlTasks();
        log.info("CrawlScheduler启动完成，线程池大小: {}", poolSize);
    }

    private int calculateThreadPoolSize() {
        int total = 0;
        for (DataSourceConfig cfg : dataSourceManager.getAllConfigs()) {
            total += cfg.getMaxConcurrent();
        }
        return Math.min(Math.max(total, 8), 64);
    }

    private void scheduleAutoCrawlTasks() {
        for (DataSourceConfig cfg : dataSourceManager.getEnabledConfigs()) {
            scheduleTask(cfg);
        }
    }

    private void scheduleTask(DataSourceConfig cfg) {
        if (cfg.getCron() == null || cfg.getCron().isEmpty()) return;
        String code = cfg.getCode();
        long initialDelay = calculateInitialDelay(cfg);
        long period = estimatePeriodFromCron(cfg.getCron());

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (dataSourceManager.isSourceEnabled(code)) {
                    executeSourceAsync(code);
                }
            } catch (Exception e) {
                log.error("定时执行数据源[{}]异常: {}", code, e.getMessage());
            }
        }, initialDelay, period, TimeUnit.MILLISECONDS);

        scheduledTasks.put(code, future);
        log.info("已调度数据源[{}]的定时任务，初始延迟: {}ms, 周期: {}ms", code, initialDelay, period);
    }

    private long calculateInitialDelay(DataSourceConfig cfg) {
        Random rnd = new Random();
        return 5000L + rnd.nextInt(30000);
    }

    private long estimatePeriodFromCron(String cron) {
        String[] parts = cron.trim().split("\\s+");
        if (parts.length < 6) return 3600_000L;
        String min = parts[0];
        String hr = parts[1];
        if (min.contains("*") && hr.contains("*")) return 60_000L;
        if (min.contains("*/") || hr.contains("*/")) {
            try {
                if (min.contains("*/")) return Long.parseLong(min.substring(2)) * 60_000L;
                if (hr.contains("*/")) return Long.parseLong(hr.substring(2)) * 3600_000L;
            } catch (Exception ignored) {}
        }
        if (hr.contains(",")) return 6 * 3600_000L;
        return 3600_000L;
    }

    public CrawlTask executeSource(String code) throws Exception {
        return executeSource(code, null);
    }

    public CrawlTask executeSource(String code, String keyword) throws Exception {
        DataSourceConfig cfg = dataSourceManager.getConfig(code);
        PlatformAdapter adapter = dataSourceManager.getAdapter(code);
        if (adapter == null) throw new IllegalArgumentException("未找到数据源: " + code);

        if (!robotsManager.isAllowed(code, adapter.getBaseUrl() + "/")) {
            log.warn("[{}] robots协议禁止爬取，跳过", code);
            throw new IllegalStateException("robots协议禁止爬取该数据源: " + code);
        }

        CrawlTask task = adapter.createDefaultTask();
        if (task.getTaskId() == null) task.setTaskId(UUID.randomUUID().toString());
        task.setPlatform(code);

        if (keyword != null && !keyword.isEmpty()) {
            task.setKeyword(keyword);
            task.getRequests().clear();
            task.addRequest(adapter.buildSearchRequest(keyword, 1, 20));
        }

        if (cfg != null) {
            task.setMaxItems(cfg.getMaxItemsPerCrawl());
            task.setMaxDepth(cfg.getMaxDepth());
            task.setIncremental(cfg.isIncrementalEnabled());
        }

        if (task.isIncremental()) {
            incrementalManager.applyIncrementalConfig(task);
        }

        runningTasks.put(task.getTaskId(), task);
        crawlMonitor.onTaskStarted(task);

        log.info("========== 开始执行数据源[{}]采集任务: {} ==========", code, task.getTaskName());
        long globalStart = System.currentTimeMillis();

        AtomicInteger successReq = new AtomicInteger(0);
        AtomicInteger failReq = new AtomicInteger(0);
        AtomicInteger totalItems = new AtomicInteger(0);

        try {
            CrawlResponse overallResp = executeTaskInternal(task, adapter, successReq, failReq, totalItems);

            List<DataItem> validItems = new ArrayList<>();
            for (DataItem item : task.getCollectedItems()) {
                if (adapter.validateDataItem(item)) {
                    if (item.getItemId() == null || item.getItemId().isEmpty()) {
                        item.setItemId(adapter.generateItemId(item));
                    }
                    validItems.add(item);
                }
            }
            task.setCollectedItems(validItems);

            if (task.isIncremental()) {
                incrementalManager.updateIncrementalState(task);
            }

            int saved = storageService.saveBatch(validItems, code);
            log.info("[{}] 批量存储完成，保存{}条记录", code, saved);

            long cost = System.currentTimeMillis() - globalStart;
            crawlMonitor.onTaskCompleted(task, successReq.get(), failReq.get(), totalItems.get(), cost);

            log.info("========== 数据源[{}]采集完成: 成功{}项, 失败{}项, 采集{}条, 耗时{}ms ==========",
                    code, successReq.get(), failReq.get(), totalItems.get(), cost);
            return task;

        } catch (Exception e) {
            task.setStatus(CrawlTask.TaskStatus.FAILED);
            task.setLastError(e.getMessage());
            crawlMonitor.onTaskFailed(task, e);
            log.error("数据源[{}]采集任务异常: {}", code, e.getMessage(), e);
            throw e;
        } finally {
            runningTasks.remove(task.getTaskId());
        }
    }

    public CompletableFuture<CrawlTask> executeSourceAsync(String code) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeSource(code);
            } catch (Exception e) {
                log.error("异步执行数据源[{}]失败: {}", code, e.getMessage());
                return null;
            }
        }, crawlExecutor);
    }

    public CompletableFuture<CrawlTask> executeSourceAsync(String code, String keyword) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeSource(code, keyword);
            } catch (Exception e) {
                log.error("异步执行数据源[{}]搜索[{}]失败: {}", code, keyword, e.getMessage());
                return null;
            }
        }, crawlExecutor);
    }

    private CrawlResponse executeTaskInternal(CrawlTask task, PlatformAdapter adapter,
                                              AtomicInteger successReq, AtomicInteger failReq,
                                              AtomicInteger totalItems) {
        task.setStatus(CrawlTask.TaskStatus.RUNNING);
        task.setStartTime(LocalDateTime.now());

        CrawlResponse overallResponse = CrawlResponse.builder()
                .platform(task.getPlatform())
                .taskId(task.getTaskId())
                .startTime(LocalDateTime.now())
                .build();

        int processed = 0;
        Set<String> visitedUrls = ConcurrentHashMap.newKeySet();
        Queue<CrawlRequest> pendingQueue = new ConcurrentLinkedQueue<>(task.getRequests());

        while (!pendingQueue.isEmpty() && processed < task.getMaxItems() * 3) {
            CrawlRequest req = pendingQueue.poll();
            if (req == null) break;

            if (!robotsManager.isAllowed(task.getPlatform(), req.getUrl())) {
                log.warn("[{}] robots协议禁止访问: {}", task.getPlatform(), req.getUrl());
                failReq.incrementAndGet();
                continue;
            }

            if (req.getUrl() != null && visitedUrls.contains(req.getUrl())) continue;
            if (req.getUrl() != null) visitedUrls.add(req.getUrl());

            processed++;
            task.getProcessedCount().set(processed);

            try {
                CrawlResponse resp = crawlerClient.execute(req);
                task.addResponse(resp);

                List<DataItem> items;
                switch (req.getRequestType()) {
                    case SEARCH: items = adapter.parseSearchResponse(resp); break;
                    case DETAIL:
                        DataItem d = adapter.parseDetailResponse(resp);
                        items = d != null ? Collections.singletonList(d) : Collections.emptyList();
                        break;
                    default: items = adapter.parseListResponse(resp); break;
                }

                if (items != null && !items.isEmpty()) {
                    task.collectItems(items);
                    totalItems.addAndGet(items.size());
                    overallResponse.addDataItems(items);

                    if (req.getCurrentDepth() < task.getMaxDepth() && resp.getFollowUpRequests() != null) {
                        for (CrawlRequest followUp : resp.getFollowUpRequests()) {
                            followUp.setCurrentDepth(req.getCurrentDepth() + 1);
                            followUp.setTaskId(task.getTaskId());
                            followUp.setPlatform(task.getPlatform());
                            pendingQueue.offer(followUp);
                            task.addRequest(followUp);
                        }
                    }

                    successReq.incrementAndGet();
                } else if (resp.isSuccess()) {
                    successReq.incrementAndGet();
                } else {
                    failReq.incrementAndGet();
                    overallResponse.addError("请求失败: " + resp.getMessage());
                }

            } catch (Exception e) {
                failReq.incrementAndGet();
                overallResponse.addError(e.getMessage());
                log.warn("[{}] 请求执行异常: {}", task.getPlatform(), e.getMessage());
            }
        }

        task.setEndTime(LocalDateTime.now());
        task.setStatus(failReq.get() == 0 ? CrawlTask.TaskStatus.COMPLETED : CrawlTask.TaskStatus.COMPLETED);
        task.setSuccessCount(successReq);
        task.setFailCount(failReq);

        overallResponse.setEndTime(LocalDateTime.now());
        overallResponse.setDurationMs(System.currentTimeMillis() - task.getStartTime().getNano() / 1_000_000);
        overallResponse.setTotalItems(processed);
        overallResponse.setParsedItems(successReq.get());
        overallResponse.setFailedItems(failReq.get());
        overallResponse.setStatus(failReq.get() == 0 ? CrawlResponse.Status.SUCCESS
                : (successReq.get() > 0 ? CrawlResponse.Status.PARTIAL_SUCCESS : CrawlResponse.Status.FAILED));

        return overallResponse;
    }

    public Map<String, Object> executeAllSources() {
        List<DataSourceConfig> configs = dataSourceManager.getEnabledConfigs();
        List<CompletableFuture<CrawlTask>> futures = new ArrayList<>();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalSources", configs.size());
        result.put("startTime", LocalDateTime.now());

        for (DataSourceConfig cfg : configs) {
            futures.add(executeSourceAsync(cfg.getCode()));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        List<CrawlTask> tasks = new ArrayList<>();
        for (CompletableFuture<CrawlTask> f : futures) {
            try {
                CrawlTask t = f.get();
                if (t != null) tasks.add(t);
            } catch (Exception ignored) {}
        }
        result.put("endTime", LocalDateTime.now());
        result.put("completedTasks", tasks.size());
        result.put("tasks", tasks);
        return result;
    }

    public CrawlTask getRunningTask(String taskId) {
        return runningTasks.get(taskId);
    }

    public Collection<CrawlTask> getAllRunningTasks() {
        return runningTasks.values();
    }

    public void cancelTask(String taskId) {
        CrawlTask task = runningTasks.get(taskId);
        if (task != null) {
            task.setStatus(CrawlTask.TaskStatus.CANCELLED);
            log.info("取消任务: {}", taskId);
        }
    }

    public void shutdown() {
        for (ScheduledFuture<?> f : scheduledTasks.values()) {
            f.cancel(false);
        }
        scheduledTasks.clear();
        scheduler.shutdown();
        if (crawlExecutor != null) {
            crawlExecutor.shutdown();
        }
        crawlMonitor.shutdown();
        log.info("CrawlScheduler已关闭");
    }
}
