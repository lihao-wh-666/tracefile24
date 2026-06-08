package com.hotevent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "hot_event")
@TableName("hot_event")
public class HotEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "source", nullable = false, length = 50)
    private String source;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Column(name = "hot_value")
    private Long hotValue;

    @Column(name = "hot_rank")
    private Integer hotRank;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "is_hot")
    private Boolean isHot;

    @Column(name = "is_rising")
    private Boolean isRising;

    @Column(name = "rising_rate")
    private Double risingRate;

    @Column(name = "crawl_time")
    private LocalDateTime crawlTime;

    @Column(name = "first_seen_time")
    private LocalDateTime firstSeenTime;

    @Column(name = "last_seen_time")
    private LocalDateTime lastSeenTime;

    @Column(name = "deleted")
    private Boolean deleted = false;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (firstSeenTime == null) {
            firstSeenTime = LocalDateTime.now();
        }
        if (lastSeenTime == null) {
            lastSeenTime = LocalDateTime.now();
        }
        if (isHot == null) {
            isHot = true;
        }
        if (isRising == null) {
            isRising = false;
        }
        if (deleted == null) {
            deleted = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
