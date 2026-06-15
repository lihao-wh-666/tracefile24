package com.hotevent.controller;

import com.hotevent.common.Result;
import com.hotevent.i18n.I18nProperties;
import com.hotevent.i18n.TranslationService;
import com.hotevent.nlp.NlpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/i18n")
public class I18nController {

    @Autowired
    private TranslationService translationService;

    @Autowired
    private NlpService nlpService;

    @Autowired
    private I18nProperties i18nProperties;

    @GetMapping("/supported-languages")
    public Result<List<String>> getSupportedLanguages() {
        return Result.success(i18nProperties.getSupportedLocales());
    }

    @GetMapping("/language-names")
    public Result<Map<String, String>> getLanguageNames() {
        return Result.success(nlpService.getLanguageNames());
    }

    @PostMapping("/translate")
    public Result<Map<String, Object>> translate(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        String sourceLang = request.get("sourceLang");
        String targetLang = request.get("targetLang");

        if (text == null || text.isEmpty()) {
            return Result.error("翻译文本不能为空");
        }
        if (sourceLang == null || sourceLang.isEmpty()) {
            sourceLang = nlpService.detectLanguage(text);
        }
        if (targetLang == null || targetLang.isEmpty()) {
            targetLang = i18nProperties.getDefaultLocale();
        }

        String translated = translationService.translate(text, sourceLang, targetLang);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("originalText", text);
        result.put("translatedText", translated);
        result.put("sourceLang", sourceLang);
        result.put("targetLang", targetLang);
        result.put("detectedSourceLang", nlpService.detectLanguage(text));

        return Result.success(result);
    }

    @PostMapping("/translate-batch")
    public Result<Map<String, String>> translateBatch(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        Map<String, String> texts = (Map<String, String>) request.get("texts");
        String sourceLang = (String) request.get("sourceLang");
        String targetLang = (String) request.get("targetLang");

        if (texts == null || texts.isEmpty()) {
            return Result.error("翻译内容不能为空");
        }
        if (sourceLang == null) sourceLang = "zh-CN";
        if (targetLang == null) targetLang = i18nProperties.getDefaultLocale();

        Map<String, String> result = translationService.translateBatch(texts, sourceLang, targetLang);
        return Result.success(result);
    }

    @PostMapping("/detect-language")
    public Result<Map<String, Object>> detectLanguage(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        if (text == null || text.isEmpty()) {
            return Result.error("检测文本不能为空");
        }

        String language = nlpService.detectLanguage(text);
        String languageName = nlpService.getLanguageNames().getOrDefault(language, language);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("text", text.substring(0, Math.min(100, text.length())) + (text.length() > 100 ? "..." : ""));
        result.put("language", language);
        result.put("languageName", languageName);
        result.put("isTraditionalChinese", "zh-TW".equals(language) || nlpService.isTraditionalChinese(text));

        return Result.success(result);
    }

    @PostMapping("/segment")
    public Result<Map<String, Object>> segment(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        String language = request.get("language");

        if (text == null || text.isEmpty()) {
            return Result.error("分词文本不能为空");
        }
        if (language == null || language.isEmpty()) {
            language = nlpService.detectLanguage(text);
        }

        Map<String, Object> analysis = nlpService.analyzeText(text, language);
        return Result.success(analysis);
    }

    @PostMapping("/extract-keywords")
    public Result<Map<String, Object>> extractKeywords(@RequestBody Map<String, Object> request) {
        String text = (String) request.get("text");
        String language = (String) request.get("language");
        Integer count = (Integer) request.get("count");

        if (text == null || text.isEmpty()) {
            return Result.error("文本不能为空");
        }
        if (language == null || language.isEmpty()) {
            language = nlpService.detectLanguage(text);
        }
        if (count == null || count <= 0) {
            count = 10;
        }

        List<String> keywords = nlpService.extractKeywords(text, language, count);
        Map<String, Double> keywordScores = nlpService.extractKeywordsWithScore(text, language, count);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("keywords", keywords);
        result.put("keywordScores", keywordScores);
        result.put("language", language);

        return Result.success(result);
    }

    @PostMapping("/convert")
    public Result<Map<String, Object>> convertChinese(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        String direction = request.get("direction");

        if (text == null || text.isEmpty()) {
            return Result.error("转换文本不能为空");
        }

        String converted;
        if ("toTraditional".equals(direction)) {
            converted = nlpService.convertToTraditional(text);
        } else if ("toSimplified".equals(direction)) {
            converted = nlpService.convertToSimplified(text);
        } else {
            return Result.error("direction参数必须为 toTraditional 或 toSimplified");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("originalText", text);
        result.put("convertedText", converted);
        result.put("direction", direction);

        return Result.success(result);
    }

    @GetMapping("/status")
    public Result<Map<String, Object>> getI18nStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("defaultLocale", i18nProperties.getDefaultLocale());
        status.put("supportedLocales", i18nProperties.getSupportedLocales());
        status.put("translationEnabled", i18nProperties.getTranslation().isEnabled());
        status.put("translationProvider", i18nProperties.getTranslation().getProvider());
        status.put("translationAvailable", translationService.isTranslationAvailable());
        status.put("autoTranslateOnCrawl", i18nProperties.getTranslation().isAutoTranslateOnCrawl());
        status.put("nlpChineseSegmenterEnabled", i18nProperties.getNlp().getSegmenter().getChinese().isEnabled());
        status.put("nlpEnglishSegmenterEnabled", i18nProperties.getNlp().getSegmenter().getEnglish().isEnabled());
        status.put("traditionalChineseSupport", i18nProperties.getNlp().getSegmenter().getChinese().isTraditionalChineseSupport());

        return Result.success(status);
    }
}
