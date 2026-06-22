package com.hotevent.crawler.filter;

import com.hotevent.cache.DictionaryCacheService;
import com.hotevent.crawler.core.DataItem;
import com.hotevent.entity.HotEvent;
import com.hotevent.service.SysConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("敏感内容过滤测试")
class SensitiveContentFilterTest {

    @Mock
    private SysConfigService sysConfigService;

    @Mock
    private DictionaryCacheService dictionaryCacheService;

    @InjectMocks
    private SensitiveContentFilter sensitiveContentFilter;

    @BeforeEach
    void setUp() {
        Map<SensitiveType, Set<String>> keywordMap = new HashMap<>();
        Map<SensitiveType, List<Pattern>> regexPatternMap = new HashMap<>();

        Set<String> politicsKeywords = new HashSet<>(Arrays.asList("法轮功", "邪教"));
        keywordMap.put(SensitiveType.POLITICS, politicsKeywords);

        Set<String> pornKeywords = new HashSet<>(Arrays.asList("色情", "黄色", "成人", "av", "性爱"));
        keywordMap.put(SensitiveType.PORN, pornKeywords);

        Set<String> abuseKeywords = new HashSet<>(Arrays.asList("傻逼", "蠢货", "狗娘养", "王八蛋", "滚蛋", "狗屎"));
        keywordMap.put(SensitiveType.ABUSE, abuseKeywords);

        Set<String> adKeywords = new HashSet<>(Arrays.asList("加微信", "加qq", "代购", "代理", "刷单", "兼职赚钱", "网赚"));
        keywordMap.put(SensitiveType.AD, adKeywords);

        Set<String> violenceKeywords = new HashSet<>(Arrays.asList("杀人", "自杀", "暴力", "血腥"));
        keywordMap.put(SensitiveType.VIOLENCE, violenceKeywords);

        Set<String> gamblingKeywords = new HashSet<>(Arrays.asList("赌博", "博彩", "彩票", "百家乐", "老虎机"));
        keywordMap.put(SensitiveType.GAMBLING, gamblingKeywords);

        Set<String> drugKeywords = new HashSet<>(Arrays.asList("毒品", "大麻", "可卡因", "海洛因"));
        keywordMap.put(SensitiveType.DRUG, drugKeywords);

        Set<String> otherKeywords = new HashSet<>();
        keywordMap.put(SensitiveType.OTHER, otherKeywords);

        List<Pattern> adPatterns = new ArrayList<>();
        adPatterns.add(Pattern.compile("(微信|wx|vx)[\\s:：]?[a-zA-Z0-9_-]{6,}", Pattern.CASE_INSENSITIVE));
        adPatterns.add(Pattern.compile("(qq|扣扣)[\\s:：]?\\d{6,}", Pattern.CASE_INSENSITIVE));
        adPatterns.add(Pattern.compile("(电话|手机|联系电话|Tel|tel)[\\s:：]?1[3-9]\\d{9}", Pattern.CASE_INSENSITIVE));
        regexPatternMap.put(SensitiveType.AD, adPatterns);

        List<Pattern> pornPatterns = new ArrayList<>();
        pornPatterns.add(Pattern.compile("(www\\.)?[^\\s]*?(porn|sex|xxx|成人|色情)[^\\s]*", Pattern.CASE_INSENSITIVE));
        regexPatternMap.put(SensitiveType.PORN, pornPatterns);

        for (SensitiveType type : SensitiveType.values()) {
            if (!regexPatternMap.containsKey(type)) {
                regexPatternMap.put(type, new ArrayList<>());
            }
        }

        ReflectionTestUtils.setField(sensitiveContentFilter, "keywordMap", keywordMap);
        ReflectionTestUtils.setField(sensitiveContentFilter, "regexPatternMap", regexPatternMap);
        ReflectionTestUtils.setField(sensitiveContentFilter, "filterEnabled", true);
    }

    @Nested
    @DisplayName("敏感词检测 - 涉政")
    class PoliticsDetectionTests {

        @Test
        @DisplayName("正常场景 - 检测到涉政敏感词")
        void testCheck_PoliticsKeywordDetected() {
            String text = "这是一条包含法轮功的测试文本";

            SensitiveCheckResult result = sensitiveContentFilter.check(text);

            assertNotNull(result);
            assertTrue(result.isSensitive());
            assertTrue(result.getMatchedTypes().contains(SensitiveType.POLITICS));
            assertTrue(result.getMatchedWords().contains("法轮功"));
            assertFalse(result.getMatchedDetails().isEmpty());
        }

