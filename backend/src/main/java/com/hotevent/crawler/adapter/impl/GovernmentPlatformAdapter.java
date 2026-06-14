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
public class GovernmentPlatformAdapter extends AbstractPlatformAdapter {

    private static final String PLATFORM_CODE = "government";
    private static final String PLATFORM_NAME = "政务平台";

    private String baseUrl = "http://www.gov.cn";

    public GovernmentPlatformAdapter() {
        super();
        this.authConfig.required = false;
        this.authConfig.sessionExpireMinutes = 60 * 24;
        this.antiCrawlConfig.requestIntervalMs = 2000;
        this.antiCrawlConfig.pageIntervalMs = 3500;
        this.antiCrawlConfig.useRandomUserAgent = true;
        this.antiCrawlConfig.useRandomDelay = true;
        this.antiCrawlConfig.rotateProxy = false;
        this.antiCrawlConfig.supportJsRender = false;
        this.antiCrawlConfig.maxDailyRequests = 5000;
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
        return PlatformType.GOVERNMENT;
    }

    @Override
    protected String getListApiUrl(int page, int pageSize, String category, String keyword) {
        if (category != null && !category.isEmpty()) {
            switch (category) {
                case "policy":
                    return baseUrl + "/zhengce/cece/" + (page > 1 ? "index_" + (page - 1) : "index") + ".htm";
                case "notice":
                    return baseUrl + "/xinwen/yaowen/";
                case "department":
                    return baseUrl + "/xinwen/bumendongtai/";
                case "local":
                    return baseUrl + "/xinwen/difangdongtai/";
                default:
                    break;
            }
        }
        return baseUrl + "/xinwen/gongwuyuan/";
    }

    @Override
    protected String getDetailApiUrl(String itemId) {
        if (itemId == null) return baseUrl;
        if (itemId.startsWith("http")) return itemId;
        if (itemId.startsWith("/")) return baseUrl + itemId;
        return baseUrl + "/" + itemId;
    }

    @Override
    protected String getSearchApiUrl(String keyword, int page, int pageSize) {
        try {
            String encoded = java.net.URLEncoder.encode(keyword, "UTF-8");
            return "https://s.www.gov.cn/search-gov/data?t=zhengcelibrary_gw&q=" + encoded
                    + "&timetype=&mintime=&maxtime=&sort=score1&sortType=1&searchfield=title&pcodeJiguan=&childtype=&subchildtype=&tsbq=&pubtimeyear=&puborg=&pcodeYear=&pcodeNum=&filetype=&p=" + page
                    + "&n=" + pageSize + "&inpro=&bmfl=&dup=&orpro=&searchToken=&_=" + System.currentTimeMillis();
        } catch (Exception e) {
            return "https://s.www.gov.cn/search-gov/data";
        }
    }

    @Override
    protected void customizeListRequest(CrawlRequest.CrawlRequestBuilder builder, int page, int pageSize, String category, String keyword) {
        builder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Referer", baseUrl + "/")
                .context("parse_html", true);
    }

    @Override
    protected void customizeSearchRequest(CrawlRequest.CrawlRequestBuilder builder, String keyword, int page, int pageSize) {
        builder.header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("Referer", "https://s.www.gov.cn/")
                .header("X-Requested-With", "XMLHttpRequest");
    }

    @Override
    protected void customizeDetailRequest(CrawlRequest.CrawlRequestBuilder builder, String itemId, String url) {
        builder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .context("parse_html", true);
    }

    @Override
    protected List<DataItem> doParseList(JSONObject data, CrawlResponse response) {
        if (data != null && data.containsKey("searchVO")) {
            return parseSearchList(data);
        }
        return parseListFromHtml(response);
    }

