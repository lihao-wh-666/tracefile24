package com.hotevent.crawler;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.hotevent.entity.HotEvent;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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

    @Value("${hot-event.crawler.fallback.enabled:false}")
    protected boolean fallbackEnabled;

    protected ThreadLocalRandom random = ThreadLocalRandom.current();

    protected abstract String getCrawlUrl();

    protected abstract List<HotEvent> parseHtml(String html);

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
                log.debug("[{}] 请求前等待 {}ms", getSourceName(), sleepTime);
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[{}] 请求等待被中断", getSourceName());
            }
        }
    }

    protected String fetchHtmlWithRetry(String url) throws Exception {
        Exception lastException = null;
        long currentDelay = retryDelayMs;

        for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
            try {
                log.info("[{}] 开始请求URL (第{}次尝试): {}", getSourceName(), attempt, url);
                sleepBeforeRequest();
                String html = fetchHtml(url, attempt);
                log.info("[{}] 请求成功 (第{}次尝试)，响应长度: {}字符", 
                         getSourceName(), attempt, html != null ? html.length() : 0);
                return html;
            } catch (SocketTimeoutException e) {
                lastException = e;
                log.warn("[{}] 请求超时 (第{}次尝试): {}", getSourceName(), attempt, e.getMessage());
            } catch (UnknownHostException e) {
                lastException = e;
                log.warn("[{}] DNS解析失败 (第{}次尝试): {}", getSourceName(), attempt, e.getMessage());
            } catch (java.net.ConnectException e) {
                lastException = e;
                log.warn("[{}] 连接被拒绝 (第{}次尝试): {}", getSourceName(), attempt, e.getMessage());
            } catch (javax.net.ssl.SSLException e) {
                lastException = e;
                log.warn("[{}] SSL握手失败 (第{}次尝试): {}", getSourceName(), attempt, e.getMessage());
            } catch (RuntimeException e) {
                lastException = e;
                if (e.getMessage() != null && e.getMessage().contains("HTTP请求失败")) {
                    log.warn("[{}] HTTP请求失败 (第{}次尝试): {}", getSourceName(), attempt, e.getMessage());
                } else {
                    log.warn("[{}] 请求发生运行时异常 (第{}次尝试)", getSourceName(), attempt, e);
                }
            } catch (Exception e) {
                lastException = e;
                log.warn("[{}] 请求发生未知异常 (第{}次尝试)", getSourceName(), attempt, e);
            }

            if (attempt < maxRetryAttempts) {
                try {
                    long jitter = random.nextLong(currentDelay / 2);
                    long waitTime = currentDelay + jitter;
                    log.info("[{}] 等待 {}ms 后进行第{}次重试", getSourceName(), waitTime, attempt + 1);
                    Thread.sleep(waitTime);
                    currentDelay = (long) (currentDelay * retryMultiplier);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("[{}] 重试等待被中断", getSourceName());
                    break;
                }
            }
        }

        String errorMsg = String.format("[%s] 请求URL失败，已重试%d次仍未成功。最后错误: %s",
                getSourceName(), maxRetryAttempts,
                lastException != null ? lastException.getMessage() : "未知错误");
        log.error(errorMsg);
        throw new CrawlerException(errorMsg, lastException, getSourceName(), CrawlerException.ErrorType.NETWORK_ERROR);
    }

    protected String fetchHtml(String url, int attempt) throws Exception {
        HttpResponse response = HttpRequest.get(url)
                .header("User-Agent", getRandomUserAgent())
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8,ja;q=0.7")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .header("Connection", "keep-alive")
                .header("Upgrade-Insecure-Requests", "1")
                .header("Sec-Ch-Ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"")
                .header("Sec-Ch-Ua-Mobile", "?0")
                .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Sec-Fetch-User", "?1")
                .setConnectionTimeout(connectTimeout)
                .setReadTimeout(timeout)
                .execute();

        if (!response.isOk()) {
            int status = response.getStatus();
            if (status == 403) {
                throw new CrawlerException("HTTP 403 禁止访问，可能被反爬机制拦截",
                        getSourceName(), CrawlerException.ErrorType.ACCESS_DENIED);
            } else if (status == 404) {
                throw new CrawlerException("HTTP 404 URL不存在: " + url,
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
    }

    protected Document parseDocument(String html) {
        if (html == null || html.isEmpty()) {
            throw new CrawlerException("HTML内容为空，无法解析",
                    getSourceName(), CrawlerException.ErrorType.PARSE_ERROR);
        }
        return Jsoup.parse(html);
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
        if (hotValue == null || rank == null) {
            return false;
        }
        return rank <= 10 || hotValue > 500000;
    }

    protected double calculateRisingRate(Long hotValue, Integer rank) {
        if (hotValue == null || hotValue <= 0 || rank == null) {
            return 0.0;
        }
        double baseRate = (hotValue / 1000000.0) * 50;
        double rankFactor = Math.max(0, (50 - rank) / 50.0) * 50;
        double jitter = random.nextDouble() * 10;
        return Math.round((baseRate + rankFactor + jitter) * 100.0) / 100.0;
    }

    protected List<HotEvent> validateEvents(List<HotEvent> events) {
        if (events == null || events.isEmpty()) {
            log.warn("[{}] 解析结果为空列表", getSourceName());
            throw new CrawlerException("解析结果为空，未获取到任何热点事件",
                    getSourceName(), CrawlerException.ErrorType.PARSE_ERROR);
        }

        List<HotEvent> validEvents = new ArrayList<>();
        int invalidCount = 0;

        for (HotEvent event : events) {
            if (requireTitle) {
                String title = event.getTitle();
                if (title == null || title.trim().isEmpty()) {
                    invalidCount++;
                    log.debug("[{}] 跳过无效事件：标题为空", getSourceName());
                    continue;
                }
                if (title.trim().length() < minTitleLength) {
                    invalidCount++;
                    log.debug("[{}] 跳过无效事件：标题过短 ({}) - {}", 
                             getSourceName(), title.length(), title);
                    continue;
                }
            }

            if (requireHotValue && (event.getHotValue() == null || event.getHotValue() <= 0)) {
                invalidCount++;
                log.debug("[{}] 跳过无效事件：热度值无效 - {}", getSourceName(), event.getTitle());
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

        log.info("[{}] 数据验证完成：原始{}条，有效{}条，无效{}条",
                getSourceName(), events.size(), validEvents.size(), invalidCount);

        if (validEvents.size() < minEventCount) {
            log.warn("[{}] 有效事件数量({})低于最小要求({})",
                    getSourceName(), validEvents.size(), minEventCount);
        }

        return validEvents;
    }

    protected List<HotEvent> executeCrawl() throws Exception {
        String source = getSourceName();
        log.info("========== [{}] 开始抓取任务 ==========", source);
        long startTime = System.currentTimeMillis();

        try {
            String url = getCrawlUrl();
            log.info("[{}] 目标URL: {}", source, url);

            String html = fetchHtmlWithRetry(url);
            log.debug("[{}] HTML内容长度: {}字符", source, html.length());

            List<HotEvent> rawEvents = parseHtml(html);
            log.info("[{}] 原始解析结果: {}条", source, rawEvents != null ? rawEvents.size() : 0);

            List<HotEvent> validEvents = validateEvents(rawEvents);
            long costTime = System.currentTimeMillis() - startTime;

            log.info("[{}] 抓取成功！共获取{}条有效热点事件，耗时{}ms",
                    source, validEvents.size(), costTime);
            log.info("========== [{}] 抓取任务完成 ==========", source);

            return validEvents;

        } catch (CrawlerException e) {
            long costTime = System.currentTimeMillis() - startTime;
            log.error("[{}] 抓取失败！耗时{}ms，错误类型: {}, 消息: {}",
                    source, costTime, e.getErrorType(), e.getMessage());
            log.info("========== [{}] 抓取任务失败 ==========", source);
            throw e;
        } catch (Exception e) {
            long costTime = System.currentTimeMillis() - startTime;
            log.error("[{}] 抓取失败！耗时{}ms，发生未预期异常", source, costTime, e);
            log.info("========== [{}] 抓取任务异常 ==========", source);
            throw new CrawlerException("抓取过程发生未预期异常: " + e.getMessage(),
                    e, source, CrawlerException.ErrorType.UNKNOWN);
        }
    }

    public static class CrawlerException extends RuntimeException {
        public enum ErrorType {
            NETWORK_ERROR,
            ACCESS_DENIED,
            NOT_FOUND,
            RATE_LIMITED,
            SERVER_ERROR,
            PARSE_ERROR,
            VALIDATION_ERROR,
            UNKNOWN
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

        public String getSourceName() {
            return sourceName;
        }

        public ErrorType getErrorType() {
            return errorType;
        }
    }
}
