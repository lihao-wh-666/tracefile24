package com.hotevent.controller;

import com.hotevent.common.PageResult;
import com.hotevent.common.Result;
import com.hotevent.entity.EventTranslation;
import com.hotevent.entity.HotEvent;
import com.hotevent.service.HotEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/events")
public class HotEventController {

    @Autowired
    private HotEventService hotEventService;

    @GetMapping
    public Result<?> getHotEventList(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String lang) {

        if (lang != null && !lang.isEmpty() && !"zh-CN".equals(lang)) {
            PageResult<Map<String, Object>> result = hotEventService.getHotEventListLocalized(source, keyword, page, size, lang);
            return Result.success(result);
        }

        PageResult<HotEvent> result = hotEventService.getHotEventList(source, keyword, page, size);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<?> getHotEventById(
            @PathVariable Long id,
            @RequestParam(required = false) String lang) {

        if (lang != null && !lang.isEmpty() && !"zh-CN".equals(lang)) {
            Map<String, Object> event = hotEventService.getHotEventByIdLocalized(id, lang);
            if (event == null) {
                return Result.error("热点事件不存在");
            }
            return Result.success(event);
        }

        HotEvent event = hotEventService.getHotEventById(id);
        if (event == null) {
            return Result.error("热点事件不存在");
        }
        return Result.success(event);
    }

    @GetMapping("/sources")
    public Result<List<String>> getAvailableSources() {
        List<String> sources = hotEventService.getAvailableSources();
        return Result.success(sources);
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics() {
        Map<String, Object> statistics = hotEventService.getStatistics();
        return Result.success(statistics);
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteHotEvent(@PathVariable Long id) {
        boolean success = hotEventService.deleteHotEvent(id);
        if (success) {
            return Result.success();
        } else {
            return Result.error("删除失败");
        }
    }

    @PostMapping
    public Result<HotEvent> saveHotEvent(@RequestBody HotEvent hotEvent) {
        HotEvent saved = hotEventService.saveHotEvent(hotEvent);
        return Result.success(saved);
    }

    @GetMapping("/{id}/translations")
    public Result<List<EventTranslation>> getEventTranslations(@PathVariable Long id) {
        List<EventTranslation> translations = hotEventService.getEventTranslations(id);
        return Result.success(translations);
    }

    @PutMapping("/{id}/translations/{language}")
    public Result<EventTranslation> updateEventTranslation(
            @PathVariable Long id,
            @PathVariable String language,
            @RequestBody Map<String, String> body) {
        String title = body.get("title");
        String description = body.get("description");
        String category = body.get("category");

        EventTranslation translation = hotEventService.updateTranslation(id, language, title, description, category);
        return Result.success(translation);
    }
}
