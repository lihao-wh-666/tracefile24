package com.hotevent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HotTopicVO {

    private Long id;

    private String title;

    private String description;

    private String source;

    private String sourceUrl;

    private Long hotValue;

    private Integer hotRank;

    private String category;

    private String imageUrl;

    private Boolean isHot;

    private Boolean isRising;

    private Double risingRate;

    private LocalDateTime publishTime;

    private LocalDateTime crawlTime;
}
