-- ==========================================
-- Foxya Coin Service - 초기 데이터베이스 설정
-- ==========================================
-- Docker 컨테이너 최초 실행 시 자동으로 실행됩니다.

-- 확장 기능 활성화
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 기본 설정
SET timezone = 'Asia/Seoul';

-- 데이터베이스 설정
ALTER DATABASE foxya_coin SET timezone TO 'Asia/Seoul';

-- 알림 (실제 테이블은 Flyway 마이그레이션으로 생성)
DO $$
BEGIN
    RAISE NOTICE 'Database initialized. Tables will be created by Flyway migration.';
END $$;

