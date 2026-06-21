package com.hotevent.cache;

import com.hotevent.entity.SysConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Slf4j
@Service
public class SysConfigCacheService extends AbstractCacheService {

    private final Map<String, SysConfig> configCache = new ConcurrentHashMap<>();

    @Override
    protected String getCacheName() {
        return "SysConfigCache";
    }

    public SysConfig getByKey(String configKey) {
        if (configKey == null || configKey.isEmpty()) return null;

        SysConfig localCached = configCache.get(configKey);
        if (localCached != null) {
            cacheStatsManager.recordHit(getCacheName());
            return localCached;
        }

        String redisKey = CacheKeyConstants.buildSysConfigKey(configKey);
        SysConfig redisCached = (SysConfig) getFromCache(redisKey);
        if (redisCached != null) {
            cacheStatsManager.recordHit(getCacheName());
            configCache.put(configKey, redisCached);
            return redisCached;
        }

        cacheStatsManager.recordMiss(getCacheName());
        return null;
    }

    public String getValue(String configKey) {
        SysConfig config = getByKey(configKey);
        return config != null ? config.getConfigValue() : null;
    }

    public String getValueOrDefault(String configKey, String defaultValue) {
        String value = getValue(configKey);
        return value != null ? value : defaultValue;
    }

    public int getIntValue(String configKey, int defaultValue) {
        String value = getValue(configKey);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("[SysConfigCache] 配置值解析为int失败 key={}, value={}", configKey, value);
            return defaultValue;
        }
    }

    public long getLongValue(String configKey, long defaultValue) {
        String value = getValue(configKey);
        if (value == null) return defaultValue;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("[SysConfigCache] 配置值解析为long失败 key={}, value={}", configKey, value);
            return defaultValue;
        }
    }

    public boolean getBooleanValue(String configKey, boolean defaultValue) {
        String value = getValue(configKey);
        if (value == null) return defaultValue;
        try {
            return Boolean.parseBoolean(value);
        } catch (Exception e) {
            log.warn("[SysConfigCache] 配置值解析为boolean失败 key={}, value={}", configKey, value);
            return defaultValue;
        }
    }

    public <T> T getOrLoad(String configKey, Supplier<T> loader, Class<T> type) {
        SysConfig cached = getByKey(configKey);
        if (cached != null) {
            try {
                String value = cached.getConfigValue();
                if (type == String.class) {
                    return type.cast(value);
                } else if (type == Integer.class || type == int.class) {
                    return type.cast(Integer.parseInt(value));
                } else if (type == Long.class || type == long.class) {
                    return type.cast(Long.parseLong(value));
                } else if (type == Boolean.class || type == boolean.class) {
                    return type.cast(Boolean.parseBoolean(value));
                } else if (type == Double.class || type == double.class) {
                    return type.cast(Double.parseDouble(value));
                }
            } catch (Exception e) {
                log.warn("[SysConfigCache] 类型转换失败 key={}: {}", configKey, e.getMessage());
            }
        }
        T loaded = loader.get();
        if (loaded != null) {
            cacheConfig(configKey, String.valueOf(loaded), null, null);
        }
        return loaded;
    }

    public void cacheConfig(SysConfig config) {
        if (config == null || config.getConfigKey() == null) return;
        String redisKey = CacheKeyConstants.buildSysConfigKey(config.getConfigKey());
        configCache.put(config.getConfigKey(), config);
        putToCache(redisKey, config, cacheProperties.getSysConfig().getTtlSeconds());
    }

    public void cacheConfig(String configKey, String configValue, String configName, String description) {
        if (configKey == null) return;
        SysConfig config = new SysConfig();
        config.setConfigKey(configKey);
        config.setConfigValue(configValue);
        config.setConfigName(configName);
        config.setDescription(description);
        cacheConfig(config);
    }

    public void cacheBatch(List<SysConfig> configs) {
        if (configs == null || configs.isEmpty()) return;
        for (SysConfig config : configs) {
            cacheConfig(config);
        }
        log.info("[SysConfigCache] 批量缓存系统配置 {} 条", configs.size());
    }

    public void evictConfig(String configKey) {
        if (configKey == null) return;
        configCache.remove(configKey);
        String redisKey = CacheKeyConstants.buildSysConfigKey(configKey);
        evictFromCache(redisKey);
        log.info("[SysConfigCache] 失效配置缓存 key={}", configKey);
    }

    public void evictAll() {
        configCache.clear();
        try {
            var keys = redisTemplate.keys(CacheKeyConstants.CONFIG_SYS + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
            evictFromCache(CacheKeyConstants.CONFIG_SYS_ALL);
            log.info("[SysConfigCache] 清理所有系统配置缓存");
        } catch (Exception e) {
            log.warn("[SysConfigCache] 清理所有配置缓存失败: {}", e.getMessage());
        }
    }

    public void refreshConfig(String configKey, Supplier<SysConfig> loader) {
        try {
            String lockKey = CacheKeyConstants.buildLockKey(
                    CacheKeyConstants.LOCK_SYS_CONFIG, configKey);
            var lock = redisLockRegistry.obtain(lockKey);
            boolean locked = lock.tryLock(
                    cacheProperties.getBreakdownProtection().getLockWaitTimeMs(),
                    java.util.concurrent.TimeUnit.MILLISECONDS);
            try {
                if (locked) {
                    SysConfig fresh = loader.get();
                    if (fresh != null) {
                        cacheConfig(fresh);
                        log.info("[SysConfigCache] 主动刷新配置成功 key={}", configKey);
                    }
                }
            } finally {
                if (locked) lock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[SysConfigCache] 刷新配置被中断 key={}", configKey);
        } catch (Exception e) {
            log.warn("[SysConfigCache] 刷新配置失败 key={}: {}", configKey, e.getMessage());
        }
    }

    public Map<String, String> getAllConfigValues() {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, SysConfig> entry : configCache.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getConfigValue());
        }
        return result;
    }

    public CacheStatsManager.CacheStatsSnapshot getStats() {
        return cacheStatsManager.getSnapshot(getCacheName());
    }

    public int getLocalCacheSize() {
        return configCache.size();
    }
}
