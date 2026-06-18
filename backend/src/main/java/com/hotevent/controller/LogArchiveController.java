package com.hotevent.controller;

import com.hotevent.common.PageResult;
import com.hotevent.common.Result;
import com.hotevent.entity.LogArchive;
import com.hotevent.service.LogArchiveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/log-archives")
public class LogArchiveController {

    @Autowired
    private LogArchiveService logArchiveService;

    @GetMapping
    public Result<PageResult<LogArchive>> getArchives(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String logType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResult<LogArchive> result = logArchiveService.getArchives(status, logType, page, size);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<LogArchive> getArchiveById(@PathVariable Long id) {
        return logArchiveService.getArchiveById(id)
                .map(Result::success)
                .orElse(Result.error("归档记录不存在"));
    }

    @PostMapping
    public Result<LogArchive> createArchive(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "DATABASE_LOG") String logType,
            @RequestParam(required = false) String remark) {
        try {
            LogArchive archive = logArchiveService.executeArchive(startTime, endTime, logType, remark);
            return Result.success(archive);
        } catch (Exception e) {
            return Result.error("归档失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteArchive(@PathVariable Long id) {
        try {
            logArchiveService.deleteArchive(id);
            return Result.success();
        } catch (Exception e) {
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadArchive(@PathVariable Long id) {
        Path filePath = logArchiveService.getArchiveFilePath(id);
        if (filePath == null) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(filePath);
        String fileName = filePath.getFileName().toString();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(resource);
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics() {
        Map<String, Object> statistics = logArchiveService.getArchiveStatistics();
        return Result.success(statistics);
    }

    @PostMapping("/auto")
    public Result<Void> triggerAutoArchive() {
        try {
            logArchiveService.executeAutoArchive();
            return Result.success();
        } catch (Exception e) {
            return Result.error("自动归档失败: " + e.getMessage());
        }
    }
}
