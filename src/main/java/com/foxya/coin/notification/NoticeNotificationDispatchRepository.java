package com.foxya.coin.notification;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.notification.enums.NotificationType;
import com.foxya.coin.utils.QueryBuilder;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlClient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class NoticeNotificationDispatchRepository extends BaseRepository {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NoticeDispatchJob {
        private Long noticeId;
        private String title;
        private String content;
        private Long lastUserId;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NoticeDispatchChunkResult {
        private Long nextCursor;
        private Integer scannedUserCount;
        private Integer insertedCount;
    }

    public Future<Void> ensureJobsForImportantNotices(SqlClient client) {
        String sql = """
            INSERT INTO notice_notification_jobs (notice_id, last_user_id, last_scanned_at, created_at, updated_at)
            SELECT n.id, 0, NULL, NOW(), NOW()
            FROM notices n
            WHERE n.is_important = true
              AND COALESCE(n.is_event, false) = false
            ON CONFLICT (notice_id) DO NOTHING
            """;

        return query(client, sql)
            .<Void>mapEmpty()
            .onFailure(throwable -> log.error("중요 공지 배치 잡 생성 실패", throwable));
    }

    public Future<List<NoticeDispatchJob>> fetchImportantNoticeJobs(SqlClient client, int limit) {
        String sql = """
            SELECT
                j.notice_id,
                n.title,
                n.content,
                j.last_user_id
            FROM notice_notification_jobs j
            JOIN notices n ON n.id = j.notice_id
            WHERE n.is_important = true
              AND COALESCE(n.is_event, false) = false
            ORDER BY j.notice_id ASC
            LIMIT #{limit}
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("limit", Math.max(1, Math.min(limit, 50)));

        return query(client, QueryBuilder.selectStringQuery(sql).build(), params)
            .map(rows -> {
                List<NoticeDispatchJob> jobs = new ArrayList<>();
                for (Row row : rows) {
                    jobs.add(NoticeDispatchJob.builder()
                        .noticeId(getLongColumnValue(row, "notice_id"))
                        .title(getStringColumnValue(row, "title"))
                        .content(getStringColumnValue(row, "content"))
                        .lastUserId(getLongColumnValue(row, "last_user_id"))
                        .build());
                }
                return jobs;
            });
    }

    public Future<NoticeDispatchChunkResult> dispatchChunk(SqlClient client, NoticeDispatchJob job, int userBatchSize) {
        String sql = """
            WITH target_users AS (
                SELECT u.id
                FROM users u
                WHERE u.deleted_at IS NULL
                  AND u.id > #{after_user_id}
                ORDER BY u.id ASC
                LIMIT #{user_batch_size}
            ),
            ins AS (
                INSERT INTO notifications (user_id, type, title, message, is_read, related_id, metadata, created_at, updated_at)
                SELECT
                    tu.id,
                    #{type},
                    #{title},
                    #{message},
                    false,
                    #{notice_id},
                    CAST(#{metadata} AS jsonb),
                    NOW(),
                    NOW()
                FROM target_users tu
                ON CONFLICT (user_id, type, related_id) WHERE deleted_at IS NULL DO NOTHING
                RETURNING 1
            )
            SELECT
                COALESCE((SELECT MAX(id) FROM target_users), #{after_user_id}) AS next_cursor,
                COALESCE((SELECT COUNT(*) FROM target_users), 0) AS scanned_user_count,
                COALESCE((SELECT COUNT(*) FROM ins), 0) AS inserted_count
            """;

        long afterUserId = job.getLastUserId() != null ? job.getLastUserId() : 0L;
        Map<String, Object> params = new HashMap<>();
        params.put("after_user_id", afterUserId);
        params.put("user_batch_size", Math.max(1, Math.min(userBatchSize, 5000)));
        params.put("type", NotificationType.NOTICE.name());
        params.put("title", job.getTitle());
        params.put("message", job.getContent());
        params.put("notice_id", job.getNoticeId());
        params.put("metadata", "{\"noticeId\":" + job.getNoticeId() + ",\"important\":true,\"source\":\"important-notice-scheduler\"}");

        return query(client, QueryBuilder.selectStringQuery(sql).build(), params)
            .map(rows -> {
                Row row = rows.iterator().next();
                Long nextCursor = getLongColumnValue(row, "next_cursor");
                Long scannedLong = getLongColumnValue(row, "scanned_user_count");
                Long insertedLong = getLongColumnValue(row, "inserted_count");
                return NoticeDispatchChunkResult.builder()
                    .nextCursor(nextCursor == null ? afterUserId : nextCursor)
                    .scannedUserCount(scannedLong == null ? 0 : scannedLong.intValue())
                    .insertedCount(insertedLong == null ? 0 : insertedLong.intValue())
                    .build();
            });
    }

    public Future<Void> updateCursor(SqlClient client, Long noticeId, Long nextCursor) {
        String sql = """
            UPDATE notice_notification_jobs
            SET
                last_user_id = GREATEST(COALESCE(last_user_id, 0), #{next_cursor}),
                last_scanned_at = NOW(),
                updated_at = NOW()
            WHERE notice_id = #{notice_id}
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("notice_id", noticeId);
        params.put("next_cursor", nextCursor == null ? 0L : nextCursor);

        return query(client, QueryBuilder.selectStringQuery(sql).build(), params)
            .<Void>mapEmpty();
    }
}
