package com.hotevent.service;

import com.hotevent.cache.SysConfigCacheService;
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
    public static final String KEY_SENSITIVE_FILTER_ENABLED = "sensitiveFilterEnabled";
    public static final String KEY_SENSITIVE_KEYWORDS_PREFIX = "sensitiveKeywords.";
    public static final String KEY_SENSITIVE_REGEX_PREFIX = "sensitiveRegex.";

    public static final int DEFAULT_MAX_LOGIN_ATTEMPTS = 5;
    public static final int DEFAULT_LOGIN_LOCK_MINUTES = 30;
    public static final int DEFAULT_LOGIN_ATTEMPT_WINDOW_MINUTES = 30;
    public static final int DEFAULT_SESSION_TIMEOUT_MINUTES = 30;
    public static final int DEFAULT_SESSION_WARNING_MINUTES = 5;
    public static final int DEFAULT_MESSAGE_DURATION = 1500;
    public static final int DEFAULT_CRAWL_INTERVAL_MINUTES = 30;
    public static final boolean DEFAULT_SENSITIVE_FILTER_ENABLED = true;

    @Autowired
    private SysConfigRepository sysConfigRepository;

    @Autowired
    private SysConfigCacheService sysConfigCacheService;

    public List<SysConfig> listAll() {
        List<SysConfig> all = sysConfigRepository.findAll();
        if (all != null && !all.isEmpty()) {
            sysConfigCacheService.cacheBatch(all);
        }
        return all;
    }

    public Optional<SysConfig> getByKey(String configKey) {
        SysConfig cached = sysConfigCacheService.getByKey(configKey);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<SysConfig> dbResult = sysConfigRepository.findByConfigKey(configKey);
        dbResult.ifPresent(sysConfigCacheService::cacheConfig);
        return dbResult;
    }

    public String getValue(String configKey, String defaultValue) {
        String cachedValue = sysConfigCacheService.getValue(configKey);
        if (cachedValue != null) {
            return cachedValue;
        }
        String dbValue = sysConfigRepository.findByConfigKey(configKey)
                .map(SysConfig::getConfigValue)
                .orElse(null);
        if (dbValue != null) {
            sysConfigCacheService.cacheConfig(configKey, dbValue, null, null);
            return dbValue;
        }
        return defaultValue;
    }

    public int getIntValue(String configKey, int defaultValue) {
        try {
            String cached = sysConfigCacheService.getValue(configKey);
            if (cached != null) {
                return Integer.parseInt(cached);
            }
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

    public boolean getBooleanValue(String configKey, boolean defaultValue) {
        try {
            String cached = sysConfigCacheService.getValue(configKey);
            if (cached != null) {
                return Boolean.parseBoolean(cached);
            }
            String value = getValue(configKey, null);
            return value != null ? Boolean.parseBoolean(value) : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public boolean isSensitiveFilterEnabled() {
        return getBooleanValue(KEY_SENSITIVE_FILTER_ENABLED, DEFAULT_SENSITIVE_FILTER_ENABLED);
    }

    public String getSensitiveKeywords(String typeCode) {
        return getValue(KEY_SENSITIVE_KEYWORDS_PREFIX + typeCode, "");
    }

    public String getSensitiveRegex(String typeCode) {
        return getValue(KEY_SENSITIVE_REGEX_PREFIX + typeCode, "");
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
        sysConfigCacheService.cacheConfig(config);
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
        sysConfigCacheService.cacheConfig(config);
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
        sysConfigCacheService.cacheConfig(config);
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
        String configKey = config.getConfigKey();
        sysConfigRepository.deleteById(id);
        sysConfigCacheService.evictConfig(configKey);
        log.info("删除系统配置成功: key={}", configKey);
    }

    public boolean isSystemConfig(String configKey) {
        return KEY_MAX_LOGIN_ATTEMPTS.equals(configKey)
                || KEY_LOGIN_LOCK_MINUTES.equals(configKey)
                || KEY_LOGIN_ATTEMPT_WINDOW_MINUTES.equals(configKey)
                || KEY_SESSION_TIMEOUT_MINUTES.equals(configKey)
                || KEY_SESSION_WARNING_MINUTES.equals(configKey)
                || KEY_MESSAGE_DURATION.equals(configKey)
                || KEY_CRAWL_INTERVAL_MINUTES.equals(configKey)
                || KEY_SENSITIVE_FILTER_ENABLED.equals(configKey)
                || (configKey != null && configKey.startsWith(KEY_SENSITIVE_KEYWORDS_PREFIX))
                || (configKey != null && configKey.startsWith(KEY_SENSITIVE_REGEX_PREFIX));
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
        if (!sysConfigRepository.existsByConfigKey(KEY_SENSITIVE_FILTER_ENABLED)) {
            save(KEY_SENSITIVE_FILTER_ENABLED, String.valueOf(DEFAULT_SENSITIVE_FILTER_ENABLED),
                    "敏感内容过滤开关", "是否启用敏感内容过滤功能，过滤涉政、色情、辱骂、广告等内容");
        }
        initSensitiveKeywordConfigs();
        log.info("[SysConfig] 初始化系统配置，预热Redis缓存");
        List<SysConfig> all = sysConfigRepository.findAll();
        sysConfigCacheService.cacheBatch(all);
    }

    private void initSensitiveKeywordConfigs() {
        String[][] defaultConfigs = {
                {"politics", "涉政敏感词", "法轮功,邪教"},
                {"porn", "色情敏感词", "色情,黄色,成人,av,性爱"},
                {"abuse", "辱骂敏感词", "傻逼,蠢货,垃圾,狗娘养,王八蛋,滚蛋"},
                {"ad", "广告敏感词", "加微信,加qq,代购,代理,刷单,兼职赚钱,网赚"},
                {"violence", "暴力敏感词", "杀人,自杀,暴力,血腥"},
                {"gambling", "赌博敏感词", "赌博,博彩,彩票,百家乐,老虎机"},
                {"drug", "毒品敏感词", "毒品,大麻,可卡因,海洛因"}
        };
        for (String[] cfg : defaultConfigs) {
            String key = KEY_SENSITIVE_KEYWORDS_PREFIX + cfg[0];
            if (!sysConfigRepository.existsByConfigKey(key)) {
                save(key, cfg[2], cfg[1], "多个关键词用英文逗号、中文逗号或空格分隔");
            }
        }
        String[][] defaultRegexConfigs = {
                {"ad", "广告正则表达式", "(微信|wx|vx)[\\s:：]?[a-zA-Z0-9_-]{5,}||(qq|扣扣)[\\s:：]?\\d{5,}||(电话|手机|联系电话)[\\s:：]?1[3-9]\\d{9}"},
                {"porn", "色情正则表达式", "(www\\.)?[^\\s]*?(porn|sex|xxx|成人|色情)[^\\s]*"}
        };
        for (String[] cfg : defaultRegexConfigs) {
            String key = KEY_SENSITIVE_REGEX_PREFIX + cfg[0];
            if (!sysConfigRepository.existsByConfigKey(key)) {
                save(key, cfg[2], cfg[1], "多个正则表达式用 || 分隔");
            }
        }
    }

    public void refreshCache() {
        sysConfigCacheService.evictAll();
        List<SysConfig> all = sysConfigRepository.findAll();
        sysConfigCacheService.cacheBatch(all);
        log.info("[SysConfig] 手动刷新Redis缓存完成，共{}条配置", all.size());
    }
}
