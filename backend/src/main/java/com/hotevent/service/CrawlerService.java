package com.hotevent.service;

import com.hotevent.crawler.HotEventCrawler;
import com.hotevent.entity.CrawlRecord;
import com.hotevent.entity.HotEvent;
import com.hotevent.repository.CrawlRecordRepository;
import com.hotevent.repository.HotEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class CrawlerService {

    @Autowired
    private List<HotEventCrawler> crawlers;

    @Autowired
    private HotEventRepository hotEventRepository;

    @Autowired
    private CrawlRecordRepository crawlRecordRepository;

    @Transactional
    public void crawlAllSources() {
        for (HotEventCrawler crawler : crawlers) {
            if (crawler.isEnabled()) {
                crawlSource(crawler);
            }
        }
    }

    @Transactional
    public CrawlRecord crawlSource(HotEventCrawler crawler) {
        String source = crawler.getSourceName();
        log.info("开始抓取{}热搜数据...", source);
        long startTime = System.currentTimeMillis();
        CrawlRecord record = new CrawlRecord();
        record.setSource(source);
        record.setCrawlTime(LocalDateTime.now());

        try {
            List<HotEvent> events = crawler.crawl();
            record.setEventCount(events.size());

            int successCount = 0;
            int failCount = 0;

            for (HotEvent event : events) {
                try {
                    saveOrUpdateEvent(event);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    log.error("保存热点事件失败: {}", event.getTitle(), e);
                }
            }

            record.setSuccessCount(successCount);
            record.setFailCount(failCount);
            record.setStatus("success");
            log.info("{}热搜抓取完成，共{}条，成功{}条，失败{}条", source, events.size(), successCount, failCount);
        } catch (Exception e) {
            record.setStatus("failed");
            record.setErrorMessage(e.getMessage());
            record.setSuccessCount(0);
            record.setFailCount(0);
            record.setEventCount(0);
            log.error("{}热搜抓取失败", source, e);
        }

        long costTime = System.currentTimeMillis() - startTime;
        record.setCostTimeMs(costTime);

        return crawlRecordRepository.save(record);
    }

    @Transactional
    public CrawlRecord crawlSourceByName(String sourceName) {
        for (HotEventCrawler crawler : crawlers) {
            if (crawler.getSourceName().equals(sourceName) && crawler.isEnabled()) {
                return crawlSource(crawler);
            }
        }
        throw new RuntimeException("未找到数据源: " + sourceName);
    }

    private void saveOrUpdateEvent(HotEvent event) {
        Optional<HotEvent> existing = hotEventRepository.findBySourceAndTitleAndDeletedFalse(
                event.getSource(), event.getTitle());

        if (existing.isPresent()) {
            HotEvent existingEvent = existing.get();
            existingEvent.setHotValue(event.getHotValue());
            existingEvent.setHotRank(event.getHotRank());
            existingEvent.setIsRising(event.getIsRising());
            existingEvent.setRisingRate(event.getRisingRate());
            existingEvent.setLastSeenTime(LocalDateTime.now());
            existingEvent.setCrawlTime(event.getCrawlTime());
            if (event.getDescription() != null && existingEvent.getDescription() == null) {
                existingEvent.setDescription(event.getDescription());
            }
            if (event.getImageUrl() != null && existingEvent.getImageUrl() == null) {
                existingEvent.setImageUrl(event.getImageUrl());
            }
            if (event.getCategory() != null && existingEvent.getCategory() == null) {
                existingEvent.setCategory(event.getCategory());
            }
            hotEventRepository.save(existingEvent);
        } else {
            hotEventRepository.save(event);
        }
    }

    public List<String> getAvailableSources() {
        List<String> sources = new ArrayList<>();
        for (HotEventCrawler crawler : crawlers) {
            if (crawler.isEnabled()) {
                sources.add(crawler.getSourceName());
            }
        }
        return sources;
    }
}
