package com.example.tradingengine.account;

import com.example.tradingengine.account.service.CashLedger;
import com.example.tradingengine.model.Account;
import java.util.Currency;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration(proxyBeanMethods = false)
class CashLedgerConfiguration {

    @Bean
    @DependsOnDatabaseInitialization
    CashLedger cashLedger(JdbcClient jdbc) {
        CashLedger ledger = new CashLedger();
        jdbc.sql("SELECT id, user_id, available_balance, currency FROM accounts")
                .query((rs, row) -> new Account(
                        rs.getLong("id"),
                        rs.getLong("user_id"),
                        rs.getBigDecimal("available_balance"),
                        Currency.getInstance(rs.getString("currency"))))
                .list()
                .forEach(ledger::register);

        jdbc.sql("SELECT account_id, seccode, available_quantity FROM positions")
                .query(rs -> {
                    ledger.registerPosition(rs.getLong("account_id"), rs.getString("seccode"),
                            rs.getBigDecimal("available_quantity").toBigIntegerExact());
                });
        return ledger;
    }
}
