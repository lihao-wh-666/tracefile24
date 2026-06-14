package com.hotevent.crawler.core;

import com.hotevent.crawler.http.HttpMethod;
import com.hotevent.crawler.http.RetryStrategy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrawlRequest {

    public enum RequestType {
        LIST, DETAIL, SEARCH, LOGIN, VERIFY
    }

    private RequestType requestType = RequestType.LIST;
    private String platform;
    private String taskId;
    private String requestId;
    private String url;
    private HttpMethod method = HttpMethod.GET;
    private Map<String, String> headers = new HashMap<>();
    private Map<String, String> queryParams = new HashMap<>();
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

    public static CrawlRequestBuilder builder() {
        return new CrawlRequestBuilder();
    }

    @Setter
    @Accessors(fluent = true, chain = true)
    public static class CrawlRequestBuilder {
        private RequestType requestType = RequestType.LIST;
        private String platform;
        private String taskId;
        private String requestId;
        private String url;
        private HttpMethod method = HttpMethod.GET;
        private Map<String, String> headers = new HashMap<>();
        private Map<String, String> queryParams = new HashMap<>();
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
        private Map<String, Object> context = new HashMap<>();

        public CrawlRequestBuilder header(String name, String value) {
            this.headers.put(name, value);
            return this;
        }

        public CrawlRequestBuilder queryParam(String name, String value) {
            this.queryParams.put(name, value);
            return this;
        }

        public CrawlRequestBuilder formParam(String name, Object value) {
            this.formParams.put(name, value);
            return this;
        }

        public CrawlRequestBuilder context(String key, Object value) {
            this.context.put(key, value);
            return this;
        }

        public CrawlRequest build() {
            CrawlRequest req = new CrawlRequest();
            req.requestType = this.requestType;
            req.platform = this.platform;
            req.taskId = this.taskId;
            req.requestId = this.requestId;
            req.url = this.url;
            req.method = this.method;
            req.headers = this.headers;
            req.queryParams = this.queryParams;
            req.formParams = this.formParams;
            req.body = this.body;
            req.contentType = this.contentType;
            req.keyword = this.keyword;
            req.category = this.category;
            req.page = this.page;
            req.pageSize = this.pageSize;
            req.cursor = this.cursor;
            req.maxDepth = this.maxDepth;
            req.currentDepth = this.currentDepth;
            req.priority = this.priority;
            req.needLogin = this.needLogin;
            req.needRender = this.needRender;
            req.useProxy = this.useProxy;
            req.requestIntervalMs = this.requestIntervalMs;
            req.retryStrategy = this.retryStrategy;
            req.context = this.context;
            return req;
        }
    }
}
