package com.hotevent.crawler.adapter.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hotevent.crawler.adapter.AbstractPlatformAdapter;
import com.hotevent.crawler.core.CrawlRequest;
import com.hotevent.crawler.core.CrawlResponse;
import com.hotevent.crawler.core.DataItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class WeChatAdapter extends AbstractPlatformAdapter {

    private static final String PLATFORM_CODE = "wechat";
    private static final String PLATFORM_NAME = "微信公众号";
    private static final String BASE_URL = "https://mp.weixin.qq.com";

    private static final String SEARCH_API = "https://weixin.sogou.com/weixin";
    private static final String ARTICLE_LIST_API = "https://mp.weixin.qq.com/mp/profile_ext";
    private static final String HOT_ARTICLES_URL = "https://weixin.sogou.com/weixin?type=1&ie=utf8";

    public WeChatAdapter() {
        super();
        this.authConfig.required = false;
        this.authConfig.sessionExpireMinutes = 60 * 12;
        this.antiCrawlConfig.requestIntervalMs = 3000;
        this.antiCrawlConfig.pageIntervalMs = 5000;
        this.antiCrawlConfig.useRandomDelay = true;
        this.antiCrawlConfig.rotateProxy = true;
        this.antiCrawlConfig.maxDailyRequests = 3000;
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
        try {
            if ((keyword == null || keyword.isEmpty()) && page == 1) {
                return HOT_ARTICLES_URL;
            }
            StringBuilder url = new StringBuilder(SEARCH_API + "?type=2");
            if (keyword != null && !keyword.isEmpty()) {
                url.append("&query=").append(URLEncoder.encode(keyword, StandardCharsets.UTF_8.name()));
            }
            url.append("&page=").append(page);
            return url.toString();
        } catch (Exception e) {
            return SEARCH_API + "?page=" + page;
        }
    }

    @Override
    protected String getDetailApiUrl(String itemId) {
        return BASE_URL + "/s?__biz=" + itemId;
    }

    @Override
    protected String getSearchApiUrl(String keyword, int page, int pageSize) {
        return getListApiUrl(page, pageSize, null, keyword);
    }

    @Override
    protected void customizeListRequest(CrawlRequest.CrawlRequestBuilder builder, int page, int pageSize, String category, String keyword) {
        builder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        builder.header("Host", "weixin.sogou.com");
        if ((keyword == null || keyword.isEmpty()) && page == 1) {
            builder.header("Referer", "https://weixin.sogou.com/");
            builder.header("Accept-Language", "zh-CN,zh;q=0.9");
        }
    }

    @Override
    protected void customizeSearchRequest(CrawlRequest.CrawlRequestBuilder builder, String keyword, int page, int pageSize) {
        customizeListRequest(builder, page, pageSize, null, keyword);
    }

    @Override
    protected List<DataItem> doParseList(JSONObject data, CrawlResponse response) {
        List<DataItem> items = new ArrayList<>();
        if (isHotArticlesResponse(response)) {
            return parseHotRankHtml(response, items);
        }
        if (data == null) return parseHtmlFallback(response, items);

        JSONArray list = null;
        if (data.containsKey("data")) {
            Object d = data.get("data");
            if (d instanceof JSONArray) list = (JSONArray) d;
            else if (d instanceof JSONObject) {
                JSONObject dataObj = (JSONObject) d;
                list = dataObj.getJSONArray("list");
                if (list == null) list = dataObj.getJSONArray("articles");
            }
        }
        if (list == null) list = data.getJSONArray("articles");
        if (list == null) list = data.getJSONArray("list");

        if (list == null || list.isEmpty()) {
            return parseHtmlFallback(response, items);
        }

        for (int i = 0; i < list.size(); i++) {
            try {
                JSONObject item = list.getJSONObject(i);
                DataItem di = parseArticleItem(item);
                if (di != null) {
                    di.setHotRank(i + 1);
                    items.add(di);
                }
            } catch (Exception e) {
                log.warn("[wechat] 解析第{}条列表项异常: {}", i, e.getMessage());
            }
        }
        return items;
    }

    private boolean isHotArticlesResponse(CrawlResponse response) {
        if (response == null || response.getRequestId() == null) return false;
        String url = response.getRequestId();
        return url.contains("type=1") && url.contains("weixin.sogou.com");
    }

    private List<DataItem> parseHotRankHtml(CrawlResponse response, List<DataItem> items) {
        try {
            if (response == null || response.getRawResponse() == null) return items;
            String html = response.getRawResponse().getBody();
            if (html == null || html.isEmpty()) return items;

            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html);
            org.jsoup.select.Elements newsList = doc.select(".news-list li, .news-list2 li");
            if (newsList.isEmpty()) {
                newsList = doc.select(".txt-box");
            }
            int rank = 0;
            for (org.jsoup.nodes.Element li : newsList) {
                try {
                    org.jsoup.nodes.Element linkEl = li.selectFirst("h3 a, .txt-box h3 a");
                    if (linkEl == null) continue;

                    String title = linkEl.text().trim();
                    String url = linkEl.attr("data-share");
                    if (url == null || url.isEmpty()) url = linkEl.absUrl("href");

                    org.jsoup.nodes.Element summaryEl = li.selectFirst(".txt-info, .s-p .s2");
                    String summary = summaryEl != null ? summaryEl.text().trim() : null;

                    org.jsoup.nodes.Element accountEl = li.selectFirst(".account, .s-p .account");
                    String account = accountEl != null ? accountEl.text().trim() : null;

                    org.jsoup.nodes.Element dateEl = li.selectFirst(".s2, .s-p time");
                    String dateText = dateEl != null ? dateEl.text().trim() : null;

                    org.jsoup.nodes.Element imgEl = li.selectFirst(".img-box img, .txt-box img");
                    String cover = imgEl != null ? imgEl.absUrl("src") : null;

                    DataItem di = DataItem.builder()
                            .title(title)
                            .url(url)
                            .summary(summary)
                            .author(account)
                            .coverImage(cover)
                            .publishTime(parseWechatDate(dateText))
                            .category("微信热门")
                            .hotRank(++rank)
                            .build();
                    items.add(di);
                } catch (Exception e) {
                    log.warn("[wechat] 热门文章解析单条异常: {}", e.getMessage());
                }
            }
            log.info("[wechat] 热门文章解析完成, 共{}条", items.size());
        } catch (Exception e) {
            log.warn("[wechat] 热门文章HTML解析异常: {}", e.getMessage());
        }
        return items;
    }

    private List<DataItem> parseHtmlFallback(CrawlResponse response, List<DataItem> items) {
        try {
            if (response == null || response.getRawResponse() == null) return items;
            String html = response.getRawResponse().getBody();
            if (html == null || html.isEmpty()) return items;

            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html);
            org.jsoup.select.Elements newsList = doc.select(".news-list li");
            int rank = 0;
            for (org.jsoup.nodes.Element li : newsList) {
                try {
                    org.jsoup.nodes.Element link = li.selectFirst(".txt-box h3 a");
                    if (link == null) continue;

                    String title = link.text().trim();
                    String url = link.attr("data-share");
                    if (url == null || url.isEmpty()) url = link.absUrl("href");

                    org.jsoup.nodes.Element summaryEl = li.selectFirst(".txt-info");
                    String summary = summaryEl != null ? summaryEl.text().trim() : null;

                    org.jsoup.nodes.Element accountEl = li.selectFirst(".account");
                    String account = accountEl != null ? accountEl.text().trim() : null;

                    org.jsoup.nodes.Element dateEl = li.selectFirst(".s2");
                    String dateText = dateEl != null ? dateEl.text().trim() : null;

                    DataItem di = DataItem.builder()
                            .title(title)
                            .url(url)
                            .summary(summary)
                            .author(account)
                            .publishTime(parseWechatDate(dateText))
                            .category("公众号文章")
                            .hotRank(++rank)
                            .build();
                    items.add(di);
                } catch (Exception e) {
                    log.warn("[wechat] HTML解析单条异常: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[wechat] HTML解析异常: {}", e.getMessage());
        }
        return items;
    }

    private java.time.LocalDateTime parseWechatDate(String dateText) {
        if (dateText == null || dateText.isEmpty()) return java.time.LocalDateTime.now();
        try {
            if (dateText.contains("分钟前")) {
                int min = Integer.parseInt(dateText.replaceAll("\\D", ""));
                return java.time.LocalDateTime.now().minusMinutes(min);
            } else if (dateText.contains("小时前")) {
                int hr = Integer.parseInt(dateText.replaceAll("\\D", ""));
                return java.time.LocalDateTime.now().minusHours(hr);
            } else if (dateText.contains("天前")) {
                int d = Integer.parseInt(dateText.replaceAll("\\D", ""));
                return java.time.LocalDateTime.now().minusDays(d);
            }
            return parseTimestamp(dateText);
        } catch (Exception e) {
            return java.time.LocalDateTime.now();
        }
    }

    private DataItem parseArticleItem(JSONObject item) {
        String title = item.getStr("title", item.getStr("name", ""));
        if (title == null || title.isEmpty()) return null;

        String url = item.getStr("url", item.getStr("link", item.getStr("content_url", "")));
        String author = item.getStr("author", item.getStr("nickname", item.getStr("user_name", "")));
        String summary = item.getStr("digest", item.getStr("summary", item.getStr("description", "")));
        String cover = item.getStr("cover", item.getStr("pic", item.getStr("cover_img", "")));
        Object pubTime = item.getObj("create_time", item.getObj("publish_time", item.getObj("datetime")));
        Long readCount = parseLongValue(item.getObj("read_num", item.getObj("read_count")));
        Long likeCount = parseLongValue(item.getObj("like_num", item.getObj("digg_count", item.getObj("like_count"))));
        Long commentCount = parseLongValue(item.getObj("comment_num", item.getObj("comment_count")));
        String biz = item.getStr("biz", item.getStr("__biz", ""));

        DataItem di = DataItem.builder()
                .title(title)
                .url(url)
                .author(author)
                .authorId(biz)
                .summary(summary)
                .coverImage(cover)
                .publishTime(parseTimestamp(pubTime))
                .viewCount(readCount)
                .likeCount(likeCount)
                .commentCount(commentCount)
                .category("公众号文章")
                .rawData(item.toString())
                .build();

        if (readCount != null) {
            di.setHotValue(readCount);
        }
        return di;
    }

    @Override
    protected DataItem doParseDetail(JSONObject data, CrawlResponse response) {
        if (data == null) {
            return parseDetailHtml(response);
        }
        JSONObject article = data.containsKey("data") ? data.getJSONObject("data") : data;
        return parseArticleItem(article);
    }

    private DataItem parseDetailHtml(CrawlResponse response) {
        try {
            if (response == null || response.getRawResponse() == null) return null;
            String html = response.getRawResponse().getBody();
            if (html == null || html.isEmpty()) return null;

            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html);
            String title = doc.selectFirst("#activity-name") != null
                    ? doc.selectFirst("#activity-name").text().trim()
                    : doc.title();

            org.jsoup.nodes.Element authorEl = doc.selectFirst("#js_name, .rich_media_meta_nickname a, .profile_nickname");
            String author = authorEl != null ? authorEl.text().trim() : null;

            org.jsoup.nodes.Element contentEl = doc.selectFirst("#js_content");
            String content = contentEl != null ? contentEl.text().trim() : null;

            org.jsoup.select.Elements imgs = doc.select("#js_content img");
            List<String> imgList = new ArrayList<>();
            for (org.jsoup.nodes.Element img : imgs) {
                String src = img.attr("data-src");
                if (src == null || src.isEmpty()) src = img.absUrl("src");
                if (src != null && !src.isEmpty()) imgList.add(src);
            }

            DataItem di = DataItem.builder()
                    .title(title)
                    .author(author)
                    .content(content)
                    .images(imgList.toArray(new String[0]))
                    .url(response.getRequestId() != null ? "" : "")
                    .category("公众号文章")
                    .build();

            if (response.getRawResponse() != null) {
                di.setUrl(response.getRawResponse().getFinalUrl());
            }
            return di;
        } catch (Exception e) {
            log.warn("[wechat] 详情HTML解析异常: {}", e.getMessage());
            return null;
        }
    }
}
