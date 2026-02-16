-- Notification dedupe safety indexes
-- 1) Deactivate duplicated active rows before adding unique index
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY user_id, type, related_id
               ORDER BY id DESC
           ) AS rn
    FROM notifications
    WHERE related_id IS NOT NULL
      AND deleted_at IS NULL
)
UPDATE notifications n
SET deleted_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP
FROM ranked r
WHERE n.id = r.id
  AND r.rn > 1;

-- 2) Race-safe dedupe index for event notifications keyed by related_id
CREATE UNIQUE INDEX IF NOT EXISTS uq_notifications_user_type_related_active
    ON notifications(user_id, type, related_id)
    WHERE related_id IS NOT NULL AND deleted_at IS NULL;

-- 3) Read-performance index for date-based duplicate checks
CREATE INDEX IF NOT EXISTS idx_notifications_user_type_created_date_active
    ON notifications(user_id, type, (created_at::date))
    WHERE deleted_at IS NULL;
