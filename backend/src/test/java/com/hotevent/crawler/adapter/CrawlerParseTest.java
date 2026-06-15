package com.hotevent.crawler.adapter;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hotevent.crawler.adapter.impl.BilibiliAdapter;
import com.hotevent.crawler.adapter.impl.DouyinAdapter;
import com.hotevent.crawler.core.CrawlRequest;
import com.hotevent.crawler.core.CrawlResponse;
import com.hotevent.crawler.core.DataItem;
import com.hotevent.crawler.http.HttpResponseWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("爬虫数据解析测试")
public class CrawlerParseTest {

    private DouyinAdapter douyinAdapter;
    private BilibiliAdapter bilibiliAdapter;

    @BeforeEach
    void setUp() {
        douyinAdapter = new DouyinAdapter();
        bilibiliAdapter = new BilibiliAdapter();
    }

    private CrawlResponse buildResponse(String body, String url) {
        CrawlResponse response = new CrawlResponse();
        response.setStatus(CrawlResponse.Status.SUCCESS);
        HttpResponseWrapper raw = HttpResponseWrapper.builder()
                .body(body)
                .statusCode(200)
                .isSuccessful(true)
                .build();
        response.setRawResponse(raw);
        response.setRequest(CrawlRequest.builder()
                .url(url)
                .build());
        return response;
    }

    @Test
    @DisplayName("测试抖音60s.viki.moe API数据解析 - 验证生成完整文章数据")
    void testDouyin60sVikiParse() {
        String jsonBody = "{\n" +
                "  \"code\": 200,\n" +
                "  \"message\": \"获取成功\",\n" +
                "  \"data\": [\n" +
                "    {\n" +
                "      \"title\": \"美伊达成和平协议\",\n" +
                "      \"hot_value\": 12136253,\n" +
                "      \"link\": \"https://www.douyin.com/search/%E7%BE%8E%E4%BC%8A%E8%BE%BE%E6%88%90%E5%92%8C%E5%B9%B3%E5%8D%8F%E8%AE%AE\",\n" +
                "      \"event_time\": \"2026/06/15 05:32:54\",\n" +
                "      \"event_time_at\": 1781472774,\n" +
                "      \"active_time\": \"2026-06-15 07:46:59\",\n" +
                "      \"active_time_at\": 1781509619000\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"德国7:1库拉索\",\n" +
                "      \"hot_value\": 12074778,\n" +
                "      \"cover\": \"https://p11-sign.douyinpic.com/test.jpg\",\n" +
                "      \"link\": \"https://www.douyin.com/search/test\",\n" +
                "      \"event_time\": \"2026/06/14 18:03:00\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        CrawlResponse response = buildResponse(jsonBody, "https://60s.viki.moe/v2/douyin");
        List<DataItem> items = douyinAdapter.parseListResponse(response);

        assertNotNull(items, "解析结果不应为空");
        assertFalse(items.isEmpty(), "解析结果列表不应为空");
        assertEquals(2, items.size(), "应解析出2条数据");

        DataItem item1 = items.get(0);
        System.out.println("========== 抖音数据解析测试 - 第1条 ==========");
        System.out.println("标题: " + item1.getTitle());
        System.out.println("摘要: " + item1.getSummary());
        System.out.println("内容: " + (item1.getContent() != null && item1.getContent().length() > 200
                ? item1.getContent().substring(0, 200) + "..." : item1.getContent()));
        System.out.println("URL: " + item1.getUrl());
        System.out.println("热度值: " + item1.getHotValue());
        System.out.println("排名: " + item1.getHotRank());
        System.out.println("分类: " + item1.getCategory());
        System.out.println("发布时间: " + item1.getPublishTime());
        System.out.println("ItemId: " + item1.getItemId());
        System.out.println("Extra: " + item1.getExtra());
        System.out.println("============================================");

        assertNotNull(item1.getTitle(), "标题不应为空");
        assertEquals("美伊达成和平协议", item1.getTitle(), "标题应匹配");
        assertNotNull(item1.getSummary(), "摘要不应为空");
        assertTrue(item1.getSummary().contains("抖音热榜"), "摘要应包含'抖音热榜'标识");
        assertTrue(item1.getSummary().contains("第1名"), "摘要应包含排名信息");
        assertNotNull(item1.getContent(), "内容不应为空");
        assertTrue(item1.getContent().contains("详细信息"), "内容应包含详细信息块");
        assertTrue(item1.getContent().length() > 100, "内容长度应足够（文章数据）");
        assertNotNull(item1.getUrl(), "URL不应为空");
        assertTrue(item1.getUrl().contains("douyin.com"), "URL应指向抖音");
        assertEquals(12136253L, item1.getHotValue(), "热度值应匹配");
        assertEquals(1, item1.getHotRank(), "排名应为1");
        assertEquals("抖音热榜", item1.getCategory(), "分类应为抖音热榜");
        assertNotNull(item1.getItemId(), "ItemId不应为空");
        assertTrue(item1.getItemId().startsWith("douyin_"), "ItemId前缀应正确");
        assertNotNull(item1.getExtra(), "Extra数据不应为空");
        assertTrue(item1.getExtra().containsKey("source"), "Extra应包含source");
        assertTrue(item1.getExtra().containsKey("hot_value_formatted"), "Extra应包含格式化热度值");

        DataItem item2 = items.get(1);
        assertNotNull(item2.getCoverImage(), "第2条数据应有封面图");
        assertEquals(2, item2.getHotRank(), "第2条排名应为2");
        assertEquals(12074778L, item2.getHotValue(), "第2条热度值应匹配");

        System.out.println("\n✅ 抖音60s.viki.moe API数据解析测试通过！成功生成完整文章数据\n");
    }

