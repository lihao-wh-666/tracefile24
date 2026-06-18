-- 数据库初始化脚本
-- 强制使用 UTF-8 编码读取和写入，防止中文乱码
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;
SET character_set_client = utf8mb4;
SET character_set_connection = utf8mb4;
SET character_set_database = utf8mb4;
SET character_set_results = utf8mb4;
SET character_set_server = utf8mb4;

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

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码',
    nickname VARCHAR(100) COMMENT '昵称',
    email VARCHAR(200) COMMENT '邮箱',
    avatar VARCHAR(500) COMMENT '头像URL',
    role VARCHAR(20) NOT NULL DEFAULT 'VIEWER' COMMENT '角色',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    password_version INT DEFAULT 0 COMMENT '密码版本',
    login_fail_count INT DEFAULT 0 COMMENT '登录失败次数',
    last_login_fail_time DATETIME COMMENT '最后登录失败时间',
    lock_time DATETIME COMMENT '账号锁定时间',
    deleted TINYINT(1) DEFAULT 0 COMMENT '是否删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_username (username),
    INDEX idx_role (role),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 系统配置表
CREATE TABLE IF NOT EXISTS sys_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    config_key VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键',
    config_value VARCHAR(500) NOT NULL COMMENT '配置值',
    config_name VARCHAR(100) COMMENT '配置名称',
    description VARCHAR(500) COMMENT '配置描述',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- 初始化系统配置
INSERT INTO sys_config (config_key, config_value, config_name, description) VALUES
('maxLoginAttempts', '5', '最大登录失败次数', '用户在锁定时间窗口内允许的最大密码错误次数'),
('loginLockMinutes', '30', '账号锁定时间(分钟)', '超过最大失败次数后账号被锁定的时间'),
('loginAttemptWindowMinutes', '30', '登录失败统计窗口(分钟)', '统计登录失败次数的时间窗口'),
('sessionTimeoutMinutes', '30', '登录超时时间(分钟)', '用户无操作后自动登出的时间'),
('sessionWarningMinutes', '5', '超时警告提前时间(分钟)', '在超时前多久弹出提示警告用户'),
('sensitiveFilterEnabled', 'true', '敏感内容过滤开关', '是否启用敏感内容过滤功能，过滤涉政、色情、辱骂、广告等内容'),
('sensitiveKeywords.politics', '法轮功,邪教', '涉政敏感词', '多个关键词用英文逗号、中文逗号或空格分隔'),
('sensitiveKeywords.porn', '色情,黄色,成人,av,性爱', '色情敏感词', '多个关键词用英文逗号、中文逗号或空格分隔'),
('sensitiveKeywords.abuse', '傻逼,蠢货,狗娘养,王八蛋,滚蛋,狗屎', '辱骂敏感词', '多个关键词用英文逗号、中文逗号或空格分隔'),
('sensitiveKeywords.ad', '加微信,加qq,代购,代理,刷单,兼职赚钱,网赚', '广告敏感词', '多个关键词用英文逗号、中文逗号或空格分隔'),
('sensitiveKeywords.violence', '杀人,自杀,暴力,血腥', '暴力敏感词', '多个关键词用英文逗号、中文逗号或空格分隔'),
('sensitiveKeywords.gambling', '赌博,博彩,彩票,百家乐,老虎机', '赌博敏感词', '多个关键词用英文逗号、中文逗号或空格分隔'),
('sensitiveKeywords.drug', '毒品,大麻,可卡因,海洛因', '毒品敏感词', '多个关键词用英文逗号、中文逗号或空格分隔'),
('sensitiveRegex.ad', '(微信|wx|vx)[\\\\s:：]?[a-zA-Z0-9_-]{5,}||(qq|扣扣)[\\\\s:：]?\\\\d{5,}||(电话|手机|联系电话)[\\\\s:：]?1[3-9]\\\\d{9}', '广告正则表达式', '多个正则表达式用 || 分隔'),
('sensitiveRegex.porn', '(www\\\\.)?[^\\\\s]*?(porn|sex|xxx|成人|色情)[^\\\\s]*', '色情正则表达式', '多个正则表达式用 || 分隔')
ON DUPLICATE KEY UPDATE
    config_value = VALUES(config_value),
    config_name = VALUES(config_name),
    description = VALUES(description);

-- 存储过程：在 sys_user 表中安全地添加列
DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DELIMITER //
CREATE PROCEDURE add_column_if_not_exists(
    IN tableName VARCHAR(100),
    IN colName VARCHAR(100),
    IN colDef TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = tableName
          AND COLUMN_NAME = colName
    ) THEN
        SET @sql = CONCAT('ALTER TABLE `', tableName, '` ADD COLUMN `', colName, '` ', colDef);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL add_column_if_not_exists('sys_user', 'login_fail_count', 'INT DEFAULT 0 COMMENT ''登录失败次数'' AFTER password_version');
