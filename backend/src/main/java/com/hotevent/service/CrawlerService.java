package com.hotevent.service;

import com.hotevent.config.AsyncTaskExecutor;
import com.hotevent.crawler.AbstractHotEventCrawler;
import com.hotevent.crawler.HotEventCrawler;
import com.hotevent.entity.CrawlRecord;
import com.hotevent.entity.HotEvent;
import com.hotevent.repository.CrawlRecordRepository;
import com.hotevent.repository.HotEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class CrawlerService {

    @Autowired
    private List<HotEventCrawler> crawlers;

    @Autowired
    private HotEventRepository hotEventRepository;

    @Autowired
    private CrawlRecordRepository crawlRecordRepository;

    @Autowired
    private AsyncTaskExecutor asyncTaskExecutor;

    private final Map<String, AtomicInteger> consecutiveFailures = new ConcurrentHashMap<>();

    private static final int MAX_CONSECUTIVE_FAILURES = 5;

    @Transactional
    public void crawlAllSources() {
        crawlAllSourcesAsync().join();
    }

    public CompletableFuture<Map<String, Object>> crawlAllSourcesAsync() {
        log.info("========================================");
        log.info("开始执行全数据源异步并行抓取任务");
        log.info("========================================");
        long globalStartTime = System.currentTimeMillis();

        List<HotEventCrawler> enabledCrawlers = new ArrayList<>();
        List<String> skippedSources = new ArrayList<>();

        for (HotEventCrawler crawler : crawlers) {
            if (crawler.isEnabled()) {
                String source = crawler.getSourceName();
                AtomicInteger failCount = consecutiveFailures.computeIfAbsent(source, k -> new AtomicInteger(0));

                if (failCount.get() >= MAX_CONSECUTIVE_FAILURES) {
                    log.warn("数据源 [{}] 连续失败{}次，已达到最大阈值，本次跳过抓取。",
                            source, MAX_CONSECUTIVE_FAILURES);
                    skippedSources.add(source + "(连续失败过多，跳过)");
                } else {
                    enabledCrawlers.add(crawler);
                }
            } else {
                log.info("数据源 [{}] 已禁用，跳过", crawler.getSourceName());
            }
        }

        List<CompletableFuture<CrawlRecord>> futures = new ArrayList<>();
        for (HotEventCrawler crawler : enabledCrawlers) {
            final HotEventCrawler finalCrawler = crawler;
            CompletableFuture<CrawlRecord> future = asyncTaskExecutor.submitIoTask(
                    () -> crawlSourceInternal(finalCrawler),
                    "crawlSource[" + crawler.getSourceName() + "]"
            );
            futures.add(future);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<String> successSources = new ArrayList<>();
                    List<String> failedSources = new ArrayList<>(skippedSources);
                    int totalEvents = 0;
                    int totalSuccess = 0;
                    int totalFail = 0;

                    for (CompletableFuture<CrawlRecord> future : futures) {
                        CrawlRecord record = future.join();
                        if (record != null) {
                            String source = record.getSource();
                            if ("success".equals(record.getStatus())) {
                                successSources.add(source);
                                totalEvents += record.getEventCount() != null ? record.getEventCount() : 0;
                                totalSuccess += record.getSuccessCount() != null ? record.getSuccessCount() : 0;
                                totalFail += record.getFailCount() != null ? record.getFailCount() : 0;
                                consecutiveFailures.computeIfAbsent(source, k -> new AtomicInteger(0)).set(0);
                            } else {
                                failedSources.add(source);
                                consecutiveFailures.computeIfAbsent(source, k -> new AtomicInteger(0)).incrementAndGet();
                            }
                        }
                    }

                    long globalCostTime = System.currentTimeMillis() - globalStartTime;

                    log.info("========================================");
                    log.info("全数据源异步并行抓取任务完成汇总:");
                    log.info("  总耗时: {}ms ({}秒)", globalCostTime, String.format("%.2f", globalCostTime / 1000.0));
                    log.info("  并行抓取数据源数量: {}", enabledCrawlers.size());
                    log.info("  成功数据源 ({}): {}", successSources.size(), successSources);
                    log.info("  失败数据源 ({}): {}", failedSources.size(), failedSources);
                    log.info("  总事件数: {}", totalEvents);
                    log.info("  总成功保存: {}", totalSuccess);
                    log.info("  总失败保存: {}", totalFail);
                    log.info("========================================");

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("totalCostTimeMs", globalCostTime);
                    result.put("successSources", successSources);
                    result.put("failedSources", failedSources);
                    result.put("totalEvents", totalEvents);
                    result.put("totalSuccess", totalSuccess);
                    result.put("totalFail", totalFail);
                    result.put("isAsync", true);
                    return result;
                });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CrawlRecord crawlSource(HotEventCrawler crawler) {
        return crawlSourceInternal(crawler);
    }

    private CrawlRecord crawlSourceInternal(HotEventCrawler crawler) {
        String source = crawler.getSourceName();
        log.info("---------- 开始抓取 [{}] ----------", source);
        long startTime = System.currentTimeMillis();
        CrawlRecord record = new CrawlRecord();
        record.setSource(source);
        record.setCrawlTime(LocalDateTime.now());

        try {
            List<HotEvent> events = crawler.crawl();
            record.setEventCount(events.size());
            log.info("[{}] 获取到 {} 条热点事件，开始入库处理", source, events.size());

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);
            AtomicInteger updateCount = new AtomicInteger(0);
            AtomicInteger insertCount = new AtomicInteger(0);

            for (HotEvent event : events) {
                try {
                    boolean isNew = saveOrUpdateEvent(event);
                    successCount.incrementAndGet();
                    if (isNew) {
                        insertCount.incrementAndGet();
                    } else {
                        updateCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    log.error("[{}] 保存热点事件失败: 标题=[{}], 错误={}",
                             source,
                             event.getTitle() != null && event.getTitle().length() > 50
                                     ? event.getTitle().substring(0, 50) + "..."
                                     : event.getTitle(),
                             e.getMessage());
                }
            }

            record.setSuccessCount(successCount.get());
            record.setFailCount(failCount.get());
            record.setStatus("success");
            long costTime = System.currentTimeMillis() - startTime;
            record.setCostTimeMs(costTime);

            log.info("[{}] 抓取入库完成:", source);
            log.info("  - 获取事件数: {}", events.size());
            log.info("  - 成功保存: {} (新增: {}, 更新: {})", successCount.get(), insertCount.get(), updateCount.get());
            log.info("  - 失败数: {}", failCount.get());
            log.info("  - 耗时: {}ms ({}秒)", costTime, String.format("%.2f", costTime / 1000.0));
            log.info("---------- [{}] 抓取完成 ----------", source);

        } catch (AbstractHotEventCrawler.CrawlerException ce) {
            record.setStatus("failed");
            record.setErrorMessage(
                    String.format("[错误类型: %s] %s", ce.getErrorType(), ce.getMessage())
                            + (ce.getCause() != null ? " | 根因: " + ce.getCause().getMessage() : "")
            );
            record.setSuccessCount(0);
            record.setFailCount(0);
            record.setEventCount(0);
            long costTime = System.currentTimeMillis() - startTime;
            record.setCostTimeMs(costTime);

            log.error("[{}] 抓取失败! 错误类型: {}, 消息: {}, 耗时: {}ms",
                     source, ce.getErrorType(), ce.getMessage(), costTime);
            log.error("[{}] 异常堆栈: {}", source, getStackTrace(ce));
            log.info("---------- [{}] 抓取失败 ----------", source);

        } catch (Exception e) {
            record.setStatus("failed");
            record.setErrorMessage(
                    String.format("[错误类型: %s] %s", "UNKNOWN", e.getMessage())
                            + (e.getCause() != null ? " | 根因: " + e.getCause().getMessage() : "")
            );
            record.setSuccessCount(0);
            record.setFailCount(0);
            record.setEventCount(0);
            long costTime = System.currentTimeMillis() - startTime;
            record.setCostTimeMs(costTime);

            log.error("[{}] 抓取发生未知异常! 耗时: {}ms", source, costTime, e);
            log.info("---------- [{}] 抓取异常 ----------", source);
        }

        return crawlRecordRepository.save(record);
    }

    public CompletableFuture<CrawlRecord> crawlSourceAsyncByName(String sourceName) {
        AtomicInteger failCount = consecutiveFailures.computeIfAbsent(sourceName, k -> new AtomicInteger(0));
        failCount.set(0);
        log.info("手动触发数据源 [{}] 异步抓取，已重置连续失败计数", sourceName);

        for (HotEventCrawler crawler : crawlers) {
            if (crawler.getSourceName().equals(sourceName) && crawler.isEnabled()) {
                return asyncTaskExecutor.submitIoTask(
                        () -> crawlSourceInternal(crawler),
                        "crawlSourceByName[" + sourceName + "]"
                );
            }
        }
        CompletableFuture<CrawlRecord> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("未找到数据源: " + sourceName));
        return failedFuture;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CrawlRecord crawlSourceByName(String sourceName) {
        return crawlSourceAsyncByName(sourceName).join();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public boolean saveOrUpdateEvent(HotEvent event) {
        if (event.getTitle() == null || event.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("事件标题不能为空");
        }
        if (event.getSource() == null || event.getSource().trim().isEmpty()) {
            throw new IllegalArgumentException("事件来源不能为空");
        }

        String normalizedTitle = event.getTitle().trim();
        String normalizedSource = event.getSource().trim();

        Optional<HotEvent> existing = hotEventRepository.findBySourceAndTitleAndDeletedFalse(
                normalizedSource, normalizedTitle);

        if (existing.isPresent()) {
            HotEvent existingEvent = existing.get();

            boolean updated = false;
            if (event.getHotValue() != null && !event.getHotValue().equals(existingEvent.getHotValue())) {
                existingEvent.setHotValue(event.getHotValue());
                updated = true;
            }
            if (event.getHotRank() != null && !event.getHotRank().equals(existingEvent.getHotRank())) {
                existingEvent.setHotRank(event.getHotRank());
                updated = true;
            }
            if (event.getIsRising() != null && !event.getIsRising().equals(existingEvent.getIsRising())) {
                existingEvent.setIsRising(event.getIsRising());
                updated = true;
            }
            if (event.getRisingRate() != null && !event.getRisingRate().equals(existingEvent.getRisingRate())) {
                existingEvent.setRisingRate(event.getRisingRate());
                updated = true;
            }
            if (event.getDescription() != null && !event.getDescription().trim().isEmpty()
                    && (existingEvent.getDescription() == null || existingEvent.getDescription().trim().isEmpty())) {
                existingEvent.setDescription(event.getDescription().trim());
                updated = true;
            }
            if (event.getImageUrl() != null && !event.getImageUrl().trim().isEmpty()
                    && (existingEvent.getImageUrl() == null || existingEvent.getImageUrl().trim().isEmpty())) {
                existingEvent.setImageUrl(event.getImageUrl().trim());
                updated = true;
            }
            if (event.getCategory() != null && !event.getCategory().trim().isEmpty()
                    && (existingEvent.getCategory() == null || existingEvent.getCategory().trim().isEmpty())) {
                existingEvent.setCategory(event.getCategory().trim());
                updated = true;
            }

            existingEvent.setLastSeenTime(LocalDateTime.now());
            existingEvent.setCrawlTime(event.getCrawlTime());
            existingEvent.setIsHot(true);

            hotEventRepository.save(existingEvent);
            return false;
        } else {
            event.setTitle(normalizedTitle);
            event.setSource(normalizedSource);
            if (event.getDescription() != null) {
                event.setDescription(event.getDescription().trim());
            }
            if (event.getImageUrl() != null) {
                event.setImageUrl(event.getImageUrl().trim());
            }
            if (event.getCategory() != null) {
                event.setCategory(event.getCategory().trim());
            }
            event.setIsHot(true);
            event.setDeleted(false);

            hotEventRepository.save(event);
            return true;
        }
    }

    public List<String> getAvailableSources() {
        List<String> sources = new ArrayList<>();
        for (HotEventCrawler crawler : crawlers) {
            if (crawler.isEnabled()) {
                sources.add(crawler.getSourceName());
            }
        }
        return sources;
    }

    public Map<String, Object> getCrawlerStatus() {
        Map<String, Object> status = new HashMap<>();
        List<Map<String, Object>> sourceStatuses = new ArrayList<>();

        for (HotEventCrawler crawler : crawlers) {
            Map<String, Object> sourceStatus = new HashMap<>();
            String sourceName = crawler.getSourceName();
            sourceStatus.put("name", sourceName);
            sourceStatus.put("enabled", crawler.isEnabled());
            AtomicInteger failCount = consecutiveFailures.get(sourceName);
            sourceStatus.put("consecutiveFailures", failCount != null ? failCount.get() : 0);
            sourceStatus.put("maxConsecutiveFailures", MAX_CONSECUTIVE_FAILURES);

            Optional<CrawlRecord> latestRecord =
                    crawlRecordRepository.findTopBySourceOrderByCrawlTimeDesc(sourceName);
            if (latestRecord.isPresent()) {
                CrawlRecord record = latestRecord.get();
                Map<String, Object> latestCrawl = new HashMap<>();
                latestCrawl.put("status", record.getStatus());
                latestCrawl.put("eventCount", record.getEventCount());
                latestCrawl.put("successCount", record.getSuccessCount());
                latestCrawl.put("failCount", record.getFailCount());
                latestCrawl.put("crawlTime", record.getCrawlTime());
                latestCrawl.put("costTimeMs", record.getCostTimeMs());
                sourceStatus.put("latestCrawl", latestCrawl);
            }
            sourceStatuses.add(sourceStatus);
        }

        status.put("sources", sourceStatuses);
        status.put("totalSources", crawlers.size());
        status.put("enabledSources", (int) sourceStatuses.stream()
                .filter(s -> Boolean.TRUE.equals(s.get("enabled"))).count());
        return status;
    }

    private String getStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        String stackTrace = sw.toString();
        return stackTrace.length() > 500 ? stackTrace.substring(0, 500) + "..." : stackTrace;
    }
}
