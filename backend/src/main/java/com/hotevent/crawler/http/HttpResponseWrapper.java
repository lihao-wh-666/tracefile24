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
public class HttpResponseWrapper {

    private int statusCode;

    private String message;

    private String body;

    private byte[] rawBody;

    @Builder.Default
    private Map<String, String> headers = new HashMap<>();

    @Builder.Default
    private Map<String, String> cookies = new HashMap<>();

    private String contentType;

    private String charset;

    private long contentLength;

    private String requestUrl;

    private String finalUrl;

    private int redirectCount;

    private long responseTimeMs;

    private boolean isFromCache;

    private boolean isSuccessful;

    private boolean isRedirect;

    private boolean isClientError;

    private boolean isServerError;

    private int retryAttempts;

    private ProxyInfo usedProxy;

    private Exception error;

    public boolean isOk() {
        return statusCode >= 200 && statusCode < 300;
    }

    public String getHeader(String name) {
        if (headers == null) return null;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public static HttpResponseWrapper error(Exception e, String url) {
        return HttpResponseWrapper.builder()
                .statusCode(0)
                .message("REQUEST_ERROR")
                .requestUrl(url)
                .error(e)
                .isSuccessful(false)
                .build();
    }
}
