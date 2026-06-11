package com.hotevent.config;

import com.hotevent.dto.UserCreateRequest;
import com.hotevent.entity.Role;
import com.hotevent.service.CrawlerService;
import com.hotevent.service.SysConfigService;
import com.hotevent.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private CrawlerService crawlerService;

    @Autowired
    private UserService userService;

    @Autowired
    private SysConfigService sysConfigService;

    @Override
    public void run(String... args) {
        initSysConfigs();
        initUsers();
        log.info("应用启动，开始执行初始数据抓取...");
        try {
            crawlerService.crawlAllSources();
            log.info("初始数据抓取完成");
        } catch (Exception e) {
            log.error("初始数据抓取失败", e);
        }
    }

    private void initSysConfigs() {
        log.info("开始初始化系统配置...");
        try {
            sysConfigService.initDefaultConfigs();
            log.info("系统配置初始化完成");
        } catch (Exception e) {
            log.error("系统配置初始化失败", e);
        }
    }

    private void initUsers() {
        log.info("开始初始化用户数据...");
        try {
            createUserIfNotExists("admin", "admin123", "超级管理员", "admin@example.com", Role.ADMIN);
            createUserIfNotExists("user1", "user123", "普通用户1", "user1@example.com", Role.USER);
            createUserIfNotExists("user2", "user123", "普通用户2", "user2@example.com", Role.USER);
            createUserIfNotExists("user3", "user123", "普通用户3", "user3@example.com", Role.USER);
            log.info("用户数据初始化完成");
        } catch (Exception e) {
            log.error("用户数据初始化失败", e);
        }
    }

    private void createUserIfNotExists(String username, String password, String nickname, String email, Role role) {
        try {
            userService.getByUsername(username);
            log.info("用户已存在，跳过创建: {}", username);
        } catch (Exception e) {
            UserCreateRequest request = new UserCreateRequest();
            request.setUsername(username);
            request.setPassword(password);
            request.setNickname(nickname);
            request.setEmail(email);
            request.setRole(role);
            request.setEnabled(true);
            userService.create(request);
            log.info("创建用户成功: {}", username);
        }
    }
}
