package com.hotevent.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Slf4j
@Component
public class CacheStatsManager {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CacheProperties cacheProperties;

    private final Map<String, LongAdder> localHitCounters = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> localMissCounters = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> localPutCounters = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> localEvictCounters = new ConcurrentHashMap<>();

    private volatile long lastSnapshotTime = System.currentTimeMillis();
    private final Map<String, CacheStatsSnapshot> lastSnapshot = new ConcurrentHashMap<>();

    public void recordHit(String cacheName) {
        if (!cacheProperties.isStatsEnabled()) return;
        localHitCounters.computeIfAbsent(cacheName, k -> new LongAdder()).increment();
    }

    public void recordMiss(String cacheName) {
        if (!cacheProperties.isStatsEnabled()) return;
        localMissCounters.computeIfAbsent(cacheName, k -> new LongAdder()).increment();
    }

    public void recordPut(String cacheName) {
        if (!cacheProperties.isStatsEnabled()) return;
        localPutCounters.computeIfAbsent(cacheName, k -> new LongAdder()).increment();
    }

    public void recordEvict(String cacheName) {
        if (!cacheProperties.isStatsEnabled()) return;
        localEvictCounters.computeIfAbsent(cacheName, k -> new LongAdder()).increment();
    }

    public double getHitRate(String cacheName) {
        long hits = getCounterValue(localHitCounters, cacheName);
        long misses = getCounterValue(localMissCounters, cacheName);
        long total = hits + misses;
        return total == 0 ? 0.0 : (double) hits / total;
    }

    public CacheStatsSnapshot getSnapshot(String cacheName) {
        CacheStatsSnapshot snapshot = new CacheStatsSnapshot();
        snapshot.setCacheName(cacheName);
        snapshot.setHitCount(getCounterValue(localHitCounters, cacheName));
        snapshot.setMissCount(getCounterValue(localMissCounters, cacheName));
        snapshot.setPutCount(getCounterValue(localPutCounters, cacheName));
        snapshot.setEvictCount(getCounterValue(localEvictCounters, cacheName));
        snapshot.setHitRate(snapshot.getHitCount() + snapshot.getMissCount() == 0 ? 0.0
                : (double) snapshot.getHitCount() / (snapshot.getHitCount() + snapshot.getMissCount()));
        return snapshot;
    }

    public Map<String, CacheStatsSnapshot> getAllSnapshots() {
        Map<String, CacheStatsSnapshot> result = new ConcurrentHashMap<>();
        for (String name : localHitCounters.keySet()) {
            result.put(name, getSnapshot(name));
        }
        for (String name : localMissCounters.keySet()) {
            result.putIfAbsent(name, getSnapshot(name));
        }
        return result;
    }

    @Scheduled(fixedRate = 60000)
    public void flushStatsToRedis() {
        if (!cacheProperties.isStatsEnabled()) return;

        long now = System.currentTimeMillis();
        long intervalMs = now - lastSnapshotTime;
        lastSnapshotTime = now;

        try {
            for (Map.Entry<String, LongAdder> entry : localHitCounters.entrySet()) {
                String cacheName = entry.getKey();
                long hits = entry.getValue().sumThenReset();
                long misses = getCounterValue(localMissCounters, cacheName);
                long puts = getCounterValue(localPutCounters, cacheName);
                long evicts = getCounterValue(localEvictCounters, cacheName);

                if (hits > 0) {
                    stringRedisTemplate.opsForValue().increment(
                            CacheKeyConstants.buildKey(CacheKeyConstants.STATS_HIT, cacheName), hits);
                }
                if (misses > 0) {
                    localMissCounters.getOrDefault(cacheName, new LongAdder()).sumThenReset();
                    stringRedisTemplate.opsForValue().increment(
                            CacheKeyConstants.buildKey(CacheKeyConstants.STATS_MISS, cacheName), misses);
                }
                if (puts > 0) {
                    localPutCounters.getOrDefault(cacheName, new LongAdder()).sumThenReset();
                    stringRedisTemplate.opsForValue().increment(
                            CacheKeyConstants.buildKey(CacheKeyConstants.STATS_PUT, cacheName), puts);
                }
                if (evicts > 0) {
                    localEvictCounters.getOrDefault(cacheName, new LongAdder()).sumThenReset();
                    stringRedisTemplate.opsForValue().increment(
                            CacheKeyConstants.buildKey(CacheKeyConstants.STATS_EVICT, cacheName), evicts);
                }

                CacheStatsSnapshot diff = new CacheStatsSnapshot();
                diff.setCacheName(cacheName);
                diff.setHitCount(hits);
                diff.setMissCount(misses);
                diff.setPutCount(puts);
                diff.setEvictCount(evicts);
                diff.setHitRate(hits + misses == 0 ? 0.0 : (double) hits / (hits + misses));
                diff.setIntervalMs(intervalMs);
                lastSnapshot.put(cacheName, diff);

                if (hits + misses > 100 && diff.getHitRate() < 0.5) {
                    log.warn("[CacheStats] 缓存命中率较低 cache={}, hitRate={:.2%}, interval={}ms",
                            cacheName, diff.getHitRate(), intervalMs);
                }
            }
        } catch (Exception e) {
            log.warn("[CacheStats] 刷新缓存统计到Redis失败: {}", e.getMessage());
        }
    }

    private long getCounterValue(Map<String, LongAdder> counters, String cacheName) {
        LongAdder adder = counters.get(cacheName);
        return adder == null ? 0 : adder.sum();
    }

    public static class CacheStatsSnapshot {
        private String cacheName;
        private long hitCount;
        private long missCount;
        private long putCount;
        private long evictCount;
        private double hitRate;
        private long intervalMs;

        public String getCacheName() { return cacheName; }
        public void setCacheName(String cacheName) { this.cacheName = cacheName; }
        public long getHitCount() { return hitCount; }
        public void setHitCount(long hitCount) { this.hitCount = hitCount; }
        public long getMissCount() { return missCount; }
        public void setMissCount(long missCount) { this.missCount = missCount; }
        public long getPutCount() { return putCount; }
        public void setPutCount(long putCount) { this.putCount = putCount; }
        public long getEvictCount() { return evictCount; }
        public void setEvictCount(long evictCount) { this.evictCount = evictCount; }
        public double getHitRate() { return hitRate; }
        public void setHitRate(double hitRate) { this.hitRate = hitRate; }
        public long getIntervalMs() { return intervalMs; }
        public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }
    }
}
