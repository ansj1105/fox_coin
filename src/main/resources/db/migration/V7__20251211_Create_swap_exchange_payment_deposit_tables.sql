-- 스왑, 환전, 결제 입금, 토큰 입금 테이블 생성
-- 기존 전송 테이블에 order_number, transaction_type 필드 추가

-- 기존 테이블에 필드 추가
ALTER TABLE internal_transfers ADD COLUMN IF NOT EXISTS order_number VARCHAR(50) NULL;
ALTER TABLE internal_transfers ADD COLUMN IF NOT EXISTS transaction_type VARCHAR(50) NULL;
ALTER TABLE external_transfers ADD COLUMN IF NOT EXISTS order_number VARCHAR(50) NULL;
ALTER TABLE external_transfers ADD COLUMN IF NOT EXISTS transaction_type VARCHAR(50) NULL;

COMMENT ON COLUMN internal_transfers.order_number IS '주문번호';
COMMENT ON COLUMN internal_transfers.transaction_type IS '거래 유형 (WITHDRAW, TOKEN_DEPOSIT, PAYMENT_DEPOSIT, SWAP, EXCHANGE)';
COMMENT ON COLUMN external_transfers.order_number IS '주문번호';
COMMENT ON COLUMN external_transfers.transaction_type IS '거래 유형 (WITHDRAW, TOKEN_DEPOSIT, PAYMENT_DEPOSIT, SWAP, EXCHANGE)';

-- 스왑 테이블
CREATE TABLE swaps (
    id BIGSERIAL NOT NULL,
    swap_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    order_number VARCHAR(50) NOT NULL,
    from_currency_id INT NOT NULL,
    to_currency_id INT NOT NULL,
    from_amount DECIMAL(36, 18) NOT NULL,
    to_amount DECIMAL(36, 18) NOT NULL,
    network VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    completed_at TIMESTAMP NULL,
    failed_at TIMESTAMP NULL,
    error_message VARCHAR(255) NULL,
    CONSTRAINT PK_swaps PRIMARY KEY (id),
    CONSTRAINT UK_swaps_swap_id UNIQUE (swap_id),
    CONSTRAINT UK_swaps_order_number UNIQUE (order_number),
    CONSTRAINT FK_swaps_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT FK_swaps_from_currency FOREIGN KEY (from_currency_id) REFERENCES currency(id) ON DELETE RESTRICT,
    CONSTRAINT FK_swaps_to_currency FOREIGN KEY (to_currency_id) REFERENCES currency(id) ON DELETE RESTRICT,
    CONSTRAINT CHK_swaps_from_amount CHECK (from_amount > 0),
    CONSTRAINT CHK_swaps_to_amount CHECK (to_amount > 0)
);

COMMENT ON TABLE swaps IS '토큰 스왑 테이블';
COMMENT ON COLUMN swaps.id IS 'Sequence ID';
COMMENT ON COLUMN swaps.swap_id IS '스왑 고유 ID (UUID)';
COMMENT ON COLUMN swaps.user_id IS '사용자 ID';
COMMENT ON COLUMN swaps.order_number IS '주문번호';
COMMENT ON COLUMN swaps.from_currency_id IS 'FROM 통화 ID';
COMMENT ON COLUMN swaps.to_currency_id IS 'TO 통화 ID';
COMMENT ON COLUMN swaps.from_amount IS 'FROM 금액';
COMMENT ON COLUMN swaps.to_amount IS 'TO 금액';
COMMENT ON COLUMN swaps.network IS '네트워크 (Ether, TRON 등)';
COMMENT ON COLUMN swaps.status IS '상태 (PENDING, COMPLETED, FAILED)';

-- 환전 테이블
CREATE TABLE exchanges (
    id BIGSERIAL NOT NULL,
    exchange_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    order_number VARCHAR(50) NOT NULL,
    from_currency_id INT NOT NULL,
    to_currency_id INT NOT NULL,
    from_amount DECIMAL(36, 18) NOT NULL,
    to_amount DECIMAL(36, 18) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    completed_at TIMESTAMP NULL,
    failed_at TIMESTAMP NULL,
    error_message VARCHAR(255) NULL,
    CONSTRAINT PK_exchanges PRIMARY KEY (id),
    CONSTRAINT UK_exchanges_exchange_id UNIQUE (exchange_id),
    CONSTRAINT UK_exchanges_order_number UNIQUE (order_number),
    CONSTRAINT FK_exchanges_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT FK_exchanges_from_currency FOREIGN KEY (from_currency_id) REFERENCES currency(id) ON DELETE RESTRICT,
    CONSTRAINT FK_exchanges_to_currency FOREIGN KEY (to_currency_id) REFERENCES currency(id) ON DELETE RESTRICT,
    CONSTRAINT CHK_exchanges_from_amount CHECK (from_amount > 0),
    CONSTRAINT CHK_exchanges_to_amount CHECK (to_amount > 0)
);

COMMENT ON TABLE exchanges IS '환전 테이블 (KRWT → 블루 다이아)';
COMMENT ON COLUMN exchanges.id IS 'Sequence ID';
COMMENT ON COLUMN exchanges.exchange_id IS '환전 고유 ID (UUID)';
COMMENT ON COLUMN exchanges.user_id IS '사용자 ID';
COMMENT ON COLUMN exchanges.order_number IS '주문번호';
COMMENT ON COLUMN exchanges.from_currency_id IS 'FROM 통화 ID (KRWT)';
COMMENT ON COLUMN exchanges.to_currency_id IS 'TO 통화 ID (BLUEDIA)';
COMMENT ON COLUMN exchanges.from_amount IS 'FROM 금액';
COMMENT ON COLUMN exchanges.to_amount IS 'TO 금액';
COMMENT ON COLUMN exchanges.status IS '상태 (PENDING, COMPLETED, FAILED)';

