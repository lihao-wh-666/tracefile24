package com.hotevent.i18n.translation;

public interface TranslationProvider {

    String translate(String text, String sourceLang, String targetLang);

    String getProviderName();

    boolean isAvailable();
}
