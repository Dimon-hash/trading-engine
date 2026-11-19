INSERT INTO instruments (seccode, instrument_type, lot_size, min_step, settlement_currency)
VALUES ('DEMO', 'SECURITY', 10, 0.05, 'RUB');

INSERT INTO accounts (id, user_id, currency, available_balance)
VALUES (1, 101, 'RUB', 10000.00), (2, 102, 'RUB', 0.00);

INSERT INTO positions (account_id, seccode, available_quantity) VALUES (2, 'DEMO', 1000);