-- 결제 입금 테이블
CREATE TABLE payment_deposits (
    id BIGSERIAL NOT NULL,
    deposit_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    order_number VARCHAR(50) NOT NULL,
    currency_id INT NOT NULL,
    amount DECIMAL(36, 18) NOT NULL,
    deposit_method VARCHAR(20) NOT NULL,
    payment_amount DECIMAL(36, 18) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    completed_at TIMESTAMP NULL,
    failed_at TIMESTAMP NULL,
    error_message VARCHAR(255) NULL,
    CONSTRAINT PK_payment_deposits PRIMARY KEY (id),
    CONSTRAINT UK_payment_deposits_deposit_id UNIQUE (deposit_id),
    CONSTRAINT UK_payment_deposits_order_number UNIQUE (order_number),
    CONSTRAINT FK_payment_deposits_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT FK_payment_deposits_currency FOREIGN KEY (currency_id) REFERENCES currency(id) ON DELETE RESTRICT,
    CONSTRAINT CHK_payment_deposits_amount CHECK (amount > 0),
    CONSTRAINT CHK_payment_deposits_payment_amount CHECK (payment_amount > 0)
);

COMMENT ON TABLE payment_deposits IS '결제 입금 테이블 (카드/계좌이체/페이)';
COMMENT ON COLUMN payment_deposits.id IS 'Sequence ID';
COMMENT ON COLUMN payment_deposits.deposit_id IS '입금 고유 ID (UUID)';
COMMENT ON COLUMN payment_deposits.user_id IS '사용자 ID';
COMMENT ON COLUMN payment_deposits.order_number IS '주문번호';
COMMENT ON COLUMN payment_deposits.currency_id IS '통화 ID';
COMMENT ON COLUMN payment_deposits.amount IS '입금 금액 (토큰)';
COMMENT ON COLUMN payment_deposits.deposit_method IS '입금 방법 (CARD, BANK_TRANSFER, PAY)';
COMMENT ON COLUMN payment_deposits.payment_amount IS '결제 금액 (원화 등)';
COMMENT ON COLUMN payment_deposits.status IS '상태 (PENDING, COMPLETED, FAILED)';

-- 토큰 입금 테이블 (외부에서 들어오는 입금)
CREATE TABLE token_deposits (
    id BIGSERIAL NOT NULL,
    deposit_id VARCHAR(36) NOT NULL,
    user_id BIGINT NULL,
    order_number VARCHAR(50) NOT NULL,
    currency_id INT NOT NULL,
    amount DECIMAL(36, 18) NOT NULL,
    network VARCHAR(50) NOT NULL,
    sender_address VARCHAR(255) NOT NULL,
    tx_hash VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    confirmed_at TIMESTAMP NULL,
    failed_at TIMESTAMP NULL,
    error_message VARCHAR(255) NULL,
    CONSTRAINT PK_token_deposits PRIMARY KEY (id),
    CONSTRAINT UK_token_deposits_deposit_id UNIQUE (deposit_id),
    CONSTRAINT UK_token_deposits_order_number UNIQUE (order_number),
    CONSTRAINT FK_token_deposits_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT FK_token_deposits_currency FOREIGN KEY (currency_id) REFERENCES currency(id) ON DELETE RESTRICT,
    CONSTRAINT CHK_token_deposits_amount CHECK (amount > 0)
);

COMMENT ON TABLE token_deposits IS '토큰 입금 테이블 (외부에서 들어오는 입금)';
COMMENT ON COLUMN token_deposits.id IS 'Sequence ID';
COMMENT ON COLUMN token_deposits.deposit_id IS '입금 고유 ID (UUID)';
COMMENT ON COLUMN token_deposits.user_id IS '사용자 ID (자동 매칭 시)';
COMMENT ON COLUMN token_deposits.order_number IS '주문번호';
COMMENT ON COLUMN token_deposits.currency_id IS '통화 ID';
COMMENT ON COLUMN token_deposits.amount IS '입금 금액';
COMMENT ON COLUMN token_deposits.network IS '네트워크 (Ether, TRON 등)';
COMMENT ON COLUMN token_deposits.sender_address IS '송신 지갑 주소';
COMMENT ON COLUMN token_deposits.tx_hash IS '블록체인 트랜잭션 해시';
COMMENT ON COLUMN token_deposits.status IS '상태 (PENDING, COMPLETED, FAILED)';

-- 인덱스 생성
CREATE INDEX IDX_swaps_user_id ON swaps(user_id);
CREATE INDEX IDX_swaps_status ON swaps(status);
CREATE INDEX IDX_swaps_created_at ON swaps(created_at);

CREATE INDEX IDX_exchanges_user_id ON exchanges(user_id);
CREATE INDEX IDX_exchanges_status ON exchanges(status);
CREATE INDEX IDX_exchanges_created_at ON exchanges(created_at);

CREATE INDEX IDX_payment_deposits_user_id ON payment_deposits(user_id);
CREATE INDEX IDX_payment_deposits_status ON payment_deposits(status);
CREATE INDEX IDX_payment_deposits_created_at ON payment_deposits(created_at);

CREATE INDEX IDX_token_deposits_user_id ON token_deposits(user_id);
CREATE INDEX IDX_token_deposits_status ON token_deposits(status);
CREATE INDEX IDX_token_deposits_created_at ON token_deposits(created_at);
CREATE INDEX IDX_token_deposits_tx_hash ON token_deposits(tx_hash);

