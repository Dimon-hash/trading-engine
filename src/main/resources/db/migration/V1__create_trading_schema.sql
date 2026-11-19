CREATE TABLE trading_write_lock (
    id INTEGER PRIMARY KEY CHECK (id = 1)
);

INSERT INTO trading_write_lock (id) VALUES (1);

CREATE TABLE instruments (
    seccode TEXT PRIMARY KEY,
    instrument_type TEXT NOT NULL CHECK (instrument_type IN ('SECURITY', 'CURRENCY')),
    lot_size BIGINT NOT NULL CHECK (lot_size > 0),
    min_step NUMERIC NOT NULL CHECK (min_step > 0),
    settlement_currency CHAR(3) NOT NULL
);

CREATE TABLE accounts (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    available_balance NUMERIC NOT NULL
);

CREATE TABLE positions (
    account_id BIGINT NOT NULL REFERENCES accounts (id),
    seccode TEXT NOT NULL REFERENCES instruments (seccode),
    available_quantity NUMERIC NOT NULL CHECK (
        available_quantity >= 0 AND available_quantity = FLOOR(available_quantity)
    ),
    PRIMARY KEY (account_id, seccode)
);

CREATE SEQUENCE trading_order_id_seq;
CREATE SEQUENCE trading_queue_priority_seq;
CREATE SEQUENCE trade_id_seq;

CREATE TABLE trading_orders (
    id BIGINT PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts (id),
    seccode TEXT NOT NULL REFERENCES instruments (seccode),
    side TEXT NOT NULL CHECK (side IN ('BUY', 'SELL')),
    price NUMERIC NOT NULL CHECK (price > 0),
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    remaining_quantity BIGINT NOT NULL CHECK (remaining_quantity BETWEEN 0 AND quantity),
    cancelled BOOLEAN NOT NULL DEFAULT FALSE,
    queue_priority BIGINT NOT NULL DEFAULT nextval('trading_queue_priority_seq'),
    CHECK (NOT cancelled OR remaining_quantity = 0)
);

CREATE INDEX trading_orders_by_instrument ON trading_orders (seccode, queue_priority);
CREATE INDEX trading_orders_by_account ON trading_orders (account_id, remaining_quantity);

CREATE TABLE trades (
    id BIGINT PRIMARY KEY,
    seccode TEXT NOT NULL REFERENCES instruments (seccode),
    buy_order_id BIGINT NOT NULL REFERENCES trading_orders (id),
    sell_order_id BIGINT NOT NULL REFERENCES trading_orders (id),
    price NUMERIC NOT NULL CHECK (price > 0),
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    CHECK (buy_order_id <> sell_order_id)
);

CREATE INDEX trades_by_instrument ON trades (seccode, id);
