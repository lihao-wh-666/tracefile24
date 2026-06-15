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
public class BilibiliAdapter extends AbstractPlatformAdapter {

    private static final String PLATFORM_CODE = "bilibili";
    private static final String PLATFORM_NAME = "B站";
    private static final String BASE_URL = "https://www.bilibili.com";

    private static final String HOT_RANK_API = "https://api.bilibili.com/x/web-interface/ranking/v2";
    private static final String POPULAR_API = "https://api.bilibili.com/x/web-interface/popular";
    private static final String VIKI_API = "https://60s.viki.moe/bili";
    private static final String SEARCH_API = "https://api.bilibili.com/x/web-interface/search/type";
    private static final String DETAIL_API = "https://api.bilibili.com/x/web-interface/view";
    private static final String VIDEO_STAT_API = "https://api.bilibili.com/x/web-interface/archive/stat";

    public BilibiliAdapter() {
        super();
        this.authConfig.required = false;
        this.authConfig.sessionCookieName = "SESSDATA";
        this.authConfig.sessionExpireMinutes = 60 * 24 * 30;
        this.antiCrawlConfig.requestIntervalMs = 1500;
        this.antiCrawlConfig.pageIntervalMs = 2500;
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
        return PlatformType.SHORT_VIDEO;
    }

    @Override
    protected String getListApiUrl(int page, int pageSize, String category, String keyword) {
        if (page == 1) {
            return HOT_RANK_API + "?rid=0&type=all";
        } else if (page == 2) {
            return POPULAR_API + "?ps=" + pageSize + "&pn=1";
        } else if (page == 3) {
            return VIKI_API;
        }
        int pn = page - 1;
        return POPULAR_API + "?ps=" + pageSize + "&pn=" + pn;
    }

    @Override
    protected String getDetailApiUrl(String itemId) {
        if (itemId.startsWith("BV")) {
            return DETAIL_API + "?bvid=" + itemId;
        } else if (itemId.startsWith("av") || itemId.startsWith("AV")) {
            return DETAIL_API + "?aid=" + itemId.substring(2);
        }
        return DETAIL_API + "?aid=" + itemId;
    }

    @Override
    protected String getSearchApiUrl(String keyword, int page, int pageSize) {
        try {
            String encoded = java.net.URLEncoder.encode(keyword, "UTF-8");
            return SEARCH_API + "?search_type=video&keyword=" + encoded
                    + "&page=" + page + "&page_size=" + pageSize + "&order=click";
        } catch (Exception e) {
            return SEARCH_API + "?page=" + page;
        }
    }

    @Override
    protected void customizeListRequest(CrawlRequest.CrawlRequestBuilder builder, int page, int pageSize, String category, String keyword) {
        if (page == 3) {
            return;
        }
        builder.header("Host", "api.bilibili.com")
                .header("Referer", BASE_URL + "/v/popular/rank/all")
                .header("Origin", "https://www.bilibili.com")
                .header("Accept", "application/json, text/plain, */*");
    }

    @Override
    protected void customizeSearchRequest(CrawlRequest.CrawlRequestBuilder builder, String keyword, int page, int pageSize) {
        builder.header("Host", "api.bilibili.com")
                .header("Referer", BASE_URL + "/search?keyword=" + encode(keyword));
    }

    @Override
    protected void customizeDetailRequest(CrawlRequest.CrawlRequestBuilder builder, String itemId, String url) {
        builder.header("Host", "api.bilibili.com")
                .header("Referer", BASE_URL + "/video/" + itemId);
    }

