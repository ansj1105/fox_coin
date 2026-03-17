CREATE TABLE virtual_wallet_mappings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    network VARCHAR(20) NOT NULL,
    hot_wallet_address VARCHAR(255) NOT NULL,
    virtual_address VARCHAR(255) NOT NULL,
    owner_address VARCHAR(255),
    mapping_seed VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL
);

CREATE UNIQUE INDEX uk_virtual_wallet_mappings_user_network
    ON virtual_wallet_mappings(user_id, network)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_virtual_wallet_mappings_owner_network
    ON virtual_wallet_mappings(owner_address, network)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_virtual_wallet_mappings_virtual_network
    ON virtual_wallet_mappings(virtual_address, network)
    WHERE deleted_at IS NULL;
