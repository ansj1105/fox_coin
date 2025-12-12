package com.foxya.coin.banner;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.utils.QueryBuilder;
import com.foxya.coin.utils.BaseQueryBuilder.Op;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlClient;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class BannerRepository extends BaseRepository {
    
    private final RowMapper<Banner> bannerMapper = row -> Banner.builder()
        .id(getLongColumnValue(row, "id"))
        .title(getStringColumnValue(row, "title"))
        .imageUrl(getStringColumnValue(row, "image_url"))
        .linkUrl(getStringColumnValue(row, "link_url"))
        .position(getStringColumnValue(row, "position"))
        .isActive(getBooleanColumnValue(row, "is_active"))
        .startDate(getLocalDateTimeColumnValue(row, "start_date"))
        .endDate(getLocalDateTimeColumnValue(row, "end_date"))
        .clickCount(getLongColumnValue(row, "click_count"))
        .createdAt(getLocalDateTimeColumnValue(row, "created_at"))
        .updatedAt(getLocalDateTimeColumnValue(row, "updated_at"))
        .build();
    
    /**
     * 배너 목록 조회
     */
    public Future<List<Banner>> getBanners(SqlClient client, String position) {
        String sql = """
            SELECT * FROM banners
            WHERE is_active = true
                AND start_date <= #{now}
                AND end_date >= #{now}
                AND (#{position} IS NULL OR position = #{position})
            ORDER BY created_at DESC
            """;
        
        String query = QueryBuilder.selectStringQuery(sql).build();
        Map<String, Object> params = new HashMap<>();
        params.put("now", LocalDateTime.now());
        params.put("position", position);
        
        return query(client, query, params)
            .map(rows -> fetchAll(bannerMapper, rows))
            .onFailure(throwable -> log.error("배너 목록 조회 실패: {}", throwable.getMessage()));
    }
    
    /**
     * 배너 클릭 이벤트 기록
     */
    public Future<Void> recordBannerClick(SqlClient client, Long bannerId, Long userId, String ipAddress, String userAgent) {
        Map<String, Object> clickParams = new HashMap<>();
        clickParams.put("banner_id", bannerId);
        clickParams.put("user_id", userId);
        clickParams.put("ip_address", ipAddress);
        clickParams.put("user_agent", userAgent);
        clickParams.put("clicked_at", LocalDateTime.now());
        
        String insertSql = QueryBuilder.insert("banner_clicks", clickParams, null);
        
        return query(client, insertSql, clickParams)
            .compose(rows -> {
                // 클릭 수 증가
                String updateSql = QueryBuilder
                    .update("banners")
                    .increase("click_count")
                    .whereById()
                    .build();
                return query(client, updateSql, Collections.singletonMap("id", bannerId))
                    .map(updateRows -> null);
            })
            .onFailure(throwable -> log.error("배너 클릭 이벤트 기록 실패: {}", throwable.getMessage()));
    }
    
    /**
     * 배너 존재 여부 확인
     */
    public Future<Boolean> existsBanner(SqlClient client, Long bannerId) {
        String sql = QueryBuilder
            .count("banners")
            .whereById()
            .build();
        
        return query(client, sql, Collections.singletonMap("id", bannerId))
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    Long count = getLongColumnValue(rows.iterator().next(), "count");
                    return count != null && count > 0;
                }
                return false;
            })
            .onFailure(throwable -> log.error("배너 존재 여부 확인 실패: {}", throwable.getMessage()));
    }
}