    @Override
    protected List<DataItem> doParseList(JSONObject data, CrawlResponse response) {
        List<DataItem> items = new ArrayList<>();
        if (data == null) return items;

        String requestUrl = response.getRequest() != null ? response.getRequest().getUrl() : "";

        if (requestUrl.contains("60s.viki.moe") || requestUrl.contains("viki.moe")) {
            List<DataItem> vikiItems = parseVikiApi(data);
            if (!vikiItems.isEmpty()) {
                return vikiItems;
            }
            log.warn("[bilibili] viki.moe解析为空，回退到官方API解析");
        }

        if (data.getInt("code", -1) != 0) {
            log.warn("[bilibili] 接口返回错误: code={} msg={}",
                    data.getInt("code"),
                    data.getStr("message"));
            return items;
        }

        JSONObject result = data.getJSONObject("data");
        JSONArray list = null;
        if (result != null) {
            list = result.getJSONArray("list");
            if (list == null) list = result.getJSONArray("result");
        }

        if (list == null || list.isEmpty()) {
            log.warn("[bilibili] 未找到列表数据");
            return items;
        }

        int rank = 0;
        for (int i = 0; i < list.size(); i++) {
            try {
                JSONObject item = list.getJSONObject(i);
                DataItem di = parseVideoItem(item);
                if (di != null) {
                    if (di.getHotRank() == null) {
                        di.setHotRank(++rank);
                    } else {
                        rank = di.getHotRank();
                    }
                    items.add(di);
                }
            } catch (Exception e) {
                log.warn("[bilibili] 解析第{}项异常: {}", i, e.getMessage());
            }
        }
        return items;
    }

    private List<DataItem> parseVikiApi(JSONObject json) {
        List<DataItem> items = new ArrayList<>();

        Integer status = json.getInt("status");
        if (status != null && status != 200) {
            log.warn("[bilibili-viki] 接口状态码异常: {}", status);
            return items;
        }

        JSONArray dataArray = null;
        Object dataObj = json.get("data");
        if (dataObj instanceof JSONArray) {
            dataArray = (JSONArray) dataObj;
        } else if (dataObj instanceof JSONObject) {
            JSONObject d = (JSONObject) dataObj;
            Object listObj = d.get("list");
            if (listObj instanceof JSONArray) {
                dataArray = (JSONArray) listObj;
            } else {
                Object innerDataObj = d.get("data");
                if (innerDataObj instanceof JSONArray) {
                    dataArray = (JSONArray) innerDataObj;
                }
            }
        }

        if (dataArray == null || dataArray.isEmpty()) return items;

        for (int i = 0; i < dataArray.size(); i++) {
            try {
                JSONObject item = dataArray.getJSONObject(i);
                String keyword = item.getStr("keyword", "");
                String showName = item.getStr("show_name", "");
                String title = showName != null && !showName.isEmpty() ? showName : keyword;
                if (title == null || title.trim().isEmpty()) continue;

                Integer position = item.getInt("position", i + 1);
                String hotId = item.getStr("hot_id", "");
                String icon = item.getStr("icon", "");

                String url;
                try {
                    url = BASE_URL + "/search?keyword=" + java.net.URLEncoder.encode(keyword, "UTF-8");
                } catch (Exception e) {
                    url = BASE_URL + "/search?keyword=" + keyword;
                }

                DataItem di = DataItem.builder()
                        .itemId(hotId != null && !hotId.isEmpty() ? hotId : "bili_hot_" + (i + 1))
                        .title(title.trim())
                        .url(url)
                        .hotRank(position != null ? position : i + 1)
                        .coverImage(icon != null && !icon.isEmpty() ? icon : null)
                        .category("B站热搜")
                        .rawData(item.toString())
                        .build();
                items.add(di);
            } catch (Exception e) {
                log.warn("[bilibili-viki] 解析第{}项异常: {}", i + 1, e.getMessage());
            }
        }
        return items;
    }

