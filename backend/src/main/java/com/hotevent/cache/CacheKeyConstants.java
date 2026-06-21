package com.hotevent.cache;

public final class CacheKeyConstants {

    private CacheKeyConstants() {}

    public static final String SEPARATOR = ":";

    public static final String HOT_EVENT_PREFIX = "hotevent:event";
    public static final String HOT_EVENT_DETAIL = HOT_EVENT_PREFIX + ":detail";
    public static final String HOT_EVENT_LIST = HOT_EVENT_PREFIX + ":list";
    public static final String HOT_EVENT_TRENDING = HOT_EVENT_PREFIX + ":trending";
    public static final String HOT_EVENT_STATS = HOT_EVENT_PREFIX + ":stats";
    public static final String HOT_EVENT_SOURCES = HOT_EVENT_PREFIX + ":sources";
    public static final String HOT_EVENT_CATEGORIES = HOT_EVENT_PREFIX + ":categories";
    public static final String HOT_EVENT_RANKED = HOT_EVENT_PREFIX + ":ranked";

    public static final String DICT_PREFIX = "hotevent:dict";
    public static final String DICT_SENSITIVE_KEYWORDS = DICT_PREFIX + ":sensitive:keywords";
    public static final String DICT_SENSITIVE_REGEX = DICT_PREFIX + ":sensitive:regex";
    public static final String DICT_PLATFORMS = DICT_PREFIX + ":platforms";
    public static final String DICT_ROLES = DICT_PREFIX + ":roles";

    public static final String CONFIG_PREFIX = "hotevent:config";
    public static final String CONFIG_SYS = CONFIG_PREFIX + ":sys";
    public static final String CONFIG_SYS_ALL = CONFIG_SYS + ":all";

    public static final String TRANSLATION_PREFIX = "hotevent:translation";
    public static final String TRANSLATION_EVENT = TRANSLATION_PREFIX + ":event";
    public static final String TRANSLATION_TEXT = TRANSLATION_PREFIX + ":text";

    public static final String LOCK_PREFIX = "hotevent:lock";
    public static final String LOCK_HOT_EVENT_DETAIL = LOCK_PREFIX + ":event:detail";
    public static final String LOCK_HOT_EVENT_LIST = LOCK_PREFIX + ":event:list";
    public static final String LOCK_SYS_CONFIG = LOCK_PREFIX + ":config:sys";
    public static final String LOCK_DICT_REFRESH = LOCK_PREFIX + ":dict:refresh";

    public static final String BLOOM_FILTER_PREFIX = "hotevent:bloom";
    public static final String BLOOM_FILTER_EVENT_ID = BLOOM_FILTER_PREFIX + ":event:id";

    public static final String STATS_PREFIX = "hotevent:stats:cache";
    public static final String STATS_HIT = STATS_PREFIX + ":hit";
    public static final String STATS_MISS = STATS_PREFIX + ":miss";
    public static final String STATS_PUT = STATS_PREFIX + ":put";
    public static final String STATS_EVICT = STATS_PREFIX + ":evict";

    public static String buildKey(String... parts) {
        return String.join(SEPARATOR, parts);
    }

    public static String buildEventDetailKey(Long id) {
        return buildKey(HOT_EVENT_DETAIL, String.valueOf(id));
    }

    public static String buildEventListKey(String source, String keyword, String category,
                                           int page, int size) {
        return buildKey(HOT_EVENT_LIST,
                source != null ? source : "all",
                keyword != null ? keyword.hashCode() + "" : "none",
                category != null ? category : "all",
                String.valueOf(page),
                String.valueOf(size));
    }

    public static String buildEventTrendingKey(String source, String category, int limit) {
        return buildKey(HOT_EVENT_TRENDING,
                source != null ? source : "all",
                category != null ? category : "all",
                String.valueOf(limit));
    }

    public static String buildEventRankedKey(String source, int limit) {
        return buildKey(HOT_EVENT_RANKED,
                source != null ? source : "all",
                String.valueOf(limit));
    }

    public static String buildSysConfigKey(String configKey) {
        return buildKey(CONFIG_SYS, configKey);
    }

    public static String buildDictSensitiveKeywordsKey(String typeCode) {
        return buildKey(DICT_SENSITIVE_KEYWORDS, typeCode);
    }

    public static String buildDictSensitiveRegexKey(String typeCode) {
        return buildKey(DICT_SENSITIVE_REGEX, typeCode);
    }

    public static String buildTranslationEventKey(Long eventId, String language) {
        return buildKey(TRANSLATION_EVENT, String.valueOf(eventId), language);
    }

    public static String buildTranslationTextKey(String sourceLang, String targetLang, String textHash) {
        return buildKey(TRANSLATION_TEXT, sourceLang, targetLang, textHash);
    }

    public static String buildLockKey(String lockPrefix, String identifier) {
        return buildKey(lockPrefix, identifier);
    }
}
