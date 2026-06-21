package com.hotevent.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "hot-event.cache")
public class CacheProperties {

    private boolean enabled = true;
    private String keyPrefix = "hotevent:";
    private int nullCacheTtlSeconds = 60;
    private boolean statsEnabled = true;

    private HotEventCacheProps hotEvent = new HotEventCacheProps();
    private DictionaryCacheProps dictionary = new DictionaryCacheProps();
    private SysConfigCacheProps sysConfig = new SysConfigCacheProps();
    private TranslationCacheProps translation = new TranslationCacheProps();
    private PenetrationProtectionProps penetrationProtection = new PenetrationProtectionProps();
    private BreakdownProtectionProps breakdownProtection = new BreakdownProtectionProps();
    private AvalancheProtectionProps avalancheProtection = new AvalancheProtectionProps();

    @Data
    public static class HotEventCacheProps {
        private int detailTtlSeconds = 300;
        private int listTtlSeconds = 60;
        private int trendingTtlSeconds = 120;
        private int statsTtlSeconds = 300;
        private int sourcesTtlSeconds = 1800;
        private int categoriesTtlSeconds = 1800;
        private int rankedTtlSeconds = 120;
        private int randomTtlOffsetSeconds = 30;
    }

    @Data
    public static class DictionaryCacheProps {
        private int ttlSeconds = 86400;
        private int refreshIntervalSeconds = 43200;
    }

    @Data
    public static class SysConfigCacheProps {
        private int ttlSeconds = 3600;
        private boolean refreshOnWrite = true;
    }

    @Data
    public static class TranslationCacheProps {
        private int ttlSeconds = 259200;
    }

    @Data
    public static class PenetrationProtectionProps {
        private boolean bloomFilterEnabled = true;
        private boolean nullValueCacheEnabled = true;
    }

    @Data
    public static class BreakdownProtectionProps {
        private boolean mutexLockEnabled = true;
        private long lockWaitTimeMs = 3000;
        private long lockLeaseTimeMs = 60000;
    }

    @Data
    public static class AvalancheProtectionProps {
        private boolean randomOffsetEnabled = true;
        private int maxOffsetSeconds = 300;
    }
}
