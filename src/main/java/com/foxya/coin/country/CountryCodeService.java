package com.foxya.coin.country;

import com.foxya.coin.auth.dto.CountryOptionResponseDto;
import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.utils.CountryCodeUtils;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class CountryCodeService extends BaseService {

    private static final String SYNC_JOB_NAME = "signup_country_codes";
    private static final String COUNTRY_CODES_CSV_RESOURCE = "country/country_codes_mofa_20251222.csv";
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    private static final int CSV_COL_ISO2 = 0;
    private static final int CSV_COL_ISO3 = 1;
    private static final int CSV_COL_NAME_EN = 6;
    private static final int CSV_COL_NAME_KO = 7;

    private static final List<String> PRIORITY_CODES = List.of(
        "KR", "US", "JP", "CN", "GB", "FR", "DE", "IT", "ES", "CA",
        "AU", "BR", "IN", "NG", "RU", "MX", "ID", "TH", "VN", "PH",
        "MY", "SG", "TW", "HK", "ETC"
    );

    private final CountryCodeRepository countryCodeRepository;
    private final AtomicReference<Future<Integer>> syncInFlight = new AtomicReference<>(null);

    public CountryCodeService(PgPool pool, CountryCodeRepository countryCodeRepository) {
        super(pool);
        this.countryCodeRepository = countryCodeRepository;
    }

    public Future<List<CountryOptionResponseDto>> getSignupCountries() {
        return countryCodeRepository.listActiveCountryCodes(pool)
            .compose(rows -> {
                if (rows != null && !rows.isEmpty()) {
                    return Future.succeededFuture(toResponse(rows));
                }

                return syncCountryCodesFromLocale()
                    .compose(count -> countryCodeRepository.listActiveCountryCodes(pool))
                    .map(this::toResponse)
                    .recover(throwable -> {
                        log.warn("국가코드 DB 조회/동기화 실패, fallback 목록 사용: {}", throwable.getMessage());
                        return Future.succeededFuture(toFallbackResponse());
                    });
            });
    }

    /**
     * CSV(외교부 국가표준코드) 기반으로 국가코드 마스터를 동기화.
     * CSV 로드 실패 시 Locale 기반 seed로 fallback.
     */
    public Future<Integer> syncCountryCodesFromLocale() {
        Future<Integer> inFlight = syncInFlight.get();
        if (inFlight != null && !inFlight.isComplete()) {
            return inFlight;
        }

        Promise<Integer> promise = Promise.promise();
        Future<Integer> created = promise.future();

        if (!syncInFlight.compareAndSet(inFlight, created)) {
            Future<Integer> raced = syncInFlight.get();
            return raced != null ? raced : Future.succeededFuture(0);
        }

        List<CountryCodeUtils.CountrySeed> seeds = loadCountrySeedsFromCsv();
        if (seeds.isEmpty()) {
            log.warn("CSV 국가코드 로드 결과가 비어 Locale seed로 fallback 합니다.");
            seeds = CountryCodeUtils.buildCountrySeeds();
        }

        List<CountryCodeUtils.CountrySeed> finalSeeds = seeds;
        countryCodeRepository.upsertCountryCodes(pool, finalSeeds)
            .compose(v -> countryCodeRepository.recordSyncStatus(pool, SYNC_JOB_NAME, "SUCCESS", finalSeeds.size(), null))
            .onSuccess(v -> {
                log.info("국가코드 동기화 완료: {}건", finalSeeds.size());
                promise.complete(finalSeeds.size());
            })
            .onFailure(throwable -> {
                String message = truncate(throwable.getMessage());
                countryCodeRepository.recordSyncStatus(pool, SYNC_JOB_NAME, "FAILED", 0, message)
                    .onComplete(ignored -> promise.fail(throwable));
            })
            .onComplete(ar -> syncInFlight.set(null));

        return created;
    }

    private List<CountryCodeUtils.CountrySeed> loadCountrySeedsFromCsv() {
        InputStream inputStream = Thread.currentThread().getContextClassLoader()
            .getResourceAsStream(COUNTRY_CODES_CSV_RESOURCE);
        if (inputStream == null) {
            log.warn("국가코드 CSV 리소스를 찾을 수 없습니다: {}", COUNTRY_CODES_CSV_RESOURCE);
            return List.of();
        }

        Map<String, Integer> priorityOrder = buildPriorityOrderMap();
        Map<String, CountryCodeUtils.CountrySeed> seedMap = new LinkedHashMap<>();
        int fallbackOrder = 1000;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            boolean isHeader = true;
            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                if (line.isBlank()) {
                    continue;
                }

                List<String> columns = parseCsvLine(line);
                if (columns.size() <= CSV_COL_NAME_KO) {
                    continue;
                }

                String iso2 = CountryCodeUtils.normalizeCountryCode(stripBom(columns.get(CSV_COL_ISO2)));
                if (!CountryCodeUtils.isValidSignupCountryCode(iso2) || "ETC".equals(iso2)) {
                    continue;
                }

                if (seedMap.containsKey(iso2)) {
                    continue;
                }

                String iso3 = normalizeIso3(columns.get(CSV_COL_ISO3));
                String nameEn = trimToNull(columns.get(CSV_COL_NAME_EN));
                String nameKo = trimToNull(columns.get(CSV_COL_NAME_KO));
                int sortOrder = priorityOrder.getOrDefault(iso2, fallbackOrder++);

                seedMap.put(iso2, new CountryCodeUtils.CountrySeed(
                    iso2,
                    iso2,
                    iso3,
                    nameEn != null ? nameEn : iso2,
                    nameKo,
                    CountryCodeUtils.resolveFlagEmoji(iso2),
                    sortOrder,
                    true,
                    "MOFA_CSV"
                ));
            }
        } catch (IOException e) {
            log.warn("국가코드 CSV 로드 실패: {}", e.getMessage(), e);
            return List.of();
        }

        seedMap.put("ETC", new CountryCodeUtils.CountrySeed(
            "ETC",
            null,
            null,
            "Other (ETC)",
            "기타",
            "🏳️",
            priorityOrder.getOrDefault("ETC", 9998),
            true,
            "MOFA_CSV"
        ));

        List<CountryCodeUtils.CountrySeed> seeds = new ArrayList<>(seedMap.values());
        seeds.sort((a, b) -> {
            int bySort = Integer.compare(a.sortOrder(), b.sortOrder());
            if (bySort != 0) {
                return bySort;
            }
            return a.code().compareTo(b.code());
        });

        return seeds;
    }

    private Map<String, Integer> buildPriorityOrderMap() {
        Map<String, Integer> priority = new LinkedHashMap<>();
        for (int i = 0; i < PRIORITY_CODES.size(); i++) {
            priority.put(PRIORITY_CODES.get(i), (i + 1) * 10);
        }
        return priority;
    }

    private String stripBom(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (value.charAt(0) == '\uFEFF') {
            return value.substring(1);
        }
        return value;
    }

    private String normalizeIso3(String iso3Code) {
        if (iso3Code == null) {
            return null;
        }
        String normalized = iso3Code.trim().toUpperCase(Locale.ROOT);
        return normalized.length() == 3 ? normalized : null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<String> parseCsvLine(String line) {
        List<String> columns = new ArrayList<>();
        if (line == null) {
            return columns;
        }

        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }

            if (ch == ',' && !inQuotes) {
                columns.add(current.toString());
                current.setLength(0);
                continue;
            }

            current.append(ch);
        }
        columns.add(current.toString());
        return columns;
    }

    private List<CountryOptionResponseDto> toResponse(List<CountryCodeRepository.CountryCodeRow> rows) {
        List<CountryOptionResponseDto> result = new ArrayList<>();
        for (CountryCodeRepository.CountryCodeRow row : rows) {
            String code = row.getCode();
            String name = pickDisplayName(code, row.getNameKo(), row.getNameEn());
            result.add(CountryOptionResponseDto.builder()
                .code(code)
                .name(name)
                .flag(row.getFlag() != null ? row.getFlag() : CountryCodeUtils.resolveFlagEmoji(code))
                .build());
        }
        return result;
    }

    private List<CountryOptionResponseDto> toFallbackResponse() {
        List<CountryOptionResponseDto> result = new ArrayList<>();
        for (CountryCodeUtils.CountrySeed seed : CountryCodeUtils.buildCountrySeeds()) {
            result.add(CountryOptionResponseDto.builder()
                .code(seed.code())
                .name(pickDisplayName(seed.code(), seed.nameKo(), seed.nameEn()))
                .flag(seed.flag())
                .build());
        }
        return result;
    }

    private String pickDisplayName(String code, String nameKo, String nameEn) {
        if (nameKo != null && !nameKo.isBlank()) {
            return nameKo;
        }
        if (nameEn != null && !nameEn.isBlank()) {
            return nameEn;
        }
        String fallback = CountryCodeUtils.resolveCountryName(code, Locale.KOREAN);
        return (fallback == null || fallback.isBlank()) ? code : fallback;
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        if (message.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
