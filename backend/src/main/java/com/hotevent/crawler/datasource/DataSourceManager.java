package com.hotevent.crawler.datasource;

import com.hotevent.crawler.adapter.PlatformAdapter;
import com.hotevent.crawler.adapter.impl.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class DataSourceManager {

    @Autowired
    private WeChatAdapter weChatAdapter;

    @Autowired
    private XiaohongshuAdapter xiaohongshuAdapter;

    @Autowired
    private DouyinAdapter douyinAdapter;

    @Autowired
    private BilibiliAdapter bilibiliAdapter;

    @Autowired
    private LocalForumAdapter localForumAdapter;

    @Autowired
    private GovernmentPlatformAdapter governmentPlatformAdapter;

    @Autowired
    private BaiduAdapter baiduAdapter;

    @Autowired
    private ZhihuAdapter zhihuAdapter;

    @Autowired
    private WeiboAdapter weiboAdapter;

    private final Map<String, PlatformAdapter> adapterRegistry = new ConcurrentHashMap<>();
    private final Map<String, DataSourceConfig> configRegistry = new ConcurrentHashMap<>();
    private final Set<String> enabledSources = Collections.synchronizedSet(new HashSet<>());

    private volatile boolean initialized = false;

    public void init() {
        if (initialized) {
            log.debug("DataSourceManager已初始化，跳过重复初始化");
            return;
        }
        registerAdapter(weChatAdapter);
        registerAdapter(xiaohongshuAdapter);
        registerAdapter(douyinAdapter);
        registerAdapter(bilibiliAdapter);
        registerAdapter(localForumAdapter);
        registerAdapter(governmentPlatformAdapter);
        registerAdapter(baiduAdapter);
        registerAdapter(zhihuAdapter);
        registerAdapter(weiboAdapter);

        List<DataSourceConfig> defaultConfigs = buildDefaultConfigs();
        for (DataSourceConfig config : defaultConfigs) {
            registerConfig(config);
            if (config.isEnabled()) {
                enabledSources.add(config.getCode());
            }
        }
        initialized = true;
        log.info("DataSourceManager初始化完成，已注册{}个数据源，启用{}个",
                adapterRegistry.size(), enabledSources.size());
    }

    private List<DataSourceConfig> buildDefaultConfigs() {
        List<DataSourceConfig> configs = new ArrayList<>();
        configs.add(DataSourceConfig.builder()
                .code("wechat").name("微信公众号").type("social_media")
                .priority(3).enabled(true).maxConcurrent(2).maxRequestsPerHour(600)
                .cron("0 0 */2 * * ?").build());
        configs.add(DataSourceConfig.builder()
                .code("xiaohongshu").name("小红书").type("social_media")
                .priority(2).enabled(true).maxConcurrent(2).maxRequestsPerHour(800)
                .cron("0 30 */1 * * ?").build());
        configs.add(DataSourceConfig.builder()
                .code("douyin").name("抖音").type("short_video")
                .priority(1).enabled(true).maxConcurrent(3).maxRequestsPerHour(1200)
                .cron("0 15 */1 * * ?").build());
        configs.add(DataSourceConfig.builder()
                .code("bilibili").name("B站").type("short_video")
                .priority(2).enabled(true).maxConcurrent(3).maxRequestsPerHour(1500)
                .cron("0 0 */1 * * ?").build());
        configs.add(DataSourceConfig.builder()
                .code("local_forum").name("本地论坛").type("bbs")
                .priority(4).enabled(true).maxConcurrent(2).maxRequestsPerHour(500)
                .cron("0 0 */3 * * ?").build());
        configs.add(DataSourceConfig.builder()
                .code("government").name("政务平台").type("government")
                .priority(3).enabled(true).maxConcurrent(2).maxRequestsPerHour(400)
                .cron("0 0 8,12,18 * * ?").build());
        configs.add(DataSourceConfig.builder()
                .code("baidu").name("百度").type("news")
                .priority(1).enabled(true).maxConcurrent(3).maxRequestsPerHour(1500)
                .cron("0 */30 * * * ?").build());
        configs.add(DataSourceConfig.builder()
                .code("zhihu").name("知乎").type("social_media")
                .priority(2).enabled(true).maxConcurrent(2).maxRequestsPerHour(1000)
                .cron("0 */30 * * * ?").build());
        configs.add(DataSourceConfig.builder()
                .code("weibo").name("微博").type("social_media")
                .priority(1).enabled(true).maxConcurrent(3).maxRequestsPerHour(1500)
                .cron("0 */15 * * * ?").build());
        return configs;
    }

    public void registerAdapter(PlatformAdapter adapter) {
        if (adapter == null) return;
        String code = adapter.getPlatformCode();
        adapterRegistry.put(code, adapter);
        log.info("注册平台适配器: {} ({})", adapter.getPlatformName(), code);
    }

    public void registerConfig(DataSourceConfig config) {
        if (config == null) return;
        configRegistry.put(config.getCode(), config);
    }

    public PlatformAdapter getAdapter(String code) {
        return adapterRegistry.get(code);
    }

    public DataSourceConfig getConfig(String code) {
        return configRegistry.get(code);
    }

    public List<PlatformAdapter> getAllAdapters() {
        return new ArrayList<>(adapterRegistry.values());
    }

    public List<PlatformAdapter> getEnabledAdapters() {
        List<PlatformAdapter> result = new ArrayList<>();
        for (String code : enabledSources) {
            PlatformAdapter adapter = adapterRegistry.get(code);
            if (adapter != null && adapter.isEnabled()) {
                result.add(adapter);
            }
        }
        return result;
    }

    public List<DataSourceConfig> getAllConfigs() {
        return new ArrayList<>(configRegistry.values());
    }

    public List<DataSourceConfig> getEnabledConfigs() {
        List<DataSourceConfig> result = new ArrayList<>();
        for (DataSourceConfig cfg : configRegistry.values()) {
            if (cfg.isEnabled() && enabledSources.contains(cfg.getCode())) {
                result.add(cfg);
            }
        }
        result.sort(Comparator.comparingInt(DataSourceConfig::getPriority));
        return result;
    }

    public void enableSource(String code) {
        if (adapterRegistry.containsKey(code)) {
            enabledSources.add(code);
            DataSourceConfig cfg = configRegistry.get(code);
            if (cfg != null) cfg.setEnabled(true);
            log.info("启用数据源: {}", code);
        }
    }

    public void disableSource(String code) {
        enabledSources.remove(code);
        DataSourceConfig cfg = configRegistry.get(code);
        if (cfg != null) cfg.setEnabled(false);
        log.info("禁用数据源: {}", code);
    }

    public boolean isSourceEnabled(String code) {
        return enabledSources.contains(code);
    }

    public Map<String, Object> getSourceStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> sources = new ArrayList<>();
        for (PlatformAdapter adapter : getAllAdapters()) {
            Map<String, Object> info = new LinkedHashMap<>();
            String code = adapter.getPlatformCode();
            DataSourceConfig cfg = configRegistry.get(code);
            info.put("code", code);
            info.put("name", adapter.getPlatformName());
            info.put("type", adapter.getPlatformType() != null ? adapter.getPlatformType().name() : "UNKNOWN");
            info.put("baseUrl", adapter.getBaseUrl());
            info.put("enabled", isSourceEnabled(code) && adapter.isEnabled());
            info.put("needsLogin", adapter.needsLogin());
            info.put("loggedIn", adapter.isLoggedIn());
            info.put("priority", cfg != null ? cfg.getPriority() : 999);
            info.put("maxConcurrent", cfg != null ? cfg.getMaxConcurrent() : 1);
            info.put("maxRequestsPerHour", cfg != null ? cfg.getMaxRequestsPerHour() : 0);
            info.put("cron", cfg != null ? cfg.getCron() : "");
            sources.add(info);
        }
        result.put("totalSources", sources.size());
        result.put("enabledSources", (int) sources.stream()
                .filter(s -> Boolean.TRUE.equals(s.get("enabled"))).count());
        result.put("sources", sources);
        return result;
    }

    public boolean loginPlatform(String code, Map<String, String> credentials) throws Exception {
        PlatformAdapter adapter = getAdapter(code);
        if (adapter == null) throw new IllegalArgumentException("未找到数据源: " + code);
        return adapter.login(credentials);
    }

    public void logoutPlatform(String code) throws Exception {
        PlatformAdapter adapter = getAdapter(code);
        if (adapter == null) throw new IllegalArgumentException("未找到数据源: " + code);
        adapter.logout();
    }
}
