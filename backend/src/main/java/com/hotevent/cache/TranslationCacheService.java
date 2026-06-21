package com.hotevent.cache;

import com.hotevent.entity.EventTranslation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TranslationCacheService extends AbstractCacheService {

    @Override
    protected String getCacheName() {
        return "TranslationCache";
    }

    public EventTranslation getEventTranslation(Long eventId, String language) {
        if (eventId == null || language == null) return null;
        String key = CacheKeyConstants.buildTranslationEventKey(eventId, language);
        Object cached = getFromCache(key);
        if (cached instanceof EventTranslation) {
            return (EventTranslation) cached;
        }
        return null;
    }

    public void cacheEventTranslation(EventTranslation translation) {
        if (translation == null || translation.getEventId() == null || translation.getLanguage() == null) return;
        String key = CacheKeyConstants.buildTranslationEventKey(
                translation.getEventId(), translation.getLanguage());
        putToCache(key, translation, cacheProperties.getTranslation().getTtlSeconds());
    }

    public void evictEventTranslation(Long eventId, String language) {
        if (eventId == null || language == null) return;
        String key = CacheKeyConstants.buildTranslationEventKey(eventId, language);
        evictFromCache(key);
    }

    public void evictAllTranslationsForEvent(Long eventId) {
        if (eventId == null) return;
        try {
            String pattern = CacheKeyConstants.TRANSLATION_EVENT + ":" + eventId + "*";
            var keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("[TranslationCache] 清理事件{}的所有翻译缓存 {} 条", eventId, keys.size());
            }
        } catch (Exception e) {
            log.warn("[TranslationCache] 清理事件翻译缓存失败 eventId={}: {}", eventId, e.getMessage());
        }
    }

    public String getTextTranslation(String sourceLang, String targetLang, String text) {
        if (sourceLang == null || targetLang == null || text == null) return null;
        String textHash = sourceLang + ":" + targetLang + ":" + text.hashCode() + ":" + text.length();
        String key = CacheKeyConstants.buildTranslationTextKey(sourceLang, targetLang, textHash);
        Object cached = getFromCache(key);
        return cached instanceof String ? (String) cached : null;
    }

    public void cacheTextTranslation(String sourceLang, String targetLang, String text, String translation) {
        if (sourceLang == null || targetLang == null || text == null || translation == null) return;
        String textHash = sourceLang + ":" + targetLang + ":" + text.hashCode() + ":" + text.length();
        String key = CacheKeyConstants.buildTranslationTextKey(sourceLang, targetLang, textHash);
        putToCache(key, translation, cacheProperties.getTranslation().getTtlSeconds());
    }

    public CacheStatsManager.CacheStatsSnapshot getStats() {
        return cacheStatsManager.getSnapshot(getCacheName());
    }
}
