package com.hotevent.controller;

import com.hotevent.common.PageResult;
import com.hotevent.common.Result;
import com.hotevent.entity.FrontendLog;
import com.hotevent.service.FrontendLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/frontend-logs")
public class FrontendLogController {

    @Autowired
    private FrontendLogService frontendLogService;

    @PostMapping("/report")
    public Result<FrontendLog> reportLog(@RequestBody FrontendLog frontendLog) {
        try {
            FrontendLog saved = frontendLogService.saveLog(frontendLog);
            return Result.success(saved);
        } catch (Exception e) {
            return Result.error("上报失败: " + e.getMessage());
        }
    }

    @PostMapping("/batch")
    public Result<List<FrontendLog>> reportLogs(@RequestBody List<FrontendLog> logs) {
        try {
            List<FrontendLog> saved = frontendLogService.saveLogs(logs);
            return Result.success(saved);
        } catch (Exception e) {
            return Result.error("批量上报失败: " + e.getMessage());
        }
    }

    @GetMapping
    public Result<PageResult<FrontendLog>> getLogs(
            @RequestParam(required = false) String logLevel,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageResult<FrontendLog> result = frontendLogService.getLogs(
                logLevel, userId, username, startTime, endTime, page, size);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<FrontendLog> getLogById(@PathVariable Long id) {
        return frontendLogService.getLogById(id)
                .map(Result::success)
                .orElse(Result.error("日志不存在"));
    }

    @GetMapping("/levels")
    public Result<List<String>> getAvailableLevels() {
        List<String> levels = frontendLogService.getAvailableLevels();
        return Result.success(levels);
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        Map<String, Object> statistics = frontendLogService.getStatistics(startTime, endTime);
        return Result.success(statistics);
    }
}
