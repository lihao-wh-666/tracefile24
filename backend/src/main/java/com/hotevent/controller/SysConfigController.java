package com.hotevent.controller;

import com.hotevent.common.Result;
import com.hotevent.entity.SysConfig;
import com.hotevent.service.SysConfigService;
import com.hotevent.task.CrawlScheduleManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sys-configs")
public class SysConfigController {

    @Autowired
    private SysConfigService sysConfigService;

    @Autowired
    private CrawlScheduleManager crawlScheduleManager;

    @GetMapping("/session-timeout")
    public Result<Map<String, Integer>> getSessionTimeoutConfig() {
        Map<String, Integer> config = new HashMap<>();
        config.put("sessionTimeoutMinutes", sysConfigService.getSessionTimeoutMinutes());
        config.put("sessionWarningMinutes", sysConfigService.getSessionWarningMinutes());
        return Result.success(config);
    }

    @GetMapping("/message-config")
    public Result<Map<String, Integer>> getMessageConfig() {
        Map<String, Integer> config = new HashMap<>();
        config.put("messageDuration", sysConfigService.getMessageDuration());
        return Result.success(config);
    }

    @GetMapping("/crawl-interval")
    public Result<Map<String, Object>> getCrawlIntervalConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("crawlIntervalMinutes", sysConfigService.getCrawlIntervalMinutes());
        config.put("currentIntervalMinutes", crawlScheduleManager.getCurrentIntervalMinutes());
        return Result.success(config);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<SysConfig>> list() {
        return Result.success(sysConfigService.listAll());
    }

    @GetMapping("/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<SysConfig> getByKey(@PathVariable String key) {
        return sysConfigService.getByKey(key)
                .map(Result::success)
                .orElse(Result.error("配置不存在"));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<SysConfig> create(@RequestBody Map<String, String> body) {
        String configKey = body.get("configKey");
        String configValue = body.get("configValue");
        String configName = body.get("configName");
        String description = body.get("description");
        if (configKey == null || configKey.trim().isEmpty()) {
            return Result.error("配置键不能为空");
        }
        if (configValue == null || configValue.trim().isEmpty()) {
            return Result.error("配置值不能为空");
        }
        return Result.success("创建成功", sysConfigService.create(configKey, configValue, configName, description));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<SysConfig> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String configValue = body.get("configValue");
        String configName = body.get("configName");
        String description = body.get("description");
        if (configValue == null || configValue.trim().isEmpty()) {
            return Result.error("配置值不能为空");
        }
        SysConfig config = sysConfigService.update(id, configValue, configName, description);
        if (SysConfigService.KEY_CRAWL_INTERVAL_MINUTES.equals(config.getConfigKey())) {
            crawlScheduleManager.reschedule();
        }
        return Result.success("更新成功", config);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        sysConfigService.delete(id);
        return Result.success("删除成功", null);
    }

    @PostMapping("/cache/refresh")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> refreshCache() {
        sysConfigService.refreshCache();
        return Result.success("系统配置缓存刷新成功");
    }
}
