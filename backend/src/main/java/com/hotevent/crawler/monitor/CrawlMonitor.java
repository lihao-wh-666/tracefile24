package com.hotevent.crawler.monitor;

import com.hotevent.crawler.core.CrawlTask;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class CrawlMonitor {

    @Value("${hot-event.crawler.monitor.enabled:true}")
    private boolean monitorEnabled;

    @Value("${hot-event.crawler.monitor.alert-threshold.failures:5}")
    private int maxConsecutiveFailures;

    @Value("${hot-event.crawler.monitor.alert-threshold.empty-data:3}")
    private int maxEmptyDataRuns;

    @Value("${hot-event.crawler.monitor.alert-threshold.high-response-time:30000}")
    private long highResponseTimeThreshold;

    private ScheduledExecutorService scheduler;

    private final Map<String, PlatformStats> platformStats = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<AlertRecord> alertHistory = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<TaskRecord> taskHistory = new ConcurrentLinkedDeque<>();

    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalSuccess = new AtomicLong(0);
    private final AtomicLong totalFailures = new AtomicLong(0);
    private final AtomicLong totalItemsCollected = new AtomicLong(0);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${hot-event.crawler.monitor.alert-email:}")
    private String alertEmail;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlatformStats {
        private String platform;
        private long taskCount;
        private long successCount;
        private long failureCount;
        private long itemsCount;
        private long totalResponseTime;
        private long avgResponseTime;
        private long maxResponseTime;
        private long lastTaskTime;
        private long consecutiveFailures;
        private long emptyDataRuns;
        private LocalDateTime lastSuccessTime;
        private LocalDateTime lastFailureTime;
        private String lastError;
        private List<Double> successRateHistory;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlertRecord {
        private String alertId;
        private AlertType type;
        private String platform;
        private String message;
        private LocalDateTime time;
        private AlertLevel level;
        private boolean acknowledged;
        private String details;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskRecord {
        private String taskId;
        private String platform;
        private String taskName;
        private CrawlTask.TaskStatus status;
        private int successRequests;
        private int failRequests;
        private int itemsCount;
        private long durationMs;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
    }

    public enum AlertType {
        CONSECUTIVE_FAILURE, EMPTY_DATA_RUN, HIGH_RESPONSE_TIME,
        TASK_TIMEOUT, LOGIN_EXPIRED, RATE_LIMIT_HIT,
        BLOCKED_DETECTED, SYSTEM_ERROR, PROXY_FAILURE, ROBOTS_VIOLATION
    }

    public enum AlertLevel {
        INFO, WARNING, ERROR, CRITICAL
    }

    public void start() {
        if (!monitorEnabled) {
            log.info("CrawlMonitor已禁用");
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "crawl-monitor");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(this::generateReport, 1, 5, TimeUnit.MINUTES);
        scheduler.scheduleAtFixedRate(this::checkActiveAlerts, 10, 30, TimeUnit.SECONDS);

        log.info("CrawlMonitor启动完成");
    }

    public void onTaskStarted(CrawlTask task) {
        if (!monitorEnabled) return;
        PlatformStats stats = getOrCreateStats(task.getPlatform());
        stats.setTaskCount(stats.getTaskCount() + 1);
        stats.setLastTaskTime(System.currentTimeMillis());
    }

    public void onTaskCompleted(CrawlTask task, int success, int failures, int items, long durationMs) {
        if (!monitorEnabled) return;

        PlatformStats stats = getOrCreateStats(task.getPlatform());

        boolean successOverall = failures == 0 || success > 0;
        if (successOverall) {
            stats.setSuccessCount(stats.getSuccessCount() + 1);
            stats.setConsecutiveFailures(0);
            stats.setLastSuccessTime(LocalDateTime.now());
            if (items == 0) {
                stats.setEmptyDataRuns(stats.getEmptyDataRuns() + 1);
                if (stats.getEmptyDataRuns() >= maxEmptyDataRuns) {
                    triggerAlert(AlertType.EMPTY_DATA_RUN, AlertLevel.WARNING, task.getPlatform(),
                            String.format("平台[%s]连续%d次空数据", task.getPlatform(), stats.getEmptyDataRuns()),
                            "任务ID: " + task.getTaskId());
                }
            } else {
                stats.setEmptyDataRuns(0);
            }
        } else {
            stats.setFailureCount(stats.getFailureCount() + 1);
            stats.setConsecutiveFailures(stats.getConsecutiveFailures() + 1);
            stats.setLastFailureTime(LocalDateTime.now());
            stats.setLastError(task.getLastError());
            if (stats.getConsecutiveFailures() >= maxConsecutiveFailures) {
                triggerAlert(AlertType.CONSECUTIVE_FAILURE, AlertLevel.ERROR, task.getPlatform(),
                        String.format("平台[%s]连续失败%d次，最后错误: %s", task.getPlatform(),
                                stats.getConsecutiveFailures(),
                                task.getLastError() != null && task.getLastError().length() > 100
                                        ? task.getLastError().substring(0, 100) + "..."
                                        : task.getLastError()),
                        "任务ID: " + task.getTaskId());
            }
        }

        stats.setItemsCount(stats.getItemsCount() + items);
        stats.setTotalResponseTime(stats.getTotalResponseTime() + durationMs);
        stats.setAvgResponseTime(stats.getTaskCount() > 0 ? stats.getTotalResponseTime() / stats.getTaskCount() : 0);
        if (durationMs > stats.getMaxResponseTime()) {
            stats.setMaxResponseTime(durationMs);
        }
        if (durationMs > highResponseTimeThreshold) {
            triggerAlert(AlertType.HIGH_RESPONSE_TIME, AlertLevel.WARNING, task.getPlatform(),
                    String.format("平台[%s]任务响应时间过长: %dms", task.getPlatform(), durationMs),
                    "任务ID: " + task.getTaskId() + " 阈值: " + highResponseTimeThreshold + "ms");
        }

        totalRequests.addAndGet(success + failures);
        totalSuccess.addAndGet(success);
        totalFailures.addAndGet(failures);
        totalItemsCollected.addAndGet(items);

        taskHistory.offer(TaskRecord.builder()
                .taskId(task.getTaskId())
                .platform(task.getPlatform())
                .taskName(task.getTaskName())
                .status(task.getStatus())
                .successRequests(success)
                .failRequests(failures)
                .itemsCount(items)
                .durationMs(durationMs)
                .startTime(task.getStartTime())
                .endTime(task.getEndTime())
                .build());
        trimDeque(taskHistory, 1000);

        if (stats.getSuccessRateHistory() == null) {
            stats.setSuccessRateHistory(new CopyOnWriteArrayList<>());
        }
        double rate = stats.getTaskCount() > 0 ? (stats.getSuccessCount() * 100.0 / stats.getTaskCount()) : 0;
        stats.getSuccessRateHistory().add(rate);
        if (stats.getSuccessRateHistory().size() > 100) {
            stats.getSuccessRateHistory().remove(0);
        }
    }

    public void onTaskFailed(CrawlTask task, Exception e) {
        if (!monitorEnabled) return;
        triggerAlert(AlertType.SYSTEM_ERROR, AlertLevel.CRITICAL, task.getPlatform(),
                String.format("平台[%s]任务异常失败: %s", task.getPlatform(),
                        e.getMessage() != null && e.getMessage().length() > 100
                                ? e.getMessage().substring(0, 100) + "..."
                                : e.getMessage()),
                "任务ID: " + task.getTaskId() + "\n堆栈: " + getStackTrace(e, 10));
    }

    public void onRequestFailed(String platform, int statusCode, String message) {
        if (!monitorEnabled) return;
        if (statusCode == 403 || statusCode == 401) {
            triggerAlert(AlertType.BLOCKED_DETECTED, AlertLevel.WARNING, platform,
                    "平台[" + platform + "]检测到访问被拦截，状态码: " + statusCode, message);
        } else if (statusCode == 429) {
            triggerAlert(AlertType.RATE_LIMIT_HIT, AlertLevel.WARNING, platform,
                    "平台[" + platform + "]触发频率限制", message);
        }
    }

    public void onProxyFailure(String info) {
        triggerAlert(AlertType.PROXY_FAILURE, AlertLevel.WARNING, null,
                "代理池故障: " + info, null);
    }

    private PlatformStats getOrCreateStats(String platform) {
        return platformStats.computeIfAbsent(platform, k -> {
            PlatformStats s = new PlatformStats();
            s.setPlatform(k);
            s.setSuccessRateHistory(new CopyOnWriteArrayList<>());
            return s;
        });
    }

    private void triggerAlert(AlertType type, AlertLevel level, String platform, String message, String details) {
        AlertRecord alert = AlertRecord.builder()
                .alertId(UUID.randomUUID().toString())
                .type(type)
                .level(level)
                .platform(platform)
                .message(message)
                .details(details)
                .time(LocalDateTime.now())
                .acknowledged(false)
                .build();
        alertHistory.offer(alert);
        trimDeque(alertHistory, 500);

        String logMsg = String.format("[ALERT][%s][%s] %s: %s", level, type, platform, message);
        switch (level) {
            case CRITICAL:
            case ERROR:
                log.error(logMsg);
                break;
            case WARNING:
                log.warn(logMsg);
                break;
            default:
                log.info(logMsg);
        }

        sendEmailAlert(alert);
    }

    private void sendEmailAlert(AlertRecord alert) {
        if (mailSender == null || alertEmail == null || alertEmail.isEmpty()) return;
        if (alert.getLevel() != AlertLevel.ERROR && alert.getLevel() != AlertLevel.CRITICAL) return;
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(alertEmail.split(","));
            msg.setSubject("[爬虫系统警报] " + alert.getType() + " - " + alert.getLevel());
            msg.setText("时间: " + alert.getTime() + "\n" +
                    "平台: " + alert.getPlatform() + "\n" +
                    "类型: " + alert.getType() + "\n" +
                    "级别: " + alert.getLevel() + "\n" +
                    "消息: " + alert.getMessage() + "\n\n" +
                    "详情:\n" + (alert.getDetails() != null ? alert.getDetails() : ""));
            mailSender.send(msg);
        } catch (Exception e) {
            log.warn("发送报警邮件失败: {}", e.getMessage());
        }
    }

    private void generateReport() {
        StringBuilder sb = new StringBuilder("\n========== 爬虫监控报告 ==========\n");
        sb.append("时间: ").append(LocalDateTime.now()).append("\n");
        sb.append(String.format("全局统计: 请求=%d 成功=%d 失败=%d 采集=%d 成功率=%.2f%%\n",
                totalRequests.get(), totalSuccess.get(), totalFailures.get(),
                totalItemsCollected.get(),
                totalRequests.get() > 0 ? totalSuccess.get() * 100.0 / totalRequests.get() : 0));
        sb.append("-------- 平台详情 --------\n");
        for (PlatformStats s : platformStats.values()) {
            double rate = s.getTaskCount() > 0 ? s.getSuccessCount() * 100.0 / s.getTaskCount() : 0;
            sb.append(String.format("[%s] 任务:%d 成功:%d 失败:%d 采集:%d 成功率:%.2f%% 平均:%dms 最大:%dms 连续失败:%d\n",
                    s.getPlatform(), s.getTaskCount(), s.getSuccessCount(), s.getFailureCount(),
                    s.getItemsCount(), rate, s.getAvgResponseTime(), s.getMaxResponseTime(), s.getConsecutiveFailures()));
        }
        sb.append("========== 报告结束 ==========\n");
        log.info(sb.toString());
    }

    private void checkActiveAlerts() {
        long criticalCount = alertHistory.stream()
                .filter(a -> !a.isAcknowledged() && (a.getLevel() == AlertLevel.CRITICAL || a.getLevel() == AlertLevel.ERROR))
                .count();
        if (criticalCount > 0) {
            log.warn("当前有{}个未确认的高优先级警报", criticalCount);
        }
    }

    public Map<String, Object> getMonitorData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("global", Map.of(
                "totalRequests", totalRequests.get(),
                "totalSuccess", totalSuccess.get(),
                "totalFailures", totalFailures.get(),
                "totalItems", totalItemsCollected.get(),
                "successRate", totalRequests.get() > 0
                        ? String.format("%.2f", totalSuccess.get() * 100.0 / totalRequests.get()) + "%"
                        : "0%"
        ));
        List<Map<String, Object>> platforms = new ArrayList<>();
        for (PlatformStats s : platformStats.values()) {
            platforms.add(new LinkedHashMap<>(Map.ofEntries(
                    Map.entry("platform", s.getPlatform()),
                    Map.entry("taskCount", s.getTaskCount()),
                    Map.entry("successCount", s.getSuccessCount()),
                    Map.entry("failureCount", s.getFailureCount()),
                    Map.entry("itemsCount", s.getItemsCount()),
                    Map.entry("successRate", s.getTaskCount() > 0
                            ? String.format("%.2f%%", s.getSuccessCount() * 100.0 / s.getTaskCount())
                            : "0%"),
                    Map.entry("avgResponseTime", s.getAvgResponseTime() + "ms"),
                    Map.entry("maxResponseTime", s.getMaxResponseTime() + "ms"),
                    Map.entry("consecutiveFailures", s.getConsecutiveFailures()),
                    Map.entry("emptyDataRuns", s.getEmptyDataRuns()),
                    Map.entry("lastSuccessTime", s.getLastSuccessTime()),
                    Map.entry("lastFailureTime", s.getLastFailureTime())
            )));
        }
        data.put("platforms", platforms);

        List<Map<String, Object>> alerts = new ArrayList<>();
        Iterator<AlertRecord> it = alertHistory.descendingIterator();
        int count = 0;
        while (it.hasNext() && count < 20) {
            AlertRecord a = it.next();
            alerts.add(Map.of(
                    "type", a.getType(),
                    "level", a.getLevel(),
                    "platform", a.getPlatform() != null ? a.getPlatform() : "-",
                    "message", a.getMessage(),
                    "time", a.getTime(),
                    "acknowledged", a.isAcknowledged()
            ));
            count++;
        }
        data.put("recentAlerts", alerts);

        List<Map<String, Object>> tasks = new ArrayList<>();
        Iterator<TaskRecord> tit = taskHistory.descendingIterator();
        count = 0;
        while (tit.hasNext() && count < 20) {
            TaskRecord t = tit.next();
            tasks.add(Map.of(
                    "taskId", t.getTaskId(),
                    "platform", t.getPlatform(),
                    "status", t.getStatus(),
                    "items", t.getItemsCount(),
                    "duration", t.getDurationMs() + "ms",
                    "startTime", t.getStartTime()
            ));
            count++;
        }
        data.put("recentTasks", tasks);

        return data;
    }

    private String getStackTrace(Exception e, int maxLines) {
        StackTraceElement[] trace = e.getStackTrace();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(maxLines, trace.length); i++) {
            sb.append("\tat ").append(trace[i]).append("\n");
        }
        return sb.toString();
    }

    private <T> void trimDeque(ConcurrentLinkedDeque<T> deque, int maxSize) {
        while (deque.size() > maxSize) {
            deque.poll();
        }
    }

    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }
}
