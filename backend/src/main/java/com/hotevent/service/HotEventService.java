package com.hotevent.service;

import com.hotevent.common.PageResult;
import com.hotevent.config.AsyncTaskExecutor;
import com.hotevent.entity.EventTranslation;
import com.hotevent.entity.HotEvent;
import com.hotevent.i18n.I18nProperties;
import com.hotevent.i18n.TranslationService;
import com.hotevent.repository.EventTranslationRepository;
import com.hotevent.repository.HotEventRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class HotEventService {

    @Autowired
    private HotEventRepository hotEventRepository;

    @Autowired
    private EventTranslationRepository eventTranslationRepository;

    @Autowired
    private TranslationService translationService;

    @Autowired
    private I18nProperties i18nProperties;

    @Autowired
    private AsyncTaskExecutor asyncTaskExecutor;

    @Autowired
    private HotEventLogService hotEventLogService;

    private final Map<String, EventTranslation> translationCacheLocal = new ConcurrentHashMap<>();

    public PageResult<HotEvent> getHotEventList(String source, String keyword, int page, int size) {
        return getHotEventList(source, keyword, null, null, null, page, size);
    }

    public PageResult<HotEvent> getHotEventList(String source, String keyword, String category,
                                                LocalDateTime startTime, LocalDateTime endTime,
                                                int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "hotValue", "id"));
        Specification<HotEvent> spec = buildSearchSpecification(source, keyword, category, startTime, endTime);
        Page<HotEvent> hotEventPage = hotEventRepository.findAll(spec, pageable);

        List<HotEvent> events = hotEventPage.getContent();
        if (events != null && !events.isEmpty()) {
            int startRank = (page - 1) * size + 1;
            for (int i = 0; i < events.size(); i++) {
                events.get(i).setHotRank(startRank + i);
            }
        }

        return PageResult.of(
                events,
                hotEventPage.getTotalElements(),
                page,
                size
        );
    }

    private Specification<HotEvent> buildSearchSpecification(String source, String keyword, String category,
                                                             LocalDateTime startTime, LocalDateTime endTime) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("deleted"), false));

            if (source != null && !source.isEmpty()) {
                predicates.add(cb.equal(root.get("source"), source));
            }

            if (keyword != null && !keyword.isEmpty()) {
                predicates.add(cb.like(root.get("title"), "%" + keyword + "%"));
            }

            if (category != null && !category.isEmpty()) {
                predicates.add(cb.equal(root.get("category"), category));
            }

            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("crawlTime"), startTime));
            }

            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("crawlTime"), endTime));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public PageResult<Map<String, Object>> getHotEventListLocalized(String source, String keyword, int page, int size, String lang) {
        return getHotEventListLocalizedAsync(source, keyword, null, null, null, page, size, lang).join();
    }

    public PageResult<Map<String, Object>> getHotEventListLocalized(String source, String keyword, String category,
                                                                    LocalDateTime startTime, LocalDateTime endTime,
                                                                    int page, int size, String lang) {
        return getHotEventListLocalizedAsync(source, keyword, category, startTime, endTime, page, size, lang).join();
    }

    public CompletableFuture<PageResult<Map<String, Object>>> getHotEventListLocalizedAsync(
            String source, String keyword, int page, int size, String lang) {
        return getHotEventListLocalizedAsync(source, keyword, null, null, null, page, size, lang);
    }

    public CompletableFuture<PageResult<Map<String, Object>>> getHotEventListLocalizedAsync(
            String source, String keyword, String category, LocalDateTime startTime, LocalDateTime endTime,
            int page, int size, String lang) {
        PageResult<HotEvent> original = getHotEventList(source, keyword, category, startTime, endTime, page, size);
        List<HotEvent> events = original.getRecords();

        if (lang == null || lang.isEmpty() || "zh-CN".equals(lang) || events.isEmpty()) {
            List<Map<String, Object>> localizedList = new ArrayList<>();
            for (HotEvent event : events) {
                localizedList.add(hotEventToMap(event));
            }
            return CompletableFuture.completedFuture(
                    PageResult.of(localizedList, original.getTotal(), original.getCurrent(), original.getSize())
            );
        }

        List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();
        for (HotEvent event : events) {
            futures.add(localizeHotEventAsync(event, lang));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<Map<String, Object>> localizedList = new ArrayList<>();
                    for (CompletableFuture<Map<String, Object>> future : futures) {
                        Map<String, Object> result = future.join();
                        if (result != null) {
                            localizedList.add(result);
                        }
                    }
                    return PageResult.of(localizedList, original.getTotal(), original.getCurrent(), original.getSize());
                });
    }

    public HotEvent getHotEventById(Long id) {
        return hotEventRepository.findById(id).orElse(null);
    }

    public Map<String, Object> getHotEventByIdLocalized(Long id, String lang) {
        HotEvent event = getHotEventById(id);
        if (event == null) return null;
        return localizeHotEvent(event, lang);
    }

    public CompletableFuture<Map<String, Object>> getHotEventByIdLocalizedAsync(Long id, String lang) {
        HotEvent event = getHotEventById(id);
        if (event == null) return CompletableFuture.completedFuture(null);
        return localizeHotEventAsync(event, lang);
    }

    public Map<String, Object> localizeHotEvent(HotEvent event, String targetLang) {
        if (event == null) return null;
        if (targetLang == null || targetLang.isEmpty() || "zh-CN".equals(targetLang)) {
            return hotEventToMap(event);
        }

        String cacheKey = event.getId() + "_" + targetLang;
        EventTranslation cachedTranslation = translationCacheLocal.get(cacheKey);

        Optional<EventTranslation> existingTranslation = cachedTranslation != null
                ? Optional.of(cachedTranslation)
                : eventTranslationRepository.findByEventIdAndLanguage(event.getId(), targetLang);

        if (existingTranslation.isPresent()) {
            EventTranslation translation = existingTranslation.get();
            if (cachedTranslation == null) {
                translationCacheLocal.put(cacheKey, translation);
            }
            Map<String, Object> map = hotEventToMap(event);
            if (translation.getTitle() != null) map.put("title", translation.getTitle());
            if (translation.getDescription() != null) map.put("description", translation.getDescription());
            if (translation.getCategory() != null) map.put("category", translation.getCategory());
            map.put("translatedLanguage", targetLang);
            map.put("isTranslated", true);
            return map;
        }

        Map<String, Object> translated = translationService.translateEvent(
                event.getTitle(),
                event.getDescription(),
                event.getCategory(),
                "zh-CN",
                targetLang
        );

        Map<String, Object> map = hotEventToMap(event);
        if (translated.containsKey("title")) map.put("title", translated.get("title"));
        if (translated.containsKey("description")) map.put("description", translated.get("description"));
        if (translated.containsKey("category")) map.put("category", translated.get("category"));
        map.put("translatedLanguage", targetLang);
        map.put("isTranslated", true);

        saveTranslation(event, targetLang, translated);

        return map;
    }

    public CompletableFuture<Map<String, Object>> localizeHotEventAsync(HotEvent event, String targetLang) {
        if (event == null) return CompletableFuture.completedFuture(null);
        if (targetLang == null || targetLang.isEmpty() || "zh-CN".equals(targetLang)) {
            return CompletableFuture.completedFuture(hotEventToMap(event));
        }

        final String cacheKey = event.getId() + "_" + targetLang;
        EventTranslation cachedTranslation = translationCacheLocal.get(cacheKey);

        if (cachedTranslation != null) {
            Map<String, Object> map = hotEventToMap(event);
            if (cachedTranslation.getTitle() != null) map.put("title", cachedTranslation.getTitle());
            if (cachedTranslation.getDescription() != null) map.put("description", cachedTranslation.getDescription());
            if (cachedTranslation.getCategory() != null) map.put("category", cachedTranslation.getCategory());
            map.put("translatedLanguage", targetLang);
            map.put("isTranslated", true);
            return CompletableFuture.completedFuture(map);
        }

        return asyncTaskExecutor.submitIoTask(() -> {
            Optional<EventTranslation> existingTranslation =
                    eventTranslationRepository.findByEventIdAndLanguage(event.getId(), targetLang);

            if (existingTranslation.isPresent()) {
                EventTranslation translation = existingTranslation.get();
                translationCacheLocal.put(cacheKey, translation);
                Map<String, Object> map = hotEventToMap(event);
                if (translation.getTitle() != null) map.put("title", translation.getTitle());
                if (translation.getDescription() != null) map.put("description", translation.getDescription());
                if (translation.getCategory() != null) map.put("category", translation.getCategory());
                map.put("translatedLanguage", targetLang);
                map.put("isTranslated", true);
                return map;
            }
            return null;
        }, "findTranslation[eventId=" + event.getId() + ",lang=" + targetLang + "]")
        .thenCompose(dbResult -> {
            if (dbResult != null) {
                return CompletableFuture.completedFuture(dbResult);
            }

            return translationService.translateEventAsync(
                    event.getTitle(),
                    event.getDescription(),
                    event.getCategory(),
                    "zh-CN",
                    targetLang
            ).thenApply(translated -> {
                Map<String, Object> map = hotEventToMap(event);
                if (translated.containsKey("title")) map.put("title", translated.get("title"));
                if (translated.containsKey("description")) map.put("description", translated.get("description"));
                if (translated.containsKey("category")) map.put("category", translated.get("category"));
                map.put("translatedLanguage", targetLang);
                map.put("isTranslated", true);

                saveTranslationAsync(event, targetLang, translated);

                return map;
            });
        });
    }

    private void saveTranslation(HotEvent event, String targetLang, Map<String, Object> translated) {
        try {
            EventTranslation translation = new EventTranslation();
            translation.setEventId(event.getId());
            translation.setLanguage(targetLang);
            if (translated.containsKey("title")) translation.setTitle((String) translated.get("title"));
            if (translated.containsKey("description")) translation.setDescription((String) translated.get("description"));
            if (translated.containsKey("category")) translation.setCategory((String) translated.get("category"));
            translation.setTranslationProvider(translationService.getActiveProvider());
            translation.setIsVerified(false);
            eventTranslationRepository.save(translation);

            String cacheKey = event.getId() + "_" + targetLang;
            translationCacheLocal.put(cacheKey, translation);
        } catch (Exception e) {
            log.warn("Failed to save translation for event {}: {}", event.getId(), e.getMessage());
        }
    }

    private void saveTranslationAsync(HotEvent event, String targetLang, Map<String, Object> translated) {
        asyncTaskExecutor.submitIoRunnable(() -> saveTranslation(event, targetLang, translated),
                "saveTranslation[eventId=" + event.getId() + ",lang=" + targetLang + "]");
    }

    public void autoTranslateEvent(HotEvent event) {
        autoTranslateEventAsync(event);
    }

    public CompletableFuture<Void> autoTranslateEventAsync(HotEvent event) {
        if (!i18nProperties.getTranslation().isAutoTranslateOnCrawl()) {
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (String targetLang : i18nProperties.getSupportedLocales()) {
            if ("zh-CN".equals(targetLang)) continue;

            if (eventTranslationRepository.existsByEventIdAndLanguage(event.getId(), targetLang)) {
                continue;
            }

            CompletableFuture<Void> future = translationService.translateEventAsync(
                    event.getTitle(),
                    event.getDescription(),
                    event.getCategory(),
                    "zh-CN",
                    targetLang
            ).thenAccept(translated -> {
                if (translated != null) {
                    saveTranslation(event, targetLang, translated);
                }
            }).exceptionally(ex -> {
                log.warn("Auto-translation failed for event {} to {}: {}", event.getId(), targetLang, ex.getMessage());
                return null;
            });
            futures.add(future);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    private Map<String, Object> hotEventToMap(HotEvent event) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", event.getId());
        map.put("title", event.getTitle());
        map.put("description", event.getDescription());
        map.put("source", event.getSource());
        map.put("sourceUrl", event.getSourceUrl());
        map.put("hotValue", event.getHotValue());
        map.put("hotRank", event.getHotRank());
        map.put("category", event.getCategory());
        map.put("imageUrl", event.getImageUrl());
        map.put("isHot", event.getIsHot());
        map.put("isRising", event.getIsRising());
        map.put("risingRate", event.getRisingRate());
        map.put("crawlTime", event.getCrawlTime());
        map.put("firstSeenTime", event.getFirstSeenTime());
        map.put("lastSeenTime", event.getLastSeenTime());
        map.put("createTime", event.getCreateTime());
        map.put("updateTime", event.getUpdateTime());
        map.put("translatedLanguage", "zh-CN");
        map.put("isTranslated", false);
        return map;
    }

    public List<String> getAvailableSources() {
        return hotEventRepository.findDistinctSources();
    }

    public List<String> getAvailableCategories() {
        return hotEventRepository.findDistinctCategories();
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> statistics = new LinkedHashMap<>();

        CompletableFuture<List<Object[]>> sourceCountsFuture = CompletableFuture.supplyAsync(
                hotEventRepository::countBySource,
                asyncTaskExecutor.getIoExecutor()
        );
        CompletableFuture<List<Object[]>> categoryCountsFuture = CompletableFuture.supplyAsync(
                hotEventRepository::countByCategory,
                asyncTaskExecutor.getIoExecutor()
        );
        CompletableFuture<List<HotEvent>> topEventsFuture = CompletableFuture.supplyAsync(() -> {
            LocalDateTime startTime = LocalDateTime.now().minusHours(24);
            return hotEventRepository.findTopHotEvents(startTime, PageRequest.of(0, 10));
        }, asyncTaskExecutor.getIoExecutor());

        return CompletableFuture.allOf(sourceCountsFuture, categoryCountsFuture, topEventsFuture)
                .thenApply(v -> {
                    Map<String, Long> sourceStats = new LinkedHashMap<>();
                    long totalCount = 0;
                    for (Object[] row : sourceCountsFuture.join()) {
                        String source = (String) row[0];
                        Long count = (Long) row[1];
                        sourceStats.put(source, count);
                        totalCount += count;
                    }
                    statistics.put("sourceStats", sourceStats);
                    statistics.put("totalCount", totalCount);

                    Map<String, Long> categoryStats = new LinkedHashMap<>();
                    for (Object[] row : categoryCountsFuture.join()) {
                        String category = (String) row[0];
                        Long count = (Long) row[1];
                        categoryStats.put(category, count);
                    }
                    statistics.put("categoryStats", categoryStats);
                    statistics.put("topEvents", topEventsFuture.join());
                    return statistics;
                }).join();
    }

    public CompletableFuture<Map<String, Object>> getStatisticsAsync() {
        Map<String, Object> statistics = new LinkedHashMap<>();

        CompletableFuture<List<Object[]>> sourceCountsFuture = CompletableFuture.supplyAsync(
                hotEventRepository::countBySource,
                asyncTaskExecutor.getIoExecutor()
        );
        CompletableFuture<List<Object[]>> categoryCountsFuture = CompletableFuture.supplyAsync(
                hotEventRepository::countByCategory,
                asyncTaskExecutor.getIoExecutor()
        );
        CompletableFuture<List<HotEvent>> topEventsFuture = CompletableFuture.supplyAsync(() -> {
            LocalDateTime startTime = LocalDateTime.now().minusHours(24);
            return hotEventRepository.findTopHotEvents(startTime, PageRequest.of(0, 10));
        }, asyncTaskExecutor.getIoExecutor());

        return CompletableFuture.allOf(sourceCountsFuture, categoryCountsFuture, topEventsFuture)
                .thenApply(v -> {
                    Map<String, Long> sourceStats = new LinkedHashMap<>();
                    long totalCount = 0;
                    for (Object[] row : sourceCountsFuture.join()) {
                        String source = (String) row[0];
                        Long count = (Long) row[1];
                        sourceStats.put(source, count);
                        totalCount += count;
                    }
                    statistics.put("sourceStats", sourceStats);
                    statistics.put("totalCount", totalCount);

                    Map<String, Long> categoryStats = new LinkedHashMap<>();
                    for (Object[] row : categoryCountsFuture.join()) {
                        String category = (String) row[0];
                        Long count = (Long) row[1];
                        categoryStats.put(category, count);
                    }
                    statistics.put("categoryStats", categoryStats);
                    statistics.put("topEvents", topEventsFuture.join());
                    return statistics;
                });
    }

    public List<HotEvent> getHotEventsBySourceAndTimeRange(String source, LocalDateTime startTime, LocalDateTime endTime) {
        return hotEventRepository.findBySourceAndTimeRange(source, startTime, endTime);
    }

    public boolean deleteHotEvent(Long id) {
        HotEvent event = hotEventRepository.findById(id).orElse(null);
        if (event != null) {
            HotEvent oldEvent = cloneHotEvent(event);
            event.setDeleted(true);
            hotEventRepository.save(event);
            hotEventLogService.logDelete(oldEvent, "手动删除热点事件");
            return true;
        }
        return false;
    }

    public HotEvent saveHotEvent(HotEvent hotEvent) {
        boolean isNew = hotEvent.getId() == null;
        HotEvent oldEvent = null;
        if (!isNew) {
            oldEvent = hotEventRepository.findById(hotEvent.getId()).orElse(null);
        }

        HotEvent saved = hotEventRepository.save(hotEvent);

        if (isNew) {
            hotEventLogService.logInsert(saved, "手动新增热点事件");
        } else if (oldEvent != null) {
            hotEventLogService.logUpdate(oldEvent, saved, "手动更新热点事件");
        }

        autoTranslateEventAsync(saved).exceptionally(ex -> {
            log.warn("Auto-translation on save failed for event {}: {}", saved.getId(), ex.getMessage());
            return null;
        });
        return saved;
    }

    private HotEvent cloneHotEvent(HotEvent event) {
        if (event == null) return null;
        HotEvent clone = new HotEvent();
        clone.setId(event.getId());
        clone.setTitle(event.getTitle());
        clone.setDescription(event.getDescription());
        clone.setSource(event.getSource());
        clone.setSourceUrl(event.getSourceUrl());
        clone.setHotValue(event.getHotValue());
        clone.setHotRank(event.getHotRank());
        clone.setCategory(event.getCategory());
        clone.setImageUrl(event.getImageUrl());
        clone.setIsHot(event.getIsHot());
        clone.setIsRising(event.getIsRising());
        clone.setRisingRate(event.getRisingRate());
        clone.setCrawlTime(event.getCrawlTime());
        clone.setFirstSeenTime(event.getFirstSeenTime());
        clone.setLastSeenTime(event.getLastSeenTime());
        clone.setDeleted(event.getDeleted());
        clone.setCreateTime(event.getCreateTime());
        clone.setUpdateTime(event.getUpdateTime());
        return clone;
    }

    public List<EventTranslation> getEventTranslations(Long eventId) {
        return eventTranslationRepository.findByEventId(eventId);
    }

    public List<HotEvent> getAllHotEventsForExport(String source, String keyword) {
        return getAllHotEventsForExport(source, keyword, null, null, null);
    }

    public List<HotEvent> getAllHotEventsForExport(String source, String keyword, String category,
                                                   LocalDateTime startTime, LocalDateTime endTime) {
        Sort sort = Sort.by(Sort.Direction.DESC, "hotValue");
        Specification<HotEvent> spec = buildSearchSpecification(source, keyword, category, startTime, endTime);
        return hotEventRepository.findAll(spec, sort);
    }

    public EventTranslation updateTranslation(Long eventId, String language, String title, String description, String category) {
        Optional<EventTranslation> existing = eventTranslationRepository.findByEventIdAndLanguage(eventId, language);
        EventTranslation translation;
        boolean isNew = !existing.isPresent();
        String oldTitle = null;
        String oldDescription = null;
        String oldCategory = null;

        if (existing.isPresent()) {
            translation = existing.get();
            oldTitle = translation.getTitle();
            oldDescription = translation.getDescription();
            oldCategory = translation.getCategory();
            if (title != null) translation.setTitle(title);
            if (description != null) translation.setDescription(description);
            if (category != null) translation.setCategory(category);
            translation.setIsVerified(true);
        } else {
            translation = new EventTranslation();
            translation.setEventId(eventId);
            translation.setLanguage(language);
            translation.setTitle(title);
            translation.setDescription(description);
            translation.setCategory(category);
            translation.setTranslationProvider("manual");
            translation.setIsVerified(true);
        }
        EventTranslation saved = eventTranslationRepository.save(translation);

        String cacheKey = eventId + "_" + language;
        translationCacheLocal.put(cacheKey, saved);

        try {
            HotEvent event = hotEventRepository.findById(eventId).orElse(null);
            if (event != null) {
                if (isNew) {
                    hotEventLogService.logFieldChange(event, "translation_" + language, null,
                            "title=" + title + ",desc=" + description, "手动新增翻译");
                } else {
                    if (!Objects.equals(oldTitle, title) || !Objects.equals(oldDescription, description)
                            || !Objects.equals(oldCategory, category)) {
                        hotEventLogService.logFieldChange(event, "translation_" + language,
                                "title=" + oldTitle + ",desc=" + oldDescription,
                                "title=" + title + ",desc=" + description, "手动更新翻译");
                    }
                }
            }
        } catch (Exception e) {
            log.warn("记录翻译修改日志失败[eventId={},language={}]: {}", eventId, language, e.getMessage());
        }

        return saved;
    }

    @Transactional
    public int rerankAllSources() {
        List<String> sources = getAvailableSources();
        int totalReranked = 0;
        for (String source : sources) {
            totalReranked += rerankBySource(source);
        }
        log.info("全平台排名重排完成，共重排{}条记录", totalReranked);
        return totalReranked;
    }

    @Transactional
    public int rerankBySource(String source) {
        if (source == null || source.trim().isEmpty()) {
            return 0;
        }

        try {
            LocalDateTime recentTime = LocalDateTime.now().minusHours(24);
            List<HotEvent> events = hotEventRepository
                    .findBySourceAndCrawlTimeAfterOrderByHotValueDesc(source, recentTime);

            if (events == null || events.isEmpty()) {
                return 0;
            }

            int count = 0;
            for (int i = 0; i < events.size(); i++) {
                HotEvent event = events.get(i);
                int newRank = i + 1;
                if (event.getHotRank() == null || event.getHotRank() != newRank) {
                    event.setHotRank(newRank);
                    hotEventRepository.save(event);
                    count++;
                }
            }
            log.info("[排名重排][{}] 重排完成，更新{}条记录", source, count);
            return count;
        } catch (Exception e) {
            log.warn("[排名重排][{}] 重排失败: {}", source, e.getMessage());
            return 0;
        }
    }

    public List<HotEvent> getRankedHotEvents(String source, int limit) {
        List<HotEvent> events;
        if (source != null && !source.isEmpty()) {
            events = hotEventRepository.findBySourceOrderByHotValueDesc(source);
        } else {
            events = hotEventRepository.findAll(Sort.by(Sort.Direction.DESC, "hotValue"));
        }

        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }

        for (int i = 0; i < events.size(); i++) {
            events.get(i).setHotRank(i + 1);
        }

        if (limit > 0 && events.size() > limit) {
            return events.subList(0, limit);
        }
        return events;
    }
}
