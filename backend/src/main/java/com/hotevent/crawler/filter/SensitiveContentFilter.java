package com.hotevent.crawler.filter;

import cn.hutool.core.util.StrUtil;
import com.hotevent.cache.DictionaryCacheService;
import com.hotevent.crawler.core.DataItem;
import com.hotevent.entity.HotEvent;
import com.hotevent.service.SysConfigService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class SensitiveContentFilter {

    @Autowired
    private SysConfigService sysConfigService;

    @Autowired
    private DictionaryCacheService dictionaryCacheService;

    private final Map<SensitiveType, Set<String>> keywordMap = new ConcurrentHashMap<>();
    private final Map<SensitiveType, List<Pattern>> regexPatternMap = new ConcurrentHashMap<>();

    private volatile boolean filterEnabled = true;

    private static final int CONTEXT_PREFIX_LENGTH = 10;
    private static final int CONTEXT_SUFFIX_LENGTH = 10;

    @PostConstruct
    public void init() {
        loadConfigFromSysConfig();
        log.info("敏感内容过滤器初始化完成，启用状态: {}", filterEnabled);
    }

    public synchronized void loadConfigFromSysConfig() {
        try {
            filterEnabled = sysConfigService.getBooleanValue(
                    SysConfigService.KEY_SENSITIVE_FILTER_ENABLED,
                    SysConfigService.DEFAULT_SENSITIVE_FILTER_ENABLED);

            for (SensitiveType type : SensitiveType.values()) {
                loadTypeKeywords(type);
                loadTypeRegexPatterns(type);
            }

            warmUpDictionaryCache();
            log.info("敏感词库加载完成，共加载 {} 种类型关键词", keywordMap.size());
        } catch (Exception e) {
            log.error("加载敏感内容过滤配置失败，使用默认配置", e);
            initDefaultKeywords();
        }
    }

    private void warmUpDictionaryCache() {
        try {
            for (SensitiveType type : SensitiveType.values()) {
                Set<String> keywords = keywordMap.get(type);
                if (keywords != null && !keywords.isEmpty()) {
                    dictionaryCacheService.cacheSensitiveKeywords(type.getCode(), keywords);
                }
                List<Pattern> patterns = regexPatternMap.get(type);
                if (patterns != null && !patterns.isEmpty()) {
                    List<String> regexStrings = new ArrayList<>();
                    for (Pattern p : patterns) {
                        regexStrings.add(p.pattern());
                    }
                    dictionaryCacheService.cacheSensitiveRegex(type.getCode(), regexStrings);
                }
            }
            log.info("[SensitiveFilter] 字典缓存预热完成");
        } catch (Exception e) {
            log.warn("[SensitiveFilter] 字典缓存预热失败: {}", e.getMessage());
        }
    }

    private void loadTypeKeywords(SensitiveType type) {
        Set<String> cachedKeywords = dictionaryCacheService.getSensitiveKeywords(type.getCode());
        if (cachedKeywords != null && !cachedKeywords.isEmpty()) {
            keywordMap.put(type, new HashSet<>(cachedKeywords));
            log.debug("[SensitiveFilter] 从Redis缓存加载[{}]敏感词{}个", type.getCode(), cachedKeywords.size());
            return;
        }

        String key = SysConfigService.KEY_SENSITIVE_KEYWORDS_PREFIX + type.getCode();
        String value = sysConfigService.getValue(key, null);
        Set<String> keywords = new HashSet<>();
        List<String> cleanedKeywords = new ArrayList<>();
        boolean needsFix = false;

        if (StrUtil.isNotBlank(value)) {
            String[] parts = value.split("[,，;；\\s]+");
            for (String part : parts) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) continue;
                if (isTooGenericKeyword(type, trimmed)) {
                    log.warn("[敏感过滤修复] 检测到[{}]类型存在过于宽泛的关键词，已自动移除: {}", type.getCode(), trimmed);
                    needsFix = true;
                    continue;
                }
                keywords.add(trimmed.toLowerCase());
                cleanedKeywords.add(trimmed);
            }
        }

        if (keywords.isEmpty()) {
            keywords.addAll(getDefaultKeywords(type));
            cleanedKeywords.clear();
            for (String kw : getDefaultKeywords(type)) {
                cleanedKeywords.add(kw);
            }
        }

        if (needsFix) {
            String fixedValue = String.join(",", cleanedKeywords);
            try {
                sysConfigService.save(key, fixedValue, type.getDisplayName() + "敏感词", "多个关键词用英文逗号、中文逗号或空格分隔");
                log.info("[敏感过滤修复] 已自动修复[{}]类型的关键词配置", type.getCode());
            } catch (Exception e) {
                log.warn("[敏感过滤修复] 自动修复[{}]类型关键词配置失败: {}", type.getCode(), e.getMessage());
            }
        }

        keywordMap.put(type, keywords);
        dictionaryCacheService.cacheSensitiveKeywords(type.getCode(), keywords);
    }

    private boolean isTooGenericKeyword(SensitiveType type, String keyword) {
        if (keyword == null) return false;
        String lower = keyword.toLowerCase();
        if (type == SensitiveType.ABUSE) {
            return "垃圾".equals(lower) || "垃圾".equals(keyword);
        }
        return false;
    }

    private void loadTypeRegexPatterns(SensitiveType type) {
        List<String> cachedRegex = dictionaryCacheService.getSensitiveRegex(type.getCode());
        if (cachedRegex != null && !cachedRegex.isEmpty()) {
            List<Pattern> patterns = new ArrayList<>();
            for (String regex : cachedRegex) {
                try {
                    patterns.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
                } catch (Exception ignored) {}
            }
            if (!patterns.isEmpty()) {
                regexPatternMap.put(type, patterns);
                log.debug("[SensitiveFilter] 从Redis缓存加载[{}]正则{}个", type.getCode(), patterns.size());
                return;
            }
        }

        String key = SysConfigService.KEY_SENSITIVE_REGEX_PREFIX + type.getCode();
        String value = sysConfigService.getValue(key, null);
        List<Pattern> patterns = new ArrayList<>();

        List<String> cleanedPatterns = new ArrayList<>();
        boolean needsFix = false;

        if (StrUtil.isNotBlank(value)) {
            String[] parts = value.split("\\|\\|");
            for (String part : parts) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) continue;
                if (isProblematicUrlRegex(trimmed)) {
                    log.warn("[敏感过滤修复] 检测到[{}]类型存在有缺陷的URL正则，已自动移除: {}", type.getCode(), trimmed);
                    needsFix = true;
                    continue;
                }
                try {
                    patterns.add(Pattern.compile(trimmed, Pattern.CASE_INSENSITIVE));
                    cleanedPatterns.add(trimmed);
                } catch (Exception e) {
                    log.warn("无效的正则表达式[{}]: {}", type.getCode(), trimmed);
                    needsFix = true;
                }
            }
        }

        for (String regex : getDefaultRegexPatterns(type)) {
            try {
                patterns.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
                cleanedPatterns.add(regex);
            } catch (Exception ignored) {}
        }

        if (needsFix) {
            String fixedValue = String.join("||", cleanedPatterns);
            try {
                sysConfigService.save(key, fixedValue, type.getDisplayName() + "正则表达式", "多个正则表达式用 || 分隔");
                log.info("[敏感过滤修复] 已自动修复[{}]类型的正则配置", type.getCode());
            } catch (Exception e) {
                log.warn("[敏感过滤修复] 自动修复[{}]类型正则配置失败: {}", type.getCode(), e.getMessage());
            }
        }

        regexPatternMap.put(type, patterns);
        dictionaryCacheService.cacheSensitiveRegex(type.getCode(), cleanedPatterns);
    }

    private boolean isProblematicUrlRegex(String regex) {
        if (regex == null) return false;
        String lower = regex.toLowerCase();
        return lower.contains("http[s]?://(?!") || lower.contains("http?://") && lower.contains("weibo") && lower.contains("zhihu");
    }

    private Set<String> getDefaultKeywords(SensitiveType type) {
        Set<String> keywords = new HashSet<>();
        switch (type) {
            case POLITICS:
                keywords.add("法轮功");
                keywords.add("邪教");
                break;
            case PORN:
                keywords.add("色情");
                keywords.add("黄色");
                keywords.add("成人");
                keywords.add("av");
                keywords.add("性爱");
                break;
            case ABUSE:
                keywords.add("傻逼");
                keywords.add("蠢货");
                keywords.add("狗娘养");
                keywords.add("王八蛋");
                keywords.add("滚蛋");
                keywords.add("狗屎");
                break;
            case AD:
                keywords.add("加微信");
                keywords.add("加qq");
                keywords.add("代购");
                keywords.add("代理");
                keywords.add("刷单");
                keywords.add("兼职赚钱");
                keywords.add("网赚");
                break;
            case VIOLENCE:
                keywords.add("杀人");
                keywords.add("自杀");
                keywords.add("暴力");
                keywords.add("血腥");
                break;
            case GAMBLING:
                keywords.add("赌博");
                keywords.add("博彩");
                keywords.add("彩票");
                keywords.add("百家乐");
                keywords.add("老虎机");
                break;
            case DRUG:
                keywords.add("毒品");
                keywords.add("大麻");
                keywords.add("可卡因");
                keywords.add("海洛因");
                break;
            default:
                break;
        }
        return keywords;
    }

    private List<String> getDefaultRegexPatterns(SensitiveType type) {
        List<String> patterns = new ArrayList<>();
        switch (type) {
            case AD:
                patterns.add("(微信|wx|vx)[\\s:：]?[a-zA-Z0-9_-]{6,}");
                patterns.add("(qq|扣扣)[\\s:：]?\\d{6,}");
                patterns.add("(电话|手机|联系电话|Tel|tel)[\\s:：]?1[3-9]\\d{9}");
                break;
            case PORN:
                patterns.add("(www\\.)?[^\\s]*?(porn|sex|xxx|成人|色情)[^\\s]*");
                break;
            default:
                break;
        }
        return patterns;
    }

    private void initDefaultKeywords() {
        filterEnabled = true;
        for (SensitiveType type : SensitiveType.values()) {
            keywordMap.put(type, getDefaultKeywords(type));
            List<Pattern> patterns = new ArrayList<>();
            for (String regex : getDefaultRegexPatterns(type)) {
                try {
                    patterns.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
                } catch (Exception ignored) {}
            }
            regexPatternMap.put(type, patterns);
        }
    }

    public SensitiveCheckResult check(String text) {
        SensitiveCheckResult result = new SensitiveCheckResult();
        if (!filterEnabled || StrUtil.isBlank(text)) {
            return result;
        }

        String lowerText = text.toLowerCase();

        for (SensitiveType type : SensitiveType.values()) {
            Set<String> keywords = keywordMap.get(type);
            if (keywords != null) {
                for (String keyword : keywords) {
                    int idx = lowerText.indexOf(keyword);
                    if (idx >= 0) {
                        String context = extractContext(text, idx, keyword.length());
                        result.addMatch(type, keyword, context);
                    }
                }
            }

            List<Pattern> patterns = regexPatternMap.get(type);
            if (patterns != null) {
                for (Pattern pattern : patterns) {
                    Matcher matcher = pattern.matcher(text);
                    while (matcher.find()) {
                        String matched = matcher.group();
                        String context = extractContext(text, matcher.start(), matched.length());
                        result.addMatch(type, matched, context);
                    }
                }
            }
        }

        return result;
    }

    public SensitiveCheckResult check(DataItem item) {
        if (item == null) return new SensitiveCheckResult();

        SensitiveCheckResult titleResult = check(item.getTitle());
        SensitiveCheckResult contentResult = check(item.getContent());
        SensitiveCheckResult summaryResult = check(item.getSummary());

        SensitiveCheckResult combined = new SensitiveCheckResult();
        mergeResults(combined, titleResult);
        mergeResults(combined, contentResult);
        mergeResults(combined, summaryResult);
        return combined;
    }

    public SensitiveCheckResult check(HotEvent event) {
        if (event == null) return new SensitiveCheckResult();

        SensitiveCheckResult titleResult = check(event.getTitle());
        SensitiveCheckResult descResult = check(event.getDescription());

        SensitiveCheckResult combined = new SensitiveCheckResult();
        mergeResults(combined, titleResult);
        mergeResults(combined, descResult);
        return combined;
    }

    private void mergeResults(SensitiveCheckResult target, SensitiveCheckResult source) {
        if (source != null && source.isSensitive()) {
            for (SensitiveCheckResult.MatchDetail detail : source.getMatchedDetails()) {
                target.addMatch(detail.getType(), detail.getMatchedWord(), detail.getContext());
            }
        }
    }

    private String extractContext(String text, int startIndex, int matchLength) {
        if (text == null) return "";
        int ctxStart = Math.max(0, startIndex - CONTEXT_PREFIX_LENGTH);
        int ctxEnd = Math.min(text.length(), startIndex + matchLength + CONTEXT_SUFFIX_LENGTH);
        String prefix = ctxStart > 0 ? "..." : "";
        String suffix = ctxEnd < text.length() ? "..." : "";
        return prefix + text.substring(ctxStart, ctxEnd) + suffix;
    }

    public boolean isFilterEnabled() {
        return filterEnabled;
    }

    public void setFilterEnabled(boolean enabled) {
        this.filterEnabled = enabled;
    }

    public Map<SensitiveType, Set<String>> getAllKeywords() {
        return new HashMap<>(keywordMap);
    }

    public Set<String> getKeywords(SensitiveType type) {
        Set<String> keywords = keywordMap.get(type);
        return keywords != null ? new HashSet<>(keywords) : new HashSet<>();
    }

    public int getTotalKeywordCount() {
        int count = 0;
        for (Set<String> keywords : keywordMap.values()) {
            count += keywords.size();
        }
        return count;
    }

    public synchronized void refreshCache() {
        dictionaryCacheService.evictAllSensitive();
        loadConfigFromSysConfig();
        log.info("[SensitiveFilter] 缓存刷新完成");
    }
}
