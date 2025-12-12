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
    
    private static final RowMapper<AgencyMembership> AGENCY_MAPPER = row -> AgencyMembership.builder()
        .id(row.getLong("id"))
        .userId(row.getLong("user_id"))
        .agencyId(row.getString("agency_id"))
        .agencyName(row.getString("agency_name"))
        .joinedAt(row.getLocalDateTime("joined_at"))
        .createdAt(row.getLocalDateTime("created_at"))
        .updatedAt(row.getLocalDateTime("updated_at"))
        .build();
    
    public Future<Boolean> hasAgencyMembership(SqlClient client, Long userId) {
        String sql = QueryBuilder
            .count("agency_memberships")
            .where("user_id", Op.Equal, "userId")
            .build();
        
        return query(client, sql, Collections.singletonMap("userId", userId))
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    Long count = rows.iterator().next().getLong("count");
                    return count != null && count > 0;
                }
                return false;
            });
    }
    
    public Future<AgencyMembership> getAgencyMembership(SqlClient client, Long userId) {
        String sql = QueryBuilder
            .select("agency_memberships", "id", "user_id", "agency_id", "agency_name", "joined_at", "created_at", "updated_at")
            .where("user_id", Op.Equal, "userId")
            .build();
        
        return query(client, sql, Collections.singletonMap("userId", userId))
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return AGENCY_MAPPER.map(rows.iterator().next());
                }
                return null;
            });
    }
}

