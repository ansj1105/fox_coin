package com.foxya.coin.notice;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.notice.entities.Notice;
import com.foxya.coin.utils.QueryBuilder;
import com.foxya.coin.utils.BaseQueryBuilder.Sort;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlClient;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class NoticeRepository extends BaseRepository {
    
    private static final RowMapper<Notice> NOTICE_MAPPER = row -> Notice.builder()
        .id(row.getLong("id"))
        .title(row.getString("title"))
        .content(row.getString("content"))
        .isImportant(row.getBoolean("is_important"))
        .createdBy(row.getLong("created_by"))
        .createdAt(row.getLocalDateTime("created_at"))
        .updatedAt(row.getLocalDateTime("updated_at"))
        .build();
    
    public Future<List<Notice>> getNotices(SqlClient client, Integer limit, Integer offset) {
        String sql = QueryBuilder
            .select("notices", "id", "title", "content", "is_important", "created_by", "created_at", "updated_at")
            .orderBy("is_important", Sort.DESC)
            .appendQueryString(", created_at DESC")
            .limitRefactoring()
            .offsetRefactoring()
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("limit", limit);
        params.put("offset", offset);
        
        return query(client, sql, params)
            .map(rows -> {
                List<Notice> notices = new ArrayList<>();
                for (Row row : rows) {
                    notices.add(NOTICE_MAPPER.map(row));
                }
                return notices;
            });
    }
    
    public Future<Long> getNoticeCount(SqlClient client) {
        String sql = QueryBuilder
            .count("notices")
            .build();
        
        return query(client, sql)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return rows.iterator().next().getLong("count");
                }
                return 0L;
            });
    }
    
    public Future<Notice> getNoticeById(SqlClient client, Long id) {
        String sql = QueryBuilder
            .select("notices", "id", "title", "content", "is_important", "created_by", "created_at", "updated_at")
            .whereById()
            .build();
        
        return query(client, sql, Collections.singletonMap("id", id))
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return NOTICE_MAPPER.map(rows.iterator().next());
                }
                return null;
            });
    }
}