        @Test
        @DisplayName("正常场景 - 未检测到涉政敏感词")
        void testCheck_PoliticsNoKeyword() {
            String text = "这是一条正常的新闻内容";

            SensitiveCheckResult result = sensitiveContentFilter.check(text);

            assertNotNull(result);
            assertFalse(result.isSensitive());
            assertTrue(result.getMatchedDetails().isEmpty());
        }

        @Test
        @DisplayName("边界条件 - 大小写不敏感")
        void testCheck_CaseInsensitive() {
            String text = "这是包含FALUNGONG的文本";

            SensitiveCheckResult result = sensitiveContentFilter.check(text);

            assertNotNull(result);
            assertTrue(result.isSensitive());
            assertTrue(result.getMatchedWords().stream().anyMatch(w -> w.equalsIgnoreCase("法轮功")));
        }

        @Test
        @DisplayName("边界条件 - 多个涉政敏感词")
        void testCheck_MultiplePoliticsKeywords() {
            String text = "法轮功和邪教都是敏感内容";

            SensitiveCheckResult result = sensitiveContentFilter.check(text);

            assertNotNull(result);
            assertTrue(result.isSensitive());
            assertTrue(result.getMatchedWords().contains("法轮功"));
            assertTrue(result.getMatchedWords().contains("邪教"));
        }
    }

    @Nested
    @DisplayName("敏感词检测 - 色情")
    class PornDetectionTests {

        @Test
        @DisplayName("正常场景 - 检测到色情敏感词")
        void testCheck_PornKeywordDetected() {
            String text = "这是一条包含色情内容的文本";

            SensitiveCheckResult result = sensitiveContentFilter.check(text);

            assertNotNull(result);
            assertTrue(result.isSensitive());
            assertTrue(result.getMatchedTypes().contains(SensitiveType.PORN));
            assertTrue(result.getMatchedWords().contains("色情"));
        }

        @Test
        @DisplayName("正则匹配 - 检测色情网址")
        void testCheck_PornUrlRegex() {
            String text = "访问 www.xxx-porn-site.com 获取更多内容";

            SensitiveCheckResult result = sensitiveContentFilter.check(text);

            assertNotNull(result);
            assertTrue(result.isSensitive());
            assertTrue(result.getMatchedTypes().contains(SensitiveType.PORN));
        }

        @Test
        @DisplayName("边界条件 - 英文色情词汇")
        void testCheck_EnglishPornWords() {
            String text = "This content contains porn material";

            SensitiveCheckResult result = sensitiveContentFilter.check(text);

            assertNotNull(result);
            assertTrue(result.isSensitive());
            assertTrue(result.getMatchedTypes().contains(SensitiveType.PORN));
        }
    }

    @Nested
    @DisplayName("敏感词检测 - 辱骂")
    class AbuseDetectionTests {

        @Test
        @DisplayName("正常场景 - 检测到辱骂词汇")
        void testCheck_AbuseKeywordDetected() {
            String text = "你这个蠢货，真是个王八蛋";

            SensitiveCheckResult result = sensitiveContentFilter.check(text);

            assertNotNull(result);
            assertTrue(result.isSensitive());
            assertTrue(result.getMatchedTypes().contains(SensitiveType.ABUSE));
            assertTrue(result.getMatchedWords().contains("蠢货"));
            assertTrue(result.getMatchedWords().contains("王八蛋"));
        }

        @Test
        @DisplayName("边界条件 - 多个辱骂词")
        void testCheck_MultipleAbuseWords() {
            String text = "傻逼 狗屎 滚蛋";

            SensitiveCheckResult result = sensitiveContentFilter.check(text);

            assertNotNull(result);
            assertTrue(result.isSensitive());
            assertEquals(SensitiveType.ABUSE, result.getMatchedTypes().get(0));
            assertTrue(result.getMatchedWords().size() >= 3);
        }
    }

    @Nested
    @DisplayName("敏感词检测 - 广告")
    class AdDetectionTests {

        @Test
        @DisplayName("正常场景 - 检测到广告关键词")
        void testCheck_AdKeywordDetected() {
            String text = "加微信好友了解更多代购信息";

            SensitiveCheckResult result = sensitiveContentFilter.check(text);

            assertNotNull(result);
            assertTrue(result.isSensitive());
            assertTrue(result.getMatchedTypes().contains(SensitiveType.AD));
            assertTrue(result.getMatchedWords().contains("加微信"));
            assertTrue(result.getMatchedWords().contains("代购"));
        }

