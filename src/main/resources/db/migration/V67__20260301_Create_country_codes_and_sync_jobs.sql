-- Expand users.country_code to allow ETC(3) and globally managed country codes.
ALTER TABLE users
    ALTER COLUMN country_code TYPE VARCHAR(3);

CREATE TABLE IF NOT EXISTS country_codes (
    code VARCHAR(3) NOT NULL,
    iso2_code VARCHAR(2) NULL,
    iso3_code VARCHAR(3) NULL,
    name_en VARCHAR(120) NOT NULL,
    name_ko VARCHAR(120) NULL,
    flag VARCHAR(16) NULL,
    sort_order INTEGER NOT NULL DEFAULT 9999,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    source VARCHAR(32) NOT NULL DEFAULT 'JAVA_LOCALE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT PK_country_codes PRIMARY KEY (code)
);

COMMENT ON TABLE country_codes IS '회원가입/프로필에서 사용하는 국가코드 마스터';
COMMENT ON COLUMN country_codes.code IS '국가 코드(ISO 3166-1 alpha-2 또는 ETC)';
COMMENT ON COLUMN country_codes.iso2_code IS 'ISO 3166-1 alpha-2';
COMMENT ON COLUMN country_codes.iso3_code IS 'ISO 3166-1 alpha-3';
COMMENT ON COLUMN country_codes.name_en IS '영문 국가명';
COMMENT ON COLUMN country_codes.name_ko IS '한글 국가명';
COMMENT ON COLUMN country_codes.flag IS '국기 이모지';
COMMENT ON COLUMN country_codes.sort_order IS '정렬 우선순위 (낮을수록 우선)';
COMMENT ON COLUMN country_codes.is_active IS '활성 여부';
COMMENT ON COLUMN country_codes.source IS '데이터 소스(SEED/JAVA_LOCALE/EXTERNAL)';

CREATE INDEX IF NOT EXISTS idx_country_codes_active_sort
    ON country_codes(is_active, sort_order, code);

-- Keep backward compatibility with the current frontend defaults while scheduler fills full world list.
INSERT INTO country_codes (code, iso2_code, iso3_code, name_en, name_ko, flag, sort_order, is_active, source)
VALUES
    ('KR', 'KR', 'KOR', 'Korea (South)', '대한민국', '🇰🇷', 10, TRUE, 'SEED'),
    ('US', 'US', 'USA', 'United States', '미국', '🇺🇸', 20, TRUE, 'SEED'),
    ('JP', 'JP', 'JPN', 'Japan', '일본', '🇯🇵', 30, TRUE, 'SEED'),
    ('CN', 'CN', 'CHN', 'China', '중국', '🇨🇳', 40, TRUE, 'SEED'),
    ('GB', 'GB', 'GBR', 'United Kingdom', '영국', '🇬🇧', 50, TRUE, 'SEED'),
    ('FR', 'FR', 'FRA', 'France', '프랑스', '🇫🇷', 60, TRUE, 'SEED'),
    ('DE', 'DE', 'DEU', 'Germany', '독일', '🇩🇪', 70, TRUE, 'SEED'),
    ('IT', 'IT', 'ITA', 'Italy', '이탈리아', '🇮🇹', 80, TRUE, 'SEED'),
    ('ES', 'ES', 'ESP', 'Spain', '스페인', '🇪🇸', 90, TRUE, 'SEED'),
    ('CA', 'CA', 'CAN', 'Canada', '캐나다', '🇨🇦', 100, TRUE, 'SEED'),
    ('AU', 'AU', 'AUS', 'Australia', '호주', '🇦🇺', 110, TRUE, 'SEED'),
    ('BR', 'BR', 'BRA', 'Brazil', '브라질', '🇧🇷', 120, TRUE, 'SEED'),
    ('IN', 'IN', 'IND', 'India', '인도', '🇮🇳', 130, TRUE, 'SEED'),
    ('NG', 'NG', 'NGA', 'Nigeria', '나이지리아', '🇳🇬', 140, TRUE, 'SEED'),
    ('RU', 'RU', 'RUS', 'Russia', '러시아', '🇷🇺', 150, TRUE, 'SEED'),
    ('MX', 'MX', 'MEX', 'Mexico', '멕시코', '🇲🇽', 160, TRUE, 'SEED'),
    ('ID', 'ID', 'IDN', 'Indonesia', '인도네시아', '🇮🇩', 170, TRUE, 'SEED'),
    ('TH', 'TH', 'THA', 'Thailand', '태국', '🇹🇭', 180, TRUE, 'SEED'),
    ('VN', 'VN', 'VNM', 'Vietnam', '베트남', '🇻🇳', 190, TRUE, 'SEED'),
    ('PH', 'PH', 'PHL', 'Philippines', '필리핀', '🇵🇭', 200, TRUE, 'SEED'),
    ('MY', 'MY', 'MYS', 'Malaysia', '말레이시아', '🇲🇾', 210, TRUE, 'SEED'),
    ('SG', 'SG', 'SGP', 'Singapore', '싱가포르', '🇸🇬', 220, TRUE, 'SEED'),
    ('TW', 'TW', 'TWN', 'Taiwan', '대만', '🇹🇼', 230, TRUE, 'SEED'),
    ('HK', 'HK', 'HKG', 'Hong Kong', '홍콩', '🇭🇰', 240, TRUE, 'SEED'),
    ('ETC', NULL, NULL, 'Other (ETC)', '기타', '🏳️', 9998, TRUE, 'SEED')
ON CONFLICT (code) DO UPDATE
SET
    iso2_code = EXCLUDED.iso2_code,
    iso3_code = EXCLUDED.iso3_code,
    name_en = EXCLUDED.name_en,
    name_ko = EXCLUDED.name_ko,
    flag = EXCLUDED.flag,
    sort_order = EXCLUDED.sort_order,
    is_active = EXCLUDED.is_active,
    source = EXCLUDED.source,
    updated_at = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS country_code_sync_jobs (
    job_name VARCHAR(64) NOT NULL,
    last_synced_at TIMESTAMP NULL,
    last_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT PK_country_code_sync_jobs PRIMARY KEY (job_name),
    CONSTRAINT CK_country_code_sync_jobs_status CHECK (last_status IN ('PENDING', 'SUCCESS', 'FAILED'))
);

COMMENT ON TABLE country_code_sync_jobs IS '국가코드 마스터 동기화 배치 상태';
COMMENT ON COLUMN country_code_sync_jobs.job_name IS '배치 잡 이름';
COMMENT ON COLUMN country_code_sync_jobs.last_synced_at IS '최근 성공/실패 수행 시각';
COMMENT ON COLUMN country_code_sync_jobs.last_status IS '최근 수행 상태(PENDING/SUCCESS/FAILED)';
COMMENT ON COLUMN country_code_sync_jobs.total_count IS '최근 동기화 건수';
COMMENT ON COLUMN country_code_sync_jobs.error_message IS '최근 실패 메시지';

CREATE INDEX IF NOT EXISTS idx_country_code_sync_jobs_status
    ON country_code_sync_jobs(last_status, last_synced_at);

INSERT INTO country_code_sync_jobs (job_name, last_status, total_count)
VALUES ('signup_country_codes', 'PENDING', 0)
ON CONFLICT (job_name) DO NOTHING;
