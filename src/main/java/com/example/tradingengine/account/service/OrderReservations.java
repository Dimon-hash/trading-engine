package com.example.tradingengine.account.service;

import com.example.tradingengine.account.validation.AccountValidation;
import com.example.tradingengine.model.Account;
import com.example.tradingengine.model.Instrument;
import com.example.tradingengine.model.Order;
import com.example.tradingengine.model.Side;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class OrderReservations {

    private final AccountAssets assets;
    private final Map<Long, Reservation> byOrderId = new HashMap<>();

    OrderReservations(AccountAssets assets) {
        this.assets = assets;
    }

    void reserve(Instrument instrument, Order order) {
        AccountValidation.validateNewReserve(instrument, order, byOrderId.containsKey(order.id()));
        Account account = assets.requireAccount(order.accountId());
        AccountValidation.validateCurrency(account, instrument);

        var reserve = new Reservation(instrument, order);
        adjustAssets(reserve, -order.quantity());
        byOrderId.put(order.id(), reserve);
    }

    void changePrice(long orderId, BigDecimal newPrice) {
        Reservation reserve = require(orderId);
        Order order = reserve.order();
        if (order.price().compareTo(newPrice) == 0) {
            return;
        }
        Order repriced = order.withPrice(newPrice);
        if (order.side() == Side.BUY) {
            BigDecimal difference = reserve.cost(order.price().subtract(newPrice), order.quantity());
            assets.changeBalance(order.accountId(), difference);
        }
        byOrderId.put(orderId, new Reservation(reserve.instrument(), repriced));
    }

    void release(long orderId) {
        Reservation reserve = require(orderId);
        adjustAssets(reserve, reserve.order().quantity());
        byOrderId.remove(orderId);
    }

    Reservation require(long orderId) {
        Reservation reserve = byOrderId.get(orderId);
        if (reserve == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "У заявки нет открытого остатка");
        }
        return reserve;
    }

    void fill(Reservation reserve, long filledLots) {
        Order order = reserve.order();
        long remainingLots = order.quantity() - filledLots;
        if (remainingLots == 0L) {
            byOrderId.remove(order.id());
        } else {
            byOrderId.put(order.id(), new Reservation(reserve.instrument(), order.withQuantity(remainingLots)));
        }
    }

    Totals totalsFor(long accountId) {
        BigDecimal balance = BigDecimal.ZERO;
        Map<String, BigInteger> positions = new HashMap<>();
        for (Reservation reserve : byOrderId.values()) {
            Order order = reserve.order();
            if (order.accountId() != accountId) {
                continue;
            }
            if (order.side() == Side.BUY) {
                balance = balance.add(reserve.cost(order.price(), order.quantity()));
            } else {
                positions.merge(order.seccode(), reserve.units(order.quantity()), BigInteger::add);
            }
        }
        return new Totals(balance, positions);
    }

    private void adjustAssets(Reservation reserve, long lots) {
        Order order = reserve.order();
        if (order.side() == Side.BUY) {
            assets.changeBalance(order.accountId(), reserve.cost(order.price(), lots));
        } else {
            assets.changePosition(order.accountId(), order.seccode(), reserve.units(lots));
        }
    }

    record Totals(BigDecimal balance, Map<String, BigInteger> positions) {}

    record Reservation(Instrument instrument, Order order) {
        BigInteger units(long lots) {
            return BigInteger.valueOf(instrument.lotSize()).multiply(BigInteger.valueOf(lots));
        }

        BigDecimal cost(BigDecimal price, long lots) {
            return price.multiply(new BigDecimal(units(lots)));
        }
    }
}
