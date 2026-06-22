package com.hotevent.service;

import com.hotevent.common.PageResult;
import com.hotevent.entity.FrontendLog;
import com.hotevent.entity.HotEventLog;
import com.hotevent.entity.LogArchive;
import com.hotevent.repository.FrontendLogRepository;
import com.hotevent.repository.HotEventLogRepository;
import com.hotevent.repository.LogArchiveRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("日志归档服务测试")
class LogArchiveServiceTest {

    @Mock
    private LogArchiveRepository logArchiveRepository;

    @Mock
    private HotEventLogRepository hotEventLogRepository;

    @Mock
    private FrontendLogRepository frontendLogRepository;

    @InjectMocks
    private LogArchiveService logArchiveService;

    @TempDir
    Path tempDir;

    private LogArchive testArchive;
    private List<HotEventLog> testHotEventLogs;
    private List<FrontendLog> testFrontendLogs;

    @BeforeEach
    void setUp() {
        testArchive = new LogArchive();
        testArchive.setId(1L);
        testArchive.setArchiveName("db_archive_20240101_000000_to_20240131_235959");
        testArchive.setStartTime(LocalDateTime.of(2024, 1, 1, 0, 0, 0));
        testArchive.setEndTime(LocalDateTime.of(2024, 1, 31, 23, 59, 59));
        testArchive.setLogCount(1000);
        testArchive.setOriginalSizeBytes(500000L);
        testArchive.setArchivedSizeBytes(50000L);
        testArchive.setArchivePath(null);
        testArchive.setStatus(LogArchive.STATUS_COMPLETED);
        testArchive.setLogType(LogArchive.LOG_TYPE_DATABASE);
        testArchive.setRemark("测试归档");
        testArchive.setCreateTime(LocalDateTime.now());

        testHotEventLogs = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            HotEventLog log = new HotEventLog();
            log.setId((long) i);
            log.setEventId((long) (i % 5));
            log.setEventTitle("测试事件" + (i % 5));
            log.setSource("weibo");
            log.setOperationType(i % 2 == 0 ? "UPDATE" : "INSERT");
            log.setFieldName(i % 2 == 0 ? "title" : null);
            log.setOldValue(i % 2 == 0 ? "旧值" + i : null);
            log.setNewValue(i % 2 == 0 ? "新值" + i : null);
            log.setOperatorId(1L);
            log.setOperatorName("管理员");
            log.setReason("测试操作" + i);
            log.setOperationTime(LocalDateTime.of(2024, 1, 15, 12, 0, 0).plusHours(i));
            log.setCreateTime(LocalDateTime.of(2024, 1, 15, 12, 0, 0).plusHours(i));
            testHotEventLogs.add(log);
        }