    @Test
    @DisplayName("测试B站60s.viki.moe热搜API数据解析 - 验证生成完整文章数据")
    void testBilibiliVikiHotSearchParse() {
        String jsonBody = "{\n" +
                "  \"status\": 200,\n" +
                "  \"message\": \"获取成功\",\n" +
                "  \"data\": [\n" +
                "    {\n" +
                "      \"position\": 1,\n" +
                "      \"keyword\": \"淘宝回应支持微信支付\",\n" +
                "      \"show_name\": \"淘宝回应支持微信支付\",\n" +
                "      \"word_type\": 5,\n" +
                "      \"icon\": \"http://i0.hdslb.com/bfs/test.png\",\n" +
                "      \"hot_id\": 176109,\n" +
                "      \"is_commercial\": \"0\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"position\": 2,\n" +
                "      \"keyword\": \"徐静雨\",\n" +
                "      \"show_name\": \"终过二郎真君徐静雨\",\n" +
                "      \"word_type\": 7,\n" +
                "      \"hot_id\": 176142,\n" +
                "      \"resource_id\": 21669525,\n" +
                "      \"show_live_icon\": true,\n" +
                "      \"is_commercial\": \"0\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        CrawlResponse response = buildResponse(jsonBody, "https://60s.viki.moe/bili");
        List<DataItem> items = bilibiliAdapter.parseListResponse(response);

        assertNotNull(items, "解析结果不应为空");
        assertFalse(items.isEmpty(), "解析结果列表不应为空");
        assertEquals(2, items.size(), "应解析出2条数据");

        DataItem item1 = items.get(0);
        System.out.println("========== B站热搜解析测试 - 第1条 ==========");
        System.out.println("标题: " + item1.getTitle());
        System.out.println("摘要: " + item1.getSummary());
        System.out.println("内容: " + (item1.getContent() != null && item1.getContent().length() > 200
                ? item1.getContent().substring(0, 200) + "..." : item1.getContent()));
        System.out.println("URL: " + item1.getUrl());
        System.out.println("封面图: " + item1.getCoverImage());
        System.out.println("热度值: " + item1.getHotValue());
        System.out.println("排名: " + item1.getHotRank());
        System.out.println("分类: " + item1.getCategory());
        System.out.println("标签: " + item1.getTags());
        System.out.println("ItemId: " + item1.getItemId());
        System.out.println("Extra: " + item1.getExtra());
        System.out.println("============================================");

        assertNotNull(item1.getTitle(), "标题不应为空");
        assertEquals("淘宝回应支持微信支付", item1.getTitle(), "标题应匹配");
        assertNotNull(item1.getSummary(), "摘要不应为空");
        assertTrue(item1.getSummary().contains("B站热搜"), "摘要应包含'B站热搜'标识");
        assertTrue(item1.getSummary().contains("第1名"), "摘要应包含排名信息");
        assertNotNull(item1.getContent(), "内容不应为空");
        assertTrue(item1.getContent().contains("详细信息"), "内容应包含详细信息块");
        assertTrue(item1.getContent().length() > 100, "内容长度应足够（文章数据）");
        assertNotNull(item1.getUrl(), "URL不应为空");
        assertEquals(1, item1.getHotRank(), "排名应为1");
        assertNotNull(item1.getHotValue(), "热度值不应为空（根据排名估算）");
        assertTrue(item1.getHotValue() > 0, "热度值应大于0");
        assertEquals("B站热搜", item1.getCategory(), "分类应为B站热搜");
        assertNotNull(item1.getCoverImage(), "封面图不应为空");
        assertTrue(item1.getCoverImage().startsWith("https://"), "封面图URL应为HTTPS");
        assertNotNull(item1.getTags(), "标签不应为空");
        assertTrue(item1.getTags().contains("热点资讯"), "word_type=5应映射为热点资讯");
        assertNotNull(item1.getItemId(), "ItemId不应为空");
        assertNotNull(item1.getExtra(), "Extra数据不应为空");
        assertTrue(item1.getExtra().containsKey("word_type_label"), "Extra应包含word_type_label");

        DataItem item2 = items.get(1);
        assertEquals(2, item2.getHotRank(), "第2条排名应为2");
        assertNotNull(item2.getTags(), "第2条标签不应为空");
        assertTrue(item2.getTags().contains("直播内容"), "word_type=7应映射为直播内容");
        assertTrue(item2.getTags().contains("直播"), "直播内容应加直播标签");
        assertTrue(Boolean.TRUE.equals(item2.getExtra().get("is_live")), "直播标记应正确");
        assertTrue(item2.getUrl().contains("video/21669525"), "有关联资源时URL应指向视频");

        System.out.println("\n✅ B站60s.viki.moe热搜API数据解析测试通过！成功生成完整文章数据\n");
    }

