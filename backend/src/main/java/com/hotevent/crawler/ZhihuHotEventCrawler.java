package com.hotevent.crawler;

import com.hotevent.entity.HotEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
    protected String getCrawlUrl() {
        return "https://www.zhihu.com/hot";
    }

    @Override
    protected List<HotEvent> parseHtml(String html) {
        List<HotEvent> events = new ArrayList<>();
        try {
            var doc = parseDocument(html);
            var elements = doc.select(".HotItem");

            for (int i = 0; i < elements.size(); i++) {
                var element = elements.get(i);
                var titleElement = element.selectFirst(".HotItem-content .HotItem-title");
                var descElement = element.selectFirst(".HotItem-content .HotItem-excerpt");
                var hotElement = element.selectFirst(".HotItem-metrics");
                var urlElement = element.selectFirst("a");

                if (titleElement != null) {
                    String title = titleElement.text();
                    String url = urlElement != null ? "https://www.zhihu.com" + urlElement.attr("href") : "";
                    String description = descElement != null ? descElement.text() : "";
                    Long hotValue = 0L;
                    if (hotElement != null) {
                        String hotText = hotElement.text();
                        try {
                            hotValue = parseHotValue(hotText);
                        } catch (Exception e) {
                            hotValue = (long) (300000 - i * 15000);
                        }
                    }
                    HotEvent event = createHotEvent(title, url, hotValue, i + 1);
                    event.setDescription(description);
                    events.add(event);
                }
            }
        } catch (Exception e) {
            log.error("解析知乎热榜失败", e);
        }
        return events;
    }

    @Override
    protected String[] getMockTitles(String sourceName) {
        return new String[]{
            "如何看待人工智能的发展趋势",
            "2024年有哪些值得关注的科技热点",
            "程序员如何保持竞争力",
            "有哪些让你相见恨晚的学习方法",
            "年轻人应该如何规划职业生涯",
            "为什么越来越多的人选择躺平",
            "如何评价当前的互联网行业",
            "普通人如何实现财务自由",
            "有哪些高质量的书籍推荐",
            "如何培养孩子的自主学习能力",
            "30岁转行还来得及吗",
            "如何看待内卷现象",
            "为什么现在的年轻人不想结婚",
            "如何提升自己的认知水平",
            "有哪些实用的时间管理方法",
            "如何克服拖延症",
            "为什么读书越来越重要",
            "如何看待元宇宙概念",
            "普通人如何抓住时代机遇",
            "如何评价ChatGPT的影响",
            "深度学习入门指南",
            "如何培养批判性思维",
            "为什么很多人到了中年开始焦虑",
            "如何看待职场PUA",
            "有哪些值得关注的科技动态",
            "如何提高自己的情商",
            "为什么说选择比努力更重要",
            "如何看待教育内卷",
            "普通人如何实现阶层跨越",
            "如何培养自己的核心竞争力",
            "为什么现在年轻人压力越来越大",
            "如何看待房价走势",
            "有哪些好的理财方式",
            "如何提升自己的表达能力",
            "为什么越来越多的人开始健身",
            "如何看待新能源汽车的发展",
            "普通人如何应对不确定性",
            "如何评价当前的经济形势",
            "有哪些令人惊艳的软件推荐",
            "如何培养终身学习的习惯",
            "为什么知识付费越来越火",
            "如何看待远程办公趋势",
            "有哪些高质量的纪录片推荐",
            "如何提升自己的审美",
            "为什么说健康是最大的财富",
            "如何看待自媒体行业的发展",
            "普通人如何打造个人品牌",
            "如何提高工作效率",
            "为什么越来越多的人选择考公",
            "如何看待消费降级现象"
        };
    }

    @Override
    public List<HotEvent> crawl() throws Exception {
        try {
            String html = fetchHtml(getCrawlUrl());
            List<HotEvent> events = parseHtml(html);
            if (events.isEmpty()) {
                log.warn("知乎热榜数据为空，使用模拟数据");
                events = generateMockData(50, getSourceName());
            }
            log.info("知乎热榜抓取完成，共{}条", events.size());
            return events;
        } catch (Exception e) {
            log.error("知乎热榜抓取失败，使用模拟数据: {}", e.getMessage());
            return generateMockData(50, getSourceName());
        }
    }

    @Override
    public boolean isEnabled() {
        return enabledSources.contains("zhihu");
    }

    private Long parseHotValue(String hotText) {
        if (hotText == null || hotText.isEmpty()) {
            return 0L;
        }
        hotText = hotText.replace("万热度", "0000").replace("热度", "");
        try {
            return Long.parseLong(hotText.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
