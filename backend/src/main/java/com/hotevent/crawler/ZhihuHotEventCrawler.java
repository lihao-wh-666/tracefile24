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
public class ZhihuHotEventCrawler extends AbstractHotEventCrawler {

    @Value("${hot-event.crawler.sources:weibo,zhihu,baidu}")
    private List<String> enabledSources;

    @Override
    public String getSourceName() {
        return "zhihu";
    }

    @Override
    protected List<DataEndpoint> getEndpoints() {
        return Arrays.asList(
                new DataEndpoint(
                        "https://60s.viki.moe/v2/zhihu",
                        "https://60s.viki.moe/",
                        "application/json",
                        true,
                        "第三方60s聚合API"
                ),
                new DataEndpoint(
                        "https://tenapi.cn/v2/zhihuhot",
                        "https://tenapi.cn/",
                        "application/json",
                        true,
                        "TenAPI知乎热榜"
                ),
                new DataEndpoint(
                        "https://www.zhihu.com/api/v3/feed/topstory/hot-list-web?limit=50",
                        "https://www.zhihu.com/hot",
                        "application/json",
                        true,
                        "知乎官方热榜API"
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
                    events = parseZhihuOfficial(json);
                    break;
                default:
                    log.warn("[zhihu] 未知数据源索引: {}", endpointIndex);
            }
        } catch (Exception e) {
            log.error("[zhihu] 解析响应失败，数据源索引: {}", endpointIndex, e);
        }
        return events;
    }

    private List<HotEvent> parseZhihuOfficial(JSONObject json) {
        List<HotEvent> events = new ArrayList<>();
        JSONArray dataArr = json.getJSONArray("data");
        if (dataArr == null) dataArr = json.getJSONArray("list");

        if (dataArr == null || dataArr.isEmpty()) {
            log.warn("[zhihu-官方] 未找到data数组");
            return events;
        }

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
                        url = "https://www.zhihu.com/question/" + id;
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

                HotEvent event = createHotEvent(title.trim(), url, hotVal, i + 1);
                event.setCategory("知识");
                if (excerpt != null && !excerpt.trim().isEmpty()) {
                    event.setDescription(excerpt.trim());
                }

                try {
                    JSONArray thumbnailArr = item.getJSONArray("thumbnail");
                    if (thumbnailArr != null && !thumbnailArr.isEmpty()) {
                        String thumb = thumbnailArr.getStr(0, "");
                        if (thumb != null && !thumb.isEmpty()) event.setImageUrl(thumb);
                    } else {
                        JSONObject thumbnailObj = item.getJSONObject("thumbnail");
                        if (thumbnailObj != null) {
                            String thumb = thumbnailObj.getStr("url", "");
                            if (thumb != null && !thumb.isEmpty()) event.setImageUrl(thumb);
                        }
                    }
                } catch (Exception ignored) {}

                events.add(event);
            } catch (Exception e) {
                log.warn("[zhihu-官方] 解析第{}项失败: {}", i + 1, e.getMessage());
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
            log.warn("[zhihu-60s] 未找到数据数组");
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
                if (url == null || url.isEmpty()) url = buildZhihuSearchUrl(title);

                HotEvent event = createHotEvent(title.trim(), url, hot, i + 1);
                event.setCategory("知识");
                String desc = item.getStr("desc", "");
                if (desc != null && !desc.isEmpty()) event.setDescription(desc);
                events.add(event);
            } catch (Exception e) {
                log.warn("[zhihu-60s] 解析第{}项失败: {}", i + 1, e.getMessage());
            }
        }
        return events;
    }

    private List<HotEvent> parseTenApi(JSONObject json) {
        List<HotEvent> events = new ArrayList<>();
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
        if (list == null || list.isEmpty()) {
            log.warn("[zhihu-TenAPI] 未找到列表数据");
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
                if (url == null || url.isEmpty()) url = buildZhihuSearchUrl(title);

                HotEvent event = createHotEvent(title.trim(), url, hot, i + 1);
                event.setCategory("知识");
                events.add(event);
            } catch (Exception e) {
                log.warn("[zhihu-TenAPI] 解析第{}项失败: {}", i + 1, e.getMessage());
            }
        }
        return events;
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
            return "https://www.zhihu.com/search?q=" + encoded + "&type=content";
        } catch (Exception e) {
            return "https://www.zhihu.com/hot";
        }
    }

    private Long calculateDefaultHot(int index, int total) {
        double base = 400000.0;
        double decay = Math.pow(0.96, index);
        double jitter = (random.nextDouble() - 0.5) * 40000;
        return Math.max(10000L, (long) (base * decay + jitter));
    }

    @Override
    public List<HotEvent> crawl() throws Exception {
        return executeCrawl();
    }

    @Override
    public boolean isEnabled() {
        return enabledSources.contains("zhihu");
    }
}
