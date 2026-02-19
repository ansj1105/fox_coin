INSERT INTO app_config (config_key, config_value)
VALUES
  ('token_contract.TRON.USDT', 'TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t'),
  ('token_decimals.TRON.USDT', '6'),
  ('token_contract.TRON.KORI', 'CHANGE_ME'),
  ('token_decimals.TRON.KORI', '6'),
  ('token_contract.TRON.F_COIN', 'CHANGE_ME'),
  ('token_decimals.TRON.F_COIN', '6')
ON CONFLICT (config_key) DO NOTHING;
