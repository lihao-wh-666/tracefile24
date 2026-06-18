package com.hotevent.service;

import com.hotevent.common.PageResult;
import com.hotevent.entity.FrontendLog;
import com.hotevent.entity.HotEventLog;
import com.hotevent.entity.LogArchive;
import com.hotevent.repository.FrontendLogRepository;
import com.hotevent.repository.HotEventLogRepository;
import com.hotevent.repository.LogArchiveRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
public class LogArchiveService {

    @Autowired
    private LogArchiveRepository logArchiveRepository;

    @Autowired
    private HotEventLogRepository hotEventLogRepository;

    @Autowired
    private FrontendLogRepository frontendLogRepository;

    @Value("${hot-event.log-archive.enabled:true}")
    private boolean archiveEnabled;

    @Value("${hot-event.log-archive.archive-path:./log-archives}")
    private String archivePath;

    @Value("${hot-event.log-archive.backend-log-path:./logs}")
    private String backendLogPath;

    @Value("${hot-event.log-archive.retention-days:30}")
    private int retentionDays;

    @Value("${hot-event.log-archive.backend-retention-days:30}")
    private int backendRetentionDays;

    @Value("${hot-event.log-archive.frontend-retention-days:30}")
    private int frontendRetentionDays;

    @Value("${hot-event.log-archive.batch-size:1000}")
    private int batchSize;

    @Value("${hot-event.log-archive.delete-after-archive:true}")
    private boolean deleteAfterArchive;

