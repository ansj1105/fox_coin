-- Create Currency Table
CREATE TABLE currency (
    id SERIAL NOT NULL,
    code VARCHAR(10) NOT NULL,
    name VARCHAR(50) NOT NULL,
    chain VARCHAR(50) NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_currency PRIMARY KEY (id),
    CONSTRAINT UK_currency_code_chain UNIQUE (code, chain)
);

COMMENT ON TABLE currency IS '통화 종류 테이블';
COMMENT ON COLUMN currency.id IS 'sequence ID(인조키)';
COMMENT ON COLUMN currency.code IS '통화 코드 (KRW, USDT, BTC 등)';
COMMENT ON COLUMN currency.name IS '통화 이름';
COMMENT ON COLUMN currency.chain IS '체인 (TRON, ETH, INTERNAL 등)';
COMMENT ON COLUMN currency.is_active IS '활성화 여부';

-- Create Users Table
CREATE TABLE users (
    id BIGSERIAL NOT NULL,
    login_id VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    referral_code VARCHAR(20) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_users PRIMARY KEY (id),
    CONSTRAINT UK_users_login_id UNIQUE (login_id),
    CONSTRAINT UK_users_referral_code UNIQUE (referral_code)
);

COMMENT ON TABLE users IS '유저 테이블';
COMMENT ON COLUMN users.id IS 'user_id';
COMMENT ON COLUMN users.login_id IS '로그인 아이디';
COMMENT ON COLUMN users.password_hash IS '비밀번호 해시';
COMMENT ON COLUMN users.referral_code IS '레퍼럴 코드';
COMMENT ON COLUMN users.status IS '상태 (ACTIVE, BLOCKED, DELETED 등)';

-- Create User Wallet Table
CREATE TABLE user_wallets (
    id BIGSERIAL NOT NULL,
    user_id BIGINT NOT NULL,
    currency_id INT NOT NULL,
    address VARCHAR(255) NULL,
    private_key VARCHAR(255) NULL,
    tag_memo VARCHAR(100) NULL,
    balance DECIMAL(36, 18) DEFAULT 0 NOT NULL,
    locked_balance DECIMAL(36, 18) DEFAULT 0 NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_sync_height BIGINT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_user_wallets PRIMARY KEY (id),
    CONSTRAINT FK_wallet_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT FK_wallet_currency FOREIGN KEY (currency_id) REFERENCES currency(id) ON DELETE RESTRICT,
    CONSTRAINT UK_user_wallets_user_currency UNIQUE (user_id, currency_id)
);

COMMENT ON TABLE user_wallets IS '유저 지갑 테이블';
COMMENT ON COLUMN user_wallets.id IS 'Sequence ID (인조키)';
COMMENT ON COLUMN user_wallets.user_id IS 'user_id';
COMMENT ON COLUMN user_wallets.currency_id IS '통화 ID';
COMMENT ON COLUMN user_wallets.address IS '지갑주소';
COMMENT ON COLUMN user_wallets.private_key IS '개인 키 주소';
COMMENT ON COLUMN user_wallets.tag_memo IS 'XRP memo, TRON tag 등';
COMMENT ON COLUMN user_wallets.balance IS '지갑잔액';
COMMENT ON COLUMN user_wallets.locked_balance IS '잠금 잔액 (출금중/주문중 등)';
COMMENT ON COLUMN user_wallets.status IS '상태 (ACTIVE, FROZEN, CLOSED)';
COMMENT ON COLUMN user_wallets.last_sync_height IS '체인 동기화용 블록 높이';

-- Create Wallet Transaction Table
CREATE TABLE wallet_transactions (
    id BIGSERIAL NOT NULL,
    user_id BIGINT NOT NULL,
    wallet_id BIGINT NOT NULL,
    currency_id INT NOT NULL,
    tx_hash VARCHAR(255) NULL,
    tx_type VARCHAR(20) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    amount DECIMAL(36, 18) NOT NULL,
    fee DECIMAL(36, 18) DEFAULT 0 NOT NULL,
    status VARCHAR(20) NOT NULL,
    requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    confirmed_at TIMESTAMP NULL,
    failed_at TIMESTAMP NULL,
    request_ip VARCHAR(45) NULL,
    request_source VARCHAR(50) NULL,
    description VARCHAR(255) NULL,
    error_code VARCHAR(50) NULL,
    error_message VARCHAR(255) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_wallet_transactions PRIMARY KEY (id),
    CONSTRAINT FK_tx_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT FK_tx_wallet FOREIGN KEY (wallet_id) REFERENCES user_wallets(id) ON DELETE RESTRICT,
    CONSTRAINT FK_tx_currency FOREIGN KEY (currency_id) REFERENCES currency(id) ON DELETE RESTRICT
);

