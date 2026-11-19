CREATE TABLE order_idempotency (
    account_id BIGINT NOT NULL REFERENCES accounts (id),
    idempotency_key VARCHAR(128) NOT NULL,
    seccode TEXT NOT NULL REFERENCES instruments (seccode),
    side TEXT NOT NULL CHECK (side IN ('BUY', 'SELL')),
    price NUMERIC NOT NULL CHECK (price > 0),
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    order_id BIGINT NOT NULL UNIQUE REFERENCES trading_orders (id),
    trades_json TEXT NOT NULL,
    PRIMARY KEY (account_id, idempotency_key)
);
