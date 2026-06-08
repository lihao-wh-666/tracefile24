package com.hotevent.service;

import com.hotevent.common.PageResult;
import com.hotevent.entity.HotEvent;
import com.hotevent.repository.HotEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class HotEventService {

    @Autowired
    private HotEventRepository hotEventRepository;

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

    public HotEvent getHotEventById(Long id) {
        return hotEventRepository.findById(id).orElse(null);
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
        return hotEventRepository.save(hotEvent);
    }
}
