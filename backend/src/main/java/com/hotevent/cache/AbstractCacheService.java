package com.hotevent.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

@Slf4j
@Component
public abstract class AbstractCacheService {

    @Autowired
    protected RedisTemplate<String, Object> redisTemplate;

    @Autowired
    protected StringRedisTemplate stringRedisTemplate;

    @Autowired
    protected RedisLockRegistry redisLockRegistry;

    @Autowired
    protected CacheProperties cacheProperties;

    @Autowired
    protected CacheStatsManager cacheStatsManager;

    @Autowired
    protected BloomFilterManager bloomFilterManager;

    protected final Map<String, Object> localCacheLocks = new ConcurrentHashMap<>();

    protected abstract String getCacheName();

    protected Object getFromCache(String key) {
        try {
            if (!cacheProperties.isEnabled()) return null;
            Object value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                cacheStatsManager.recordHit(getCacheName());
                if (value instanceof NullPlaceholder) {
                    log.debug("[Cache] 命中空值缓存 key={}", key);
                    return null;
                }
                log.debug("[Cache] 命中缓存 key={}", key);
                return value;
            }
            cacheStatsManager.recordMiss(getCacheName());
            return null;
        } catch (Exception e) {
            log.warn("[Cache] 读取缓存失败 key={}: {}", key, e.getMessage());
            cacheStatsManager.recordMiss(getCacheName());
            return null;
        }
    }

    protected void putToCache(String key, Object value, int ttlSeconds) {
        try {
            if (!cacheProperties.isEnabled()) return;
            int actualTtl = applyAvalancheProtection(ttlSeconds);
            if (value == null) {
                if (cacheProperties.getPenetrationProtection().isNullValueCacheEnabled()) {
                    redisTemplate.opsForValue().set(key, new NullPlaceholder(),
                            cacheProperties.getNullCacheTtlSeconds(), TimeUnit.SECONDS);
                    cacheStatsManager.recordPut(getCacheName());
                    log.debug("[Cache] 写入空值缓存 key={}, ttl={}s", key, cacheProperties.getNullCacheTtlSeconds());
                }
                return;
            }
            redisTemplate.opsForValue().set(key, value, actualTtl, TimeUnit.SECONDS);
            cacheStatsManager.recordPut(getCacheName());
            log.debug("[Cache] 写入缓存 key={}, ttl={}s", key, actualTtl);
        } catch (Exception e) {
            log.warn("[Cache] 写入缓存失败 key={}: {}", key, e.getMessage());
        }
    }

    protected void evictFromCache(String key) {
        try {
            if (!cacheProperties.isEnabled()) return;
            Boolean deleted = redisTemplate.delete(key);
            cacheStatsManager.recordEvict(getCacheName());
            log.debug("[Cache] 失效缓存 key={}, deleted={}", key, deleted);
        } catch (Exception e) {
            log.warn("[Cache] 失效缓存失败 key={}: {}", key, e.getMessage());
        }
    }

    protected void evictBatch(Collection<String> keys) {
        try {
            if (!cacheProperties.isEnabled() || keys == null || keys.isEmpty()) return;
            Long deleted = redisTemplate.delete(keys);
            cacheStatsManager.recordEvict(getCacheName());
            log.debug("[Cache] 批量失效缓存 count={}, deleted={}", keys.size(), deleted);
        } catch (Exception e) {
            log.warn("[Cache] 批量失效缓存失败: {}", e.getMessage());
        }
    }

    protected <T> T getWithBreakdownProtection(String key, String lockIdentifier,
                                                Supplier<T> dbLoader, int ttlSeconds) {
        T cached = (T) getFromCache(key);
        if (cached != null || isNullCached(key)) {
            return cached;
        }

        if (!cacheProperties.getBreakdownProtection().isMutexLockEnabled()) {
            return loadAndCache(key, dbLoader, ttlSeconds);
        }

        Lock lock = null;
        boolean locked = false;
        try {
            String lockKey = CacheKeyConstants.buildLockKey(
                    CacheKeyConstants.LOCK_HOT_EVENT_DETAIL, lockIdentifier);
            lock = redisLockRegistry.obtain(lockKey);
            locked = lock.tryLock(
                    cacheProperties.getBreakdownProtection().getLockWaitTimeMs(),
                    TimeUnit.MILLISECONDS);

            if (locked) {
                cached = (T) getFromCache(key);
                if (cached != null || isNullCached(key)) {
                    return cached;
                }
                return loadAndCache(key, dbLoader, ttlSeconds);
            } else {
                log.warn("[CacheBreakdown] 获取分布式锁超时，直接查询DB key={}", key);
                return dbLoader.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[CacheBreakdown] 获取锁被中断，直接查询DB key={}", key);
            return dbLoader.get();
        } finally {
            if (locked && lock != null) {
                try {
                    lock.unlock();
                } catch (Exception e) {
                    log.warn("[CacheBreakdown] 释放锁失败: {}", e.getMessage());
                }
            }
        }
    }

    protected <T> T getWithLocalLock(String key, Supplier<T> dbLoader, int ttlSeconds) {
        T cached = (T) getFromCache(key);
        if (cached != null || isNullCached(key)) {
            return cached;
        }

        Object lockObj = localCacheLocks.computeIfAbsent(key, k -> new Object());
        synchronized (lockObj) {
            try {
                cached = (T) getFromCache(key);
                if (cached != null || isNullCached(key)) {
                    return cached;
                }
                return loadAndCache(key, dbLoader, ttlSeconds);
            } finally {
                localCacheLocks.remove(key);
            }
        }
    }

    private <T> T loadAndCache(String key, Supplier<T> dbLoader, int ttlSeconds) {
        try {
            T value = dbLoader.get();
            putToCache(key, value, ttlSeconds);
            return value;
        } catch (Exception e) {
            log.error("[CacheLoad] 从数据源加载失败 key={}: {}", key, e.getMessage());
            throw e;
        }
    }

    protected boolean isNullCached(String key) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            return value instanceof NullPlaceholder;
        } catch (Exception e) {
            return false;
        }
    }

    protected int applyAvalancheProtection(int baseTtlSeconds) {
        if (!cacheProperties.getAvalancheProtection().isRandomOffsetEnabled()) {
            return baseTtlSeconds;
        }
        int maxOffset = cacheProperties.getAvalancheProtection().getMaxOffsetSeconds();
        int randomOffset = maxOffset > 0 ? (int) (Math.random() * maxOffset) : 0;
        return Math.max(1, baseTtlSeconds + randomOffset);
    }

    protected void refreshCache(String key, Supplier<Object> freshDataSupplier, int ttlSeconds) {
        try {
            Object freshData = freshDataSupplier.get();
            putToCache(key, freshData, ttlSeconds);
            log.info("[CacheRefresh] 主动刷新缓存成功 key={}", key);
        } catch (Exception e) {
            log.warn("[CacheRefresh] 主动刷新缓存失败 key={}: {}", key, e.getMessage());
        }
    }

    public static class NullPlaceholder implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        private final long createTime = System.currentTimeMillis();

        public long getCreateTime() {
            return createTime;
        }
    }
}
