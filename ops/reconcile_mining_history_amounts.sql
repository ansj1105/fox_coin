BEGIN;

-- Scope:
-- 1) Reconcile mining_history.amount to match actual credited mining recorded in daily_mining.
-- 2) Do not mutate referral reward transfers here. Historical referral payouts depend on
--    point-in-time referral tiers and cannot be recomputed safely from current tables alone.
-- 3) Wallet balances are intentionally left untouched because they already reflect actual credits.

WITH day_totals AS (
    SELECT
        mh.user_id,
        mh.created_at::date AS mining_date,
        SUM(mh.amount) AS history_total,
        COALESCE(dm.mining_amount, 0) AS actual_total
    FROM mining_history mh
    LEFT JOIN daily_mining dm
        ON dm.user_id = mh.user_id
       AND dm.mining_date = mh.created_at::date
       AND dm.deleted_at IS NULL
    WHERE mh.status = 'COMPLETED'
      AND mh.deleted_at IS NULL
    GROUP BY mh.user_id, mh.created_at::date, dm.mining_amount
    HAVING ABS(SUM(mh.amount) - COALESCE(dm.mining_amount, 0)) > 0.000000000000000001
),
zero_days AS (
    SELECT user_id, mining_date
    FROM day_totals
    WHERE actual_total = 0
),
deleted_rows AS (
    UPDATE mining_history mh
       SET deleted_at = CURRENT_TIMESTAMP
      FROM zero_days zd
     WHERE mh.user_id = zd.user_id
       AND mh.created_at::date = zd.mining_date
       AND mh.status = 'COMPLETED'
       AND mh.deleted_at IS NULL
    RETURNING mh.id
),
positive_days AS (
    SELECT user_id, mining_date, history_total, actual_total
    FROM day_totals
    WHERE actual_total > 0
),
ranked_rows AS (
    SELECT
        mh.id,
        mh.user_id,
        mh.created_at::date AS mining_date,
        mh.amount,
        pd.history_total,
        pd.actual_total,
        ROW_NUMBER() OVER (
            PARTITION BY mh.user_id, mh.created_at::date
            ORDER BY mh.created_at DESC, mh.id DESC
        ) AS rn,
        TRUNC((mh.amount / NULLIF(pd.history_total, 0)) * pd.actual_total, 18) AS proportional_amount
    FROM mining_history mh
    JOIN positive_days pd
      ON pd.user_id = mh.user_id
     AND pd.mining_date = mh.created_at::date
    WHERE mh.status = 'COMPLETED'
      AND mh.deleted_at IS NULL
),
distributed_rows AS (
    SELECT
        rr.id,
        CASE
            WHEN rr.rn = 1 THEN rr.actual_total
                - COALESCE((
                    SELECT SUM(rr2.proportional_amount)
                    FROM ranked_rows rr2
                    WHERE rr2.user_id = rr.user_id
                      AND rr2.mining_date = rr.mining_date
                      AND rr2.rn > 1
                ), 0)
            ELSE rr.proportional_amount
        END AS new_amount
    FROM ranked_rows rr
),
updated_rows AS (
    UPDATE mining_history mh
       SET amount = dr.new_amount
      FROM distributed_rows dr
     WHERE mh.id = dr.id
       AND mh.amount <> dr.new_amount
    RETURNING mh.id
),
missing_days AS (
    SELECT
        dm.user_id,
        dm.mining_date,
        dm.mining_amount AS missing_amount
    FROM daily_mining dm
    LEFT JOIN (
        SELECT user_id, created_at::date AS mining_date, COUNT(*) AS history_count
        FROM mining_history
        WHERE status = 'COMPLETED'
          AND deleted_at IS NULL
        GROUP BY user_id, created_at::date
    ) mh
      ON mh.user_id = dm.user_id
     AND mh.mining_date = dm.mining_date
    WHERE dm.deleted_at IS NULL
      AND dm.mining_amount > 0
      AND COALESCE(mh.history_count, 0) = 0
),
inserted_rows AS (
    INSERT INTO mining_history (
        user_id,
        level,
        amount,
        efficiency,
        type,
        status,
        created_at
    )
    SELECT
        md.user_id,
        COALESCE(u.level, 1),
        md.missing_amount,
        NULL,
        'BROADCAST_WATCH',
        'COMPLETED',
        md.mining_date::timestamp + INTERVAL '23 hours 59 minutes 59 seconds'
    FROM missing_days md
    LEFT JOIN users u
      ON u.id = md.user_id
    RETURNING id
),
remaining_gaps AS (
    SELECT
        COALESCE(mh.user_id, dm.user_id) AS user_id,
        COALESCE(mh.d, dm.d) AS mining_date,
        COALESCE(mh.history_total, 0) AS history_total,
        COALESCE(dm.actual_total, 0) AS actual_total,
        COALESCE(dm.actual_total, 0) - COALESCE(mh.history_total, 0) AS missing_delta
    FROM (
        SELECT user_id, created_at::date AS d, SUM(amount) AS history_total
        FROM mining_history
        WHERE status = 'COMPLETED'
          AND deleted_at IS NULL
        GROUP BY user_id, created_at::date
    ) mh
    FULL OUTER JOIN (
        SELECT user_id, mining_date AS d, mining_amount AS actual_total
        FROM daily_mining
        WHERE deleted_at IS NULL
    ) dm
      ON mh.user_id = dm.user_id
     AND mh.d = dm.d
    WHERE COALESCE(dm.actual_total, 0) > COALESCE(mh.history_total, 0)
      AND ABS(COALESCE(dm.actual_total, 0) - COALESCE(mh.history_total, 0)) > 0.000000000000000001
),
adjustment_targets AS (
    SELECT
        mh.id,
        rg.missing_delta,
        ROW_NUMBER() OVER (
            PARTITION BY rg.user_id, rg.mining_date
            ORDER BY mh.created_at DESC, mh.id DESC
        ) AS rn
    FROM remaining_gaps rg
    JOIN mining_history mh
      ON mh.user_id = rg.user_id
     AND mh.created_at::date = rg.mining_date
     AND mh.status = 'COMPLETED'
     AND mh.deleted_at IS NULL
),
adjusted_rows AS (
    UPDATE mining_history mh
       SET amount = mh.amount + at.missing_delta
      FROM adjustment_targets at
     WHERE mh.id = at.id
       AND at.rn = 1
       AND at.missing_delta > 0
    RETURNING mh.id
)
SELECT
    (SELECT COUNT(*) FROM deleted_rows) AS deleted_row_count,
    (SELECT COUNT(*) FROM updated_rows) AS updated_row_count,
    (SELECT COUNT(*) FROM inserted_rows) AS inserted_row_count,
    (SELECT COUNT(*) FROM adjusted_rows) AS adjusted_row_count;

COMMIT;
