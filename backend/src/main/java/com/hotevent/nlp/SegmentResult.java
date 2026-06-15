package com.hotevent.nlp;

public class SegmentResult {

    private String word;
    private String pos;
    private double score;

    public SegmentResult(String word, String pos) {
        this.word = word;
        this.pos = pos;
        this.score = 0.0;
    }

    public SegmentResult(String word, String pos, double score) {
        this.word = word;
        this.pos = pos;
        this.score = score;
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public String getPos() {
        return pos;
    }

    public void setPos(String pos) {
        this.pos = pos;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    @Override
    public String toString() {
        return word + "/" + pos + (score > 0 ? "(" + String.format("%.4f", score) + ")" : "");
    }
}
