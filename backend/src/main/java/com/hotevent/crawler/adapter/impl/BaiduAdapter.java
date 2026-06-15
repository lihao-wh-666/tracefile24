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
public class BaiduAdapter extends AbstractPlatformAdapter {

    private static final String PLATFORM_CODE = "baidu";
    private static final String PLATFORM_NAME = "百度";
    private static final String BASE_URL = "https://top.baidu.com";

    private static final String HOT_RANK_API = "https://60s.viki.moe/v2/baidu/hot";
    private static final String BACKUP_API = "https://tenapi.cn/v2/baiduhot";
    private static final String OFFICIAL_API = "https://top.baidu.com/api/board?platform=wise&tab=realtime";
    private static final String SEARCH_API = "https://www.baidu.com/s";

    public BaiduAdapter() {
        super();
        this.authConfig.required = false;
        this.antiCrawlConfig.requestIntervalMs = 2000;
        this.antiCrawlConfig.pageIntervalMs = 3500;
        this.antiCrawlConfig.useRandomUserAgent = true;
        this.antiCrawlConfig.useRandomDelay = true;
        this.antiCrawlConfig.rotateProxy = false;
        this.antiCrawlConfig.maxDailyRequests = 8000;
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
        return PlatformType.NEWS;
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
        return buildBaiduSearchUrl(itemId);
    }