        @Test
        @DisplayName("正则匹配 - 微信号检测")
        void testCheck_WechatRegex() {
            String text = "联系我，微信：myWechat123";

            SensitiveCheckResult result = sensitiveContentFilter.check(text);

            assertNotNull(result);
            assertTrue(result.isSensitive());
            assertTrue(result.getMatchedTypes().contains(SensitiveType.AD));
        }

        @Test
        @DisplayName("正则匹配 - QQ号检测")
        void testCheck_QqRegex() {
            String text = "加QQ：123456789 了解详情";

            SensitiveCheckResult result = sensitiveContentFilter.check(text);

            assertNotNull(result);
            assertTrue(result.isSensitive());
            assertTrue(result.getMatchedTypes().contains(SensitiveType.AD));
        }

        @Test
        @DisplayName("正则匹配 - 手机号检测")
        void testCheck_PhoneRegex() {
            String text = "联系电话：13812345678";

            SensitiveCheckResult result = sensitiveContentFilter.check(text);

            assertNotNull(result);
            assertTrue(result.isSensitive());
            assertTrue(result.getMatchedTypes().contains(SensitiveType.AD));
        }

        @Test
        @DisplayName("边界条件 - 刷单兼职类广告")
        void testCheck_PartTimeAd() {
            String text = "兼职赚钱，日入千元，快来加入";

            SensitiveCheckResult result = sensitiveContentFilter.check(text);

            assertNotNull(result);
            assertTrue(result.isSensitive());
            assertTrue(result.getMatchedWords().contains("兼职赚钱"));
        }
    }

    @Nested
    @DisplayName("敏感词检测 - 暴力")
    class ViolenceDetectionTests {

        @Test
        @DisplayName("正常场景 - 检测到暴力词汇")
        void testCheck_ViolenceKeywordDetected() {
            String text = "这是一段关于杀人的暴力描述";

            SensitiveCheckResult result = sensitiveContentFilter.check(text);

            assertNotNull(result);
            assertTrue(result.isSensitive());
            assertTrue(result.getMatchedTypes().contains(SensitiveType.VIOLENCE));
            assertTrue(result.getMatchedWords().contains("杀人"));
        }

        @Test
        @DisplayName("边界条件 - 多个暴力词")
        void testCheck_MultipleViolenceWords() {
            String text = "暴力血腥的场面令人不适";

            SensitiveCheckResult result = sensitiveContentFilter.check(text);

            assertNotNull(result);
            assertTrue(result.isSensitive());
            assertTrue(result.getMatchedWords().contains("暴力"));
            assertTrue(result.getMatchedWords().contains("血腥"));
        }
    }

    @Nested
    @DisplayName("敏感词检测 - 赌博")
    class GamblingDetectionTests {

        @Test
        @DisplayName("正常场景 - 检测到赌博词汇")
        void testCheck_GamblingKeywordDetected() {
            String text = "来玩百家乐，体验博彩的乐趣";

            SensitiveCheckResult result = sensitiveContentFilter.check(text);

            assertNotNull(result);
            assertTrue(result.isSensitive());
            assertTrue(result.getMatchedTypes().contains(SensitiveType.GAMBLING));
            assertTrue(result.getMatchedWords().contains("博彩"));
            assertTrue(result.getMatchedWords().contains("百家乐"));
        }

        @Test
        @DisplayName("边界条件 - 彩票老虎机")
        void testCheck_LotteryAndSlot() {
            String text = "彩票和老虎机都是赌博的一种";

            SensitiveCheckResult result = sensitiveContentFilter.check(text);

            assertNotNull(result);
            assertTrue(result.isSensitive());
            assertTrue(result.getMatchedWords().contains("彩票"));
            assertTrue(result.getMatchedWords().contains("老虎机"));
        }
    }

    @Nested
    @DisplayName("敏感词检测 - 毒品")
    class DrugDetectionTests {

        @Test
        @DisplayName("正常场景 - 检测到毒品词汇")
        void testCheck_DrugKeywordDetected() {
            String text = "毒品和大麻都是违禁品";

            SensitiveCheckResult result = sensitiveContentFilter.check(text);

            assertNotNull(result);
            assertTrue(result.isSensitive());
            assertTrue(result.getMatchedTypes().contains(SensitiveType.DRUG));
            assertTrue(result.getMatchedWords().contains("毒品"));
            assertTrue(result.getMatchedWords().contains("大麻"));
        }

