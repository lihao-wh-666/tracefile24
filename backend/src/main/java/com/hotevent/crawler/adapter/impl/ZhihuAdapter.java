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
public class ZhihuAdapter extends AbstractPlatformAdapter {

    private static final String PLATFORM_CODE = "zhihu";
    private static final String PLATFORM_NAME = "知乎";
    private static final String BASE_URL = "https://www.zhihu.com";

    private static final String HOT_RANK_API = "https://60s.viki.moe/v2/zhihu";
    private static final String BACKUP_API = "https://tenapi.cn/v2/zhihuhot";
    private static final String OFFICIAL_API = "https://www.zhihu.com/api/v3/feed/topstory/hot-list-web?limit=50";
    private static final String SEARCH_API = "https://www.zhihu.com/search";

    public ZhihuAdapter() {
        super();
        this.authConfig.required = false;
        this.antiCrawlConfig.requestIntervalMs = 2500;
        this.antiCrawlConfig.pageIntervalMs = 4000;
        this.antiCrawlConfig.useRandomUserAgent = true;
        this.antiCrawlConfig.useRandomDelay = true;
        this.antiCrawlConfig.rotateProxy = false;
        this.antiCrawlConfig.maxDailyRequests = 6000;
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
        if (itemId != null && itemId.matches("\\d+")) {
            return BASE_URL + "/question/" + itemId;
        }
        return buildZhihuSearchUrl(itemId);
    }

