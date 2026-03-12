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
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
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
        params.put("updated_at", LocalDateTime.now());

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
        params.put("updated_at", LocalDateTime.now());
        return query(client, sql, params).mapEmpty();
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
