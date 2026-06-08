package com.hotevent.service;

import com.hotevent.common.PageResult;
import com.hotevent.entity.CrawlRecord;
import com.hotevent.repository.CrawlRecordRepository;
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
public class CrawlRecordService {

    @Autowired
    private CrawlRecordRepository crawlRecordRepository;

    public PageResult<CrawlRecord> getCrawlRecordList(String source, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "crawlTime"));
        Page<CrawlRecord> recordPage;

        if (source != null && !source.isEmpty()) {
            recordPage = crawlRecordRepository.findBySource(source, pageable);
        } else {
            recordPage = crawlRecordRepository.findAll(pageable);
        }

        return PageResult.of(
                recordPage.getContent(),
                recordPage.getTotalElements(),
                page,
                size
        );
    }

    public List<CrawlRecord> getRecentRecords(String source, int hours) {
        LocalDateTime startTime = LocalDateTime.now().minusHours(hours);
        if (source != null && !source.isEmpty()) {
            return crawlRecordRepository.findBySourceAndCrawlTimeAfterOrderByCrawlTimeDesc(source, startTime);
        } else {
            return crawlRecordRepository.findByCrawlTimeAfterOrderByCrawlTimeDesc(startTime);
        }
    }

    public Map<String, Object> getCrawlStatistics(int days) {
        Map<String, Object> result = new HashMap<>();
        LocalDateTime startTime = LocalDateTime.now().minusDays(days);

        List<Object[]> stats = crawlRecordRepository.getCrawlStatistics(startTime);
        Map<String, Map<String, Long>> sourceStats = new HashMap<>();

        long totalCrawls = 0;
        long totalSuccess = 0;
        long totalFail = 0;

        for (Object[] row : stats) {
            String source = (String) row[0];
            Long count = (Long) row[1];
            Long success = (Long) row[2];
            Long fail = (Long) row[3];

            Map<String, Long> sourceData = new HashMap<>();
            sourceData.put("count", count);
            sourceData.put("success", success);
            sourceData.put("fail", fail);
            sourceStats.put(source, sourceData);

            totalCrawls += count;
            totalSuccess += success != null ? success : 0;
            totalFail += fail != null ? fail : 0;
        }

        result.put("sourceStats", sourceStats);
        result.put("totalCrawls", totalCrawls);
        result.put("totalSuccess", totalSuccess);
        result.put("totalFail", totalFail);
        result.put("days", days);

        return result;
    }
}
