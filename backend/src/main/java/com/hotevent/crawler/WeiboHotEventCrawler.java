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
public class WeiboHotEventCrawler extends AbstractHotEventCrawler {

    @Value("${hot-event.crawler.sources:weibo,zhihu,baidu}")
    private List<String> enabledSources;

    @Override
    public String getSourceName() {
        return "weibo";
    }

    @Override
    protected List<DataEndpoint> getEndpoints() {
        return Arrays.asList(
                new DataEndpoint(
                        "https://60s.viki.moe/v2/weibo",
                        "https://60s.viki.moe/",
                        "application/json",
                        true,
                        "第三方60s聚合API"
                ),
                new DataEndpoint(
                        "https://tenapi.cn/v2/weibohot",
                        "https://tenapi.cn/",
                        "application/json",
                        true,
                        "TenAPI微博热搜"
                ),
                new DataEndpoint(
                        "https://weibo.com/ajax/side/hotSearch",
                        "https://weibo.com/",
                        "application/json",
                        true,
                        "微博官方Ajax接口"
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
                    events = parseWeiboOfficial(json);
                    break;
                default:
                    log.warn("[weibo] 未知数据源索引: {}", endpointIndex);
            }
        } catch (Exception e) {
            log.error("[weibo] 解析响应失败，数据源索引: {}", endpointIndex, e);
        }
        return events;
    }

    private List<HotEvent> parseWeiboOfficial(JSONObject json) {
        List<HotEvent> events = new ArrayList<>();
        JSONArray realtime = null;

        Object dataObj = json.get("data");
        if (dataObj instanceof JSONObject) {
            JSONObject data = (JSONObject) dataObj;
            realtime = data.getJSONArray("realtime");
        }
        if (realtime == null) {
            realtime = json.getJSONArray("realtime");
        }

        if (realtime == null || realtime.isEmpty()) {
            log.warn("[weibo-官方] 未找到realtime数组");
            return events;
        }

        for (int i = 0; i < realtime.size(); i++) {
            try {
                JSONObject item = realtime.getJSONObject(i);
                String word = item.getStr("word", "");
                if (word == null || word.trim().isEmpty()) continue;

                Long num = item.getLong("num", null);
                if (num == null) {
                    String rawHot = item.getStr("raw_hot", "");
                    num = parseWeiboHotValue(rawHot);
                }
                if (num == null) {
                    num = calculateDefaultHot(i, realtime.size());
                }

                String url = buildWeiboSearchUrl(word);
                String note = item.getStr("note", "");
                String title = note != null && !note.isEmpty() ? note : word;

                String category = item.getStr("category", "");
                if (category == null || category.isEmpty()) {
                    category = "社会";
                }

                HotEvent event = createHotEvent(title.trim(), url, num, i + 1);
                event.setCategory(category);
                String label = item.getStr("label_name", "");
                if (label != null && !label.isEmpty()) {
                    event.setDescription("标签: " + label);
                }
                events.add(event);
            } catch (Exception e) {
                log.warn("[weibo-官方] 解析第{}项失败: {}", i + 1, e.getMessage());
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
            log.warn("[weibo-60s] 未找到数据数组");
            return events;
        }

        for (int i = 0; i < data.size(); i++) {
            try {
                JSONObject item = data.getJSONObject(i);
                String title = item.getStr("title", item.getStr("word", ""));
                if (title == null || title.trim().isEmpty()) continue;

                Long hot = item.getLong("hot", null);
                if (hot == null) hot = item.getLong("num", calculateDefaultHot(i, data.size()));

                String url = item.getStr("url", "");
                if (url == null || url.isEmpty()) url = buildWeiboSearchUrl(title);

                HotEvent event = createHotEvent(title.trim(), url, hot, i + 1);
                event.setCategory("社会");
                String desc = item.getStr("desc", "");
                if (desc != null && !desc.isEmpty()) event.setDescription(desc);
                events.add(event);
            } catch (Exception e) {
                log.warn("[weibo-60s] 解析第{}项失败: {}", i + 1, e.getMessage());
            }
        }
        return events;
    }

    private List<HotEvent> parseTenApi(JSONObject json) {
        List<HotEvent> events = new ArrayList<>();
        Integer code = json.getInt("code");
        if (code != null && code != 200 && code != 0) {
            log.warn("[weibo-TenAPI] 业务状态码异常: {}", code);
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
            log.warn("[weibo-TenAPI] 未找到列表数据");
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
                if (url == null || url.isEmpty()) url = buildWeiboSearchUrl(title);

                HotEvent event = createHotEvent(title.trim(), url, hot, i + 1);
                event.setCategory("社会");
                events.add(event);
            } catch (Exception e) {
                log.warn("[weibo-TenAPI] 解析第{}项失败: {}", i + 1, e.getMessage());
            }
        }
        return events;
    }

    private Long parseWeiboHotValue(String rawHot) {
        if (rawHot == null || rawHot.isEmpty()) return null;
        try {
            String s = rawHot.replace(",", "").replace("，", "").trim();

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

    private String buildWeiboSearchUrl(String keyword) {
        try {
            String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8.name());
            return "https://s.weibo.com/weibo?q=%23" + encoded + "%23";
        } catch (Exception e) {
            return "https://s.weibo.com/top/summary";
        }
    }

    private Long calculateDefaultHot(int index, int total) {
        double base = 800000.0;
        double decay = Math.pow(0.94, index);
        double jitter = (random.nextDouble() - 0.5) * 80000;
        return Math.max(10000L, (long) (base * decay + jitter));
    }

    @Override
    public List<HotEvent> crawl() throws Exception {
        return executeCrawl();
    }

    @Override
    public boolean isEnabled() {
        return enabledSources.contains("weibo");
    }
}
