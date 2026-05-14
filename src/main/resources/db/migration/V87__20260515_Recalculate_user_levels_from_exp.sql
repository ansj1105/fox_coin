-- Adjust direct-referral EXP from the old +0.5 policy to +1, then recalculate user levels.
-- Run after V85 so level 1~100 thresholds, ad limits, and store limits are already present.

DO $$
DECLARE
    referral_exp_adjusted_count INTEGER := 0;
    updated_count INTEGER := 0;
BEGIN
    CREATE TABLE IF NOT EXISTS user_exp_policy_adjustments (
        id BIGSERIAL PRIMARY KEY,
        policy_code VARCHAR(128) NOT NULL,
        user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        referral_count INT NOT NULL DEFAULT 0,
        exp_delta NUMERIC(36, 18) NOT NULL DEFAULT 0,
        created_at TIMESTAMP NOT NULL DEFAULT NOW(),
        CONSTRAINT ux_user_exp_policy_adjustments_policy_user UNIQUE (policy_code, user_id)
    );

    WITH referral_adjustments AS (
        SELECT
            rr.referrer_id AS user_id,
            COUNT(*)::INT AS referral_count,
            (COUNT(*)::NUMERIC * 0.5)::NUMERIC(36, 18) AS exp_delta
        FROM referral_relations rr
        JOIN users u ON u.id = rr.referrer_id
        WHERE rr.level = 1
          AND rr.deleted_at IS NULL
          AND COALESCE(rr.status, 'ACTIVE') = 'ACTIVE'
          AND u.deleted_at IS NULL
          AND COALESCE(u.status, 'ACTIVE') <> 'DELETED'
        GROUP BY rr.referrer_id
    ),
    inserted_adjustments AS (
        INSERT INTO user_exp_policy_adjustments (policy_code, user_id, referral_count, exp_delta)
        SELECT
            'DIRECT_REFERRAL_EXP_0_5_TO_1_20260515',
            user_id,
            referral_count,
            exp_delta
        FROM referral_adjustments
        WHERE exp_delta > 0
        ON CONFLICT (policy_code, user_id) DO NOTHING
        RETURNING user_id, exp_delta
    ),
    adjusted_users AS (
        UPDATE users u
        SET exp = COALESCE(u.exp, 0) + inserted_adjustments.exp_delta,
            updated_at = NOW()
        FROM inserted_adjustments
        WHERE u.id = inserted_adjustments.user_id
        RETURNING 1
    )
    SELECT COUNT(*) INTO referral_exp_adjusted_count FROM adjusted_users;

    IF to_regclass('public.mining_levels') IS NOT NULL
        AND EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'mining_levels'
              AND column_name = 'daily_max_videos'
        )
        AND EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'mining_levels'
              AND column_name = 'daily_max_ads'
        ) THEN
        UPDATE mining_levels
        SET daily_max_videos = daily_max_ads,
            updated_at = NOW()
        WHERE daily_max_ads IS NOT NULL
          AND COALESCE(daily_max_videos, -1) <> daily_max_ads;
    END IF;

    WITH computed AS (
        SELECT
            u.id,
            COALESCE(u.level, 1) AS old_level,
            COALESCE(best.level, 1) AS new_level
        FROM users u
        LEFT JOIN LATERAL (
            SELECT ml.level
            FROM mining_levels ml
            WHERE COALESCE(u.exp, 0) >= COALESCE(ml.required_exp, 0)
            ORDER BY ml.level DESC
            LIMIT 1
        ) best ON TRUE
        WHERE COALESCE(u.status, 'ACTIVE') <> 'DELETED'
          AND u.deleted_at IS NULL
    ),
    changed AS (
        UPDATE users u
        SET level = computed.new_level,
            updated_at = NOW()
        FROM computed
        WHERE u.id = computed.id
          AND computed.old_level <> computed.new_level
        RETURNING 1
    )
    SELECT COUNT(*) INTO updated_count FROM changed;

    RAISE NOTICE 'Adjusted direct-referral EXP and recalculated levels. referral_exp_adjusted_count=%, level_updated_count=%',
        referral_exp_adjusted_count,
        updated_count;
END $$;