        @Test
        @DisplayName("边界条件 - 可卡因和海洛因")
        void testCheck_HardDrugs() {
            String text = "可卡因和海洛因都是严重的毒品";

            SensitiveCheckResult result = sensitiveContentFilter.check(text);

            assertNotNull(result);
            assertTrue(result.isSensitive());
            assertTrue(result.getMatchedWords().contains("可卡因"));
            assertTrue(result.getMatchedWords().contains("海洛因"));
        }
    }

    @Nested
    @DisplayName("上下文提取")
    class ContextExtractionTests {

        @Test
        @DisplayName("正常场景 - 上下文包含匹配词前后内容")
        void testExtractContext_MiddleOfText() {
            String text = "这是前面的内容，然后是色情内容，接着是后面的内容";

            SensitiveCheckResult result = sensitiveContentFilter.check(text);

            assertNotNull(result);
            assertTrue(result.isSensitive());
            assertFalse(result.getMatchedDetails().isEmpty());
            String context = result.getMatchedDetails().get(0).getContext();
            assertNotNull(context);
            assertTrue(context.contains("色情"));
        }

        @Test
        @DisplayName("边界条件 - 匹配词在文本开头")
        void testExtractContext_StartOfText() {
            String text = "色情内容是不允许的";

            SensitiveCheckResult result = sensitiveContentFilter.check(text);

            assertNotNull(result);
            assertTrue(result.isSensitive());
            String context = result.getMatchedDetails().get(0).getContext();
            assertNotNull(context);
            assertFalse(context.startsWith("..."));
        }

        @Test
        @DisplayName("边界条件 - 匹配词在文本末尾")
        void testExtractContext_EndOfText() {
            String text = "这段内容包含色情";

            SensitiveCheckResult result = sensitiveContentFilter.check(text);

            assertNotNull(result);
            assertTrue(result.isSensitive());
            String context = result.getMatchedDetails().get(0).getContext();
            assertNotNull(context);
            assertFalse(context.endsWith("..."));
        }

        @Test
        @DisplayName("边界条件 - 短文本上下文")
        void testExtractContext_ShortText() {
            String text = "短小的色情文本";

            SensitiveCheckResult result = sensitiveContentFilter.check(text);

            assertNotNull(result);
            assertTrue(result.isSensitive());
            String context = result.getMatchedDetails().get(0).getContext();
            assertNotNull(context);
            assertEquals("短小的色情文本", context.replace("...", ""));
        }
    }

    @Nested
    @DisplayName("过滤器开关")
    class FilterSwitchTests {

        @Test
        @DisplayName("正常场景 - 过滤器启用时检测敏感词")
        void testCheck_FilterEnabled() {
            ReflectionTestUtils.setField(sensitiveContentFilter, "filterEnabled", true);

            SensitiveCheckResult result = sensitiveContentFilter.check("这是色情内容");

            assertTrue(result.isSensitive());
        }

        @Test
        @DisplayName("正常场景 - 过滤器禁用时不检测")
        void testCheck_FilterDisabled() {
            ReflectionTestUtils.setField(sensitiveContentFilter, "filterEnabled", false);

            SensitiveCheckResult result = sensitiveContentFilter.check("这是色情内容");

            assertNotNull(result);
            assertFalse(result.isSensitive());
            assertTrue(result.getMatchedDetails().isEmpty());
        }

        @Test
        @DisplayName("边界条件 - 获取过滤器状态")
        void testIsFilterEnabled() {
            ReflectionTestUtils.setField(sensitiveContentFilter, "filterEnabled", true);
            assertTrue(sensitiveContentFilter.isFilterEnabled());

            ReflectionTestUtils.setField(sensitiveContentFilter, "filterEnabled", false);
            assertFalse(sensitiveContentFilter.isFilterEnabled());
        }

        @Test
        @DisplayName("边界条件 - 设置过滤器状态")
        void testSetFilterEnabled() {
            sensitiveContentFilter.setFilterEnabled(true);
            assertTrue((Boolean) ReflectionTestUtils.getField(sensitiveContentFilter, "filterEnabled"));

            sensitiveContentFilter.setFilterEnabled(false);
            assertFalse((Boolean) ReflectionTestUtils.getField(sensitiveContentFilter, "filterEnabled"));
        }
    }

    @Nested
    @DisplayName("空值与边界输入")
    class EmptyAndEdgeInputTests {

