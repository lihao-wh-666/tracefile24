package com.hotevent.nlp;

import com.hotevent.config.AsyncTaskExecutor;
import com.hotevent.nlp.impl.ChineseSegmenter;
import com.hotevent.nlp.impl.EnglishSegmenter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NlpService {

    @Autowired
    private ChineseSegmenter chineseSegmenter;

    @Autowired
    private EnglishSegmenter englishSegmenter;

    @Autowired
    private AsyncTaskExecutor asyncTaskExecutor;

    private static final Map<String, String> LANGUAGE_NAMES = Map.of(
            "zh-CN", "简体中文",
            "zh-TW", "繁體中文",
            "en", "English"
    );

    public List<String> segment(String text, String language) {
        TextSegmenter segmenter = getSegmenter(language);
        if (segmenter != null) {
            return segmenter.segment(text);
        }
        return fallbackSegment(text);
    }

    public CompletableFuture<List<String>> segmentAsync(String text, String language) {
        return asyncTaskExecutor.submitCpuTask(() -> segment(text, language),
                "segment[" + (text != null ? text.length() : 0) + "chars," + language + "]");
    }

    public List<String> segmentAuto(String text) {
        String lang = detectLanguage(text);
        return segment(text, lang);
    }

    public CompletableFuture<List<String>> segmentAutoAsync(String text) {
        return asyncTaskExecutor.submitCpuTask(() -> segmentAuto(text),
                "segmentAuto[" + (text != null ? text.length() : 0) + "chars]");
    }

    public List<String> segmentWithPos(String text, String language) {
        TextSegmenter segmenter = getSegmenter(language);
        if (segmenter != null) {
            return segmenter.segmentWithPos(text);
        }
        return fallbackSegment(text);
    }

    public CompletableFuture<List<String>> segmentWithPosAsync(String text, String language) {
        return asyncTaskExecutor.submitCpuTask(() -> segmentWithPos(text, language),
                "segmentWithPos[" + (text != null ? text.length() : 0) + "chars," + language + "]");
    }

    public List<String> extractKeywords(String text, String language, int count) {
        TextSegmenter segmenter = getSegmenter(language);
        if (segmenter != null) {
            return segmenter.extractKeywords(text, count);
        }
        return segmentAuto(text).stream().limit(count).collect(Collectors.toList());
    }

    public CompletableFuture<List<String>> extractKeywordsAsync(String text, String language, int count) {
        return asyncTaskExecutor.submitCpuTask(() -> extractKeywords(text, language, count),
                "extractKeywords[" + (text != null ? text.length() : 0) + "chars," + language + ",top" + count + "]");
    }

    public Map<String, Double> extractKeywordsWithScore(String text, String language, int count) {
        TextSegmenter segmenter = getSegmenter(language);
        if (segmenter != null) {
            return segmenter.extractKeywordsWithScore(text, count);
        }
        return Collections.emptyMap();
    }

    public CompletableFuture<Map<String, Double>> extractKeywordsWithScoreAsync(String text, String language, int count) {
        return asyncTaskExecutor.submitCpuTask(() -> extractKeywordsWithScore(text, language, count),
                "extractKeywordsWithScore[" + (text != null ? text.length() : 0) + "chars," + language + ",top" + count + "]");
    }

    public List<String> extractPhrases(String text, String language, int count) {
        TextSegmenter segmenter = getSegmenter(language);
        if (segmenter != null) {
            return segmenter.extractPhrases(text, count);
        }
        return Collections.emptyList();
    }

    public CompletableFuture<List<String>> extractPhrasesAsync(String text, String language, int count) {
        return asyncTaskExecutor.submitCpuTask(() -> extractPhrases(text, language, count),
                "extractPhrases[" + (text != null ? text.length() : 0) + "chars," + language + ",top" + count + "]");
    }

    public String detectLanguage(String text) {
        if (text == null || text.trim().isEmpty()) return "zh-CN";

        int chineseCount = 0;
        int traditionalCount = 0;
        int englishCount = 0;
        int totalChars = 0;

        for (char c : text.toCharArray()) {
            if (isChineseChar(c)) {
                chineseCount++;
                totalChars++;
                if (isTraditionalChar(c)) {
                    traditionalCount++;
                }
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                englishCount++;
                totalChars++;
            }
        }

        if (totalChars == 0) return "zh-CN";

        double chineseRatio = (double) chineseCount / totalChars;
        double englishRatio = (double) englishCount / totalChars;
        double traditionalRatio = chineseCount > 0 ? (double) traditionalCount / chineseCount : 0;

        if (chineseRatio > 0.3) {
            if (traditionalRatio > 0.3) {
                return "zh-TW";
            }
            return "zh-CN";
        }

        if (englishRatio > 0.3) {
            return "en";
        }

        return "zh-CN";
    }

    public double getLanguageConfidence(String text, String language) {
        String detected = detectLanguage(text);
        return detected.equals(language) ? 1.0 : 0.0;
    }

    public String convertToSimplified(String text) {
        return chineseSegmenter.convertToSimplified(text);
    }

    public CompletableFuture<String> convertToSimplifiedAsync(String text) {
        return asyncTaskExecutor.submitCpuTask(() -> convertToSimplified(text),
                "convertToSimplified[" + (text != null ? text.length() : 0) + "chars]");
    }

    public String convertToTraditional(String text) {
        return chineseSegmenter.convertToTraditional(text);
    }

    public CompletableFuture<String> convertToTraditionalAsync(String text) {
        return asyncTaskExecutor.submitCpuTask(() -> convertToTraditional(text),
                "convertToTraditional[" + (text != null ? text.length() : 0) + "chars]");
    }

    public boolean isTraditionalChinese(String text) {
        return chineseSegmenter.isTraditionalChinese(text);
    }

    public Map<String, Object> analyzeText(String text, String language) {
        if (language == null || language.isEmpty()) {
            language = detectLanguage(text);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("text", text);
        result.put("detectedLanguage", language);
        result.put("languageName", LANGUAGE_NAMES.getOrDefault(language, language));

        List<String> tokens = segment(text, language);
        result.put("tokens", tokens);
        result.put("tokenCount", tokens.size());

        List<String> keywords = extractKeywords(text, language, 10);
        result.put("keywords", keywords);

        Map<String, Double> keywordScores = extractKeywordsWithScore(text, language, 10);
        result.put("keywordScores", keywordScores);

        List<String> phrases = extractPhrases(text, language, 5);
        result.put("phrases", phrases);

        if ("zh-CN".equals(language) || "zh-TW".equals(language)) {
            result.put("isTraditionalChinese", isTraditionalChinese(text));
            result.put("simplifiedVersion", convertToSimplified(text));
            result.put("traditionalVersion", convertToTraditional(text));
        }

        return result;
    }

    public CompletableFuture<Map<String, Object>> analyzeTextAsync(String text, String language) {
        if (language == null || language.isEmpty()) {
            language = detectLanguage(text);
        }

        final String finalLanguage = language;
        final String textSnapshot = text;

        CompletableFuture<List<String>> tokensFuture = segmentAsync(textSnapshot, finalLanguage);
        CompletableFuture<List<String>> keywordsFuture = extractKeywordsAsync(textSnapshot, finalLanguage, 10);
        CompletableFuture<Map<String, Double>> keywordScoresFuture = extractKeywordsWithScoreAsync(textSnapshot, finalLanguage, 10);
        CompletableFuture<List<String>> phrasesFuture = extractPhrasesAsync(textSnapshot, finalLanguage, 5);

        List<CompletableFuture<?>> futures = new ArrayList<>();
        futures.add(tokensFuture);
        futures.add(keywordsFuture);
        futures.add(keywordScoresFuture);
        futures.add(phrasesFuture);

        CompletableFuture<String> simplifiedFuture = null;
        CompletableFuture<String> traditionalFuture = null;
        CompletableFuture<Boolean> isTraditionalFuture = null;

        if ("zh-CN".equals(finalLanguage) || "zh-TW".equals(finalLanguage)) {
            simplifiedFuture = convertToSimplifiedAsync(textSnapshot);
            traditionalFuture = convertToTraditionalAsync(textSnapshot);
            isTraditionalFuture = asyncTaskExecutor.submitCpuTask(() -> isTraditionalChinese(textSnapshot),
                    "isTraditionalChinese");
            futures.add(simplifiedFuture);
            futures.add(traditionalFuture);
            futures.add(isTraditionalFuture);
        }

        final CompletableFuture<String> finalSimplifiedFuture = simplifiedFuture;
        final CompletableFuture<String> finalTraditionalFuture = traditionalFuture;
        final CompletableFuture<Boolean> finalIsTraditionalFuture = isTraditionalFuture;

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("text", textSnapshot);
                    result.put("detectedLanguage", finalLanguage);
                    result.put("languageName", LANGUAGE_NAMES.getOrDefault(finalLanguage, finalLanguage));

                    List<String> tokens = tokensFuture.join();
                    result.put("tokens", tokens);
                    result.put("tokenCount", tokens != null ? tokens.size() : 0);

                    result.put("keywords", keywordsFuture.join());
                    result.put("keywordScores", keywordScoresFuture.join());
                    result.put("phrases", phrasesFuture.join());

                    if ("zh-CN".equals(finalLanguage) || "zh-TW".equals(finalLanguage)) {
                        result.put("isTraditionalChinese", finalIsTraditionalFuture != null ? finalIsTraditionalFuture.join() : false);
                        result.put("simplifiedVersion", finalSimplifiedFuture != null ? finalSimplifiedFuture.join() : textSnapshot);
                        result.put("traditionalVersion", finalTraditionalFuture != null ? finalTraditionalFuture.join() : textSnapshot);
                    }

                    return result;
                });
    }

    private TextSegmenter getSegmenter(String language) {
        if (language == null) return null;
        switch (language) {
            case "zh-CN":
            case "zh-TW":
                return chineseSegmenter;
            case "en":
                return englishSegmenter;
            default:
                return null;
        }
    }

    private List<String> fallbackSegment(String text) {
        if (text == null || text.trim().isEmpty()) return Collections.emptyList();
        return Arrays.asList(text.split("[\\s,，。！？；：、\\u201C\\u201D\\u2018\\u2019（）【】《》.!?;:()\\[\\]{}]+"))
                .stream()
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private boolean isChineseChar(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
                block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION ||
                block == Character.UnicodeBlock.GENERAL_PUNCTUATION;
    }

    private boolean isTraditionalChar(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }

    public List<String> getSupportedLanguages() {
        return List.of("zh-CN", "zh-TW", "en");
    }

    public Map<String, String> getLanguageNames() {
        return LANGUAGE_NAMES;
    }
}
