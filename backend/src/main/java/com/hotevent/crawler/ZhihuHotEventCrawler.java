package com.hotevent.crawler;

import com.hotevent.entity.HotEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    protected String getCrawlUrl() {
        return "https://www.zhihu.com/hot";
    }

    @Override
    protected List<HotEvent> parseHtml(String html) {
        List<HotEvent> events = new ArrayList<>();
        try {
            log.debug("[zhihu] 开始解析知乎热榜HTML，内容长度: {}", html.length());
            var doc = parseDocument(html);

            var selectors = new String[][]{
                    {".HotItem", ".HotItem-content .HotItem-title", ".HotItem-content .HotItem-excerpt", ".HotItem-metrics", "a"},
                    {"[class*='HotItem']", "[class*='HotItem-title']", "[class*='HotItem-excerpt']", "[class*='HotItem-metrics']", "a"},
                    {".HotList-item", ".HotList-itemTitle", ".HotList-itemExcerpt", ".HotList-itemMetrics", "a"},
                    {"section", "h2", "p", "[class*='metrics']", "a"},
                    {"a[href*='question']", "h2", "p", null, "a[href*='question']"}
            };

            boolean parsed = false;
            for (int selectorIdx = 0; selectorIdx < selectors.length && !parsed; selectorIdx++) {
                String[] sel = selectors[selectorIdx];
                var elements = doc.select(sel[0]);
                log.debug("[zhihu] 使用第{}套选择器，匹配元素数量: {}", selectorIdx + 1, elements.size());

                if (!elements.isEmpty()) {
                    for (int i = 0; i < elements.size(); i++) {
                        try {
                            var element = elements.get(i);
                            var titleElement = sel[1] != null ? element.selectFirst(sel[1]) : null;
                            var descElement = sel[2] != null ? element.selectFirst(sel[2]) : null;
                            var hotElement = sel[3] != null ? element.selectFirst(sel[3]) : null;
                            var urlElement = sel[4] != null ? element.selectFirst(sel[4]) : null;

                            if (titleElement != null) {
                                String title = titleElement.text().trim();
                                String href = urlElement != null ? urlElement.attr("href") : "";
                                String url = buildZhihuUrl(href);
                                String description = descElement != null ? descElement.text().trim() : "";
                                Long hotValue = 0L;

                                if (hotElement != null) {
                                    String hotText = hotElement.text();
                                    hotValue = parseHotValue(hotText);
                                }

                                if (title.isEmpty()) {
                                    continue;
                                }

                                if (hotValue == null || hotValue == 0) {
                                    hotValue = calculateDefaultHotValue(i, elements.size());
                                }

                                HotEvent event = createHotEvent(title, url, hotValue, i + 1);
                                event.setDescription(description);
                                event.setCategory("知识");
                                events.add(event);
                            }
                        } catch (Exception e) {
                            log.warn("[zhihu] 解析第{}条记录时出错: {}", i + 1, e.getMessage());
                        }
                    }
                    if (!events.isEmpty()) {
                        parsed = true;
                        log.info("[zhihu] 使用第{}套选择器成功解析{}条记录", selectorIdx + 1, events.size());
                    }
                }
            }

            if (events.isEmpty()) {
                log.warn("[zhihu] 所有选择器均未匹配到元素，尝试使用正则表达式从JSON数据中提取");
                events = parseFromJsonEmbedded(html);
            }

        } catch (Exception e) {
            log.error("[zhihu] 解析知乎热榜HTML失败", e);
        }
        return events;
    }

    private List<HotEvent> parseFromJsonEmbedded(String html) {
        List<HotEvent> events = new ArrayList<>();
        try {
            String[] patterns = new String[]{
                    "\"title\"\\s*:\\s*\"([^\"]+)\".*?\"detailText\"\\s*:\\s*\"([^\"]*)\".*?\"heat\"\\s*:\\s*(\\d+)",
                    "\"title\"\\s*:\\s*\"([^\"]+)\".*?\"excerpt\"\\s*:\\s*\"([^\"]*)\".*?\"heat\"\\s*:\\s*(\\d+)",
                    "\"title\"\\s*:\\s*\"([^\"]+)\".*?\"heatText\"\\s*:\\s*\"([^\"]*)\"",
                    "\"title\"\\s*:\\s*\"([^\"]+)\""
            };

            int patternIdx = 0;
            while (events.isEmpty() && patternIdx < patterns.length) {
                Pattern pattern = Pattern.compile(patterns[patternIdx]);
                Matcher matcher = pattern.matcher(html);
                int count = 0;

                while (matcher.find() && count < 50) {
                    String title = matcher.group(1);
                    if (title != null && !title.trim().isEmpty()
                            && !title.contains("{") && !title.contains("}")) {

                        String description = matcher.groupCount() >= 2 ? matcher.group(2) : "";
                        if (description == null) description = "";

                        Long hotValue;
                        try {
                            hotValue = matcher.groupCount() >= 3
                                    ? Long.parseLong(matcher.group(3))
                                    : calculateDefaultHotValue(count, 50);
                        } catch (Exception e) {
                            hotValue = calculateDefaultHotValue(count, 50);
                        }

                        HotEvent event = createHotEvent(
                                title.trim(),
                                "https://www.zhihu.com/search?q=" + title.trim().replace(" ", "%20"),
                                hotValue,
                                count + 1
                        );
                        event.setDescription(description.trim());
                        event.setCategory("知识");
                        events.add(event);
                        count++;
                    }
                }
                patternIdx++;
            }
            log.info("[zhihu] 通过JSON正则提取到{}条记录", events.size());
        } catch (Exception e) {
            log.error("[zhihu] 正则解析失败", e);
        }
        return events;
    }

    private String buildZhihuUrl(String href) {
        if (href == null || href.isEmpty()) {
            return "https://www.zhihu.com/hot";
        }
        if (href.startsWith("http")) {
            return href;
        }
        if (href.startsWith("//")) {
            return "https:" + href;
        }
        if (href.startsWith("/")) {
            return "https://www.zhihu.com" + href;
        }
        return "https://www.zhihu.com/" + href;
    }

    private Long calculateDefaultHotValue(int index, int total) {
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

    private Long parseHotValue(String hotText) {
        if (hotText == null || hotText.isEmpty()) {
            return 0L;
        }
        try {
            String processed = hotText.replace(",", "").replace("，", "").trim();

            Pattern yiPattern = Pattern.compile("([\\d.]+)\\s*亿");
            Matcher yiMatcher = yiPattern.matcher(processed);
            if (yiMatcher.find()) {
                double value = Double.parseDouble(yiMatcher.group(1));
                return (long) (value * 100000000);
            }

            Pattern wanPattern = Pattern.compile("([\\d.]+)\\s*万");
            Matcher wanMatcher = wanPattern.matcher(processed);
            if (wanMatcher.find()) {
                double value = Double.parseDouble(wanMatcher.group(1));
                return (long) (value * 10000);
            }

            Pattern qianPattern = Pattern.compile("([\\d.]+)\\s*千");
            Matcher qianMatcher = qianPattern.matcher(processed);
            if (qianMatcher.find()) {
                double value = Double.parseDouble(qianMatcher.group(1));
                return (long) (value * 1000);
            }

            Pattern numPattern = Pattern.compile("(\\d+)");
            Matcher numMatcher = numPattern.matcher(processed);
            if (numMatcher.find()) {
                return Long.parseLong(numMatcher.group(1));
            }

            return 0L;
        } catch (NumberFormatException e) {
            log.debug("[zhihu] 热度值解析失败: {}", hotText);
            return 0L;
        }
    }
}
