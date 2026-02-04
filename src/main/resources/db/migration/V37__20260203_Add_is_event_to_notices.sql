-- 공지사항: 팝업 공지(EVENT) 구분용 컬럼 (coin_system_flyway V36 기준)
-- is_event: true=팝업/이벤트 공지, false=일반 공지
ALTER TABLE notices ADD COLUMN IF NOT EXISTS is_event BOOLEAN NOT NULL DEFAULT false;
COMMENT ON COLUMN notices.is_event IS '팝업 공지 여부 (true: EVENT 게시판/팝업, false: 일반 공지)';
