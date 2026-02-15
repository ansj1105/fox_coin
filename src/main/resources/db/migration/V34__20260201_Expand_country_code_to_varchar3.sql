-- 기타(ETC) 등 3자리 국가 코드 저장 가능하도록 country_code 확장
-- 프론트 SUPPORTED_COUNTRIES에 code='ETC'(3자) 포함, API validation maxLength(3) 허용
ALTER TABLE users ALTER COLUMN country_code TYPE VARCHAR(3);
COMMENT ON COLUMN users.country_code IS '국가 코드 (ISO 3166-1 alpha-2 또는 ETC 등, 예: KR, US, ETC)';
