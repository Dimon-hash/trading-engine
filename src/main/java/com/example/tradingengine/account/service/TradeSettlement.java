package com.example.tradingengine.account.service;

import com.example.tradingengine.account.service.OrderReservations.Reservation;
import com.example.tradingengine.account.validation.AccountValidation;
import com.example.tradingengine.matching.OrderExecution;
import com.example.tradingengine.model.Account;
import com.example.tradingengine.model.Trade;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

final class TradeSettlement {

    private final AccountAssets assets;
    private final OrderReservations reservations;
    private final Set<Long> settledTradeIds = new HashSet<>();

    TradeSettlement(AccountAssets assets, OrderReservations reservations) {
        this.assets = assets;
        this.reservations = reservations;
    }

    void settle(OrderExecution execution) {
        Trade trade = execution.trade();
        if (settledTradeIds.contains(trade.id())) {
            return;
        }

        Reservation buy = reservations.require(execution.buyOrderId());
        Reservation sell = reservations.require(execution.sellOrderId());
        AccountValidation.validateSettlement(buy.instrument(), buy.order(), sell.instrument(), sell.order(), trade);

        transferAssets(trade, buy, sell);
        reservations.fill(buy, trade.quantity());
        reservations.fill(sell, trade.quantity());
        settledTradeIds.add(trade.id());
    }

    private void transferAssets(Trade trade, Reservation buy, Reservation sell) {
        Account buyer = assets.requireAccount(buy.order().accountId());
        Account seller = assets.requireAccount(sell.order().accountId());
        BigDecimal paid = buy.cost(trade.price(), trade.quantity());
        BigDecimal refund = buy.cost(buy.order().price(), trade.quantity()).subtract(paid);
        Account updatedBuyer = AccountAssets.withBalanceChange(buyer, refund);
        Account updatedSeller = AccountAssets.withBalanceChange(buyer.id() == seller.id() ? updatedBuyer : seller, paid);

        assets.changePosition(buyer.id(), trade.seccode(), buy.units(trade.quantity()));
        assets.save(updatedBuyer);
        assets.save(updatedSeller);
    }
}
