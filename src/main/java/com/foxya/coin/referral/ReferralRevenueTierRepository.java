package com.foxya.coin.referral;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.referral.entities.ReferralRevenueTier;
import com.foxya.coin.utils.BaseQueryBuilder.Op;
import com.foxya.coin.utils.BaseQueryBuilder.Sort;
import com.foxya.coin.utils.QueryBuilder;
import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ReferralRevenueTierRepository extends BaseRepository {

    private final RowMapper<ReferralRevenueTier> mapper = row -> ReferralRevenueTier.builder()
        .id(getLongColumnValue(row, "id"))
        .minTeamSize(getIntegerColumnValue(row, "min_team_size"))
        .maxTeamSize(getIntegerColumnValue(row, "max_team_size"))
        .revenuePercent(getBigDecimalColumnValue(row, "revenue_percent"))
        .isActive(getBooleanColumnValue(row, "is_active"))
        .sortOrder(getIntegerColumnValue(row, "sort_order"))
        .createdAt(getLocalDateTimeColumnValue(row, "created_at"))
        .updatedAt(getLocalDateTimeColumnValue(row, "updated_at"))
        .build();

    public Future<List<ReferralRevenueTier>> getActiveRevenueTiers(SqlClient client) {
        String sql = QueryBuilder
            .select("referral_revenue_tiers",
                "id", "min_team_size", "max_team_size", "revenue_percent", "is_active", "sort_order", "created_at", "updated_at")
            .where("is_active", Op.Equal, "is_active")
            .orderBy("sort_order", Sort.ASC)
            .build();

        Map<String, Object> params = new HashMap<>();
        params.put("is_active", true);

        return query(client, sql, params)
            .map(rows -> fetchAll(mapper, rows))
            .onFailure(throwable -> log.error("레퍼럴 수익 구간 조회 실패", throwable));
    }
}
