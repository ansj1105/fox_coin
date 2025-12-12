-- 채굴 내역 테이블 생성
CREATE TABLE mining_history (
    id BIGSERIAL NOT NULL,
    user_id BIGINT NOT NULL,
    level INT NOT NULL,
    amount DECIMAL(36, 18) NOT NULL,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_mining_history PRIMARY KEY (id),
    CONSTRAINT FK_mining_history_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT CHK_mining_history_amount CHECK (amount > 0),
    CONSTRAINT CHK_mining_history_type CHECK (type IN ('BROADCAST_PROGRESS', 'BROADCAST_WATCH'))
);

COMMENT ON TABLE mining_history IS '채굴 내역 테이블 (래퍼럴 수익 제외)';
COMMENT ON COLUMN mining_history.id IS 'Sequence ID';
COMMENT ON COLUMN mining_history.user_id IS '사용자 ID';
COMMENT ON COLUMN mining_history.level IS '채굴 당시 레벨';
COMMENT ON COLUMN mining_history.amount IS '채굴량';
COMMENT ON COLUMN mining_history.type IS '채굴 유형 (BROADCAST_PROGRESS: 방송진행, BROADCAST_WATCH: 방송시청)';
COMMENT ON COLUMN mining_history.status IS '상태 (COMPLETED, FAILED, CANCELLED)';
COMMENT ON COLUMN mining_history.created_at IS '생성 시간';

-- 인덱스 생성
CREATE INDEX IDX_mining_history_user_id ON mining_history(user_id);
CREATE INDEX IDX_mining_history_created_at ON mining_history(created_at DESC);
CREATE INDEX IDX_mining_history_user_created ON mining_history(user_id, created_at DESC);
CREATE INDEX IDX_mining_history_type ON mining_history(type);
CREATE INDEX IDX_mining_history_status ON mining_history(status);

-- updated_at 트리거 (필요시 사용)
CREATE TRIGGER update_mining_history_updated_at BEFORE UPDATE ON mining_history
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