    @Override
    protected String getSearchApiUrl(String keyword, int page, int pageSize) {
        try {
            String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8.name());
            int pn = (page - 1) * 10;
            return SEARCH_API + "?wd=" + encoded + "&pn=" + pn + "&rn=" + pageSize;
        } catch (Exception e) {
            return SEARCH_API + "?wd=" + keyword;
        }
    }

    @Override
    protected void customizeListRequest(CrawlRequest.CrawlRequestBuilder builder, int page, int pageSize, String category, String keyword) {
        builder.header("Referer", BASE_URL + "/board?tab=realtime")
                .header("Accept", "application/json, text/plain, */*");
    }

    @Override
    protected void customizeSearchRequest(CrawlRequest.CrawlRequestBuilder builder, String keyword, int page, int pageSize) {
        builder.header("Referer", "https://www.baidu.com/")
                .header("Host", "www.baidu.com");
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
            result = parseBaiduOfficial(data);
        }

        if (!result.isEmpty()) {
            items = result;
        } else {
            items = parse60sViki(data);
            if (items.isEmpty()) items = parseTenApi(data);
            if (items.isEmpty()) items = parseBaiduOfficial(data);
        }

        return items;
    }

    private List<DataItem> parseBaiduOfficial(JSONObject json) {
        List<DataItem> items = new ArrayList<>();
        JSONArray itemArr = null;

        Object dataObj = json.get("data");
        if (dataObj instanceof JSONObject) {
            JSONObject data = (JSONObject) dataObj;
            JSONArray cards = data.getJSONArray("cards");
            if (cards != null) {
                for (int c = 0; c < cards.size(); c++) {
                    try {
                        JSONObject card = cards.getJSONObject(c);
                        JSONArray content = card.getJSONArray("content");
                        if (content != null && !content.isEmpty()) {
                            itemArr = content;
                            break;
                        }
                    } catch (Exception ignored) {}
                }
            }
            if (itemArr == null) itemArr = data.getJSONArray("content");
            if (itemArr == null) itemArr = data.getJSONArray("list");
        }
        if (itemArr == null) itemArr = json.getJSONArray("content");
        if (itemArr == null) itemArr = json.getJSONArray("list");
        if (itemArr == null) itemArr = json.getJSONArray("cards");

        if (itemArr == null || itemArr.isEmpty()) {
            return items;
        }

        for (int i = 0; i < itemArr.size(); i++) {
            try {
                JSONObject item = itemArr.getJSONObject(i);
                String word = item.getStr("word", "");
                String title = item.getStr("wordShareInfo", null);
                if (title == null && item.containsKey("wordShareInfo")) {
                    try {
                        JSONObject wsi = item.getJSONObject("wordShareInfo");
                        if (wsi != null) {
                            title = wsi.getStr("word", "");
                            if (title == null || title.isEmpty()) title = wsi.getStr("title", "");
                        }
                    } catch (Exception ignored) {}
                }
                if (title == null || title.isEmpty()) title = word;
                if (title == null || title.trim().isEmpty()) continue;

                Long hotValue = item.getLong("hotValue", null);
                if (hotValue == null) hotValue = item.getLong("hotScore", null);
                if (hotValue == null || hotValue <= 0) {
                    hotValue = calculateDefaultHot(i, itemArr.size());
                }

                String url = item.getStr("url", "");
                if (url == null || url.isEmpty()) {
                    String appUrl = item.getStr("appUrl", "");
                    if (appUrl != null && !appUrl.isEmpty()) {
                        url = appUrl;
                    } else {
                        url = buildBaiduSearchUrl(title);
                    }
                }
                if (url != null && url.startsWith("//")) url = "https:" + url;

                String desc = item.getStr("desc", "");
                if (desc == null || desc.isEmpty()) desc = item.getStr("description", "");

                DataItem di = DataItem.builder()
                        .itemId("baidu_" + (i + 1) + "_" + title.hashCode())
                        .title(title.trim())
                        .content(desc != null ? desc.trim() : "")
                        .url(url)
                        .hotValue(hotValue)
                        .hotRank(i + 1)
                        .rank(i + 1)
                        .category("综合")
                        .rawData(item.toString())
                        .build();
                items.add(di);
            } catch (Exception e) {
                log.warn("[baidu] 解析第{}项异常: {}", i + 1, e.getMessage());
            }
        }
        return items;
    }

    private List<DataItem> parse60sViki(JSONObject json) {
        List<DataItem> items = new ArrayList<>();
        JSONArray data = null;

        Object dataObj = json.get("data");
        if (dataObj instanceof JSONArray) {
            data = (JSONArray) dataObj;
        } else if (dataObj instanceof JSONObject) {
            JSONObject d = (JSONObject) dataObj;
            Object listObj = d.get("list");
            if (listObj instanceof JSONArray) {
                data = (JSONArray) listObj;
            } else {
                Object innerDataObj = d.get("data");
                if (innerDataObj instanceof JSONArray) {
                    data = (JSONArray) innerDataObj;
                }
            }
        }

        if (data == null || data.isEmpty()) return items;

        for (int i = 0; i < data.size(); i++) {
            try {
                JSONObject item = data.getJSONObject(i);
                String title = item.getStr("title", item.getStr("word", ""));
                if (title == null || title.trim().isEmpty()) continue;

                Long hot = item.getLong("hot", null);
                if (hot == null) hot = item.getLong("hotValue", item.getLong("num", calculateDefaultHot(i, data.size())));

                String url = item.getStr("url", "");
                if (url == null || url.isEmpty()) url = buildBaiduSearchUrl(title);

                String desc = item.getStr("desc", "");

                DataItem di = DataItem.builder()
                        .itemId("baidu_" + (i + 1) + "_" + title.hashCode())
                        .title(title.trim())
                        .content(desc != null ? desc : "")
                        .url(url)
                        .hotValue(hot)
                        .hotRank(i + 1)
                        .rank(i + 1)
                        .category("综合")
                        .rawData(item.toString())
                        .build();
                items.add(di);
            } catch (Exception e) {
                log.warn("[baidu-60s] 解析第{}项异常: {}", i + 1, e.getMessage());
            }
        }
        return items;
    }

    private List<DataItem> parseTenApi(JSONObject json) {
        List<DataItem> items = new ArrayList<>();
        Integer code = json.getInt("code");
        if (code != null && code != 200 && code != 0) {
            log.warn("[baidu-TenAPI] 业务状态码异常: {}", code);
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
                if (url == null || url.isEmpty()) url = buildBaiduSearchUrl(title);

                DataItem di = DataItem.builder()
                        .itemId("baidu_" + (i + 1) + "_" + title.hashCode())
                        .title(title.trim())
                        .url(url)
                        .hotValue(hot)
                        .hotRank(i + 1)
                        .rank(i + 1)
                        .category("综合")
                        .rawData(item.toString())
                        .build();
                items.add(di);
            } catch (Exception e) {
                log.warn("[baidu-TenAPI] 解析第{}项异常: {}", i + 1, e.getMessage());
            }
        }
        return items;
    }

    private String buildBaiduSearchUrl(String keyword) {
        try {
            String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8.name());
            return "https://www.baidu.com/s?wd=" + encoded;
        } catch (Exception e) {
            return "https://top.baidu.com/board?tab=realtime";
        }
    }

    private Long calculateDefaultHot(int index, int total) {
        double base = 500000.0;
        double decay = Math.pow(0.95, index);
        double jitter = (random.nextDouble() - 0.5) * 50000;
        return Math.max(10000L, (long) (base * decay + jitter));
    }

    @Override
    protected DataItem doParseDetail(JSONObject data, CrawlResponse response) {
        return null;
    }
}
