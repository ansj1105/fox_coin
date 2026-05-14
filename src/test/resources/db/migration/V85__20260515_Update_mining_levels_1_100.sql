-- KORION level/mining spec 1~100.
-- New metadata columns are nullable so the admin console can fill ads, store, badge, and image later.

ALTER TABLE mining_levels ADD COLUMN IF NOT EXISTS efficiency DECIMAL(10, 2) NULL;
ALTER TABLE mining_levels ADD COLUMN IF NOT EXISTS required_exp DECIMAL(36, 18) NOT NULL DEFAULT 0;
ALTER TABLE mining_levels ADD COLUMN IF NOT EXISTS per_minute_mining DECIMAL(36, 18) NULL;
ALTER TABLE mining_levels ADD COLUMN IF NOT EXISTS expected_days INT NULL;
ALTER TABLE mining_levels ADD COLUMN IF NOT EXISTS daily_max_ads INT NULL;
ALTER TABLE mining_levels ADD COLUMN IF NOT EXISTS store_product_limit INT NULL;
ALTER TABLE mining_levels ADD COLUMN IF NOT EXISTS badge_code VARCHAR(128) NULL;
ALTER TABLE mining_levels ADD COLUMN IF NOT EXISTS photo_url VARCHAR(512) NULL;

COMMENT ON COLUMN mining_levels.level IS '레벨 (1~100)';
COMMENT ON COLUMN mining_levels.efficiency IS '채굴 효율';
COMMENT ON COLUMN mining_levels.required_exp IS '해당 레벨 도달 필요 누적 EXP';
COMMENT ON COLUMN mining_levels.per_minute_mining IS '분당 채굴량';
COMMENT ON COLUMN mining_levels.expected_days IS '예상 달성 기간(일)';
COMMENT ON COLUMN mining_levels.daily_max_ads IS '레벨별 일일 광고 시청 가능 횟수';
COMMENT ON COLUMN mining_levels.store_product_limit IS '오프라인페이 스토어 상품 개설 가능 개수';
COMMENT ON COLUMN mining_levels.badge_code IS '레벨 뱃지 코드';
COMMENT ON COLUMN mining_levels.photo_url IS '레벨 사진 URL';

DO $$
DECLARE
    daily_max NUMERIC[] := ARRAY[
        1.00,1.09,1.18,1.27,1.36,1.45,1.55,1.64,1.73,1.82,
        1.91,2.00,2.09,2.18,2.27,2.36,2.45,2.55,2.64,2.73,
        2.82,2.91,3.00,3.09,3.18,3.27,3.36,3.45,3.55,3.64,
        3.73,3.82,3.91,4.00,4.09,4.18,4.27,4.36,4.45,4.55,
        4.64,4.73,4.82,4.91,5.00,5.09,5.18,5.27,5.36,5.45,
        5.55,5.64,5.73,5.82,5.91,6.00,6.09,6.18,6.27,6.36,
        6.45,6.55,6.64,6.73,6.82,6.91,7.00,7.09,7.18,7.27,
        7.36,7.45,7.55,7.64,7.73,7.82,7.91,8.00,8.09,8.18,
        8.27,8.36,8.45,8.55,8.64,8.73,8.82,8.91,9.00,9.09,
        9.18,9.27,9.36,9.45,9.55,9.64,9.73,9.82,9.91,10.00
    ];
    required_exp NUMERIC[] := ARRAY[
        0,4,9,13,18,24,29,35,42,48,
        55,62,70,77,85,94,102,111,121,130,
        140,151,161,172,184,195,207,220,232,245,
        260,274,288,303,318,334,349,365,382,398,
        416,433,450,469,487,506,525,544,564,584,
        605,626,647,669,691,714,736,760,783,807,
        832,856,881,907,932,958,985,1011,1038,1066,
        1093,1121,1150,1178,1207,1237,1266,1296,1327,1357,
        1388,1420,1451,1483,1516,1548,1581,1615,1648,1682,
        1717,1751,1786,1822,1857,1893,1930,1966,2003,2040
    ];
    per_min NUMERIC[] := ARRAY[
        0.000694,0.000758,0.000821,0.000884,0.000947,0.001010,0.001073,0.001136,0.001199,0.001263,
        0.001326,0.001389,0.001452,0.001515,0.001578,0.001642,0.001705,0.001768,0.001831,0.001894,
        0.001957,0.002020,0.002083,0.002146,0.002210,0.002273,0.002336,0.002399,0.002462,0.002525,
        0.002588,0.002652,0.002715,0.002778,0.002841,0.002904,0.002967,0.003030,0.003093,0.003157,
        0.003220,0.003283,0.003346,0.003409,0.003472,0.003535,0.003598,0.003662,0.003725,0.003788,
        0.003851,0.003914,0.003977,0.004040,0.004103,0.004167,0.004230,0.004293,0.004356,0.004419,
        0.004482,0.004545,0.004608,0.004672,0.004735,0.004798,0.004861,0.004924,0.004987,0.005050,
        0.005114,0.005177,0.005240,0.005303,0.005366,0.005429,0.005492,0.005556,0.005619,0.005682,
        0.005745,0.005808,0.005871,0.005934,0.005998,0.006061,0.006124,0.006187,0.006250,0.006313,
        0.006376,0.006439,0.006503,0.006566,0.006629,0.006692,0.006755,0.006818,0.006881,0.006944
    ];
    expected_days INT[] := ARRAY[
        0,4,9,12,16,20,24,28,32,35,
        39,43,46,50,53,57,60,64,67,71,
        74,78,81,84,88,91,95,98,101,105,
        108,111,115,118,121,125,128,131,135,138,
        141,144,148,151,154,157,161,164,167,170,
        174,177,180,183,186,190,193,196,199,202,
        206,209,212,215,218,221,225,228,231,234,
        237,240,244,247,250,253,256,259,263,266,
        269,272,275,278,281,285,288,291,294,297,
        300,304,307,310,313,316,319,323,326,329
    ];
    i INT;
BEGIN
    FOR i IN 1..100 LOOP
        INSERT INTO mining_levels (
            level,
            daily_max_mining,
            efficiency,
            required_exp,
            per_minute_mining,
            expected_days,
            daily_max_ads,
            store_product_limit,
            badge_code,
            photo_url,
            updated_at
        )
        VALUES (
            i,
            daily_max[i],
            daily_max[i],
            required_exp[i],
            per_min[i],
            expected_days[i],
            ((i - 1) / 5) + 5,
            GREATEST(0, i - 1),
            NULL,
            NULL,
            NOW()
        )
        ON CONFLICT (level) DO UPDATE SET
            daily_max_mining = EXCLUDED.daily_max_mining,
            efficiency = EXCLUDED.efficiency,
            required_exp = EXCLUDED.required_exp,
            per_minute_mining = EXCLUDED.per_minute_mining,
            expected_days = EXCLUDED.expected_days,
            daily_max_ads = ((i - 1) / 5) + 5,
            store_product_limit = GREATEST(0, i - 1),
            badge_code = NULL,
            photo_url = NULL,
            updated_at = NOW();
    END LOOP;

    IF to_regclass('public.mining_level_limits') IS NOT NULL THEN
        FOR i IN 1..100 LOOP
            INSERT INTO mining_level_limits (level, daily_limit, updated_at)
            VALUES (i, daily_max[i], NOW())
            ON CONFLICT (level) DO UPDATE SET
                daily_limit = EXCLUDED.daily_limit,
                updated_at = NOW();
        END LOOP;
    END IF;
END $$;
