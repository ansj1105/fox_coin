-- Default hot wallet thresholds (admin can adjust)
INSERT INTO app_config (config_key, config_value)
VALUES
    ('hot_wallet_min.TRON.FOXYA', '1000'),
    ('hot_wallet_target.TRON.FOXYA', '5000'),
    ('hot_wallet_min.TRON.USDT', '1000'),
    ('hot_wallet_target.TRON.USDT', '5000'),
    ('hot_wallet_min.ETH.USDT', '1000'),
    ('hot_wallet_target.ETH.USDT', '5000'),
    ('hot_wallet_min.ETH.ETH', '10'),
    ('hot_wallet_target.ETH.ETH', '50'),
    ('hot_wallet_min.BTC.BTC', '1'),
    ('hot_wallet_target.BTC.BTC', '5'),
    ('hot_wallet_user_id', '4')
ON CONFLICT (config_key) DO NOTHING;
