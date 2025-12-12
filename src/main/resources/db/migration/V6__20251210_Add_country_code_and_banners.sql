-- 사용자 국가 코드 추가
ALTER TABLE users ADD COLUMN IF NOT EXISTS country_code VARCHAR(2) NULL;

COMMENT ON COLUMN users.country_code IS '국가 코드 (ISO 3166-1 alpha-2, 예: KR, US, JP)';

-- 배너 광고 테이블
CREATE TABLE banners (
    id BIGSERIAL NOT NULL,
    title VARCHAR(255) NOT NULL,
    image_url TEXT NOT NULL,
    link_url TEXT NULL,
    position VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    click_count BIGINT DEFAULT 0 NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_banners PRIMARY KEY (id)
);

COMMENT ON TABLE banners IS '배너 광고 테이블';
COMMENT ON COLUMN banners.id IS 'Sequence ID';
COMMENT ON COLUMN banners.title IS '배너 제목';
COMMENT ON COLUMN banners.image_url IS '배너 이미지 URL';
COMMENT ON COLUMN banners.link_url IS '배너 클릭 시 이동할 URL';
COMMENT ON COLUMN banners.position IS '배너 위치 (RANKING_TOP, DASHBOARD_TOP 등)';
COMMENT ON COLUMN banners.is_active IS '활성화 여부';
COMMENT ON COLUMN banners.start_date IS '시작 날짜';
COMMENT ON COLUMN banners.end_date IS '종료 날짜';
COMMENT ON COLUMN banners.click_count IS '클릭 횟수';

-- 배너 클릭 이벤트 테이블
CREATE TABLE banner_clicks (
    id BIGSERIAL NOT NULL,
    banner_id BIGINT NOT NULL,
    user_id BIGINT NULL,
    clicked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    ip_address VARCHAR(45) NULL,
    user_agent TEXT NULL,
    CONSTRAINT PK_banner_clicks PRIMARY KEY (id),
    CONSTRAINT FK_banner_clicks_banner FOREIGN KEY (banner_id) REFERENCES banners(id) ON DELETE CASCADE,
    CONSTRAINT FK_banner_clicks_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

COMMENT ON TABLE banner_clicks IS '배너 클릭 이벤트 테이블';
COMMENT ON COLUMN banner_clicks.id IS 'Sequence ID';
COMMENT ON COLUMN banner_clicks.banner_id IS '배너 ID';
COMMENT ON COLUMN banner_clicks.user_id IS '사용자 ID (비로그인 사용자는 NULL)';
COMMENT ON COLUMN banner_clicks.clicked_at IS '클릭 시간';
COMMENT ON COLUMN banner_clicks.ip_address IS 'IP 주소';
COMMENT ON COLUMN banner_clicks.user_agent IS 'User Agent';

-- Create indexes
CREATE INDEX IDX_users_country_code ON users(country_code);
CREATE INDEX IDX_banners_position ON banners(position);
CREATE INDEX IDX_banners_active ON banners(is_active);
CREATE INDEX IDX_banners_dates ON banners(start_date, end_date);
CREATE INDEX IDX_banner_clicks_banner ON banner_clicks(banner_id);
CREATE INDEX IDX_banner_clicks_user ON banner_clicks(user_id);
CREATE INDEX IDX_banner_clicks_clicked_at ON banner_clicks(clicked_at DESC);

-- Create trigger for updated_at
CREATE TRIGGER update_banners_updated_at BEFORE UPDATE ON banners
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

