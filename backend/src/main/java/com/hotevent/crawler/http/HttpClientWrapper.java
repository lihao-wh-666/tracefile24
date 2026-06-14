package com.hotevent.crawler.http;

import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class HttpClientWrapper {

    @Autowired
    private CookieManager cookieManager;

    @Autowired
    private ProxyPoolManager proxyPoolManager;

    @Autowired
    private RateLimitManager rateLimitManager;

    @Value("${hot-event.crawler.timeout:30000}")
    private int defaultTimeout;

    @Value("${hot-event.crawler.connect-timeout:10000}")
    private int defaultConnectTimeout;

    @Value("${hot-event.crawler.user-agents:}")
    private List<String> userAgents;

    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private volatile OkHttpClient httpClient;

    private OkHttpClient getOrCreateClient(HttpRequestWrapper request) {
        if (httpClient == null) {
            synchronized (this) {
                if (httpClient == null) {
                    httpClient = buildBaseClient(request);
                }
            }
        }
        OkHttpClient.Builder builder = httpClient.newBuilder();
        configureTimeouts(builder, request);
        configureProxy(builder, request);
        return builder.build();
    }

    private OkHttpClient buildBaseClient(HttpRequestWrapper request) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            X509TrustManager trustAllCerts = new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            };
            sslContext.init(null, new javax.net.ssl.TrustManager[]{trustAllCerts}, new java.security.SecureRandom());
            builder.sslSocketFactory(sslContext.getSocketFactory(), trustAllCerts);
            builder.hostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            log.warn("配置SSL信任管理器失败，使用默认配置: {}", e.getMessage());
        }
        if (request != null && request.isUseCookies()) {
            builder.cookieJar(cookieManager);
        }
        builder.followRedirects(request != null && request.isFollowRedirects());
        builder.followSslRedirects(true);
        if (request != null && request.getMaxRedirects() > 0) {
            Dispatcher dispatcher = new Dispatcher();
            dispatcher.setMaxRequestsPerHost(request.getMaxRedirects());
            builder.dispatcher(dispatcher);
        }
        builder.connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES));
        return builder.build();
    }

    private void configureTimeouts(OkHttpClient.Builder builder, HttpRequestWrapper request) {
        int connectTimeout = request.getConnectTimeoutMs() > 0 ? request.getConnectTimeoutMs() : defaultConnectTimeout;
        int readTimeout = request.getReadTimeoutMs() > 0 ? request.getReadTimeoutMs() : defaultTimeout;
        int writeTimeout = request.getWriteTimeoutMs() > 0 ? request.getWriteTimeoutMs() : defaultTimeout;
        builder.connectTimeout(connectTimeout, TimeUnit.MILLISECONDS);
        builder.readTimeout(readTimeout, TimeUnit.MILLISECONDS);
        builder.writeTimeout(writeTimeout, TimeUnit.MILLISECONDS);
    }

    private void configureProxy(OkHttpClient.Builder builder, HttpRequestWrapper request) {
        if (!request.isUseProxy() || !proxyPoolManager.isProxyEnabled()) {
            return;
        }
        ProxyInfo proxyInfo = proxyPoolManager.getProxy();
        if (proxyInfo == null) {
            return;
        }
        request.setUsedProxy(proxyInfo);
        Proxy.Type proxyType = switch (proxyInfo.getType()) {
            case SOCKS4, SOCKS5 -> Proxy.Type.SOCKS;
            default -> Proxy.Type.HTTP;
        };
        Proxy proxy = new Proxy(proxyType, proxyInfo.toSocketAddress());
        builder.proxy(proxy);
        if (proxyInfo.hasAuth()) {
            builder.proxyAuthenticator((route, response) -> {
                String credential = Credentials.basic(proxyInfo.getUsername(), proxyInfo.getPassword());
                return response.request().newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build();
            });
        }
    }

    public HttpResponseWrapper execute(HttpRequestWrapper request) throws Exception {
        String domain = request.extractDomain();
        rateLimitManager.acquire(domain);

        RetryStrategy retryStrategy = request.getRetryStrategy() != null
                ? request.getRetryStrategy()
                : RetryStrategy.builder().build();

        Exception lastException = null;
        Integer lastStatusCode = null;
        int attempts = 0;

        for (int attempt = 0; attempt < retryStrategy.getMaxAttempts(); attempt++) {
            attempts = attempt + 1;
            long startTime = System.currentTimeMillis();
            try {
                if (attempt > 0) {
                    retryStrategy.waitBeforeNextRetry(attempt);
                }

                Request okRequest = buildOkHttpRequest(request);
                OkHttpClient client = getOrCreateClient(request);

                log.debug("[HTTP] {} {} (第{}次请求)", request.getMethod(), request.getUrl(), attempts);

                try (Response response = client.newCall(okRequest).execute()) {
                    long responseTime = System.currentTimeMillis() - startTime;
                    HttpResponseWrapper wrapper = parseResponse(response, request, startTime);
                    wrapper.setRetryAttempts(attempt);
                    wrapper.setUsedProxy(request.getUsedProxy());
                    wrapper.setResponseTimeMs(responseTime);
                    lastStatusCode = wrapper.getStatusCode();

                    if (wrapper.isOk()) {
                        if (request.getUsedProxy() != null) {
                            proxyPoolManager.recordSuccess(request.getUsedProxy());
                        }
                        log.debug("[HTTP] 请求成功 {} {}ms 状态:{}", request.getUrl(), responseTime, wrapper.getStatusCode());
                        return wrapper;
                    }

                    if (!retryStrategy.shouldRetry(attempt + 1, null, wrapper.getStatusCode())) {
                        log.warn("[HTTP] 请求完成但状态异常，不重试: {} 状态:{}", request.getUrl(), wrapper.getStatusCode());
                        return wrapper;
                    }

                    lastException = new RuntimeException("HTTP状态码异常: " + wrapper.getStatusCode());
                    log.warn("[HTTP] 重试中... 状态码:{} URL:{}", wrapper.getStatusCode(), request.getUrl());
                }

            } catch (Exception e) {
                lastException = e;
                long responseTime = System.currentTimeMillis() - startTime;
                log.warn("[HTTP] 请求异常 (第{}次): {} - {}", attempts, e.getClass().getSimpleName(),
                        e.getMessage() != null && e.getMessage().length() > 100
                                ? e.getMessage().substring(0, 100) + "..."
                                : e.getMessage());

                if (request.getUsedProxy() != null) {
                    proxyPoolManager.recordFailure(request.getUsedProxy());
                }

                if (!retryStrategy.shouldRetry(attempt + 1, e, null)) {
                    HttpResponseWrapper errorResp = HttpResponseWrapper.error(e, request.getUrl());
                    errorResp.setResponseTimeMs(responseTime);
                    errorResp.setRetryAttempts(attempts);
                    return errorResp;
                }
            }
        }

        String msg = String.format("请求失败，已重试%d次。最后错误: %s",
                attempts,
                lastException != null ? lastException.getMessage() : "未知错误");
        log.error("[HTTP] {}", msg);
        HttpResponseWrapper finalResp = HttpResponseWrapper.error(lastException != null ? lastException : new RuntimeException(msg), request.getUrl());
        finalResp.setRetryAttempts(attempts);
        if (lastStatusCode != null) {
            finalResp.setStatusCode(lastStatusCode);
        }
        return finalResp;
    }

    private Request buildOkHttpRequest(HttpRequestWrapper request) {
        String finalUrl = buildUrlWithParams(request);
        Request.Builder builder = new Request.Builder().url(finalUrl);

        addDefaultHeaders(builder, request);
        addCustomHeaders(builder, request);

        RequestBody body = buildRequestBody(request);

        switch (request.getMethod()) {
            case GET -> builder.get();
            case POST -> builder.post(body);
            case PUT -> builder.put(body);
            case DELETE -> builder.delete(body);
            case PATCH -> builder.patch(body);
            case HEAD -> builder.head();
            case OPTIONS -> builder.method("OPTIONS", body);
            default -> builder.get();
        }
        return builder.build();
    }

    private String buildUrlWithParams(HttpRequestWrapper request) {
        if (request.getQueryParams() == null || request.getQueryParams().isEmpty()) {
            return request.getUrl();
        }
        HttpUrl httpUrl = HttpUrl.parse(request.getUrl());
        if (httpUrl == null) {
            return request.getUrl();
        }
        HttpUrl.Builder urlBuilder = httpUrl.newBuilder();
        for (Map.Entry<String, String> entry : request.getQueryParams().entrySet()) {
            if (entry.getValue() != null) {
                urlBuilder.addQueryParameter(entry.getKey(), entry.getValue());
            }
        }
        return urlBuilder.build().toString();
    }

    private void addDefaultHeaders(Request.Builder builder, HttpRequestWrapper request) {
        String ua = request.getUserAgent();
        if (ua == null || ua.isEmpty()) {
            ua = getRandomUserAgent();
        }
        builder.header("User-Agent", ua);
        builder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
        builder.header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        builder.header("Accept-Encoding", "gzip, deflate, br");
        builder.header("Connection", "keep-alive");
        builder.header("Cache-Control", "no-cache");
        builder.header("Pragma", "no-cache");
        if (request.getReferer() != null && !request.getReferer().isEmpty()) {
            builder.header("Referer", request.getReferer());
        }
    }

    private void addCustomHeaders(Request.Builder builder, HttpRequestWrapper request) {
        if (request.getHeaders() == null) return;
        for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
            if (entry.getValue() != null) {
                builder.header(entry.getKey(), entry.getValue());
            }
        }
    }

    private RequestBody buildRequestBody(HttpRequestWrapper request) {
        if (request.getMethod() == HttpMethod.GET || request.getMethod() == HttpMethod.HEAD) {
            return null;
        }
        MediaType mediaType = MediaType.parse(request.getContentType() + "; charset=" + request.getCharset());

        if (request.getBody() != null && !request.getBody().isEmpty()) {
            return RequestBody.create(request.getBody(), mediaType);
        }

        if (request.getFormParams() != null && !request.getFormParams().isEmpty()) {
            FormBody.Builder formBuilder = new FormBody.Builder(request.getCharset());
            for (Map.Entry<String, Object> entry : request.getFormParams().entrySet()) {
                Object value = entry.getValue();
                if (value != null) {
                    formBuilder.add(entry.getKey(), String.valueOf(value));
                }
            }
            return formBuilder.build();
        }

        return RequestBody.create(new byte[0], null);
    }

    private HttpResponseWrapper parseResponse(Response response, HttpRequestWrapper request, long startTime) throws Exception {
        HttpResponseWrapper.HttpResponseWrapperBuilder builder = HttpResponseWrapper.builder();
        int statusCode = response.code();
        builder.statusCode(statusCode)
                .message(response.message())
                .requestUrl(request.getUrl())
                .finalUrl(response.request().url().toString())
                .isSuccessful(response.isSuccessful())
                .isRedirect(response.isRedirect())
                .isClientError(statusCode >= 400 && statusCode < 500)
                .isServerError(statusCode >= 500 && statusCode < 600);

        Map<String, String> headers = new HashMap<>();
        for (int i = 0, size = response.headers().size(); i < size; i++) {
            headers.put(response.headers().name(i), response.headers().value(i));
        }
        builder.headers(headers);

        String contentType = response.header("Content-Type");
        builder.contentType(contentType);

        ResponseBody body = response.body();
        if (body != null) {
            byte[] rawBytes = body.bytes();
            builder.rawBody(rawBytes);
            builder.contentLength(rawBytes.length);

            String charset = request.getCharset();
            if (contentType != null && contentType.contains("charset=")) {
                int idx = contentType.indexOf("charset=");
                if (idx > 0) {
                    String cs = contentType.substring(idx + 8).trim();
                    if (!cs.isEmpty()) charset = cs.split(";")[0].trim();
                }
            }
            builder.charset(charset);
            try {
                builder.body(new String(rawBytes, charset));
            } catch (Exception e) {
                builder.body(new String(rawBytes));
            }
        }

        int redirects = response.priorResponse() != null ? countRedirects(response) + 1 : 0;
        builder.redirectCount(redirects);

        return builder.build();
    }

    private int countRedirects(Response response) {
        int count = 0;
        Response prior = response.priorResponse();
        while (prior != null) {
            count++;
            prior = prior.priorResponse();
        }
        return count;
    }

    private String getRandomUserAgent() {
        if (userAgents == null || userAgents.isEmpty()) {
            return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";
        }
        return userAgents.get(random.nextInt(userAgents.size()));
    }

    public HttpResponseWrapper get(String url) throws Exception {
        return execute(HttpRequestWrapper.builder().url(url).method(HttpMethod.GET).build());
    }

    public HttpResponseWrapper post(String url, String body, String contentType) throws Exception {
        return execute(HttpRequestWrapper.builder()
                .url(url)
                .method(HttpMethod.POST)
                .body(body)
                .contentType(contentType)
                .build());
    }

    public HttpResponseWrapper postForm(String url, Map<String, Object> formParams) throws Exception {
        return execute(HttpRequestWrapper.builder()
                .url(url)
                .method(HttpMethod.POST)
                .formParams(formParams)
                .contentType("application/x-www-form-urlencoded")
                .build());
    }
}