CALL add_column_if_not_exists('sys_user', 'last_login_fail_time', 'DATETIME COMMENT ''最后登录失败时间'' AFTER login_fail_count');
CALL add_column_if_not_exists('sys_user', 'lock_time', 'DATETIME COMMENT ''账号锁定时间'' AFTER last_login_fail_time');

DROP PROCEDURE IF EXISTS add_column_if_not_exists;

CREATE TABLE IF NOT EXISTS event_translation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    event_id BIGINT NOT NULL COMMENT '关联事件ID',
    language VARCHAR(10) NOT NULL COMMENT '语言代码(zh-CN/zh-TW/en)',
    title VARCHAR(500) COMMENT '翻译标题',
    description TEXT COMMENT '翻译描述',
    category VARCHAR(100) COMMENT '翻译分类',
    translation_provider VARCHAR(50) COMMENT '翻译提供者(baidu/google/manual/internal)',
    is_verified TINYINT(1) DEFAULT 0 COMMENT '是否人工校验',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_event_language (event_id, language),
    INDEX idx_event_id (event_id),
    INDEX idx_language (language)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事件翻译表';

-- 热点事件修改日志表
CREATE TABLE IF NOT EXISTS hot_event_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    event_id BIGINT NOT NULL COMMENT '关联事件ID',
    event_title VARCHAR(500) COMMENT '事件标题',
    source VARCHAR(50) COMMENT '数据来源',
    operation_type VARCHAR(20) NOT NULL COMMENT '操作类型(INSERT/UPDATE/DELETE)',
    field_name VARCHAR(100) COMMENT '修改字段名',
    old_value TEXT COMMENT '修改前值',
    new_value TEXT COMMENT '修改后值',
    operator_id BIGINT COMMENT '操作人ID',
    operator_name VARCHAR(100) COMMENT '操作人名称',
    reason VARCHAR(500) COMMENT '修改原因',
    operation_time DATETIME NOT NULL COMMENT '操作时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_event_id (event_id),
    INDEX idx_source (source),
    INDEX idx_operation_type (operation_type),
    INDEX idx_operator_id (operator_id),
    INDEX idx_operation_time (operation_time),
    INDEX idx_source_operation_time (source, operation_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='热点事件修改日志表';

CREATE TABLE IF NOT EXISTS log_archive (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    archive_name VARCHAR(200) NOT NULL COMMENT '归档名称',
    start_time DATETIME NOT NULL COMMENT '日志起始时间',
    end_time DATETIME NOT NULL COMMENT '日志结束时间',
    log_count INT DEFAULT 0 COMMENT '归档日志条数',
    original_size_bytes BIGINT DEFAULT 0 COMMENT '原始数据大小(字节)',
    archived_size_bytes BIGINT DEFAULT 0 COMMENT '压缩后大小(字节)',
    archive_path VARCHAR(500) COMMENT '归档文件路径',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态(PENDING/ARCHIVING/COMPLETED/FAILED)',
    log_type VARCHAR(30) NOT NULL DEFAULT 'DATABASE_LOG' COMMENT '日志类型(DATABASE_LOG/BACKEND_LOG/FRONTEND_LOG)',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_status (status),
    INDEX idx_log_type (log_type),
    INDEX idx_start_time (start_time),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='日志归档记录表';

CREATE TABLE IF NOT EXISTS frontend_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    log_level VARCHAR(20) NOT NULL COMMENT '日志级别(INFO/WARN/ERROR/DEBUG)',
    message TEXT NOT NULL COMMENT '日志消息',
    page_url VARCHAR(1000) COMMENT '页面URL',
    user_agent VARCHAR(500) COMMENT '用户代理',
    browser_info VARCHAR(500) COMMENT '浏览器信息',
    os_info VARCHAR(200) COMMENT '操作系统信息',
    screen_resolution VARCHAR(50) COMMENT '屏幕分辨率',
    stack_trace TEXT COMMENT '堆栈信息',
    user_id BIGINT COMMENT '用户ID',
    username VARCHAR(100) COMMENT '用户名',
    error_type VARCHAR(100) COMMENT '错误类型',
    line_number INT COMMENT '行号',
    column_number INT COMMENT '列号',
    additional_info TEXT COMMENT '附加信息',
    log_time DATETIME NOT NULL COMMENT '日志产生时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_log_level (log_level),
    INDEX idx_log_time (log_time),
    INDEX idx_user_id (user_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='前端日志表';
