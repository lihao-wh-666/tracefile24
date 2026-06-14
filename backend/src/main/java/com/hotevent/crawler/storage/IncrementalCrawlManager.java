package com.hotevent.crawler.storage;

import com.hotevent.crawler.core.CrawlTask;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class IncrementalCrawlManager {

    private final Map<String, IncrementalState> stateStore = new ConcurrentHashMap<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IncrementalState {
        private String platform;
        private String keyword;
        private LocalDateTime lastCrawlTime;
        private String lastCursor;
        private Integer lastPage;
        private Long lastHotValue;
        private Integer lastRank;
        private Long lastSeenMaxId;
        private String checkpointData;
        private int totalCrawled;
        private int sessionCrawled;
        private int newItemsCount;
        private int updateItemsCount;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    public void applyIncrementalConfig(CrawlTask task) {
        if (task == null) return;
        String key = buildStateKey(task);
        IncrementalState state = stateStore.get(key);

        if (state != null) {
            task.setLastCrawlTime(state.getLastCrawlTime());
            task.setIncrementalCursor(state.getLastCursor());
            task.setCheckpoint(state.getCheckpointData());
            if (state.getLastPage() != null) {
                state.setLastPage(Math.max(1, state.getLastPage()));
            }
            log.debug("应用增量爬取状态 [{}] 上次爬取: {} 游标: {}",
                    task.getPlatform(), state.getLastCrawlTime(), state.getLastCursor());
        } else {
            state = IncrementalState.builder()
                    .platform(task.getPlatform())
                    .keyword(task.getKeyword())
                    .createdAt(LocalDateTime.now())
                    .sessionCrawled(0)
                    .newItemsCount(0)
                    .updateItemsCount(0)
                    .build();
            stateStore.put(key, state);
            log.info("创建新的增量爬取状态 [{}]", key);
        }
    }

    public void updateIncrementalState(CrawlTask task) {
        if (task == null) return;
        String key = buildStateKey(task);
        IncrementalState state = stateStore.computeIfAbsent(key, k ->
                IncrementalState.builder()
                        .platform(task.getPlatform())
                        .keyword(task.getKeyword())
                        .createdAt(LocalDateTime.now())
                        .build()
        );

        state.setLastCrawlTime(LocalDateTime.now());
        if (task.getIncrementalCursor() != null) {
            state.setLastCursor(task.getIncrementalCursor());
        }
        if (task.getCheckpoint() != null) {
            state.setCheckpointData(task.getCheckpoint());
        }

        int collected = task.getCollectedCount();
        state.setSessionCrawled(collected);
        state.setTotalCrawled(state.getTotalCrawled() + collected);
        state.setUpdatedAt(LocalDateTime.now());

        log.info("更新增量爬取状态 [{}] 本次采集:{}条 累计:{}条",
                key, collected, state.getTotalCrawled());
    }

    public IncrementalState getState(String platform, String keyword) {
        return stateStore.get(buildKey(platform, keyword));
    }

    public void saveCheckpoint(String platform, String keyword, String checkpoint) {
        String key = buildKey(platform, keyword);
        IncrementalState state = stateStore.computeIfAbsent(key, k ->
                IncrementalState.builder()
                        .platform(platform)
                        .keyword(keyword)
                        .createdAt(LocalDateTime.now())
                        .build()
        );
        state.setCheckpointData(checkpoint);
        state.setUpdatedAt(LocalDateTime.now());
        log.debug("保存断点: [{}] checkpoint={}", key, checkpoint != null ? checkpoint.substring(0, Math.min(20, checkpoint.length())) + "..." : null);
    }

    public String loadCheckpoint(String platform, String keyword) {
        IncrementalState state = getState(platform, keyword);
        return state != null ? state.getCheckpointData() : null;
    }

    public boolean shouldCrawl(String platform, String keyword, long minIntervalMs) {
        IncrementalState state = getState(platform, keyword);
        if (state == null || state.getLastCrawlTime() == null) return true;
        long elapsed = java.time.Duration.between(state.getLastCrawlTime(), LocalDateTime.now()).toMillis();
        return elapsed >= minIntervalMs;
    }

    public void recordStats(String platform, String keyword, int newItems, int updateItems) {
        String key = buildKey(platform, keyword);
        IncrementalState state = stateStore.get(key);
        if (state != null) {
            state.setNewItemsCount(state.getNewItemsCount() + newItems);
            state.setUpdateItemsCount(state.getUpdateItemsCount() + updateItems);
        }
    }

    public Map<String, Object> getStats() {
        Map<String, Object> result = new ConcurrentHashMap<>();
        for (Map.Entry<String, IncrementalState> e : stateStore.entrySet()) {
            IncrementalState s = e.getValue();
            Map<String, Object> info = new ConcurrentHashMap<>();
            info.put("platform", s.getPlatform());
            info.put("keyword", s.getKeyword());
            info.put("lastCrawlTime", s.getLastCrawlTime());
            info.put("lastCursor", s.getLastCursor());
            info.put("totalCrawled", s.getTotalCrawled());
            info.put("sessionCrawled", s.getSessionCrawled());
            info.put("newItemsCount", s.getNewItemsCount());
            info.put("updateItemsCount", s.getUpdateItemsCount());
            result.put(e.getKey(), info);
        }
        return result;
    }

    public void clearState(String platform, String keyword) {
        String key = buildKey(platform, keyword);
        stateStore.remove(key);
        log.info("清除增量爬取状态 [{}]", key);
    }

    public void clearAllStates() {
        int count = stateStore.size();
        stateStore.clear();
        log.info("清除全部{}个增量爬取状态", count);
    }

    private String buildStateKey(CrawlTask task) {
        return buildKey(task.getPlatform(), task.getKeyword());
    }

    private String buildKey(String platform, String keyword) {
        if (keyword == null || keyword.isEmpty()) return platform;
        return platform + ":" + keyword;
    }
}
