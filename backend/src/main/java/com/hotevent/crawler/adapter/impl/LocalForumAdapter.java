package com.hotevent.crawler.adapter.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.hotevent.crawler.adapter.AbstractPlatformAdapter;
import com.hotevent.crawler.core.CrawlRequest;
import com.hotevent.crawler.core.CrawlResponse;
import com.hotevent.crawler.core.DataItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class LocalForumAdapter extends AbstractPlatformAdapter {

    private static final String PLATFORM_CODE = "local_forum";
    private static final String PLATFORM_NAME = "本地论坛";

    private String baseUrl = "https://bbs.example.com";

    public LocalForumAdapter() {
        super();
        this.authConfig.required = false;
        this.authConfig.sessionCookieName = "bbs_token";
        this.authConfig.sessionExpireMinutes = 60 * 24;
        this.antiCrawlConfig.requestIntervalMs = 1800;
        this.antiCrawlConfig.pageIntervalMs = 3000;
        this.antiCrawlConfig.useRandomUserAgent = true;
        this.antiCrawlConfig.useRandomDelay = true;
        this.antiCrawlConfig.rotateProxy = false;
        this.antiCrawlConfig.supportJsRender = false;
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
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public PlatformType getPlatformType() {
        return PlatformType.BBS;
    }

    @Override
    protected String getListApiUrl(int page, int pageSize, String category, String keyword) {
        String fid = "2";
        if (category != null && !category.isEmpty()) {
            fid = category;
        }
        return baseUrl + "/forum-" + fid + "-" + page + ".html";
    }

    @Override
    protected String getDetailApiUrl(String itemId) {
        if (itemId.contains("://")) return itemId;
        return baseUrl + "/thread-" + itemId + "-1-1.html";
    }

    @Override
    protected String getSearchApiUrl(String keyword, int page, int pageSize) {
        try {
            String encoded = java.net.URLEncoder.encode(keyword, "GBK");
            return baseUrl + "/search.php?mod=forum&srchtxt=" + encoded
                    + "&orderby=heats&page=" + page;
        } catch (Exception e) {
            return baseUrl + "/search.php?mod=forum&page=" + page;
        }
    }

    @Override
    protected void customizeListRequest(CrawlRequest.CrawlRequestBuilder builder, int page, int pageSize, String category, String keyword) {
        builder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Referer", baseUrl + "/forum.php")
                .context("parse_html", true);
    }

    @Override
    protected void customizeDetailRequest(CrawlRequest.CrawlRequestBuilder builder, String itemId, String url) {
        builder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .context("parse_html", true);
    }

    @Override
    protected List<DataItem> doParseList(JSONObject data, CrawlResponse response) {
        return parseListFromHtml(response);
    }

    private List<DataItem> parseListFromHtml(CrawlResponse response) {
        List<DataItem> items = new ArrayList<>();
        try {
            if (response == null || response.getRawResponse() == null) return items;
            String html = response.getRawResponse().getBody();
            if (html == null || html.isEmpty()) return items;

            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html, baseUrl);
            org.jsoup.select.Elements threads = doc.select("#threadlisttableid tbody[id^=normalthread_], .threadlist li, .bm_c table tbody[id^=normalthread_]");
            if (threads.isEmpty()) {
                threads = doc.select("table#threadlisttableid tbody");
            }

            int rank = 0;
            for (org.jsoup.nodes.Element tbody : threads) {
                try {
                    org.jsoup.nodes.Element subject = tbody.selectFirst("a.s.xst, th.new a.xst, th.common a.xst, a.s.xst");
                    if (subject == null) subject = tbody.selectFirst("a[href*=thread]");
                    if (subject == null) continue;

                    String title = subject.text().trim();
                    String url = subject.absUrl("href");
                    if (url == null || url.isEmpty()) url = subject.attr("href");
                    if (title.isEmpty()) continue;

                    String tid = extractTid(url);

                    org.jsoup.select.Elements tds = tbody.select("td.num, td em");
                    Long replyCount = null;
                    Long viewCount = null;
                    if (tds.size() >= 2) {
                        replyCount = parseLongValue(tds.get(0).text());
                        viewCount = parseLongValue(tds.get(1).text());
                    } else if (!tds.isEmpty()) {
                        String num = tds.get(0).text();
                        String[] parts = num.split("[-/]");
                        if (parts.length == 2) {
                            replyCount = parseLongValue(parts[0]);
                            viewCount = parseLongValue(parts[1]);
                        }
                    }

                    org.jsoup.nodes.Element author = tbody.selectFirst("td.by cite a, .by cite a, a.xw1");
                    String authorName = author != null ? author.text().trim() : null;
                    String authorUrl = author != null ? author.absUrl("href") : null;
                    String uid = extractUid(authorUrl);

                    org.jsoup.nodes.Element dateEl = tbody.selectFirst("td.by em span, .by em span, em[id^=authorposton]");
                    String dateText = dateEl != null ? dateEl.text().trim() : (dateEl != null ? dateEl.attr("title") : null);

                    org.jsoup.select.Elements hotIcons = tbody.select("img[src*=hot], .heat, i[class*=hot]");
                    boolean isHot = !hotIcons.isEmpty();

                    org.jsoup.select.Elements imgs = tbody.select("img.attach_pop, img[src*=attachment], .pic img");
                    String cover = null;
                    if (!imgs.isEmpty()) {
                        cover = imgs.get(0).absUrl("src");
                        if (cover == null || cover.isEmpty()) cover = imgs.get(0).attr("src");
                    }

                    org.jsoup.select.Elements typeEls = tbody.select("em.ts, a[class*=type], td.icn a");
                    String typeName = null;
                    if (!typeEls.isEmpty()) {
                        typeName = typeEls.get(0).text().trim();
                    }

                    long hotValue = 0;
                    if (viewCount != null) hotValue += viewCount;
                    if (replyCount != null) hotValue += replyCount * 3;

                    DataItem di = DataItem.builder()
                            .itemId(tid)
                            .title(title)
                            .url(url)
                            .author(authorName)
                            .authorId(uid)
                            .viewCount(viewCount)
                            .commentCount(replyCount)
                            .hotValue(hotValue > 0 ? hotValue : null)
                            .hotRank(++rank)
                            .coverImage(cover)
                            .category(typeName != null ? typeName : "本地论坛")
                            .publishTime(parseForumDate(dateText))
                            .rawData(tbody.outerHtml())
                            .build();

                    if (isHot) di.putExtra("is_hot_topic", true);
                    items.add(di);
                } catch (Exception e) {
                    log.warn("[local_forum] 解析帖子项异常: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[local_forum] 解析列表HTML异常: {}", e.getMessage());
        }
        return items;
    }

    @Override
    protected DataItem doParseDetail(JSONObject data, CrawlResponse response) {
        return parseDetailFromHtml(response);
    }

    private DataItem parseDetailFromHtml(CrawlResponse response) {
        try {
            if (response == null || response.getRawResponse() == null) return null;
            String html = response.getRawResponse().getBody();
            if (html == null || html.isEmpty()) return null;

            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html, baseUrl);
            org.jsoup.nodes.Element firstPost = doc.selectFirst("#postlist > div[id^=post_]");
            if (firstPost == null) firstPost = doc.selectFirst(".plhin, .pls, .t_fsz, table.plhin");
            if (firstPost == null) firstPost = doc.body();

            String title = doc.title();
            org.jsoup.nodes.Element titleEl = doc.selectFirst("#thread_subject, h1.ts, .ts span");
            if (titleEl != null) title = titleEl.text().trim();

            String content = "";
            org.jsoup.nodes.Element contentEl = doc.selectFirst("td.t_f, .t_f, .pcb, .message");
            if (contentEl != null) {
                content = contentEl.text().trim();
            }

            org.jsoup.nodes.Element authorEl = firstPost.selectFirst("a.xw1, .authi a, .pls cite a, .avt a");
            String authorName = authorEl != null ? authorEl.text().trim() : null;

            org.jsoup.select.Elements allImgs = doc.select("td.t_f img, .t_f img, .attnm img, .pcb img");
            List<String> imgUrls = new ArrayList<>();
            for (org.jsoup.nodes.Element img : allImgs) {
                String src = img.absUrl("src");
                if (src == null || src.isEmpty()) src = img.attr("data-src");
                if (src != null && !src.isEmpty() && !src.contains("static/image/common/")) {
                    imgUrls.add(src);
                }
            }

            DataItem di = DataItem.builder()
                    .title(title)
                    .content(content)
                    .author(authorName)
                    .images(imgUrls.toArray(new String[0]))
                    .category("本地论坛")
                    .publishTime(parseForumDate(doc.select("em[id^=authorposton], .pti em, .authi em").text()))
                    .build();

            String currentUrl = response.getRawResponse().getFinalUrl();
            di.setUrl(currentUrl);
            di.setItemId(extractTid(currentUrl));

            return di;
        } catch (Exception e) {
            log.error("[local_forum] 解析详情HTML异常: {}", e.getMessage());
            return null;
        }
    }

    private String extractTid(String url) {
        if (url == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("thread-(\\d+)").matcher(url);
        if (m.find()) return m.group(1);
        m = java.util.regex.Pattern.compile("tid=(\\d+)").matcher(url);
        if (m.find()) return m.group(1);
        m = java.util.regex.Pattern.compile("/t/(\\d+)").matcher(url);
        if (m.find()) return m.group(1);
        return "tf_" + Math.abs(url.hashCode());
    }

    private String extractUid(String url) {
        if (url == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("uid=(\\d+)").matcher(url);
        if (m.find()) return m.group(1);
        m = java.util.regex.Pattern.compile("space-uid-(\\d+)").matcher(url);
        if (m.find()) return m.group(1);
        return null;
    }

    private java.time.LocalDateTime parseForumDate(String text) {
        if (text == null || text.isEmpty()) return java.time.LocalDateTime.now();
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})\\s+(\\d{1,2}):(\\d{1,2})").matcher(text);
            if (m.find()) {
                return java.time.LocalDateTime.of(
                        Integer.parseInt(m.group(1)),
                        Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3)),
                        Integer.parseInt(m.group(4)),
                        Integer.parseInt(m.group(5))
                );
            }
            m = java.util.regex.Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})").matcher(text);
            if (m.find()) {
                return java.time.LocalDateTime.of(
                        Integer.parseInt(m.group(1)),
                        Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3)),
                        0, 0
                );
            }
            if (text.contains("分钟前")) {
                int n = Integer.parseInt(text.replaceAll("\\D", ""));
                return java.time.LocalDateTime.now().minusMinutes(n);
            }
            if (text.contains("小时前")) {
                int n = Integer.parseInt(text.replaceAll("\\D", ""));
                return java.time.LocalDateTime.now().minusHours(n);
            }
            if (text.contains("天前")) {
                int n = Integer.parseInt(text.replaceAll("\\D", ""));
                return java.time.LocalDateTime.now().minusDays(n);
            }
        } catch (Exception ignored) {}
        return parseTimestamp(text);
    }
}
