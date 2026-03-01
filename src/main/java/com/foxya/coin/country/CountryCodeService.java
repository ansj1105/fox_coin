package com.foxya.coin.country;

import com.foxya.coin.auth.dto.CountryOptionResponseDto;
import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.utils.CountryCodeUtils;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class CountryCodeService extends BaseService {

    private static final String SYNC_JOB_NAME = "signup_country_codes";
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

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

        List<CountryCodeUtils.CountrySeed> seeds = CountryCodeUtils.buildCountrySeeds();
        countryCodeRepository.upsertCountryCodes(pool, seeds)
            .compose(v -> countryCodeRepository.recordSyncStatus(pool, SYNC_JOB_NAME, "SUCCESS", seeds.size(), null))
            .onSuccess(v -> {
                log.info("국가코드 동기화 완료: {}건", seeds.size());
                promise.complete(seeds.size());
            })
            .onFailure(throwable -> {
                String message = truncate(throwable.getMessage());
                countryCodeRepository.recordSyncStatus(pool, SYNC_JOB_NAME, "FAILED", 0, message)
                    .onComplete(ignored -> promise.fail(throwable));
            })
            .onComplete(ar -> syncInFlight.set(null));

        return created;
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
