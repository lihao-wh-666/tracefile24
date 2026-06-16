package com.hotevent.i18n;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "hot-event.i18n")
public class I18nProperties {

    private String defaultLocale = "zh-CN";
    private List<String> supportedLocales = List.of("zh-CN", "zh-TW", "en");
    private Translation translation = new Translation();
    private Nlp nlp = new Nlp();

    @Data
    public static class Translation {
        private boolean enabled = true;
        private String provider = "mymemory";
        private boolean cacheEnabled = true;
        private long cacheTtlHours = 72;
        private boolean autoTranslateOnCrawl = false;
        private Baidu baidu = new Baidu();
        private Google google = new Google();
        private MyMemory myMemory = new MyMemory();
    }

    @Data
    public static class Baidu {
        private String appId;
        private String secretKey;
        private String apiUrl = "https://fanyi-api.baidu.com/api/trans/vip/translate";
    }

    @Data
    public static class Google {
        private String apiKey;
        private String apiUrl = "https://translation.googleapis.com/language/translate/v2";
    }

    @Data
    public static class MyMemory {
        private String email;
        private String apiUrl = "https://api.mymemory.translated.net/get";
    }

    @Data
    public static class Nlp {
        private Segmenter segmenter = new Segmenter();
        private LanguageDetection languageDetection = new LanguageDetection();
    }

    @Data
    public static class Segmenter {
        private Chinese chinese = new Chinese();
        private English english = new English();
    }

    @Data
    public static class Chinese {
        private boolean enabled = true;
        private String mode = "standard";
        private boolean traditionalChineseSupport = true;
    }

    @Data
    public static class English {
        private boolean enabled = true;
        private String modelType = "simple";
    }

    @Data
    public static class LanguageDetection {
        private boolean enabled = true;
        private double confidenceThreshold = 0.8;
    }

    public String getBaiduLanguageCode(String locale) {
        switch (locale) {
            case "zh-CN": return "zh";
            case "zh-TW": return "cht";
            case "en": return "en";
            default: return "zh";
        }
    }

    public String getGoogleLanguageCode(String locale) {
        switch (locale) {
            case "zh-CN": return "zh-CN";
            case "zh-TW": return "zh-TW";
            case "en": return "en";
            default: return "zh-CN";
        }
    }

    public String getMyMemoryLanguageCode(String locale) {
        switch (locale) {
            case "zh-CN": return "zh-CN";
            case "zh-TW": return "zh-TW";
            case "en": return "en-GB";
            default: return "zh-CN";
        }
    }

    public boolean isLocaleSupported(String locale) {
        return supportedLocales.contains(locale);
    }
}