    private DataItem parseVideoItem(JSONObject item) {
        String bvid = item.getStr("bvid", "");
        String aid = item.getStr("aid", item.getStr("id", ""));
        String title = item.getStr("title", "");
        if (title.isEmpty() && (bvid.isEmpty() && aid.isEmpty())) return null;

        String desc = item.getStr("description", item.getStr("desc", ""));
        String pic = item.getStr("pic", item.getStr("thumbnail", ""));
        if (pic != null && !pic.startsWith("http")) {
            pic = "https:" + pic;
        }

        JSONObject owner = item.getJSONObject("owner");
        String upName = null;
        String mid = null;
        if (owner != null) {
            upName = owner.getStr("name");
            mid = owner.getStr("mid");
        }

        JSONObject stat = item.getJSONObject("stat");
        Long view = null;
        Long danmaku = null;
        Long reply = null;
        Long favorite = null;
        Long coin = null;
        Long share = null;
        Long like = null;
        if (stat != null) {
            view = parseLongValue(stat.getObj("view"));
            danmaku = parseLongValue(stat.getObj("danmaku"));
            reply = parseLongValue(stat.getObj("reply"));
            favorite = parseLongValue(stat.getObj("favorite"));
            coin = parseLongValue(stat.getObj("coin"));
            share = parseLongValue(stat.getObj("share"));
            like = parseLongValue(stat.getObj("like"));
        }

        if (view == null) view = parseLongValue(item.getObj("play"));
        if (danmaku == null) danmaku = parseLongValue(item.getObj("video_review"));
        if (reply == null) reply = parseLongValue(item.getObj("review"));

        String tname = item.getStr("tname", item.getStr("typename", ""));
        Integer rank = item.getInt("rank");
        Integer duration = item.getInt("duration");
        String pubLocation = item.getStr("pub_location", "");

        String shortLink = item.getStr("short_link_v2", item.getStr("short_link", ""));
        String url = !shortLink.isEmpty() ? shortLink : BASE_URL + "/video/" + (bvid.isEmpty() ? "av" + aid : bvid);

        long hotValue = 0;
        if (view != null) hotValue += view;
        if (danmaku != null) hotValue += danmaku * 3;
        if (reply != null) hotValue += reply * 5;
        if (favorite != null) hotValue += favorite * 8;
        if (coin != null) hotValue += coin * 10;
        if (like != null) hotValue += like * 4;
        if (share != null) hotValue += share * 15;

        List<String> tagList = new ArrayList<>();
        if (tname != null && !tname.isEmpty()) tagList.add(tname);
        if (pubLocation != null && !pubLocation.isEmpty()) tagList.add(pubLocation);

        DataItem di = DataItem.builder()
                .itemId(bvid.isEmpty() ? aid : bvid)
                .title(title)
                .content(desc)
                .url(url)
                .author(upName)
                .authorId(mid)
                .coverImage(pic)
                .viewCount(view)
                .commentCount(reply)
                .likeCount(like != null ? like : (danmaku != null && like == null ? danmaku : null))
                .shareCount(share)
                .hotValue(hotValue > 0 ? hotValue : null)
                .hotRank(rank)
                .category(tname.isEmpty() ? "B站视频" : tname)
                .tags(String.join(",", tagList))
                .publishTime(parseTimestamp(item.getObj("pubdate", item.getObj("created", item.getObj("senddate")))))
                .rawData(item.toString())
                .build();

        di.putExtra("aid", aid);
        di.putExtra("bvid", bvid);
        di.putExtra("danmaku", danmaku);
        di.putExtra("favorite", favorite);
        di.putExtra("coin", coin);
        di.putExtra("duration", duration);

        return di;
    }

    @Override
    protected DataItem doParseDetail(JSONObject data, CrawlResponse response) {
        if (data == null || data.getInt("code", -1) != 0) return null;
        JSONObject item = data.getJSONObject("data");
        if (item == null) return null;
        DataItem di = parseVideoItem(item);

        JSONArray pages = item.getJSONArray("pages");
        if (pages != null && !pages.isEmpty()) {
            di.putMetadata("total_pages", pages.size());
        }
        JSONArray tags = null;
        JSONObject tagData = data.getByPath("tags", JSONObject.class);
        if (tagData == null) {
            try {
                tags = item.getJSONArray("tag");
            } catch (Exception ignored) {
                tags = null;
            }
        }
        if (tags != null) {
            List<String> tlist = new ArrayList<>();
            for (int i = 0; i < tags.size(); i++) {
                JSONObject t = tags.getJSONObject(i);
                if (t != null) {
                    String name = t.getStr("tag_name", t.getStr("name", ""));
                    if (!name.isEmpty()) tlist.add(name);
                }
            }
            if (!tlist.isEmpty()) {
                di.setTags(String.join(",", tlist));
            }
        }
        return di;
    }

    private String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
