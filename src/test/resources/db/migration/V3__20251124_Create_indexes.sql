-- Create indexes for better query performance

-- Users table indexes
CREATE INDEX idx_users_login_id ON users(login_id);
CREATE INDEX idx_users_referral_code ON users(referral_code);
CREATE INDEX idx_users_status ON users(status);

-- Currency table indexes
CREATE INDEX idx_currency_code ON currency(code);
CREATE INDEX idx_currency_is_active ON currency(is_active);

-- User Wallets table indexes
CREATE INDEX idx_user_wallets_user_id ON user_wallets(user_id);
CREATE INDEX idx_user_wallets_currency_id ON user_wallets(currency_id);
CREATE INDEX idx_user_wallets_address ON user_wallets(address);
CREATE INDEX idx_user_wallets_user_currency ON user_wallets(user_id, currency_id);
CREATE INDEX idx_user_wallets_status ON user_wallets(status);

-- Wallet Transactions table indexes
CREATE INDEX idx_tx_user ON wallet_transactions(user_id, id);
CREATE INDEX idx_tx_wallet ON wallet_transactions(wallet_id, id);
CREATE INDEX idx_tx_hash ON wallet_transactions(tx_hash);
CREATE INDEX idx_tx_status ON wallet_transactions(status);
CREATE INDEX idx_tx_currency ON wallet_transactions(currency_id);
CREATE INDEX idx_tx_type ON wallet_transactions(tx_type);
CREATE INDEX idx_tx_created_at ON wallet_transactions(created_at);
CREATE INDEX idx_tx_user_created ON wallet_transactions(user_id, created_at DESC);
CREATE INDEX idx_tx_wallet_created ON wallet_transactions(wallet_id, created_at DESC);

-- Wallet Transaction Status Logs table indexes
CREATE INDEX idx_tx_status_tx ON wallet_transaction_status_logs(tx_id);
CREATE INDEX idx_tx_status_created_at ON wallet_transaction_status_logs(created_at);

-- Referral Relations table indexes
CREATE INDEX idx_referral_relations_referrer_id ON referral_relations(referrer_id);
CREATE INDEX idx_referral_relations_referred_id ON referral_relations(referred_id);
CREATE INDEX idx_referral_relations_status ON referral_relations(status);
CREATE INDEX idx_referral_relations_referrer_referred ON referral_relations(referrer_id, referred_id);
CREATE INDEX idx_referral_relations_deleted_at ON referral_relations(deleted_at);

-- Referral Stats Logs table indexes
CREATE INDEX idx_referral_stats_logs_user_id ON referral_stats_logs(user_id);
