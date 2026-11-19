package com.example.tradingengine.account.service;

import com.example.tradingengine.account.AccountView;
import com.example.tradingengine.matching.OrderExecution;
import com.example.tradingengine.model.Account;
import com.example.tradingengine.model.Instrument;
import com.example.tradingengine.model.Order;
import java.math.BigDecimal;
import java.math.BigInteger;

public class CashLedger {

    private final AccountAssets assets = new AccountAssets();
    private final OrderReservations reservations = new OrderReservations(assets);
    private final TradeSettlement settlement = new TradeSettlement(assets, reservations);

    public synchronized void register(Account account) {
        assets.register(account);
    }

    public synchronized void registerPosition(long accountId, String seccode, BigInteger quantity) {
        assets.registerPosition(accountId, seccode, quantity);
    }

    public synchronized AccountView findAccount(long accountId) {
        Account account = assets.requireAccount(accountId);
        var reserved = reservations.totalsFor(accountId);
        var positions = assets.availablePositions(accountId).entrySet().stream()
                .map(entry -> new AccountView.Position(entry.getKey(), entry.getValue(),
                        reserved.positions().getOrDefault(entry.getKey(), BigInteger.ZERO)))
                .toList();
        return new AccountView(account.id(), account.userId(), account.currency(),
                account.balance(), reserved.balance(), positions);
    }

    public synchronized void reserve(Instrument instrument, Order order) {
        reservations.reserve(instrument, order);
    }

    public synchronized void changePrice(long orderId, BigDecimal newPrice) {
        reservations.changePrice(orderId, newPrice);
    }

    public synchronized void settle(OrderExecution execution) {
        settlement.settle(execution);
    }

    public synchronized void release(long orderId) {
        reservations.release(orderId);
    }
}
