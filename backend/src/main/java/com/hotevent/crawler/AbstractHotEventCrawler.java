package com.hotevent.crawler;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hotevent.entity.HotEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
public abstract class AbstractHotEventCrawler implements HotEventCrawler {

    @Value("${hot-event.crawler.user-agents:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36}")
    protected List<String> userAgents;

    @Value("${hot-event.crawler.timeout:15000}")
    protected int timeout;

    @Value("${hot-event.crawler.connect-timeout:10000}")
    protected int connectTimeout;

    @Value("${hot-event.crawler.retry.max-attempts:3}")
    protected int maxRetryAttempts;

    @Value("${hot-event.crawler.retry.delay-ms:2000}")
    protected long retryDelayMs;

    @Value("${hot-event.crawler.retry.multiplier:2.0}")
    protected double retryMultiplier;

    @Value("${hot-event.crawler.request-interval-ms:500}")
    protected long requestIntervalMs;

    @Value("${hot-event.crawler.validation.require-title:true}")
    protected boolean requireTitle;

    @Value("${hot-event.crawler.validation.min-title-length:2}")
    protected int minTitleLength;

    @Value("${hot-event.crawler.validation.require-hot-value:false}")
    protected boolean requireHotValue;

    @Value("${hot-event.crawler.validation.min-event-count:5}")
    protected int minEventCount;

    protected ThreadLocalRandom random = ThreadLocalRandom.current();

    protected abstract List<DataEndpoint> getEndpoints();

    protected abstract List<HotEvent> parseJsonResponse(JSONObject json, int endpointIndex);

    protected static class DataEndpoint {
        public final String url;
        public final String referer;
        public final String contentType;
        public final boolean isJson;
        public final String description;

        public DataEndpoint(String url, String referer, String contentType, boolean isJson, String description) {
            this.url = url;
            this.referer = referer;
            this.contentType = contentType;
            this.isJson = isJson;
            this.description = description;
        }
    }

    protected String getRandomUserAgent() {
        if (userAgents == null || userAgents.isEmpty()) {
            return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
        }
        return userAgents.get(random.nextInt(userAgents.size()));
    }

