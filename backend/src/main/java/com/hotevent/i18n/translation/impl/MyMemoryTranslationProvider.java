package com.hotevent.i18n.translation.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hotevent.i18n.I18nProperties;
import com.hotevent.i18n.translation.TranslationProvider;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MyMemoryTranslationProvider implements TranslationProvider {

    private final I18nProperties.MyMemory myMemoryConfig;
    private final I18nProperties i18nProperties;
    private final OkHttpClient httpClient;

    public MyMemoryTranslationProvider(I18nProperties.MyMemory myMemoryConfig, I18nProperties i18nProperties) {
        this.myMemoryConfig = myMemoryConfig;
        this.i18nProperties = i18nProperties;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String translate(String text, String sourceLang, String targetLang) {
        if (text == null || text.trim().isEmpty()) return null;

        String source = i18nProperties.getMyMemoryLanguageCode(sourceLang);
        String target = i18nProperties.getMyMemoryLanguageCode(targetLang);

        String langPair = source + "|" + target;

        try {
            StringBuilder urlBuilder = new StringBuilder(myMemoryConfig.getApiUrl());
            urlBuilder.append("?q=").append(URLEncoder.encode(text, StandardCharsets.UTF_8.name()));
            urlBuilder.append("&langpair=").append(URLEncoder.encode(langPair, StandardCharsets.UTF_8.name()));

            if (myMemoryConfig.getEmail() != null && !myMemoryConfig.getEmail().isEmpty()) {
                urlBuilder.append("&de=").append(URLEncoder.encode(myMemoryConfig.getEmail(), StandardCharsets.UTF_8.name()));
            }

            Request request = new Request.Builder()
                    .url(urlBuilder.toString())
                    .header("Accept", "application/json")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    JSONObject json = JSONUtil.parseObj(body);
                    JSONObject responseData = json.getJSONObject("responseData");
                    if (responseData != null) {
                        String translatedText = responseData.getStr("translatedText");
                        if (translatedText != null && !translatedText.isEmpty() && !translatedText.equals(text)) {
                            return translatedText;
                        }
                    }
                    if (json.containsKey("responseStatus")) {
                        int status = json.getInt("responseStatus");
                        if (status != 200) {
                            String details = json.getStr("responseDetails");
                            log.warn("[MyMemory] Translation API returned status {}: {}", status, details);
                        }
                    }
                } else {
                    log.warn("[MyMemory] HTTP request failed with status: {}", response.code());
                }
            }
        } catch (Exception e) {
            log.error("[MyMemory] Translation request failed: {}", e.getMessage());
        }

        return null;
    }

    @Override
    public String getProviderName() {
        return "mymemory";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
