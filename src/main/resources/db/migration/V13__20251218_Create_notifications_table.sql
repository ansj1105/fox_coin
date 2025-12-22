-- 알림 테이블
-- Create Notifications Table
CREATE TABLE notifications (
    id BIGSERIAL NOT NULL,
    user_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT false,
    related_id BIGINT NULL,
    metadata JSONB NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_notifications PRIMARY KEY (id),
    CONSTRAINT FK_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

COMMENT ON TABLE notifications IS '알림 테이블';
COMMENT ON COLUMN notifications.id IS 'Sequence ID';
COMMENT ON COLUMN notifications.user_id IS '유저 ID';
COMMENT ON COLUMN notifications.type IS '알림 타입 (DEPOSIT_SUCCESS, DEPOSIT_FAILED, WITHDRAW_SUCCESS, WITHDRAW_FAILED, EXCHANGE_SUCCESS, EXCHANGE_FAILED, SWAP_SUCCESS, SWAP_FAILED, NOTICE, INQUIRY_SUCCESS, MINING_LIMIT_REACHED, LEVEL_UP, MISSION_ACTIVATED)';
COMMENT ON COLUMN notifications.title IS '알림 제목';
COMMENT ON COLUMN notifications.message IS '알림 메시지';
COMMENT ON COLUMN notifications.is_read IS '읽음 여부';
COMMENT ON COLUMN notifications.related_id IS '관련 ID (예: 입금 ID, 출금 ID 등)';
COMMENT ON COLUMN notifications.metadata IS '추가 메타데이터 (JSON)';

-- 인덱스 생성
CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_user_id_is_read ON notifications(user_id, is_read);
CREATE INDEX idx_notifications_created_at ON notifications(created_at DESC);

