package com.hotevent.service;

import com.hotevent.cache.HotEventCacheService;
import com.hotevent.cache.TranslationCacheService;
import com.hotevent.common.PageResult;
import com.hotevent.config.AsyncTaskExecutor;
import com.hotevent.entity.EventTranslation;
import com.hotevent.entity.HotEvent;
import com.hotevent.i18n.I18nProperties;
import com.hotevent.i18n.TranslationService;
import com.hotevent.repository.EventTranslationRepository;
import com.hotevent.repository.HotEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("热点事件服务测试")
class HotEventServiceTest {

    @Mock
    private HotEventRepository hotEventRepository;

    @Mock
    private EventTranslationRepository eventTranslationRepository;

    @Mock
    private TranslationService translationService;

    @Mock
    private I18nProperties i18nProperties;

    @Mock
    private AsyncTaskExecutor asyncTaskExecutor;

    @Mock
    private HotEventLogService hotEventLogService;

    @Mock
    private HotEventCacheService hotEventCacheService;

    @Mock
    private TranslationCacheService translationCacheService;

    @InjectMocks
    private HotEventService hotEventService;

    private HotEvent testEvent;
    private List<HotEvent> testEventList;

    @BeforeEach
    void setUp() {
        testEvent = new HotEvent();
        testEvent.setId(1L);
        testEvent.setTitle("测试热点事件");
        testEvent.setDescription("这是一个测试热点事件的描述");
        testEvent.setSource("weibo");
        testEvent.setSourceUrl("https://weibo.com/test");
        testEvent.setHotValue(1000000L);
        testEvent.setHotRank(1);
        testEvent.setCategory("社会");
        testEvent.setImageUrl("https://example.com/image.jpg");
        testEvent.setIsHot(true);
        testEvent.setIsRising(true);
        testEvent.setRisingRate(50.5);
        testEvent.setCrawlTime(LocalDateTime.now());
        testEvent.setFirstSeenTime(LocalDateTime.now().minusHours(2));
        testEvent.setLastSeenTime(LocalDateTime.now());
        testEvent.setDeleted(false);
        testEvent.setCreateTime(LocalDateTime.now().minusHours(2));
        testEvent.setUpdateTime(LocalDateTime.now());

        testEventList = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            HotEvent event = new HotEvent();
            event.setId((long) i);
            event.setTitle("测试事件" + i);
            event.setSource(i % 2 == 0 ? "weibo" : "zhihu");
            event.setHotValue(1000000L - i * 10000L);
            event.setCategory(i % 3 == 0 ? "科技" : "社会");
            event.setCrawlTime(LocalDateTime.now());
            event.setDeleted(false);
            testEventList.add(event);
        }
    }

    @Nested
    @DisplayName("热点事件列表查询")
    class GetHotEventListTests {

        @Test
        @DisplayName("正常场景 - 查询热点事件列表成功")
        void testGetHotEventList_Success() {
            Page<HotEvent> page = new PageImpl<>(testEventList, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "hotValue", "id")), 100);
            when(hotEventCacheService.getEventList(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(null);
            when(hotEventRepository.findAll(any(Specification.class), any(PageRequest.class)))
                    .thenReturn(page);

            PageResult<HotEvent> result = hotEventService.getHotEventList(null, null, 1, 10);

            assertNotNull(result);
            assertEquals(10, result.getRecords().size());
            assertEquals(100, result.getTotal());
            assertEquals(1, result.getCurrent());
            assertEquals(10, result.getSize());
            verify(hotEventCacheService, times(1)).cacheEventList(any(), any(), any(), any(), any(), anyInt(), anyInt(), any());
        }

        @Test
        @DisplayName("缓存命中 - 直接从缓存返回结果")
        void testGetHotEventList_CacheHit() {
            PageResult<HotEvent> cachedResult = PageResult.of(testEventList, 100, 1, 10);
            when(hotEventCacheService.getEventList(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(cachedResult);

            PageResult<HotEvent> result = hotEventService.getHotEventList(null, null, 1, 10);

            assertNotNull(result);
            assertEquals(10, result.getRecords().size());
            verify(hotEventRepository, never()).findAll(any(Specification.class), any(PageRequest.class));
        }

        @Test
        @DisplayName("边界条件 - 第一页数据")
        void testGetHotEventList_FirstPage() {
            Page<HotEvent> page = new PageImpl<>(testEventList.subList(0, 5), PageRequest.of(0, 5), 100);
            when(hotEventCacheService.getEventList(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(null);
            when(hotEventRepository.findAll(any(Specification.class), any(PageRequest.class)))
                    .thenReturn(page);

            PageResult<HotEvent> result = hotEventService.getHotEventList(null, null, 1, 5);

            assertNotNull(result);
            assertEquals(5, result.getRecords().size());
            assertEquals(1, result.getRecords().get(0).getHotRank());
        }

        @Test
        @DisplayName("边界条件 - 空结果列表")
        void testGetHotEventList_EmptyResult() {
            Page<HotEvent> page = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);
            when(hotEventCacheService.getEventList(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(null);
            when(hotEventRepository.findAll(any(Specification.class), any(PageRequest.class)))
                    .thenReturn(page);

            PageResult<HotEvent> result = hotEventService.getHotEventList(null, null, 1, 10);

            assertNotNull(result);
            assertTrue(result.getRecords().isEmpty());
            assertEquals(0, result.getTotal());
        }

        @Test
        @DisplayName("按来源筛选 - 微博来源")
        void testGetHotEventList_BySource() {
            List<HotEvent> weiboEvents = testEventList.stream()
                    .filter(e -> "weibo".equals(e.getSource()))
                    .toList();
            Page<HotEvent> page = new PageImpl<>(weiboEvents, PageRequest.of(0, 10), weiboEvents.size());
            when(hotEventCacheService.getEventList(eq("weibo"), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(null);
            when(hotEventRepository.findAll(any(Specification.class), any(PageRequest.class)))
                    .thenReturn(page);

            PageResult<HotEvent> result = hotEventService.getHotEventList("weibo", null, 1, 10);

            assertNotNull(result);
            assertTrue(result.getRecords().stream().allMatch(e -> "weibo".equals(e.getSource())));
        }

        @Test
        @DisplayName("按关键词搜索 - 模糊匹配标题")
        void testGetHotEventList_ByKeyword() {
            List<HotEvent> matchedEvents = testEventList.stream()
                    .filter(e -> e.getTitle().contains("1"))
                    .toList();
            Page<HotEvent> page = new PageImpl<>(matchedEvents, PageRequest.of(0, 10), matchedEvents.size());
            when(hotEventCacheService.getEventList(any(), eq("1"), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(null);
            when(hotEventRepository.findAll(any(Specification.class), any(PageRequest.class)))
                    .thenReturn(page);

            PageResult<HotEvent> result = hotEventService.getHotEventList(null, "1", 1, 10);

            assertNotNull(result);
            assertTrue(result.getRecords().stream().allMatch(e -> e.getTitle().contains("1")));
        }

        @Test
        @DisplayName("按时间范围筛选")
        void testGetHotEventList_ByTimeRange() {
            LocalDateTime startTime = LocalDateTime.now().minusDays(1);
            LocalDateTime endTime = LocalDateTime.now();
            Page<HotEvent> page = new PageImpl<>(testEventList, PageRequest.of(0, 10), 10);
            when(hotEventCacheService.getEventList(any(), any(), any(), eq(startTime), eq(endTime), anyInt(), anyInt()))
                    .thenReturn(null);
            when(hotEventRepository.findAll(any(Specification.class), any(PageRequest.class)))
                    .thenReturn(page);

            PageResult<HotEvent> result = hotEventService.getHotEventList(null, null, null, startTime, endTime, 1, 10);

            assertNotNull(result);
            assertEquals(10, result.getRecords().size());
        }

        @Test
        @DisplayName("异常情况 - 数据库查询异常")
        void testGetHotEventList_DatabaseException() {
            when(hotEventCacheService.getEventList(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(null);
            when(hotEventRepository.findAll(any(Specification.class), any(PageRequest.class)))
                    .thenThrow(new RuntimeException("数据库连接失败"));

            assertThrows(RuntimeException.class, () -> {
                hotEventService.getHotEventList(null, null, 1, 10);
            });
        }
    }

    @Nested
    @DisplayName("热点事件详情查询")
    class GetHotEventByIdTests {

        @Test
        @DisplayName("正常场景 - 根据ID查询事件成功")
        void testGetHotEventById_Success() {
            when(hotEventCacheService.getEventById(eq(1L))).thenReturn(null);
            when(hotEventRepository.findById(eq(1L))).thenReturn(Optional.of(testEvent));

            HotEvent result = hotEventService.getHotEventById(1L);

            assertNotNull(result);
            assertEquals(1L, result.getId());
            assertEquals("测试热点事件", result.getTitle());
            verify(hotEventCacheService, times(1)).cacheEvent(any());
        }

        @Test
        @DisplayName("缓存命中 - 从缓存获取事件")
        void testGetHotEventById_CacheHit() {
            when(hotEventCacheService.getEventById(eq(1L))).thenReturn(testEvent);

            HotEvent result = hotEventService.getHotEventById(1L);

            assertNotNull(result);
            assertEquals(1L, result.getId());
            verify(hotEventRepository, never()).findById(anyLong());
        }

        @Test
        @DisplayName("边界条件 - 事件不存在")
        void testGetHotEventById_NotFound() {
            when(hotEventCacheService.getEventById(eq(999L))).thenReturn(null);
            when(hotEventRepository.findById(eq(999L))).thenReturn(Optional.empty());

            HotEvent result = hotEventService.getHotEventById(999L);

            assertNull(result);
        }

        @Test
        @DisplayName("边界条件 - ID为null")
        void testGetHotEventById_NullId() {
            when(hotEventCacheService.getEventById(isNull())).thenReturn(null);
            when(hotEventRepository.findById(isNull())).thenReturn(Optional.empty());

            HotEvent result = hotEventService.getHotEventById(null);

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("热点事件保存")
    class SaveHotEventTests {

        @Test
        @DisplayName("正常场景 - 新增热点事件成功")
        void testSaveHotEvent_CreateNew() {
            HotEvent newEvent = new HotEvent();
            newEvent.setTitle("新热点事件");
            newEvent.setSource("weibo");
            newEvent.setHotValue(500000L);
            when(hotEventRepository.save(any(HotEvent.class))).thenAnswer(invocation -> {
                HotEvent saved = invocation.getArgument(0);
                saved.setId(100L);
                saved.setCreateTime(LocalDateTime.now());
                saved.setUpdateTime(LocalDateTime.now());
                return saved;
            });
            when(i18nProperties.getTranslation()).thenReturn(mock(I18nProperties.TranslationProperties.class));
            when(i18nProperties.getTranslation().isAutoTranslateOnCrawl()).thenReturn(false);

            HotEvent result = hotEventService.saveHotEvent(newEvent);

            assertNotNull(result);
            assertNotNull(result.getId());
            assertEquals("新热点事件", result.getTitle());
            verify(hotEventLogService, times(1)).logInsert(any(), anyString());
            verify(hotEventCacheService, times(1)).onEventChanged(anyLong());
        }

        @Test
        @DisplayName("正常场景 - 更新热点事件成功")
        void testSaveHotEvent_UpdateExisting() {
            testEvent.setTitle("更新后的标题");
            when(hotEventRepository.findById(eq(1L))).thenReturn(Optional.of(testEvent));
            when(hotEventRepository.save(any(HotEvent.class))).thenReturn(testEvent);
            when(i18nProperties.getTranslation()).thenReturn(mock(I18nProperties.TranslationProperties.class));
            when(i18nProperties.getTranslation().isAutoTranslateOnCrawl()).thenReturn(false);

            HotEvent result = hotEventService.saveHotEvent(testEvent);

            assertNotNull(result);
            assertEquals("更新后的标题", result.getTitle());
            verify(hotEventLogService, times(1)).logUpdate(any(), any(), anyString());
        }

        @Test
        @DisplayName("边界条件 - 保存null事件")
        void testSaveHotEvent_NullEvent() {
            when(hotEventRepository.save(isNull())).thenThrow(new IllegalArgumentException("Entity must not be null"));

            assertThrows(IllegalArgumentException.class, () -> {
                hotEventService.saveHotEvent(null);
            });
        }

        @Test
        @DisplayName("异常情况 - 保存时数据库异常")
        void testSaveHotEvent_DatabaseException() {
            when(hotEventRepository.save(any(HotEvent.class)))
                    .thenThrow(new RuntimeException("数据库写入失败"));

            assertThrows(RuntimeException.class, () -> {
                hotEventService.saveHotEvent(testEvent);
            });
        }
    }

    @Nested
    @DisplayName("热点事件删除")
    class DeleteHotEventTests {

        @Test
        @DisplayName("正常场景 - 删除热点事件成功")
        void testDeleteHotEvent_Success() {
            when(hotEventRepository.findById(eq(1L))).thenReturn(Optional.of(testEvent));
            when(hotEventRepository.save(any(HotEvent.class))).thenReturn(testEvent);

            boolean result = hotEventService.deleteHotEvent(1L);

            assertTrue(result);
            assertTrue(testEvent.getDeleted());
            verify(hotEventLogService, times(1)).logDelete(any(), anyString());
            verify(hotEventCacheService, times(1)).onEventChanged(eq(1L));
        }

        @Test
        @DisplayName("边界条件 - 删除不存在的事件")
        void testDeleteHotEvent_NotFound() {
            when(hotEventRepository.findById(eq(999L))).thenReturn(Optional.empty());

            boolean result = hotEventService.deleteHotEvent(999L);

            assertFalse(result);
            verify(hotEventLogService, never()).logDelete(any(), anyString());
        }

        @Test
        @DisplayName("边界条件 - ID为null")
        void testDeleteHotEvent_NullId() {
            when(hotEventRepository.findById(isNull())).thenReturn(Optional.empty());

            boolean result = hotEventService.deleteHotEvent(null);

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("可用来源查询")
    class GetAvailableSourcesTests {

        @Test
        @DisplayName("正常场景 - 获取所有可用来源")
        void testGetAvailableSources_Success() {
            List<String> sources = Arrays.asList("weibo", "zhihu", "baidu", "douyin", "bilibili");
            when(hotEventCacheService.getSources()).thenReturn(null);
            when(hotEventRepository.findDistinctSources()).thenReturn(sources);

            List<String> result = hotEventService.getAvailableSources();

            assertNotNull(result);
            assertEquals(5, result.size());
            assertTrue(result.contains("weibo"));
            verify(hotEventCacheService, times(1)).cacheSources(any());
        }

        @Test
        @DisplayName("缓存命中 - 从缓存获取来源列表")
        void testGetAvailableSources_CacheHit() {
            List<String> cachedSources = Arrays.asList("weibo", "zhihu");
            when(hotEventCacheService.getSources()).thenReturn(cachedSources);

            List<String> result = hotEventService.getAvailableSources();

            assertNotNull(result);
            assertEquals(2, result.size());
            verify(hotEventRepository, never()).findDistinctSources();
        }

        @Test
        @DisplayName("边界条件 - 无数据来源")
        void testGetAvailableSources_Empty() {
            when(hotEventCacheService.getSources()).thenReturn(null);
            when(hotEventRepository.findDistinctSources()).thenReturn(Collections.emptyList());

            List<String> result = hotEventService.getAvailableSources();

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("排名重排")
    class RerankTests {

        @Test
        @DisplayName("正常场景 - 按来源重排排名")
        void testRerankBySource_Success() {
            List<HotEvent> events = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                HotEvent e = new HotEvent();
                e.setId((long) i);
                e.setSource("weibo");
                e.setHotValue(1000000L - i * 100000L);
                e.setCrawlTime(LocalDateTime.now());
                events.add(e);
            }
            when(hotEventRepository.findBySourceAndCrawlTimeAfterOrderByHotValueDesc(
                    eq("weibo"), any(LocalDateTime.class))).thenReturn(events);
            when(hotEventRepository.save(any(HotEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

            int count = hotEventService.rerankBySource("weibo");

            assertEquals(5, count);
            for (int i = 0; i < 5; i++) {
                assertEquals(i + 1, events.get(i).getHotRank());
            }
        }

        @Test
        @DisplayName("边界条件 - 来源为null")
        void testRerankBySource_NullSource() {
            int count = hotEventService.rerankBySource(null);
            assertEquals(0, count);
        }

        @Test
        @DisplayName("边界条件 - 来源为空字符串")
        void testRerankBySource_EmptySource() {
            int count = hotEventService.rerankBySource("");
            assertEquals(0, count);
        }

        @Test
        @DisplayName("边界条件 - 无数据需要重排")
        void testRerankBySource_NoData() {
            when(hotEventRepository.findBySourceAndCrawlTimeAfterOrderByHotValueDesc(
                    eq("unknown"), any(LocalDateTime.class))).thenReturn(Collections.emptyList());

            int count = hotEventService.rerankBySource("unknown");
            assertEquals(0, count);
        }
    }

    @Nested
    @DisplayName("统计数据查询")
    class GetStatisticsTests {

        @Test
        @DisplayName("正常场景 - 获取统计数据成功")
        void testGetStatistics_Success() {
            List<Object[]> sourceCounts = new ArrayList<>();
            sourceCounts.add(new Object[]{"weibo", 500L});
            sourceCounts.add(new Object[]{"zhihu", 300L});

            List<Object[]> categoryCounts = new ArrayList<>();
            categoryCounts.add(new Object[]{"社会", 400L});
            categoryCounts.add(new Object[]{"科技", 300L});

            List<HotEvent> topEvents = testEventList.subList(0, 10);

            when(hotEventCacheService.getStatistics()).thenReturn(null);
            when(hotEventRepository.countBySource()).thenReturn(sourceCounts);
            when(hotEventRepository.countByCategory()).thenReturn(categoryCounts);
            when(hotEventRepository.findTopHotEvents(any(LocalDateTime.class), any(PageRequest.class)))
                    .thenReturn(topEvents);
            when(asyncTaskExecutor.getIoExecutor()).thenReturn(Runnable::run);

            Map<String, Object> result = hotEventService.getStatistics();

            assertNotNull(result);
            assertTrue(result.containsKey("sourceStats"));
            assertTrue(result.containsKey("categoryStats"));
            assertTrue(result.containsKey("topEvents"));
            assertTrue(result.containsKey("totalCount"));
            assertEquals(800L, result.get("totalCount"));
            verify(hotEventCacheService, times(1)).cacheStatistics(any());
        }

        @Test
        @DisplayName("缓存命中 - 从缓存获取统计数据")
        void testGetStatistics_CacheHit() {
            Map<String, Object> cachedStats = new HashMap<>();
            cachedStats.put("totalCount", 1000L);
            when(hotEventCacheService.getStatistics()).thenReturn(cachedStats);

            Map<String, Object> result = hotEventService.getStatistics();

            assertNotNull(result);
            assertEquals(1000L, result.get("totalCount"));
            verify(hotEventRepository, never()).countBySource();
        }
    }

    @Nested
    @DisplayName("翻译功能")
    class TranslationTests {

        @Test
        @DisplayName("正常场景 - 中文本地化直接返回")
        void testLocalizeHotEvent_ChineseLocale() {
            Map<String, Object> result = hotEventService.localizeHotEvent(testEvent, "zh-CN");

            assertNotNull(result);
            assertEquals("测试热点事件", result.get("title"));
            assertEquals("zh-CN", result.get("translatedLanguage"));
            assertFalse((Boolean) result.get("isTranslated"));
        }

        @Test
        @DisplayName("正常场景 - 英文本地化从数据库获取翻译")
        void testLocalizeHotEvent_EnglishFromDb() {
            EventTranslation translation = new EventTranslation();
            translation.setEventId(1L);
            translation.setLanguage("en");
            translation.setTitle("Test Hot Event");
            translation.setDescription("This is a test hot event description");
            translation.setCategory("Society");

            when(translationCacheService.getEventTranslation(eq(1L), eq("en"))).thenReturn(null);
            when(eventTranslationRepository.findByEventIdAndLanguage(eq(1L), eq("en")))
                    .thenReturn(Optional.of(translation));

            Map<String, Object> result = hotEventService.localizeHotEvent(testEvent, "en");

            assertNotNull(result);
            assertEquals("Test Hot Event", result.get("title"));
            assertEquals("Society", result.get("category"));
            assertTrue((Boolean) result.get("isTranslated"));
            verify(translationCacheService, times(1)).cacheEventTranslation(any());
        }

        @Test
        @DisplayName("边界条件 - 事件为null")
        void testLocalizeHotEvent_NullEvent() {
            Map<String, Object> result = hotEventService.localizeHotEvent(null, "en");
            assertNull(result);
        }

        @Test
        @DisplayName("边界条件 - 语言为null")
        void testLocalizeHotEvent_NullLanguage() {
            Map<String, Object> result = hotEventService.localizeHotEvent(testEvent, null);

            assertNotNull(result);
            assertEquals("测试热点事件", result.get("title"));
        }
    }
}
