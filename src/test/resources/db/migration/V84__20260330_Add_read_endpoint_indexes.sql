CREATE INDEX IF NOT EXISTS idx_notifications_user_created_active
    ON notifications (user_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_notifications_user_unread_active
    ON notifications (user_id)
    WHERE deleted_at IS NULL AND is_read = FALSE;

CREATE INDEX IF NOT EXISTS idx_user_wallets_watch_addresses_active
    ON user_wallets (currency_id, address, user_id)
    WHERE deleted_at IS NULL AND address IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_referral_revenue_tiers_active_sort
    ON referral_revenue_tiers (is_active, sort_order);
