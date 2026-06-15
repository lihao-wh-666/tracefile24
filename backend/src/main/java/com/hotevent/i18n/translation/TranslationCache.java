package com.hotevent.i18n.translation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class TranslationCache {

    private static final int MAX_CACHE_SIZE = 10000;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public void put(String text, String sourceLang, String targetLang, String translation) {
        if (text == null || translation == null) return;
        String key = buildKey(text, sourceLang, targetLang);
        if (cache.size() >= MAX_CACHE_SIZE) {
            evictOldest();
        }
        cache.put(key, new CacheEntry(translation, System.currentTimeMillis()));
    }

    public String get(String text, String sourceLang, String targetLang) {
        String key = buildKey(text, sourceLang, targetLang);
        CacheEntry entry = cache.get(key);
        if (entry == null) return null;
        return entry.translation;
    }

    public void clear() {
        cache.clear();
        log.info("[TranslationCache] Cache cleared");
    }

    public int size() {
        return cache.size();
    }

    private String buildKey(String text, String sourceLang, String targetLang) {
        return sourceLang + ":" + targetLang + ":" + text.hashCode() + ":" + text.length();
    }

    private void evictOldest() {
        long oldestTime = Long.MAX_VALUE;
        String oldestKey = null;
        for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
            if (entry.getValue().timestamp < oldestTime) {
                oldestTime = entry.getValue().timestamp;
                oldestKey = entry.getKey();
            }
        }
        if (oldestKey != null) {
            cache.remove(oldestKey);
        }
    }

    private static class CacheEntry {
        final String translation;
        final long timestamp;

        CacheEntry(String translation, long timestamp) {
            this.translation = translation;
            this.timestamp = timestamp;
        }
    }
}
