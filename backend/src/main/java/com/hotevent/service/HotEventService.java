package com.hotevent.service;

import com.hotevent.common.PageResult;
import com.hotevent.entity.EventTranslation;
import com.hotevent.entity.HotEvent;
import com.hotevent.i18n.I18nProperties;
import com.hotevent.i18n.TranslationService;
import com.hotevent.repository.EventTranslationRepository;
import com.hotevent.repository.HotEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

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

    public PageResult<HotEvent> getHotEventList(String source, String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "hotValue"));
        Page<HotEvent> hotEventPage;

        if (keyword != null && !keyword.isEmpty()) {
            hotEventPage = hotEventRepository.findByTitleContainingAndDeletedFalse(keyword, pageable);
        } else if (source != null && !source.isEmpty()) {
            hotEventPage = hotEventRepository.findBySourceAndDeletedFalse(source, pageable);
        } else {
            hotEventPage = hotEventRepository.findByDeletedFalse(pageable);
        }

        return PageResult.of(
                hotEventPage.getContent(),
                hotEventPage.getTotalElements(),
                page,
                size
        );
    }

    public PageResult<Map<String, Object>> getHotEventListLocalized(String source, String keyword, int page, int size, String lang) {
        PageResult<HotEvent> original = getHotEventList(source, keyword, page, size);

        List<Map<String, Object>> localizedList = new ArrayList<>();
        for (HotEvent event : original.getRecords()) {
            Map<String, Object> localized = localizeHotEvent(event, lang);
            localizedList.add(localized);
        }

        return PageResult.of(localizedList, original.getTotal(), original.getCurrent(), original.getSize());
    }

    public HotEvent getHotEventById(Long id) {
        return hotEventRepository.findById(id).orElse(null);
    }

    public Map<String, Object> getHotEventByIdLocalized(Long id, String lang) {
        HotEvent event = getHotEventById(id);
        if (event == null) return null;
        return localizeHotEvent(event, lang);
    }

    public Map<String, Object> localizeHotEvent(HotEvent event, String targetLang) {
        if (event == null) return null;
        if (targetLang == null || targetLang.isEmpty() || "zh-CN".equals(targetLang)) {
            return hotEventToMap(event);
        }

        Optional<EventTranslation> existingTranslation = eventTranslationRepository
                .findByEventIdAndLanguage(event.getId(), targetLang);

        if (existingTranslation.isPresent()) {
            EventTranslation translation = existingTranslation.get();
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
        } catch (Exception e) {
            log.warn("Failed to save translation for event {}: {}", event.getId(), e.getMessage());
        }
    }

    public void autoTranslateEvent(HotEvent event) {
        if (!i18nProperties.getTranslation().isAutoTranslateOnCrawl()) return;

        for (String targetLang : i18nProperties.getSupportedLocales()) {
            if ("zh-CN".equals(targetLang)) continue;

            if (eventTranslationRepository.existsByEventIdAndLanguage(event.getId(), targetLang)) {
                continue;
            }

            try {
                Map<String, Object> translated = translationService.translateEvent(
                        event.getTitle(),
                        event.getDescription(),
                        event.getCategory(),
                        "zh-CN",
                        targetLang
                );
                saveTranslation(event, targetLang, translated);
            } catch (Exception e) {
                log.warn("Auto-translation failed for event {} to {}: {}", event.getId(), targetLang, e.getMessage());
            }
        }
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

    public Map<String, Object> getStatistics() {
        Map<String, Object> statistics = new HashMap<>();

        List<Object[]> sourceCounts = hotEventRepository.countBySource();
        Map<String, Long> sourceStats = new HashMap<>();
        long totalCount = 0;
        for (Object[] row : sourceCounts) {
            String source = (String) row[0];
            Long count = (Long) row[1];
            sourceStats.put(source, count);
            totalCount += count;
        }
        statistics.put("sourceStats", sourceStats);
        statistics.put("totalCount", totalCount);

        List<Object[]> categoryCounts = hotEventRepository.countByCategory();
        Map<String, Long> categoryStats = new HashMap<>();
        for (Object[] row : categoryCounts) {
            String category = (String) row[0];
            Long count = (Long) row[1];
            categoryStats.put(category, count);
        }
        statistics.put("categoryStats", categoryStats);

        LocalDateTime startTime = LocalDateTime.now().minusHours(24);
        List<HotEvent> topEvents = hotEventRepository.findTopHotEvents(
                startTime, PageRequest.of(0, 10));
        statistics.put("topEvents", topEvents);

        return statistics;
    }

    public List<HotEvent> getHotEventsBySourceAndTimeRange(String source, LocalDateTime startTime, LocalDateTime endTime) {
        return hotEventRepository.findBySourceAndTimeRange(source, startTime, endTime);
    }

    public boolean deleteHotEvent(Long id) {
        HotEvent event = hotEventRepository.findById(id).orElse(null);
        if (event != null) {
            event.setDeleted(true);
            hotEventRepository.save(event);
            return true;
        }
        return false;
    }

    public HotEvent saveHotEvent(HotEvent hotEvent) {
        HotEvent saved = hotEventRepository.save(hotEvent);
        try {
            autoTranslateEvent(saved);
        } catch (Exception e) {
            log.warn("Auto-translation on save failed for event {}: {}", saved.getId(), e.getMessage());
        }
        return saved;
    }

    public List<EventTranslation> getEventTranslations(Long eventId) {
        return eventTranslationRepository.findByEventId(eventId);
    }

    public EventTranslation updateTranslation(Long eventId, String language, String title, String description, String category) {
        Optional<EventTranslation> existing = eventTranslationRepository.findByEventIdAndLanguage(eventId, language);
        EventTranslation translation;
        if (existing.isPresent()) {
            translation = existing.get();
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
        return eventTranslationRepository.save(translation);
    }
}
