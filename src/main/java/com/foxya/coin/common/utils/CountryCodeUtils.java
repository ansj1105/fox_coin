package com.foxya.coin.common.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public final class CountryCodeUtils {

    private static final String ETC_CODE = "ETC";

    private static final Set<String> ISO_ALPHA2_CODES = Arrays.stream(Locale.getISOCountries())
        .map(code -> code == null ? null : code.trim().toUpperCase(Locale.ROOT))
        .filter(code -> code != null && code.length() == 2)
        .collect(Collectors.toUnmodifiableSet());

    private static final List<String> PRIORITY_CODES = List.of(
        "KR", "US", "JP", "CN", "GB", "FR", "DE", "IT", "ES", "CA",
        "AU", "BR", "IN", "NG", "RU", "MX", "ID", "TH", "VN", "PH",
        "MY", "SG", "TW", "HK", ETC_CODE
    );

    private CountryCodeUtils() {
    }

    public static String normalizeCountryCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    public static boolean isValidSignupCountryCode(String code) {
        String normalized = normalizeCountryCode(code);
        if (normalized == null) {
            return false;
        }
        if (ETC_CODE.equals(normalized)) {
            return true;
        }
        return normalized.length() == 2 && ISO_ALPHA2_CODES.contains(normalized);
    }

    public static String resolveFlagEmoji(String code) {
        String normalized = normalizeCountryCode(code);
        if (normalized == null || ETC_CODE.equals(normalized)) {
            return "🏳️";
        }
        if (!normalized.matches("^[A-Z]{2}$")) {
            return "🏳️";
        }

        int base = 127397;
        return normalized
            .chars()
            .mapToObj(ch -> String.valueOf(Character.toChars(base + ch)))
            .collect(Collectors.joining());
    }

    public static String resolveCountryName(String code, Locale locale) {
        String normalized = normalizeCountryCode(code);
        if (normalized == null) {
            return null;
        }
        if (ETC_CODE.equals(normalized)) {
            return Locale.KOREAN.getLanguage().equals(locale.getLanguage()) ? "기타" : "Other (ETC)";
        }
        if (!isValidSignupCountryCode(normalized)) {
            return normalized;
        }

        try {
            Locale countryLocale = new Locale("", normalized);
            String name = countryLocale.getDisplayCountry(locale);
            if (name == null || name.isBlank()) {
                return normalized;
            }
            return name;
        } catch (Exception e) {
            log.debug("Failed to resolve country name. code={}, locale={}, reason={}", normalized, locale, e.getMessage());
            return normalized;
        }
    }

    public static List<CountrySeed> buildCountrySeeds() {
        Map<String, Integer> priorityOrder = new HashMap<>();
        for (int i = 0; i < PRIORITY_CODES.size(); i++) {
            priorityOrder.put(PRIORITY_CODES.get(i), (i + 1) * 10);
        }

        List<String> isoCodes = new ArrayList<>(ISO_ALPHA2_CODES);
        Collections.sort(isoCodes);

        List<CountrySeed> seeds = new ArrayList<>(isoCodes.size() + 1);
        int fallbackOrder = 1000;

        for (String code : isoCodes) {
            String nameEn = resolveCountryName(code, Locale.ENGLISH);
            String nameKo = resolveCountryName(code, Locale.KOREAN);
            String iso3 = resolveIso3Code(code);
            int sortOrder = priorityOrder.getOrDefault(code, fallbackOrder++);
            seeds.add(new CountrySeed(
                code,
                code,
                iso3,
                nameEn != null ? nameEn : code,
                nameKo,
                resolveFlagEmoji(code),
                sortOrder,
                true,
                "JAVA_LOCALE"
            ));
        }

        seeds.add(new CountrySeed(
            ETC_CODE,
            null,
            null,
            "Other (ETC)",
            "기타",
            "🏳️",
            priorityOrder.getOrDefault(ETC_CODE, 9998),
            true,
            "JAVA_LOCALE"
        ));

        seeds.sort((a, b) -> {
            int bySort = Integer.compare(a.sortOrder(), b.sortOrder());
            if (bySort != 0) {
                return bySort;
            }
            return a.code().compareTo(b.code());
        });

        return seeds;
    }

    private static String resolveIso3Code(String code) {
        try {
            return new Locale("", code).getISO3Country();
        } catch (MissingResourceException ignored) {
            return null;
        }
    }

    public record CountrySeed(
        String code,
        String iso2Code,
        String iso3Code,
        String nameEn,
        String nameKo,
        String flag,
        int sortOrder,
        boolean active,
        String source
    ) {
    }
}
