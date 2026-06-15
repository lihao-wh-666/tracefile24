package com.hotevent.nlp;

import java.util.List;
import java.util.Map;

public interface TextSegmenter {

    String getLanguage();

    List<String> segment(String text);

    List<String> segmentWithPos(String text);

    List<String> extractKeywords(String text, int count);

    Map<String, Double> extractKeywordsWithScore(String text, int count);

    List<String> extractPhrases(String text, int count);
}
