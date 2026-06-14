package com.hotevent.crawler.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlTask {

    public enum TaskStatus {
        PENDING, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED
    }

    public enum TaskMode {
        ONCE, SCHEDULED, INCREMENTAL, FULL
    }

    @Builder.Default
    private String taskId = UUID.randomUUID().toString();

    private String taskName;
    private String platform;
    private TaskMode mode;

    @Builder.Default
    private TaskStatus status = TaskStatus.PENDING;

    private String keyword;
    private String category;
    private int maxItems;
    private int maxDepth;
    private LocalDateTime scheduledTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private long totalDurationMs;

    @Builder.Default
    private List<CrawlRequest> requests = new ArrayList<>();

    @Builder.Default
    private List<CrawlResponse> responses = new ArrayList<>();

    @Builder.Default
    private List<DataItem> collectedItems = new ArrayList<>();

    @Builder.Default
    private AtomicInteger successCount = new AtomicInteger(0);

    @Builder.Default
    private AtomicInteger failCount = new AtomicInteger(0);

    @Builder.Default
    private AtomicInteger processedCount = new AtomicInteger(0);

    private String checkpoint;
    private String lastError;
    private boolean incremental;
    private LocalDateTime lastCrawlTime;
    private String incrementalCursor;

    public synchronized void collectItem(DataItem item) {
        if (collectedItems == null) collectedItems = new ArrayList<>();
        collectedItems.add(item);
    }

    public synchronized void collectItems(List<DataItem> items) {
        if (collectedItems == null) collectedItems = new ArrayList<>();
        if (items != null) collectedItems.addAll(items);
    }

    public synchronized void addRequest(CrawlRequest request) {
        if (requests == null) requests = new ArrayList<>();
        requests.add(request);
    }

    public synchronized void addResponse(CrawlResponse response) {
        if (responses == null) responses = new ArrayList<>();
        responses.add(response);
    }

    public synchronized int getTotalRequests() {
        return requests != null ? requests.size() : 0;
    }

    public synchronized int getTotalResponses() {
        return responses != null ? responses.size() : 0;
    }

    public synchronized int getCollectedCount() {
        return collectedItems != null ? collectedItems.size() : 0;
    }

    public double getProgress() {
        int total = getTotalRequests();
        if (total == 0) return 0.0;
        return Math.min(100.0, (processedCount.get() * 100.0) / total);
    }
}
