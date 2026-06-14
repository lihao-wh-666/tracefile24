package com.hotevent.crawler.core;

import com.hotevent.crawler.http.HttpMethod;
import com.hotevent.crawler.http.RetryStrategy;
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
public class CrawlRequest {

    public enum RequestType {
        LIST, DETAIL, SEARCH, LOGIN, VERIFY
    }

    @Builder.Default
    private RequestType requestType = RequestType.LIST;

    private String platform;
    private String taskId;
    private String requestId;
    private String url;

    @Builder.Default
    private HttpMethod method = HttpMethod.GET;

    @Builder.Default
    private Map<String, String> headers = new HashMap<>();

    @Builder.Default
    private Map<String, String> queryParams = new HashMap<>();

    @Builder.Default
    private Map<String, Object> formParams = new HashMap<>();

    private String body;
    private String contentType;
    private String keyword;
    private String category;
    private int page;
    private int pageSize;
    private String cursor;
    private int maxDepth;
    private int currentDepth;
    private int priority;
    private boolean needLogin;
    private boolean needRender;
    private boolean useProxy;
    private long requestIntervalMs;
    private RetryStrategy retryStrategy;

    @Builder.Default
    private Map<String, Object> context = new HashMap<>();

    public CrawlRequest header(String name, String value) {
        this.headers.put(name, value);
        return this;
    }

    public CrawlRequest queryParam(String name, String value) {
        this.queryParams.put(name, value);
        return this;
    }

    public CrawlRequest formParam(String name, Object value) {
        this.formParams.put(name, value);
        return this;
    }

    public CrawlRequest context(String key, Object value) {
        this.context.put(key, value);
        return this;
    }

    public Object getContext(String key) {
        return this.context.get(key);
    }
}
