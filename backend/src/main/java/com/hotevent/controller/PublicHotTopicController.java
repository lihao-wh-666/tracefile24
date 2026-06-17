package com.hotevent.controller;

import com.hotevent.common.PageResult;
import com.hotevent.common.Result;
import com.hotevent.dto.HotTopicVO;
import com.hotevent.entity.HotEvent;
import com.hotevent.service.HotEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/public/hot-topics")
public class PublicHotTopicController {

    @Autowired
    private HotEventService hotEventService;

    @GetMapping
    public Result<PageResult<HotTopicVO>> listHotTopics(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {

        if (page < 1) page = 1;
        if (size < 1) size = 1;
        if (size > 100) size = 100;

        PageResult<HotEvent> eventPage = hotEventService.getHotEventList(
                source, keyword, category, startTime, endTime, page, size);

        List<HotTopicVO> voList = eventPage.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        PageResult<HotTopicVO> result = PageResult.of(
                voList, eventPage.getTotal(), eventPage.getCurrent(), eventPage.getSize());

        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<HotTopicVO> getHotTopic(@PathVariable Long id) {
        HotEvent event = hotEventService.getHotEventById(id);
        if (event == null) {
            return Result.error(404, "热点不存在");
        }
        return Result.success(toVO(event));
    }

    @GetMapping("/trending")
    public Result<List<HotTopicVO>> getTrending(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "10") int limit) {

        if (limit < 1) limit = 1;
        if (limit > 50) limit = 50;

        LocalDateTime startTime = LocalDateTime.now().minusHours(24);
        PageResult<HotEvent> eventPage = hotEventService.getHotEventList(
                source, null, category, startTime, null, 1, limit);

        List<HotTopicVO> voList = eventPage.getRecords().stream()
                .filter(e -> Boolean.TRUE.equals(e.getIsRising()) || Boolean.TRUE.equals(e.getIsHot()))
                .map(this::toVO)
                .collect(Collectors.toList());

        return Result.success(voList);
    }

    @GetMapping("/sources")
    public Result<List<String>> getSources() {
        return Result.success(hotEventService.getAvailableSources());
    }

    @GetMapping("/categories")
    public Result<List<String>> getCategories() {
        return Result.success(hotEventService.getAvailableCategories());
    }

    private HotTopicVO toVO(HotEvent event) {
        HotTopicVO vo = new HotTopicVO();
        vo.setId(event.getId());
        vo.setTitle(event.getTitle());
        vo.setDescription(event.getDescription());
        vo.setSource(event.getSource());
        vo.setSourceUrl(event.getSourceUrl());
        vo.setHotValue(event.getHotValue());
        vo.setHotRank(event.getHotRank());
        vo.setCategory(event.getCategory());
        vo.setImageUrl(event.getImageUrl());
        vo.setIsHot(event.getIsHot());
        vo.setIsRising(event.getIsRising());
        vo.setRisingRate(event.getRisingRate());
        vo.setPublishTime(event.getFirstSeenTime());
        vo.setCrawlTime(event.getCrawlTime());
        return vo;
    }
}
