package com.example.tradingengine.account.validation;

import com.example.tradingengine.model.Account;
import com.example.tradingengine.model.Instrument;
import com.example.tradingengine.model.Order;
import com.example.tradingengine.model.Side;
import com.example.tradingengine.model.Trade;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class AccountValidation {

    private AccountValidation() {}

    public static void validateNewAccount(Account previous) {
        if (previous != null) {
            throw new IllegalArgumentException("Счёт уже зарегистрирован");
        }
    }

    public static void validateNewPosition(BigInteger quantity, boolean alreadyRegistered) {
        if (quantity.signum() < 0 || alreadyRegistered) {
            throw new IllegalArgumentException("Некорректная или уже зарегистрированная позиция");
        }
    }

    public static void validateNewReserve(Instrument instrument, Order order, boolean alreadyReserved) {
        if (!instrument.seccode().equals(order.seccode())) {
            throw new IllegalArgumentException("Заявка относится к другому инструменту");
        }
        if (alreadyReserved) {
            throw new IllegalStateException("Для заявки уже есть резерв");
        }
    }

    public static void validateCurrency(Account account, Instrument instrument) {
        if (!account.currency().equals(instrument.settlementCurrency())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Валюта счёта не совпадает с валютой расчёта");
        }
    }

    public static void validateSettlement(Instrument buyInstrument, Order buy,
                                          Instrument sellInstrument, Order sell, Trade trade) {
        if (!buyInstrument.equals(sellInstrument)
                || !canSettle(buy, Side.BUY, trade) || !canSettle(sell, Side.SELL, trade)
                || trade.quantity() <= 0 || trade.price().signum() <= 0
                || buy.price().compareTo(trade.price()) < 0 || sell.price().compareTo(trade.price()) > 0) {
            throw new IllegalStateException("Сделка не соответствует резервам заявок");
        }
    }

    public static void validateBalanceChange(BigDecimal amount, BigDecimal balance) {
        if (amount.signum() < 0 && balance.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Недостаточно средств");
        }
    }

    public static void validatePositionQuantity(BigInteger quantity) {
        if (quantity.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Недостаточно бумаг");
        }
    }

    private static boolean canSettle(Order order, Side side, Trade trade) {
        return order.side() == side && order.seccode().equals(trade.seccode())
                && order.quantity() >= trade.quantity();
    }
}
