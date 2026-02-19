-- Remove obsolete TRON.FOXYA config keys
DELETE FROM app_config WHERE config_key IN (
    'hot_wallet_min.TRON.FOXYA',
    'hot_wallet_target.TRON.FOXYA',
    'sweep_enabled.TRON.FOXYA',
    'sweep_min_amount.TRON.FOXYA'
);
