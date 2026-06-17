package com.hotevent.crawler.storage;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hotevent.crawler.core.DataItem;
import com.hotevent.crawler.filter.SensitiveCheckResult;
import com.hotevent.crawler.filter.SensitiveContentFilter;
import com.hotevent.entity.HotEvent;
import com.hotevent.repository.HotEventRepository;
import com.hotevent.service.HotEventLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class DataStorageService {

    @Autowired
    private HotEventRepository hotEventRepository;

    @Autowired
    private IncrementalCrawlManager incrementalCrawlManager;

    @Autowired
    private SensitiveContentFilter sensitiveContentFilter;

    @Autowired
    private HotEventLogService hotEventLogService;

    private static final int BATCH_SIZE = 50;

    @Transactional
    public int saveBatch(List<DataItem> items, String source) {
        if (items == null || items.isEmpty()) return 0;

        AtomicInteger insertCount = new AtomicInteger(0);
        AtomicInteger updateCount = new AtomicInteger(0);
        AtomicInteger skipCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        List<HotEvent> batchInsert = new ArrayList<>(BATCH_SIZE);
        List<HotEvent> allInserted = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            DataItem item = items.get(i);
            try {
                SaveResult result = saveOrUpdate(item, source);
                switch (result.status) {
                    case INSERTED:
                        batchInsert.add(result.event);
                        allInserted.add(result.event);
                        insertCount.incrementAndGet();
                        break;
                    case UPDATED:
                        updateCount.incrementAndGet();
                        break;
                    case SKIPPED:
                        skipCount.incrementAndGet();
                        break;
                }

                if (batchInsert.size() >= BATCH_SIZE) {
                    hotEventRepository.saveAll(batchInsert);
                    batchInsert.clear();
                }

            } catch (Exception e) {
                errorCount.incrementAndGet();
                log.warn("保存数据项异常[{}#{}]: {}", source, i, e.getMessage());
            }
        }

        if (!batchInsert.isEmpty()) {
            hotEventRepository.saveAll(batchInsert);
        }

        for (HotEvent event : allInserted) {
            try {
                hotEventLogService.logInsert(event, "爬虫自动新增");
            } catch (Exception e) {
                log.warn("记录新增日志失败[{}]: {}", event.getId(), e.getMessage());
            }
        }

        incrementalCrawlManager.recordStats(source, null, insertCount.get(), updateCount.get());

        int total = insertCount.get() + updateCount.get();
        log.info("[存储][{}] 批处理完成: 新增={}, 更新={}, 跳过={}, 错误={}, 总计={}",
                source, insertCount.get(), updateCount.get(), skipCount.get(), errorCount.get(), total);
        return total;
    }

    public SaveResult saveOrUpdate(DataItem item, String source) {
        if (item == null) return new SaveResult(null, SaveStatus.SKIPPED);
        if (StrUtil.isBlank(item.getTitle())) return new SaveResult(null, SaveStatus.SKIPPED);

        SensitiveCheckResult checkResult = sensitiveContentFilter.check(item);
        if (checkResult.isSensitive()) {
            log.info("[存储层] 过滤敏感内容: 标题={}, 命中词={}, 类型={}",
                    item.getTitle(),
                    checkResult.getMatchedWords(),
                    checkResult.getMatchedTypes().stream()
                            .map(t -> t.getDisplayName()).toList());
            return new SaveResult(null, SaveStatus.SKIPPED);
        }

        String normalizedSource = source != null ? source : item.getPlatform();
        String normalizedTitle = item.getTitle().trim();

        item.setSource(normalizedSource);

        Optional<HotEvent> existing = hotEventRepository.findBySourceAndTitleAndDeletedFalse(
                normalizedSource, normalizedTitle);

        HotEvent event;
        boolean isNew = false;

        if (existing.isPresent()) {
            event = existing.get();
            HotEvent oldEvent = cloneHotEvent(event);
            boolean changed = updateHotEventFromDataItem(event, item);
            event.setLastSeenTime(LocalDateTime.now());
            event.setCrawlTime(item.getCrawlTime() != null ? item.getCrawlTime() : LocalDateTime.now());
            event.setIsHot(true);
            if (changed) {
                hotEventRepository.save(event);
                try {
                    hotEventLogService.logUpdate(oldEvent, event, "爬虫自动更新");
                } catch (Exception e) {
                    log.warn("记录更新日志失败[{}]: {}", event.getId(), e.getMessage());
                }
                return new SaveResult(event, SaveStatus.UPDATED);
            }
            return new SaveResult(event, SaveStatus.SKIPPED);
        } else {
            event = dataItemToHotEvent(item, normalizedSource, normalizedTitle);
            isNew = true;
            return new SaveResult(event, SaveStatus.INSERTED);
        }
    }

    private HotEvent dataItemToHotEvent(DataItem item, String source, String title) {
        HotEvent event = new HotEvent();
        event.setTitle(title);
        event.setSource(source);
        event.setSourceUrl(item.getUrl());
        event.setHotValue(item.getHotValue() != null ? item.getHotValue()
                : calculateHotValueFromDataItem(item));
        event.setHotRank(item.getHotRank());
        event.setIsHot(true);
        event.setIsRising(calculateIsRising(item));
        event.setRisingRate(calculateRisingRate(item));

        String desc = item.getContent() != null ? item.getContent() : item.getSummary();
        if (desc != null && desc.length() > 2000) desc = desc.substring(0, 2000);
        event.setDescription(desc);

        event.setImageUrl(item.getCoverImage());
        event.setCategory(StrUtil.isNotBlank(item.getCategory()) ? item.getCategory() : getCategoryFromSource(source));
        event.setCrawlTime(item.getCrawlTime() != null ? item.getCrawlTime() : LocalDateTime.now());
        event.setFirstSeenTime(LocalDateTime.now());
        event.setLastSeenTime(LocalDateTime.now());
        event.setDeleted(false);

        if (item.getExtra() != null && !item.getExtra().isEmpty()) {
            try {
                String extraJson = JSONUtil.toJsonStr(item.getExtra());
                if (event.getDescription() != null) {
                    event.setDescription(event.getDescription() + "\n[extra]" + extraJson);
                }
            } catch (Exception ignored) {}
        }
        return event;
    }

    private boolean updateHotEventFromDataItem(HotEvent e, DataItem item) {
        boolean changed = false;
        if (item.getHotValue() != null && !item.getHotValue().equals(e.getHotValue())) {
            e.setHotValue(item.getHotValue());
            changed = true;
        }
        if (item.getHotRank() != null && !item.getHotRank().equals(e.getHotRank())) {
            e.setHotRank(item.getHotRank());
            changed = true;
        }
        boolean rising = calculateIsRising(item);
        if (!Objects.equals(rising, e.getIsRising())) {
            e.setIsRising(rising);
            changed = true;
        }
        double rate = calculateRisingRate(item);
        if (Math.abs(rate - (e.getRisingRate() != null ? e.getRisingRate() : 0.0)) > 0.01) {
            e.setRisingRate(rate);
            changed = true;
        }
        if (StrUtil.isNotBlank(item.getContent()) || StrUtil.isNotBlank(item.getSummary())) {
            String desc = item.getContent() != null ? item.getContent() : item.getSummary();
            if (StrUtil.isBlank(e.getDescription()) && StrUtil.isNotBlank(desc)) {
                if (desc.length() > 2000) desc = desc.substring(0, 2000);
                e.setDescription(desc);
                changed = true;
            }
        }
        if (StrUtil.isBlank(e.getImageUrl()) && StrUtil.isNotBlank(item.getCoverImage())) {
            e.setImageUrl(item.getCoverImage());
            changed = true;
        }
        if (StrUtil.isBlank(e.getCategory()) && StrUtil.isNotBlank(item.getCategory())) {
            e.setCategory(item.getCategory());
            changed = true;
        }
        if (StrUtil.isBlank(e.getSourceUrl()) && StrUtil.isNotBlank(item.getUrl())) {
            e.setSourceUrl(item.getUrl());
            changed = true;
        }
        if (changed) {
            hotEventRepository.save(e);
        }
        return changed;
    }

    private Long calculateHotValueFromDataItem(DataItem item) {
        long total = 0;
        if (item.getViewCount() != null) total += item.getViewCount();
        if (item.getLikeCount() != null) total += item.getLikeCount() * 3;
        if (item.getCommentCount() != null) total += item.getCommentCount() * 5;
        if (item.getShareCount() != null) total += item.getShareCount() * 10;
        return total > 0 ? total : null;
    }

    private boolean calculateIsRising(DataItem item) {
        if (item.getHotRank() != null && item.getHotRank() <= 10) return true;
        if (item.getHotValue() != null && item.getHotValue() > 500000) return true;
        if (item.getCommentCount() != null && item.getCommentCount() > 5000) return true;
        return false;
    }

    private double calculateRisingRate(DataItem item) {
        long hv = item.getHotValue() != null ? item.getHotValue() : 0L;
        int rank = item.getHotRank() != null ? item.getHotRank() : 999;
        double baseRate = (hv / 1000000.0) * 50;
        double rankFactor = Math.max(0, (50.0 - rank) / 50.0) * 50;
        double jitter = Math.random() * 10;
        return Math.round((baseRate + rankFactor + jitter) * 100.0) / 100.0;
    }

    private String getCategoryFromSource(String source) {
        switch (source) {
            case "douyin": return "抖音";
            case "bilibili": return "B站";
            case "baidu": return "百度";
            case "zhihu": return "知乎";
            case "weibo": return "微博";
            default: return source;
        }
    }

    public static class SaveResult {
        public final HotEvent event;
        public final SaveStatus status;
        public SaveResult(HotEvent event, SaveStatus status) {
            this.event = event;
            this.status = status;
        }
    }

    public enum SaveStatus {
        INSERTED, UPDATED, SKIPPED
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
}
