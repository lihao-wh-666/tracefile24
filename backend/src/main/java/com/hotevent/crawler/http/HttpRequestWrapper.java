package com.hotevent.crawler.http;

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
public class HttpRequestWrapper {

    @Builder.Default
    private HttpMethod method = HttpMethod.GET;

    private String url;

    @Builder.Default
    private Map<String, String> headers = new HashMap<>();

    @Builder.Default
    private Map<String, String> queryParams = new HashMap<>();

    @Builder.Default
    private Map<String, Object> formParams = new HashMap<>();

    private String body;

    @Builder.Default
    private String contentType = "application/json";

    @Builder.Default
    private int connectTimeoutMs = 10000;

    @Builder.Default
    private int readTimeoutMs = 30000;

    @Builder.Default
    private int writeTimeoutMs = 30000;

    @Builder.Default
    private boolean followRedirects = true;

    @Builder.Default
    private int maxRedirects = 10;

    @Builder.Default
    private boolean useCookies = true;

    @Builder.Default
    private boolean useProxy = true;

    private String userAgent;

    private String referer;

    private RetryStrategy retryStrategy;

    @Builder.Default
    private String platform = "default";

    @Builder.Default
    private String charset = "UTF-8";

    public HttpRequestWrapper header(String name, String value) {
        this.headers.put(name, value);
        return this;
    }

    public HttpRequestWrapper queryParam(String name, String value) {
        this.queryParams.put(name, value);
        return this;
    }

    public HttpRequestWrapper formParam(String name, Object value) {
        this.formParams.put(name, value);
        return this;
    }

    public String extractDomain() {
        try {
            java.net.URI uri = new java.net.URI(url);
            return uri.getHost();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