    private List<DataItem> parseSearchList(JSONObject data) {
        List<DataItem> items = new ArrayList<>();
        try {
            JSONObject searchVO = data.getJSONObject("searchVO");
            if (searchVO == null) return items;
            JSONArray list = searchVO.getJSONArray("listVO");
            if (list == null || list.isEmpty()) return items;

            for (int i = 0; i < list.size(); i++) {
                try {
                    JSONObject item = list.getJSONObject(i);
                    String title = item.getStr("title", "");
                    if (title == null || title.isEmpty()) continue;
                    title = title.replaceAll("<[^>]+>", "").trim();

                    String url = item.getStr("url", "");
                    String summary = item.getStr("summary", "");
                    summary = summary != null ? summary.replaceAll("<[^>]+>", "").trim() : null;

                    String dateStr = item.getStr("pubtimeStr", item.getStr("docPublishDate", ""));
                    String org = item.getStr("sOrganName", item.getStr("pubOrgName", ""));
                    String fileType = item.getStr("filetype", item.getStr("typeOfFile", ""));

                    DataItem di = DataItem.builder()
                            .itemId("gov_" + Math.abs((title + url).hashCode()))
                            .title(title)
                            .url(url)
                            .summary(summary)
                            .author(org)
                            .category(fileType != null && !fileType.isEmpty() ? fileType : "政务公开")
                            .publishTime(parseTimestamp(dateStr))
                            .hotRank(i + 1)
                            .rawData(item.toString())
                            .build();

                    di.putExtra("doc_id", item.getStr("docId"));
                    di.putExtra("org_name", org);
                    di.putExtra("file_type", fileType);
                    items.add(di);
                } catch (Exception e) {
                    log.warn("[government] 解析搜索结果第{}项异常: {}", i, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[government] 解析搜索列表异常: {}", e.getMessage());
        }
        return items;
    }

    private List<DataItem> parseListFromHtml(CrawlResponse response) {
        List<DataItem> items = new ArrayList<>();
        try {
            if (response == null || response.getRawResponse() == null) return items;
            String html = response.getRawResponse().getBody();
            if (html == null || html.isEmpty()) return items;

            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html, baseUrl);
            org.jsoup.select.Elements lists = doc.select(".list li, .news_list li, .g-news-list li, .zhengce-list li, ul.list li");
            if (lists.isEmpty()) {
                lists = doc.select("table tr, .content li, .pages-content li");
            }

            int rank = 0;
            for (org.jsoup.nodes.Element li : lists) {
                try {
                    org.jsoup.nodes.Element link = li.selectFirst("a[href]");
                    if (link == null) continue;

                    String title = link.attr("title");
                    if (title == null || title.isEmpty()) title = link.text().trim();
                    if (title == null || title.isEmpty()) continue;

                    String url = link.absUrl("href");
                    if (url == null || url.isEmpty()) url = link.attr("href");

                    org.jsoup.nodes.Element dateEl = li.selectFirst(".date, .time, span.date, .news_cls_li_date, .pages-date");
                    String dateStr = dateEl != null ? dateEl.text().trim() : null;

                    org.jsoup.nodes.Element orgEl = li.selectFirst(".source, .from, .news_cls_li_yuan, .yuan");
                    String orgName = orgEl != null ? orgEl.text().trim() : null;

                    org.jsoup.nodes.Element summaryEl = li.selectFirst(".summary, .desc, p.summary, .news_cls_li_summary");
                    String summary = summaryEl != null ? summaryEl.text().trim() : null;

                    String docType = null;
                    org.jsoup.select.Elements typeTags = li.select("em, .type, .cls, .biaoshi");
                    if (!typeTags.isEmpty()) {
                        docType = typeTags.get(0).text().trim();
                    }

                    org.jsoup.nodes.Element regionEl = li.selectFirst("span.region, .diqu");
                    String region = regionEl != null ? regionEl.text().trim() : null;

                    DataItem di = DataItem.builder()
                            .itemId("gov_" + Math.abs((title + url).hashCode()))
                            .title(title)
                            .url(url)
                            .summary(summary)
                            .author(orgName)
                            .category(docType != null ? docType : "政务信息")
                            .hotRank(++rank)
                            .publishTime(parseGovDate(dateStr))
                            .rawData(li.outerHtml())
                            .build();

                    if (region != null) di.putExtra("region", region);
                    items.add(di);
                } catch (Exception e) {
                    log.warn("[government] 解析列表项异常: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[government] 解析列表HTML异常: {}", e.getMessage());
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

            String title = "";
            org.jsoup.nodes.Element titleEl = doc.selectFirst("h1, .title, .pages-title, .article-title, h1.tc");
            if (titleEl != null) title = titleEl.text().trim();
            if (title == null || title.isEmpty()) title = doc.title();

            org.jsoup.nodes.Element metaEl = doc.selectFirst(".pages-date, .date-source, .pages_01, .ly, .source, .time");
            String publishDate = null;
            String source = null;
            if (metaEl != null) {
                String metaText = metaEl.text().trim();
                java.util.regex.Matcher dm = java.util.regex.Pattern.compile("(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2})").matcher(metaText);
                if (dm.find()) publishDate = dm.group(1);
                java.util.regex.Matcher sm = java.util.regex.Pattern.compile("来源[:：]\\s*([^\\s|]+)").matcher(metaText);
                if (sm.find()) source = sm.group(1);
            }

            org.jsoup.nodes.Element contentEl = doc.selectFirst("#UCAP-CONTENT, .pages_content, .TRS_Editor, .content, .article-content, #content");
            String content = null;
            if (contentEl != null) {
                content = contentEl.text().trim();
            }

            org.jsoup.select.Elements imgs = doc.select("#UCAP-CONTENT img, .pages_content img, .TRS_Editor img, .content img");
            List<String> imgList = new ArrayList<>();
            for (org.jsoup.nodes.Element img : imgs) {
                String src = img.absUrl("src");
                if (src == null || src.isEmpty()) src = img.attr("src");
                if (src != null && !src.isEmpty()) imgList.add(src);
            }

            org.jsoup.select.Elements attachments = doc.select(".fujian a, .accessory a, .attachment a, a[href$=.pdf], a[href$=.doc], a[href$=.docx]");
            List<String> attachList = new ArrayList<>();
            for (org.jsoup.nodes.Element a : attachments) {
                String href = a.absUrl("href");
                if (href != null && !href.isEmpty()) attachList.add(href);
            }

            String fileNo = null;
            org.jsoup.nodes.Element fileNoEl = doc.selectFirst(".wenhao, .number, .wh, .zcfgjgflmbp_wh, .docno");
            if (fileNoEl != null) fileNo = fileNoEl.text().trim();

            String category = null;
            org.jsoup.select.Elements breadcrumbs = doc.select(".current_location a, .crumb a, .location a");
            if (!breadcrumbs.isEmpty()) {
                StringBuilder cat = new StringBuilder();
                for (org.jsoup.nodes.Element crumb : breadcrumbs) {
                    String c = crumb.text().trim();
                    if (!c.isEmpty() && !c.equals("首页") && !c.equals("当前位置")) {
                        if (cat.length() > 0) cat.append(" > ");
                        cat.append(c);
                    }
                }
                if (cat.length() > 0) category = cat.toString();
            }
            if (category == null || category.isEmpty()) category = "政务公开";

            DataItem di = DataItem.builder()
                    .title(title)
                    .content(content)
                    .author(source)
                    .images(imgList.toArray(new String[0]))
                    .category(category)
                    .publishTime(parseGovDate(publishDate))
                    .build();

            String curUrl = response.getRawResponse().getFinalUrl();
            di.setUrl(curUrl);
            di.setItemId("gov_" + Math.abs(curUrl.hashCode()));

            if (fileNo != null) di.putExtra("file_no", fileNo);
            if (!attachList.isEmpty()) di.putExtra("attachments", attachList);

            return di;
        } catch (Exception e) {
            log.error("[government] 解析详情HTML异常: {}", e.getMessage());
            return null;
        }
    }

    private java.time.LocalDateTime parseGovDate(String text) {
        if (text == null || text.isEmpty()) return java.time.LocalDateTime.now();
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{4})[年\\-/](\\d{1,2})[月\\-/](\\d{1,2})[日\\s]*(\\d{1,2})[:时](\\d{1,2})").matcher(text);
            if (m.find()) {
                return java.time.LocalDateTime.of(
                        Integer.parseInt(m.group(1)),
                        Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3)),
                        Integer.parseInt(m.group(4)),
                        Integer.parseInt(m.group(5))
                );
            }
            m = java.util.regex.Pattern.compile("(\\d{4})[年\\-/](\\d{1,2})[月\\-/](\\d{1,2})").matcher(text);
            if (m.find()) {
                return java.time.LocalDateTime.of(
                        Integer.parseInt(m.group(1)),
                        Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3)),
                        0, 0
                );
            }
        } catch (Exception ignored) {}
        return parseTimestamp(text);
    }
}
