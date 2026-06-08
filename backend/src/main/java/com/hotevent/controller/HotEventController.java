package com.hotevent.controller;

import com.hotevent.common.PageResult;
import com.hotevent.common.Result;
import com.hotevent.entity.HotEvent;
import com.hotevent.service.HotEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/events")
@CrossOrigin(origins = "*")
public class HotEventController {

    @Autowired
    private HotEventService hotEventService;

    @GetMapping
    public Result<PageResult<HotEvent>> getHotEventList(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResult<HotEvent> result = hotEventService.getHotEventList(source, keyword, page, size);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<HotEvent> getHotEventById(@PathVariable Long id) {
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
}
