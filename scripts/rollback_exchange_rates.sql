-- Manual rollback for V43__20260215_Create_exchange_rates_table.sql
-- NOTE: Flyway Community doesn't support automatic "down" migrations; keep this script for emergency rollback.

DROP TABLE IF EXISTS exchange_rates;

