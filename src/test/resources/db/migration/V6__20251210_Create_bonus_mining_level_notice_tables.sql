-- 보너스 채굴 효율 관련 테이블
-- Create User Bonuses Table
CREATE TABLE user_bonuses (
    id BIGSERIAL NOT NULL,
    user_id BIGINT NOT NULL,
    bonus_type VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    expires_at TIMESTAMP NULL,
    current_count INT DEFAULT 0 NOT NULL,
    max_count INT NULL,
    metadata JSONB NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_user_bonuses PRIMARY KEY (id),
    CONSTRAINT FK_user_bonuses_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT UK_user_bonuses_user_type UNIQUE (user_id, bonus_type)
);

COMMENT ON TABLE user_bonuses IS '사용자 보너스 활성화 상태 테이블';
COMMENT ON COLUMN user_bonuses.id IS 'Sequence ID';
COMMENT ON COLUMN user_bonuses.user_id IS '유저 ID';
COMMENT ON COLUMN user_bonuses.bonus_type IS '보너스 타입 (SOCIAL_LINK, PHONE_VERIFICATION, AD_WATCH, REFERRAL, PREMIUM_SUBSCRIPTION, REVIEW, AGENCY, REFERRAL_CODE_INPUT)';
COMMENT ON COLUMN user_bonuses.is_active IS '활성화 여부';
COMMENT ON COLUMN user_bonuses.expires_at IS '만료 시간 (영구 보너스는 NULL)';
COMMENT ON COLUMN user_bonuses.current_count IS '현재 사용 횟수 (광고 시청 등)';
COMMENT ON COLUMN user_bonuses.max_count IS '최대 사용 횟수 (NULL이면 무제한)';
COMMENT ON COLUMN user_bonuses.metadata IS '추가 메타데이터 (JSON)';

-- 채굴 레벨 및 일일 채굴량 관련 테이블
-- Create Mining Levels Table
CREATE TABLE mining_levels (
    id SERIAL NOT NULL,
    level INT NOT NULL,
    daily_max_mining DECIMAL(36, 18) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_mining_levels PRIMARY KEY (id),
    CONSTRAINT UK_mining_levels_level UNIQUE (level)
);

COMMENT ON TABLE mining_levels IS '레벨별 일일 최대 채굴량 테이블';
COMMENT ON COLUMN mining_levels.id IS 'Sequence ID';
COMMENT ON COLUMN mining_levels.level IS '레벨 (1~9)';
COMMENT ON COLUMN mining_levels.daily_max_mining IS '일일 최대 채굴량';

-- Create Daily Mining Table
CREATE TABLE daily_mining (
    id BIGSERIAL NOT NULL,
    user_id BIGINT NOT NULL,
    mining_date DATE NOT NULL,
    mining_amount DECIMAL(36, 18) DEFAULT 0 NOT NULL,
    reset_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_daily_mining PRIMARY KEY (id),
    CONSTRAINT FK_daily_mining_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT UK_daily_mining_user_date UNIQUE (user_id, mining_date)
);

COMMENT ON TABLE daily_mining IS '일일 채굴량 기록 테이블';
COMMENT ON COLUMN daily_mining.id IS 'Sequence ID';
COMMENT ON COLUMN daily_mining.user_id IS '유저 ID';
COMMENT ON COLUMN daily_mining.mining_date IS '채굴 날짜';
COMMENT ON COLUMN daily_mining.mining_amount IS '채굴량';
COMMENT ON COLUMN daily_mining.reset_at IS '리셋 시간 (다음날 00:00:00)';

-- 사용자 레벨 및 경험치 (users 테이블에 컬럼 추가)
ALTER TABLE users ADD COLUMN IF NOT EXISTS level INT DEFAULT 1 NOT NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS exp DECIMAL(36, 18) DEFAULT 0 NOT NULL;

COMMENT ON COLUMN users.level IS '사용자 레벨 (1~9)';
COMMENT ON COLUMN users.exp IS '현재 경험치';