        @Test
        @DisplayName("边界条件 - 空字符串")
        void testCheck_EmptyString() {
            SensitiveCheckResult result = sensitiveContentFilter.check("");

            assertNotNull(result);
            assertFalse(result.isSensitive());
            assertTrue(result.getMatchedDetails().isEmpty());
        }

        @Test
        @DisplayName("边界条件 - null文本")
        void testCheck_NullText() {
            SensitiveCheckResult result = sensitiveContentFilter.check((String) null);

            assertNotNull(result);
            assertFalse(result.isSensitive());
            assertTrue(result.getMatchedDetails().isEmpty());
        }

        @Test
        @DisplayName("边界条件 - 空白文本")
        void testCheck_BlankText() {
            SensitiveCheckResult result = sensitiveContentFilter.check("   ");

            assertNotNull(result);
            assertFalse(result.isSensitive());
        }

        @Test
        @DisplayName("边界条件 - 纯数字文本")
        void testCheck_NumericText() {
            SensitiveCheckResult result = sensitiveContentFilter.check("1234567890");

            assertNotNull(result);
            assertFalse(result.isSensitive());
        }

        @Test
        @DisplayName("边界条件 - 特殊字符文本")
        void testCheck_SpecialCharacters() {
            SensitiveCheckResult result = sensitiveContentFilter.check("!@#$%^&*()");

            assertNotNull(result);
            assertFalse(result.isSensitive());
        }
    }

    @Nested
    @DisplayName("DataItem检测")
    class DataItemCheckTests {

        @Test
        @DisplayName("正常场景 - 检测DataItem中的敏感词")
        void testCheck_DataItem() {
            DataItem item = new DataItem();
            item.setTitle("正常标题");
            item.setContent("这里有色情内容");
            item.setSummary("正常摘要");

            SensitiveCheckResult result = sensitiveContentFilter.check(item);

            assertNotNull(result);
            assertTrue(result.isSensitive());
            assertTrue(result.getMatchedTypes().contains(SensitiveType.PORN));
        }

        @Test
        @DisplayName("边界条件 - DataItem为null")
        void testCheck_NullDataItem() {
            SensitiveCheckResult result = sensitiveContentFilter.check((DataItem) null);

            assertNotNull(result);
            assertFalse(result.isSensitive());
        }

        @Test
        @DisplayName("边界条件 - DataItem各字段均为null")
        void testCheck_DataItemAllNull() {
            DataItem item = new DataItem();

            SensitiveCheckResult result = sensitiveContentFilter.check(item);

            assertNotNull(result);
            assertFalse(result.isSensitive());
        }

        @Test
        @DisplayName("正常场景 - 标题和内容都有敏感词")
        void testCheck_DataItemMultipleFields() {
            DataItem item = new DataItem();
            item.setTitle("涉政标题法轮功");
            item.setContent("这里有色情内容");

            SensitiveCheckResult result = sensitiveContentFilter.check(item);

            assertNotNull(result);
            assertTrue(result.isSensitive());
            assertTrue(result.getMatchedTypes().contains(SensitiveType.POLITICS));
            assertTrue(result.getMatchedTypes().contains(SensitiveType.PORN));
        }
    }

    @Nested
    @DisplayName("HotEvent检测")
    class HotEventCheckTests {

        @Test
        @DisplayName("正常场景 - 检测HotEvent中的敏感词")
        void testCheck_HotEvent() {
            HotEvent event = new HotEvent();
            event.setTitle("正常标题");
            event.setDescription("这里有赌博相关的内容");

            SensitiveCheckResult result = sensitiveContentFilter.check(event);

            assertNotNull(result);
            assertTrue(result.isSensitive());
            assertTrue(result.getMatchedTypes().contains(SensitiveType.GAMBLING));
        }

        @Test
        @DisplayName("边界条件 - HotEvent为null")
        void testCheck_NullHotEvent() {
            SensitiveCheckResult result = sensitiveContentFilter.check((HotEvent) null);

            assertNotNull(result);
            assertFalse(result.isSensitive());
        }

        @Test
        @DisplayName("边界条件 - HotEvent标题和描述都为null")
        void testCheck_HotEventAllNull() {
            HotEvent event = new HotEvent();

            SensitiveCheckResult result = sensitiveContentFilter.check(event);

            assertNotNull(result);
            assertFalse(result.isSensitive());
        }

