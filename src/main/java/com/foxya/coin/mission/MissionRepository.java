package com.foxya.coin.mission;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.mission.entities.Mission;
import com.foxya.coin.mission.entities.UserMission;
import com.foxya.coin.mission.enums.MissionType;
import com.foxya.coin.utils.QueryBuilder;
import com.foxya.coin.utils.BaseQueryBuilder.Sort;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlClient;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class MissionRepository extends BaseRepository {
    
    private final RowMapper<Mission> missionMapper = row -> Mission.builder()
        .id(getLongColumnValue(row, "id"))
        .title(getStringColumnValue(row, "title"))
        .description(getStringColumnValue(row, "description"))
        .type(MissionType.valueOf(getStringColumnValue(row, "type")))
        .requiredCount(getIntegerColumnValue(row, "required_count"))
        .isActive(getBooleanColumnValue(row, "is_active"))
        .createdAt(getLocalDateTimeColumnValue(row, "created_at"))
        .updatedAt(getLocalDateTimeColumnValue(row, "updated_at"))
        .build();
    
    private final RowMapper<UserMission> userMissionMapper = row -> {
        LocalDate missionDate = null;
        if (!checkColumn(row, "mission_date") && row.getLocalDate("mission_date") != null) {
            missionDate = row.getLocalDate("mission_date");
        }
        
        return UserMission.builder()
            .id(getLongColumnValue(row, "id"))
            .userId(getLongColumnValue(row, "user_id"))
            .missionId(getLongColumnValue(row, "mission_id"))
            .missionDate(missionDate)
            .currentCount(getIntegerColumnValue(row, "current_count"))
            .resetAt(getLocalDateTimeColumnValue(row, "reset_at"))
            .createdAt(getLocalDateTimeColumnValue(row, "created_at"))
            .updatedAt(getLocalDateTimeColumnValue(row, "updated_at"))
            .build();
    };
    
    /**
     * 활성화된 모든 미션 조회
     */
    public Future<List<Mission>> getActiveMissions(SqlClient client) {
        String sql = QueryBuilder
            .select("missions", "id", "title", "description", "type", "required_count", "is_active", "created_at", "updated_at")
            .where("is_active", com.foxya.coin.utils.BaseQueryBuilder.Op.Equal, "is_active")
            .orderBy("id", Sort.ASC)
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("is_active", true);
        
        return query(client, sql, params)
            .map(rows -> {
                List<Mission> missions = new ArrayList<>();
                for (Row row : rows) {
                    missions.add(missionMapper.map(row));
                }
                return missions;
            });
    }
    
    /**
     * 사용자의 특정 날짜 미션 진행 상황 조회
     */
    public Future<UserMission> getUserMission(SqlClient client, Long userId, Long missionId, LocalDate date) {
        String sql = QueryBuilder
            .select("user_missions", "id", "user_id", "mission_id", "mission_date", "current_count", "reset_at", "created_at", "updated_at")
            .where("user_id", com.foxya.coin.utils.BaseQueryBuilder.Op.Equal, "user_id")
            .andWhere("mission_id", com.foxya.coin.utils.BaseQueryBuilder.Op.Equal, "mission_id")
            .andWhere("mission_date", com.foxya.coin.utils.BaseQueryBuilder.Op.Equal, "mission_date")
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("mission_id", missionId);
        params.put("mission_date", date);
        
        return query(client, sql, params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return userMissionMapper.map(rows.iterator().next());
                }
                return null;
            });
    }
    
    /**
     * 사용자의 오늘 미션 진행 상황 조회 (모든 미션)
     */
    public Future<List<UserMission>> getUserMissionsForToday(SqlClient client, Long userId, LocalDate today) {
        String sql = QueryBuilder
            .select("user_missions", "id", "user_id", "mission_id", "mission_date", "current_count", "reset_at", "created_at", "updated_at")
            .where("user_id", com.foxya.coin.utils.BaseQueryBuilder.Op.Equal, "user_id")
            .andWhere("mission_date", com.foxya.coin.utils.BaseQueryBuilder.Op.Equal, "mission_date")
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("mission_date", today);
        
        return query(client, sql, params)
            .map(rows -> {
                List<UserMission> userMissions = new ArrayList<>();
                for (Row row : rows) {
                    userMissions.add(userMissionMapper.map(row));
                }
                return userMissions;
            });
    }
    
    /**
     * 사용자 미션 생성 또는 업데이트
     */
    public Future<UserMission> createOrUpdateUserMission(SqlClient client, Long userId, Long missionId, 
                                                          LocalDate date, Integer currentCount) {
        LocalDateTime resetAt = LocalDateTime.of(date.plusDays(1), LocalTime.MIDNIGHT);
        
        String sql = QueryBuilder
            .insert("user_missions", "user_id", "mission_id", "mission_date", "current_count", "reset_at")
            .onConflict("user_id, mission_id, mission_date")
            .doUpdateCustom("current_count = EXCLUDED.current_count, reset_at = EXCLUDED.reset_at, updated_at = CURRENT_TIMESTAMP")
            .returning("id, user_id, mission_id, mission_date, current_count, reset_at, created_at, updated_at");
        
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("mission_id", missionId);
        params.put("mission_date", date);
        params.put("current_count", currentCount);
        params.put("reset_at", resetAt);
        
        return query(client, sql, params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return userMissionMapper.map(rows.iterator().next());
                }
                return null;
            });
    }
    
    /**
     * 미션 완료 처리 (current_count 증가)
     */
    public Future<Boolean> incrementMissionCount(SqlClient client, Long userId, Long missionId, LocalDate date) {
        LocalDateTime resetAt = LocalDateTime.of(date.plusDays(1), LocalTime.MIDNIGHT);
        
        String sql = QueryBuilder
            .insert("user_missions", "user_id", "mission_id", "mission_date", "current_count", "reset_at")
            .onConflict("user_id, mission_id, mission_date")
            .doUpdateCustom("current_count = user_missions.current_count + 1, reset_at = EXCLUDED.reset_at, updated_at = CURRENT_TIMESTAMP")
            .returning("id");
        
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("mission_id", missionId);
        params.put("mission_date", date);
        params.put("current_count", 1);
        params.put("reset_at", resetAt);
        
        return query(client, sql, params)
            .map(rows -> rows.iterator().hasNext());
    }
}