-- 공지사항 테이블
-- Create Notices Table
CREATE TABLE notices (
    id BIGSERIAL NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    is_important BOOLEAN NOT NULL DEFAULT false,
    created_by BIGINT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_notices PRIMARY KEY (id),
    CONSTRAINT FK_notices_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
);

COMMENT ON TABLE notices IS '공지사항 테이블';
COMMENT ON COLUMN notices.id IS 'Sequence ID';
COMMENT ON COLUMN notices.title IS '제목';
COMMENT ON COLUMN notices.content IS '내용';
COMMENT ON COLUMN notices.is_important IS '중요 공지 여부';
COMMENT ON COLUMN notices.created_by IS '작성자 ID (관리자)';

-- 소셜 연동 테이블
-- Create Social Links Table
CREATE TABLE social_links (
    id BIGSERIAL NOT NULL,
    user_id BIGINT NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    email VARCHAR(255) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_social_links PRIMARY KEY (id),
    CONSTRAINT FK_social_links_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT UK_social_links_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT UK_social_links_user_provider UNIQUE (user_id, provider)
);

COMMENT ON TABLE social_links IS '소셜 계정 연동 테이블';
COMMENT ON COLUMN social_links.id IS 'Sequence ID';
COMMENT ON COLUMN social_links.user_id IS '유저 ID';
COMMENT ON COLUMN social_links.provider IS '제공자 (KAKAO, GOOGLE, EMAIL)';
COMMENT ON COLUMN social_links.provider_user_id IS '제공자 사용자 ID';
COMMENT ON COLUMN social_links.email IS '이메일 (EMAIL 제공자일 경우)';

-- 본인인증 테이블
-- Create Phone Verifications Table
CREATE TABLE phone_verifications (
    id BIGSERIAL NOT NULL,
    user_id BIGINT NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    verification_code VARCHAR(10) NULL,
    is_verified BOOLEAN NOT NULL DEFAULT false,
    verified_at TIMESTAMP NULL,
    expires_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_phone_verifications PRIMARY KEY (id),
    CONSTRAINT FK_phone_verifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT UK_phone_verifications_user UNIQUE (user_id),
    CONSTRAINT UK_phone_verifications_phone UNIQUE (phone_number)
);

COMMENT ON TABLE phone_verifications IS '본인인증 테이블';
COMMENT ON COLUMN phone_verifications.id IS 'Sequence ID';
COMMENT ON COLUMN phone_verifications.user_id IS '유저 ID';
COMMENT ON COLUMN phone_verifications.phone_number IS '휴대폰 번호';
COMMENT ON COLUMN phone_verifications.verification_code IS '인증 코드';
COMMENT ON COLUMN phone_verifications.is_verified IS '인증 완료 여부';
COMMENT ON COLUMN phone_verifications.verified_at IS '인증 완료 시간';
COMMENT ON COLUMN phone_verifications.expires_at IS '인증 코드 만료 시간';

-- 프리미엄 구독 테이블
-- Create Subscriptions Table
CREATE TABLE subscriptions (
    id BIGSERIAL NOT NULL,
    user_id BIGINT NOT NULL,
    package_type VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    started_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_subscriptions PRIMARY KEY (id),
    CONSTRAINT FK_subscriptions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT UK_subscriptions_user UNIQUE (user_id)
);

COMMENT ON TABLE subscriptions IS '프리미엄 구독 테이블';
COMMENT ON COLUMN subscriptions.id IS 'Sequence ID';
COMMENT ON COLUMN subscriptions.user_id IS '유저 ID';
COMMENT ON COLUMN subscriptions.package_type IS '패키지 타입';
COMMENT ON COLUMN subscriptions.is_active IS '활성화 여부';
COMMENT ON COLUMN subscriptions.started_at IS '시작 시간';
COMMENT ON COLUMN subscriptions.expires_at IS '만료 시간 (NULL이면 무제한)';

