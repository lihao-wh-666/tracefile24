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
    private static final String DEFAULT_COOKIE = "buvid3=infoc; b_nut=1718438400; CURRENT_FNVAL=4048;";

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
        }
        return POPULAR_API + "?ps=" + pageSize + "&pn=" + page;
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
        String referer;
        if (page == 1) {
            referer = BASE_URL + "/v/popular/rank/all";
        } else {
            referer = BASE_URL + "/v/popular/all/";
        }
        builder.header("Host", "api.bilibili.com")
                .header("Referer", referer)
                .header("Origin", "https://www.bilibili.com")
                .header("Accept", "application/json, text/plain, */*")
                .header("Cookie", DEFAULT_COOKIE);
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
                int rank = position != null ? position : i + 1;
                String hotId = item.getStr("hot_id", "");
                String icon = item.getStr("icon", "");
                Integer wordType = item.getInt("word_type");
                String resourceId = item.getStr("resource_id", "");
                Boolean showLiveIcon = item.getBool("show_live_icon", false);
                String isCommercial = item.getStr("is_commercial", "");

                Long hotValue = calculateDefaultHot(rank - 1, dataArray.size());

                String typeLabel = resolveWordType(wordType);

                String url;
                String searchKeyword = keyword != null && !keyword.isEmpty() ? keyword : title;
                try {
                    url = BASE_URL + "/search?keyword=" + java.net.URLEncoder.encode(searchKeyword, "UTF-8");
                } catch (Exception e) {
                    url = BASE_URL + "/search?keyword=" + searchKeyword;
                }

                if (resourceId != null && !resourceId.isEmpty()) {
                    url = BASE_URL + "/video/" + resourceId;
                }

                String safeIcon = null;
                if (icon != null && !icon.isEmpty()) {
                    safeIcon = icon.startsWith("http://") ? icon.replaceFirst("http://", "https://") : icon;
                }

                StringBuilder summary = new StringBuilder();
                summary.append("【B站热搜第").append(rank).append("名】").append(title.trim());
                if (typeLabel != null) {
                    summary.append(" [").append(typeLabel).append("]");
                }
                summary.append("。当前热度约: ").append(formatHotValue(hotValue));
                if ("1".equals(isCommercial)) {
                    summary.append("。注意：本条为商业推广内容");
                }

                StringBuilder content = new StringBuilder(summary);
                content.append("\n\n=== 详细信息 ===\n");
                content.append("标题: ").append(title.trim()).append("\n");
                content.append("排名: 第").append(rank).append("名\n");
                content.append("预估热度: ").append(formatHotValue(hotValue)).append("\n");
                if (keyword != null && !keyword.isEmpty() && !keyword.equals(title)) {
                    content.append("搜索关键词: ").append(keyword).append("\n");
                }
                if (typeLabel != null) {
                    content.append("内容类型: ").append(typeLabel).append("\n");
                }
                if (hotId != null && !hotId.isEmpty()) {
                    content.append("热搜ID: ").append(hotId).append("\n");
                }
                if (resourceId != null && !resourceId.isEmpty()) {
                    content.append("关联资源: ").append(resourceId).append("\n");
                }
                if (showLiveIcon) {
                    content.append("直播状态: 正在直播\n");
                }
                if ("1".equals(isCommercial)) {
                    content.append("商业推广: 是\n");
                }
                content.append("来源链接: ").append(url).append("\n");
                if (safeIcon != null) {
                    content.append("图标: ").append(safeIcon).append("\n");
                }

                List<String> tagList = new ArrayList<>();
                tagList.add("B站热搜");
                if (typeLabel != null) tagList.add(typeLabel);
                if (showLiveIcon) tagList.add("直播");

                DataItem di = DataItem.builder()
                        .itemId(hotId != null && !hotId.isEmpty() ? "bili_" + hotId : "bili_hot_" + rank + "_" + Math.abs(title.hashCode()))
                        .title(title.trim())
                        .summary(summary.toString())
                        .content(content.toString())
                        .url(url)
                        .hotValue(hotValue)
                        .hotRank(rank)
                        .rank(rank)
                        .coverImage(safeIcon)
                        .category("B站热搜")
                        .tags(String.join(",", tagList))
                        .rawData(item.toString())
                        .build();

                di.putExtra("source", "60s.viki.moe");
                di.putExtra("word_type", wordType);
                di.putExtra("word_type_label", typeLabel);
                di.putExtra("hot_value_formatted", formatHotValue(hotValue));
                if (resourceId != null && !resourceId.isEmpty()) {
                    di.putExtra("resource_id", resourceId);
                }
                if (showLiveIcon) {
                    di.putExtra("is_live", true);
                }
                di.putExtra("is_commercial", "1".equals(isCommercial));

                items.add(di);
            } catch (Exception e) {
                log.warn("[bilibili-viki] 解析第{}项异常: {}", i + 1, e.getMessage());
            }
        }
        return items;
    }

    private String resolveWordType(Integer wordType) {
        if (wordType == null) return null;
        switch (wordType) {
            case 1: return "新上榜";
            case 2: return "热门话题";
            case 3: return "热议话题";
            case 4: return "娱乐新闻";
            case 5: return "热点资讯";
            case 6: return "知识科普";
            case 7: return "直播内容";
            case 8: return "社会新闻";
            case 9: return "趣味内容";
            case 10: return "游戏内容";
            case 11: return "影视内容";
            case 12: return "生活日常";
            case 13: return "科技数码";
            case 14: return "美食内容";
            default: return "综合内容";
        }
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
        if (pic != null && pic.startsWith("http://")) {
            pic = pic.replaceFirst("http://", "https://");
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
        String dynamic = item.getStr("dynamic", "");

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

        if (hotValue <= 0) {
            hotValue = calculateDefaultHot(rank != null ? rank - 1 : 0, 100);
        }

        List<String> tagList = new ArrayList<>();
        tagList.add("B站视频");
        if (tname != null && !tname.isEmpty()) tagList.add(tname);
        if (pubLocation != null && !pubLocation.isEmpty()) tagList.add(pubLocation);

        String rankStr = rank != null ? "第" + rank + "名" : "";

        StringBuilder summary = new StringBuilder();
        if (rank != null) {
            summary.append("【B站排行榜").append(rankStr).append("】");
        }
        summary.append(title.trim());
        if (!tname.isEmpty()) {
            summary.append(" [").append(tname).append("]");
        }
        summary.append("。UP主: ").append(upName != null && !upName.isEmpty() ? upName : "未知");
        summary.append("。播放量: ").append(view != null ? formatHotValue(view) : "暂无");
        summary.append("，点赞: ").append(like != null ? formatHotValue(like) : "暂无");
        if (desc != null && !desc.trim().isEmpty()) {
            String shortDesc = desc.trim();
            if (shortDesc.length() > 100) shortDesc = shortDesc.substring(0, 100) + "...";
            summary.append("。简介: ").append(shortDesc);
        }

        StringBuilder content = new StringBuilder(summary);
        content.append("\n\n=== 视频详细信息 ===\n");
        content.append("标题: ").append(title.trim()).append("\n");
        if (rank != null) {
            content.append("排行榜位置: ").append(rankStr).append("\n");
        }
        content.append("分类: ").append(tname.isEmpty() ? "综合" : tname).append("\n");
        if (upName != null && !upName.isEmpty()) {
            content.append("UP主: ").append(upName);
            if (mid != null && !mid.isEmpty()) {
                content.append(" (UID: ").append(mid).append(")");
            }
            content.append("\n");
        }
        if (pubLocation != null && !pubLocation.isEmpty()) {
            content.append("发布地点: ").append(pubLocation).append("\n");
        }
        content.append("\n--- 数据统计 ---\n");
        content.append("播放量: ").append(view != null ? formatHotValue(view) : "暂无").append("\n");
        content.append("弹幕数: ").append(danmaku != null ? formatHotValue(danmaku) : "暂无").append("\n");
        content.append("评论数: ").append(reply != null ? formatHotValue(reply) : "暂无").append("\n");
        content.append("点赞数: ").append(like != null ? formatHotValue(like) : "暂无").append("\n");
        content.append("投币数: ").append(coin != null ? formatHotValue(coin) : "暂无").append("\n");
        content.append("收藏数: ").append(favorite != null ? formatHotValue(favorite) : "暂无").append("\n");
        content.append("分享数: ").append(share != null ? formatHotValue(share) : "暂无").append("\n");
        content.append("综合热度: ").append(formatHotValue(hotValue)).append("\n");
        if (duration != null && duration > 0) {
            int min = duration / 60;
            int sec = duration % 60;
            content.append("视频时长: ").append(min).append("分").append(sec).append("秒\n");
        }
        if (desc != null && !desc.trim().isEmpty()) {
            content.append("\n--- 视频简介 ---\n").append(desc.trim()).append("\n");
        }
        if (dynamic != null && !dynamic.trim().isEmpty()) {
            content.append("\n--- UP主动态 ---\n").append(dynamic.trim()).append("\n");
        }
        content.append("\n--- 链接信息 ---\n");
        content.append("视频地址: ").append(url).append("\n");
        if (!shortLink.isEmpty()) {
            content.append("短链接: ").append(shortLink).append("\n");
        }
        if (!bvid.isEmpty()) {
            content.append("BV号: ").append(bvid).append("\n");
        }
        if (!aid.isEmpty()) {
            content.append("AV号: av").append(aid).append("\n");
        }
        if (pic != null && !pic.isEmpty()) {
            content.append("封面图: ").append(pic).append("\n");
        }

        DataItem di = DataItem.builder()
                .itemId(bvid.isEmpty() ? aid : bvid)
                .title(title.trim())
                .summary(summary.toString())
                .content(content.toString())
                .url(url)
                .author(upName)
                .authorId(mid)
                .coverImage(pic)
                .viewCount(view)
                .commentCount(reply)
                .likeCount(like != null ? like : (danmaku != null && like == null ? danmaku : null))
                .shareCount(share)
                .hotValue(hotValue)
                .hotRank(rank)
                .rank(rank)
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
        di.putExtra("source", "bilibili_official");
        di.putExtra("hot_value_formatted", formatHotValue(hotValue));
        di.putExtra("view_formatted", view != null ? formatHotValue(view) : "");
        di.putExtra("like_formatted", like != null ? formatHotValue(like) : "");

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

    private long calculateDefaultHot(int index, int total) {
        double base = 1000000.0;
        double decay = Math.pow(0.95, index);
        double jitter = (random.nextDouble() - 0.5) * 100000;
        return Math.max(10000L, (long) (base * decay + jitter));
    }

    private String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
