package com.hotevent.crawler;

import com.hotevent.entity.HotEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
    protected String getCrawlUrl() {
        return "https://top.baidu.com/board?tab=realtime";
    }

    @Override
    protected List<HotEvent> parseHtml(String html) {
        List<HotEvent> events = new ArrayList<>();
        try {
            var doc = parseDocument(html);
            var elements = doc.select(".category-wrap_iQLoo");

            for (int i = 0; i < elements.size(); i++) {
                var element = elements.get(i);
                var titleElement = element.selectFirst(".c-single-text-ellipsis");
                var descElement = element.selectFirst(".hot-desc_1m_j");
                var hotElement = element.selectFirst(".hot-index_1");
                var urlElement = element.selectFirst("a");

                if (titleElement != null) {
                    String title = titleElement.text();
                    String url = urlElement != null ? urlElement.attr("href") : "";
                    String description = descElement != null ? descElement.text() : "";
                    Long hotValue = 0L;
                    if (hotElement != null) {
                        String hotText = hotElement.text();
                        try {
                            hotValue = parseHotValue(hotText);
                        } catch (Exception e) {
                            hotValue = (long) (400000 - i * 18000);
                        }
                    }
                    HotEvent event = createHotEvent(title, url, hotValue, i + 1);
                    event.setDescription(description);
                    events.add(event);
                }
            }
        } catch (Exception e) {
            log.error("解析百度热搜失败", e);
        }
        return events;
    }

    @Override
    protected String[] getMockTitles(String sourceName) {
        return new String[]{
            "2024年春运启动最新消息",
            "2024年春节放假安排",
            "冷空气影响全国多地",
            "国产芯片取得重大突破",
            "教育部发布新规影响万亿市场",
            "2024年经济工作会议",
            "高速免费通行时间表",
            "最新疫情最新消息",
            "2024年高考改革方案",
            "养老金调整最新消息",
            "房地产新政落地",
            "市场监管总局发布新规",
            "新能源汽车补贴政策",
            "医保改革最新消息",
            "多地发布2024年经济数据",
            "个税专项附加扣除",
            "新能源项目开工",
            "景区免门票政策",
            "2024年春运火车票",
            "冷空气来袭气温骤降",
            "重大工程进展顺利",
            "2024年春节联欢晚会",
            "流感最新疫情最新消息",
            "高铁新线路开通",
            "高速公路免费时间",
            "2024年春运高峰",
            "多地发布寒潮预警",
            "最新放假通知",
            "2024年养老金上调",
            "房地产市场回暖",
            "多地发布重要通知",
            "2024年GDP目标",
            "民生实事项目",
            "最新政策解读",
            "多地迎来降雪",
            "2024年春节档电影",
            "多地发布最新通告",
            "重大项目集中开工",
            "2024年春运启动仪式",
            "多地气温创历史新低",
            "最新交通管制通知",
            "2024年春运首日",
            "多地发布预警信息",
            "最新科技成果发布",
            "2024年春节出行指南",
            "多地迎来寒潮天气",
            "最新政策发布",
            "2024年春运返程",
            "多地发布最新政策",
            "最新消息新闻"
        };
    }

    @Override
    public List<HotEvent> crawl() throws Exception {
        try {
            String html = fetchHtml(getCrawlUrl());
            List<HotEvent> events = parseHtml(html);
            if (events.isEmpty()) {
                log.warn("百度热搜数据为空，使用模拟数据");
                events = generateMockData(50, getSourceName());
            }
            log.info("百度热搜抓取完成，共{}条", events.size());
            return events;
        } catch (Exception e) {
            log.error("百度热搜抓取失败，使用模拟数据: {}", e.getMessage());
            return generateMockData(50, getSourceName());
        }
    }

    @Override
    public boolean isEnabled() {
        return enabledSources.contains("baidu");
    }

    private Long parseHotValue(String hotText) {
        if (hotText == null || hotText.isEmpty()) {
            return 0L;
        }
        hotText = hotText.replace(",", "").replace("万", "0000").trim();
        try {
            return Long.parseLong(hotText);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
