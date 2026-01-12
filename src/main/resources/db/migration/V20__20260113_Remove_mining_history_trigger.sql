-- mining_history 테이블에는 updated_at 컬럼이 없으므로 트리거를 삭제합니다.
DROP TRIGGER IF EXISTS update_mining_history_updated_at ON mining_history;

