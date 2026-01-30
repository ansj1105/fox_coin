-- 영상 1회 = 1시간 채굴 연료 (MINING_AND_LEVEL_SPEC)
-- 시청 즉시 전량 적립이 아니라, 1시간에 걸쳐 시간당 채굴량만큼 적립. 다음 영상은 1시간 후에만 시청 가능.
CREATE TABLE mining_sessions (
    id BIGSERIAL NOT NULL,
    user_id BIGINT NOT NULL,
    started_at TIMESTAMP NOT NULL,
    ends_at TIMESTAMP NOT NULL,
    rate_per_hour DECIMAL(36, 18) NOT NULL,
    last_settled_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT PK_mining_sessions PRIMARY KEY (id),
    CONSTRAINT FK_mining_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT CHK_mining_sessions_rate CHECK (rate_per_hour > 0)
);

CREATE INDEX IDX_mining_sessions_user_id ON mining_sessions(user_id);
CREATE INDEX IDX_mining_sessions_ends_at ON mining_sessions(ends_at);
CREATE INDEX IDX_mining_sessions_user_ends ON mining_sessions(user_id, ends_at) WHERE ends_at > NOW();

COMMENT ON TABLE mining_sessions IS '1시간 채굴 세션 (영상 1회 시청 시 생성, ends_at까지 시간당 rate_per_hour만큼 settle로 적립)';
COMMENT ON COLUMN mining_sessions.rate_per_hour IS '이 세션의 시간당 채굴량(KORI). 레벨별 영상 1회당 채굴량과 동일';
COMMENT ON COLUMN mining_sessions.last_settled_at IS '마지막으로 잔액 적립한 시점. getMiningInfo/credit-video 시 (min(now,ends_at)-last_settled_at)*rate/3600 만큼 적립';
