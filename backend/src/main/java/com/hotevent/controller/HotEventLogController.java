package com.hotevent.controller;

import com.hotevent.common.PageResult;
import com.hotevent.common.Result;
import com.hotevent.entity.HotEventLog;
import com.hotevent.service.HotEventLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/event-logs")
public class HotEventLogController {

    @Autowired
    private HotEventLogService hotEventLogService;

    @GetMapping
    public Result<PageResult<HotEventLog>> getLogs(
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) String operationType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageResult<HotEventLog> result = hotEventLogService.getLogs(
                eventId, source, operator, startTime, endTime, operationType, page, size);
        return Result.success(result);
    }

    @GetMapping("/event/{eventId}")
    public Result<List<HotEventLog>> getEventLogs(@PathVariable Long eventId) {
        List<HotEventLog> logs = hotEventLogService.getEventLogs(eventId);
        return Result.success(logs);
    }

    @GetMapping("/sources")
    public Result<List<String>> getAvailableSources() {
        List<String> sources = hotEventLogService.getAvailableSources();
        return Result.success(sources);
    }

    @GetMapping("/operators")
    public Result<List<String>> getAvailableOperators() {
        List<String> operators = hotEventLogService.getAvailableOperators();
        return Result.success(operators);
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        Map<String, Object> statistics = hotEventLogService.getStatistics(startTime, endTime);
        return Result.success(statistics);
    }
}
