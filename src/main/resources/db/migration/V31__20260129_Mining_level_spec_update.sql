-- MINING_AND_LEVEL_SPEC: 레벨별 하루 최대 채굴량(KORI), 일일 영상 시청 수 반영
-- LV1~LV9: daily_max_mining = 1, 2, 3.2, 4.8, 6.5, 8, 9, 9.6, 10 (KORI)
-- daily_max_videos = 5, 7, 9, 12, 15, 18, 20, 22, 24

ALTER TABLE mining_levels ADD COLUMN IF NOT EXISTS daily_max_videos INT NULL;
COMMENT ON COLUMN mining_levels.daily_max_videos IS '레벨별 일일 시청 가능 영상 수';

UPDATE mining_levels SET daily_max_mining = 1,    daily_max_videos = 5  WHERE level = 1;
UPDATE mining_levels SET daily_max_mining = 2,    daily_max_videos = 7  WHERE level = 2;
UPDATE mining_levels SET daily_max_mining = 3.2,  daily_max_videos = 9  WHERE level = 3;
UPDATE mining_levels SET daily_max_mining = 4.8,  daily_max_videos = 12 WHERE level = 4;
UPDATE mining_levels SET daily_max_mining = 6.5,  daily_max_videos = 15 WHERE level = 5;
UPDATE mining_levels SET daily_max_mining = 8,    daily_max_videos = 18 WHERE level = 6;
UPDATE mining_levels SET daily_max_mining = 9,    daily_max_videos = 20 WHERE level = 7;
UPDATE mining_levels SET daily_max_mining = 9.6,  daily_max_videos = 22 WHERE level = 8;
UPDATE mining_levels SET daily_max_mining = 10,   daily_max_videos = 24 WHERE level = 9;