    protected void sleepBeforeRequest() {
        if (requestIntervalMs > 0) {
            try {
                long jitter = random.nextLong(requestIntervalMs / 2);
                long sleepTime = requestIntervalMs + jitter;
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    protected RawResponse fetchWithFallback() throws Exception {
        List<DataEndpoint> endpoints = getEndpoints();
        Exception lastException = null;

        for (int epIdx = 0; epIdx < endpoints.size(); epIdx++) {
            DataEndpoint ep = endpoints.get(epIdx);
            log.info("[{}] 尝试数据源 #{} [{}]: {}", getSourceName(), epIdx + 1, ep.description, ep.url);

            try {
                String responseBody = fetchEndpointWithRetry(ep, epIdx);
                log.info("[{}] 数据源 #{} [{}] 请求成功", getSourceName(), epIdx + 1, ep.description);
                return new RawResponse(responseBody, epIdx, ep);
            } catch (Exception e) {
                lastException = e;
                log.warn("[{}] 数据源 #{} [{}] 请求失败: {}", 
                        getSourceName(), epIdx + 1, ep.description, 
                        e.getMessage() != null && e.getMessage().length() > 150 
                                ? e.getMessage().substring(0, 150) + "..." 
                                : e.getMessage());
                if (epIdx < endpoints.size() - 1) {
                    long wait = 1000L + random.nextLong(500);
                    log.info("[{}] 等待 {}ms 后切换到下一数据源", getSourceName(), wait);
                    Thread.sleep(wait);
                }
            }
        }

        throw new CrawlerException(
                "所有数据源均请求失败，最后错误: " + (lastException != null ? lastException.getMessage() : "未知错误"),
                lastException, getSourceName(), CrawlerException.ErrorType.NETWORK_ERROR);
    }

    protected String fetchEndpointWithRetry(DataEndpoint endpoint, int epIdx) throws Exception {
        Exception lastException = null;
        long currentDelay = retryDelayMs;

        for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
            try {
                sleepBeforeRequest();
                HttpResponse response = buildRequest(endpoint).execute();

                if (!response.isOk()) {
                    int status = response.getStatus();
                    if (status == 403) {
                        throw new CrawlerException("HTTP 403 禁止访问，可能被反爬机制拦截",
                                getSourceName(), CrawlerException.ErrorType.ACCESS_DENIED);
                    } else if (status == 404) {
                        throw new CrawlerException("HTTP 404 URL不存在: " + endpoint.url,
                                getSourceName(), CrawlerException.ErrorType.NOT_FOUND);
                    } else if (status == 429) {
                        throw new CrawlerException("HTTP 429 请求过于频繁，触发限流",
                                getSourceName(), CrawlerException.ErrorType.RATE_LIMITED);
                    } else if (status >= 500) {
                        throw new CrawlerException("HTTP " + status + " 服务器错误",
                                getSourceName(), CrawlerException.ErrorType.SERVER_ERROR);
                    } else {
                        throw new RuntimeException("HTTP请求失败，状态码：" + status);
                    }
                }

                return response.body();

            } catch (Exception e) {
                lastException = e;
                String simpleName = e.getClass().getSimpleName();
                String causeName = e.getCause() != null ? e.getCause().getClass().getSimpleName() : "";
                log.warn("[{}] 数据源#{} 请求异常 (第{}次尝试): {} -> {}: {}", 
                        getSourceName(), epIdx + 1, attempt,
                        simpleName, causeName,
                        e.getMessage() != null && e.getMessage().length() > 200
                                ? e.getMessage().substring(0, 200) + "..."
                                : e.getMessage());
            }

            if (attempt < maxRetryAttempts) {
                try {
                    long jitter = random.nextLong(currentDelay / 2);
                    long waitTime = currentDelay + jitter;
                    Thread.sleep(waitTime);
                    currentDelay = (long) (currentDelay * retryMultiplier);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        if (lastException instanceof CrawlerException) {
            throw lastException;
        }
        throw new CrawlerException(
                "数据源请求失败（重试" + maxRetryAttempts + "次）: " + 
                (lastException != null ? lastException.getMessage() : "未知错误"),
                lastException, getSourceName(), CrawlerException.ErrorType.NETWORK_ERROR);
    }

    protected HttpRequest buildRequest(DataEndpoint ep) {
        HttpRequest req = HttpRequest.get(ep.url)
                .header("User-Agent", getRandomUserAgent())
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Accept-Encoding", "gzip, deflate")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .header("Connection", "keep-alive")
                .setConnectionTimeout(connectTimeout)
                .setReadTimeout(timeout);

        if (ep.referer != null && !ep.referer.isEmpty()) {
            req.header("Referer", ep.referer);
            req.header("Origin", ep.referer.endsWith("/") 
                    ? ep.referer.substring(0, ep.referer.length() - 1) 
                    : ep.referer);
        }

        if (ep.isJson) {
            req.header("Accept", "application/json, text/plain, */*");
        }

        return req;
    }

    protected JSONObject parseJsonObject(String body) throws CrawlerException {
        if (body == null || body.isEmpty()) {
            throw new CrawlerException("响应内容为空", getSourceName(), CrawlerException.ErrorType.PARSE_ERROR);
        }
        try {
            return JSONUtil.parseObj(body);
        } catch (Exception e) {
            throw new CrawlerException("JSON解析失败: " + e.getMessage() + 
                    "，响应前200字符: " + (body.length() > 200 ? body.substring(0, 200) : body),
                    e, getSourceName(), CrawlerException.ErrorType.PARSE_ERROR);
        }
    }

    protected HotEvent createHotEvent(String title, String url, Long hotValue, Integer rank) {
        HotEvent event = new HotEvent();
        event.setTitle(title);
        event.setSourceUrl(url);
        event.setSource(getSourceName());
        event.setHotValue(hotValue != null ? hotValue : 0L);
        event.setHotRank(rank);
        event.setIsHot(true);
        event.setIsRising(calculateIsRising(hotValue, rank));
        event.setRisingRate(calculateRisingRate(hotValue, rank));
        event.setCrawlTime(LocalDateTime.now());
        return event;
    }

    protected boolean calculateIsRising(Long hotValue, Integer rank) {
        if (rank != null && rank <= 10) return true;
        if (hotValue != null && hotValue > 500000) return true;
        return false;
    }

    protected double calculateRisingRate(Long hotValue, Integer rank) {
        if (hotValue == null || hotValue <= 0 || rank == null) return 0.0;
        double baseRate = (hotValue / 1000000.0) * 50;
        double rankFactor = Math.max(0, (50 - rank) / 50.0) * 50;
        double jitter = random.nextDouble() * 10;
        return Math.round((baseRate + rankFactor + jitter) * 100.0) / 100.0;
    }

    protected List<HotEvent> validateEvents(List<HotEvent> events) {
        if (events == null || events.isEmpty()) {
            throw new CrawlerException("解析结果为空，未获取到任何热点事件",
                    getSourceName(), CrawlerException.ErrorType.PARSE_ERROR);
        }

        List<HotEvent> validEvents = new ArrayList<>();
        int invalidCount = 0;

        for (HotEvent event : events) {
            String title = event.getTitle();
            if (requireTitle && (title == null || title.trim().length() < minTitleLength)) {
                invalidCount++;
                continue;
            }
            if (requireHotValue && (event.getHotValue() == null || event.getHotValue() <= 0)) {
                invalidCount++;
                continue;
            }
            if (event.getSource() == null || event.getSource().isEmpty()) {
                event.setSource(getSourceName());
            }
            if (event.getCrawlTime() == null) {
                event.setCrawlTime(LocalDateTime.now());
            }
            if (event.getIsHot() == null) {
                event.setIsHot(true);
            }
            validEvents.add(event);
        }

        log.info("[{}] 数据验证：原始{}条，有效{}条，无效{}条",
                getSourceName(), events.size(), validEvents.size(), invalidCount);

        if (validEvents.size() < minEventCount) {
            log.warn("[{}] 有效事件数({})低于最小要求({})",
                    getSourceName(), validEvents.size(), minEventCount);
        }

        return validEvents;
    }

    protected List<HotEvent> executeCrawl() throws Exception {
        String source = getSourceName();
        log.info("========== [{}] 开始抓取 ==========", source);
        long startTime = System.currentTimeMillis();

        try {
            RawResponse raw = fetchWithFallback();
            log.debug("[{}] 响应长度: {}字符", source, raw.body.length());

            JSONObject json = parseJsonObject(raw.body);
            List<HotEvent> rawEvents = parseJsonResponse(json, raw.endpointIndex);
            log.info("[{}] 原始解析: {}条", source, rawEvents != null ? rawEvents.size() : 0);

            List<HotEvent> validEvents = validateEvents(rawEvents);
            long cost = System.currentTimeMillis() - startTime;
            log.info("[{}] 抓取成功！{}条有效，耗时{}ms [数据源: {}]",
                    source, validEvents.size(), cost, raw.endpoint.description);
            log.info("========== [{}] 完成 ==========", source);
            return validEvents;

        } catch (CrawlerException e) {
            long cost = System.currentTimeMillis() - startTime;
            log.error("[{}] 抓取失败！耗时{}ms，类型: {}, 消息: {}",
                    source, cost, e.getErrorType(), e.getMessage());
            log.info("========== [{}] 失败 ==========", source);
            throw e;
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - startTime;
            log.error("[{}] 抓取异常！耗时{}ms", source, cost, e);
            log.info("========== [{}] 异常 ==========", source);
            throw new CrawlerException("未预期异常: " + e.getMessage(),
                    e, source, CrawlerException.ErrorType.UNKNOWN);
        }
    }

    protected static class RawResponse {
        public final String body;
        public final int endpointIndex;
        public final DataEndpoint endpoint;

        public RawResponse(String body, int endpointIndex, DataEndpoint endpoint) {
            this.body = body;
            this.endpointIndex = endpointIndex;
            this.endpoint = endpoint;
        }
    }

    public static class CrawlerException extends RuntimeException {
        public enum ErrorType {
            NETWORK_ERROR, ACCESS_DENIED, NOT_FOUND, RATE_LIMITED,
            SERVER_ERROR, PARSE_ERROR, VALIDATION_ERROR, UNKNOWN
        }
        private final String sourceName;
        private final ErrorType errorType;

        public CrawlerException(String message, String sourceName, ErrorType errorType) {
            super(message);
            this.sourceName = sourceName;
            this.errorType = errorType;
        }
        public CrawlerException(String message, Throwable cause, String sourceName, ErrorType errorType) {
            super(message, cause);
            this.sourceName = sourceName;
            this.errorType = errorType;
        }
        public String getSourceName() { return sourceName; }
        public ErrorType getErrorType() { return errorType; }
    }
}
