package com.hotevent.i18n.translation.impl;

import com.hotevent.i18n.translation.TranslationProvider;
import com.hotevent.nlp.NlpService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InternalTranslationProvider implements TranslationProvider {

    private final NlpService nlpService;

    private static final java.util.Map<String, String> ZH_TW_CATEGORY_MAP = java.util.Map.of(
            "抖音", "抖音",
            "B站", "B站",
            "百度", "百度",
            "知乎", "知乎",
            "微博", "微博"
    );

    private static final java.util.Map<String, String> EN_CATEGORY_MAP = java.util.Map.of(
            "抖音", "Douyin",
            "B站", "Bilibili",
            "百度", "Baidu",
            "知乎", "Zhihu",
            "微博", "Weibo",
            "未分类", "Uncategorized"
    );

    public InternalTranslationProvider(NlpService nlpService) {
        this.nlpService = nlpService;
    }

    @Override
    public String translate(String text, String sourceLang, String targetLang) {
        if (text == null || text.trim().isEmpty()) return text;
        if (sourceLang.equals(targetLang)) return text;

        if ("zh-CN".equals(sourceLang) && "zh-TW".equals(targetLang)) {
            return nlpService.convertToTraditional(text);
        }
        if ("zh-TW".equals(sourceLang) && "zh-CN".equals(targetLang)) {
            return nlpService.convertToSimplified(text);
        }
        if ("zh-CN".equals(sourceLang) && "en".equals(targetLang)) {
            return zhCnToEn(text);
        }
        if ("zh-TW".equals(sourceLang) && "en".equals(targetLang)) {
            String simplified = nlpService.convertToSimplified(text);
            return zhCnToEn(simplified);
        }
        if ("en".equals(sourceLang) && "zh-CN".equals(targetLang)) {
            return enToZhCn(text);
        }
        if ("en".equals(sourceLang) && "zh-TW".equals(targetLang)) {
            String simplified = enToZhCn(text);
            return nlpService.convertToTraditional(simplified);
        }

        return text;
    }

    private String zhCnToEn(String text) {
        if (EN_CATEGORY_MAP.containsKey(text)) {
            return EN_CATEGORY_MAP.get(text);
        }
        return text;
    }

    private String enToZhCn(String text) {
        for (java.util.Map.Entry<String, String> entry : EN_CATEGORY_MAP.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(text)) {
                return entry.getKey();
            }
        }
        return text;
    }

    @Override
    public String getProviderName() {
        return "internal";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
