package com.foxya.coin.agency;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.agency.entities.AgencyMembership;
import com.foxya.coin.utils.QueryBuilder;
import com.foxya.coin.utils.BaseQueryBuilder.Op;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlClient;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class AgencyRepository extends BaseRepository {
    
    private final RowMapper<AgencyMembership> agencyMapper = row -> AgencyMembership.builder()
        .id(getLongColumnValue(row, "id"))
        .userId(getLongColumnValue(row, "user_id"))
        .agencyId(getStringColumnValue(row, "agency_id"))
        .agencyName(getStringColumnValue(row, "agency_name"))
        .joinedAt(getLocalDateTimeColumnValue(row, "joined_at"))
        .createdAt(getLocalDateTimeColumnValue(row, "created_at"))
        .updatedAt(getLocalDateTimeColumnValue(row, "updated_at"))
        .build();
    
    public Future<Boolean> hasAgencyMembership(SqlClient client, Long userId) {
        String sql = QueryBuilder
            .count("agency_memberships")
            .where("user_id", Op.Equal, "userId")
            .build();
        
        return query(client, sql, Collections.singletonMap("userId", userId))
            .map(rows -> {
                Integer count = fetchOne(COUNT_MAPPER, rows);
                return count != null && count > 0;
            });
    }
    
    public Future<AgencyMembership> getAgencyMembership(SqlClient client, Long userId) {
        String sql = QueryBuilder
            .select("agency_memberships", "id", "user_id", "agency_id", "agency_name", "joined_at", "created_at", "updated_at")
            .where("user_id", Op.Equal, "userId")
            .build();
        
        return query(client, sql, Collections.singletonMap("userId", userId))
            .map(rows -> fetchOne(agencyMapper, rows))
            .onFailure(e -> log.error("에이전시 멤버십 조회 실패 - userId: {}", userId));
    }
}

