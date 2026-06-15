package com.hotevent.crawler.adapter;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hotevent.crawler.core.*;
import com.hotevent.crawler.http.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
public abstract class AbstractPlatformAdapter implements PlatformAdapter {

    @Autowired
    protected CrawlerClient crawlerClient;

    @Autowired
    protected HttpClientWrapper httpClient;

    @Autowired
    protected CookieManager cookieManager;

    @Autowired
    protected RateLimitManager rateLimitManager;

    protected ThreadLocalRandom random = ThreadLocalRandom.current();

    protected List<String> userAgents;

    protected boolean enabled = true;

    protected boolean loggedIn = false;

    protected Map<String, Object> authCache = new HashMap<>();

    protected AntiCrawlConfig antiCrawlConfig;

    protected AuthConfig authConfig;

    protected abstract String getListApiUrl(int page, int pageSize, String category, String keyword);

    protected abstract String getDetailApiUrl(String itemId);

    protected abstract String getSearchApiUrl(String keyword, int page, int pageSize);

    protected abstract List<DataItem> doParseList(JSONObject data, CrawlResponse response);

    protected abstract DataItem doParseDetail(JSONObject data, CrawlResponse response);

    protected AbstractPlatformAdapter() {
        this.antiCrawlConfig = new AntiCrawlConfig();
        this.antiCrawlConfig.requestIntervalMs = 1500;
        this.antiCrawlConfig.pageIntervalMs = 3000;
        this.antiCrawlConfig.useRandomUserAgent = true;
        this.antiCrawlConfig.useRandomDelay = true;
        this.antiCrawlConfig.rotateProxy = false;
        this.antiCrawlConfig.supportJsRender = false;
        this.antiCrawlConfig.maxDailyRequests = 10000;

        this.authConfig = new AuthConfig();
        this.authConfig.required = false;
        this.authConfig.sessionExpireMinutes = 60 * 24;

        this.userAgents = Arrays.asList(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:126.0) Gecko/20100101 Firefox/126.0",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15"
        );
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean needsLogin() {
        return authConfig != null && authConfig.required;
    }

    @Override
    public boolean isLoggedIn() {
        if (!needsLogin()) return true;
        return loggedIn;
    }

    @Override
    public boolean login(Map<String, String> credentials) throws Exception {
        if (!needsLogin()) {
            loggedIn = true;
            return true;
        }
        try {
            boolean result = doLogin(credentials);
            loggedIn = result;
            if (result) {
                authCache.put("loginTime", System.currentTimeMillis());
                authCache.put("credentials", credentials);
            }
            return result;
        } catch (Exception e) {
            log.error("[{}] 登录失败: {}", getPlatformCode(), e.getMessage());
            loggedIn = false;
            throw e;
        }
    }

    protected boolean doLogin(Map<String, String> credentials) throws Exception {
        return true;
    }

    @Override
    public void logout() throws Exception {
        cookieManager.clearCookies(extractDomain(getBaseUrl()));
        loggedIn = false;
        authCache.clear();
    }

    @Override
    public CrawlRequest buildListRequest(int page, int pageSize, String category, String keyword) {
        ensureLoggedInIfNeeded();
        CrawlRequest.CrawlRequestBuilder builder = CrawlRequest.builder()
                .requestType(CrawlRequest.RequestType.LIST)
                .platform(getPlatformCode())
                .url(getListApiUrl(page, pageSize, category, keyword))
                .method(HttpMethod.GET)
                .page(page)
                .pageSize(pageSize)
                .category(category)
                .keyword(keyword)
                .requestIntervalMs(getRequestIntervalWithJitter())
                .useProxy(antiCrawlConfig != null && antiCrawlConfig.rotateProxy);

        Map<String, String> headers = buildCommonHeaders();
        builder.headers(headers);

        customizeListRequest(builder, page, pageSize, category, keyword);

        return builder.build();
    }

    @Override
    public CrawlRequest buildDetailRequest(String itemId, String url) {
        ensureLoggedInIfNeeded();
        String targetUrl = url != null && !url.isEmpty() ? url : getDetailApiUrl(itemId);
        CrawlRequest.CrawlRequestBuilder builder = CrawlRequest.builder()
                .requestType(CrawlRequest.RequestType.DETAIL)
                .platform(getPlatformCode())
                .url(targetUrl)
                .method(HttpMethod.GET)
                .requestIntervalMs(getRequestIntervalWithJitter())
                .useProxy(antiCrawlConfig != null && antiCrawlConfig.rotateProxy);

        Map<String, String> headers = buildCommonHeaders();
        builder.headers(headers);
        builder.context("itemId", itemId);

        customizeDetailRequest(builder, itemId, url);

        return builder.build();
    }

