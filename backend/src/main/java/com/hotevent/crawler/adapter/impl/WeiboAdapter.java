package com.hotevent.crawler.adapter.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.hotevent.crawler.adapter.AbstractPlatformAdapter;
import com.hotevent.crawler.core.CrawlRequest;
import com.hotevent.crawler.core.CrawlResponse;
import com.hotevent.crawler.core.DataItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class WeiboAdapter extends AbstractPlatformAdapter {

    private static final String PLATFORM_CODE = "weibo";
    private static final String PLATFORM_NAME = "微博";
    private static final String BASE_URL = "https://weibo.com";

    private static final String HOT_RANK_API = "https://60s.viki.moe/v2/weibo";
    private static final String BACKUP_API = "https://tenapi.cn/v2/weibohot";
    private static final String OFFICIAL_API = "https://weibo.com/ajax/side/hotSearch";
    private static final String SEARCH_API = "https://s.weibo.com/weibo";

    public WeiboAdapter() {
        super();
        this.authConfig.required = false;
        this.antiCrawlConfig.requestIntervalMs = 2000;
        this.antiCrawlConfig.pageIntervalMs = 3500;
        this.antiCrawlConfig.useRandomUserAgent = true;
        this.antiCrawlConfig.useRandomDelay = true;
        this.antiCrawlConfig.rotateProxy = false;
        this.antiCrawlConfig.maxDailyRequests = 10000;
    }

    @Override
    public String getPlatformName() {
        return PLATFORM_NAME;
    }

    @Override
    public String getPlatformCode() {
        return PLATFORM_CODE;
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL;
    }

    @Override
    public PlatformType getPlatformType() {
        return PlatformType.SOCIAL_MEDIA;
    }

    @Override
    protected String getListApiUrl(int page, int pageSize, String category, String keyword) {
        if (page == 1) {
            return HOT_RANK_API;
        } else if (page == 2) {
            return BACKUP_API;
        }
        return OFFICIAL_API;
    }

    @Override
    protected String getDetailApiUrl(String itemId) {
        return buildWeiboSearchUrl(itemId);
    }

    @Override
    protected String getSearchApiUrl(String keyword, int page, int pageSize) {
        try {
            String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8.name());
            return SEARCH_API + "?q=%23" + encoded + "%23&page=" + page;
        } catch (Exception e) {
            return SEARCH_API + "?q=" + keyword;
        }
    }

    @Override
    protected void customizeListRequest(CrawlRequest.CrawlRequestBuilder builder, int page, int pageSize, String category, String keyword) {
        builder.header("Referer", "https://s.weibo.com/top/summary")
                .header("Accept", "application/json, text/plain, */*");
    }

    @Override
    protected void customizeSearchRequest(CrawlRequest.CrawlRequestBuilder builder, String keyword, int page, int pageSize) {
        builder.header("Referer", "https://s.weibo.com/")
                .header("Host", "s.weibo.com");
    }

    @Override
    protected List<DataItem> doParseList(JSONObject data, CrawlResponse response) {
        List<DataItem> items = new ArrayList<>();
        if (data == null) return items;

        String requestUrl = response.getRequest() != null ? response.getRequest().getUrl() : "";

        List<DataItem> result;
        if (requestUrl.contains("60s.viki.moe")) {
            result = parse60sViki(data);
        } else if (requestUrl.contains("tenapi.cn")) {
            result = parseTenApi(data);
        } else {
            result = parseWeiboOfficial(data);
        }

        if (!result.isEmpty()) {
            items = result;
        } else {
            items = parse60sViki(data);
            if (items.isEmpty()) items = parseTenApi(data);
            if (items.isEmpty()) items = parseWeiboOfficial(data);
        }

        return items;
    }

    private List<DataItem> parseWeiboOfficial(JSONObject json) {
        List<DataItem> items = new ArrayList<>();
        JSONArray realtime = null;

        Object dataObj = json.get("data");
        if (dataObj instanceof JSONObject) {
            JSONObject data = (JSONObject) dataObj;
            realtime = data.getJSONArray("realtime");
        }
        if (realtime == null) {
            realtime = json.getJSONArray("realtime");
        }

        if (realtime == null || realtime.isEmpty()) return items;

        for (int i = 0; i < realtime.size(); i++) {
            try {
                JSONObject item = realtime.getJSONObject(i);
                String word = item.getStr("word", "");
                if (word == null || word.trim().isEmpty()) continue;

                Long num = item.getLong("num", null);
                if (num == null) {
                    String rawHot = item.getStr("raw_hot", "");
                    num = parseWeiboHotValue(rawHot);
                }
                if (num == null) {
                    num = calculateDefaultHot(i, realtime.size());
                }

                String url = buildWeiboSearchUrl(word);
                String note = item.getStr("note", "");
                String title = note != null && !note.isEmpty() ? note : word;

                String category = item.getStr("category", "");
                if (category == null || category.isEmpty()) {
                    category = "社会";
                }

                String label = item.getStr("label_name", "");
                String desc = label != null && !label.isEmpty() ? "标签: " + label : "";

                DataItem di = DataItem.builder()
                        .itemId("weibo_" + (i + 1) + "_" + title.hashCode())
                        .title(title.trim())
                        .content(desc)
                        .url(url)
                        .hotValue(num)
                        .hotRank(i + 1)
                        .rank(i + 1)
                        .category(category)
                        .rawData(item.toString())
                        .build();
                items.add(di);
            } catch (Exception e) {
                log.warn("[weibo] 解析第{}项异常: {}", i + 1, e.getMessage());
            }
        }
        return items;
    }

    private List<DataItem> parse60sViki(JSONObject json) {
        List<DataItem> items = new ArrayList<>();
        JSONArray data = json.getJSONArray("data");

        if (data == null && json.containsKey("data")) {
            Object d = json.get("data");
            if (d instanceof JSONObject) {
                data = ((JSONObject) d).getJSONArray("list");
                if (data == null) data = ((JSONObject) d).getJSONArray("data");
            }
        }

        if (data == null || data.isEmpty()) return items;

        for (int i = 0; i < data.size(); i++) {
            try {
                JSONObject item = data.getJSONObject(i);
                String title = item.getStr("title", item.getStr("word", ""));
                if (title == null || title.trim().isEmpty()) continue;

                Long hot = item.getLong("hot", null);
                if (hot == null) hot = item.getLong("num", calculateDefaultHot(i, data.size()));

                String url = item.getStr("url", "");
                if (url == null || url.isEmpty()) url = buildWeiboSearchUrl(title);

                String desc = item.getStr("desc", "");

                DataItem di = DataItem.builder()
                        .itemId("weibo_" + (i + 1) + "_" + title.hashCode())
                        .title(title.trim())
                        .content(desc != null ? desc : "")
                        .url(url)
                        .hotValue(hot)
                        .hotRank(i + 1)
                        .rank(i + 1)
                        .category("社会")
                        .rawData(item.toString())
                        .build();
                items.add(di);
            } catch (Exception e) {
                log.warn("[weibo-60s] 解析第{}项异常: {}", i + 1, e.getMessage());
            }
        }
        return items;
    }

    private List<DataItem> parseTenApi(JSONObject json) {
        List<DataItem> items = new ArrayList<>();
        Integer code = json.getInt("code");
        if (code != null && code != 200 && code != 0) {
            log.warn("[weibo-TenAPI] 业务状态码异常: {}", code);
        }

        JSONArray list = null;
        Object data = json.get("data");
        if (data instanceof JSONObject) {
            list = ((JSONObject) data).getJSONArray("list");
        } else if (data instanceof JSONArray) {
            list = (JSONArray) data;
        }

        if (list == null) list = json.getJSONArray("list");
        if (list == null || list.isEmpty()) return items;

        for (int i = 0; i < list.size(); i++) {
            try {
                JSONObject item = list.getJSONObject(i);
                String title = item.getStr("name", item.getStr("title", item.getStr("word", "")));
                if (title == null || title.trim().isEmpty()) continue;

                Long hot = item.getLong("hot", null);
                if (hot == null) hot = item.getLong("hotValue", item.getLong("num", calculateDefaultHot(i, list.size())));

                String url = item.getStr("url", "");
                if (url == null || url.isEmpty()) url = buildWeiboSearchUrl(title);

                DataItem di = DataItem.builder()
                        .itemId("weibo_" + (i + 1) + "_" + title.hashCode())
                        .title(title.trim())
                        .url(url)
                        .hotValue(hot)
                        .hotRank(i + 1)
                        .rank(i + 1)
                        .category("社会")
                        .rawData(item.toString())
                        .build();
                items.add(di);
            } catch (Exception e) {
                log.warn("[weibo-TenAPI] 解析第{}项异常: {}", i + 1, e.getMessage());
            }
        }
        return items;
    }

    private Long parseWeiboHotValue(String rawHot) {
        if (rawHot == null || rawHot.isEmpty()) return null;
        try {
            String s = rawHot.replace(",", "").replace("，", "").trim();

            java.util.regex.Pattern pYi = java.util.regex.Pattern.compile("([\\d.]+)\\s*亿");
            java.util.regex.Matcher mYi = pYi.matcher(s);
            if (mYi.find()) return (long) (Double.parseDouble(mYi.group(1)) * 100000000);

            java.util.regex.Pattern pWan = java.util.regex.Pattern.compile("([\\d.]+)\\s*万");
            java.util.regex.Matcher mWan = pWan.matcher(s);
            if (mWan.find()) return (long) (Double.parseDouble(mWan.group(1)) * 10000);

            java.util.regex.Pattern pNum = java.util.regex.Pattern.compile("(\\d+)");
            java.util.regex.Matcher mNum = pNum.matcher(s);
            if (mNum.find()) return Long.parseLong(mNum.group(1));

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String buildWeiboSearchUrl(String keyword) {
        try {
            String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8.name());
            return "https://s.weibo.com/weibo?q=%23" + encoded + "%23";
        } catch (Exception e) {
            return "https://s.weibo.com/top/summary";
        }
    }

    private Long calculateDefaultHot(int index, int total) {
        double base = 800000.0;
        double decay = Math.pow(0.94, index);
        double jitter = (random.nextDouble() - 0.5) * 80000;
        return Math.max(10000L, (long) (base * decay + jitter));
    }

    @Override
    protected DataItem doParseDetail(JSONObject data, CrawlResponse response) {
        return null;
    }
}
