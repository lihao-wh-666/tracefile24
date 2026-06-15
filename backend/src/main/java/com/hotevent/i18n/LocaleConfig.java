package com.hotevent.i18n;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.util.List;
import java.util.Locale;

@Slf4j
@Configuration
public class LocaleConfig implements WebMvcConfigurer {

    @Autowired
    private I18nProperties i18nProperties;

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver() {
            @Override
            public Locale resolveLocale(jakarta.servlet.http.HttpServletRequest request) {
                String lang = request.getParameter("lang");
                if (lang != null && !lang.isEmpty()) {
                    try {
                        return parseLocale(lang);
                    } catch (Exception e) {
                        log.debug("Invalid lang parameter: {}", lang);
                    }
                }

                String acceptLanguage = request.getHeader("Accept-Language");
                if (acceptLanguage != null && !acceptLanguage.isEmpty()) {
                    try {
                        List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(acceptLanguage);
                        for (Locale.LanguageRange range : ranges) {
                            Locale locale = Locale.forLanguageTag(range.getRange());
                            if (isSupportedLocale(locale)) {
                                return locale;
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Failed to parse Accept-Language: {}", acceptLanguage);
                    }
                }

                return getDefaultLocale();
            }
        };
        resolver.setDefaultLocale(getDefaultLocale());
        return resolver;
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }

    private Locale parseLocale(String lang) {
        switch (lang) {
            case "zh-CN":
                return Locale.SIMPLIFIED_CHINESE;
            case "zh-TW":
                return Locale.TRADITIONAL_CHINESE;
            case "en":
                return Locale.ENGLISH;
            default:
                return Locale.forLanguageTag(lang.replace("_", "-"));
        }
    }

    private Locale getDefaultLocale() {
        return parseLocale(i18nProperties.getDefaultLocale());
    }

    private boolean isSupportedLocale(Locale locale) {
        String tag = locale.toLanguageTag();
        return i18nProperties.getSupportedLocales().contains(tag);
    }

    public static String localeToLanguageTag(Locale locale) {
        String tag = locale.toLanguageTag();
        switch (tag) {
            case "zh-CN":
            case "zh-Hans":
                return "zh-CN";
            case "zh-TW":
            case "zh-Hant":
                return "zh-TW";
            case "en":
            case "en-US":
                return "en";
            default:
                return tag;
        }
    }
}
