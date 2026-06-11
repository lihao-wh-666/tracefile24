package com.hotevent.service;

import com.hotevent.entity.SysConfig;
import com.hotevent.repository.SysConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class SysConfigService {

    public static final String KEY_MAX_LOGIN_ATTEMPTS = "maxLoginAttempts";
    public static final String KEY_LOGIN_LOCK_MINUTES = "loginLockMinutes";
    public static final String KEY_LOGIN_ATTEMPT_WINDOW_MINUTES = "loginAttemptWindowMinutes";

    public static final int DEFAULT_MAX_LOGIN_ATTEMPTS = 5;
    public static final int DEFAULT_LOGIN_LOCK_MINUTES = 30;
    public static final int DEFAULT_LOGIN_ATTEMPT_WINDOW_MINUTES = 30;

    @Autowired
    private SysConfigRepository sysConfigRepository;

    public List<SysConfig> listAll() {
        return sysConfigRepository.findAll();
    }

    public Optional<SysConfig> getByKey(String configKey) {
        return sysConfigRepository.findByConfigKey(configKey);
    }

    public String getValue(String configKey, String defaultValue) {
        return sysConfigRepository.findByConfigKey(configKey)
                .map(SysConfig::getConfigValue)
                .orElse(defaultValue);
    }

    public int getIntValue(String configKey, int defaultValue) {
        try {
            String value = getValue(configKey, null);
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public int getMaxLoginAttempts() {
        return getIntValue(KEY_MAX_LOGIN_ATTEMPTS, DEFAULT_MAX_LOGIN_ATTEMPTS);
    }

    public int getLoginLockMinutes() {
        return getIntValue(KEY_LOGIN_LOCK_MINUTES, DEFAULT_LOGIN_LOCK_MINUTES);
    }

    public int getLoginAttemptWindowMinutes() {
        return getIntValue(KEY_LOGIN_ATTEMPT_WINDOW_MINUTES, DEFAULT_LOGIN_ATTEMPT_WINDOW_MINUTES);
    }

    @Transactional
    public SysConfig save(String configKey, String configValue, String configName, String description) {
        SysConfig config = sysConfigRepository.findByConfigKey(configKey).orElse(new SysConfig());
        config.setConfigKey(configKey);
        config.setConfigValue(configValue);
        if (configName != null) {
            config.setConfigName(configName);
        }
        if (description != null) {
            config.setDescription(description);
        }
        config = sysConfigRepository.save(config);
        log.info("保存系统配置成功: {}={}", configKey, configValue);
        return config;
    }

    @Transactional
    public SysConfig update(Long id, String configValue, String configName, String description) {
        SysConfig config = sysConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("配置不存在"));
        config.setConfigValue(configValue);
        if (configName != null) {
            config.setConfigName(configName);
        }
        if (description != null) {
            config.setDescription(description);
        }
        config = sysConfigRepository.save(config);
        log.info("更新系统配置成功: {}={}", config.getConfigKey(), configValue);
        return config;
    }

    @Transactional
    public SysConfig create(String configKey, String configValue, String configName, String description) {
        if (sysConfigRepository.existsByConfigKey(configKey)) {
            throw new RuntimeException("配置键已存在: " + configKey);
        }
        SysConfig config = new SysConfig();
        config.setConfigKey(configKey);
        config.setConfigValue(configValue);
        config.setConfigName(configName);
        config.setDescription(description);
        config = sysConfigRepository.save(config);
        log.info("创建系统配置成功: {}={}", configKey, configValue);
        return config;
    }

    @Transactional
    public void delete(Long id) {
        SysConfig config = sysConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("配置不存在"));
        if (KEY_MAX_LOGIN_ATTEMPTS.equals(config.getConfigKey())
                || KEY_LOGIN_LOCK_MINUTES.equals(config.getConfigKey())
                || KEY_LOGIN_ATTEMPT_WINDOW_MINUTES.equals(config.getConfigKey())) {
            throw new RuntimeException("系统内置配置不允许删除");
        }
        sysConfigRepository.deleteById(id);
        log.info("删除系统配置成功: key={}", config.getConfigKey());
    }

    public void initDefaultConfigs() {
        if (!sysConfigRepository.existsByConfigKey(KEY_MAX_LOGIN_ATTEMPTS)) {
            save(KEY_MAX_LOGIN_ATTEMPTS, String.valueOf(DEFAULT_MAX_LOGIN_ATTEMPTS),
                    "最大登录失败次数", "用户在锁定时间窗口内允许的最大密码错误次数");
        }
        if (!sysConfigRepository.existsByConfigKey(KEY_LOGIN_LOCK_MINUTES)) {
            save(KEY_LOGIN_LOCK_MINUTES, String.valueOf(DEFAULT_LOGIN_LOCK_MINUTES),
                    "账号锁定时间(分钟)", "超过最大失败次数后账号被锁定的时间");
        }
        if (!sysConfigRepository.existsByConfigKey(KEY_LOGIN_ATTEMPT_WINDOW_MINUTES)) {
            save(KEY_LOGIN_ATTEMPT_WINDOW_MINUTES, String.valueOf(DEFAULT_LOGIN_ATTEMPT_WINDOW_MINUTES),
                    "登录失败统计窗口(分钟)", "统计登录失败次数的时间窗口");
        }
    }
}