    @Override
    public CrawlRequest buildSearchRequest(String keyword, int page, int pageSize) {
        ensureLoggedInIfNeeded();
        CrawlRequest.CrawlRequestBuilder builder = CrawlRequest.builder()
                .requestType(CrawlRequest.RequestType.SEARCH)
                .platform(getPlatformCode())
                .url(getSearchApiUrl(keyword, page, pageSize))
                .method(HttpMethod.GET)
                .page(page)
                .pageSize(pageSize)
                .keyword(keyword)
                .requestIntervalMs(getRequestIntervalWithJitter())
                .useProxy(antiCrawlConfig != null && antiCrawlConfig.rotateProxy);

        Map<String, String> headers = buildCommonHeaders();
        builder.headers(headers);

        customizeSearchRequest(builder, keyword, page, pageSize);

        return builder.build();
    }

    protected void customizeListRequest(CrawlRequest.CrawlRequestBuilder builder, int page, int pageSize, String category, String keyword) {
    }

    protected void customizeDetailRequest(CrawlRequest.CrawlRequestBuilder builder, String itemId, String url) {
    }

    protected void customizeSearchRequest(CrawlRequest.CrawlRequestBuilder builder, String keyword, int page, int pageSize) {
    }

    @Override
    public List<DataItem> parseListResponse(CrawlResponse response) {
        if (response == null || !response.isSuccess()) {
            log.warn("[{}] 列表响应无效: status={}", getPlatformCode(), response != null ? response.getStatus() : "null");
            return Collections.emptyList();
        }
        try {
            JSONObject json = parseResponseJson(response);
            if (json == null) {
                return Collections.emptyList();
            }
            List<DataItem> items = doParseList(json, response);
            if (items == null) return Collections.emptyList();
            items.forEach(this::finalizeDataItem);
            return items;
        } catch (Exception e) {
            log.error("[{}] 解析列表响应异常: {}", getPlatformCode(), e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public DataItem parseDetailResponse(CrawlResponse response) {
        if (response == null || !response.isSuccess()) {
            return null;
        }
        try {
            JSONObject json = parseResponseJson(response);
            if (json == null) return null;
            DataItem item = doParseDetail(json, response);
            if (item != null) {
                finalizeDataItem(item);
            }
            return item;
        } catch (Exception e) {
            log.error("[{}] 解析详情响应异常: {}", getPlatformCode(), e.getMessage());
            return null;
        }
    }

    @Override
    public List<DataItem> parseSearchResponse(CrawlResponse response) {
        return parseListResponse(response);
    }

    @Override
    public CrawlTask createDefaultTask() {
        CrawlTask task = CrawlTask.builder()
                .taskName(getPlatformName() + "默认采集任务")
                .platform(getPlatformCode())
                .mode(CrawlTask.TaskMode.INCREMENTAL)
                .maxItems(50)
                .maxDepth(2)
                .build();

        for (int page = 1; page <= 3; page++) {
            task.addRequest(buildListRequest(page, 20, null, null));
        }
        return task;
    }

    @Override
    public boolean validateDataItem(DataItem item) {
        if (item == null) return false;
        if (StrUtil.isBlank(item.getTitle())) return false;
        if (StrUtil.isBlank(item.getPlatform())) item.setPlatform(getPlatformCode());
        return true;
    }

    @Override
    public String generateItemId(DataItem item) {
        try {
            String source = item.getUrl() != null ? item.getUrl()
                    : (item.getTitle() + "_" + item.getPublishTime());
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(getPlatformCode() + "_");
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return getPlatformCode() + "_" + UUID.randomUUID().toString().replace("-", "");
        }
    }

    @Override
    public AuthConfig getAuthConfig() {
        return authConfig;
    }

    @Override
    public AntiCrawlConfig getAntiCrawlConfig() {
        return antiCrawlConfig;
    }

    protected Map<String, String> buildCommonHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", getRandomUserAgent());
        headers.put("Accept", "application/json, text/plain, */*");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        headers.put("Cache-Control", "no-cache");
        headers.put("Pragma", "no-cache");
        headers.put("Connection", "keep-alive");
        String base = getBaseUrl();
        if (base != null) {
            headers.put("Referer", base);
        }
        return headers;
    }

    protected String getRandomUserAgent() {
        if (userAgents == null || userAgents.isEmpty()) {
            return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
        }
        return userAgents.get(random.nextInt(userAgents.size()));
    }

    protected long getRequestIntervalWithJitter() {
        if (antiCrawlConfig == null) return 1000L;
        long base = antiCrawlConfig.requestIntervalMs;
        if (!antiCrawlConfig.useRandomDelay) return base;
        long jitter = (long) (base * 0.5 * (random.nextDouble() - 0.5) * 2);
        return Math.max(500L, base + jitter);
    }

    protected void ensureLoggedInIfNeeded() {
        if (needsLogin() && !isLoggedIn()) {
            throw new IllegalStateException("平台[" + getPlatformCode() + "]需要登录，请先调用login()方法");
        }
    }

    protected JSONObject parseResponseJson(CrawlResponse response) {
        HttpResponseWrapper raw = response.getRawResponse();
        if (raw == null || raw.getBody() == null || raw.getBody().isEmpty()) {
            log.warn("[{}] 响应体为空", getPlatformCode());
            return null;
        }
        String body = raw.getBody();
        try {
            body = cleanJsonBody(body);
            if (log.isDebugEnabled()) {
                String preview = body.length() > 200 ? body.substring(0, 200) + "..." : body;
                log.debug("[{}] 响应体预览: {}", getPlatformCode(), preview);
            }
            return JSONUtil.parseObj(body);
        } catch (Exception e) {
            log.warn("[{}] JSON解析失败，body前100字符=[{}], 错误: {}", getPlatformCode(),
                    body.length() > 100 ? body.substring(0, 100) : body, e.getMessage());
            try {
                String cleaned = aggressiveCleanJson(body);
                if (!cleaned.equals(body)) {
                    log.info("[{}] 尝试激进清洗后重新解析", getPlatformCode());
                    return JSONUtil.parseObj(cleaned);
                }
            } catch (Exception e2) {
                log.warn("[{}] 激进清洗后解析仍失败: {}", getPlatformCode(), e2.getMessage());
            }
            return null;
        }
    }

    private String cleanJsonBody(String body) {
        if (body == null) return null;
        String cleaned = body;
        if (cleaned.startsWith("\uFEFF")) {
            cleaned = cleaned.substring(1);
            log.debug("[{}] 去除UTF-8 BOM头", getPlatformCode());
        }
        cleaned = cleaned.trim();
        while (!cleaned.isEmpty() && cleaned.charAt(0) != '{' && cleaned.charAt(0) != '[') {
            cleaned = cleaned.substring(1);
        }
        while (!cleaned.isEmpty() && cleaned.charAt(cleaned.length() - 1) != '}' && cleaned.charAt(cleaned.length() - 1) != ']') {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned;
    }

    private String aggressiveCleanJson(String body) {
        if (body == null) return null;
        int firstBrace = body.indexOf('{');
        int firstBracket = body.indexOf('[');
        int start = -1;
        if (firstBrace >= 0 && firstBracket >= 0) {
            start = Math.min(firstBrace, firstBracket);
        } else if (firstBrace >= 0) {
            start = firstBrace;
        } else if (firstBracket >= 0) {
            start = firstBracket;
        }
        if (start > 0) {
            return body.substring(start).trim();
        }
        return body.trim();
    }

    protected void finalizeDataItem(DataItem item) {
        if (item == null) return;
        if (StrUtil.isBlank(item.getPlatform())) item.setPlatform(getPlatformCode());
        if (item.getCrawlTime() == null) item.setCrawlTime(LocalDateTime.now());
        if (StrUtil.isBlank(item.getItemId())) item.setItemId(generateItemId(item));
    }

    protected String extractDomain(String url) {
        try {
            URL u = new URL(url);
            return u.getHost();
        } catch (MalformedURLException e) {
            return "unknown";
        }
    }

    protected LocalDateTime parseTimestamp(Object ts) {
        if (ts == null) return null;
        try {
            if (ts instanceof Number) {
                long millis = ((Number) ts).longValue();
                if (millis < 10000000000L) millis *= 1000;
                return LocalDateTime.ofEpochSecond(millis / 1000, 0, ZoneOffset.ofHours(8));
            } else if (ts instanceof String) {
                String s = (String) ts;
                for (String pattern : Arrays.asList(
                        "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss",
                        "yyyy/MM/dd HH:mm:ss", "yyyy-MM-dd",
                        "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
                )) {
                    try {
                        return LocalDateTime.parse(s, DateTimeFormatter.ofPattern(pattern));
                    } catch (Exception ignored) {}
                }
                try {
                    long millis = Long.parseLong(s);
                    if (millis < 10000000000L) millis *= 1000;
                    return LocalDateTime.ofEpochSecond(millis / 1000, 0, ZoneOffset.ofHours(8));
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return null;
    }

    protected Long parseLongValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            String s = String.valueOf(value).replace(",", "").trim();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("([\\d.]+)\\s*(亿|万|k|K|m|M)?").matcher(s);
            if (m.find()) {
                double num = Double.parseDouble(m.group(1));
                String unit = m.group(2);
                if (unit != null) {
                    switch (unit) {
                        case "亿": return (long) (num * 100000000);
                        case "万": return (long) (num * 10000);
                        case "k": case "K": return (long) (num * 1000);
                        case "m": case "M": return (long) (num * 1000000);
                    }
                }
                return (long) num;
            }
            return Long.parseLong(s);
        } catch (Exception e) {
            return null;
        }
    }
}
