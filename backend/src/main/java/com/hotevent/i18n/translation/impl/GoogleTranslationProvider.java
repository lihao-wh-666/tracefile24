package com.hotevent.i18n.translation.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hotevent.i18n.I18nProperties;
import com.hotevent.i18n.translation.TranslationProvider;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GoogleTranslationProvider implements TranslationProvider {

    private final I18nProperties.Google googleConfig;
    private final I18nProperties i18nProperties;

    public GoogleTranslationProvider(I18nProperties.Google googleConfig, I18nProperties i18nProperties) {
        this.googleConfig = googleConfig;
        this.i18nProperties = i18nProperties;
    }

    @Override
    public String translate(String text, String sourceLang, String targetLang) {
        if (text == null || text.trim().isEmpty()) return text;

        String source = i18nProperties.getGoogleLanguageCode(sourceLang);
        String target = i18nProperties.getGoogleLanguageCode(targetLang);

        try {
            String url = googleConfig.getApiKey() + "?key=" + googleConfig.getApiKey() +
                    "&q=" + java.net.URLEncoder.encode(text, "UTF-8") +
                    "&source=" + source + "&target=" + target +
                    "&format=text";

            HttpResponse response = HttpRequest.get(googleConfig.getApiUrl())
                    .form("key", googleConfig.getApiKey())
                    .form("q", text)
                    .form("source", source)
                    .form("target", target)
                    .form("format", "text")
                    .timeout(10000)
                    .execute();

            if (response.isOk()) {
                JSONObject json = JSONUtil.parseObj(response.body());
                JSONObject data = json.getJSONObject("data");
                if (data != null) {
                    JSONArray translations = data.getJSONArray("translations");
                    if (translations != null && !translations.isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < translations.size(); i++) {
                            JSONObject item = translations.getJSONObject(i);
                            if (i > 0) sb.append("\n");
                            sb.append(item.getStr("translatedText"));
                        }
                        return sb.toString();
                    }
                }
            }
        } catch (Exception e) {
            log.error("[GoogleTranslation] Request failed: {}", e.getMessage());
        }

        return text;
    }

    @Override
    public String getProviderName() {
        return "google";
    }

    @Override
    public boolean isAvailable() {
        return googleConfig.getApiKey() != null && !googleConfig.getApiKey().isEmpty();
    }
}
