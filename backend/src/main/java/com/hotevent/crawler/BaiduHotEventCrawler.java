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
public class BaiduHotEventCrawler extends AbstractHotEventCrawler {

    @Value("${hot-event.crawler.sources:weibo,zhihu,baidu}")
    private List<String> enabledSources;

    @Override
    public String getSourceName() {
        return "baidu";
    }

    @Override
    protected String getCrawlUrl() {
        return "https://top.baidu.com/board?tab=realtime";
    }

    @Override
    protected List<HotEvent> parseHtml(String html) {
        List<HotEvent> events = new ArrayList<>();
        try {
            log.debug("[baidu] 开始解析百度热搜HTML，内容长度: {}", html.length());
            var doc = parseDocument(html);

            var selectors = new String[][]{
                    {".category-wrap_iQLoo", ".c-single-text-ellipsis", ".hot-desc_1m_j", ".hot-index_1", "a"},
                    {"[class*='category-wrap']", "[class*='single-text-ellipsis']", "[class*='hot-desc']", "[class*='hot-index']", "a"},
                    {".content_1YWBm", ".c-single-text-ellipsis", ".hot-desc_1m_j", ".hot-index_1", "a"},
                    {"div[class*='_1y3sW']", ".c-single-text-ellipsis", null, ".hot-index_1", "a"}
            };

            boolean parsed = false;
            for (int selectorIdx = 0; selectorIdx < selectors.length && !parsed; selectorIdx++) {
                String[] sel = selectors[selectorIdx];
                var elements = doc.select(sel[0]);
                log.debug("[baidu] 使用第{}套选择器，匹配元素数量: {}", selectorIdx + 1, elements.size());

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
                                String url = urlElement != null ? urlElement.attr("href") : "";
                                String description = descElement != null ? descElement.text().trim() : "";
                                Long hotValue = 0L;

                                if (hotElement != null) {
                                    String hotText = hotElement.text();
                                    hotValue = parseHotValue(hotText);
                                }

                                if (hotValue == null || hotValue == 0) {
                                    hotValue = calculateDefaultHotValue(i, elements.size());
                                }

                                if (url != null && !url.isEmpty() && !url.startsWith("http")) {
                                    if (url.startsWith("//")) {
                                        url = "https:" + url;
                                    } else if (url.startsWith("/")) {
                                        url = "https://top.baidu.com" + url;
                                    }
                                }

                                HotEvent event = createHotEvent(title, url, hotValue, i + 1);
                                event.setDescription(description);
                                event.setCategory("综合");
                                events.add(event);
                            }
                        } catch (Exception e) {
                            log.warn("[baidu] 解析第{}条记录时出错: {}", i + 1, e.getMessage());
                        }
                    }
                    if (!events.isEmpty()) {
                        parsed = true;
                        log.info("[baidu] 使用第{}套选择器成功解析{}条记录", selectorIdx + 1, events.size());
                    }
                }
            }

            if (events.isEmpty()) {
                log.warn("[baidu] 所有选择器均未匹配到元素，尝试使用正则表达式从JSON数据中提取");
                events = parseFromJsonEmbedded(html);
            }

        } catch (Exception e) {
            log.error("[baidu] 解析百度热搜HTML失败", e);
        }
        return events;
    }

    private List<HotEvent> parseFromJsonEmbedded(String html) {
        List<HotEvent> events = new ArrayList<>();
        try {
            Pattern pattern = Pattern.compile("\"word\"\\s*:\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(html);
            int count = 0;
            while (matcher.find() && count < 50) {
                String title = matcher.group(1);
                if (title != null && !title.trim().isEmpty() && !title.contains("{") && !title.contains("}")) {
                    HotEvent event = createHotEvent(
                            title.trim(),
                            "https://www.baidu.com/s?wd=" + title.trim().replace(" ", "%20"),
                            calculateDefaultHotValue(count, 50),
                            count + 1
                    );
                    event.setCategory("综合");
                    events.add(event);
                    count++;
                }
            }
            log.info("[baidu] 通过JSON正则提取到{}条记录", events.size());
        } catch (Exception e) {
            log.error("[baidu] 正则解析失败", e);
        }
        return events;
    }

    private Long calculateDefaultHotValue(int index, int total) {
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

    private Long parseHotValue(String hotText) {
        if (hotText == null || hotText.isEmpty()) {
            return 0L;
        }
        try {
            String processed = hotText.replace(",", "").replace("，", "").trim();

            Pattern wanPattern = Pattern.compile("([\\d.]+)\\s*万");
            Matcher wanMatcher = wanPattern.matcher(processed);
            if (wanMatcher.find()) {
                double value = Double.parseDouble(wanMatcher.group(1));
                return (long) (value * 10000);
            }

            Pattern yiPattern = Pattern.compile("([\\d.]+)\\s*亿");
            Matcher yiMatcher = yiPattern.matcher(processed);
            if (yiMatcher.find()) {
                double value = Double.parseDouble(yiMatcher.group(1));
                return (long) (value * 100000000);
            }

            Pattern numPattern = Pattern.compile("(\\d+)");
            Matcher numMatcher = numPattern.matcher(processed);
            if (numMatcher.find()) {
                return Long.parseLong(numMatcher.group(1));
            }

            return 0L;
        } catch (NumberFormatException e) {
            log.debug("[baidu] 热度值解析失败: {}", hotText);
            return 0L;
        }
    }
}
