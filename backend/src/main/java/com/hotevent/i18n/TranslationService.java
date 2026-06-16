package com.hotevent.i18n;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hotevent.config.AsyncTaskExecutor;
import com.hotevent.i18n.translation.TranslationCache;
import com.hotevent.i18n.translation.TranslationProvider;
import com.hotevent.i18n.translation.impl.BaiduTranslationProvider;
import com.hotevent.i18n.translation.impl.GoogleTranslationProvider;
import com.hotevent.i18n.translation.impl.InternalTranslationProvider;
import com.hotevent.i18n.translation.impl.MyMemoryTranslationProvider;
import com.hotevent.nlp.NlpService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class TranslationService {

    @Autowired
    private I18nProperties i18nProperties;

    @Autowired
    private NlpService nlpService;

    @Autowired
    private TranslationCache translationCache;

    @Autowired
    private AsyncTaskExecutor asyncTaskExecutor;

    private final Map<String, TranslationProvider> providers = new ConcurrentHashMap<>();

    public String translate(String text, String sourceLang, String targetLang) {
        if (text == null || text.trim().isEmpty()) return text;
        if (sourceLang.equals(targetLang)) return text;

        if (isZhCnToZhTw(sourceLang, targetLang)) {
            return nlpService.convertToTraditional(text);
        }
        if (isZhTwToZhCn(sourceLang, targetLang)) {
            return nlpService.convertToSimplified(text);
        }

        if (i18nProperties.getTranslation().isCacheEnabled()) {
            String cached = translationCache.get(text, sourceLang, targetLang);
            if (cached != null) {
                log.debug("[Translation] Cache hit: {} -> {} ({})", sourceLang, targetLang, text.substring(0, Math.min(20, text.length())));
                return cached;
            }
        }

        if (!i18nProperties.getTranslation().isEnabled()) {
            log.warn("[Translation] Translation is disabled, returning original text");
            return text;
        }

        TranslationProvider provider = getProvider();
        if (provider == null) {
            log.warn("[Translation] No translation provider available");
            return text;
        }

        try {
            String result = provider.translate(text, sourceLang, targetLang);

            if (result != null && !result.isEmpty()) {
                if (i18nProperties.getTranslation().isCacheEnabled()) {
                    translationCache.put(text, sourceLang, targetLang, result);
                }
                return result;
            }
        } catch (Exception e) {
            log.error("[Translation] Translation failed: {}", e.getMessage());
        }

        return text;
    }

    public CompletableFuture<String> translateAsync(String text, String sourceLang, String targetLang) {
        if (text == null || text.trim().isEmpty()) {
            return CompletableFuture.completedFuture(text);
        }
        if (sourceLang.equals(targetLang)) {
            return CompletableFuture.completedFuture(text);
        }

        if (isZhCnToZhTw(sourceLang, targetLang)) {
            return nlpService.convertToTraditionalAsync(text);
        }
        if (isZhTwToZhCn(sourceLang, targetLang)) {
            return nlpService.convertToSimplifiedAsync(text);
        }

        if (i18nProperties.getTranslation().isCacheEnabled()) {
            String cached = translationCache.get(text, sourceLang, targetLang);
            if (cached != null) {
                log.debug("[Translation] Cache hit: {} -> {} ({})", sourceLang, targetLang,
                        text.substring(0, Math.min(20, text.length())));
                return CompletableFuture.completedFuture(cached);
            }
        }

        if (!i18nProperties.getTranslation().isEnabled()) {
            log.warn("[Translation] Translation is disabled, returning original text");
            return CompletableFuture.completedFuture(text);
        }

        TranslationProvider provider = getProvider();
        if (provider == null) {
            log.warn("[Translation] No translation provider available");
            return CompletableFuture.completedFuture(text);
        }

        final String textSnapshot = text;
        final String srcLang = sourceLang;
        final String tgtLang = targetLang;

        return asyncTaskExecutor.submitIoTask(() -> {
            try {
                String result = provider.translate(textSnapshot, srcLang, tgtLang);

                if (result != null && !result.isEmpty()) {
                    if (i18nProperties.getTranslation().isCacheEnabled()) {
                        translationCache.put(textSnapshot, srcLang, tgtLang, result);
                    }
                    return result;
                }
            } catch (Exception e) {
                log.error("[Translation] Async translation failed: {}", e.getMessage());
            }
            return textSnapshot;
        }, "translate[" + srcLang + "->" + tgtLang + "," + (textSnapshot != null ? textSnapshot.length() : 0) + "chars]");
    }

    public Map<String, String> translateBatch(Map<String, String> texts, String sourceLang, String targetLang) {
        Map<String, String> results = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : texts.entrySet()) {
            String translated = translate(entry.getValue(), sourceLang, targetLang);
            results.put(entry.getKey(), translated);
        }
        return results;
    }

    public CompletableFuture<Map<String, String>> translateBatchAsync(Map<String, String> texts, String sourceLang, String targetLang) {
        if (texts == null || texts.isEmpty()) {
            return CompletableFuture.completedFuture(new LinkedHashMap<>());
        }

        List<Map.Entry<String, String>> entries = new ArrayList<>(texts.entrySet());
        List<CompletableFuture<Map.Entry<String, String>>> futures = new ArrayList<>();

        for (Map.Entry<String, String> entry : entries) {
            final String key = entry.getKey();
            final String value = entry.getValue();
            CompletableFuture<Map.Entry<String, String>> future =
                    translateAsync(value, sourceLang, targetLang)
                            .thenApply(translated -> new AbstractMap.SimpleEntry<>(key, translated));
            futures.add(future);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    Map<String, String> results = new LinkedHashMap<>();
                    for (CompletableFuture<Map.Entry<String, String>> future : futures) {
                        Map.Entry<String, String> entry = future.join();
                        results.put(entry.getKey(), entry.getValue());
                    }
                    return results;
                });
    }

    public Map<String, Object> translateEvent(String title, String description, String category,
                                                String sourceLang, String targetLang) {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("title", translate(title, sourceLang, targetLang));

        if (description != null && !description.isEmpty()) {
            result.put("description", translate(description, sourceLang, targetLang));
        }

        if (category != null && !category.isEmpty()) {
            result.put("category", translate(category, sourceLang, targetLang));
        }

        result.put("sourceLang", sourceLang);
        result.put("targetLang", targetLang);

        return result;
    }

    public CompletableFuture<Map<String, Object>> translateEventAsync(String title, String description, String category,
                                                                       String sourceLang, String targetLang) {
        CompletableFuture<String> titleFuture = translateAsync(title, sourceLang, targetLang);

        CompletableFuture<String> descFuture = (description != null && !description.isEmpty())
                ? translateAsync(description, sourceLang, targetLang)
                : CompletableFuture.completedFuture(null);

        CompletableFuture<String> catFuture = (category != null && !category.isEmpty())
                ? translateAsync(category, sourceLang, targetLang)
                : CompletableFuture.completedFuture(null);

        final String srcLang = sourceLang;
        final String tgtLang = targetLang;

        return CompletableFuture.allOf(titleFuture, descFuture, catFuture)
                .thenApply(v -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("title", titleFuture.join());
                    if (descFuture.join() != null) result.put("description", descFuture.join());
                    if (catFuture.join() != null) result.put("category", catFuture.join());
                    result.put("sourceLang", srcLang);
                    result.put("targetLang", tgtLang);
                    return result;
                });
    }

    private TranslationProvider getProvider() {
        String providerName = i18nProperties.getTranslation().getProvider();
        return providers.computeIfAbsent(providerName, name -> {
            switch (name.toLowerCase()) {
                case "mymemory":
                    return new MyMemoryTranslationProvider(i18nProperties.getTranslation().getMyMemory(), i18nProperties);
                case "baidu":
                    I18nProperties.Baidu baiduConfig = i18nProperties.getTranslation().getBaidu();
                    if (baiduConfig.getAppId() != null && !baiduConfig.getAppId().isEmpty() &&
                            baiduConfig.getSecretKey() != null && !baiduConfig.getSecretKey().isEmpty()) {
                        return new BaiduTranslationProvider(baiduConfig, i18nProperties);
                    }
                    log.warn("[Translation] Baidu credentials not configured, falling back to mymemory provider");
                    return new MyMemoryTranslationProvider(i18nProperties.getTranslation().getMyMemory(), i18nProperties);
                case "google":
                    I18nProperties.Google googleConfig = i18nProperties.getTranslation().getGoogle();
                    if (googleConfig.getApiKey() != null && !googleConfig.getApiKey().isEmpty()) {
                        return new GoogleTranslationProvider(googleConfig, i18nProperties);
                    }
                    log.warn("[Translation] Google credentials not configured, falling back to mymemory provider");
                    return new MyMemoryTranslationProvider(i18nProperties.getTranslation().getMyMemory(), i18nProperties);
                default:
                    log.warn("[Translation] Unknown provider: {}, using mymemory provider", name);
                    return new MyMemoryTranslationProvider(i18nProperties.getTranslation().getMyMemory(), i18nProperties);
            }
        });
    }

    private boolean isZhCnToZhTw(String source, String target) {
        return "zh-CN".equals(source) && "zh-TW".equals(target);
    }

    private boolean isZhTwToZhCn(String source, String target) {
        return "zh-TW".equals(source) && "zh-CN".equals(target);
    }

    public boolean isTranslationAvailable() {
        if (!i18nProperties.getTranslation().isEnabled()) return false;
        TranslationProvider provider = getProvider();
        return provider != null && !(provider instanceof InternalTranslationProvider) ||
                (provider instanceof InternalTranslationProvider);
    }

    public String getActiveProvider() {
        return i18nProperties.getTranslation().getProvider();
    }

    public void clearCache() {
        translationCache.clear();
    }
}
