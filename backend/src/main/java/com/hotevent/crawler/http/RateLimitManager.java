package com.hotevent.crawler.http;

import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RateLimitManager {

    @Value("${hot-event.crawler.rate-limit.default-qps:2.0}")
    private double defaultQps;

    @Value("${hot-event.crawler.rate-limit.global-qps:10.0}")
    private double globalQps;

    @Value("${hot-event.crawler.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    private final Map<String, RateLimiter> domainRateLimiters = new ConcurrentHashMap<>();
    private final Map<String, Long> domainLastRequestTime = new ConcurrentHashMap<>();
    private volatile RateLimiter globalRateLimiter;

    public RateLimitManager() {
    }

    private RateLimiter getGlobalRateLimiter() {
        if (globalRateLimiter == null) {
            synchronized (this) {
                if (globalRateLimiter == null) {
                    globalRateLimiter = RateLimiter.create(globalQps);
                }
            }
        }
        return globalRateLimiter;
    }

    private RateLimiter getDomainRateLimiter(String domain) {
        return domainRateLimiters.computeIfAbsent(domain, k -> RateLimiter.create(defaultQps));
    }

    public void setDomainQps(String domain, double qps) {
        if (domain == null || domain.isEmpty()) return;
        if (qps <= 0) {
            domainRateLimiters.remove(domain);
            return;
        }
        RateLimiter existing = domainRateLimiters.get(domain);
        if (existing != null) {
            existing.setRate(qps);
        } else {
            domainRateLimiters.put(domain, RateLimiter.create(qps));
        }
        log.info("设置域名[{}]的QPS限制为: {}", domain, qps);
    }

    public void acquire(String domain) {
        if (!rateLimitEnabled) return;
        if (domain == null || domain.isEmpty()) {
            getGlobalRateLimiter().acquire();
            return;
        }
        getGlobalRateLimiter().acquire();
        getDomainRateLimiter(domain).acquire();
        domainLastRequestTime.put(domain, System.currentTimeMillis());
    }

    public boolean tryAcquire(String domain, long timeoutMs) {
        if (!rateLimitEnabled) return true;
        try {
            if (domain == null || domain.isEmpty()) {
                return getGlobalRateLimiter().tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
            }
            boolean globalOk = getGlobalRateLimiter().tryAcquire(timeoutMs / 2, TimeUnit.MILLISECONDS);
            if (!globalOk) return false;
            boolean domainOk = getDomainRateLimiter(domain).tryAcquire(timeoutMs / 2, TimeUnit.MILLISECONDS);
            if (domainOk) {
                domainLastRequestTime.put(domain, System.currentTimeMillis());
            }
            return domainOk;
        } catch (Exception e) {
            log.warn("限流获取异常: {}", e.getMessage());
            return false;
        }
    }

    public long getTimeSinceLastRequest(String domain) {
        Long lastTime = domainLastRequestTime.get(domain);
        if (lastTime == null) return Long.MAX_VALUE;
        return System.currentTimeMillis() - lastTime;
    }

    public void clearDomainLimiter(String domain) {
        domainRateLimiters.remove(domain);
        domainLastRequestTime.remove(domain);
    }

    public void setRateLimitEnabled(boolean enabled) {
        this.rateLimitEnabled = enabled;
        log.info("请求频率限制已{}", enabled ? "启用" : "禁用");
    }
}
