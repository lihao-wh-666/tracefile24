package com.hotevent.i18n.translation.impl;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hotevent.i18n.I18nProperties;
import com.hotevent.i18n.translation.TranslationProvider;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BaiduTranslationProvider implements TranslationProvider {

    private final I18nProperties.Baidu baiduConfig;
    private final I18nProperties i18nProperties;

    public BaiduTranslationProvider(I18nProperties.Baidu baiduConfig, I18nProperties i18nProperties) {
        this.baiduConfig = baiduConfig;
        this.i18nProperties = i18nProperties;
    }

    @Override
    public String translate(String text, String sourceLang, String targetLang) {
        if (text == null || text.trim().isEmpty()) return text;

        String from = i18nProperties.getBaiduLanguageCode(sourceLang);
        String to = i18nProperties.getBaiduLanguageCode(targetLang);

        String salt = String.valueOf(System.currentTimeMillis());
        String sign = SecureUtil.md5(baiduConfig.getAppId() + text + salt + baiduConfig.getSecretKey());

        try {
            HttpResponse response = HttpRequest.get(baiduConfig.getApiUrl())
                    .form("q", text)
                    .form("from", from)
                    .form("to", to)
                    .form("appid", baiduConfig.getAppId())
                    .form("salt", salt)
                    .form("sign", sign)
                    .timeout(10000)
                    .execute();

            if (response.isOk()) {
                JSONObject json = JSONUtil.parseObj(response.body());
                if (json.containsKey("trans_result")) {
                    JSONArray results = json.getJSONArray("trans_result");
                    if (results != null && !results.isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < results.size(); i++) {
                            JSONObject item = results.getJSONObject(i);
                            if (i > 0) sb.append("\n");
                            sb.append(item.getStr("dst"));
                        }
                        return sb.toString();
                    }
                }
                if (json.containsKey("error_code")) {
                    log.error("[BaiduTranslation] API error: code={}, msg={}",
                            json.getStr("error_code"), json.getStr("error_msg"));
                }
            }
        } catch (Exception e) {
            log.error("[BaiduTranslation] Request failed: {}", e.getMessage());
        }

        return text;
    }

    @Override
    public String getProviderName() {
        return "baidu";
    }

    @Override
    public boolean isAvailable() {
        return baiduConfig.getAppId() != null && !baiduConfig.getAppId().isEmpty() &&
                baiduConfig.getSecretKey() != null && !baiduConfig.getSecretKey().isEmpty();
    }
}
