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
import java.util.List;

@Slf4j
@Component
public class XiaohongshuAdapter extends AbstractPlatformAdapter {

    private static final String PLATFORM_CODE = "xiaohongshu";
    private static final String PLATFORM_NAME = "小红书";
    private static final String BASE_URL = "https://www.xiaohongshu.com";

    private static final String FEED_API = "https://edith.xiaohongshu.com/api/sns/web/v1/homefeed";
    private static final String SEARCH_API = "https://edith.xiaohongshu.com/api/sns/web/v1/search/notes";
    private static final String DETAIL_API = "https://edith.xiaohongshu.com/api/sns/web/v1/feed";

    public XiaohongshuAdapter() {
        super();
        this.authConfig.required = true;
        this.authConfig.loginUrl = BASE_URL + "/explore";
        this.authConfig.sessionCookieName = "web_session";
        this.authConfig.sessionExpireMinutes = 60 * 24 * 7;
        this.antiCrawlConfig.requestIntervalMs = 2500;
        this.antiCrawlConfig.pageIntervalMs = 4000;
        this.antiCrawlConfig.useRandomUserAgent = true;
        this.antiCrawlConfig.useRandomDelay = true;
        this.antiCrawlConfig.rotateProxy = true;
        this.antiCrawlConfig.supportJsRender = true;
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
        return BASE_URL;
    }

    @Override
    public PlatformType getPlatformType() {
        return PlatformType.SOCIAL_MEDIA;
    }

    @Override
    protected String getListApiUrl(int page, int pageSize, String category, String keyword) {
        return FEED_API;
    }

    @Override
    protected String getDetailApiUrl(String itemId) {
        return DETAIL_API + "?source_note_id=" + itemId;
    }

    @Override
    protected String getSearchApiUrl(String keyword, int page, int pageSize) {
        return SEARCH_API;
    }

    @Override
    protected void customizeListRequest(CrawlRequest.CrawlRequestBuilder builder, int page, int pageSize, String category, String keyword) {
        builder.method(com.hotevent.crawler.http.HttpMethod.POST)
                .contentType("application/json")
                .body(buildListRequestBody(page, pageSize, category))
                .header("Origin", BASE_URL)
                .header("Referer", BASE_URL + "/explore")
                .header("X-S", "")
                .header("X-T", String.valueOf(System.currentTimeMillis() / 1000));
    }

    @Override
    protected void customizeSearchRequest(CrawlRequest.CrawlRequestBuilder builder, String keyword, int page, int pageSize) {
        builder.method(com.hotevent.crawler.http.HttpMethod.POST)
                .contentType("application/json")
                .body(buildSearchRequestBody(keyword, page, pageSize))
                .header("Origin", BASE_URL)
                .header("Referer", BASE_URL + "/search_result?keyword=" + encode(keyword))
                .header("X-T", String.valueOf(System.currentTimeMillis() / 1000));
    }

    @Override
    protected void customizeDetailRequest(CrawlRequest.CrawlRequestBuilder builder, String itemId, String url) {
        builder.method(com.hotevent.crawler.http.HttpMethod.POST)
                .contentType("application/json")
                .body("{\"source_note_id\":\"" + itemId + "\"}")
                .header("Origin", BASE_URL)
                .header("Referer", BASE_URL + "/explore/" + itemId);
    }

    private String buildListRequestBody(int page, int pageSize, String category) {
        JSONObject body = new JSONObject();
        body.set("cursor_score", (page - 1) * pageSize);
        body.set("num", pageSize);
        JSONArray noteTypes = JSONUtil.parseArray("[\"video\",\"normal\"]");
        body.set("note_types", noteTypes);
        body.set("refresh_type", 3);
        body.set("index", (page - 1) * pageSize + 1);
        if (category != null && !category.isEmpty()) {
            body.set("category", category);
        }
        return body.toString();
    }

    private String buildSearchRequestBody(String keyword, int page, int pageSize) {
        JSONObject body = new JSONObject();
        body.set("keyword", keyword);
        body.set("page", page);
        body.set("page_size", pageSize);
        body.set("search_id", java.util.UUID.randomUUID().toString().replace("-", ""));
        body.set("sort", "general");
        body.set("note_type", 0);
        return body.toString();
    }

