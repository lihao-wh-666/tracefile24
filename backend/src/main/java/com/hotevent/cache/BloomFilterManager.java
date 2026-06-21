package com.hotevent.cache;

import com.google.common.hash.Funnels;
import com.google.common.hash.Hashing;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class BloomFilterManager {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CacheProperties cacheProperties;

    private static final int DEFAULT_EXPECTED_INSERTIONS = 1000000;
    private static final double DEFAULT_FPP = 0.01;

    private int numHashFunctions;
    private int bitSize;

    public BloomFilterManager() {
        this.bitSize = optimalNumOfBits(DEFAULT_EXPECTED_INSERTIONS, DEFAULT_FPP);
        this.numHashFunctions = optimalNumOfHashFunctions(DEFAULT_EXPECTED_INSERTIONS, this.bitSize);
    }

    public void add(String filterKey, String value) {
        if (!cacheProperties.getPenetrationProtection().isBloomFilterEnabled()) return;
        try {
            long[] offsets = getOffsets(value);
            for (long offset : offsets) {
                stringRedisTemplate.opsForValue().setBit(filterKey, offset, true);
            }
        } catch (Exception e) {
            log.warn("[BloomFilter] 添加元素失败 filter={}, value={}: {}", filterKey, value, e.getMessage());
        }
    }

    public boolean mightContain(String filterKey, String value) {
        if (!cacheProperties.getPenetrationProtection().isBloomFilterEnabled()) return true;
        try {
            long[] offsets = getOffsets(value);
            for (long offset : offsets) {
                Boolean bit = stringRedisTemplate.opsForValue().getBit(filterKey, offset);
                if (bit == null || !bit) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("[BloomFilter] 检查元素失败 filter={}, value={}: {}", filterKey, value, e.getMessage());
            return true;
        }
    }

    public void addEventId(Long id) {
        add(CacheKeyConstants.BLOOM_FILTER_EVENT_ID, String.valueOf(id));
    }

    public boolean eventIdMightExist(Long id) {
        if (id == null || id <= 0) return false;
        return mightContain(CacheKeyConstants.BLOOM_FILTER_EVENT_ID, String.valueOf(id));
    }

    public void rebuildEventIdBloomFilter(Iterable<Long> allIds) {
        try {
            stringRedisTemplate.delete(CacheKeyConstants.BLOOM_FILTER_EVENT_ID);
            int count = 0;
            for (Long id : allIds) {
                addEventId(id);
                count++;
            }
            log.info("[BloomFilter] 重建事件ID布隆过滤器完成，共{}条记录", count);
        } catch (Exception e) {
            log.warn("[BloomFilter] 重建事件ID布隆过滤器失败: {}", e.getMessage());
        }
    }

    private long[] getOffsets(String value) {
        long[] offsets = new long[numHashFunctions];
        long hash1 = Hashing.murmur3_128().hashString(value, StandardCharsets.UTF_8).asLong();
        long hash2 = Hashing.sipHash24().hashString(value, StandardCharsets.UTF_8).asLong();
        long combined = hash1;
        for (int i = 0; i < numHashFunctions; i++) {
            offsets[i] = Math.abs((combined + (long) i * hash2) % bitSize);
            if (offsets[i] < 0) offsets[i] += bitSize;
        }
        return offsets;
    }

    private int optimalNumOfBits(long n, double p) {
        if (p == 0) p = Double.MIN_VALUE;
        return (int) (-n * Math.log(p) / (Math.log(2) * Math.log(2)));
    }

    private int optimalNumOfHashFunctions(long n, long m) {
        return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
    }
}
