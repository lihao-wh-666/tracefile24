package com.hotevent.controller;

import com.hotevent.common.PageResult;
import com.hotevent.common.Result;
import com.hotevent.entity.CrawlRecord;
import com.hotevent.service.CrawlRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/crawl-records")
public class CrawlRecordController {

    @Autowired
    private CrawlRecordService crawlRecordService;

    @GetMapping
    public Result<PageResult<CrawlRecord>> getCrawlRecordList(
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResult<CrawlRecord> result = crawlRecordService.getCrawlRecordList(source, page, size);
        return Result.success(result);
    }

    @GetMapping("/recent")
    public Result<List<CrawlRecord>> getRecentRecords(
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "24") int hours) {
        List<CrawlRecord> records = crawlRecordService.getRecentRecords(source, hours);
        return Result.success(records);
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> getCrawlStatistics(
            @RequestParam(defaultValue = "7") int days) {
        Map<String, Object> statistics = crawlRecordService.getCrawlStatistics(days);
        return Result.success(statistics);
    }
}
