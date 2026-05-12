ALTER TABLE internal_transfers
    ALTER COLUMN transfer_id TYPE VARCHAR(128),
    ALTER COLUMN transfer_type TYPE VARCHAR(64),
    ALTER COLUMN order_number TYPE VARCHAR(128),
    ALTER COLUMN transaction_type TYPE VARCHAR(64);

ALTER TABLE external_transfers
    ALTER COLUMN transfer_id TYPE VARCHAR(128),
    ALTER COLUMN order_number TYPE VARCHAR(128),
    ALTER COLUMN transaction_type TYPE VARCHAR(64);

ALTER TABLE swaps
    ALTER COLUMN swap_id TYPE VARCHAR(128),
    ALTER COLUMN order_number TYPE VARCHAR(128);

ALTER TABLE exchanges
    ALTER COLUMN exchange_id TYPE VARCHAR(128),
    ALTER COLUMN order_number TYPE VARCHAR(128);

ALTER TABLE payment_deposits
    ALTER COLUMN deposit_id TYPE VARCHAR(128),
    ALTER COLUMN order_number TYPE VARCHAR(128);

ALTER TABLE token_deposits
    ALTER COLUMN deposit_id TYPE VARCHAR(128),
    ALTER COLUMN order_number TYPE VARCHAR(128);
