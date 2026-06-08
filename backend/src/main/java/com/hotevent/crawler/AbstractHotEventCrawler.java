package com.hotevent.crawler;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Slf4j
public abstract class AbstractHotEventCrawler implements HotEventCrawler {

    @Value("${hot-event.crawler.user-agent}")
    protected String userAgent;

    @Value("${hot-event.crawler.timeout:10000}")
    protected int timeout;

    protected Random random = new Random();

    protected abstract String getCrawlUrl();

    protected abstract List<HotEvent> parseHtml(String html);

    protected String fetchHtml(String url) throws Exception {
        HttpResponse response = HttpRequest.get(url)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .timeout(timeout)
                .execute();

        if (!response.isOk()) {
            throw new RuntimeException("HTTP请求失败，状态码：" + response.getStatus());
        }

        return response.body();
    }

    protected Document parseDocument(String html) {
        return Jsoup.parse(html);
    }

    protected HotEvent createHotEvent(String title, String url, Long hotValue, Integer rank) {
        HotEvent event = new HotEvent();
        event.setTitle(title);
        event.setSourceUrl(url);
        event.setSource(getSourceName());
        event.setHotValue(hotValue);
        event.setHotRank(rank);
        event.setHot(true);
        event.setIsRising(random.nextBoolean());
        event.setRisingRate(random.nextDouble() * 100);
        event.setCrawlTime(LocalDateTime.now());
        return event;
    }

    protected List<HotEvent> generateMockData(int count, String sourceName) {
        List<HotEvent> events = new ArrayList<>();
        String[] categories = {"社会", "科技", "娱乐", "体育", "财经", "教育", "健康", "国际"};
        String[] mockTitles = getMockTitles(sourceName);

        for (int i = 0; i < Math.min(count, mockTitles.length); i++) {
            HotEvent event = new HotEvent();
            event.setTitle(mockTitles[i]);
            event.setSource(sourceName);
            event.setSourceUrl("https://example.com/hot/" + (i + 1));
            event.setHotValue((long) (1000000 - i * 50000 + random.nextInt(10000)));
            event.setHotRank(i + 1);
            event.setCategory(categories[random.nextInt(categories.length)]);
            event.setIsHot(true);
            event.setIsRising(random.nextBoolean());
            event.setRisingRate(random.nextDouble() * 200);
            event.setCrawlTime(LocalDateTime.now());
            event.setDescription(mockTitles[i] + " - 详细内容请点击查看...");
            events.add(event);
        }

        return events;
    }

    protected abstract String[] getMockTitles(String sourceName);
}
