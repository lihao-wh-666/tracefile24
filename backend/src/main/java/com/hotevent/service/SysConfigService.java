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
    public static final String KEY_SESSION_TIMEOUT_MINUTES = "sessionTimeoutMinutes";
    public static final String KEY_SESSION_WARNING_MINUTES = "sessionWarningMinutes";
    public static final String KEY_MESSAGE_DURATION = "messageDuration";
    public static final String KEY_CRAWL_INTERVAL_MINUTES = "crawlIntervalMinutes";

    public static final int DEFAULT_MAX_LOGIN_ATTEMPTS = 5;
    public static final int DEFAULT_LOGIN_LOCK_MINUTES = 30;
    public static final int DEFAULT_LOGIN_ATTEMPT_WINDOW_MINUTES = 30;
    public static final int DEFAULT_SESSION_TIMEOUT_MINUTES = 30;
    public static final int DEFAULT_SESSION_WARNING_MINUTES = 5;
    public static final int DEFAULT_MESSAGE_DURATION = 1500;
    public static final int DEFAULT_CRAWL_INTERVAL_MINUTES = 30;

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

    public int getSessionTimeoutMinutes() {
        return getIntValue(KEY_SESSION_TIMEOUT_MINUTES, DEFAULT_SESSION_TIMEOUT_MINUTES);
    }

    public int getSessionWarningMinutes() {
        return getIntValue(KEY_SESSION_WARNING_MINUTES, DEFAULT_SESSION_WARNING_MINUTES);
    }

    public int getMessageDuration() {
        return getIntValue(KEY_MESSAGE_DURATION, DEFAULT_MESSAGE_DURATION);
    }

    public int getCrawlIntervalMinutes() {
        return getIntValue(KEY_CRAWL_INTERVAL_MINUTES, DEFAULT_CRAWL_INTERVAL_MINUTES);
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
        if (isSystemConfig(config.getConfigKey())) {
            throw new RuntimeException("系统内置配置不允许删除");
        }
        sysConfigRepository.deleteById(id);
        log.info("删除系统配置成功: key={}", config.getConfigKey());
    }

    public boolean isSystemConfig(String configKey) {
        return KEY_MAX_LOGIN_ATTEMPTS.equals(configKey)
                || KEY_LOGIN_LOCK_MINUTES.equals(configKey)
                || KEY_LOGIN_ATTEMPT_WINDOW_MINUTES.equals(configKey)
                || KEY_SESSION_TIMEOUT_MINUTES.equals(configKey)
                || KEY_SESSION_WARNING_MINUTES.equals(configKey)
                || KEY_MESSAGE_DURATION.equals(configKey)
                || KEY_CRAWL_INTERVAL_MINUTES.equals(configKey);
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
        if (!sysConfigRepository.existsByConfigKey(KEY_SESSION_TIMEOUT_MINUTES)) {
            save(KEY_SESSION_TIMEOUT_MINUTES, String.valueOf(DEFAULT_SESSION_TIMEOUT_MINUTES),
                    "登录超时时间(分钟)", "用户无操作后自动登出的时间");
        }
        if (!sysConfigRepository.existsByConfigKey(KEY_SESSION_WARNING_MINUTES)) {
            save(KEY_SESSION_WARNING_MINUTES, String.valueOf(DEFAULT_SESSION_WARNING_MINUTES),
                    "超时警告提前时间(分钟)", "在超时前多久弹出提示警告用户");
        }
        if (!sysConfigRepository.existsByConfigKey(KEY_MESSAGE_DURATION)) {
            save(KEY_MESSAGE_DURATION, String.valueOf(DEFAULT_MESSAGE_DURATION),
                    "消息提示显示时长(毫秒)", "ElMessage消息提示框的显示持续时间");
        }
        if (!sysConfigRepository.existsByConfigKey(KEY_CRAWL_INTERVAL_MINUTES)) {
            save(KEY_CRAWL_INTERVAL_MINUTES, String.valueOf(DEFAULT_CRAWL_INTERVAL_MINUTES),
                    "定时抓取间隔(分钟)", "定时抓取热点事件的时间间隔，修改后实时生效");
        }
    }
}
