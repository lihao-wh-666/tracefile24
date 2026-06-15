package com.hotevent.crawler.adapter.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.hotevent.crawler.adapter.AbstractPlatformAdapter;
import com.hotevent.crawler.core.CrawlRequest;
import com.hotevent.crawler.core.CrawlResponse;
import com.hotevent.crawler.core.DataItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class DouyinAdapter extends AbstractPlatformAdapter {

    private static final String PLATFORM_CODE = "douyin";
    private static final String PLATFORM_NAME = "抖音";
    private static final String BASE_URL = "https://www.douyin.com";

    private static final String HOT_RANK_API = "https://60s.viki.moe/v2/douyin";
    private static final String BACKUP_API = "https://60s.viki.moe/v2/douyin";
    private static final String OFFICIAL_API = "https://www.douyin.com/aweme/v1/web/hot/search/list/";
    private static final String SEARCH_API = "https://www.douyin.com/aweme/v1/web/general/search/single/";
    private static final String DETAIL_API = "https://www.douyin.com/aweme/v1/web/aweme/detail/";

    public DouyinAdapter() {
        super();
        this.authConfig.required = false;
        this.authConfig.sessionCookieName = "sessionid";
        this.authConfig.sessionExpireMinutes = 60 * 24 * 14;
        this.antiCrawlConfig.requestIntervalMs = 2000;
        this.antiCrawlConfig.pageIntervalMs = 3500;
        this.antiCrawlConfig.useRandomUserAgent = true;
        this.antiCrawlConfig.useRandomDelay = true;
        this.antiCrawlConfig.rotateProxy = true;
        this.antiCrawlConfig.supportJsRender = true;
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
        return PlatformType.SHORT_VIDEO;
    }

    @Override
    protected String getListApiUrl(int page, int pageSize, String category, String keyword) {
        if (page == 1) {
            return HOT_RANK_API;
        }
        return OFFICIAL_API + "?device_platform=webapp&aid=6383&channel=channel_pc_web";
    }

    @Override
    protected String getDetailApiUrl(String itemId) {
        return DETAIL_API + "?device_platform=webapp&aid=6383&aweme_id=" + itemId;
    }

    @Override
    protected String getSearchApiUrl(String keyword, int page, int pageSize) {
        try {
            String encoded = java.net.URLEncoder.encode(keyword, "UTF-8");
            int offset = (page - 1) * pageSize;
            return SEARCH_API + "?device_platform=webapp&aid=6383&keyword=" + encoded
                    + "&search_channel=aweme_video_web&sort_type=0&publish_time=0&search_source=normal_search"
                    + "&offset=" + offset + "&count=" + pageSize;
        } catch (Exception e) {
            return SEARCH_API;
        }
    }

    @Override
    protected void customizeListRequest(CrawlRequest.CrawlRequestBuilder builder, int page, int pageSize, String category, String keyword) {
        if (page == 1 || page == 2) {
            builder.header("Accept", "application/json, text/plain, */*");
            return;
        }
        builder.header("Host", "www.douyin.com")
                .header("Referer", BASE_URL + "/")
                .header("Origin", BASE_URL)
                .header("Accept", "application/json, text/plain, */*");
    }

    @Override
    protected void customizeSearchRequest(CrawlRequest.CrawlRequestBuilder builder, String keyword, int page, int pageSize) {
        try {
            builder.header("Host", "www.douyin.com")
                    .header("Referer", BASE_URL + "/search/" + java.net.URLEncoder.encode(keyword, "UTF-8") + "?type=general")
                    .header("Origin", BASE_URL);
        } catch (Exception ignored) {}
    }

    @Override
    protected void customizeDetailRequest(CrawlRequest.CrawlRequestBuilder builder, String itemId, String url) {
        builder.header("Host", "www.douyin.com")
                .header("Referer", BASE_URL + "/video/" + itemId)
                .header("Origin", BASE_URL);
    }

    @Override
    protected List<DataItem> doParseList(JSONObject data, CrawlResponse response) {
        List<DataItem> items = new ArrayList<>();
        if (data == null) return items;

        String requestUrl = response.getRequest() != null ? response.getRequest().getUrl() : "";

        if (requestUrl.contains("60s.viki.moe")) {
            List<DataItem> result = parse60sViki(data);
            if (!result.isEmpty()) return result;
        }

        JSONArray wordList = data.getByPath("data.word_list", JSONArray.class);
        if (wordList == null) wordList = data.getJSONArray("word_list");

        if (wordList != null && !wordList.isEmpty()) {
            int rank = 0;
            for (int i = 0; i < wordList.size(); i++) {
                try {
                    JSONObject item = wordList.getJSONObject(i);
                    String word = item.getStr("word", item.getStr("word_str", ""));
                    if (word == null || word.isEmpty()) continue;

                    Long hotValue = parseLongValue(item.getObj("hot_value", item.getObj("hot_value_list")));
                    if (hotValue == null) hotValue = parseLongValue(item.getObj("event_value"));

                    String label = item.getStr("label", "");
                    String sentence = item.getStr("sentence", item.getStr("summary", ""));
                    Object timestamp = item.getObj("timestamp");

                    ++rank;

                    StringBuilder summary = new StringBuilder();
                    summary.append("【抖音热榜第").append(rank).append("名】").append(word.trim());
                    if (sentence != null && !sentence.trim().isEmpty()) {
                        summary.append("。").append(sentence.trim());
                    }
                    summary.append("。当前热度值: ").append(hotValue != null ? formatHotValue(hotValue) : "暂无数据");

                    StringBuilder content = new StringBuilder(summary);
                    content.append("\n\n=== 详细信息 ===\n");
                    content.append("关键词: ").append(word.trim()).append("\n");
                    content.append("排名: 第").append(rank).append("名\n");
                    content.append("热度: ").append(hotValue != null ? formatHotValue(hotValue) : "暂无").append("\n");
                    if (label != null && !label.isEmpty()) {
                        content.append("标签: ").append(label).append("\n");
                    }
                    if (sentence != null && !sentence.trim().isEmpty()) {
                        content.append("简介: ").append(sentence.trim()).append("\n");
                    }
                    content.append("搜索链接: ").append(BASE_URL).append("/search/").append(encode(word)).append("?type=general\n");

                    DataItem di = DataItem.builder()
                            .title(word.trim())
                            .summary(summary.toString())
                            .content(content.toString())
                            .url(BASE_URL + "/search/" + encode(word) + "?type=general")
                            .hotValue(hotValue)
                            .hotRank(rank)
                            .rank(rank)
                            .tags(label)
                            .category("抖音热榜")
                            .publishTime(parseTimestamp(timestamp))
                            .rawData(item.toString())
                            .build();

                    JSONObject event = item.getJSONObject("event_info");
                    if (event != null) {
                        di.putExtra("event_id", event.getStr("event_id"));
                        di.putExtra("event_level", event.getStr("event_level"));
                        Long viewCount = parseLongValue(event.getObj("view_count"));
                        di.setViewCount(viewCount);
                        if (viewCount != null) {
                            content.append("浏览量: ").append(formatHotValue(viewCount)).append("\n");
                            di.setContent(content.toString());
                        }
                    }
                    di.putExtra("source", "douyin_official");
                    di.putExtra("hot_value_formatted", hotValue != null ? formatHotValue(hotValue) : "");
                    items.add(di);
                } catch (Exception e) {
                    log.warn("[douyin] 解析热榜第{}项异常: {}", i, e.getMessage());
                }
            }
            return items;
        }

        JSONArray awemeList = data.getByPath("data.aweme_list", JSONArray.class);
        if (awemeList == null) awemeList = data.getJSONArray("aweme_list");
        if (awemeList == null) {
            Object res = data.get("data");
            if (res instanceof JSONArray) awemeList = (JSONArray) res;
        }

        if (awemeList != null) {
            int rank = 0;
            for (int i = 0; i < awemeList.size(); i++) {
                try {
                    JSONObject aweme = awemeList.getJSONObject(i);
                    DataItem di = parseAwemeItem(aweme);
                    if (di != null) {
                        di.setHotRank(++rank);
                        items.add(di);
                    }
                } catch (Exception e) {
                    log.warn("[douyin] 解析视频第{}项异常: {}", i, e.getMessage());
                }
            }
        }
        return items;
    }

    private DataItem parseAwemeItem(JSONObject aweme) {
        if (aweme == null) return null;

        String awemeId = aweme.getStr("aweme_id", aweme.getStr("awemeId", ""));
        String desc = aweme.getStr("desc", aweme.getStr("title", ""));

        JSONObject author = aweme.getJSONObject("author");
        String nickname = null;
        String uid = null;
        String secUid = null;
        if (author != null) {
            nickname = author.getStr("nickname");
            uid = author.getStr("uid", author.getStr("user_id"));
            secUid = author.getStr("sec_uid");
        }

        JSONObject statistics = aweme.getJSONObject("statistics");
        Long playCount = null;
        Long diggCount = null;
        Long commentCount = null;
        Long shareCount = null;
        Long collectCount = null;
        if (statistics != null) {
            playCount = parseLongValue(statistics.getObj("play_count"));
            diggCount = parseLongValue(statistics.getObj("digg_count"));
            commentCount = parseLongValue(statistics.getObj("comment_count"));
            shareCount = parseLongValue(statistics.getObj("share_count"));
            collectCount = parseLongValue(statistics.getObj("collect_count"));
        }

        JSONObject video = aweme.getJSONObject("video");
        String cover = null;
        String[] videos = null;
        if (video != null) {
            JSONObject coverObj = video.getJSONObject("cover");
            if (coverObj != null) {
                JSONArray urlList = coverObj.getJSONArray("url_list");
                if (urlList != null && !urlList.isEmpty()) {
                    cover = urlList.getStr(0);
                }
            }
            JSONObject playAddr = video.getJSONObject("play_addr");
            if (playAddr != null) {
                JSONArray urlList = playAddr.getJSONArray("url_list");
                if (urlList != null && !urlList.isEmpty()) {
                    List<String> vlist = new ArrayList<>();
                    for (int i = 0; i < urlList.size(); i++) {
                        vlist.add(urlList.getStr(i));
                    }
                    videos = vlist.toArray(new String[0]);
                }
            }
        }

        JSONArray imgList = null;
        JSONObject imagePostInfo = aweme.getJSONObject("image_post_info");
        List<String> coverImages = new ArrayList<>();
        if (imagePostInfo != null) {
            imgList = imagePostInfo.getJSONArray("images");
            if (imgList != null) {
                for (int i = 0; i < imgList.size(); i++) {
                    JSONObject img = imgList.getJSONObject(i);
                    if (img != null) {
                        JSONObject dl = img.getJSONObject("download_url_list");
                        JSONArray ul = dl != null ? dl.getJSONArray("url_list") : null;
                        if (ul != null && !ul.isEmpty()) {
                            String u = ul.getStr(0);
                            coverImages.add(u);
                            if (cover == null) cover = u;
                        }
                    }
                }
            }
        }

        long hotValue = 0;
        if (playCount != null) hotValue += playCount;
        if (diggCount != null) hotValue += diggCount * 5;
        if (commentCount != null) hotValue += commentCount * 10;
        if (shareCount != null) hotValue += shareCount * 20;

        JSONArray textExtras = aweme.getJSONArray("text_extra");
        StringBuilder tags = new StringBuilder();
        if (textExtras != null) {
            for (int i = 0; i < textExtras.size(); i++) {
                JSONObject te = textExtras.getJSONObject(i);
                if (te != null && te.getInt("type", -1) == 1) {
                    String hashtag = te.getStr("hashtag_name");
                    if (hashtag != null) {
                        if (tags.length() > 0) tags.append(",");
                        tags.append(hashtag);
                    }
                }
            }
        }

        return DataItem.builder()
                .itemId(awemeId)
                .title(desc)
                .url(BASE_URL + "/video/" + awemeId)
                .author(nickname)
                .authorId(uid)
                .coverImage(cover)
                .images(coverImages.toArray(new String[0]))
                .videos(videos)
                .viewCount(playCount)
                .likeCount(diggCount)
                .commentCount(commentCount)
                .shareCount(shareCount)
                .hotValue(hotValue > 0 ? hotValue : null)
                .tags(tags.toString())
                .category("抖音视频")
                .publishTime(parseTimestamp(aweme.getObj("create_time")))
                .rawData(aweme.toString())
                .build();
    }

    private List<DataItem> parse60sViki(JSONObject json) {
        List<DataItem> items = new ArrayList<>();

        JSONArray dataArr = null;
        Object dataObj = json.get("data");
        if (dataObj instanceof JSONArray) {
            dataArr = (JSONArray) dataObj;
        } else if (dataObj instanceof JSONObject) {
            JSONObject d = (JSONObject) dataObj;
            Object listObj = d.get("list");
            if (listObj instanceof JSONArray) {
                dataArr = (JSONArray) listObj;
            } else {
                Object innerDataObj = d.get("data");
                if (innerDataObj instanceof JSONArray) {
                    dataArr = (JSONArray) innerDataObj;
                }
            }
        }

        if (dataArr == null || dataArr.isEmpty()) return items;

        for (int i = 0; i < dataArr.size(); i++) {
            try {
                JSONObject item = dataArr.getJSONObject(i);
                String title = item.getStr("title", item.getStr("word", ""));
                if (title == null || title.trim().isEmpty()) continue;

                Long hotValue = item.getLong("hot_value", null);
                if (hotValue == null) hotValue = item.getLong("hot", calculateDefaultHot(i, dataArr.size()));

                String url = item.getStr("link", item.getStr("url", ""));
                if (url == null || url.isEmpty()) url = BASE_URL + "/search/" + encode(title) + "?type=general";

                String cover = item.getStr("cover", "");
                String eventTime = item.getStr("event_time", "");
                Object eventTimeAt = item.getObj("event_time_at");
                Object activeTimeAt = item.getObj("active_time_at");
                String activeTime = item.getStr("active_time", "");

                String word = item.getStr("word", title);
                String sentence = item.getStr("sentence", item.getStr("summary", ""));

                StringBuilder summary = new StringBuilder();
                summary.append("【抖音热榜第").append(i + 1).append("名】").append(title.trim());
                if (sentence != null && !sentence.trim().isEmpty()) {
                    summary.append("。").append(sentence.trim());
                }
                summary.append("。当前热度值: ").append(hotValue != null ? formatHotValue(hotValue) : "暂无数据");
                if (eventTime != null && !eventTime.isEmpty()) {
                    summary.append("。上榜时间: ").append(eventTime);
                }

                StringBuilder content = new StringBuilder(summary);
                content.append("\n\n=== 详细信息 ===\n");
                content.append("标题: ").append(title.trim()).append("\n");
                content.append("排名: 第").append(i + 1).append("名\n");
                content.append("热度: ").append(hotValue != null ? formatHotValue(hotValue) : "暂无").append("\n");
                if (word != null && !word.equals(title)) {
                    content.append("关键词: ").append(word).append("\n");
                }
                if (eventTime != null && !eventTime.isEmpty()) {
                    content.append("上榜时间: ").append(eventTime).append("\n");
                }
                if (activeTime != null && !activeTime.isEmpty()) {
                    content.append("活跃时间: ").append(activeTime).append("\n");
                }
                content.append("来源链接: ").append(url).append("\n");
                if (cover != null && !cover.isEmpty()) {
                    content.append("封面图: ").append(cover).append("\n");
                }

                LocalDateTime publishTime = null;
                if (eventTimeAt != null) {
                    publishTime = parseTimestamp(eventTimeAt);
                }
                if (publishTime == null && eventTime != null && !eventTime.isEmpty()) {
                    publishTime = parseTimestamp(eventTime);
                }

                DataItem di = DataItem.builder()
                        .itemId("douyin_" + (i + 1) + "_" + Math.abs(title.hashCode()))
                        .title(title.trim())
                        .summary(summary.toString())
                        .content(content.toString())
                        .url(url)
                        .coverImage(cover != null && !cover.isEmpty() ? cover : null)
                        .hotValue(hotValue)
                        .hotRank(i + 1)
                        .rank(i + 1)
                        .category("抖音热榜")
                        .tags(word != null && !word.isEmpty() && !word.equals(title) ? word : null)
                        .publishTime(publishTime)
                        .rawData(item.toString())
                        .build();

                if (activeTimeAt != null) {
                    di.putExtra("active_time_at", activeTimeAt);
                }
                if (eventTimeAt != null) {
                    di.putExtra("event_time_at", eventTimeAt);
                }
                di.putExtra("source", "60s.viki.moe");
                di.putExtra("hot_value_formatted", hotValue != null ? formatHotValue(hotValue) : "");

                items.add(di);
            } catch (Exception e) {
                log.warn("[douyin-60s] 解析第{}项异常: {}", i + 1, e.getMessage());
            }
        }
        return items;
    }

    private String formatHotValue(Long value) {
        if (value == null) return "";
        if (value >= 100000000) {
            return String.format("%.2f亿", value / 100000000.0);
        } else if (value >= 10000) {
            return String.format("%.2f万", value / 10000.0);
        }
        return String.valueOf(value);
    }

    private long calculateDefaultHot(int index, int total) {
        double base = 800000.0;
        double decay = Math.pow(0.94, index);
        double jitter = (random.nextDouble() - 0.5) * 80000;
        return Math.max(10000L, (long) (base * decay + jitter));
    }

    @Override
    protected DataItem doParseDetail(JSONObject data, CrawlResponse response) {
        if (data == null) return null;
        JSONObject aweme = data.getByPath("data.aweme_detail", JSONObject.class);
        if (aweme == null) aweme = data.getJSONObject("aweme_detail");
        return parseAwemeItem(aweme);
    }

    private String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
