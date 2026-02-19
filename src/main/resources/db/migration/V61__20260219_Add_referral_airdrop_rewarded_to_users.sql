ALTER TABLE users
    ADD COLUMN IF NOT EXISTS referral_airdrop_rewarded BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN users.referral_airdrop_rewarded IS '추천인 등록 에어드랍 1회 지급 여부 (false=미지급, true=지급완료)';
