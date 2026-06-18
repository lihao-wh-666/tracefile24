package com.hotevent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "log_archive")
@TableName("log_archive")
public class LogArchive {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @Column(name = "archive_name", length = 200, nullable = false)
    private String archiveName;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "log_count")
    private Integer logCount;

    @Column(name = "original_size_bytes")
    private Long originalSizeBytes;

    @Column(name = "archived_size_bytes")
    private Long archivedSizeBytes;

    @Column(name = "archive_path", length = 500)
    private String archivePath;

    @Column(name = "status", length = 20, nullable = false)
    private String status;

    @Column(name = "remark", length = 500)
    private String remark;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        if (status == null) {
            status = "PENDING";
        }
    }

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ARCHIVING = "ARCHIVING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
}
