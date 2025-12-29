-- 미션 정의 테이블
-- Create Missions Table
CREATE TABLE missions (
    id BIGSERIAL NOT NULL,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(500) NOT NULL,
    type VARCHAR(50) NOT NULL,
    required_count INT NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_missions PRIMARY KEY (id),
    CONSTRAINT UK_missions_type UNIQUE (type)
);

COMMENT ON TABLE missions IS '미션 정의 테이블';
COMMENT ON COLUMN missions.id IS 'Sequence ID';
COMMENT ON COLUMN missions.title IS '미션 제목';
COMMENT ON COLUMN missions.description IS '미션 설명';
COMMENT ON COLUMN missions.type IS '미션 유형 (CAMPAIGN, COMMUNITY_POST, LIVE_AD, DAILY_CHECKIN, BROADCAST_TIME)';
COMMENT ON COLUMN missions.required_count IS '완료에 필요한 횟수';
COMMENT ON COLUMN missions.is_active IS '활성화 여부';
COMMENT ON COLUMN missions.created_at IS '생성 시간';
COMMENT ON COLUMN missions.updated_at IS '수정 시간';

-- 사용자별 미션 진행 상황 테이블
-- Create User Missions Table
CREATE TABLE user_missions (
    id BIGSERIAL NOT NULL,
    user_id BIGINT NOT NULL,
    mission_id BIGINT NOT NULL,
    mission_date DATE NOT NULL,
    current_count INT NOT NULL DEFAULT 0,
    reset_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_user_missions PRIMARY KEY (id),
    CONSTRAINT FK_user_missions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT FK_user_missions_mission FOREIGN KEY (mission_id) REFERENCES missions(id) ON DELETE CASCADE,
    CONSTRAINT UK_user_missions_user_mission_date UNIQUE (user_id, mission_id, mission_date)
);

COMMENT ON TABLE user_missions IS '사용자별 미션 진행 상황 테이블';
COMMENT ON COLUMN user_missions.id IS 'Sequence ID';
COMMENT ON COLUMN user_missions.user_id IS '유저 ID';
COMMENT ON COLUMN user_missions.mission_id IS '미션 ID';
COMMENT ON COLUMN user_missions.mission_date IS '미션 날짜';
COMMENT ON COLUMN user_missions.current_count IS '현재 완료한 횟수';
COMMENT ON COLUMN user_missions.reset_at IS '리셋 시간 (다음날 00:00:00)';
COMMENT ON COLUMN user_missions.created_at IS '생성 시간';
COMMENT ON COLUMN user_missions.updated_at IS '수정 시간';

-- 인덱스 생성
CREATE INDEX idx_user_missions_user_id ON user_missions(user_id);
CREATE INDEX idx_user_missions_mission_id ON user_missions(mission_id);
CREATE INDEX idx_user_missions_user_mission_date ON user_missions(user_id, mission_id, mission_date);
CREATE INDEX idx_user_missions_mission_date ON user_missions(mission_date);

-- 기본 미션 데이터 삽입
INSERT INTO missions (title, description, type, required_count) VALUES
('캠페인 참여', '랜덤 통화 2회 참여', 'CAMPAIGN', 2),
('커뮤니티 게시글 작성', '커뮤니티 게시글 1회 작성', 'COMMUNITY_POST', 1),
('라이브 방송 내 광고 시청', '라이브 방송 내 광고 시청 1회', 'LIVE_AD', 1),
('일일 출석체크', '일일 출석체크', 'DAILY_CHECKIN', 1),
('방송 진행 15분 or 청취 15분', '방송 진행 15분 or 청취 15분', 'BROADCAST_TIME', 1);

