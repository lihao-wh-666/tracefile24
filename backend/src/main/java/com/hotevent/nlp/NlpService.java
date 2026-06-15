package com.hotevent.nlp;

import com.hotevent.nlp.impl.ChineseSegmenter;
import com.hotevent.nlp.impl.EnglishSegmenter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NlpService {

    @Autowired
    private ChineseSegmenter chineseSegmenter;

    @Autowired
    private EnglishSegmenter englishSegmenter;

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

    public List<String> segmentAuto(String text) {
        String lang = detectLanguage(text);
        return segment(text, lang);
    }

    public List<String> segmentWithPos(String text, String language) {
        TextSegmenter segmenter = getSegmenter(language);
        if (segmenter != null) {
            return segmenter.segmentWithPos(text);
        }
        return fallbackSegment(text);
    }

    public List<String> extractKeywords(String text, String language, int count) {
        TextSegmenter segmenter = getSegmenter(language);
        if (segmenter != null) {
            return segmenter.extractKeywords(text, count);
        }
        return segmentAuto(text).stream().limit(count).collect(Collectors.toList());
    }

    public Map<String, Double> extractKeywordsWithScore(String text, String language, int count) {
        TextSegmenter segmenter = getSegmenter(language);
        if (segmenter != null) {
            return segmenter.extractKeywordsWithScore(text, count);
        }
        return Collections.emptyMap();
    }

    public List<String> extractPhrases(String text, String language, int count) {
        TextSegmenter segmenter = getSegmenter(language);
        if (segmenter != null) {
            return segmenter.extractPhrases(text, count);
        }
        return Collections.emptyList();
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

    public String convertToTraditional(String text) {
        return chineseSegmenter.convertToTraditional(text);
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
        return Arrays.asList(text.split("[\\s,，。！？；：、""''（）【】《》.!?;:()\\[\\]{}]+"))
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