    @Test
    @DisplayName("测试B站官方排行榜API数据解析 - 验证生成完整视频文章数据")
    void testBilibiliOfficialRankingParse() {
        String jsonBody = "{\n" +
                "  \"code\": 0,\n" +
                "  \"message\": \"OK\",\n" +
                "  \"data\": {\n" +
                "    \"list\": [\n" +
                "      {\n" +
                "        \"aid\": 116749374593001,\n" +
                "        \"bvid\": \"BV1nAJK6PEwh\",\n" +
                "        \"tid\": 172,\n" +
                "        \"tname\": \"手机游戏\",\n" +
                "        \"pic\": \"//i0.hdslb.com/bfs/archive/test.jpg\",\n" +
                "        \"title\": \"《原神》动画短片——「最后的遗产」\",\n" +
                "        \"pubdate\": 1781496000,\n" +
                "        \"desc\": \"奇械公 护国的白骑士 详细剧情介绍...\",\n" +
                "        \"owner\": {\n" +
                "          \"mid\": 401742377,\n" +
                "          \"name\": \"原神\",\n" +
                "          \"face\": \"https://i2.hdslb.com/bfs/face/test.jpg\"\n" +
                "        },\n" +
                "        \"stat\": {\n" +
                "          \"view\": 1605304,\n" +
                "          \"danmaku\": 29238,\n" +
                "          \"reply\": 24835,\n" +
                "          \"favorite\": 80485,\n" +
                "          \"coin\": 258198,\n" +
                "          \"share\": 54368,\n" +
                "          \"like\": 238200\n" +
                "        },\n" +
                "        \"duration\": 1861,\n" +
                "        \"short_link_v2\": \"https://b23.tv/BV1nAJK6PEwh\",\n" +
                "        \"pub_location\": \"上海\",\n" +
                "        \"dynamic\": \"#原神# #桑多涅# 动画短片发布！\"\n" +
                "      }\n" +
                "    ]\n" +
                "  }\n" +
                "}";

        CrawlResponse response = buildResponse(jsonBody, "https://api.bilibili.com/x/web-interface/ranking/v2?rid=0&type=all");
        List<DataItem> items = bilibiliAdapter.parseListResponse(response);

        assertNotNull(items, "解析结果不应为空");
        assertFalse(items.isEmpty(), "解析结果列表不应为空");
        assertEquals(1, items.size(), "应解析出1条数据");

        DataItem item = items.get(0);
        System.out.println("========== B站官方排行榜解析测试 ==========");
        System.out.println("标题: " + item.getTitle());
        System.out.println("UP主: " + item.getAuthor());
        System.out.println("摘要: " + (item.getSummary() != null && item.getSummary().length() > 150
                ? item.getSummary().substring(0, 150) + "..." : item.getSummary()));
        System.out.println("内容长度: " + (item.getContent() != null ? item.getContent().length() : 0) + " 字符");
        System.out.println("内容预览: " + (item.getContent() != null && item.getContent().length() > 300
                ? item.getContent().substring(0, 300) + "..." : item.getContent()));
        System.out.println("URL: " + item.getUrl());
        System.out.println("封面图: " + item.getCoverImage());
        System.out.println("播放量: " + item.getViewCount());
        System.out.println("点赞数: " + item.getLikeCount());
        System.out.println("评论数: " + item.getCommentCount());
        System.out.println("热度值: " + item.getHotValue());
        System.out.println("分类: " + item.getCategory());
        System.out.println("标签: " + item.getTags());
        System.out.println("ItemId: " + item.getItemId());
        System.out.println("Extra keys: " + item.getExtra().keySet());
        System.out.println("============================================");

        assertNotNull(item.getTitle(), "标题不应为空");
        assertEquals("《原神》动画短片——「最后的遗产」", item.getTitle(), "标题应匹配");
        assertEquals("原神", item.getAuthor(), "UP主应匹配");
        assertNotNull(item.getSummary(), "摘要不应为空");
        assertTrue(item.getSummary().contains("UP主"), "摘要应包含UP主信息");
        assertTrue(item.getSummary().contains("播放量"), "摘要应包含播放量");
        assertNotNull(item.getContent(), "内容不应为空");
        assertTrue(item.getContent().contains("视频详细信息"), "内容应包含详细信息块");
        assertTrue(item.getContent().contains("数据统计"), "内容应包含数据统计块");
        assertTrue(item.getContent().contains("视频简介"), "内容应包含视频简介");
        assertTrue(item.getContent().contains("链接信息"), "内容应包含链接信息");
        assertTrue(item.getContent().length() > 300, "视频内容长度应足够（文章数据）");
        assertEquals("BV1nAJK6PEwh", item.getItemId(), "ItemId应为BV号");
        assertEquals("https://b23.tv/BV1nAJK6PEwh", item.getUrl(), "URL应为短链接");
        assertNotNull(item.getCoverImage(), "封面图不应为空");
        assertTrue(item.getCoverImage().startsWith("https:"), "封面图URL应补全HTTPS");
        assertEquals(1605304L, item.getViewCount(), "播放量应匹配");
        assertEquals(238200L, item.getLikeCount(), "点赞数应匹配");
        assertEquals(24835L, item.getCommentCount(), "评论数应匹配");
        assertTrue(item.getHotValue() > 1605304L, "热度值应大于播放量（综合计算）");
        assertEquals("手机游戏", item.getCategory(), "分类应为手机游戏");
        assertNotNull(item.getTags(), "标签不应为空");
        assertTrue(item.getTags().contains("B站视频"), "标签应包含B站视频");
        assertTrue(item.getTags().contains("手机游戏"), "标签应包含分类");
        assertTrue(item.getTags().contains("上海"), "标签应包含发布地点");
        assertNotNull(item.getPublishTime(), "发布时间不应为空");
        assertNotNull(item.getExtra(), "Extra数据不应为空");
        assertTrue(item.getExtra().containsKey("aid"), "Extra应包含aid");
        assertTrue(item.getExtra().containsKey("bvid"), "Extra应包含bvid");
        assertTrue(item.getExtra().containsKey("duration"), "Extra应包含时长");
        assertTrue(item.getExtra().containsKey("hot_value_formatted"), "Extra应包含格式化热度值");
        assertTrue(item.getExtra().containsKey("view_formatted"), "Extra应包含格式化播放量");

        System.out.println("\n✅ B站官方排行榜API数据解析测试通过！成功生成完整视频文章数据\n");
    }

