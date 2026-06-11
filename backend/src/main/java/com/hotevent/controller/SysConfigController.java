package com.hotevent.controller;

import com.hotevent.common.Result;
import com.hotevent.entity.SysConfig;
import com.hotevent.service.SysConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sys-configs")
@PreAuthorize("hasRole('ADMIN')")
public class SysConfigController {

    @Autowired
    private SysConfigService sysConfigService;

    @GetMapping
    public Result<List<SysConfig>> list() {
        return Result.success(sysConfigService.listAll());
    }

    @GetMapping("/{key}")
    public Result<SysConfig> getByKey(@PathVariable String key) {
        return sysConfigService.getByKey(key)
                .map(Result::success)
                .orElse(Result.error("配置不存在"));
    }

    @PostMapping
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
    public Result<SysConfig> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String configValue = body.get("configValue");
        String configName = body.get("configName");
        String description = body.get("description");
        if (configValue == null || configValue.trim().isEmpty()) {
            return Result.error("配置值不能为空");
        }
        return Result.success("更新成功", sysConfigService.update(id, configValue, configName, description));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysConfigService.delete(id);
        return Result.success("删除成功", null);
    }
}
