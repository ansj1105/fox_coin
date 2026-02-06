package com.foxya.coin.notice;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.notice.entities.Notice;
import com.foxya.coin.utils.QueryBuilder;
import com.foxya.coin.utils.BaseQueryBuilder.Op;
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
    
    private final RowMapper<Notice> NOTICE_MAPPER = row -> Notice.builder()
        .id(row.getLong("id"))
        .title(row.getString("title"))
        .content(row.getString("content"))
        .isImportant(row.getBoolean("is_important"))
        .isEvent(getBooleanColumnValue(row, "is_event"))
        .createdBy(row.getLong("created_by"))
        .createdAt(row.getLocalDateTime("created_at"))
        .updatedAt(row.getLocalDateTime("updated_at"))
        .build();

    public Future<List<Notice>> getNotices(SqlClient client, Integer limit, Integer offset, Boolean isEvent) {
        var selectBuilder = QueryBuilder
            .select("notices", "id", "title", "content", "is_important", "is_event", "created_by", "created_at", "updated_at")
            .orderBy("is_important", Sort.DESC)
            .appendQueryString(", created_at DESC")
            .limitRefactoring()
            .offsetRefactoring();
        if (isEvent != null) {
            selectBuilder = selectBuilder.where("is_event", Op.Equal, "is_event");
        }
        String sql = selectBuilder.build();

        Map<String, Object> params = new HashMap<>();
        params.put("limit", limit);
        params.put("offset", offset);
        if (isEvent != null) {
            params.put("is_event", isEvent);
        }

        return query(client, sql, params)
            .map(rows -> {
                List<Notice> notices = new ArrayList<>();
                for (Row row : rows) {
                    notices.add(NOTICE_MAPPER.map(row));
                }
                return notices;
            });
    }

    public Future<Long> getNoticeCount(SqlClient client, Boolean isEvent) {
        var countBuilder = QueryBuilder.count("notices");
        if (isEvent != null) {
            countBuilder = countBuilder.where("is_event", Op.Equal, "is_event");
        }
        String sql = countBuilder.build();

        Map<String, Object> params = new HashMap<>();
        if (isEvent != null) {
            params.put("is_event", isEvent);
        }

        return query(client, sql, params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return rows.iterator().next().getLong("count");
                }
                return 0L;
            });
    }
    
    public Future<Notice> getNoticeById(SqlClient client, Long id) {
        String sql = QueryBuilder
            .select("notices", "id", "title", "content", "is_important", "is_event", "created_by", "created_at", "updated_at")
            .where("id", Op.Equal, "id")
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