    @Override
    protected List<DataItem> doParseList(JSONObject data, CrawlResponse response) {
        List<DataItem> items = new ArrayList<>();
        if (data == null) return items;

        JSONArray itemsArr = null;
        Object successData = data.get("data");
        if (successData instanceof JSONObject) {
            itemsArr = ((JSONObject) successData).getJSONArray("items");
            if (itemsArr == null) itemsArr = ((JSONObject) successData).getJSONArray("notes");
        }
        if (itemsArr == null) itemsArr = data.getJSONArray("items");

        if (itemsArr == null || itemsArr.isEmpty()) {
            return parseNoteIds(data, items);
        }

        int rank = 0;
        for (int i = 0; i < itemsArr.size(); i++) {
            try {
                JSONObject itemObj = itemsArr.getJSONObject(i);
                JSONObject noteCard = itemObj.getJSONObject("note_card");
                if (noteCard == null) noteCard = itemObj;
                DataItem di = parseNoteItem(noteCard);
                if (di != null) {
                    di.setHotRank(++rank);
                    items.add(di);
                }
            } catch (Exception e) {
                log.warn("[xiaohongshu] 解析第{}条笔记异常: {}", i, e.getMessage());
            }
        }
        return items;
    }

    private List<DataItem> parseNoteIds(JSONObject data, List<DataItem> items) {
        JSONArray ids = data.getByPath("data.item_ids", JSONArray.class);
        if (ids == null) return items;
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.getStr(i);
            if (id != null && !id.isEmpty()) {
                items.add(DataItem.builder()
                        .itemId(id)
                        .url(BASE_URL + "/explore/" + id)
                        .build());
            }
        }
        return items;
    }

    private DataItem parseNoteItem(JSONObject noteCard) {
        String noteId = noteCard.getStr("note_id", noteCard.getStr("id", ""));
        String title = noteCard.getStr("display_title", noteCard.getStr("title", ""));
        if (noteId.isEmpty() && title.isEmpty()) return null;

        String cover = noteCard.getStr("cover");
        if (cover == null) {
            JSONObject imageList = noteCard.getJSONObject("image_list");
            if (imageList != null && imageList.containsKey("url_default")) {
                cover = imageList.getStr("url_default");
            }
        }

        JSONObject user = noteCard.getJSONObject("user");
        String nickname = null;
        String userId = null;
        if (user != null) {
            nickname = user.getStr("nickname");
            userId = user.getStr("user_id", user.getStr("red_id"));
        }

        JSONObject interact = noteCard.getJSONObject("interact_info");
        Long likedCount = null;
        Long collectedCount = null;
        Long commentCount = null;
        if (interact != null) {
            likedCount = parseLongValue(interact.getObj("liked_count"));
            collectedCount = parseLongValue(interact.getObj("collected_count"));
            commentCount = parseLongValue(interact.getObj("comment_count"));
        }

        JSONArray tagList = noteCard.getJSONArray("tag_list");
        StringBuilder tags = new StringBuilder();
        if (tagList != null) {
            for (int i = 0; i < tagList.size(); i++) {
                JSONObject tag = tagList.getJSONObject(i);
                if (tag != null) {
                    String tname = tag.getStr("name");
                    if (tname != null) {
                        if (tags.length() > 0) tags.append(",");
                        tags.append(tname);
                    }
                }
            }
        }

        JSONArray imgList = noteCard.getJSONArray("image_list");
        List<String> images = new ArrayList<>();
        if (imgList != null) {
            for (int i = 0; i < imgList.size(); i++) {
                JSONObject img = imgList.getJSONObject(i);
                String url = img != null ? img.getStr("url_default", img.getStr("url")) : null;
                if (url != null) images.add(url);
            }
        }

        JSONObject video = noteCard.getJSONObject("video");
        String[] videos = null;
        if (video != null) {
            String media = video.getByPath("media.stream.h264.0.master_url", String.class);
            if (media == null) media = video.getStr("url");
            if (media != null) videos = new String[]{media};
        }

        long hotValue = 0;
        if (likedCount != null) hotValue += likedCount;
        if (collectedCount != null) hotValue += collectedCount * 2;
        if (commentCount != null) hotValue += commentCount * 3;

        return DataItem.builder()
                .itemId(noteId)
                .title(title)
                .url(BASE_URL + "/explore/" + noteId)
                .author(nickname)
                .authorId(userId)
                .coverImage(cover)
                .images(images.toArray(new String[0]))
                .videos(videos)
                .viewCount(parseLongValue(noteCard.getObj("view_count")))
                .likeCount(likedCount)
                .commentCount(commentCount)
                .shareCount(parseLongValue(noteCard.getObj("share_count")))
                .hotValue(hotValue > 0 ? hotValue : null)
                .tags(tags.toString())
                .category("小红书笔记")
                .publishTime(parseTimestamp(noteCard.getObj("last_update_time", noteCard.getObj("time"))))
                .rawData(noteCard.toString())
                .build();
    }

    @Override
    protected DataItem doParseDetail(JSONObject data, CrawlResponse response) {
        if (data == null) return null;
        JSONObject note = data.getByPath("data.items[0].note_card", JSONObject.class);
        if (note == null) note = data.getByPath("data.items[0]", JSONObject.class);
        if (note == null) note = data;
        return parseNoteItem(note);
    }

    private String encode(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return s;
        }
    }
}