    @Test
    @DisplayName("测试数据完整性验证 - 确保所有必要字段都被填充")
    void testDataItemCompleteness() {
        String douyinJson = "{\"code\":200,\"data\":[{\"title\":\"测试抖音话题\",\"hot_value\":5000000}]}";
        String biliJson = "{\"status\":200,\"data\":[{\"position\":1,\"keyword\":\"测试B站话题\",\"show_name\":\"测试B站话题显示名\",\"word_type\":5}]}";

        List<DataItem> douyinItems = douyinAdapter.parseListResponse(
                buildResponse(douyinJson, "https://60s.viki.moe/v2/douyin"));
        List<DataItem> biliItems = bilibiliAdapter.parseListResponse(
                buildResponse(biliJson, "https://60s.viki.moe/bili"));

        assertFalse(douyinItems.isEmpty(), "抖音解析结果不应为空");
        assertFalse(biliItems.isEmpty(), "B站解析结果不应为空");

        DataItem di = douyinItems.get(0);
        System.out.println("========== 抖音数据完整性检查 ==========");
        System.out.println("title: " + (di.getTitle() != null ? "✅" : "❌"));
        System.out.println("summary: " + (di.getSummary() != null ? "✅" : "❌"));
        System.out.println("content: " + (di.getContent() != null ? "✅" : "❌"));
        System.out.println("url: " + (di.getUrl() != null ? "✅" : "❌"));
        System.out.println("hotValue: " + (di.getHotValue() != null ? "✅" : "❌"));
        System.out.println("hotRank: " + (di.getHotRank() != null ? "✅" : "❌"));
        System.out.println("category: " + (di.getCategory() != null ? "✅" : "❌"));
        System.out.println("itemId: " + (di.getItemId() != null ? "✅" : "❌"));
        System.out.println("extra: " + (di.getExtra() != null && !di.getExtra().isEmpty() ? "✅" : "❌"));

        assertNotNull(di.getTitle(), "抖音title不应为空");
        assertNotNull(di.getSummary(), "抖音summary不应为空 - 用于保存到description");
        assertNotNull(di.getContent(), "抖音content不应为空 - 文章主体内容");
        assertNotNull(di.getUrl(), "抖音url不应为空");
        assertNotNull(di.getHotValue(), "抖音hotValue不应为空");
        assertNotNull(di.getHotRank(), "抖音hotRank不应为空");
        assertNotNull(di.getCategory(), "抖音category不应为空");

        DataItem bi = biliItems.get(0);
        System.out.println("\n========== B站数据完整性检查 ==========");
        System.out.println("title: " + (bi.getTitle() != null ? "✅" : "❌"));
        System.out.println("summary: " + (bi.getSummary() != null ? "✅" : "❌"));
        System.out.println("content: " + (bi.getContent() != null ? "✅" : "❌"));
        System.out.println("url: " + (bi.getUrl() != null ? "✅" : "❌"));
        System.out.println("hotValue: " + (bi.getHotValue() != null ? "✅" : "❌"));
        System.out.println("hotRank: " + (bi.getHotRank() != null ? "✅" : "❌"));
        System.out.println("category: " + (bi.getCategory() != null ? "✅" : "❌"));
        System.out.println("itemId: " + (bi.getItemId() != null ? "✅" : "❌"));
        System.out.println("tags: " + (bi.getTags() != null ? "✅" : "❌"));
        System.out.println("extra: " + (bi.getExtra() != null && !bi.getExtra().isEmpty() ? "✅" : "❌"));

        assertNotNull(bi.getTitle(), "B站title不应为空");
        assertNotNull(bi.getSummary(), "B站summary不应为空 - 用于保存到description");
        assertNotNull(bi.getContent(), "B站content不应为空 - 文章主体内容");
        assertNotNull(bi.getUrl(), "B站url不应为空");
        assertNotNull(bi.getHotValue(), "B站hotValue不应为空");
        assertNotNull(bi.getHotRank(), "B站hotRank不应为空");
        assertNotNull(bi.getCategory(), "B站category不应为空");
        assertNotNull(bi.getTags(), "B站tags不应为空");

        System.out.println("\n✅ 数据完整性验证通过！所有必要字段均已填充，可以成功保存为文章数据\n");
    }
}