        @Test
        @DisplayName("正常场景 - 标题和描述都有敏感词")
        void testCheck_HotEventMultipleFields() {
            HotEvent event = new HotEvent();
            event.setTitle("包含暴力的标题");
            event.setDescription("描述中有色情内容");

            SensitiveCheckResult result = sensitiveContentFilter.check(event);

            assertNotNull(result);
            assertTrue(result.isSensitive());
            assertTrue(result.getMatchedTypes().contains(SensitiveType.VIOLENCE));
            assertTrue(result.getMatchedTypes().contains(SensitiveType.PORN));
        }
    }

    @Nested
    @DisplayName("关键词统计")
    class KeywordStatisticsTests {

        @Test
        @DisplayName("正常场景 - 获取所有关键词")
        void testGetAllKeywords() {
            Map<SensitiveType, Set<String>> allKeywords = sensitiveContentFilter.getAllKeywords();

            assertNotNull(allKeywords);
            assertEquals(SensitiveType.values().length, allKeywords.size());
        }

        @Test
        @DisplayName("正常场景 - 获取指定类型关键词")
        void testGetKeywords_ByType() {
            Set<String> pornKeywords = sensitiveContentFilter.getKeywords(SensitiveType.PORN);

            assertNotNull(pornKeywords);
            assertFalse(pornKeywords.isEmpty());
            assertTrue(pornKeywords.contains("色情"));
            assertTrue(pornKeywords.contains("黄色"));
        }

        @Test
        @DisplayName("边界条件 - 获取不存在类型的关键词")
        void testGetKeywords_NonExistentType() {
            Set<String> keywords = sensitiveContentFilter.getKeywords(null);

            assertNotNull(keywords);
            assertTrue(keywords.isEmpty());
        }

        @Test
        @DisplayName("正常场景 - 获取关键词总数")
        void testGetTotalKeywordCount() {
            int totalCount = sensitiveContentFilter.getTotalKeywordCount();

            assertTrue(totalCount > 0);
            Map<SensitiveType, Set<String>> allKeywords = sensitiveContentFilter.getAllKeywords();
            int expectedCount = allKeywords.values().stream().mapToInt(Set::size).sum();
            assertEquals(expectedCount, totalCount);
        }
    }

    @Nested
    @DisplayName("多种敏感类型混合检测")
    class MixedSensitiveTypesTests {

        @Test
        @DisplayName("正常场景 - 文本包含多种类型敏感词")
        void testCheck_MixedTypes() {
            String text = "这条内容包含色情、赌博、暴力等多种敏感信息";

            SensitiveCheckResult result = sensitiveContentFilter.check(text);

            assertNotNull(result);
            assertTrue(result.isSensitive());
            assertTrue(result.getMatchedTypes().size() >= 2);
            assertTrue(result.getMatchedTypes().contains(SensitiveType.PORN));
            assertTrue(result.getMatchedTypes().contains(SensitiveType.GAMBLING));
            assertTrue(result.getMatchedTypes().contains(SensitiveType.VIOLENCE));
        }

        @Test
        @DisplayName("正常场景 - 关键词和正则都匹配")
        void testCheck_KeywordAndRegexMatch() {
            String text = "加微信 abc123def 了解更多色情内容";

            SensitiveCheckResult result = sensitiveContentFilter.check(text);

            assertNotNull(result);
            assertTrue(result.isSensitive());
            assertTrue(result.getMatchedTypes().contains(SensitiveType.AD));
            assertTrue(result.getMatchedTypes().contains(SensitiveType.PORN));
            assertTrue(result.getMatchedDetails().size() >= 2);
        }

        @Test
        @DisplayName("边界条件 - 敏感词在文本中多次出现")
        void testCheck_MultipleOccurrences() {
            String text = "色情内容1，色情内容2，色情内容3";

            SensitiveCheckResult result = sensitiveContentFilter.check(text);

            assertNotNull(result);
            assertTrue(result.isSensitive());
            assertTrue(result.getMatchedWords().contains("色情"));
        }

        @Test
        @DisplayName("边界条件 - 长文本敏感内容检测")
        void testCheck_LongText() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                sb.append("这是一段正常的文本内容。");
            }
            sb.append("突然出现了法轮功这个敏感词。");
            for (int i = 0; i < 100; i++) {
                sb.append("这又是一段正常的文本内容。");
            }

            SensitiveCheckResult result = sensitiveContentFilter.check(sb.toString());

            assertNotNull(result);
            assertTrue(result.isSensitive());
            assertTrue(result.getMatchedTypes().contains(SensitiveType.POLITICS));
        }
    }
}