    public PageResult<LogArchive> getArchives(String status, String logType, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));
        Specification<LogArchive> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isEmpty()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (logType != null && !logType.isEmpty()) {
                predicates.add(cb.equal(root.get("logType"), logType));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<LogArchive> archivePage = logArchiveRepository.findAll(spec, pageable);
        return PageResult.of(archivePage.getContent(), archivePage.getTotalElements(), page, size);
    }

    public Optional<LogArchive> getArchiveById(Long id) {
        return logArchiveRepository.findById(id);
    }

    public Path getArchiveFilePath(Long id) {
        Optional<LogArchive> archiveOpt = logArchiveRepository.findById(id);
        if (archiveOpt.isEmpty()) {
            return null;
        }
        LogArchive archive = archiveOpt.get();
        if (!LogArchive.STATUS_COMPLETED.equals(archive.getStatus())) {
            return null;
        }
        Path filePath = Paths.get(archive.getArchivePath());
        if (Files.exists(filePath)) {
            return filePath;
        }
        return null;
    }

    @Transactional
    public LogArchive executeArchive(LocalDateTime startTime, LocalDateTime endTime, String logType, String remark) {
        if (!archiveEnabled) {
            throw new RuntimeException("日志归档功能未启用");
        }

        if (logArchiveRepository.existsByStatus(LogArchive.STATUS_ARCHIVING)) {
            throw new RuntimeException("已有归档任务正在执行中，请稍后再试");
        }

        String actualLogType = logType != null ? logType : LogArchive.LOG_TYPE_DATABASE;

        LogArchive archive = new LogArchive();
        archive.setArchiveName(generateArchiveName(startTime, endTime, actualLogType));
        archive.setStartTime(startTime);
        archive.setEndTime(endTime);
        archive.setLogType(actualLogType);
        archive.setStatus(LogArchive.STATUS_PENDING);
        archive.setRemark(remark);
        archive = logArchiveRepository.save(archive);

        try {
            archive.setStatus(LogArchive.STATUS_ARCHIVING);
            logArchiveRepository.save(archive);

            switch (actualLogType) {
                case LogArchive.LOG_TYPE_DATABASE:
                    doDatabaseArchive(archive);
                    break;
                case LogArchive.LOG_TYPE_BACKEND:
                    doBackendArchive(archive);
                    break;
                case LogArchive.LOG_TYPE_FRONTEND:
                    doFrontendArchive(archive);
                    break;
                default:
                    throw new IllegalArgumentException("不支持的日志类型: " + actualLogType);
            }

            archive.setStatus(LogArchive.STATUS_COMPLETED);
            logArchiveRepository.save(archive);
            log.info("日志归档完成: {}, 类型: {}, 归档日志数: {}",
                    archive.getArchiveName(), archive.getLogType(), archive.getLogCount());
        } catch (Exception e) {
            log.error("日志归档失败: {}, 类型: {}", archive.getArchiveName(), archive.getLogType(), e);
            archive.setStatus(LogArchive.STATUS_FAILED);
            archive.setRemark((archive.getRemark() != null ? archive.getRemark() + " - " : "") + "归档失败: " + e.getMessage());
            logArchiveRepository.save(archive);
        }

        return archive;
    }

    private void doDatabaseArchive(LogArchive archive) throws Exception {
        Path archiveDir = Paths.get(archivePath);
        if (!Files.exists(archiveDir)) {
            Files.createDirectories(archiveDir);
        }

        String csvFileName = archive.getArchiveName() + ".csv";
        Path csvFilePath = archiveDir.resolve(csvFileName);
        Path zipFilePath = archiveDir.resolve(archive.getArchiveName() + ".zip");

        long originalSizeBytes = 0;
        int totalCount = 0;

        try (BufferedWriter writer = Files.newBufferedWriter(csvFilePath, StandardCharsets.UTF_8)) {
            writer.write("\uFEFF");
            writer.write("id,event_id,event_title,source,operation_type,field_name,old_value,new_value,operator_id,operator_name,reason,operation_time,create_time");
            writer.newLine();

            int page = 0;
            boolean hasMore = true;
            while (hasMore) {
                Pageable pageable = PageRequest.of(page, batchSize, Sort.by(Sort.Direction.ASC, "operationTime"));
                Specification<HotEventLog> spec = (root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    predicates.add(cb.greaterThanOrEqualTo(root.get("operationTime"), archive.getStartTime()));
                    predicates.add(cb.lessThan(root.get("operationTime"), archive.getEndTime()));
                    return cb.and(predicates.toArray(new Predicate[0]));
                };
                Page<HotEventLog> logPage = hotEventLogRepository.findAll(spec, pageable);

                for (HotEventLog logEntry : logPage.getContent()) {
                    writer.write(formatDatabaseCsvLine(logEntry));
                    writer.newLine();
                    totalCount++;
                }

                hasMore = logPage.hasNext();
                page++;
            }
        }

        originalSizeBytes = Files.size(csvFilePath);
        compressFile(csvFilePath, zipFilePath);
        long archivedSizeBytes = Files.size(zipFilePath);

        Files.deleteIfExists(csvFilePath);

        if (deleteAfterArchive && totalCount > 0) {
            deleteArchivedDatabaseLogs(archive.getStartTime(), archive.getEndTime());
        }

        archive.setLogCount(totalCount);
        archive.setOriginalSizeBytes(originalSizeBytes);
        archive.setArchivedSizeBytes(archivedSizeBytes);
        archive.setArchivePath(zipFilePath.toAbsolutePath().toString());
    }

    private void doBackendArchive(LogArchive archive) throws Exception {
        Path archiveDir = Paths.get(archivePath);
        if (!Files.exists(archiveDir)) {
            Files.createDirectories(archiveDir);
        }

        Path logDir = Paths.get(backendLogPath);
        if (!Files.exists(logDir)) {
            throw new FileNotFoundException("后端日志目录不存在: " + backendLogPath);
        }

        Path zipFilePath = archiveDir.resolve(archive.getArchiveName() + ".zip");

        long originalSizeBytes = 0;
        int fileCount = 0;
        List<Path> filesToArchive = new ArrayList<>();

        long startTimeEpoch = archive.getStartTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long endTimeEpoch = archive.getEndTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(logDir, "*.log")) {
            for (Path file : stream) {
                BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                long fileTime = attrs.lastModifiedTime().toMillis();
                if (fileTime >= startTimeEpoch && fileTime < endTimeEpoch) {
                    filesToArchive.add(file);
                    originalSizeBytes += attrs.size();
                    fileCount++;
                }
            }
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(logDir, "*.log.gz")) {
            for (Path file : stream) {
                BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                long fileTime = attrs.lastModifiedTime().toMillis();
                if (fileTime >= startTimeEpoch && fileTime < endTimeEpoch) {
                    filesToArchive.add(file);
                    originalSizeBytes += attrs.size();
                    fileCount++;
                }
            }
        }

        if (fileCount > 0) {
            compressFiles(filesToArchive, zipFilePath);
            long archivedSizeBytes = Files.size(zipFilePath);

            if (deleteAfterArchive) {
                for (Path file : filesToArchive) {
                    Files.deleteIfExists(file);
                    log.info("已删除归档的后端日志文件: {}", file.getFileName());
                }
            }

            archive.setLogCount(fileCount);
            archive.setOriginalSizeBytes(originalSizeBytes);
            archive.setArchivedSizeBytes(archivedSizeBytes);
            archive.setArchivePath(zipFilePath.toAbsolutePath().toString());
        } else {
            archive.setLogCount(0);
            archive.setOriginalSizeBytes(0L);
            archive.setArchivedSizeBytes(0L);
            archive.setArchivePath(null);
        }
    }

    private void doFrontendArchive(LogArchive archive) throws Exception {
        Path archiveDir = Paths.get(archivePath);
        if (!Files.exists(archiveDir)) {
            Files.createDirectories(archiveDir);
        }

        String csvFileName = archive.getArchiveName() + ".csv";
        Path csvFilePath = archiveDir.resolve(csvFileName);
        Path zipFilePath = archiveDir.resolve(archive.getArchiveName() + ".zip");

        long originalSizeBytes = 0;
        int totalCount = 0;

        try (BufferedWriter writer = Files.newBufferedWriter(csvFilePath, StandardCharsets.UTF_8)) {
            writer.write("\uFEFF");
            writer.write("id,log_level,message,page_url,user_agent,browser_info,os_info,screen_resolution,stack_trace,user_id,username,error_type,line_number,column_number,additional_info,log_time,create_time");
            writer.newLine();

            int page = 0;
            boolean hasMore = true;
            while (hasMore) {
                Pageable pageable = PageRequest.of(page, batchSize, Sort.by(Sort.Direction.ASC, "logTime"));
                Specification<FrontendLog> spec = (root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    predicates.add(cb.greaterThanOrEqualTo(root.get("logTime"), archive.getStartTime()));
                    predicates.add(cb.lessThan(root.get("logTime"), archive.getEndTime()));
                    return cb.and(predicates.toArray(new Predicate[0]));
                };
                Page<FrontendLog> logPage = frontendLogRepository.findAll(spec, pageable);

                for (FrontendLog logEntry : logPage.getContent()) {
                    writer.write(formatFrontendCsvLine(logEntry));
                    writer.newLine();
                    totalCount++;
                }

                hasMore = logPage.hasNext();
                page++;
            }
        }

        originalSizeBytes = Files.size(csvFilePath);
        compressFile(csvFilePath, zipFilePath);
        long archivedSizeBytes = Files.size(zipFilePath);

        Files.deleteIfExists(csvFilePath);

        if (deleteAfterArchive && totalCount > 0) {
            frontendLogRepository.deleteByLogTimeRange(archive.getStartTime(), archive.getEndTime());
            log.info("已删除归档前端日志 {} 条", totalCount);
        }

        archive.setLogCount(totalCount);
        archive.setOriginalSizeBytes(originalSizeBytes);
        archive.setArchivedSizeBytes(archivedSizeBytes);
        archive.setArchivePath(zipFilePath.toAbsolutePath().toString());
    }

    private void deleteArchivedDatabaseLogs(LocalDateTime startTime, LocalDateTime endTime) {
        int page = 0;
        boolean hasMore = true;
        while (hasMore) {
            Pageable pageable = PageRequest.of(page, batchSize, Sort.by(Sort.Direction.ASC, "operationTime"));
            Specification<HotEventLog> spec = (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                predicates.add(cb.greaterThanOrEqualTo(root.get("operationTime"), startTime));
                predicates.add(cb.lessThan(root.get("operationTime"), endTime));
                return cb.and(predicates.toArray(new Predicate[0]));
            };
            Page<HotEventLog> logPage = hotEventLogRepository.findAll(spec, pageable);

            if (logPage.isEmpty()) {
                hasMore = false;
            } else {
                List<HotEventLog> logsToDelete = logPage.getContent();
                hotEventLogRepository.deleteAllInBatch(logsToDelete);
                log.info("已删除归档数据库日志 {} 条", logsToDelete.size());
                hasMore = logPage.hasNext();
            }
        }
    }

    private void compressFile(Path sourceFile, Path zipFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile));
             InputStream fis = Files.newInputStream(sourceFile)) {
            ZipEntry zipEntry = new ZipEntry(sourceFile.getFileName().toString());
            zos.putNextEntry(zipEntry);

            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, len);
            }
            zos.closeEntry();
        }
    }

    private void compressFiles(List<Path> sourceFiles, Path zipFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            for (Path sourceFile : sourceFiles) {
                try (InputStream fis = Files.newInputStream(sourceFile)) {
                    ZipEntry zipEntry = new ZipEntry(sourceFile.getFileName().toString());
                    zos.putNextEntry(zipEntry);

                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                    zos.closeEntry();
                }
            }
        }
    }

    private String formatDatabaseCsvLine(HotEventLog logEntry) {
        StringBuilder sb = new StringBuilder();
        sb.append(escapeCsv(String.valueOf(logEntry.getId()))).append(',');
        sb.append(escapeCsv(String.valueOf(logEntry.getEventId()))).append(',');
        sb.append(escapeCsv(logEntry.getEventTitle())).append(',');
        sb.append(escapeCsv(logEntry.getSource())).append(',');
        sb.append(escapeCsv(logEntry.getOperationType())).append(',');
        sb.append(escapeCsv(logEntry.getFieldName())).append(',');
        sb.append(escapeCsv(logEntry.getOldValue())).append(',');
        sb.append(escapeCsv(logEntry.getNewValue())).append(',');
        sb.append(escapeCsv(String.valueOf(logEntry.getOperatorId()))).append(',');
        sb.append(escapeCsv(logEntry.getOperatorName())).append(',');
        sb.append(escapeCsv(logEntry.getReason())).append(',');
        sb.append(escapeCsv(logEntry.getOperationTime() != null ? logEntry.getOperationTime().toString() : "")).append(',');
        sb.append(escapeCsv(logEntry.getCreateTime() != null ? logEntry.getCreateTime().toString() : ""));
        return sb.toString();
    }

    private String formatFrontendCsvLine(FrontendLog logEntry) {
        StringBuilder sb = new StringBuilder();
        sb.append(escapeCsv(String.valueOf(logEntry.getId()))).append(',');
        sb.append(escapeCsv(logEntry.getLogLevel())).append(',');
        sb.append(escapeCsv(logEntry.getMessage())).append(',');
        sb.append(escapeCsv(logEntry.getPageUrl())).append(',');
        sb.append(escapeCsv(logEntry.getUserAgent())).append(',');
        sb.append(escapeCsv(logEntry.getBrowserInfo())).append(',');
        sb.append(escapeCsv(logEntry.getOsInfo())).append(',');
        sb.append(escapeCsv(logEntry.getScreenResolution())).append(',');
        sb.append(escapeCsv(logEntry.getStackTrace())).append(',');
        sb.append(escapeCsv(String.valueOf(logEntry.getUserId()))).append(',');
        sb.append(escapeCsv(logEntry.getUsername())).append(',');
        sb.append(escapeCsv(logEntry.getErrorType())).append(',');
        sb.append(escapeCsv(String.valueOf(logEntry.getLineNumber()))).append(',');
        sb.append(escapeCsv(String.valueOf(logEntry.getColumnNumber()))).append(',');
        sb.append(escapeCsv(logEntry.getAdditionalInfo())).append(',');
        sb.append(escapeCsv(logEntry.getLogTime() != null ? logEntry.getLogTime().toString() : "")).append(',');
        sb.append(escapeCsv(logEntry.getCreateTime() != null ? logEntry.getCreateTime().toString() : ""));
        return sb.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String generateArchiveName(LocalDateTime startTime, LocalDateTime endTime, String logType) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String typePrefix;
        switch (logType) {
            case LogArchive.LOG_TYPE_DATABASE:
                typePrefix = "db";
                break;
            case LogArchive.LOG_TYPE_BACKEND:
                typePrefix = "backend";
                break;
            case LogArchive.LOG_TYPE_FRONTEND:
                typePrefix = "frontend";
                break;
            default:
                typePrefix = "log";
        }
        return typePrefix + "_archive_" + startTime.format(formatter) + "_to_" + endTime.format(formatter);
    }

    public void executeAutoArchive() {
        if (!archiveEnabled) {
            log.debug("日志归档功能未启用，跳过自动归档");
            return;
        }

        if (logArchiveRepository.existsByStatus(LogArchive.STATUS_ARCHIVING)) {
            log.info("已有归档任务正在执行中，跳过本次自动归档");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dbEndTime = now.minusDays(retentionDays);
        LocalDateTime backendEndTime = now.minusDays(backendRetentionDays);
        LocalDateTime frontendEndTime = now.minusDays(frontendRetentionDays);
        LocalDateTime startTime = LocalDateTime.of(2000, 1, 1, 0, 0);

        log.info("开始自动归档日志，数据库日志归档范围: {} 之前，后端日志归档范围: {} 之前，前端日志归档范围: {} 之前",
                dbEndTime, backendEndTime, frontendEndTime);

        try {
            executeArchive(startTime, dbEndTime, LogArchive.LOG_TYPE_DATABASE,
                    "系统自动归档 - 归档" + retentionDays + "天前的数据库操作日志");
        } catch (Exception e) {
            log.error("自动归档数据库日志失败", e);
        }

        try {
            executeArchive(startTime, backendEndTime, LogArchive.LOG_TYPE_BACKEND,
                    "系统自动归档 - 归档" + backendRetentionDays + "天前的后端日志文件");
        } catch (Exception e) {
            log.error("自动归档后端日志失败", e);
        }

        try {
            executeArchive(startTime, frontendEndTime, LogArchive.LOG_TYPE_FRONTEND,
                    "系统自动归档 - 归档" + frontendRetentionDays + "天前的前端日志");
        } catch (Exception e) {
            log.error("自动归档前端日志失败", e);
        }
    }

    @Transactional
    public void deleteArchive(Long id) {
        Optional<LogArchive> archiveOpt = logArchiveRepository.findById(id);
        if (archiveOpt.isEmpty()) {
            throw new RuntimeException("归档记录不存在");
        }

        LogArchive archive = archiveOpt.get();
        if (LogArchive.STATUS_ARCHIVING.equals(archive.getStatus())) {
            throw new RuntimeException("归档任务执行中，无法删除");
        }

        if (archive.getArchivePath() != null) {
            try {
                Path filePath = Paths.get(archive.getArchivePath());
                Files.deleteIfExists(filePath);
                log.info("已删除归档文件: {}", archive.getArchivePath());
            } catch (Exception e) {
                log.warn("删除归档文件失败: {}", archive.getArchivePath(), e);
            }
        }

        logArchiveRepository.deleteById(id);
    }

    public Map<String, Object> getArchiveStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalCount", logArchiveRepository.count());
        stats.put("completedCount", logArchiveRepository.countByStatus(LogArchive.STATUS_COMPLETED));
        stats.put("archivingCount", logArchiveRepository.countByStatus(LogArchive.STATUS_ARCHIVING));
        stats.put("failedCount", logArchiveRepository.countByStatus(LogArchive.STATUS_FAILED));

        List<LogArchive> completedArchives = logArchiveRepository.findByStatusOrderByCreateTimeDesc(LogArchive.STATUS_COMPLETED);
        long totalOriginalSize = completedArchives.stream()
                .mapToLong(a -> a.getOriginalSizeBytes() != null ? a.getOriginalSizeBytes() : 0)
                .sum();
        long totalArchivedSize = completedArchives.stream()
                .mapToLong(a -> a.getArchivedSizeBytes() != null ? a.getArchivedSizeBytes() : 0)
                .sum();
        long totalLogCount = completedArchives.stream()
                .mapToLong(a -> a.getLogCount() != null ? a.getLogCount() : 0)
                .sum();

        stats.put("totalOriginalSize", totalOriginalSize);
        stats.put("totalArchivedSize", totalArchivedSize);
        stats.put("totalLogCount", totalLogCount);

        double compressionRatio = totalOriginalSize > 0 ? (1 - (double) totalArchivedSize / totalOriginalSize) * 100 : 0;
        stats.put("compressionRatio", Math.round(compressionRatio * 100.0) / 100.0);

        Map<String, Object> typeStats = new LinkedHashMap<>();
        for (String type : Arrays.asList(LogArchive.LOG_TYPE_DATABASE, LogArchive.LOG_TYPE_BACKEND, LogArchive.LOG_TYPE_FRONTEND)) {
            Map<String, Object> typeStat = new LinkedHashMap<>();
            long typeTotal = completedArchives.stream().filter(a -> type.equals(a.getLogType())).count();
            long typeSize = completedArchives.stream()
                    .filter(a -> type.equals(a.getLogType()))
                    .mapToLong(a -> a.getOriginalSizeBytes() != null ? a.getOriginalSizeBytes() : 0)
                    .sum();
            long typeLogCount = completedArchives.stream()
                    .filter(a -> type.equals(a.getLogType()))
                    .mapToLong(a -> a.getLogCount() != null ? a.getLogCount() : 0)
                    .sum();
            typeStat.put("count", typeTotal);
            typeStat.put("originalSize", typeSize);
            typeStat.put("logCount", typeLogCount);
            typeStats.put(type, typeStat);
        }
        stats.put("typeStats", typeStats);

        return stats;
    }
}
