package com.hotevent.nlp.impl;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.Segment;
import com.hankcs.hanlp.seg.common.Term;
import com.hotevent.nlp.SegmentResult;
import com.hotevent.nlp.TextSegmenter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ChineseSegmenter implements TextSegmenter {

    private static final String LANGUAGE = "zh";

    @Value("${hot-event.i18n.nlp.segmenter.chinese.mode:standard}")
    private String segmentMode;

    @Value("${hot-event.i18n.nlp.segmenter.chinese.traditional-chinese-support:true}")
    private boolean traditionalChineseSupport;

    private volatile Segment segmenter;
    private volatile Segment traditionalSegmenter;

    private Segment getSegmenter() {
        if (segmenter == null) {
            synchronized (this) {
                if (segmenter == null) {
                    switch (segmentMode.toLowerCase()) {
                        case "nlp":
                            segmenter = HanLP.newSegment().enableCustomDictionary(true)
                                    .enablePlaceRecognize(true).enableOrganizationRecognize(true);
                            break;
                        case "index":
                            segmenter = HanLP.newSegment().enableIndexMode(true);
                            break;
                        case "nshortest":
                            segmenter = HanLP.newSegment();
                            break;
                        default:
                            segmenter = HanLP.newSegment();
                            break;
                    }
                    segmenter.enableCustomDictionary(true)
                            .enableNumberQuantifierRecognize(true)
                            .enableNameRecognize(true)
                            .enableTranslatedNameRecognize(true)
                            .enableJapaneseNameRecognize(true)
                            .enablePlaceRecognize(true)
                            .enableOrganizationRecognize(true);
                    log.info("[ChineseSegmenter] Initialized with mode: {}", segmentMode);
                }
            }
        }
        return segmenter;
    }

    private Segment getTraditionalSegmenter() {
        if (traditionalSegmenter == null && traditionalChineseSupport) {
            synchronized (this) {
                if (traditionalSegmenter == null && traditionalChineseSupport) {
                    traditionalSegmenter = HanLP.newSegment();
                    traditionalSegmenter.enableCustomDictionary(true)
                            .enableNumberQuantifierRecognize(true)
                            .enableNameRecognize(true)
                            .enablePlaceRecognize(true)
                            .enableOrganizationRecognize(true);
                    log.info("[ChineseSegmenter] Traditional Chinese segmenter initialized");
                }
            }
        }
        return traditionalSegmenter;
    }

    private String toSimplified(String text) {
        return HanLP.convertToSimplifiedChinese(text);
    }

    private String toTraditional(String text) {
        return HanLP.convertToTraditionalChinese(text);
    }

    public boolean isTraditionalChinese(String text) {
        if (text == null || text.isEmpty()) return false;
        String simplified = toSimplified(text);
        return !text.equals(simplified);
    }

    @Override
    public String getLanguage() {
        return LANGUAGE;
    }

    @Override
    public List<String> segment(String text) {
        if (text == null || text.trim().isEmpty()) return Collections.emptyList();

        String processedText = text;
        Segment seg = getSegmenter();

        if (traditionalChineseSupport && isTraditionalChinese(text)) {
            processedText = toSimplified(text);
            log.debug("[ChineseSegmenter] Detected traditional Chinese, converted to simplified for segmentation");
        }

        try {
            List<Term> terms = seg.seg(processedText);
            return terms.stream()
                    .filter(t -> t.word.trim().length() > 0)
                    .filter(t -> !isStopWord(t.word, t.nature != null ? t.nature.toString() : ""))
                    .map(t -> t.word.trim())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("[ChineseSegmenter] Segmentation failed: {}", e.getMessage());
            return Arrays.asList(text.split("[\\s，。！？；：、\\u201C\\u201D\\u2018\\u2019（）【】《》]+"));
        }
    }

    @Override
    public List<String> segmentWithPos(String text) {
        if (text == null || text.trim().isEmpty()) return Collections.emptyList();

        String processedText = text;
        Segment seg = getSegmenter();

        if (traditionalChineseSupport && isTraditionalChinese(text)) {
            processedText = toSimplified(text);
        }

        try {
            List<Term> terms = seg.seg(processedText);
            return terms.stream()
                    .filter(t -> t.word.trim().length() > 0)
                    .map(t -> t.word.trim() + "/" + (t.nature != null ? t.nature.toString() : "unknown"))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("[ChineseSegmenter] Segmentation with POS failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<String> extractKeywords(String text, int count) {
        if (text == null || text.trim().isEmpty()) return Collections.emptyList();

        String processedText = text;
        if (traditionalChineseSupport && isTraditionalChinese(text)) {
            processedText = toSimplified(text);
        }

        try {
            return HanLP.extractKeyword(processedText, count);
        } catch (Exception e) {
            log.error("[ChineseSegmenter] Keyword extraction failed: {}", e.getMessage());
            return segment(text).stream().limit(count).collect(Collectors.toList());
        }
    }

    @Override
    public Map<String, Double> extractKeywordsWithScore(String text, int count) {
        if (text == null || text.trim().isEmpty()) return Collections.emptyMap();

        String processedText = text;
        if (traditionalChineseSupport && isTraditionalChinese(text)) {
            processedText = toSimplified(text);
        }

        try {
            List<String> keywords = HanLP.extractKeyword(processedText, count);
            Map<String, Double> result = new LinkedHashMap<>();
            double score = count;
            for (String keyword : keywords) {
                result.put(keyword, score / count);
                score--;
            }
            return result;
        } catch (Exception e) {
            log.error("[ChineseSegmenter] Keyword extraction with score failed: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    @Override
    public List<String> extractPhrases(String text, int count) {
        if (text == null || text.trim().isEmpty()) return Collections.emptyList();

        String processedText = text;
        if (traditionalChineseSupport && isTraditionalChinese(text)) {
            processedText = toSimplified(text);
        }

        try {
            return HanLP.extractPhrase(processedText, count);
        } catch (Exception e) {
            log.error("[ChineseSegmenter] Phrase extraction failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<SegmentResult> segmentDetailed(String text) {
        if (text == null || text.trim().isEmpty()) return Collections.emptyList();

        String processedText = text;
        Segment seg = getSegmenter();

        if (traditionalChineseSupport && isTraditionalChinese(text)) {
            processedText = toSimplified(text);
        }

        try {
            List<Term> terms = seg.seg(processedText);
            return terms.stream()
                    .filter(t -> t.word.trim().length() > 0)
                    .filter(t -> !isStopWord(t.word, t.nature != null ? t.nature.toString() : ""))
                    .map(t -> new SegmentResult(t.word.trim(), t.nature != null ? t.nature.toString() : "unknown"))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("[ChineseSegmenter] Detailed segmentation failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<SegmentResult> segmentTraditionalDetailed(String text) {
        if (text == null || text.trim().isEmpty()) return Collections.emptyList();

        Segment tradSeg = getTraditionalSegmenter();
        if (tradSeg == null) {
            return segmentDetailed(text);
        }

        try {
            List<Term> terms = tradSeg.seg(text);
            return terms.stream()
                    .filter(t -> t.word.trim().length() > 0)
                    .filter(t -> !isStopWord(t.word, t.nature != null ? t.nature.toString() : ""))
                    .map(t -> new SegmentResult(toTraditional(t.word.trim()), t.nature != null ? t.nature.toString() : "unknown"))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("[ChineseSegmenter] Traditional Chinese detailed segmentation failed: {}", e.getMessage());
            return segmentDetailed(text);
        }
    }

    public String convertToSimplified(String traditionalText) {
        return HanLP.convertToSimplifiedChinese(traditionalText);
    }

    public String convertToTraditional(String simplifiedText) {
        return HanLP.convertToTraditionalChinese(simplifiedText);
    }

    private boolean isStopWord(String word, String pos) {
        if (word.length() == 1) {
            char c = word.charAt(0);
            if (c >= '0' && c <= '9') return true;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) return true;
        }
        Set<String> stopPos = Set.of("w", "wp", "ws", "wu", "wkz", "wky", "wyz", "wyy", "wj", "ww", "wt", "wd", "wf");
        if (stopPos.contains(pos)) return true;
        return false;
    }
}