COMMENT ON TABLE wallet_transactions IS '트랜잭션 기록 테이블';
COMMENT ON COLUMN wallet_transactions.id IS '트랜잭션 ID';
COMMENT ON COLUMN wallet_transactions.user_id IS 'user_id';
COMMENT ON COLUMN wallet_transactions.wallet_id IS 'wallet_id';
COMMENT ON COLUMN wallet_transactions.currency_id IS '통화 ID';
COMMENT ON COLUMN wallet_transactions.tx_hash IS '트랜잭션 hash기록';
COMMENT ON COLUMN wallet_transactions.tx_type IS '트랜잭션 타입 (DEPOSIT, WITHDRAW, TRANSFER)';
COMMENT ON COLUMN wallet_transactions.direction IS '방향 (IN, OUT)';
COMMENT ON COLUMN wallet_transactions.amount IS '트랜잭션 양';
COMMENT ON COLUMN wallet_transactions.fee IS '수수료';
COMMENT ON COLUMN wallet_transactions.status IS '트랜잭션 상태 (PENDING, CONFIRMED, FAILED, CANCELED)';

-- Create Wallet Transaction Status Log Table
CREATE TABLE wallet_transaction_status_logs (
    id BIGSERIAL NOT NULL,
    tx_id BIGINT NOT NULL,
    old_status VARCHAR(20) NULL,
    new_status VARCHAR(20) NOT NULL,
    description VARCHAR(255) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by VARCHAR(50) NULL,
    CONSTRAINT PK_wallet_transaction_status_logs PRIMARY KEY (id),
    CONSTRAINT FK_tx_status_tx FOREIGN KEY (tx_id) REFERENCES wallet_transactions(id) ON DELETE CASCADE
);

COMMENT ON TABLE wallet_transaction_status_logs IS '트랜잭션 상태 기록 테이블';
COMMENT ON COLUMN wallet_transaction_status_logs.tx_id IS '트랜잭션 ID';
COMMENT ON COLUMN wallet_transaction_status_logs.old_status IS '이전 상태';
COMMENT ON COLUMN wallet_transaction_status_logs.new_status IS '새 상태';
COMMENT ON COLUMN wallet_transaction_status_logs.description IS '설명';
COMMENT ON COLUMN wallet_transaction_status_logs.created_by IS '생성자 (시스템 or 관리자ID)';

-- Create Referral Relation Table
CREATE TABLE referral_relations (
    id BIGSERIAL NOT NULL,
    referrer_id BIGINT NOT NULL,
    referred_id BIGINT NOT NULL,
    level INT NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT PK_referral_relations PRIMARY KEY (id),
    CONSTRAINT FK_ref_rel_referrer FOREIGN KEY (referrer_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT FK_ref_rel_referred FOREIGN KEY (referred_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT UK_referral_relations_unique UNIQUE (referred_id, level)
);

COMMENT ON TABLE referral_relations IS '레퍼럴 관계 테이블';
COMMENT ON COLUMN referral_relations.id IS 'referral_id';
COMMENT ON COLUMN referral_relations.referrer_id IS '추천인 (상위)';
COMMENT ON COLUMN referral_relations.referred_id IS '피추천인 (하위)';
COMMENT ON COLUMN referral_relations.level IS '레벨 (1=직접, 2,3.. 멀티레벨)';
COMMENT ON COLUMN referral_relations.status IS '상태 (ACTIVE, DEACTIVE)';

-- Create Referral Stats Table
CREATE TABLE referral_stats_logs (
    id BIGSERIAL NOT NULL,
    user_id BIGINT NOT NULL,
    direct_count INT DEFAULT 0 NOT NULL,
    team_count INT DEFAULT 0 NOT NULL,
    total_reward DECIMAL(36, 18) DEFAULT 0 NOT NULL,
    today_reward DECIMAL(36, 18) DEFAULT 0 NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    today_signup_count INT DEFAULT 0 NOT NULL,
    total_signup_count INT DEFAULT 0 NOT NULL,
    CONSTRAINT PK_referral_stats_logs PRIMARY KEY (id),
    CONSTRAINT FK_ref_stats_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

COMMENT ON TABLE referral_stats_logs IS '레퍼럴 통계 테이블';
COMMENT ON COLUMN referral_stats_logs.user_id IS '유저 ID';
COMMENT ON COLUMN referral_stats_logs.direct_count IS '직접 추천 수';
COMMENT ON COLUMN referral_stats_logs.team_count IS '전체 팀원 수';
COMMENT ON COLUMN referral_stats_logs.total_reward IS '총 리워드';
COMMENT ON COLUMN referral_stats_logs.today_reward IS '오늘 리워드';
COMMENT ON COLUMN referral_stats_logs.today_signup_count IS '오늘 가입자 수';
COMMENT ON COLUMN referral_stats_logs.total_signup_count IS '총 가입자 수';

-- Create function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create triggers for updated_at
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_user_wallets_updated_at BEFORE UPDATE ON user_wallets
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_wallet_transactions_updated_at BEFORE UPDATE ON wallet_transactions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_referral_stats_logs_updated_at BEFORE UPDATE ON referral_stats_logs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
