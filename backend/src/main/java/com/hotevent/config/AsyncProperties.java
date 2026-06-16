package com.hotevent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hot-event.async")
public class AsyncProperties {

    private ThreadPoolConfig threadPool = new ThreadPoolConfig();
    private MonitorConfig monitor = new MonitorConfig();

    @Data
    public static class ThreadPoolConfig {
        private PoolDetail cpuIntensive = new PoolDetail();
        private PoolDetail ioIntensive = new PoolDetail();
    }

    @Data
    public static class PoolDetail {
        private String description;
        private String corePoolSize;
        private String maxPoolSize;
        private int queueCapacity = 100;
        private int keepAliveSeconds = 60;
        private String rejectionPolicy = "CallerRunsPolicy";
    }

    @Data
    public static class MonitorConfig {
        private boolean enabled = true;
        private String metricsEndpoint = "/async-monitor";
    }
}
