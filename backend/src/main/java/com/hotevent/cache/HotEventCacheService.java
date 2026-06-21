package com.hotevent.cache;

import com.hotevent.common.PageResult;
import com.hotevent.entity.HotEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class HotEventCacheService extends AbstractCacheService {

    @Override
    protected String getCacheName() {
        return "HotEventCache";
    }

    public HotEvent getEventById(Long id) {
        if (id == null || id <= 0) return null;

        if (!bloomFilterManager.eventIdMightExist(id)) {
            log.debug("[HotEventCache] 布隆过滤器拦截非法ID: {}", id);
            cacheStatsManager.recordHit(getCacheName());
            return null;
        }

        String key = CacheKeyConstants.buildEventDetailKey(id);
        return getWithBreakdownProtection(key, String.valueOf(id),
                () -> loadEventFromDb(id),
                cacheProperties.getHotEvent().getDetailTtlSeconds());
    }

    private HotEvent loadEventFromDb(Long id) {
        return null;
    }

    public void cacheEvent(HotEvent event) {
        if (event == null || event.getId() == null) return;
        String key = CacheKeyConstants.buildEventDetailKey(event.getId());
        putToCache(key, event, cacheProperties.getHotEvent().getDetailTtlSeconds());
        bloomFilterManager.addEventId(event.getId());
    }

    public void evictEvent(Long id) {
        if (id == null) return;
        String key = CacheKeyConstants.buildEventDetailKey(id);
        evictFromCache(key);
    }

    @SuppressWarnings("unchecked")
    public PageResult<HotEvent> getEventList(String source, String keyword, String category,
                                              LocalDateTime startTime, LocalDateTime endTime,
                                              int page, int size) {
        String key = CacheKeyConstants.buildEventListKey(
                source != null ? source : "all",
                keyword,
                category != null ? category : "all",
                page,
                size);
        if (startTime != null) key += ":" + startTime.hashCode();
        if (endTime != null) key += ":" + endTime.hashCode();

        Object cached = getFromCache(key);
        if (cached instanceof PageResult) {
            return (PageResult<HotEvent>) cached;
        }
        return null;
    }

    public void cacheEventList(String source, String keyword, String category,
                                LocalDateTime startTime, LocalDateTime endTime,
                                int page, int size, PageResult<HotEvent> result) {
        String key = CacheKeyConstants.buildEventListKey(
                source != null ? source : "all",
                keyword,
                category != null ? category : "all",
                page,
                size);
        if (startTime != null) key += ":" + startTime.hashCode();
        if (endTime != null) key += ":" + endTime.hashCode();
        putToCache(key, result, cacheProperties.getHotEvent().getListTtlSeconds());
    }

    public void evictAllEventLists() {
        try {
            String pattern = CacheKeyConstants.HOT_EVENT_LIST + "*";
            evictByPattern(pattern);
            log.info("[HotEventCache] 批量失效所有事件列表缓存");
        } catch (Exception e) {
            log.warn("[HotEventCache] 批量失效列表缓存失败: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public List<HotEvent> getRankedEvents(String source, int limit) {
        String key = CacheKeyConstants.buildEventRankedKey(source, limit);
        Object cached = getFromCache(key);
        if (cached instanceof List) {
            return (List<HotEvent>) cached;
        }
        return null;
    }

    public void cacheRankedEvents(String source, int limit, List<HotEvent> events) {
        String key = CacheKeyConstants.buildEventRankedKey(source, limit);
        putToCache(key, events, cacheProperties.getHotEvent().getRankedTtlSeconds());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getStatistics() {
        String key = CacheKeyConstants.HOT_EVENT_STATS;
        Object cached = getFromCache(key);
        if (cached instanceof Map) {
            return (Map<String, Object>) cached;
        }
        return null;
    }

    public void cacheStatistics(Map<String, Object> stats) {
        String key = CacheKeyConstants.HOT_EVENT_STATS;
        putToCache(key, stats, cacheProperties.getHotEvent().getStatsTtlSeconds());
    }

    public void evictStatistics() {
        evictFromCache(CacheKeyConstants.HOT_EVENT_STATS);
    }

    @SuppressWarnings("unchecked")
    public List<String> getSources() {
        Object cached = getFromCache(CacheKeyConstants.HOT_EVENT_SOURCES);
        if (cached instanceof List) {
            return (List<String>) cached;
        }
        return null;
    }

    public void cacheSources(List<String> sources) {
        putToCache(CacheKeyConstants.HOT_EVENT_SOURCES, sources,
                cacheProperties.getHotEvent().getSourcesTtlSeconds());
    }

    @SuppressWarnings("unchecked")
    public List<String> getCategories() {
        Object cached = getFromCache(CacheKeyConstants.HOT_EVENT_CATEGORIES);
        if (cached instanceof List) {
            return (List<String>) cached;
        }
        return null;
    }

    public void cacheCategories(List<String> categories) {
        putToCache(CacheKeyConstants.HOT_EVENT_CATEGORIES, categories,
                cacheProperties.getHotEvent().getCategoriesTtlSeconds());
    }

    public void evictSourcesAndCategories() {
        evictFromCache(CacheKeyConstants.HOT_EVENT_SOURCES);
        evictFromCache(CacheKeyConstants.HOT_EVENT_CATEGORIES);
    }

    public void onEventChanged(Long eventId) {
        evictEvent(eventId);
        evictAllEventLists();
        evictStatistics();
        evictSourcesAndCategories();
        log.info("[HotEventCache] 事件变更，清理关联缓存 eventId={}", eventId);
    }

    public void onBatchEventChanged() {
        evictAllEventLists();
        evictStatistics();
        evictSourcesAndCategories();
        log.info("[HotEventCache] 批量事件变更，清理关联缓存");
    }

    private void evictByPattern(String pattern) {
        try {
            var keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("[HotEventCache] 按模式删除缓存失败 pattern={}: {}", pattern, e.getMessage());
        }
    }

    public void refreshStatistics(Runnable loader) {
        refreshCache(CacheKeyConstants.HOT_EVENT_STATS, () -> {
            loader.run();
            return getStatistics();
        }, cacheProperties.getHotEvent().getStatsTtlSeconds());
    }
}
