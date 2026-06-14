package com.hotevent.crawler.datasource;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceConfig {

    private String code;
    private String name;
    private String type;
    private String description;
    private String baseUrl;

    @Builder.Default
    private int priority = 5;

    @Builder.Default
    private boolean enabled = true;

    @Builder.Default
    private int maxConcurrent = 2;

    @Builder.Default
    private int maxRequestsPerHour = 600;

    @Builder.Default
    private int maxItemsPerCrawl = 100;

    @Builder.Default
    private int maxDepth = 2;

    private String cron;

    @Builder.Default
    private boolean incrementalEnabled = true;

    @Builder.Default
    private long incrementalCheckIntervalMs = 5 * 60 * 1000L;

    @Builder.Default
    private Map<String, Object> parameters = new HashMap<>();

    public Object getParam(String key) {
        return parameters != null ? parameters.get(key) : null;
    }

    public void setParam(String key, Object value) {
        if (parameters == null) parameters = new HashMap<>();
        parameters.put(key, value);
    }
}
