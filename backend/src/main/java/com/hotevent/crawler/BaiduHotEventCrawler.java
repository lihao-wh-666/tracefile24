package com.hotevent.crawler;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.hotevent.entity.HotEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class BaiduHotEventCrawler extends AbstractHotEventCrawler {

    @Value("${hot-event.crawler.sources:weibo,zhihu,baidu}")
    private List<String> enabledSources;

    @Override
    public String getSourceName() {
        return "baidu";
    }

    @Override
    protected List<DataEndpoint> getEndpoints() {
        return Arrays.asList(
                new DataEndpoint(
                        "https://60s.viki.moe/v2/baidu/hot",
                        "https://60s.viki.moe/",
                        "application/json",
                        true,
                        "第三方60s聚合API"
                ),
                new DataEndpoint(
                        "https://tenapi.cn/v2/baiduhot",
                        "https://tenapi.cn/",
                        "application/json",
                        true,
                        "TenAPI百度热搜"
                ),
                new DataEndpoint(
                        "https://top.baidu.com/api/board?platform=wise&tab=realtime",
                        "https://top.baidu.com/",
                        "application/json",
                        true,
                        "百度官方热榜API"
                )
        );
    }

    @Override
    protected List<HotEvent> parseJsonResponse(JSONObject json, int endpointIndex) {
        List<HotEvent> events = new ArrayList<>();
        try {
            switch (endpointIndex) {
                case 0:
                    events = parse60sViki(json);
                    if (!events.isEmpty()) break;
                case 1:
                    events = parseTenApi(json);
                    if (!events.isEmpty()) break;
                case 2:
                    events = parseBaiduOfficial(json);
                    break;
                default:
                    log.warn("[baidu] 未知数据源索引: {}", endpointIndex);
            }
        } catch (Exception e) {
            log.error("[baidu] 解析响应失败，数据源索引: {}", endpointIndex, e);
        }
        return events;
    }

    private List<HotEvent> parseBaiduOfficial(JSONObject json) {
        List<HotEvent> events = new ArrayList<>();
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
            if (itemArr == null) {
                itemArr = data.getJSONArray("content");
            }
            if (itemArr == null) {
                itemArr = data.getJSONArray("list");
            }
            if (itemArr == null) {
                Object v = data.get("list");
                if (v instanceof JSONObject) {
                    try {
                        itemArr = ((JSONObject) v).getJSONArray("content");
                    } catch (Exception ignored) {}
                }
            }
        }
        if (itemArr == null) {
            itemArr = json.getJSONArray("content");
        }
        if (itemArr == null) {
            itemArr = json.getJSONArray("list");
        }
        if (itemArr == null) {
            itemArr = json.getJSONArray("cards");
        }
        if (itemArr == null || itemArr.isEmpty()) {
            log.warn("[baidu-官方] 未找到任何列表数组，JSON顶层keys: {}", json.keySet());
            return events;
        }
        log.info("[baidu-官方] 找到数组，长度: {}", itemArr.size());

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
                if (desc == null || desc.isEmpty()) {
                    desc = item.getStr("description", "");
                }

                HotEvent event = createHotEvent(title.trim(), url, hotValue, i + 1);
                event.setCategory("综合");
                if (desc != null && !desc.isEmpty()) event.setDescription(desc.trim());
                events.add(event);
            } catch (Exception e) {
                log.warn("[baidu-官方] 解析第{}项失败: {}", i + 1, e.getMessage());
            }
        }
        return events;
    }

    private List<HotEvent> parse60sViki(JSONObject json) {
        List<HotEvent> events = new ArrayList<>();
        JSONArray data = json.getJSONArray("data");

        if (data == null && json.containsKey("data")) {
            Object d = json.get("data");
            if (d instanceof JSONObject) {
                data = ((JSONObject) d).getJSONArray("list");
                if (data == null) data = ((JSONObject) d).getJSONArray("data");
            }
        }

        if (data == null || data.isEmpty()) {
            log.warn("[baidu-60s] 未找到数据数组");
            return events;
        }

        for (int i = 0; i < data.size(); i++) {
            try {
                JSONObject item = data.getJSONObject(i);
                String title = item.getStr("title", item.getStr("word", ""));
                if (title == null || title.trim().isEmpty()) continue;

                Long hot = item.getLong("hot", null);
                if (hot == null) hot = item.getLong("hotValue", item.getLong("num", calculateDefaultHot(i, data.size())));

                String url = item.getStr("url", "");
                if (url == null || url.isEmpty()) url = buildBaiduSearchUrl(title);

                HotEvent event = createHotEvent(title.trim(), url, hot, i + 1);
                event.setCategory("综合");
                String desc = item.getStr("desc", "");
                if (desc != null && !desc.isEmpty()) event.setDescription(desc);
                events.add(event);
            } catch (Exception e) {
                log.warn("[baidu-60s] 解析第{}项失败: {}", i + 1, e.getMessage());
            }
        }
        return events;
    }

    private List<HotEvent> parseTenApi(JSONObject json) {
        List<HotEvent> events = new ArrayList<>();
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
        if (list == null || list.isEmpty()) {
            log.warn("[baidu-TenAPI] 未找到列表数据");
            return events;
        }

        for (int i = 0; i < list.size(); i++) {
            try {
                JSONObject item = list.getJSONObject(i);
                String title = item.getStr("name", item.getStr("title", item.getStr("word", "")));
                if (title == null || title.trim().isEmpty()) continue;

                Long hot = item.getLong("hot", null);
                if (hot == null) hot = item.getLong("hotValue", item.getLong("num", calculateDefaultHot(i, list.size())));

                String url = item.getStr("url", "");
                if (url == null || url.isEmpty()) url = buildBaiduSearchUrl(title);

                HotEvent event = createHotEvent(title.trim(), url, hot, i + 1);
                event.setCategory("综合");
                events.add(event);
            } catch (Exception e) {
                log.warn("[baidu-TenAPI] 解析第{}项失败: {}", i + 1, e.getMessage());
            }
        }
        return events;
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
    public List<HotEvent> crawl() throws Exception {
        return executeCrawl();
    }

    @Override
    public boolean isEnabled() {
        return enabledSources.contains("baidu");
    }
}
