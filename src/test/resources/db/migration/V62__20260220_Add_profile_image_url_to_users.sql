ALTER TABLE users
    ADD COLUMN IF NOT EXISTS profile_image_url VARCHAR(512) NULL;

COMMENT ON COLUMN users.profile_image_url IS '프로필 이미지 URL (LV2 이상 등록 가능)';
