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
import java.util.concurrent.CompletableFuture;

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
    public Result<Map<String, Object>> translate(
            @RequestBody Map<String, String> request,
            @RequestParam(defaultValue = "false") boolean async) {
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

        final String srcLang = sourceLang;
        final String tgtLang = targetLang;

        if (async) {
            CompletableFuture<String> translateFuture =
                    translationService.translateAsync(text, srcLang, tgtLang);
            String translated = translateFuture.join();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("originalText", text);
            result.put("translatedText", translated);
            result.put("sourceLang", srcLang);
            result.put("targetLang", tgtLang);
            result.put("detectedSourceLang", nlpService.detectLanguage(text));
            result.put("isAsync", true);

            return Result.success(result);
        }

        String translated = translationService.translate(text, srcLang, tgtLang);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("originalText", text);
        result.put("translatedText", translated);
        result.put("sourceLang", srcLang);
        result.put("targetLang", tgtLang);
        result.put("detectedSourceLang", nlpService.detectLanguage(text));

        return Result.success(result);
    }

    @PostMapping("/translate-batch")
    public Result<Map<String, String>> translateBatch(
            @RequestBody Map<String, Object> request,
            @RequestParam(defaultValue = "false") boolean async) {
        @SuppressWarnings("unchecked")
        Map<String, String> texts = (Map<String, String>) request.get("texts");
        String sourceLang = (String) request.get("sourceLang");
        String targetLang = (String) request.get("targetLang");

        if (texts == null || texts.isEmpty()) {
            return Result.error("翻译内容不能为空");
        }
        if (sourceLang == null) sourceLang = "zh-CN";
        if (targetLang == null) targetLang = i18nProperties.getDefaultLocale();

        if (async) {
            CompletableFuture<Map<String, String>> future =
                    translationService.translateBatchAsync(texts, sourceLang, targetLang);
            return Result.success(future.join());
        }

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
    public Result<Map<String, Object>> segment(
            @RequestBody Map<String, String> request,
            @RequestParam(defaultValue = "false") boolean async) {
        String text = request.get("text");
        String language = request.get("language");

        if (text == null || text.isEmpty()) {
            return Result.error("分词文本不能为空");
        }
        if (language == null || language.isEmpty()) {
            language = nlpService.detectLanguage(text);
        }

        final String finalLanguage = language;
        if (async) {
            CompletableFuture<Map<String, Object>> future =
                    nlpService.analyzeTextAsync(text, finalLanguage);
            return Result.success(future.join());
        }

        Map<String, Object> analysis = nlpService.analyzeText(text, finalLanguage);
        return Result.success(analysis);
    }

    @PostMapping("/extract-keywords")
    public Result<Map<String, Object>> extractKeywords(
            @RequestBody Map<String, Object> request,
            @RequestParam(defaultValue = "false") boolean async) {
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

        final String finalLanguage = language;
        final int finalCount = count;

        if (async) {
            CompletableFuture<List<String>> keywordsFuture =
                    nlpService.extractKeywordsAsync(text, finalLanguage, finalCount);
            CompletableFuture<Map<String, Double>> keywordScoresFuture =
                    nlpService.extractKeywordsWithScoreAsync(text, finalLanguage, finalCount);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("keywords", keywordsFuture.join());
            result.put("keywordScores", keywordScoresFuture.join());
            result.put("language", finalLanguage);
            result.put("isAsync", true);

            return Result.success(result);
        }

        List<String> keywords = nlpService.extractKeywords(text, finalLanguage, finalCount);
        Map<String, Double> keywordScores = nlpService.extractKeywordsWithScore(text, finalLanguage, finalCount);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("keywords", keywords);
        result.put("keywordScores", keywordScores);
        result.put("language", finalLanguage);

        return Result.success(result);
    }

    @PostMapping("/convert")
    public Result<Map<String, Object>> convertChinese(
            @RequestBody Map<String, String> request,
            @RequestParam(defaultValue = "false") boolean async) {
        String text = request.get("text");
        String direction = request.get("direction");

        if (text == null || text.isEmpty()) {
            return Result.error("转换文本不能为空");
        }

        if (async) {
            CompletableFuture<String> convertFuture;
            if ("toTraditional".equals(direction)) {
                convertFuture = nlpService.convertToTraditionalAsync(text);
            } else if ("toSimplified".equals(direction)) {
                convertFuture = nlpService.convertToSimplifiedAsync(text);
            } else {
                return Result.error("direction参数必须为 toTraditional 或 toSimplified");
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("originalText", text);
            result.put("convertedText", convertFuture.join());
            result.put("direction", direction);
            result.put("isAsync", true);

            return Result.success(result);
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
