package com.hotevent.crawler.http;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryStrategy {

    @Builder.Default
    private int maxAttempts = 3;

    @Builder.Default
    private long initialDelayMs = 1000;

    @Builder.Default
    private double multiplier = 2.0;

    @Builder.Default
    private long maxDelayMs = 30000;

    @Builder.Default
    private double jitterFactor = 0.5;

    @Builder.Default
    private Set<Class<? extends Throwable>> retryableExceptions = new HashSet<>(Arrays.asList(
            SocketTimeoutException.class,
            UnknownHostException.class,
            java.net.ConnectException.class,
            java.net.SocketException.class,
            java.io.IOException.class
    ));

    @Builder.Default
    private Set<Integer> retryableStatusCodes = new HashSet<>(Arrays.asList(
            408, 429, 500, 502, 503, 504
    ));

    public boolean shouldRetry(int attempt, Exception exception, Integer statusCode) {
        if (attempt >= maxAttempts) {
            return false;
        }
        if (statusCode != null && retryableStatusCodes.contains(statusCode)) {
            log.debug("HTTP状态码[{}]属于可重试状态码", statusCode);
            return true;
        }
        if (exception != null) {
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            for (Class<? extends Throwable> retryable : retryableExceptions) {
                if (retryable.isInstance(cause) || retryable.isInstance(exception)) {
                    log.debug("异常[{}]属于可重试异常类型", cause.getClass().getSimpleName());
                    return true;
                }
            }
        }
        return false;
    }

    public long calculateDelay(int attempt) {
        if (attempt <= 0) attempt = 1;
        long delay = (long) (initialDelayMs * Math.pow(multiplier, attempt - 1));
        delay = Math.min(delay, maxDelayMs);
        if (jitterFactor > 0) {
            long jitter = (long) (delay * jitterFactor * (Math.random() - 0.5) * 2);
            delay = Math.max(initialDelayMs, delay + jitter);
        }
        return delay;
    }

    public void waitBeforeNextRetry(int attempt) throws InterruptedException {
        long delay = calculateDelay(attempt);
        log.debug("等待{}ms后进行第{}次重试", delay, attempt + 1);
        Thread.sleep(delay);
    }
}
