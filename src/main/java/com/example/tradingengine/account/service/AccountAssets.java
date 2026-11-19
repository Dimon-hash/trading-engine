package com.example.tradingengine.account.service;

import com.example.tradingengine.account.validation.AccountValidation;
import com.example.tradingengine.model.Account;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class AccountAssets {

    private final Map<Long, Account> accounts = new HashMap<>();
    private final Map<PositionKey, BigInteger> positions = new HashMap<>();

    void register(Account account) {
        AccountValidation.validateNewAccount(accounts.putIfAbsent(account.id(), account));
    }

    void registerPosition(long accountId, String seccode, BigInteger quantity) {
        requireAccount(accountId);
        PositionKey key = new PositionKey(accountId, seccode);
        AccountValidation.validateNewPosition(quantity, positions.containsKey(key));
        positions.put(key, quantity);
    }

    Account requireAccount(long accountId) {
        Account account = accounts.get(accountId);
        if (account == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Счёт не найден");
        }
        return account;
    }

    Map<String, BigInteger> availablePositions(long accountId) {
        Map<String, BigInteger> available = new TreeMap<>();
        positions.forEach((key, quantity) -> {
            if (key.accountId() == accountId) {
                available.put(key.seccode(), quantity);
            }
        });
        return available;
    }

    void changeBalance(long accountId, BigDecimal amount) {
        save(withBalanceChange(requireAccount(accountId), amount));
    }

    void save(Account account) {
        accounts.put(account.id(), account);
    }

    static Account withBalanceChange(Account account, BigDecimal amount) {
        BigDecimal balance = account.balance().add(amount);
        AccountValidation.validateBalanceChange(amount, balance);
        return new Account(account.id(), account.userId(), balance, account.currency());
    }

    void changePosition(long accountId, String seccode, BigInteger delta) {
        PositionKey key = new PositionKey(accountId, seccode);
        BigInteger updated = positions.getOrDefault(key, BigInteger.ZERO).add(delta);
        AccountValidation.validatePositionQuantity(updated);
        positions.put(key, updated);
    }

    private record PositionKey(long accountId, String seccode) {}
}
