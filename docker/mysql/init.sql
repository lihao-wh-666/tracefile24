-- 数据库初始化脚本
-- 创建数据库
CREATE DATABASE IF NOT EXISTS hot_event_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE hot_event_db;

-- 热点事件表
CREATE TABLE IF NOT EXISTS hot_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    title VARCHAR(500) NOT NULL COMMENT '事件标题',
    description TEXT COMMENT '事件描述',
    source VARCHAR(50) NOT NULL COMMENT '数据来源',
    source_url VARCHAR(1000) COMMENT '源链接',
    hot_value BIGINT DEFAULT 0 COMMENT '热度值',
    hot_rank INT DEFAULT 0 COMMENT '热度排名',
    category VARCHAR(100) COMMENT '分类',
    image_url VARCHAR(1000) COMMENT '图片链接',
    is_hot TINYINT(1) DEFAULT 1 COMMENT '是否热门',
    is_rising TINYINT(1) DEFAULT 0 COMMENT '是否飙升',
    rising_rate DOUBLE DEFAULT 0 COMMENT '上升率',
    crawl_time DATETIME COMMENT '抓取时间',
    first_seen_time DATETIME COMMENT '首次出现时间',
    last_seen_time DATETIME COMMENT '最后出现时间',
    deleted TINYINT(1) DEFAULT 0 COMMENT '是否删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_source (source),
    INDEX idx_hot_value (hot_value),
    INDEX idx_crawl_time (crawl_time),
    INDEX idx_source_title (source, title(255)),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='热点事件表';

-- 抓取记录表
CREATE TABLE IF NOT EXISTS crawl_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    source VARCHAR(50) NOT NULL COMMENT '数据来源',
    status VARCHAR(20) COMMENT '状态',
    event_count INT DEFAULT 0 COMMENT '事件总数',
    success_count INT DEFAULT 0 COMMENT '成功数量',
    fail_count INT DEFAULT 0 COMMENT '失败数量',
    error_message TEXT COMMENT '错误信息',
    cost_time_ms BIGINT DEFAULT 0 COMMENT '耗时(毫秒)',
    crawl_time DATETIME COMMENT '抓取时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_source (source),
    INDEX idx_crawl_time (crawl_time),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='抓取记录表';
