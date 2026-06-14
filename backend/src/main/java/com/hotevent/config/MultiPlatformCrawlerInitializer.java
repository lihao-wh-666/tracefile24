package com.hotevent.config;

import com.hotevent.crawler.datasource.CrawlScheduler;
import com.hotevent.crawler.datasource.DataSourceConfig;
import com.hotevent.crawler.datasource.DataSourceManager;
import com.hotevent.crawler.http.ProxyInfo;
import com.hotevent.crawler.http.ProxyPoolManager;
import com.hotevent.crawler.http.RateLimitManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "hot-event.multi-platform", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MultiPlatformCrawlerInitializer implements CommandLineRunner {

    @Autowired
    private MultiPlatformCrawlerProperties properties;

    @Autowired
    private CrawlScheduler crawlScheduler;

    @Autowired
    private DataSourceManager dataSourceManager;

    @Autowired
    private ProxyPoolManager proxyPoolManager;

    @Autowired
    private RateLimitManager rateLimitManager;

    @Override
    public void run(String... args) throws Exception {
        log.info("========== 多平台数据采集系统初始化启动 ==========");
        try {
            initProxyPool();
            initRateLimitConfig();
            applyPlatformConfigs();
            if (properties.getMultiPlatform().isAutoStart()) {
                log.info("自动启动CrawlScheduler...");
                crawlScheduler.start();
            } else {
                log.info("自动启动已禁用，需手动启动CrawlScheduler");
                dataSourceManager.init();
            }
            logSystemInfo();
            log.info("========== 多平台数据采集系统初始化完成 ==========");
        } catch (Exception e) {
            log.error("多平台数据采集系统初始化失败: {}", e.getMessage(), e);
        }
    }

    private void initProxyPool() {
        MultiPlatformCrawlerProperties.ProxyConfig proxyCfg = properties.getCrawler().getProxy();
        if (proxyCfg != null && proxyCfg.isEnabled() && proxyCfg.getPool() != null) {
            List<MultiPlatformCrawlerProperties.ProxyItem> items = proxyCfg.getPool();
            int added = 0;
            for (MultiPlatformCrawlerProperties.ProxyItem item : items) {
                try {
                    ProxyInfo.ProxyType type = ProxyInfo.ProxyType.valueOf(
                            item.getType() != null ? item.getType().toUpperCase() : "HTTP");
                    ProxyInfo proxy = ProxyInfo.builder()
                            .host(item.getHost())
                            .port(item.getPort())
                            .username(item.getUsername())
                            .password(item.getPassword())
                            .type(type)
                            .build();
                    proxyPoolManager.addProxy(proxy);
                    added++;
                } catch (Exception e) {
                    log.warn("添加代理失败 {}:{} : {}", item.getHost(), item.getPort(), e.getMessage());
                }
            }
            log.info("代理池初始化完成: 添加{}个代理，可用{}个", added, proxyPoolManager.getUsableCount());
        } else {
            log.info("代理功能未启用或未配置代理池");
        }
    }

    private void initRateLimitConfig() {
        MultiPlatformCrawlerProperties.RateLimitConfig rl = properties.getCrawler().getRateLimit();
        if (rl != null) {
            rateLimitManager.setRateLimitEnabled(rl.isEnabled());
            log.info("限流配置: 全局QPS={}, 默认域名QPS={}, 启用={}",
                    rl.getGlobalQps(), rl.getDefaultQps(), rl.isEnabled());
        }

        var platformConfigs = properties.getCrawler().getPlatforms();
        if (platformConfigs != null) {
            for (var entry : platformConfigs.entrySet()) {
                String code = entry.getKey();
                var cfg = entry.getValue();
                int mc = cfg.getMaxConcurrent();
                double qps = mc > 0 ? Math.max(0.5, mc * 0.8) : properties.getMultiPlatform().getDefaultQps();
                rateLimitManager.setDomainQps(getDomainForPlatform(code), qps);
            }
        }
    }

    private String getDomainForPlatform(String code) {
        return switch (code) {
            case "wechat" -> "weixin.sogou.com";
            case "xiaohongshu" -> "www.xiaohongshu.com";
            case "douyin" -> "www.douyin.com";
            case "bilibili" -> "api.bilibili.com";
            case "government" -> "www.gov.cn";
            default -> "localhost";
        };
    }

    private void applyPlatformConfigs() {
        var platformConfigs = properties.getCrawler().getPlatforms();
        if (platformConfigs == null) return;

        dataSourceManager.init();

        for (var entry : platformConfigs.entrySet()) {
            String code = entry.getKey();
            MultiPlatformCrawlerProperties.PlatformConfig cfg = entry.getValue();
            if (!cfg.isEnabled()) {
                dataSourceManager.disableSource(code);
                log.info("数据源[{}]已禁用（按配置）", code);
                continue;
            }

            DataSourceConfig dsc = dataSourceManager.getConfig(code);
            if (dsc != null) {
                if (cfg.getCron() != null) dsc.setCron(cfg.getCron());
                dsc.setMaxConcurrent(cfg.getMaxConcurrent());
                dsc.setMaxItemsPerCrawl(cfg.getMaxItems());
                if (cfg.getParams() != null) {
                    for (var p : cfg.getParams().entrySet()) {
                        dsc.setParam(p.getKey(), p.getValue());
                    }
                }
                log.debug("应用数据源配置: {} cron={} maxConcurrent={} maxItems={}",
                        code, dsc.getCron(), dsc.getMaxConcurrent(), dsc.getMaxItemsPerCrawl());
            }
        }
    }

    private void logSystemInfo() {
        log.info("系统配置摘要:");
        log.info("  - 总数据源: {}", dataSourceManager.getSourceStatus().get("totalSources"));
        log.info("  - 启用数据源: {}", dataSourceManager.getSourceStatus().get("enabledSources"));
        log.info("  - 线程池大小: {}", properties.getMultiPlatform().getThreadPoolSize());
        log.info("  - 自动启动: {}", properties.getMultiPlatform().isAutoStart());
        log.info("  - 增量爬取: {}", properties.getMultiPlatform().getStorage().isIncrementalEnabled());
        log.info("  - 代理启用: {}", properties.getCrawler().getProxy().isEnabled());
        log.info("  - 限流启用: {}", properties.getCrawler().getRateLimit().isEnabled());
        log.info("  - 监控启用: {}", properties.getCrawler().getMonitor().isEnabled());
        log.info("  - Robots合规: {}", properties.getCrawler().getCompliance().isRobotsEnabled());
    }
}
