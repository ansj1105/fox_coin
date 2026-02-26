CREATE TABLE IF NOT EXISTS notice_notification_jobs (
    notice_id BIGINT NOT NULL,
    last_user_id BIGINT NOT NULL DEFAULT 0,
    last_scanned_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT PK_notice_notification_jobs PRIMARY KEY (notice_id),
    CONSTRAINT FK_notice_notification_jobs_notice FOREIGN KEY (notice_id) REFERENCES notices(id) ON DELETE CASCADE
);

COMMENT ON TABLE notice_notification_jobs IS '중요 공지 알림 배치 처리 커서 테이블';
COMMENT ON COLUMN notice_notification_jobs.notice_id IS '공지 ID';
COMMENT ON COLUMN notice_notification_jobs.last_user_id IS '마지막 처리 사용자 ID 커서';
COMMENT ON COLUMN notice_notification_jobs.last_scanned_at IS '마지막 스캔 시각';
COMMENT ON COLUMN notice_notification_jobs.created_at IS '생성 시각';
COMMENT ON COLUMN notice_notification_jobs.updated_at IS '수정 시각';

CREATE INDEX IF NOT EXISTS idx_notice_notification_jobs_last_scanned_at
    ON notice_notification_jobs(last_scanned_at);
