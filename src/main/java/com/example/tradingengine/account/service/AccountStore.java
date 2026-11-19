package com.example.tradingengine.account.service;

import com.example.tradingengine.account.AccountView;
import com.example.tradingengine.model.Account;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Currency;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AccountStore {
    private final JdbcClient jdbc;

    public AccountStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public CashLedger loadAvailable() {
        CashLedger ledger = new CashLedger();
        jdbc.sql("SELECT id, user_id, available_balance, currency FROM accounts")
                .query((rs, row) -> new Account(rs.getLong("id"), rs.getLong("user_id"),
                        rs.getBigDecimal("available_balance"), Currency.getInstance(rs.getString("currency"))))
                .list().forEach(ledger::register);

        jdbc.sql("SELECT account_id, seccode, available_quantity FROM positions").query(rs -> {
            ledger.registerPosition(rs.getLong("account_id"), rs.getString("seccode"),
                    rs.getBigDecimal("available_quantity").toBigIntegerExact());
        });
        return ledger;
    }

    public void saveAvailable(AccountView account, String seccode) {
        jdbc.sql("UPDATE accounts SET available_balance = :balance WHERE id = :id")
                .param("balance", account.availableBalance()).param("id", account.id()).update();

        account.positions().stream().filter(position -> position.seccode().equals(seccode))
                .findFirst().ifPresent(position -> savePosition(account.id(), position));
    }

    private void savePosition(long id, AccountView.Position position) {
        int updated = jdbc.sql("""
                UPDATE positions SET available_quantity = :quantity
                WHERE account_id = :id AND seccode = :code
                """).param("quantity", new BigDecimal(position.availableQuantity()))
                .param("id", id).param("code", position.seccode()).update();
        if (updated == 0) {
            registerPosition(id, position.seccode(), position.availableQuantity());
        }
    }

    public void register(Account account) {
        jdbc.sql("INSERT INTO accounts (id, user_id, currency, available_balance) VALUES (:id, :user, :currency, :balance)")
                .param("id", account.id()).param("user", account.userId())
                .param("currency", account.currency().getCurrencyCode())
                .param("balance", account.balance()).update();
    }

    public void registerPosition(long accountId, String seccode, BigInteger quantity) {
        jdbc.sql("INSERT INTO positions (account_id, seccode, available_quantity) VALUES (:id, :code, :quantity)")
                .param("id", accountId).param("code", seccode)
                .param("quantity", new BigDecimal(quantity)).update();
    }
}
