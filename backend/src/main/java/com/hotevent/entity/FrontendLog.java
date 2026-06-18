package com.hotevent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "frontend_log")
@TableName("frontend_log")
public class FrontendLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @Column(name = "log_level", length = 20, nullable = false)
    private String logLevel;

    @Column(name = "message", columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(name = "page_url", length = 1000)
    private String pageUrl;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "browser_info", length = 500)
    private String browserInfo;

    @Column(name = "os_info", length = 200)
    private String osInfo;

    @Column(name = "screen_resolution", length = 50)
    private String screenResolution;

    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "error_type", length = 100)
    private String errorType;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "column_number")
    private Integer columnNumber;

    @Column(name = "additional_info", columnDefinition = "TEXT")
    private String additionalInfo;

    @Column(name = "log_time", nullable = false)
    private LocalDateTime logTime;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        if (logTime == null) {
            logTime = LocalDateTime.now();
        }
    }

    public static final String LEVEL_INFO = "INFO";
    public static final String LEVEL_WARN = "WARN";
    public static final String LEVEL_ERROR = "ERROR";
    public static final String LEVEL_DEBUG = "DEBUG";
}
