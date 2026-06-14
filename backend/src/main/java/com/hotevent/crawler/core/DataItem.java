package com.hotevent.crawler.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataItem {

    private String itemId;
    private String platform;
    private String source;
    private String title;
    private String content;
    private String summary;
    private String url;
    private String author;
    private String authorId;
    private Long viewCount;
    private Long likeCount;
    private Long commentCount;
    private Long shareCount;
    private Long hotValue;
    private Integer rank;
    private Integer hotRank;
    private String category;
    private String tags;
    private String coverImage;
    private String[] images;
    private String[] videos;
    private LocalDateTime publishTime;
    private LocalDateTime crawlTime;
    private String rawData;
    @Builder.Default
    private Map<String, Object> extra = new HashMap<>();
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    public Object getExtra(String key) {
        return extra != null ? extra.get(key) : null;
    }

    public void putExtra(String key, Object value) {
        if (extra == null) extra = new HashMap<>();
        extra.put(key, value);
    }

    public Object getMetadata(String key) {
        return metadata != null ? metadata.get(key) : null;
    }

    public void putMetadata(String key, Object value) {
        if (metadata == null) metadata = new HashMap<>();
        metadata.put(key, value);
    }
}
