package com.foxya.coin.country;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.utils.CountryCodeUtils;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class CountryCodeRepository extends BaseRepository {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CountryCodeRow {
        private String code;
        private String nameEn;
        private String nameKo;
        private String flag;
        private Integer sortOrder;
    }

    public Future<List<CountryCodeRow>> listActiveCountryCodes(SqlClient client) {
        String sql = """
            SELECT code, name_en, name_ko, flag, sort_order
            FROM country_codes
            WHERE is_active = true
            ORDER BY sort_order ASC, code ASC
            """;

        return query(client, sql)
            .map(rows -> {
                List<CountryCodeRow> result = new ArrayList<>();
                for (Row row : rows) {
                    result.add(CountryCodeRow.builder()
                        .code(getStringColumnValue(row, "code"))
                        .nameEn(getStringColumnValue(row, "name_en"))
                        .nameKo(getStringColumnValue(row, "name_ko"))
                        .flag(getStringColumnValue(row, "flag"))
                        .sortOrder(getIntegerColumnValue(row, "sort_order"))
                        .build());
                }
                return result;
            })
            .onFailure(throwable -> log.error("활성 국가코드 목록 조회 실패", throwable));
    }

    public Future<Void> upsertCountryCodes(SqlClient client, List<CountryCodeUtils.CountrySeed> seeds) {
        if (seeds == null || seeds.isEmpty()) {
            return Future.succeededFuture();
        }

        String sql = """
            INSERT INTO country_codes (
                code, iso2_code, iso3_code, name_en, name_ko, flag,
                sort_order, is_active, source, created_at, updated_at
            )
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, NOW(), NOW())
            ON CONFLICT (code) DO UPDATE
            SET
                iso2_code = EXCLUDED.iso2_code,
                iso3_code = EXCLUDED.iso3_code,
                name_en = EXCLUDED.name_en,
                name_ko = EXCLUDED.name_ko,
                flag = EXCLUDED.flag,
                sort_order = EXCLUDED.sort_order,
                is_active = EXCLUDED.is_active,
                source = EXCLUDED.source,
                updated_at = NOW()
            """;

        List<Tuple> tuples = new ArrayList<>(seeds.size());
        for (CountryCodeUtils.CountrySeed seed : seeds) {
            tuples.add(Tuple.of(
                seed.code(),
                seed.iso2Code(),
                seed.iso3Code(),
                seed.nameEn(),
                seed.nameKo(),
                seed.flag(),
                seed.sortOrder(),
                seed.active(),
                seed.source()
            ));
        }

        return client.preparedQuery(sql)
            .executeBatch(tuples)
            .<Void>mapEmpty()
            .onFailure(throwable -> log.error("국가코드 upsert 실패", throwable));
    }

    public Future<Void> recordSyncStatus(SqlClient client,
                                         String jobName,
                                         String status,
                                         Integer totalCount,
                                         String errorMessage) {
        String sql = """
            INSERT INTO country_code_sync_jobs (
                job_name, last_synced_at, last_status, total_count, error_message, created_at, updated_at
            )
            VALUES (
                #{job_name}, NOW(), #{last_status}, #{total_count}, #{error_message}, NOW(), NOW()
            )
            ON CONFLICT (job_name) DO UPDATE
            SET
                last_synced_at = EXCLUDED.last_synced_at,
                last_status = EXCLUDED.last_status,
                total_count = EXCLUDED.total_count,
                error_message = EXCLUDED.error_message,
                updated_at = NOW()
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("job_name", jobName);
        params.put("last_status", status);
        params.put("total_count", totalCount == null ? 0 : totalCount);
        params.put("error_message", errorMessage);

        return query(client, sql, params)
            .<Void>mapEmpty()
            .onFailure(throwable -> log.error("국가코드 sync 상태 기록 실패", throwable));
    }
}
