-- 사용자 및 관련 테이블에 deleted_at 컬럼 추가 (Soft Delete 지원)
-- 테스트 환경용 마이그레이션

-- 1. users 테이블에 deleted_at 추가
ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;

COMMENT ON COLUMN users.deleted_at IS '삭제 시간 (Soft Delete)';

-- 2. user_wallets 테이블에 deleted_at 추가
ALTER TABLE user_wallets ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;

COMMENT ON COLUMN user_wallets.deleted_at IS '삭제 시간 (Soft Delete)';

-- 3. internal_transfers 테이블에 deleted_at 추가
ALTER TABLE internal_transfers ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;

COMMENT ON COLUMN internal_transfers.deleted_at IS '삭제 시간 (Soft Delete)';

-- 4. external_transfers 테이블에 deleted_at 추가
ALTER TABLE external_transfers ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;

COMMENT ON COLUMN external_transfers.deleted_at IS '삭제 시간 (Soft Delete)';

-- 5. swaps 테이블에 deleted_at 추가
ALTER TABLE swaps ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;

COMMENT ON COLUMN swaps.deleted_at IS '삭제 시간 (Soft Delete)';

-- 6. exchanges 테이블에 deleted_at 추가
ALTER TABLE exchanges ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;

COMMENT ON COLUMN exchanges.deleted_at IS '삭제 시간 (Soft Delete)';

-- 7. payment_deposits 테이블에 deleted_at 추가
ALTER TABLE payment_deposits ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;

COMMENT ON COLUMN payment_deposits.deleted_at IS '삭제 시간 (Soft Delete)';

-- 8. token_deposits 테이블에 deleted_at 추가
ALTER TABLE token_deposits ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;

COMMENT ON COLUMN token_deposits.deleted_at IS '삭제 시간 (Soft Delete)';

-- 9. user_bonuses 테이블에 deleted_at 추가
ALTER TABLE user_bonuses ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;

COMMENT ON COLUMN user_bonuses.deleted_at IS '삭제 시간 (Soft Delete)';

-- 10. daily_mining 테이블에 deleted_at 추가
ALTER TABLE daily_mining ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;

COMMENT ON COLUMN daily_mining.deleted_at IS '삭제 시간 (Soft Delete)';

-- 11. social_links 테이블에 deleted_at 추가
ALTER TABLE social_links ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;

COMMENT ON COLUMN social_links.deleted_at IS '삭제 시간 (Soft Delete)';

-- 12. phone_verifications 테이블에 deleted_at 추가
ALTER TABLE phone_verifications ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;

COMMENT ON COLUMN phone_verifications.deleted_at IS '삭제 시간 (Soft Delete)';

-- 13. subscriptions 테이블에 deleted_at 추가
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;

COMMENT ON COLUMN subscriptions.deleted_at IS '삭제 시간 (Soft Delete)';

-- 14. reviews 테이블에 deleted_at 추가
ALTER TABLE reviews ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;

COMMENT ON COLUMN reviews.deleted_at IS '삭제 시간 (Soft Delete)';

-- 15. agency_memberships 테이블에 deleted_at 추가
ALTER TABLE agency_memberships ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;

COMMENT ON COLUMN agency_memberships.deleted_at IS '삭제 시간 (Soft Delete)';

-- 16. notifications 테이블에 deleted_at 추가
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;

COMMENT ON COLUMN notifications.deleted_at IS '삭제 시간 (Soft Delete)';

-- 17. inquiries 테이블에 deleted_at 추가
ALTER TABLE inquiries ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;

COMMENT ON COLUMN inquiries.deleted_at IS '삭제 시간 (Soft Delete)';

-- 18. user_missions 테이블에 deleted_at 추가
ALTER TABLE user_missions ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;

COMMENT ON COLUMN user_missions.deleted_at IS '삭제 시간 (Soft Delete)';

-- 19. email_verifications 테이블에 deleted_at 추가
ALTER TABLE email_verifications ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;

COMMENT ON COLUMN email_verifications.deleted_at IS '삭제 시간 (Soft Delete)';

-- 20. mining_history 테이블에 deleted_at 추가
ALTER TABLE mining_history ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;

COMMENT ON COLUMN mining_history.deleted_at IS '삭제 시간 (Soft Delete)';

-- 21. airdrop_phases 테이블에 deleted_at 추가
ALTER TABLE airdrop_phases ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;

COMMENT ON COLUMN airdrop_phases.deleted_at IS '삭제 시간 (Soft Delete)';

-- 22. airdrop_transfers 테이블에 deleted_at 추가
ALTER TABLE airdrop_transfers ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;

COMMENT ON COLUMN airdrop_transfers.deleted_at IS '삭제 시간 (Soft Delete)';

-- 인덱스 생성 (삭제되지 않은 데이터 조회 성능 향상)
CREATE INDEX IF NOT EXISTS idx_users_deleted_at ON users(deleted_at);
CREATE INDEX IF NOT EXISTS idx_user_wallets_deleted_at ON user_wallets(deleted_at);
CREATE INDEX IF NOT EXISTS idx_internal_transfers_deleted_at ON internal_transfers(deleted_at);
CREATE INDEX IF NOT EXISTS idx_external_transfers_deleted_at ON external_transfers(deleted_at);
CREATE INDEX IF NOT EXISTS idx_swaps_deleted_at ON swaps(deleted_at);
CREATE INDEX IF NOT EXISTS idx_exchanges_deleted_at ON exchanges(deleted_at);
CREATE INDEX IF NOT EXISTS idx_payment_deposits_deleted_at ON payment_deposits(deleted_at);
CREATE INDEX IF NOT EXISTS idx_token_deposits_deleted_at ON token_deposits(deleted_at);
CREATE INDEX IF NOT EXISTS idx_user_bonuses_deleted_at ON user_bonuses(deleted_at);
CREATE INDEX IF NOT EXISTS idx_daily_mining_deleted_at ON daily_mining(deleted_at);
CREATE INDEX IF NOT EXISTS idx_social_links_deleted_at ON social_links(deleted_at);
CREATE INDEX IF NOT EXISTS idx_phone_verifications_deleted_at ON phone_verifications(deleted_at);
CREATE INDEX IF NOT EXISTS idx_subscriptions_deleted_at ON subscriptions(deleted_at);
CREATE INDEX IF NOT EXISTS idx_reviews_deleted_at ON reviews(deleted_at);
CREATE INDEX IF NOT EXISTS idx_agency_memberships_deleted_at ON agency_memberships(deleted_at);
CREATE INDEX IF NOT EXISTS idx_notifications_deleted_at ON notifications(deleted_at);
CREATE INDEX IF NOT EXISTS idx_inquiries_deleted_at ON inquiries(deleted_at);
CREATE INDEX IF NOT EXISTS idx_user_missions_deleted_at ON user_missions(deleted_at);
CREATE INDEX IF NOT EXISTS idx_email_verifications_deleted_at ON email_verifications(deleted_at);
CREATE INDEX IF NOT EXISTS idx_mining_history_deleted_at ON mining_history(deleted_at);
CREATE INDEX IF NOT EXISTS idx_airdrop_phases_deleted_at ON airdrop_phases(deleted_at);
CREATE INDEX IF NOT EXISTS idx_airdrop_transfers_deleted_at ON airdrop_transfers(deleted_at);

