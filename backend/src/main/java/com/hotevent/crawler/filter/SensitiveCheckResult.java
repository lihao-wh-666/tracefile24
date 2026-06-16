package com.hotevent.crawler.filter;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SensitiveCheckResult {

    private boolean sensitive;
    private List<MatchDetail> matchedDetails;

    public SensitiveCheckResult() {
        this.sensitive = false;
        this.matchedDetails = new ArrayList<>();
    }

    public void addMatch(SensitiveType type, String matchedWord, String context) {
        this.sensitive = true;
        this.matchedDetails.add(new MatchDetail(type, matchedWord, context));
    }

    public List<String> getMatchedWords() {
        List<String> words = new ArrayList<>();
        for (MatchDetail detail : matchedDetails) {
            words.add(detail.getMatchedWord());
        }
        return words;
    }

    public List<SensitiveType> getMatchedTypes() {
        List<SensitiveType> types = new ArrayList<>();
        for (MatchDetail detail : matchedDetails) {
            if (!types.contains(detail.getType())) {
                types.add(detail.getType());
            }
        }
        return types;
    }

    @Data
    public static class MatchDetail {
        private SensitiveType type;
        private String matchedWord;
        private String context;

        public MatchDetail() {}

        public MatchDetail(SensitiveType type, String matchedWord, String context) {
            this.type = type;
            this.matchedWord = matchedWord;
            this.context = context;
        }
    }
}
