-- 내부 전송 테이블 생성
-- 내부 전송: DB 트랜잭션으로만 처리 (블록체인 불필요)

-- Create Internal Transfer Table
CREATE TABLE internal_transfers (
    id BIGSERIAL NOT NULL,
    transfer_id VARCHAR(36) NOT NULL,
    sender_id BIGINT NOT NULL,
    sender_wallet_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    receiver_wallet_id BIGINT NOT NULL,
    currency_id INT NOT NULL,
    amount DECIMAL(36, 18) NOT NULL,
    fee DECIMAL(36, 18) DEFAULT 0 NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    transfer_type VARCHAR(20) NOT NULL DEFAULT 'INTERNAL',
    memo VARCHAR(255) NULL,
    request_ip VARCHAR(45) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    completed_at TIMESTAMP NULL,
    failed_at TIMESTAMP NULL,
    error_message VARCHAR(255) NULL,
    CONSTRAINT PK_internal_transfers PRIMARY KEY (id),
    CONSTRAINT UK_internal_transfers_transfer_id UNIQUE (transfer_id),
    CONSTRAINT FK_transfer_sender FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT FK_transfer_sender_wallet FOREIGN KEY (sender_wallet_id) REFERENCES user_wallets(id) ON DELETE RESTRICT,
    CONSTRAINT FK_transfer_receiver FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT FK_transfer_receiver_wallet FOREIGN KEY (receiver_wallet_id) REFERENCES user_wallets(id) ON DELETE RESTRICT,
    CONSTRAINT FK_transfer_currency FOREIGN KEY (currency_id) REFERENCES currency(id) ON DELETE RESTRICT,
    CONSTRAINT CHK_transfer_amount CHECK (amount > 0),
    CONSTRAINT CHK_transfer_fee CHECK (fee >= 0)
);

COMMENT ON TABLE internal_transfers IS '내부 전송 테이블';
COMMENT ON COLUMN internal_transfers.id IS 'Sequence ID';
COMMENT ON COLUMN internal_transfers.transfer_id IS '전송 고유 ID (UUID)';
COMMENT ON COLUMN internal_transfers.sender_id IS '송신자 유저 ID';
COMMENT ON COLUMN internal_transfers.sender_wallet_id IS '송신자 지갑 ID';
COMMENT ON COLUMN internal_transfers.receiver_id IS '수신자 유저 ID';
COMMENT ON COLUMN internal_transfers.receiver_wallet_id IS '수신자 지갑 ID';
COMMENT ON COLUMN internal_transfers.currency_id IS '통화 ID';
COMMENT ON COLUMN internal_transfers.amount IS '전송 금액';
COMMENT ON COLUMN internal_transfers.fee IS '수수료';
COMMENT ON COLUMN internal_transfers.status IS '상태 (PENDING, COMPLETED, FAILED, CANCELLED)';
COMMENT ON COLUMN internal_transfers.transfer_type IS '전송 타입 (INTERNAL, REFERRAL_REWARD, ADMIN_GRANT 등)';
COMMENT ON COLUMN internal_transfers.memo IS '메모';

-- Create External Transfer (Withdrawal) Request Table
CREATE TABLE external_transfers (
    id BIGSERIAL NOT NULL,
    transfer_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    wallet_id BIGINT NOT NULL,
    currency_id INT NOT NULL,
    to_address VARCHAR(255) NOT NULL,
    amount DECIMAL(36, 18) NOT NULL,
    fee DECIMAL(36, 18) DEFAULT 0 NOT NULL,
    network_fee DECIMAL(36, 18) DEFAULT 0 NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    tx_hash VARCHAR(255) NULL,
    chain VARCHAR(50) NOT NULL,
    confirmations INT DEFAULT 0 NOT NULL,
    required_confirmations INT DEFAULT 1 NOT NULL,
    memo VARCHAR(255) NULL,
    request_ip VARCHAR(45) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    submitted_at TIMESTAMP NULL,
    confirmed_at TIMESTAMP NULL,
    failed_at TIMESTAMP NULL,
    error_code VARCHAR(50) NULL,
    error_message VARCHAR(255) NULL,
    retry_count INT DEFAULT 0 NOT NULL,
    CONSTRAINT PK_external_transfers PRIMARY KEY (id),
    CONSTRAINT UK_external_transfers_transfer_id UNIQUE (transfer_id),
    CONSTRAINT FK_ext_transfer_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT FK_ext_transfer_wallet FOREIGN KEY (wallet_id) REFERENCES user_wallets(id) ON DELETE RESTRICT,
    CONSTRAINT FK_ext_transfer_currency FOREIGN KEY (currency_id) REFERENCES currency(id) ON DELETE RESTRICT,
    CONSTRAINT CHK_ext_transfer_amount CHECK (amount > 0)
);

COMMENT ON TABLE external_transfers IS '외부 전송 (출금) 테이블';
COMMENT ON COLUMN external_transfers.id IS 'Sequence ID';
COMMENT ON COLUMN external_transfers.transfer_id IS '전송 고유 ID (UUID)';
COMMENT ON COLUMN external_transfers.user_id IS '유저 ID';
COMMENT ON COLUMN external_transfers.wallet_id IS '출금 지갑 ID';
COMMENT ON COLUMN external_transfers.currency_id IS '통화 ID';
COMMENT ON COLUMN external_transfers.to_address IS '수신 주소 (외부 지갑)';
COMMENT ON COLUMN external_transfers.amount IS '전송 금액';
COMMENT ON COLUMN external_transfers.fee IS '서비스 수수료';
COMMENT ON COLUMN external_transfers.network_fee IS '네트워크 수수료 (가스비)';
COMMENT ON COLUMN external_transfers.status IS '상태 (PENDING, PROCESSING, SUBMITTED, CONFIRMED, FAILED, CANCELLED)';
COMMENT ON COLUMN external_transfers.tx_hash IS '블록체인 트랜잭션 해시';
COMMENT ON COLUMN external_transfers.chain IS '체인 (TRON, ETH 등)';
COMMENT ON COLUMN external_transfers.confirmations IS '현재 컨펌 수';
COMMENT ON COLUMN external_transfers.required_confirmations IS '필요 컨펌 수';

-- Create indexes
CREATE INDEX IDX_internal_transfers_sender ON internal_transfers(sender_id);
CREATE INDEX IDX_internal_transfers_receiver ON internal_transfers(receiver_id);
CREATE INDEX IDX_internal_transfers_status ON internal_transfers(status);
CREATE INDEX IDX_internal_transfers_created_at ON internal_transfers(created_at);

CREATE INDEX IDX_external_transfers_user ON external_transfers(user_id);
CREATE INDEX IDX_external_transfers_status ON external_transfers(status);
CREATE INDEX IDX_external_transfers_tx_hash ON external_transfers(tx_hash);
CREATE INDEX IDX_external_transfers_created_at ON external_transfers(created_at);

-- Create trigger for updated_at (if needed)
-- internal_transfers와 external_transfers는 상태 변경 시 별도 timestamp 컬럼 사용

