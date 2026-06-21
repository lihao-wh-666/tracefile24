package com.hotevent.config;

import com.hotevent.cache.BloomFilterManager;
import com.hotevent.entity.HotEvent;
import com.hotevent.repository.HotEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@Order(2)
public class BloomFilterInitializer implements CommandLineRunner {

    @Autowired
    private BloomFilterManager bloomFilterManager;

    @Autowired
    private HotEventRepository hotEventRepository;

    @Override
    public void run(String... args) {
        log.info("[BloomFilter] 开始预热布隆过滤器...");
        try {
            warmUpEventIdBloomFilter();
            log.info("[BloomFilter] 布隆过滤器预热完成");
        } catch (Exception e) {
            log.warn("[BloomFilter] 布隆过滤器预热失败: {}", e.getMessage());
        }
    }

    private void warmUpEventIdBloomFilter() {
        int pageSize = 1000;
        int page = 0;
        long totalAdded = 0;

        while (true) {
            Page<HotEvent> hotEventPage = hotEventRepository.findAll(
                    PageRequest.of(page, pageSize));
            List<HotEvent> events = hotEventPage.getContent();

            if (events == null || events.isEmpty()) {
                break;
            }

            for (HotEvent event : events) {
                if (event.getId() != null && !Boolean.TRUE.equals(event.getDeleted())) {
                    bloomFilterManager.addEventId(event.getId());
                    totalAdded++;
                }
            }

            if (!hotEventPage.hasNext()) {
                break;
            }
            page++;
        }

        log.info("[BloomFilter] 事件ID布隆过滤器共添加 {} 条记录", totalAdded);
    }
}
