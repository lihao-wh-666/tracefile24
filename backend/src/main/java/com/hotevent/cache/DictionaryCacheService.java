package com.hotevent.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class DictionaryCacheService extends AbstractCacheService {

    private final Map<String, Set<String>> sensitiveKeywordsCache = new ConcurrentHashMap<>();
    private final Map<String, List<String>> sensitiveRegexCache = new ConcurrentHashMap<>();
    private final Map<String, List<String>> platformConfigCache = new ConcurrentHashMap<>();
    private final Set<String> roleCache = ConcurrentHashMap.newKeySet();

    private volatile long lastRefreshTime = 0;

    @Override
    protected String getCacheName() {
        return "DictionaryCache";
    }

    public Set<String> getSensitiveKeywords(String typeCode) {
        if (typeCode == null) return Collections.emptySet();

        String key = CacheKeyConstants.buildDictSensitiveKeywordsKey(typeCode);
        Set<String> localCache = sensitiveKeywordsCache.get(typeCode);
        if (localCache != null && !localCache.isEmpty()) {
            cacheStatsManager.recordHit(getCacheName());
            return Collections.unmodifiableSet(localCache);
        }

        @SuppressWarnings("unchecked")
        Set<String> redisCache = (Set<String>) getFromCache(key);
        if (redisCache != null && !redisCache.isEmpty()) {
            cacheStatsManager.recordHit(getCacheName());
            sensitiveKeywordsCache.put(typeCode, ConcurrentHashMap.newKeySet());
            sensitiveKeywordsCache.get(typeCode).addAll(redisCache);
            return Collections.unmodifiableSet(redisCache);
        }

        cacheStatsManager.recordMiss(getCacheName());
        return null;
    }

    public void cacheSensitiveKeywords(String typeCode, Set<String> keywords) {
        if (typeCode == null || keywords == null) return;

        String key = CacheKeyConstants.buildDictSensitiveKeywordsKey(typeCode);
        Set<String> copy = new HashSet<>(keywords);

        sensitiveKeywordsCache.put(typeCode, ConcurrentHashMap.newKeySet());
        sensitiveKeywordsCache.get(typeCode).addAll(copy);

        putToCache(key, copy, cacheProperties.getDictionary().getTtlSeconds());
        log.info("[DictionaryCache] 缓存敏感词类型: {}, 数量: {}", typeCode, keywords.size());
    }

    public void evictSensitiveKeywords(String typeCode) {
        if (typeCode == null) return;
        sensitiveKeywordsCache.remove(typeCode);
        String key = CacheKeyConstants.buildDictSensitiveKeywordsKey(typeCode);
        evictFromCache(key);
    }

    public List<String> getSensitiveRegex(String typeCode) {
        if (typeCode == null) return Collections.emptyList();

        String key = CacheKeyConstants.buildDictSensitiveRegexKey(typeCode);
        List<String> localCache = sensitiveRegexCache.get(typeCode);
        if (localCache != null && !localCache.isEmpty()) {
            cacheStatsManager.recordHit(getCacheName());
            return Collections.unmodifiableList(localCache);
        }

        @SuppressWarnings("unchecked")
        List<String> redisCache = (List<String>) getFromCache(key);
        if (redisCache != null && !redisCache.isEmpty()) {
            cacheStatsManager.recordHit(getCacheName());
            sensitiveRegexCache.put(typeCode, new ArrayList<>(redisCache));
            return Collections.unmodifiableList(redisCache);
        }

        cacheStatsManager.recordMiss(getCacheName());
        return null;
    }

    public void cacheSensitiveRegex(String typeCode, List<String> regexList) {
        if (typeCode == null || regexList == null) return;

        String key = CacheKeyConstants.buildDictSensitiveRegexKey(typeCode);
        List<String> copy = new ArrayList<>(regexList);

        sensitiveRegexCache.put(typeCode, new ArrayList<>(copy));
        putToCache(key, copy, cacheProperties.getDictionary().getTtlSeconds());
        log.info("[DictionaryCache] 缓存敏感正则类型: {}, 数量: {}", typeCode, regexList.size());
    }

    public void evictSensitiveRegex(String typeCode) {
        if (typeCode == null) return;
        sensitiveRegexCache.remove(typeCode);
        String key = CacheKeyConstants.buildDictSensitiveRegexKey(typeCode);
        evictFromCache(key);
    }

    public List<String> getPlatformList() {
        String key = CacheKeyConstants.DICT_PLATFORMS;
        List<String> localCache = platformConfigCache.get("list");
        if (localCache != null && !localCache.isEmpty()) {
            cacheStatsManager.recordHit(getCacheName());
            return Collections.unmodifiableList(localCache);
        }

        @SuppressWarnings("unchecked")
        List<String> redisCache = (List<String>) getFromCache(key);
        if (redisCache != null && !redisCache.isEmpty()) {
            cacheStatsManager.recordHit(getCacheName());
            platformConfigCache.put("list", new ArrayList<>(redisCache));
            return Collections.unmodifiableList(redisCache);
        }

        cacheStatsManager.recordMiss(getCacheName());
        return null;
    }

    public void cachePlatformList(List<String> platforms) {
        if (platforms == null) return;
        String key = CacheKeyConstants.DICT_PLATFORMS;
        List<String> copy = new ArrayList<>(platforms);
        platformConfigCache.put("list", new ArrayList<>(copy));
        putToCache(key, copy, cacheProperties.getDictionary().getTtlSeconds());
    }

    public Set<String> getRoles() {
        String key = CacheKeyConstants.DICT_ROLES;
        if (!roleCache.isEmpty()) {
            cacheStatsManager.recordHit(getCacheName());
            return Collections.unmodifiableSet(roleCache);
        }

        @SuppressWarnings("unchecked")
        Set<String> redisCache = (Set<String>) getFromCache(key);
        if (redisCache != null && !redisCache.isEmpty()) {
            cacheStatsManager.recordHit(getCacheName());
            roleCache.addAll(redisCache);
            return Collections.unmodifiableSet(redisCache);
        }

        cacheStatsManager.recordMiss(getCacheName());
        return null;
    }

    public void cacheRoles(Set<String> roles) {
        if (roles == null) return;
        String key = CacheKeyConstants.DICT_ROLES;
        Set<String> copy = new HashSet<>(roles);
        roleCache.clear();
        roleCache.addAll(copy);
        putToCache(key, copy, cacheProperties.getDictionary().getTtlSeconds());
    }

    public void evictAll() {
        sensitiveKeywordsCache.clear();
        sensitiveRegexCache.clear();
        platformConfigCache.clear();
        roleCache.clear();

        try {
            var keys = redisTemplate.keys(CacheKeyConstants.DICT_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
            log.info("[DictionaryCache] 清理所有字典缓存");
        } catch (Exception e) {
            log.warn("[DictionaryCache] 清理字典缓存失败: {}", e.getMessage());
        }
    }

    public void evictAllSensitive() {
        sensitiveKeywordsCache.clear();
        sensitiveRegexCache.clear();
        try {
            var keys = redisTemplate.keys(CacheKeyConstants.DICT_SENSITIVE_KEYWORDS + "*");
            if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
            var regexKeys = redisTemplate.keys(CacheKeyConstants.DICT_SENSITIVE_REGEX + "*");
            if (regexKeys != null && !regexKeys.isEmpty()) redisTemplate.delete(regexKeys);
            log.info("[DictionaryCache] 清理所有敏感词缓存");
        } catch (Exception e) {
            log.warn("[DictionaryCache] 清理敏感词缓存失败: {}", e.getMessage());
        }
    }

    @Scheduled(fixedRateString = "${hot-event.cache.dictionary.refresh-interval-seconds:43200}000")
    public void scheduledRefreshCheck() {
        long now = System.currentTimeMillis();
        if (lastRefreshTime > 0 && now - lastRefreshTime < cacheProperties.getDictionary().getRefreshIntervalSeconds() * 1000L) {
            return;
        }
        lastRefreshTime = now;
        log.info("[DictionaryCache] 定时刷新字典缓存检查执行");
    }

    public CacheStatsManager.CacheStatsSnapshot getStats() {
        return cacheStatsManager.getSnapshot(getCacheName());
    }
}
