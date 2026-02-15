package com.foxya.coin.currency;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.currency.dto.ExchangeRatesDto;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ExchangeRateRepository extends BaseRepository {

    public Future<ExchangeRatesDto> getExchangeRates(SqlClient client) {
        String sql = "SELECT currency_code, krw_rate, updated_at FROM exchange_rates";

        return query(client, sql)
            .map(rows -> {
                Map<String, BigDecimal> rates = new HashMap<>();
                Instant updatedAt = null;

                for (Row row : rows) {
                    String code = row.getString("currency_code");
                    BigDecimal rate = row.getBigDecimal("krw_rate");
                    if (code != null && rate != null) {
                        rates.put(code, rate);
                    }

                    Instant rowUpdatedAt = toInstant(row, "updated_at");
                    if (rowUpdatedAt != null && (updatedAt == null || rowUpdatedAt.isAfter(updatedAt))) {
                        updatedAt = rowUpdatedAt;
                    }
                }

                return ExchangeRatesDto.builder()
                    .rates(rates)
                    .updatedAt(updatedAt != null ? updatedAt : Instant.now())
                    .build();
            })
            .onFailure(t -> log.warn("Failed to load exchange rates from DB: {}", t.getMessage()));
    }

    public Future<Void> upsertRates(PgPool pool, Map<String, BigDecimal> rates, String source) {
        if (rates == null || rates.isEmpty()) {
            return Future.succeededFuture();
        }

        List<Future> futures = new ArrayList<>();
        for (var entry : rates.entrySet()) {
            String code = entry.getKey();
            BigDecimal rate = entry.getValue();
            if (code == null || code.isBlank() || rate == null) continue;
            futures.add(upsertRate(pool, code, rate, source));
        }
        return CompositeFuture.all(futures).mapEmpty();
    }

    private Future<Void> upsertRate(SqlClient client, String currencyCode, BigDecimal krwRate, String source) {
        String sql = """
            INSERT INTO exchange_rates (currency_code, krw_rate, source, updated_at)
            VALUES ($1, $2, $3, now())
            ON CONFLICT (currency_code) DO UPDATE
            SET krw_rate = EXCLUDED.krw_rate,
                source = EXCLUDED.source,
                updated_at = now()
            """;
        return client.preparedQuery(sql)
            .execute(Tuple.of(currencyCode, krwRate, source))
            .mapEmpty();
    }

    private static Instant toInstant(Row row, String column) {
        try {
            OffsetDateTime odt = row.getOffsetDateTime(column);
            if (odt != null) return odt.toInstant();
        } catch (Exception ignored) {
        }
        try {
            LocalDateTime ldt = row.getLocalDateTime(column);
            if (ldt != null) return ldt.toInstant(java.time.ZoneOffset.UTC);
        } catch (Exception ignored) {
        }
        return null;
    }
}

