package com.foxya.coin.notification.utils;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NotificationMessageLocalizer {

    private static final String BUNDLE_BASE_NAME = "i18n.notifications";
    private static final Locale KOREAN_LOCALE = Locale.KOREAN;
    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-zA-Z0-9_]+)}");
    private static final Pattern DECIMAL_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");

    private NotificationMessageLocalizer() {
    }

    public static Locale resolveLocale(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return DEFAULT_LOCALE;
        }
        String normalizedCountryCode = countryCode.trim().toUpperCase(Locale.ROOT);
        if ("KR".equals(normalizedCountryCode)) {
            return KOREAN_LOCALE;
        }
        return DEFAULT_LOCALE;
    }

    public static String resolve(String messageKey, Locale locale, JsonNode metadata, String fallback) {
        if (messageKey == null || messageKey.isBlank()) {
            return fallback;
        }

        String template = resolveTemplate(messageKey, locale);
        if (template == null || template.isBlank()) {
            return fallback;
        }

        return applyMetadata(template, metadata);
    }

    private static String resolveTemplate(String messageKey, Locale locale) {
        String localized = getMessage(messageKey, locale);
        if (localized != null) {
            return localized;
        }
        if (!DEFAULT_LOCALE.equals(locale)) {
            return getMessage(messageKey, DEFAULT_LOCALE);
        }
        return null;
    }

    private static String getMessage(String messageKey, Locale locale) {
        try {
            ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_BASE_NAME, locale);
            if (bundle.containsKey(messageKey)) {
                return bundle.getString(messageKey);
            }
            return null;
        } catch (MissingResourceException ignored) {
            return null;
        }
    }

    private static String applyMetadata(String template, JsonNode metadata) {
        if (metadata == null || !metadata.isObject()) {
            return template;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuffer resolved = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            JsonNode valueNode = metadata.get(key);
            String replacement = "";
            if (valueNode != null && !valueNode.isNull()) {
                replacement = valueNode.isTextual() ? valueNode.asText() : valueNode.toString();
                replacement = formatNotificationValue(key, replacement);
            }
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    private static String formatNotificationValue(String key, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (!isAmountLikeKey(key) || !DECIMAL_PATTERN.matcher(value.trim()).matches()) {
            return value;
        }

        BigDecimal decimal = new BigDecimal(value.trim()).setScale(6, RoundingMode.DOWN).stripTrailingZeros();
        if (decimal.scale() < 0) {
            decimal = decimal.setScale(0);
        }
        return decimal.toPlainString();
    }

    private static boolean isAmountLikeKey(String key) {
        return "amount".equals(key)
            || "fromAmount".equals(key)
            || "toAmount".equals(key)
            || "fee".equals(key)
            || "paymentAmount".equals(key);
    }
}