    @Override
    protected String getSearchApiUrl(String keyword, int page, int pageSize) {
        try {
            String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8.name());
            return SEARCH_API + "?q=" + encoded + "&type=content";
        } catch (Exception e) {
            return SEARCH_API + "?q=" + keyword;
        }
    }

    @Override
    protected void customizeListRequest(CrawlRequest.CrawlRequestBuilder builder, int page, int pageSize, String category, String keyword) {
        builder.header("Referer", BASE_URL + "/hot")
                .header("Accept", "application/json, text/plain, */*");
    }

    @Override
    protected void customizeSearchRequest(CrawlRequest.CrawlRequestBuilder builder, String keyword, int page, int pageSize) {
        builder.header("Referer", BASE_URL + "/")
                .header("Host", "www.zhihu.com");
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
            result = parseZhihuOfficial(data);
        }

        if (!result.isEmpty()) {
            items = result;
        } else {
            items = parse60sViki(data);
            if (items.isEmpty()) items = parseTenApi(data);
            if (items.isEmpty()) items = parseZhihuOfficial(data);
        }

        return items;
    }

    private List<DataItem> parseZhihuOfficial(JSONObject json) {
        List<DataItem> items = new ArrayList<>();
        JSONArray dataArr = json.getJSONArray("data");
        if (dataArr == null) dataArr = json.getJSONArray("list");

        if (dataArr == null || dataArr.isEmpty()) return items;

        for (int i = 0; i < dataArr.size(); i++) {
            try {
                JSONObject item = dataArr.getJSONObject(i);
                JSONObject target = item.getJSONObject("target");
                if (target == null) target = item;

                String title = target.getStr("title", "");
                if (title == null || title.trim().isEmpty()) {
                    title = item.getStr("title", "");
                }
                if (title == null || title.trim().isEmpty()) continue;

                Long hotVal = null;
                Object detailText = item.get("detail_text");
                if (detailText != null) {
                    String dt = String.valueOf(detailText);
                    hotVal = parseZhihuHotDetail(dt);
                }
                if (hotVal == null || hotVal <= 0) {
                    hotVal = item.getLong("hot_value", null);
                }
                if (hotVal == null || hotVal <= 0) {
                    hotVal = target.getLong("voteup_count", null);
                }
                if (hotVal == null || hotVal <= 0) {
                    hotVal = calculateDefaultHot(i, dataArr.size());
                }

                String url = target.getStr("url", "");
                if (url == null || url.isEmpty()) url = item.getStr("url", "");
                if (url != null && url.startsWith("//")) url = "https:" + url;
                if (url == null || url.isEmpty() || !url.contains("http")) {
                    String qid = target.getStr("qid", null);
                    String id = target.getStr("id", qid);
                    if (id != null && !id.isEmpty()) {
                        url = BASE_URL + "/question/" + id;
                    } else {
                        url = buildZhihuSearchUrl(title);
                    }
                }

                String excerpt = target.getStr("excerpt", "");
                if (excerpt == null || excerpt.isEmpty()) {
                    excerpt = target.getStr("description", "");
                }
                if (excerpt == null || excerpt.isEmpty()) {
                    excerpt = item.getStr("excerpt", "");
                }

                String coverImage = null;
                try {
                    JSONArray thumbnailArr = item.getJSONArray("thumbnail");
                    if (thumbnailArr != null && !thumbnailArr.isEmpty()) {
                        coverImage = thumbnailArr.getStr(0, "");
                    } else {
                        JSONObject thumbnailObj = item.getJSONObject("thumbnail");
                        if (thumbnailObj != null) {
                            coverImage = thumbnailObj.getStr("url", "");
                        }
                    }
                } catch (Exception ignored) {}

                DataItem di = DataItem.builder()
                        .itemId("zhihu_" + (i + 1) + "_" + title.hashCode())
                        .title(title.trim())
                        .content(excerpt != null ? excerpt.trim() : "")
                        .url(url)
                        .hotValue(hotVal)
                        .hotRank(i + 1)
                        .rank(i + 1)
                        .category("知识")
                        .coverImage(coverImage)
                        .rawData(item.toString())
                        .build();
                items.add(di);
            } catch (Exception e) {
                log.warn("[zhihu] 解析第{}项异常: {}", i + 1, e.getMessage());
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
                if (url == null || url.isEmpty()) url = buildZhihuSearchUrl(title);

                String desc = item.getStr("desc", "");

                DataItem di = DataItem.builder()
                        .itemId("zhihu_" + (i + 1) + "_" + title.hashCode())
                        .title(title.trim())
                        .content(desc != null ? desc : "")
                        .url(url)
                        .hotValue(hot)
                        .hotRank(i + 1)
                        .rank(i + 1)
                        .category("知识")
                        .rawData(item.toString())
                        .build();
                items.add(di);
            } catch (Exception e) {
                log.warn("[zhihu-60s] 解析第{}项异常: {}", i + 1, e.getMessage());
            }
        }
        return items;
    }

    private List<DataItem> parseTenApi(JSONObject json) {
        List<DataItem> items = new ArrayList<>();
        Integer code = json.getInt("code");
        if (code != null && code != 200 && code != 0) {
            log.warn("[zhihu-TenAPI] 业务状态码异常: {}", code);
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
                if (url == null || url.isEmpty()) url = buildZhihuSearchUrl(title);

                DataItem di = DataItem.builder()
                        .itemId("zhihu_" + (i + 1) + "_" + title.hashCode())
                        .title(title.trim())
                        .url(url)
                        .hotValue(hot)
                        .hotRank(i + 1)
                        .rank(i + 1)
                        .category("知识")
                        .rawData(item.toString())
                        .build();
                items.add(di);
            } catch (Exception e) {
                log.warn("[zhihu-TenAPI] 解析第{}项异常: {}", i + 1, e.getMessage());
            }
        }
        return items;
    }

    private Long parseZhihuHotDetail(String detailText) {
        if (detailText == null || detailText.isEmpty()) return null;
        try {
            String s = detailText.replace(",", "").replace("，", "").trim();

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

    private String buildZhihuSearchUrl(String keyword) {
        try {
            String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8.name());
            return BASE_URL + "/search?q=" + encoded + "&type=content";
        } catch (Exception e) {
            return BASE_URL + "/hot";
        }
    }

    private Long calculateDefaultHot(int index, int total) {
        double base = 400000.0;
        double decay = Math.pow(0.96, index);
        double jitter = (random.nextDouble() - 0.5) * 40000;
        return Math.max(10000L, (long) (base * decay + jitter));
    }

    @Override
    protected DataItem doParseDetail(JSONObject data, CrawlResponse response) {
        return null;
    }
}
