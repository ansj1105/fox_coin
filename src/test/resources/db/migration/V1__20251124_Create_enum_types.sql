-- Note: PostgreSQL에서는 ENUM 대신 VARCHAR와 CHECK 제약조건을 사용하는 것이 더 유연합니다.
-- 필요시 ENUM 타입을 생성할 수 있지만, 여기서는 VARCHAR를 사용합니다.

-- ENUM 타입은 사용하지 않고 VARCHAR로 처리합니다.
-- 애플리케이션 레벨에서 검증하거나 CHECK 제약조건을 추가할 수 있습니다.
