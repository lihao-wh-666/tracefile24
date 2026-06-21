package com.hotevent.controller;

import com.hotevent.cache.*;
import com.hotevent.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/admin/cache")
public class CacheMonitorController {

    @Autowired
    private CacheStatsManager cacheStatsManager;

    @Autowired
    private HotEventCacheService hotEventCacheService;

    @Autowired
    private SysConfigCacheService sysConfigCacheService;

    @Autowired
    private DictionaryCacheService dictionaryCacheService;

    @Autowired
    private TranslationCacheService translationCacheService;

    @GetMapping("/stats")
    public Result<Map<String, CacheStatsManager.CacheStatsSnapshot>> getAllStats() {
        return Result.success(cacheStatsManager.getAllSnapshots());
    }

    @GetMapping("/stats/{cacheName}")
    public Result<CacheStatsManager.CacheStatsSnapshot> getCacheStats(@PathVariable String cacheName) {
        return Result.success(cacheStatsManager.getSnapshot(cacheName));
    }

    @GetMapping("/overview")
    public Result<Map<String, Object>> getOverview() {
        Map<String, Object> overview = new LinkedHashMap<>();

        Map<String, Object> detail = new LinkedHashMap<>();
        CacheStatsManager.CacheStatsSnapshot snapshot = cacheStatsManager.getSnapshot("HotEventCache");
        detail.put("hotEvent", buildStatsInfo(snapshot));

        snapshot = cacheStatsManager.getSnapshot("SysConfigCache");
        detail.put("sysConfig", buildStatsInfo(snapshot));

        snapshot = cacheStatsManager.getSnapshot("DictionaryCache");
        detail.put("dictionary", buildStatsInfo(snapshot));

        snapshot = cacheStatsManager.getSnapshot("TranslationCache");
        detail.put("translation", buildStatsInfo(snapshot));

        overview.put("details", detail);
        overview.put("sysConfigLocalCacheSize", sysConfigCacheService.getLocalCacheSize());
        overview.put("generatedAt", System.currentTimeMillis());

        return Result.success(overview);
    }

    private Map<String, Object> buildStatsInfo(CacheStatsManager.CacheStatsSnapshot snapshot) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("hitCount", snapshot.getHitCount());
        info.put("missCount", snapshot.getMissCount());
        info.put("hitRate", String.format("%.2f%%", snapshot.getHitRate() * 100));
        info.put("putCount", snapshot.getPutCount());
        info.put("evictCount", snapshot.getEvictCount());
        info.put("totalRequests", snapshot.getHitCount() + snapshot.getMissCount());
        return info;
    }

    @PostMapping("/hot-event/clear")
    public Result<String> clearHotEventCache() {
        try {
            hotEventCacheService.evictAllEventLists();
            hotEventCacheService.evictStatistics();
            hotEventCacheService.evictSourcesAndCategories();
            log.info("[CacheMonitor] 手动清理热点事件缓存");
            return Result.success("缓存清理成功");
        } catch (Exception e) {
            return Result.error(500, "缓存清理失败: " + e.getMessage());
        }
    }

    @PostMapping("/sys-config/clear")
    public Result<String> clearSysConfigCache() {
        try {
            sysConfigCacheService.evictAll();
            log.info("[CacheMonitor] 手动清理系统配置缓存");
            return Result.success("缓存清理成功");
        } catch (Exception e) {
            return Result.error(500, "缓存清理失败: " + e.getMessage());
        }
    }

    @PostMapping("/dictionary/clear")
    public Result<String> clearDictionaryCache() {
        try {
            dictionaryCacheService.evictAll();
            log.info("[CacheMonitor] 手动清理字典缓存");
            return Result.success("缓存清理成功");
        } catch (Exception e) {
            return Result.error(500, "缓存清理失败: " + e.getMessage());
        }
    }

    @PostMapping("/translation/clear")
    public Result<String> clearTranslationCache() {
        try {
            log.info("[CacheMonitor] 手动清理翻译缓存");
            return Result.success("缓存清理成功");
        } catch (Exception e) {
            return Result.error(500, "缓存清理失败: " + e.getMessage());
        }
    }

    @PostMapping("/all/clear")
    public Result<String> clearAllCache() {
        try {
            hotEventCacheService.evictAllEventLists();
            hotEventCacheService.evictStatistics();
            hotEventCacheService.evictSourcesAndCategories();
            sysConfigCacheService.evictAll();
            dictionaryCacheService.evictAll();
            log.info("[CacheMonitor] 手动清理全部缓存");
            return Result.success("全部缓存清理成功");
        } catch (Exception e) {
            return Result.error(500, "缓存清理失败: " + e.getMessage());
        }
    }
}
