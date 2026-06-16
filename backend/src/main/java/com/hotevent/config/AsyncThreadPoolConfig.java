package com.hotevent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Configuration
@EnableAsync
public class AsyncThreadPoolConfig {

    private static final int CPU_CORES = Runtime.getRuntime().availableProcessors();

    private static final AtomicInteger cpuPoolThreadCounter = new AtomicInteger(1);
    private static final AtomicInteger ioPoolThreadCounter = new AtomicInteger(1);

    @Autowired
    private AsyncProperties asyncProperties;

    private int parseInt(String value, int defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("无法解析线程池配置值 '{}', 使用默认值: {}", value, defaultValue);
            return defaultValue;
        }
    }

    @Bean(name = "cpuIntensiveThreadPool")
    public ThreadPoolExecutor cpuIntensiveThreadPool() {
        AsyncProperties.PoolDetail config = asyncProperties.getThreadPool().getCpuIntensive();

        int defaultCore = Math.max(CPU_CORES - 1, 1);
        int defaultMax = CPU_CORES + 1;

        int corePoolSize = parseInt(config.getCorePoolSize(), defaultCore);
        int maxPoolSize = parseInt(config.getMaxPoolSize(), defaultMax);
        int queueCapacity = config.getQueueCapacity() > 0 ? config.getQueueCapacity() : 100;
        long keepAliveTime = config.getKeepAliveSeconds() > 0 ? config.getKeepAliveSeconds() : 60L;

        RejectedExecutionHandler rejectionPolicy = getRejectionPolicy(config.getRejectionPolicy());

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                keepAliveTime,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                r -> {
                    Thread thread = new Thread(r, "cpu-intensive-pool-" + cpuPoolThreadCounter.getAndIncrement());
                    thread.setDaemon(false);
                    return thread;
                },
                rejectionPolicy
        );

        log.info("==========================================");
        log.info("[线程池初始化] CPU密集型线程池创建完成");
        log.info("  说明: {}", config.getDescription());
        log.info("  CPU核心数: {}", CPU_CORES);
        log.info("  核心线程数: {}", corePoolSize);
        log.info("  最大线程数: {}", maxPoolSize);
        log.info("  队列容量: {}", queueCapacity);
        log.info("  存活时间: {}秒", keepAliveTime);
        log.info("  拒绝策略: {}", rejectionPolicy.getClass().getSimpleName());
        log.info("==========================================");

        return executor;
    }

    @Bean(name = "ioIntensiveThreadPool")
    public ThreadPoolExecutor ioIntensiveThreadPool() {
        AsyncProperties.PoolDetail config = asyncProperties.getThreadPool().getIoIntensive();

        int defaultCore = CPU_CORES * 2;
        int defaultMax = CPU_CORES * 4;

        int corePoolSize = parseInt(config.getCorePoolSize(), defaultCore);
        int maxPoolSize = parseInt(config.getMaxPoolSize(), defaultMax);
        int queueCapacity = config.getQueueCapacity() > 0 ? config.getQueueCapacity() : 500;
        long keepAliveTime = config.getKeepAliveSeconds() > 0 ? config.getKeepAliveSeconds() : 120L;

        RejectedExecutionHandler rejectionPolicy = getRejectionPolicy(config.getRejectionPolicy());

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                keepAliveTime,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                r -> {
                    Thread thread = new Thread(r, "io-intensive-pool-" + ioPoolThreadCounter.getAndIncrement());
                    thread.setDaemon(false);
                    return thread;
                },
                rejectionPolicy
        );

        log.info("==========================================");
        log.info("[线程池初始化] IO密集型线程池创建完成");
        log.info("  说明: {}", config.getDescription());
        log.info("  CPU核心数: {}", CPU_CORES);
        log.info("  核心线程数: {}", corePoolSize);
        log.info("  最大线程数: {}", maxPoolSize);
        log.info("  队列容量: {}", queueCapacity);
        log.info("  存活时间: {}秒", keepAliveTime);
        log.info("  拒绝策略: {}", rejectionPolicy.getClass().getSimpleName());
        log.info("==========================================");

        return executor;
    }

    private RejectedExecutionHandler getRejectionPolicy(String policyName) {
        if (policyName == null || policyName.trim().isEmpty()) {
            return new ThreadPoolExecutor.CallerRunsPolicy();
        }
        switch (policyName.trim()) {
            case "AbortPolicy":
                return new ThreadPoolExecutor.AbortPolicy();
            case "DiscardPolicy":
                return new ThreadPoolExecutor.DiscardPolicy();
            case "DiscardOldestPolicy":
                return new ThreadPoolExecutor.DiscardOldestPolicy();
            case "CallerRunsPolicy":
            default:
                return new ThreadPoolExecutor.CallerRunsPolicy();
        }
    }

    public static class ThreadPoolMetrics {
        private final String poolName;
        private final int corePoolSize;
        private final int maximumPoolSize;
        private final int activeThreadCount;
        private final int poolSize;
        private final long completedTaskCount;
        private final long taskCount;
        private final int queueSize;
        private final int queueRemainingCapacity;
        private final double completionRate;
        private final double queueUtilization;

        public ThreadPoolMetrics(String poolName, ThreadPoolExecutor executor) {
            this.poolName = poolName;
            this.corePoolSize = executor.getCorePoolSize();
            this.maximumPoolSize = executor.getMaximumPoolSize();
            this.activeThreadCount = executor.getActiveCount();
            this.poolSize = executor.getPoolSize();
            this.completedTaskCount = executor.getCompletedTaskCount();
            this.taskCount = executor.getTaskCount();
            this.queueSize = executor.getQueue().size();
            this.queueRemainingCapacity = executor.getQueue().remainingCapacity();
            this.completionRate = taskCount > 0 ? (double) completedTaskCount / taskCount * 100 : 100.0;
            int totalCapacity = queueSize + queueRemainingCapacity;
            this.queueUtilization = totalCapacity > 0 ? (double) queueSize / totalCapacity * 100 : 0.0;
        }

        public String getPoolName() { return poolName; }
        public int getCorePoolSize() { return corePoolSize; }
        public int getMaximumPoolSize() { return maximumPoolSize; }
        public int getActiveThreadCount() { return activeThreadCount; }
        public int getPoolSize() { return poolSize; }
        public long getCompletedTaskCount() { return completedTaskCount; }
        public long getTaskCount() { return taskCount; }
        public int getQueueSize() { return queueSize; }
        public int getQueueRemainingCapacity() { return queueRemainingCapacity; }
        public double getCompletionRate() { return completionRate; }
        public double getQueueUtilization() { return queueUtilization; }
    }
}
