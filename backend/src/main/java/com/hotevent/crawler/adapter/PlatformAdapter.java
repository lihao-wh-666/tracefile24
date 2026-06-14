package com.hotevent.crawler.adapter;

import com.hotevent.crawler.core.CrawlRequest;
import com.hotevent.crawler.core.CrawlResponse;
import com.hotevent.crawler.core.CrawlTask;
import com.hotevent.crawler.core.DataItem;

import java.util.List;
import java.util.Map;

public interface PlatformAdapter {

    String getPlatformName();

    String getPlatformCode();

    String getBaseUrl();

    PlatformType getPlatformType();

    boolean isEnabled();

    boolean needsLogin();

    boolean isLoggedIn();

    boolean login(Map<String, String> credentials) throws Exception;

    void logout() throws Exception;

    CrawlRequest buildListRequest(int page, int pageSize, String category, String keyword);

    CrawlRequest buildDetailRequest(String itemId, String url);

    CrawlRequest buildSearchRequest(String keyword, int page, int pageSize);

    List<DataItem> parseListResponse(CrawlResponse response);

    DataItem parseDetailResponse(CrawlResponse response);

    List<DataItem> parseSearchResponse(CrawlResponse response);

    CrawlTask createDefaultTask();

    boolean validateDataItem(DataItem item);

    String generateItemId(DataItem item);

    AuthConfig getAuthConfig();

    AntiCrawlConfig getAntiCrawlConfig();

    enum PlatformType {
        SOCIAL_MEDIA, SHORT_VIDEO, BBS, NEWS, GOVERNMENT, BLOG, ECOMMERCE
    }

    class AuthConfig {
        public boolean required;
        public String loginUrl;
        public String loginMethod;
        public String usernameField;
        public String passwordField;
        public String sessionCookieName;
        public long sessionExpireMinutes;
        public boolean supportCaptcha;
    }

    class AntiCrawlConfig {
        public int requestIntervalMs;
        public int pageIntervalMs;
        public boolean useRandomUserAgent;
        public boolean useRandomDelay;
        public boolean rotateProxy;
        public boolean supportJsRender;
        public String[] avoidPatterns;
        public int maxDailyRequests;
    }
}
