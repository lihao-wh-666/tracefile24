package com.hotevent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Supplier;

@Slf4j
@Component
public class AsyncTaskExecutor {

    @Autowired
    @Qualifier("cpuIntensiveThreadPool")
    private ThreadPoolExecutor cpuIntensiveThreadPool;

    @Autowired
    @Qualifier("ioIntensiveThreadPool")
    private ThreadPoolExecutor ioIntensiveThreadPool;

    public <T> CompletableFuture<T> submitCpuTask(Supplier<T> task, String taskName) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            String threadName = Thread.currentThread().getName();
            log.debug("[CPU密集型任务] 开始执行: {} | 线程: {}", taskName, threadName);
            try {
                T result = task.get();
                long cost = System.currentTimeMillis() - startTime;
                log.debug("[CPU密集型任务] 执行完成: {} | 耗时: {}ms | 线程: {}", taskName, cost, threadName);
                return result;
            } catch (Exception e) {
                long cost = System.currentTimeMillis() - startTime;
                log.error("[CPU密集型任务] 执行异常: {} | 耗时: {}ms | 错误: {} | 线程: {}",
                        taskName, cost, e.getMessage(), threadName, e);
                throw e;
            }
        }, cpuIntensiveThreadPool).exceptionally(ex -> {
            log.error("[CPU密集型任务] 未捕获异常: {} | 错误: {}", taskName, ex.getMessage(), ex);
            return null;
        });
    }

    public <T> CompletableFuture<T> submitIoTask(Supplier<T> task, String taskName) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            String threadName = Thread.currentThread().getName();
            log.debug("[IO密集型任务] 开始执行: {} | 线程: {}", taskName, threadName);
            try {
                T result = task.get();
                long cost = System.currentTimeMillis() - startTime;
                log.debug("[IO密集型任务] 执行完成: {} | 耗时: {}ms | 线程: {}", taskName, cost, threadName);
                return result;
            } catch (Exception e) {
                long cost = System.currentTimeMillis() - startTime;
                log.error("[IO密集型任务] 执行异常: {} | 耗时: {}ms | 错误: {} | 线程: {}",
                        taskName, cost, e.getMessage(), threadName, e);
                throw e;
            }
        }, ioIntensiveThreadPool).exceptionally(ex -> {
            log.error("[IO密集型任务] 未捕获异常: {} | 错误: {}", taskName, ex.getMessage(), ex);
            return null;
        });
    }

    public CompletableFuture<Void> submitIoRunnable(Runnable task, String taskName) {
        return CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            String threadName = Thread.currentThread().getName();
            log.debug("[IO密集型Runnable] 开始执行: {} | 线程: {}", taskName, threadName);
            try {
                task.run();
                long cost = System.currentTimeMillis() - startTime;
                log.debug("[IO密集型Runnable] 执行完成: {} | 耗时: {}ms | 线程: {}", taskName, cost, threadName);
            } catch (Exception e) {
                long cost = System.currentTimeMillis() - startTime;
                log.error("[IO密集型Runnable] 执行异常: {} | 耗时: {}ms | 错误: {} | 线程: {}",
                        taskName, cost, e.getMessage(), threadName, e);
                throw e;
            }
        }, ioIntensiveThreadPool).exceptionally(ex -> {
            log.error("[IO密集型Runnable] 未捕获异常: {} | 错误: {}", taskName, ex.getMessage(), ex);
            return null;
        });
    }

    public CompletableFuture<Void> submitCpuRunnable(Runnable task, String taskName) {
        return CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            String threadName = Thread.currentThread().getName();
            log.debug("[CPU密集型Runnable] 开始执行: {} | 线程: {}", taskName, threadName);
            try {
                task.run();
                long cost = System.currentTimeMillis() - startTime;
                log.debug("[CPU密集型Runnable] 执行完成: {} | 耗时: {}ms | 线程: {}", taskName, cost, threadName);
            } catch (Exception e) {
                long cost = System.currentTimeMillis() - startTime;
                log.error("[CPU密集型Runnable] 执行异常: {} | 耗时: {}ms | 错误: {} | 线程: {}",
                        taskName, cost, e.getMessage(), threadName, e);
                throw e;
            }
        }, cpuIntensiveThreadPool).exceptionally(ex -> {
            log.error("[CPU密集型Runnable] 未捕获异常: {} | 错误: {}", taskName, ex.getMessage(), ex);
            return null;
        });
    }

    @SafeVarargs
    public final <T> CompletableFuture<List<T>> allOf(List<CompletableFuture<T>> futures) {
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<T> results = new ArrayList<>();
                    for (CompletableFuture<T> future : futures) {
                        T result = future.join();
                        if (result != null) {
                            if (result instanceof Collection) {
                                results.addAll((Collection<? extends T>) result);
                            } else {
                                results.add(result);
                            }
                        }
                    }
                    return results;
                });
    }

    public <T> CompletableFuture<List<T>> parallelExecuteCpu(List<Supplier<T>> tasks, String baseTaskName) {
        List<CompletableFuture<T>> futures = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            final int idx = i;
            futures.add(submitCpuTask(tasks.get(i), baseTaskName + "-" + (idx + 1)));
        }
        return allOf(futures);
    }

    public <T> CompletableFuture<List<T>> parallelExecuteIo(List<Supplier<T>> tasks, String baseTaskName) {
        List<CompletableFuture<T>> futures = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            final int idx = i;
            futures.add(submitIoTask(tasks.get(i), baseTaskName + "-" + (idx + 1)));
        }
        return allOf(futures);
    }

    public Executor getCpuExecutor() {
        return cpuIntensiveThreadPool;
    }

    public Executor getIoExecutor() {
        return ioIntensiveThreadPool;
    }

    public Map<String, Object> getCpuPoolMetrics() {
        return getPoolMetrics(cpuIntensiveThreadPool, "CPU密集型线程池");
    }

    public Map<String, Object> getIoPoolMetrics() {
        return getPoolMetrics(ioIntensiveThreadPool, "IO密集型线程池");
    }

    public Map<String, Object> getAllPoolMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("cpuIntensive", getCpuPoolMetrics());
        metrics.put("ioIntensive", getIoPoolMetrics());
        return metrics;
    }

    private Map<String, Object> getPoolMetrics(ThreadPoolExecutor executor, String poolName) {
        AsyncThreadPoolConfig.ThreadPoolMetrics m = new AsyncThreadPoolConfig.ThreadPoolMetrics(poolName, executor);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("poolName", m.getPoolName());
        map.put("corePoolSize", m.getCorePoolSize());
        map.put("maximumPoolSize", m.getMaximumPoolSize());
        map.put("activeThreadCount", m.getActiveThreadCount());
        map.put("poolSize", m.getPoolSize());
        map.put("completedTaskCount", m.getCompletedTaskCount());
        map.put("totalTaskCount", m.getTaskCount());
        map.put("queueSize", m.getQueueSize());
        map.put("queueRemainingCapacity", m.getQueueRemainingCapacity());
        map.put("completionRate", String.format("%.2f%%", m.getCompletionRate()));
        map.put("queueUtilization", String.format("%.2f%%", m.getQueueUtilization()));
        return map;
    }
}
