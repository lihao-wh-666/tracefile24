package com.hotevent.controller;

import com.hotevent.common.Result;
import com.hotevent.crawler.filter.SensitiveCheckResult;
import com.hotevent.crawler.filter.SensitiveContentFilter;
import com.hotevent.crawler.filter.SensitiveType;
import com.hotevent.service.SysConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/sensitive-content")
public class SensitiveContentController {

    @Autowired
    private SensitiveContentFilter sensitiveContentFilter;

    @Autowired
    private SysConfigService sysConfigService;

    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> getStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", sensitiveContentFilter.isFilterEnabled());
        result.put("totalKeywordCount", sensitiveContentFilter.getTotalKeywordCount());

        List<Map<String, Object>> typeStats = new ArrayList<>();
        for (SensitiveType type : SensitiveType.values()) {
            Map<String, Object> stat = new LinkedHashMap<>();
            stat.put("code", type.getCode());
            stat.put("displayName", type.getDisplayName());
            stat.put("keywordCount", sensitiveContentFilter.getKeywords(type).size());
            typeStats.add(stat);
        }
        result.put("typeStats", typeStats);
        return Result.success(result);
    }

    @PostMapping("/check")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<SensitiveCheckResult> checkContent(@RequestBody Map<String, String> body) {
        String text = body.get("text");
        if (text == null || text.trim().isEmpty()) {
            return Result.success(new SensitiveCheckResult());
        }
        SensitiveCheckResult result = sensitiveContentFilter.check(text);
        return Result.success(result);
    }

    @GetMapping("/config")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> getAllConfig() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", sysConfigService.isSensitiveFilterEnabled());

        Map<String, Map<String, String>> typeConfigs = new LinkedHashMap<>();
        for (SensitiveType type : SensitiveType.values()) {
            Map<String, String> cfg = new LinkedHashMap<>();
            cfg.put("keywords", sysConfigService.getSensitiveKeywords(type.getCode()));
            cfg.put("regex", sysConfigService.getSensitiveRegex(type.getCode()));
            typeConfigs.put(type.getCode(), cfg);
        }
        result.put("typeConfigs", typeConfigs);
        return Result.success(result);
    }

    @PutMapping("/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> toggleFilter(@RequestBody Map<String, Boolean> body) {
        Boolean enabled = body.get("enabled");
        if (enabled == null) {
            return Result.error("参数错误");
        }
        sysConfigService.save(
                SysConfigService.KEY_SENSITIVE_FILTER_ENABLED,
                String.valueOf(enabled),
                "敏感内容过滤开关",
                "是否启用敏感内容过滤功能"
        );
        sensitiveContentFilter.loadConfigFromSysConfig();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", sensitiveContentFilter.isFilterEnabled());
        return Result.success("更新成功", result);
    }

    @PutMapping("/keywords/{typeCode}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> updateKeywords(@PathVariable String typeCode, @RequestBody Map<String, String> body) {
        String keywords = body.get("keywords");
        if (keywords == null) keywords = "";

        SensitiveType type = SensitiveType.fromCode(typeCode);
        String configKey = SysConfigService.KEY_SENSITIVE_KEYWORDS_PREFIX + typeCode;
        String configName = type.getDisplayName() + "敏感词";

        sysConfigService.save(configKey, keywords, configName, "多个关键词用英文逗号、中文逗号或空格分隔");
        sensitiveContentFilter.loadConfigFromSysConfig();

        log.info("更新敏感词配置: type={}, keywords={}", typeCode, keywords);
        return Result.success("保存成功", null);
    }

    @PutMapping("/regex/{typeCode}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> updateRegex(@PathVariable String typeCode, @RequestBody Map<String, String> body) {
        String regex = body.get("regex");
        if (regex == null) regex = "";

        SensitiveType type = SensitiveType.fromCode(typeCode);
        String configKey = SysConfigService.KEY_SENSITIVE_REGEX_PREFIX + typeCode;
        String configName = type.getDisplayName() + "正则表达式";

        sysConfigService.save(configKey, regex, configName, "多个正则表达式用 || 分隔");
        sensitiveContentFilter.loadConfigFromSysConfig();

        log.info("更新敏感正则配置: type={}, regex={}", typeCode, regex);
        return Result.success("保存成功", null);
    }

    @PostMapping("/reload")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> reloadConfig() {
        sensitiveContentFilter.loadConfigFromSysConfig();
        log.info("敏感内容过滤配置已重新加载");
        return Result.success("重新加载成功", null);
    }
}
