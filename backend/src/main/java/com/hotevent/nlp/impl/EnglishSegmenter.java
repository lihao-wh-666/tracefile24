package com.hotevent.nlp.impl;

import com.hotevent.nlp.SegmentResult;
import com.hotevent.nlp.TextSegmenter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
public class EnglishSegmenter implements TextSegmenter {

    private static final String LANGUAGE = "en";

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "is", "are", "was", "were", "be", "been",
            "being", "have", "has", "had", "do", "does", "did", "will", "would",
            "could", "should", "may", "might", "shall", "can", "need", "dare",
            "it", "its", "this", "that", "these", "those", "i", "me", "my",
            "we", "our", "you", "your", "he", "him", "his", "she", "her",
            "they", "them", "their", "what", "which", "who", "whom", "when",
            "where", "why", "how", "not", "no", "nor", "if", "then", "than",
            "too", "very", "just", "about", "above", "after", "again", "all",
            "also", "am", "as", "because", "before", "between", "both", "each",
            "few", "further", "get", "here", "into", "more", "most", "other",
            "out", "over", "own", "same", "so", "some", "such", "up"
    );

    private static final Pattern WORD_PATTERN = Pattern.compile("[a-zA-Z]+(?:'[a-zA-Z]+)?");
    private static final Pattern SENTENCE_PATTERN = Pattern.compile("[.!?]+\\s+");

    private static final Set<String> NOUN_POS = Set.of("NN", "NNS", "NNP", "NNPS");
    private static final Set<String> VERB_POS = Set.of("VB", "VBD", "VBG", "VBN", "VBP", "VBZ");
    private static final Set<String> ADJ_POS = Set.of("JJ", "JJR", "JJS");
    private static final Set<String> ADV_POS = Set.of("RB", "RBR", "RBS");

    @Override
    public String getLanguage() {
        return LANGUAGE;
    }

    @Override
    public List<String> segment(String text) {
        if (text == null || text.trim().isEmpty()) return Collections.emptyList();

        Matcher matcher = WORD_PATTERN.matcher(text);
        List<String> words = new ArrayList<>();
        while (matcher.find()) {
            String word = matcher.group().toLowerCase();
            if (!STOP_WORDS.contains(word) && word.length() > 1) {
                words.add(word);
            }
        }
        return words;
    }

    @Override
    public List<String> segmentWithPos(String text) {
        if (text == null || text.trim().isEmpty()) return Collections.emptyList();

        Matcher matcher = WORD_PATTERN.matcher(text);
        List<String> results = new ArrayList<>();
        while (matcher.find()) {
            String word = matcher.group();
            String pos = guessPos(word);
            results.add(word + "/" + pos);
        }
        return results;
    }

    @Override
    public List<String> extractKeywords(String text, int count) {
        if (text == null || text.trim().isEmpty()) return Collections.emptyList();

        List<String> tokens = segment(text);
        Map<String, Integer> freq = new HashMap<>();
        for (String token : tokens) {
            freq.merge(token, 1, Integer::sum);
        }

        return freq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(count)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Double> extractKeywordsWithScore(String text, int count) {
        if (text == null || text.trim().isEmpty()) return Collections.emptyMap();

        List<String> tokens = segment(text);
        Map<String, Integer> freq = new HashMap<>();
        for (String token : tokens) {
            freq.merge(token, 1, Integer::sum);
        }

        int maxFreq = freq.values().stream().max(Integer::compare).orElse(1);

        return freq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(count)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> (double) e.getValue() / maxFreq,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    @Override
    public List<String> extractPhrases(String text, int count) {
        if (text == null || text.trim().isEmpty()) return Collections.emptyList();

        List<String> sentences = splitSentences(text);
        Map<String, Integer> phraseFreq = new HashMap<>();

        for (String sentence : sentences) {
            List<String> words = segmentWithPos(sentence);
            for (int i = 0; i < words.size() - 1; i++) {
                String[] parts1 = words.get(i).split("/");
                String[] parts2 = words.get(i + 1).split("/");
                if (parts1.length == 2 && parts2.length == 2) {
                    String pos1 = parts1[1];
                    String pos2 = parts2[1];
                    if ((NOUN_POS.contains(pos1) && ADJ_POS.contains(pos2)) ||
                            (ADJ_POS.contains(pos1) && NOUN_POS.contains(pos2)) ||
                            (NOUN_POS.contains(pos1) && NOUN_POS.contains(pos2))) {
                        String phrase = parts1[0] + " " + parts2[0];
                        phraseFreq.merge(phrase, 1, Integer::sum);
                    }
                }
            }
        }

        return phraseFreq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(count)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public List<SegmentResult> segmentDetailed(String text) {
        if (text == null || text.trim().isEmpty()) return Collections.emptyList();

        Matcher matcher = WORD_PATTERN.matcher(text);
        List<SegmentResult> results = new ArrayList<>();
        while (matcher.find()) {
            String word = matcher.group();
            String pos = guessPos(word);
            double score = STOP_WORDS.contains(word.toLowerCase()) ? 0.0 : 1.0;
            results.add(new SegmentResult(word, pos, score));
        }
        return results;
    }

    public List<String> splitSentences(String text) {
        if (text == null || text.trim().isEmpty()) return Collections.emptyList();
        String[] parts = SENTENCE_PATTERN.split(text);
        return Arrays.stream(parts)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private String guessPos(String word) {
        if (word.isEmpty()) return "UNK";

        String lower = word.toLowerCase();

        if (STOP_WORDS.contains(lower)) return "IN";

        if (Character.isUpperCase(word.charAt(0)) && word.length() > 1) {
            boolean allUpper = word.equals(word.toUpperCase());
            if (allUpper) return "NNP";
        }

        if (lower.endsWith("ing")) return "VBG";
        if (lower.endsWith("ed")) return "VBD";
        if (lower.endsWith("ly")) return "RB";
        if (lower.endsWith("tion") || lower.endsWith("sion") || lower.endsWith("ment") ||
                lower.endsWith("ness") || lower.endsWith("ity") || lower.endsWith("ence") ||
                lower.endsWith("ance")) return "NN";
        if (lower.endsWith("ous") || lower.endsWith("ive") || lower.endsWith("ful") ||
                lower.endsWith("less") || lower.endsWith("able") || lower.endsWith("ible") ||
                lower.endsWith("al") || lower.endsWith("ial")) return "JJ";
        if (lower.endsWith("es") || lower.endsWith("s")) {
            if (!lower.endsWith("ss") && !lower.endsWith("us")) return "NNS";
        }

        return "NN";
    }
}
