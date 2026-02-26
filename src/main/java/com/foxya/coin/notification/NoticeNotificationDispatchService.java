package com.foxya.coin.notification;

import com.foxya.coin.common.BaseService;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.SqlClient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class NoticeNotificationDispatchService extends BaseService {

    private final NoticeNotificationDispatchRepository noticeDispatchRepository;

    public NoticeNotificationDispatchService(PgPool pool, NoticeNotificationDispatchRepository noticeDispatchRepository) {
        super(pool);
        this.noticeDispatchRepository = noticeDispatchRepository;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DispatchBatchResult {
        private Integer jobCount;
        private Integer scannedUserCount;
        private Integer insertedNotificationCount;
    }

    public Future<DispatchBatchResult> runImportantNoticeDispatchBatch(int noticeBatchSize, int userBatchSize) {
        int safeNoticeBatchSize = Math.max(1, Math.min(noticeBatchSize, 50));
        int safeUserBatchSize = Math.max(1, Math.min(userBatchSize, 5000));

        return pool.withTransaction(client ->
            noticeDispatchRepository.ensureJobsForImportantNotices(client)
                .compose(v -> noticeDispatchRepository.fetchImportantNoticeJobs(client, safeNoticeBatchSize))
                .compose(jobs -> processJobs(client, jobs, safeUserBatchSize, 0, 0, 0, 0))
        );
    }

    private Future<DispatchBatchResult> processJobs(
        SqlClient client,
        List<NoticeNotificationDispatchRepository.NoticeDispatchJob> jobs,
        int userBatchSize,
        int index,
        int jobCount,
        int scannedUserCount,
        int insertedNotificationCount
    ) {
        if (jobs == null || index >= jobs.size()) {
            return Future.succeededFuture(DispatchBatchResult.builder()
                .jobCount(jobCount)
                .scannedUserCount(scannedUserCount)
                .insertedNotificationCount(insertedNotificationCount)
                .build());
        }

        NoticeNotificationDispatchRepository.NoticeDispatchJob job = jobs.get(index);
        return noticeDispatchRepository.dispatchChunk(client, job, userBatchSize)
            .compose(chunk -> noticeDispatchRepository
                .updateCursor(client, job.getNoticeId(), chunk.getNextCursor())
                .map(chunk))
            .compose(chunk -> processJobs(
                client,
                jobs,
                userBatchSize,
                index + 1,
                jobCount + 1,
                scannedUserCount + (chunk.getScannedUserCount() == null ? 0 : chunk.getScannedUserCount()),
                insertedNotificationCount + (chunk.getInsertedCount() == null ? 0 : chunk.getInsertedCount())
            ));
    }
}
