package com.foxya.coin.currency;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.currency.dto.CoinPriceDto;
import com.foxya.coin.currency.dto.CoinPricesDto;
import com.foxya.coin.utils.QueryBuilder;
import com.foxya.coin.utils.BaseQueryBuilder.Op;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class CoinPriceRepository extends BaseRepository {

    public Future<CoinPricesDto> getCoinPrices(SqlClient client, Set<String> currencyCodes) {
        if (currencyCodes == null || currencyCodes.isEmpty()) {
            String sql = QueryBuilder
                .select("coin_prices", "currency_code", "usd_price", "change_24h_percent", "source", "updated_at")
                .build();
            return query(client, sql).map(this::mapCoinPrices);
        }

        List<Future> futures = new ArrayList<>();
        for (String code : currencyCodes) {
            String sql = QueryBuilder
                .select("coin_prices", "currency_code", "usd_price", "change_24h_percent", "source", "updated_at")
                .where("currency_code", Op.Equal, "currency_code")
                .build();
            futures.add(query(client, sql, Collections.singletonMap("currency_code", code)));
        }

        return CompositeFuture.all(futures)
            .map(cf -> {
                List<Row> rows = new ArrayList<>();
                for (int i = 0; i < cf.size(); i++) {
                    Iterable<Row> result = cf.resultAt(i);
                    for (Row row : result) {
                        rows.add(row);
                    }
                }
                return mapCoinPrices(rows);
            })
            .onFailure(t -> log.warn("Failed to load coin prices from DB: {}", t.getMessage()));
    }

    public Future<Void> upsertCoinPrices(PgPool pool, Map<String, CoinPriceDto> prices) {
        if (prices == null || prices.isEmpty()) {
            return Future.succeededFuture();
        }

        List<Future> futures = new ArrayList<>();
        prices.forEach((code, dto) -> {
            if (code == null || code.isBlank() || dto == null || dto.getUsdPrice() == null) {
                return;
            }
            futures.add(upsertCoinPrice(pool, code, dto));
        });
        return CompositeFuture.all(futures).mapEmpty();
    }

    public Future<Map<String, BigDecimal>> getDailyClosePrices(SqlClient client, Set<String> currencyCodes, LocalDate closeDate) {
        if (currencyCodes == null || currencyCodes.isEmpty() || closeDate == null) {
            return Future.succeededFuture(Collections.emptyMap());
        }

        List<Future> futures = new ArrayList<>();
        for (String code : currencyCodes) {
            String sql = """
                SELECT currency_code, usd_close_price
                FROM coin_price_daily_closes
                WHERE currency_code = $1
                  AND close_date = $2
                """;
            futures.add(client.preparedQuery(sql).execute(Tuple.of(code, closeDate)));
        }

        return CompositeFuture.all(futures)
            .map(cf -> {
                Map<String, BigDecimal> closes = new HashMap<>();
                for (int i = 0; i < cf.size(); i++) {
                    Iterable<Row> rows = cf.resultAt(i);
                    for (Row row : rows) {
                        String code = row.getString("currency_code");
                        BigDecimal closePrice = row.getBigDecimal("usd_close_price");
                        if (code != null && closePrice != null) {
                            closes.put(code, closePrice);
                        }
                    }
                }
                return closes;
            })
            .onFailure(t -> log.warn("Failed to load coin daily close prices: {}", t.getMessage()));
    }

    public Future<Void> upsertDailyClosePrices(PgPool pool, Map<String, CoinPriceDto> prices, LocalDate closeDate) {
        if (prices == null || prices.isEmpty() || closeDate == null) {
            return Future.succeededFuture();
        }

        List<Future> futures = new ArrayList<>();
        prices.forEach((code, dto) -> {
            if (code == null || code.isBlank() || dto == null || dto.getUsdPrice() == null) {
                return;
            }
            futures.add(upsertDailyClosePrice(pool, code, dto, closeDate));
        });
        return CompositeFuture.all(futures).mapEmpty();
    }

    private Future<Void> upsertCoinPrice(SqlClient client, String currencyCode, CoinPriceDto dto) {
        return existsCoinPrice(client, currencyCode)
            .compose(exists -> exists
                ? updateCoinPrice(client, currencyCode, dto)
                : insertCoinPrice(client, currencyCode, dto));
    }

    private Future<Boolean> existsCoinPrice(SqlClient client, String currencyCode) {
        String sql = QueryBuilder
            .count("coin_prices")
            .where("currency_code", Op.Equal, "currency_code")
            .build();

        return query(client, sql, Collections.singletonMap("currency_code", currencyCode))
            .map(rows -> {
                if (!rows.iterator().hasNext()) {
                    return false;
                }
                Long count = getLongColumnValue(rows.iterator().next(), "count");
                return count != null && count > 0;
            });
    }

    private Future<Void> insertCoinPrice(SqlClient client, String currencyCode, CoinPriceDto dto) {
        Map<String, Object> params = new HashMap<>();
        params.put("currency_code", currencyCode);
        params.put("usd_price", dto.getUsdPrice());
        params.put("change_24h_percent", dto.getChange24hPercent());
        params.put("source", dto.getSource());
        params.put("updated_at", OffsetDateTime.now(ZoneOffset.UTC));

        String sql = QueryBuilder.insert("coin_prices", params, null);
        return query(client, sql, params).mapEmpty();
    }

    private Future<Void> updateCoinPrice(SqlClient client, String currencyCode, CoinPriceDto dto) {
        String sql = QueryBuilder
            .update("coin_prices", "usd_price", "change_24h_percent", "source", "updated_at")
            .where("currency_code", Op.Equal, "currency_code")
            .build();

        Map<String, Object> params = new HashMap<>();
        params.put("currency_code", currencyCode);
        params.put("usd_price", dto.getUsdPrice());
        params.put("change_24h_percent", dto.getChange24hPercent());
        params.put("source", dto.getSource());
        params.put("updated_at", OffsetDateTime.now(ZoneOffset.UTC));
        return query(client, sql, params).mapEmpty();
    }

    private Future<Void> upsertDailyClosePrice(SqlClient client, String currencyCode, CoinPriceDto dto, LocalDate closeDate) {
        OffsetDateTime sourceUpdatedAt = dto.getUpdatedAt() != null
            ? dto.getUpdatedAt().atOffset(ZoneOffset.UTC)
            : null;
        String sql = """
            INSERT INTO coin_price_daily_closes (currency_code, close_date, usd_close_price, source_price_updated_at, updated_at)
            VALUES ($1, $2, $3, $4, now())
            ON CONFLICT (currency_code, close_date) DO UPDATE
            SET usd_close_price = EXCLUDED.usd_close_price,
                source_price_updated_at = EXCLUDED.source_price_updated_at,
                updated_at = now()
            """;
        return client.preparedQuery(sql)
            .execute(Tuple.of(currencyCode, closeDate, dto.getUsdPrice(), sourceUpdatedAt))
            .mapEmpty();
    }

    private CoinPricesDto mapCoinPrices(Iterable<Row> rows) {
        Map<String, CoinPriceDto> prices = new HashMap<>();
        Instant updatedAt = null;

        for (Row row : rows) {
            String code = row.getString("currency_code");
            BigDecimal usdPrice = row.getBigDecimal("usd_price");
            if (code == null || usdPrice == null) {
                continue;
            }

            Instant rowUpdatedAt = toInstant(row, "updated_at");
            prices.put(code, CoinPriceDto.builder()
                .usdPrice(usdPrice)
                .change24hPercent(row.getBigDecimal("change_24h_percent"))
                .source(row.getString("source"))
                .updatedAt(rowUpdatedAt)
                .build());

            if (rowUpdatedAt != null && (updatedAt == null || rowUpdatedAt.isAfter(updatedAt))) {
                updatedAt = rowUpdatedAt;
            }
        }

        return CoinPricesDto.builder()
            .prices(prices)
            .updatedAt(updatedAt != null ? updatedAt : Instant.now())
            .build();
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
