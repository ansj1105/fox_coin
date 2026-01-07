-- 에어드랍 Phase 테이블
-- Create Airdrop Phases Table
CREATE TABLE airdrop_phases (
    id BIGSERIAL NOT NULL,
    user_id BIGINT NOT NULL,
    phase INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    amount DECIMAL(20, 8) NOT NULL,
    unlock_date TIMESTAMP NOT NULL,
    days_remaining INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_airdrop_phases PRIMARY KEY (id),
    CONSTRAINT FK_airdrop_phases_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT UK_airdrop_phases_user_phase UNIQUE (user_id, phase),
    CONSTRAINT CK_airdrop_phases_phase CHECK (phase >= 1 AND phase <= 5),
    CONSTRAINT CK_airdrop_phases_status CHECK (status IN ('RELEASED', 'PROCESSING'))
);

COMMENT ON TABLE airdrop_phases IS '에어드랍 Phase 테이블';
COMMENT ON COLUMN airdrop_phases.id IS 'Sequence ID';
COMMENT ON COLUMN airdrop_phases.user_id IS '사용자 ID';
COMMENT ON COLUMN airdrop_phases.phase IS 'Phase 번호 (1~5)';
COMMENT ON COLUMN airdrop_phases.status IS '상태 (RELEASED: 해제됨, PROCESSING: 처리 중)';
COMMENT ON COLUMN airdrop_phases.amount IS 'Phase별 에어드랍 금액';
COMMENT ON COLUMN airdrop_phases.unlock_date IS '언락 예정 날짜';
COMMENT ON COLUMN airdrop_phases.days_remaining IS '남은 락업 기간 (일수)';
COMMENT ON COLUMN airdrop_phases.created_at IS '생성 시간';
COMMENT ON COLUMN airdrop_phases.updated_at IS '수정 시간';

-- 에어드랍 전송 테이블
-- Create Airdrop Transfers Table
CREATE TABLE airdrop_transfers (
    id BIGSERIAL NOT NULL,
    transfer_id VARCHAR(255) NOT NULL,
    user_id BIGINT NOT NULL,
    wallet_id BIGINT NOT NULL,
    currency_id INTEGER NOT NULL,
    amount DECIMAL(20, 8) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    order_number VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_airdrop_transfers PRIMARY KEY (id),
    CONSTRAINT FK_airdrop_transfers_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT FK_airdrop_transfers_wallet FOREIGN KEY (wallet_id) REFERENCES user_wallets(id) ON DELETE CASCADE,
    CONSTRAINT FK_airdrop_transfers_currency FOREIGN KEY (currency_id) REFERENCES currency(id) ON DELETE CASCADE,
    CONSTRAINT UK_airdrop_transfers_transfer_id UNIQUE (transfer_id),
    CONSTRAINT CK_airdrop_transfers_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'CANCELLED'))
);

COMMENT ON TABLE airdrop_transfers IS '에어드랍 전송 테이블';
COMMENT ON COLUMN airdrop_transfers.id IS 'Sequence ID';
COMMENT ON COLUMN airdrop_transfers.transfer_id IS '전송 ID (UUID)';
COMMENT ON COLUMN airdrop_transfers.user_id IS '사용자 ID';
COMMENT ON COLUMN airdrop_transfers.wallet_id IS '지갑 ID';
COMMENT ON COLUMN airdrop_transfers.currency_id IS '통화 ID';
COMMENT ON COLUMN airdrop_transfers.amount IS '전송 금액';
COMMENT ON COLUMN airdrop_transfers.status IS '상태 (PENDING, COMPLETED, FAILED, CANCELLED)';
COMMENT ON COLUMN airdrop_transfers.order_number IS '주문번호';
COMMENT ON COLUMN airdrop_transfers.created_at IS '생성 시간';
COMMENT ON COLUMN airdrop_transfers.updated_at IS '수정 시간';

-- 인덱스 생성
CREATE INDEX IDX_airdrop_phases_user_id ON airdrop_phases(user_id);
CREATE INDEX IDX_airdrop_phases_status ON airdrop_phases(status);
CREATE INDEX IDX_airdrop_transfers_user_id ON airdrop_transfers(user_id);
CREATE INDEX IDX_airdrop_transfers_transfer_id ON airdrop_transfers(transfer_id);
CREATE INDEX IDX_airdrop_transfers_status ON airdrop_transfers(status);

