package com.hotevent.crawler;

import com.hotevent.entity.HotEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
    public String getCrawlUrl() {
        return "https://s.weibo.com/top/summary";
    }

    @Override
    protected List<HotEvent> parseHtml(String html) {
        List<HotEvent> events = new ArrayList<>();
        try {
            var doc = parseDocument(html);
            var elements = doc.select("#pl_01 tbody tr");

            for (int i = 0; i < elements.size(); i++) {
                var element = elements.get(i);
                var titleElement = element.selectFirst("td.td-02 a");
                var hotElement = element.selectFirst("td.td-03");

                if (titleElement != null) {
                    String title = titleElement.text();
                    String url = "https://s.weibo.com" + titleElement.attr("href");
                    Long hotValue = 0L;
                    if (hotElement != null) {
                        String hotText = hotElement.text();
                        try {
                            hotValue = parseHotValue(hotText);
                        } catch (Exception e) {
                            hotValue = (long) (500000 - i * 20000);
                        }
                    }
                    events.add(createHotEvent(title, url, hotValue, i + 1));
                }
            }
        } catch (Exception e) {
            log.error("解析微博热搜失败", e);
        }
        return events;
    }

    @Override
    protected String[] getMockTitles(String sourceName) {
        return new String[]{
            "2024年度十大流行语发布",
            "全国多地迎入冬以来最强降温",
            "国产大模型技术突破",
            "世界杯预选赛中国队最新消息",
            "新型冠状病毒新片上映票房破亿",
            "新能源汽车销量创新高",
            "人工智能赋能千行百业",
            "高铁新线路开通运营",
            "高校毕业生就业政策",
            "医保报销比例再提高",
            "科技创新企业出海步伐加快",
            "智慧城市建设新进展",
            "乡村振兴成效显著",
            "文化遗产保护工作",
            "生态环境持续改善",
            "教育双减政策落地",
            "医疗改革深入推进",
            "养老服务体系完善",
            "社会保障体系健全",
            "全民健身运动开展",
            "数字经济快速发展",
            "绿色低碳转型加速",
            "区域协调发展战略",
            "对外开放水平提升",
            "法治中国建设成就",
            "平安中国建设成效",
            "美丽中国建设进展",
            "健康中国行动推进",
            "质量强国建设纲要",
            "网络强国建设步伐",
            "交通强国建设推进",
            "海洋强国建设成就",
            "航天强国建设突破",
            "科技强国建设进展",
            "人才强国战略实施",
            "创新驱动发展战略",
            "可持续发展战略",
            "科教兴国战略实施",
            "人才培养模式创新",
            "创新创业创造活力",
            "实体经济发展活力增强",
            "市场主体活力激发",
            "营商环境持续优化",
            "放管服改革深化",
            "政务服务便民利民",
            "基层治理能力提升",
            "社会治理现代化",
            "应急管理体系完善",
            "安全生产形势稳定",
            "防灾减灾救灾工作"
        };
    }

    @Override
    public List<HotEvent> crawl() throws Exception {
        try {
            String html = fetchHtml(getCrawlUrl());
            List<HotEvent> events = parseHtml(html);
            if (events.isEmpty()) {
                log.warn("微博热搜数据为空，使用模拟数据");
                events = generateMockData(50, getSourceName());
            }
            log.info("微博热搜抓取完成，共{}条", events.size());
            return events;
        } catch (Exception e) {
            log.error("微博热搜抓取失败，使用模拟数据: {}", e.getMessage());
            return generateMockData(50, getSourceName());
        }
    }

    @Override
    public boolean isEnabled() {
        return enabledSources.contains("weibo");
    }

    private Long parseHotValue(String hotText) {
        if (hotText == null || hotText.isEmpty()) {
            return 0L;
        }
        hotText = hotText.replace(",", "").replace("万", "0000").replace("亿", "00000000");
        try {
            return Long.parseLong(hotText.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