        testFrontendLogs = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            FrontendLog log = new FrontendLog();
            log.setId((long) i);
            log.setLogLevel("INFO");
            log.setMessage("前端日志消息" + i);
            log.setPageUrl("/page" + i);
            log.setUserAgent("Mozilla/5.0");
            log.setBrowserInfo("Chrome");
            log.setOsInfo("Windows");
            log.setScreenResolution("1920x1080");
            log.setStackTrace(null);
            log.setUserId((long) (i % 3));
            log.setUsername("user" + (i % 3));
            log.setErrorType(null);
            log.setLineNumber(null);
            log.setColumnNumber(null);
            log.setAdditionalInfo("额外信息" + i);
            log.setLogTime(LocalDateTime.of(2024, 1, 15, 12, 0, 0).plusHours(i));
            log.setCreateTime(LocalDateTime.of(2024, 1, 15, 12, 0, 0).plusHours(i));
            testFrontendLogs.add(log);
        }

        ReflectionTestUtils.setField(logArchiveService, "archiveEnabled", true);
        ReflectionTestUtils.setField(logArchiveService, "archivePath", tempDir.toString());
        ReflectionTestUtils.setField(logArchiveService, "backendLogPath", tempDir.toString() + "/logs");
        ReflectionTestUtils.setField(logArchiveService, "retentionDays", 30);
        ReflectionTestUtils.setField(logArchiveService, "backendRetentionDays", 30);
        ReflectionTestUtils.setField(logArchiveService, "frontendRetentionDays", 30);
        ReflectionTestUtils.setField(logArchiveService, "batchSize", 100);
        ReflectionTestUtils.setField(logArchiveService, "deleteAfterArchive", false);
    }

    @Nested
    @DisplayName("归档列表查询")
    class GetArchivesTests {

        @Test
        @DisplayName("正常场景 - 查询归档列表成功")
        void testGetArchives_Success() {
            List<LogArchive> archives = Arrays.asList(testArchive, testArchive);
            Page<LogArchive> page = new PageImpl<>(archives, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createTime")), 2);
            when(logArchiveRepository.findAll(any(Specification.class), any(PageRequest.class)))
                    .thenReturn(page);

            PageResult<LogArchive> result = logArchiveService.getArchives(null, null, 1, 10);

            assertNotNull(result);
            assertEquals(2, result.getRecords().size());
            assertEquals(2, result.getTotal());
            assertEquals(1, result.getCurrent());
            assertEquals(10, result.getSize());
        }

        @Test
        @DisplayName("按状态筛选 - 只显示已完成的归档")
        void testGetArchives_ByStatus() {
            Page<LogArchive> page = new PageImpl<>(Collections.singletonList(testArchive), PageRequest.of(0, 10), 1);
            when(logArchiveRepository.findAll(any(Specification.class), any(PageRequest.class)))
                    .thenReturn(page);

            PageResult<LogArchive> result = logArchiveService.getArchives(LogArchive.STATUS_COMPLETED, null, 1, 10);

            assertNotNull(result);
            assertEquals(1, result.getRecords().size());
        }

        @Test
        @DisplayName("按类型筛选 - 只显示数据库日志归档")
        void testGetArchives_ByLogType() {
            Page<LogArchive> page = new PageImpl<>(Collections.singletonList(testArchive), PageRequest.of(0, 10), 1);
            when(logArchiveRepository.findAll(any(Specification.class), any(PageRequest.class)))
                    .thenReturn(page);

            PageResult<LogArchive> result = logArchiveService.getArchives(null, LogArchive.LOG_TYPE_DATABASE, 1, 10);

            assertNotNull(result);
            assertEquals(1, result.getRecords().size());
        }

        @Test
        @DisplayName("边界条件 - 空归档列表")
        void testGetArchives_EmptyResult() {
            Page<LogArchive> page = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);
            when(logArchiveRepository.findAll(any(Specification.class), any(PageRequest.class)))
                    .thenReturn(page);

            PageResult<LogArchive> result = logArchiveService.getArchives(null, null, 1, 10);

            assertNotNull(result);
            assertTrue(result.getRecords().isEmpty());
            assertEquals(0, result.getTotal());
        }
    }

    @Nested
    @DisplayName("归档详情查询")
    class GetArchiveByIdTests {

        @Test
        @DisplayName("正常场景 - 根据ID查询归档成功")
        void testGetArchiveById_Success() {
            when(logArchiveRepository.findById(eq(1L))).thenReturn(Optional.of(testArchive));

            Optional<LogArchive> result = logArchiveService.getArchiveById(1L);

            assertTrue(result.isPresent());
            assertEquals(1L, result.get().getId());
        }

        @Test
        @DisplayName("边界条件 - 归档不存在")
        void testGetArchiveById_NotFound() {
            when(logArchiveRepository.findById(eq(999L))).thenReturn(Optional.empty());

            Optional<LogArchive> result = logArchiveService.getArchiveById(999L);

            assertFalse(result.isPresent());
        }
    }

    @Nested
    @DisplayName("归档文件路径获取")
    class GetArchiveFilePathTests {

        @Test
        @DisplayName("正常场景 - 获取已完成归档的文件路径")
        void testGetArchiveFilePath_Completed() throws Exception {
            Path testFile = tempDir.resolve("test_archive.zip");
            Files.createFile(testFile);
            testArchive.setArchivePath(testFile.toString());
            testArchive.setStatus(LogArchive.STATUS_COMPLETED);

            when(logArchiveRepository.findById(eq(1L))).thenReturn(Optional.of(testArchive));

            Path result = logArchiveService.getArchiveFilePath(1L);

            assertNotNull(result);
            assertTrue(Files.exists(result));
        }

        @Test
        @DisplayName("边界条件 - 归档不存在")
        void testGetArchiveFilePath_ArchiveNotFound() {
            when(logArchiveRepository.findById(eq(999L))).thenReturn(Optional.empty());

            Path result = logArchiveService.getArchiveFilePath(999L);

            assertNull(result);
        }

        @Test
        @DisplayName("边界条件 - 归档未完成")
        void testGetArchiveFilePath_NotCompleted() {
            testArchive.setStatus(LogArchive.STATUS_ARCHIVING);
            when(logArchiveRepository.findById(eq(1L))).thenReturn(Optional.of(testArchive));

            Path result = logArchiveService.getArchiveFilePath(1L);

            assertNull(result);
        }

        @Test
        @DisplayName("边界条件 - 归档文件不存在")
        void testGetArchiveFilePath_FileNotFound() {
            testArchive.setArchivePath("/nonexistent/path.zip");
            testArchive.setStatus(LogArchive.STATUS_COMPLETED);
            when(logArchiveRepository.findById(eq(1L))).thenReturn(Optional.of(testArchive));

            Path result = logArchiveService.getArchiveFilePath(1L);

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("数据库日志归档")
    class DatabaseArchiveTests {

        @Test
        @DisplayName("正常场景 - 执行数据库日志归档成功")
        void testExecuteArchive_DatabaseSuccess() throws Exception {
            LocalDateTime startTime = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime endTime = LocalDateTime.of(2024, 1, 31, 23, 59, 59);

            when(logArchiveRepository.existsByStatus(eq(LogArchive.STATUS_ARCHIVING))).thenReturn(false);
            when(logArchiveRepository.save(any(LogArchive.class))).thenAnswer(invocation -> {
                LogArchive archive = invocation.getArgument(0);
                archive.setId(1L);
                archive.setCreateTime(LocalDateTime.now());
                return archive;
            });

            Page<HotEventLog> logPage = new PageImpl<>(testHotEventLogs, PageRequest.of(0, 100), 10);
            Page<HotEventLog> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(1, 100), 10);
            when(hotEventLogRepository.findAll(any(Specification.class), any(PageRequest.class)))
                    .thenReturn(logPage)
                    .thenReturn(emptyPage);

            LogArchive result = logArchiveService.executeArchive(startTime, endTime, LogArchive.LOG_TYPE_DATABASE, "测试归档");

            assertNotNull(result);
            assertEquals(LogArchive.STATUS_COMPLETED, result.getStatus());
            assertEquals(10, result.getLogCount());
            assertNotNull(result.getArchivePath());
            assertTrue(result.getArchivedSizeBytes() > 0);
            assertTrue(result.getOriginalSizeBytes() > 0);
        }

        @Test
        @DisplayName("边界条件 - 无日志需要归档")
        void testExecuteArchive_NoLogs() {
            LocalDateTime startTime = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime endTime = LocalDateTime.of(2024, 1, 31, 23, 59, 59);

            when(logArchiveRepository.existsByStatus(eq(LogArchive.STATUS_ARCHIVING))).thenReturn(false);
            when(logArchiveRepository.save(any(LogArchive.class))).thenAnswer(invocation -> {
                LogArchive archive = invocation.getArgument(0);
                archive.setId(1L);
                return archive;
            });

            Page<HotEventLog> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 100), 0);
            when(hotEventLogRepository.findAll(any(Specification.class), any(PageRequest.class)))
                    .thenReturn(emptyPage);

            LogArchive result = logArchiveService.executeArchive(startTime, endTime, LogArchive.LOG_TYPE_DATABASE, "测试归档");

            assertNotNull(result);
            assertEquals(LogArchive.STATUS_COMPLETED, result.getStatus());
            assertEquals(0, result.getLogCount());
        }

        @Test
        @DisplayName("异常情况 - 归档功能未启用")
        void testExecuteArchive_ArchiveDisabled() {
            ReflectionTestUtils.setField(logArchiveService, "archiveEnabled", false);
            LocalDateTime startTime = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime endTime = LocalDateTime.of(2024, 1, 31, 23, 59, 59);

            assertThrows(RuntimeException.class, () -> {
                logArchiveService.executeArchive(startTime, endTime, LogArchive.LOG_TYPE_DATABASE, "测试");
            });
        }

        @Test
        @DisplayName("异常情况 - 已有归档任务执行中")
        void testExecuteArchive_AnotherArchiving() {
            LocalDateTime startTime = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime endTime = LocalDateTime.of(2024, 1, 31, 23, 59, 59);

            when(logArchiveRepository.existsByStatus(eq(LogArchive.STATUS_ARCHIVING))).thenReturn(true);

            assertThrows(RuntimeException.class, () -> {
                logArchiveService.executeArchive(startTime, endTime, LogArchive.LOG_TYPE_DATABASE, "测试");
            });
        }

        @Test
        @DisplayName("异常情况 - 不支持的日志类型")
        void testExecuteArchive_UnsupportedLogType() {
            LocalDateTime startTime = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime endTime = LocalDateTime.of(2024, 1, 31, 23, 59, 59);

            when(logArchiveRepository.existsByStatus(eq(LogArchive.STATUS_ARCHIVING))).thenReturn(false);
            when(logArchiveRepository.save(any(LogArchive.class))).thenAnswer(invocation -> {
                LogArchive archive = invocation.getArgument(0);
                archive.setId(1L);
                return archive;
            });

            LogArchive result = logArchiveService.executeArchive(startTime, endTime, "INVALID_TYPE", "测试");

            assertEquals(LogArchive.STATUS_FAILED, result.getStatus());
            assertNotNull(result.getRemark());
            assertTrue(result.getRemark().contains("失败"));
        }

        @Test
        @DisplayName("正常场景 - 归档后删除原数据")
        void testExecuteArchive_DeleteAfterArchive() throws Exception {
            ReflectionTestUtils.setField(logArchiveService, "deleteAfterArchive", true);
            LocalDateTime startTime = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime endTime = LocalDateTime.of(2024, 1, 31, 23, 59, 59);

            when(logArchiveRepository.existsByStatus(eq(LogArchive.STATUS_ARCHIVING))).thenReturn(false);
            when(logArchiveRepository.save(any(LogArchive.class))).thenAnswer(invocation -> {
                LogArchive archive = invocation.getArgument(0);
                archive.setId(1L);
                return archive;
            });

            Page<HotEventLog> logPage = new PageImpl<>(testHotEventLogs.subList(0, 5), PageRequest.of(0, 5), 10);
            Page<HotEventLog> logPage2 = new PageImpl<>(testHotEventLogs.subList(5, 10), PageRequest.of(1, 5), 10);
            Page<HotEventLog> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(2, 5), 10);
            when(hotEventLogRepository.findAll(any(Specification.class), any(PageRequest.class)))
                    .thenReturn(logPage)
                    .thenReturn(logPage2)
                    .thenReturn(emptyPage);

            logArchiveService.executeArchive(startTime, endTime, LogArchive.LOG_TYPE_DATABASE, "测试归档");

            verify(hotEventLogRepository, atLeastOnce()).deleteAllInBatch(anyList());
        }
    }

    @Nested
    @DisplayName("后端日志归档")
    class BackendArchiveTests {

        @Test
        @DisplayName("正常场景 - 后端日志文件归档成功")
        void testExecuteArchive_BackendSuccess() throws Exception {
            Path logDir = tempDir.resolve("logs");
            Files.createDirectories(logDir);

            Path logFile1 = logDir.resolve("app.2024-01-15.0.log");
            Files.write(logFile1, "日志内容1".getBytes());

            Path logFile2 = logDir.resolve("app.2024-01-16.0.log.gz");
            Files.write(logFile2, "压缩日志内容".getBytes());

            ReflectionTestUtils.setField(logArchiveService, "backendLogPath", logDir.toString());

            LocalDateTime startTime = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime endTime = LocalDateTime.of(2024, 2, 1, 0, 0);

            when(logArchiveRepository.existsByStatus(eq(LogArchive.STATUS_ARCHIVING))).thenReturn(false);
            when(logArchiveRepository.save(any(LogArchive.class))).thenAnswer(invocation -> {
                LogArchive archive = invocation.getArgument(0);
                archive.setId(1L);
                return archive;
            });

            LogArchive result = logArchiveService.executeArchive(startTime, endTime, LogArchive.LOG_TYPE_BACKEND, "测试后端归档");

            assertNotNull(result);
            assertEquals(LogArchive.STATUS_COMPLETED, result.getStatus());
            assertTrue(result.getLogCount() >= 1);
        }

        @Test
        @DisplayName("边界条件 - 无后端日志文件")
        void testExecuteArchive_NoBackendLogs() throws Exception {
            Path logDir = tempDir.resolve("empty_logs");
            Files.createDirectories(logDir);
            ReflectionTestUtils.setField(logArchiveService, "backendLogPath", logDir.toString());

            LocalDateTime startTime = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime endTime = LocalDateTime.of(2024, 2, 1, 0, 0);

            when(logArchiveRepository.existsByStatus(eq(LogArchive.STATUS_ARCHIVING))).thenReturn(false);
            when(logArchiveRepository.save(any(LogArchive.class))).thenAnswer(invocation -> {
                LogArchive archive = invocation.getArgument(0);
                archive.setId(1L);
                return archive;
            });

            LogArchive result = logArchiveService.executeArchive(startTime, endTime, LogArchive.LOG_TYPE_BACKEND, "测试");

            assertNotNull(result);
            assertEquals(LogArchive.STATUS_COMPLETED, result.getStatus());
            assertEquals(0, result.getLogCount());
        }

        @Test
        @DisplayName("异常情况 - 后端日志目录不存在")
        void testExecuteArchive_LogDirNotFound() {
            ReflectionTestUtils.setField(logArchiveService, "backendLogPath", "/nonexistent/path");

            LocalDateTime startTime = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime endTime = LocalDateTime.of(2024, 2, 1, 0, 0);

            when(logArchiveRepository.existsByStatus(eq(LogArchive.STATUS_ARCHIVING))).thenReturn(false);
            when(logArchiveRepository.save(any(LogArchive.class))).thenAnswer(invocation -> {
                LogArchive archive = invocation.getArgument(0);
                archive.setId(1L);
                return archive;
            });

            LogArchive result = logArchiveService.executeArchive(startTime, endTime, LogArchive.LOG_TYPE_BACKEND, "测试");

            assertEquals(LogArchive.STATUS_FAILED, result.getStatus());
        }
    }

    @Nested
    @DisplayName("前端日志归档")
    class FrontendArchiveTests {

        @Test
        @DisplayName("正常场景 - 前端日志归档成功")
        void testExecuteArchive_FrontendSuccess() {
            LocalDateTime startTime = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime endTime = LocalDateTime.of(2024, 1, 31, 23, 59, 59);

            when(logArchiveRepository.existsByStatus(eq(LogArchive.STATUS_ARCHIVING))).thenReturn(false);
            when(logArchiveRepository.save(any(LogArchive.class))).thenAnswer(invocation -> {
                LogArchive archive = invocation.getArgument(0);
                archive.setId(1L);
                return archive;
            });

            Page<FrontendLog> logPage = new PageImpl<>(testFrontendLogs, PageRequest.of(0, 100), 10);
            Page<FrontendLog> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(1, 100), 10);
            when(frontendLogRepository.findAll(any(Specification.class), any(PageRequest.class)))
                    .thenReturn(logPage)
                    .thenReturn(emptyPage);

            LogArchive result = logArchiveService.executeArchive(startTime, endTime, LogArchive.LOG_TYPE_FRONTEND, "测试前端归档");

            assertNotNull(result);
            assertEquals(LogArchive.STATUS_COMPLETED, result.getStatus());
            assertEquals(10, result.getLogCount());
            assertTrue(result.getArchivedSizeBytes() > 0);
        }

        @Test
        @DisplayName("边界条件 - 无前端日志数据")
        void testExecuteArchive_NoFrontendLogs() {
            LocalDateTime startTime = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime endTime = LocalDateTime.of(2024, 1, 31, 23, 59, 59);

            when(logArchiveRepository.existsByStatus(eq(LogArchive.STATUS_ARCHIVING))).thenReturn(false);
            when(logArchiveRepository.save(any(LogArchive.class))).thenAnswer(invocation -> {
                LogArchive archive = invocation.getArgument(0);
                archive.setId(1L);
                return archive;
            });

            Page<FrontendLog> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 100), 0);
            when(frontendLogRepository.findAll(any(Specification.class), any(PageRequest.class)))
                    .thenReturn(emptyPage);

            LogArchive result = logArchiveService.executeArchive(startTime, endTime, LogArchive.LOG_TYPE_FRONTEND, "测试");

            assertNotNull(result);
            assertEquals(LogArchive.STATUS_COMPLETED, result.getStatus());
            assertEquals(0, result.getLogCount());
        }

        @Test
        @DisplayName("正常场景 - 归档后删除前端日志")
        void testExecuteArchive_DeleteFrontendAfterArchive() {
            ReflectionTestUtils.setField(logArchiveService, "deleteAfterArchive", true);
            LocalDateTime startTime = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime endTime = LocalDateTime.of(2024, 1, 31, 23, 59, 59);

            when(logArchiveRepository.existsByStatus(eq(LogArchive.STATUS_ARCHIVING))).thenReturn(false);
            when(logArchiveRepository.save(any(LogArchive.class))).thenAnswer(invocation -> {
                LogArchive archive = invocation.getArgument(0);
                archive.setId(1L);
                return archive;
            });

            Page<FrontendLog> logPage = new PageImpl<>(testFrontendLogs, PageRequest.of(0, 100), 10);
            Page<FrontendLog> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(1, 100), 10);
            when(frontendLogRepository.findAll(any(Specification.class), any(PageRequest.class)))
                    .thenReturn(logPage)
                    .thenReturn(emptyPage);

            logArchiveService.executeArchive(startTime, endTime, LogArchive.LOG_TYPE_FRONTEND, "测试归档");

            verify(frontendLogRepository, times(1)).deleteByLogTimeRange(any(), any());
        }
    }

    @Nested
    @DisplayName("归档删除")
    class DeleteArchiveTests {

        @Test
        @DisplayName("正常场景 - 删除归档记录和文件")
        void testDeleteArchive_Success() throws Exception {
            Path testFile = tempDir.resolve("test_archive.zip");
            Files.createFile(testFile);
            testArchive.setArchivePath(testFile.toString());
            testArchive.setStatus(LogArchive.STATUS_COMPLETED);

            when(logArchiveRepository.findById(eq(1L))).thenReturn(Optional.of(testArchive));
            doNothing().when(logArchiveRepository).deleteById(eq(1L));

            assertDoesNotThrow(() -> {
                logArchiveService.deleteArchive(1L);
            });

            verify(logArchiveRepository, times(1)).deleteById(eq(1L));
        }

        @Test
        @DisplayName("边界条件 - 归档不存在")
        void testDeleteArchive_NotFound() {
            when(logArchiveRepository.findById(eq(999L))).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class, () -> {
                logArchiveService.deleteArchive(999L);
            });
        }

        @Test
        @DisplayName("边界条件 - 归档进行中不能删除")
        void testDeleteArchive_Archiving() {
            testArchive.setStatus(LogArchive.STATUS_ARCHIVING);
            when(logArchiveRepository.findById(eq(1L))).thenReturn(Optional.of(testArchive));

            assertThrows(RuntimeException.class, () -> {
                logArchiveService.deleteArchive(1L);
            });
        }

        @Test
        @DisplayName("边界条件 - 归档文件不存在时仍删除记录")
        void testDeleteArchive_FileNotFound() {
            testArchive.setArchivePath("/nonexistent/file.zip");
            testArchive.setStatus(LogArchive.STATUS_COMPLETED);
            when(logArchiveRepository.findById(eq(1L))).thenReturn(Optional.of(testArchive));
            doNothing().when(logArchiveRepository).deleteById(eq(1L));

            assertDoesNotThrow(() -> {
                logArchiveService.deleteArchive(1L);
            });

            verify(logArchiveRepository, times(1)).deleteById(eq(1L));
        }
    }

    @Nested
    @DisplayName("归档统计")
    class ArchiveStatisticsTests {

        @Test
        @DisplayName("正常场景 - 获取归档统计数据")
        void testGetArchiveStatistics_Success() {
            List<LogArchive> completedArchives = new ArrayList<>();
            LogArchive archive1 = new LogArchive();
            archive1.setId(1L);
            archive1.setLogType(LogArchive.LOG_TYPE_DATABASE);
            archive1.setLogCount(1000);
            archive1.setOriginalSizeBytes(500000L);
            archive1.setArchivedSizeBytes(50000L);
            archive1.setStatus(LogArchive.STATUS_COMPLETED);
            completedArchives.add(archive1);

            LogArchive archive2 = new LogArchive();
            archive2.setId(2L);
            archive2.setLogType(LogArchive.LOG_TYPE_FRONTEND);
            archive2.setLogCount(500);
            archive2.setOriginalSizeBytes(200000L);
            archive2.setArchivedSizeBytes(20000L);
            archive2.setStatus(LogArchive.STATUS_COMPLETED);
            completedArchives.add(archive2);

            when(logArchiveRepository.count()).thenReturn(5L);
            when(logArchiveRepository.countByStatus(eq(LogArchive.STATUS_COMPLETED))).thenReturn(2L);
            when(logArchiveRepository.countByStatus(eq(LogArchive.STATUS_ARCHIVING))).thenReturn(1L);
            when(logArchiveRepository.countByStatus(eq(LogArchive.STATUS_FAILED))).thenReturn(2L);
            when(logArchiveRepository.findByStatusOrderByCreateTimeDesc(eq(LogArchive.STATUS_COMPLETED)))
                    .thenReturn(completedArchives);

            Map<String, Object> stats = logArchiveService.getArchiveStatistics();

            assertNotNull(stats);
            assertEquals(5L, stats.get("totalCount"));
            assertEquals(2L, stats.get("completedCount"));
            assertEquals(1L, stats.get("archivingCount"));
            assertEquals(2L, stats.get("failedCount"));
            assertEquals(700000L, stats.get("totalOriginalSize"));
            assertEquals(70000L, stats.get("totalArchivedSize"));
            assertEquals(1500L, stats.get("totalLogCount"));
            assertTrue(stats.containsKey("compressionRatio"));
            assertTrue(stats.containsKey("typeStats"));
        }

        @Test
        @DisplayName("边界条件 - 无归档记录时的统计")
        void testGetArchiveStatistics_Empty() {
            when(logArchiveRepository.count()).thenReturn(0L);
            when(logArchiveRepository.countByStatus(eq(LogArchive.STATUS_COMPLETED))).thenReturn(0L);
            when(logArchiveRepository.countByStatus(eq(LogArchive.STATUS_ARCHIVING))).thenReturn(0L);
            when(logArchiveRepository.countByStatus(eq(LogArchive.STATUS_FAILED))).thenReturn(0L);
            when(logArchiveRepository.findByStatusOrderByCreateTimeDesc(eq(LogArchive.STATUS_COMPLETED)))
                    .thenReturn(Collections.emptyList());

            Map<String, Object> stats = logArchiveService.getArchiveStatistics();

            assertNotNull(stats);
            assertEquals(0L, stats.get("totalCount"));
            assertEquals(0L, stats.get("completedCount"));
            assertEquals(0L, stats.get("totalOriginalSize"));
            assertEquals(0L, stats.get("totalArchivedSize"));
            assertEquals(0L, stats.get("totalLogCount"));
            assertEquals(0.0, stats.get("compressionRatio"));
        }
    }

    @Nested
    @DisplayName("自动归档")
    class AutoArchiveTests {

        @Test
        @DisplayName("正常场景 - 执行自动归档")
        void testExecuteAutoArchive_Success() {
            when(logArchiveRepository.existsByStatus(eq(LogArchive.STATUS_ARCHIVING))).thenReturn(false);
            when(logArchiveRepository.save(any(LogArchive.class))).thenAnswer(invocation -> {
                LogArchive archive = invocation.getArgument(0);
                archive.setId(System.currentTimeMillis());
                return archive;
            });

            Page<HotEventLog> emptyDbPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 100), 0);
            Page<FrontendLog> emptyFePage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 100), 0);
            when(hotEventLogRepository.findAll(any(Specification.class), any(PageRequest.class)))
                    .thenReturn(emptyDbPage);
            when(frontendLogRepository.findAll(any(Specification.class), any(PageRequest.class)))
                    .thenReturn(emptyFePage);

            assertDoesNotThrow(() -> {
                logArchiveService.executeAutoArchive();
            });
        }

        @Test
        @DisplayName("边界条件 - 归档功能禁用时跳过")
        void testExecuteAutoArchive_Disabled() {
            ReflectionTestUtils.setField(logArchiveService, "archiveEnabled", false);

            assertDoesNotThrow(() -> {
                logArchiveService.executeAutoArchive();
            });

            verify(logArchiveRepository, never()).existsByStatus(anyString());
        }

        @Test
        @DisplayName("边界条件 - 已有归档执行中时跳过")
        void testExecuteAutoArchive_AnotherArchiving() {
            when(logArchiveRepository.existsByStatus(eq(LogArchive.STATUS_ARCHIVING))).thenReturn(true);

            assertDoesNotThrow(() -> {
                logArchiveService.executeAutoArchive();
            });
        }
    }

    @Nested
    @DisplayName("CSV格式处理")
    class CsvFormatTests {

        @Test
        @DisplayName("边界条件 - 普通值无需转义")
        void testEscapeCsv_NormalValue() {
            String result = invokeEscapeCsv("普通文本");
            assertEquals("普通文本", result);
        }

        @Test
        @DisplayName("边界条件 - 包含逗号需要转义")
        void testEscapeCsv_CommaValue() {
            String result = invokeEscapeCsv("包含,逗号的文本");
            assertEquals("\"包含,逗号的文本\"", result);
        }

        @Test
        @DisplayName("边界条件 - 包含双引号需要转义")
        void testEscapeCsv_QuoteValue() {
            String result = invokeEscapeCsv("包含\"引号的文本");
            assertEquals("\"包含\"\"引号的文本\"", result);
        }

        @Test
        @DisplayName("边界条件 - 包含换行需要转义")
        void testEscapeCsv_NewlineValue() {
            String result = invokeEscapeCsv("包含\n换行的文本");
            assertEquals("\"包含\n换行的文本\"", result);
        }

        @Test
        @DisplayName("边界条件 - null值返回空字符串")
        void testEscapeCsv_NullValue() {
            String result = invokeEscapeCsv(null);
            assertEquals("", result);
        }

        private String invokeEscapeCsv(String value) {
            try {
                java.lang.reflect.Method method = LogArchiveService.class.getDeclaredMethod("escapeCsv", String.class);
                method.setAccessible(true);
                return (String) method.invoke(logArchiveService, value);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
