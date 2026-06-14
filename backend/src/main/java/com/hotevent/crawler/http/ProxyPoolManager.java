package com.hotevent.crawler.http;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class ProxyPoolManager {

    @Value("${hot-event.crawler.proxy.enabled:false}")
    private boolean proxyEnabled;

    @Value("${hot-event.crawler.proxy.min-success-rate:0.6}")
    private double minSuccessRate;

    @Value("${hot-event.crawler.proxy.max-failures:10}")
    private int maxFailures;

    @Value("${hot-event.crawler.proxy.check-interval-ms:60000}")
    private long checkIntervalMs;

    private final CopyOnWriteArrayList<ProxyInfo> proxyPool = new CopyOnWriteArrayList<>();
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);
    private volatile long lastCleanupTime = 0;

    public void addProxy(ProxyInfo proxy) {
        if (proxy != null && proxy.isValid()) {
            proxyPool.addIfAbsent(proxy);
            log.info("添加代理到池: {}:{}，当前池大小: {}", proxy.getHost(), proxy.getPort(), proxyPool.size());
        }
    }

    public void addProxies(List<ProxyInfo> proxies) {
        if (proxies != null) {
            for (ProxyInfo p : proxies) {
                addProxy(p);
            }
        }
    }

    public void removeProxy(ProxyInfo proxy) {
        proxyPool.remove(proxy);
    }

    public ProxyInfo getProxy() {
        if (!proxyEnabled || proxyPool.isEmpty()) {
            return null;
        }
        cleanupUnusableProxies();
        List<ProxyInfo> usable = getUsableProxies();
        if (usable.isEmpty()) {
            log.warn("代理池中没有可用代理，将直连请求");
            return null;
        }
        int idx = Math.abs(roundRobinIndex.getAndIncrement() % usable.size());
        return usable.get(idx);
    }

    public ProxyInfo getRandomProxy() {
        if (!proxyEnabled || proxyPool.isEmpty()) {
            return null;
        }
        cleanupUnusableProxies();
        List<ProxyInfo> usable = getUsableProxies();
        if (usable.isEmpty()) {
            return null;
        }
        Collections.shuffle(usable);
        return usable.get(0);
    }

    private List<ProxyInfo> getUsableProxies() {
        List<ProxyInfo> usable = new ArrayList<>();
        for (ProxyInfo p : proxyPool) {
            if (p.isUsable(minSuccessRate, maxFailures)) {
                usable.add(p);
            }
        }
        return usable;
    }

    private void cleanupUnusableProxies() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime < checkIntervalMs) {
            return;
        }
        lastCleanupTime = now;
        int removed = 0;
        for (ProxyInfo p : new ArrayList<>(proxyPool)) {
            if (!p.isUsable(minSuccessRate, maxFailures)) {
                proxyPool.remove(p);
                removed++;
            }
        }
        if (removed > 0) {
            log.info("清理了{}个不可用代理，当前池大小: {}", removed, proxyPool.size());
        }
    }

    public void recordSuccess(ProxyInfo proxy) {
        if (proxy != null) {
            proxy.recordSuccess();
        }
    }

    public void recordFailure(ProxyInfo proxy) {
        if (proxy != null) {
            proxy.recordFailure();
        }
    }

    public int getPoolSize() {
        return proxyPool.size();
    }

    public int getUsableCount() {
        return getUsableProxies().size();
    }

    public List<ProxyInfo> getAllProxies() {
        return new ArrayList<>(proxyPool);
    }

    public boolean isProxyEnabled() {
        return proxyEnabled;
    }
}
