package com.hotevent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "hot-event")
public class MultiPlatformCrawlerProperties {

    private MultiPlatform multiPlatform = new MultiPlatform();
    private CrawlerConfig crawler = new CrawlerConfig();

    @Data
    public static class MultiPlatform {
        private boolean enabled = true;
        private boolean autoStart = true;
        private int threadPoolSize = 16;
        private double defaultQps = 1.5;
        private StorageConfig storage = new StorageConfig();
    }

    @Data
    public static class StorageConfig {
        private int batchSize = 50;
        private boolean incrementalEnabled = true;
    }

    @Data
    public static class CrawlerConfig {
        private boolean enabled = true;
        private String cron = "0 */30 * * * ?";
        private List<String> sources;
        private int timeout = 30000;
        private int connectTimeout = 15000;
        private List<String> userAgents;
        private RetryConfig retry = new RetryConfig();
        private long requestIntervalMs = 1500;
        private ValidationConfig validation = new ValidationConfig();
        private RateLimitConfig rateLimit = new RateLimitConfig();
        private ProxyConfig proxy = new ProxyConfig();
        private MonitorConfig monitor = new MonitorConfig();
        private ComplianceConfig compliance = new ComplianceConfig();
        private Map<String, PlatformConfig> platforms = new HashMap<>();
    }

    @Data
    public static class RetryConfig {
        private int maxAttempts = 3;
        private long delayMs = 3000;
        private double multiplier = 2.0;
    }

    @Data
    public static class ValidationConfig {
        private boolean requireTitle = true;
        private int minTitleLength = 2;
        private boolean requireHotValue = false;
        private int minEventCount = 3;
    }

    @Data
    public static class RateLimitConfig {
        private boolean enabled = true;
        private double defaultQps = 2.0;
        private double globalQps = 20.0;
    }

    @Data
    public static class ProxyConfig {
        private boolean enabled = false;
        private double minSuccessRate = 0.6;
        private int maxFailures = 10;
        private long checkIntervalMs = 60000;
        private List<ProxyItem> pool;
    }

    @Data
    public static class ProxyItem {
        private String host;
        private int port;
        private String username;
        private String password;
        private String type = "HTTP";
    }

    @Data
    public static class MonitorConfig {
        private boolean enabled = true;
        private String alertEmail = "";
        private AlertThresholdConfig alertThreshold = new AlertThresholdConfig();
    }

    @Data
    public static class AlertThresholdConfig {
        private int failures = 5;
        private int emptyData = 3;
        private long highResponseTime = 30000;
    }

    @Data
    public static class ComplianceConfig {
        private boolean robotsEnabled = true;
        private int robotsCacheHours = 24;
        private boolean respectCrawlDelay = true;
        private String userAgent = "HotEventCrawler/1.0";
    }

    @Data
    public static class PlatformConfig {
        private boolean enabled = true;
        private String baseUrl;
        private String cron;
        private int maxConcurrent = 2;
        private int maxItems = 50;
        private Map<String, String> params = new HashMap<>();
    }
}
