package com.aicodereview.config;

import com.intellij.openapi.application.ApplicationManager;

/**
 * Lightweight two-language switch. The language is a plugin setting so the
 * settings page and built-in prompt can follow it independently of the IDE UI.
 */
public final class I18n {

    public static final String ZH = "zh";
    public static final String EN = "en";

    private I18n() {
    }

    public static boolean isEnglish() {
        if (ApplicationManager.getApplication() == null) {
            return false;
        }
        AiReviewSettings settings = ApplicationManager.getApplication().getService(AiReviewSettings.class);
        return settings != null && EN.equalsIgnoreCase(settings.getState().uiLanguage);
    }

    public static String t(String zh, String en) {
        return isEnglish() ? en : zh;
    }

    public static String languageCode() {
        return isEnglish() ? EN : ZH;
    }

    public static String languageName() {
        return isEnglish() ? "English" : "中文";
    }
}