-- 리뷰 테이블
-- Create Reviews Table
CREATE TABLE reviews (
    id BIGSERIAL NOT NULL,
    user_id BIGINT NOT NULL,
    platform VARCHAR(20) NULL,
    review_id VARCHAR(255) NULL,
    reviewed_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_reviews PRIMARY KEY (id),
    CONSTRAINT FK_reviews_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT UK_reviews_user UNIQUE (user_id)
);

COMMENT ON TABLE reviews IS '리뷰 작성 테이블';
COMMENT ON COLUMN reviews.id IS 'Sequence ID';
COMMENT ON COLUMN reviews.user_id IS '유저 ID';
COMMENT ON COLUMN reviews.platform IS '플랫폼 (GOOGLE_PLAY, APP_STORE 등)';
COMMENT ON COLUMN reviews.review_id IS '리뷰 ID (플랫폼별)';
COMMENT ON COLUMN reviews.reviewed_at IS '리뷰 작성 시간';

-- 에이전시 가입 테이블
-- Create Agency Memberships Table
CREATE TABLE agency_memberships (
    id BIGSERIAL NOT NULL,
    user_id BIGINT NOT NULL,
    agency_id VARCHAR(100) NOT NULL,
    agency_name VARCHAR(255) NULL,
    joined_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_agency_memberships PRIMARY KEY (id),
    CONSTRAINT FK_agency_memberships_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT UK_agency_memberships_user UNIQUE (user_id)
);

COMMENT ON TABLE agency_memberships IS '에이전시 가입 테이블';
COMMENT ON COLUMN agency_memberships.id IS 'Sequence ID';
COMMENT ON COLUMN agency_memberships.user_id IS '유저 ID';
COMMENT ON COLUMN agency_memberships.agency_id IS '에이전시 ID';
COMMENT ON COLUMN agency_memberships.agency_name IS '에이전시 이름';
COMMENT ON COLUMN agency_memberships.joined_at IS '가입 시간';

-- Create indexes
CREATE INDEX IDX_user_bonuses_user ON user_bonuses(user_id);
CREATE INDEX IDX_user_bonuses_type ON user_bonuses(bonus_type);
CREATE INDEX IDX_user_bonuses_active ON user_bonuses(is_active);

CREATE INDEX IDX_daily_mining_user ON daily_mining(user_id);
CREATE INDEX IDX_daily_mining_date ON daily_mining(mining_date);

CREATE INDEX IDX_notices_important ON notices(is_important);
CREATE INDEX IDX_notices_created_at ON notices(created_at DESC);

CREATE INDEX IDX_social_links_user ON social_links(user_id);
CREATE INDEX IDX_social_links_provider ON social_links(provider);

CREATE INDEX IDX_phone_verifications_user ON phone_verifications(user_id);
CREATE INDEX IDX_phone_verifications_phone ON phone_verifications(phone_number);

CREATE INDEX IDX_subscriptions_user ON subscriptions(user_id);
CREATE INDEX IDX_subscriptions_active ON subscriptions(is_active);

CREATE INDEX IDX_reviews_user ON reviews(user_id);

CREATE INDEX IDX_agency_memberships_user ON agency_memberships(user_id);

-- Create triggers for updated_at
CREATE TRIGGER update_user_bonuses_updated_at BEFORE UPDATE ON user_bonuses
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_daily_mining_updated_at BEFORE UPDATE ON daily_mining
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_notices_updated_at BEFORE UPDATE ON notices
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_social_links_updated_at BEFORE UPDATE ON social_links
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_phone_verifications_updated_at BEFORE UPDATE ON phone_verifications
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_subscriptions_updated_at BEFORE UPDATE ON subscriptions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_reviews_updated_at BEFORE UPDATE ON reviews
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_agency_memberships_updated_at BEFORE UPDATE ON agency_memberships
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Insert default mining levels (LV1~LV9)
INSERT INTO mining_levels (level, daily_max_mining) VALUES
    (1, 1000.0),
    (2, 2000.0),
    (3, 3000.0),
    (4, 4000.0),
    (5, 5000.0),
    (6, 6000.0),
    (7, 7000.0),
    (8, 8000.0),
    (9, 9000.0)
ON CONFLICT (level) DO NOTHING;

