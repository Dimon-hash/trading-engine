package com.example.tradingengine.account;

import com.example.tradingengine.account.service.CashLedger;
import com.example.tradingengine.matching.OrderExecution;
import com.example.tradingengine.model.Account;
import com.example.tradingengine.model.Instrument;
import com.example.tradingengine.model.InstrumentType;
import com.example.tradingengine.model.Order;
import com.example.tradingengine.model.Side;
import com.example.tradingengine.model.Trade;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CashLedgerTest {
    @Test
    @DisplayName("Расчёт учитывает размер лота и проводит каждую сделку один раз")
    void reservesSettlesAndReleasesCashInLots() {
        var ledger = new CashLedger();
        var rub = Currency.getInstance("RUB");
        ledger.register(new Account(1, 101, new BigDecimal("10000.00"), rub));
        ledger.register(new Account(2, 102, new BigDecimal("0.00"), rub));
        var instrument = new Instrument("DEMO", InstrumentType.SECURITY, 10, new BigDecimal("0.05"));
        var buy = new Order(42, 1, "DEMO", Side.BUY, new BigDecimal("100.10"), 2);
        var sell = new Order(43, 2, "DEMO", Side.SELL, new BigDecimal("100.00"), 1);
        ledger.registerPosition(2, "DEMO", BigInteger.TEN);
        ledger.reserve(instrument, sell);

        ledger.reserve(instrument, buy);
        assertEquals(new BigDecimal("7998.00"), ledger.findAccount(1).availableBalance());
        var error = assertThrows(ResponseStatusException.class,
                () -> ledger.changePrice(42, new BigDecimal("1000.00")));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals(new BigDecimal("7998.00"), ledger.findAccount(1).availableBalance());
        ledger.changePrice(42, new BigDecimal("100.20"));
        var trade = new Trade(7, "DEMO", new BigDecimal("100.00"), 1);
        var execution = new OrderExecution(trade, buy.id(), sell.id());
        ledger.settle(execution);
        ledger.settle(execution);
        assertEquals(new BigDecimal("7998.00"), ledger.findAccount(1).availableBalance());
        ledger.release(42);
        assertEquals(new BigDecimal("9000.00"), ledger.findAccount(1).availableBalance());
        assertEquals(new BigDecimal("1000.00"), ledger.findAccount(2).availableBalance());
        assertEquals(BigInteger.TEN, ledger.findAccount(1).positions().getFirst().availableQuantity());
        assertEquals(BigInteger.ZERO, ledger.findAccount(2).positions().getFirst().reservedQuantity());
    }

    @Test
    @DisplayName("Сделка внутри одного счёта возвращает разницу в цене и зачисляет выручку")
    void creditsBothPriceDifferenceAndSaleProceedsWhenAccountsCoincide() {
        var ledger = new CashLedger();
        ledger.register(new Account(1, 101, new BigDecimal("10000.00"), Currency.getInstance("RUB")));
        var instrument = new Instrument("DEMO", InstrumentType.SECURITY, 10, new BigDecimal("0.05"));
        var buy = new Order(42, 1, "DEMO", Side.BUY, new BigDecimal("100.10"), 1);
        var sell = new Order(43, 1, "DEMO", Side.SELL, new BigDecimal("100.00"), 1);
        ledger.registerPosition(1, "DEMO", BigInteger.TEN);

        ledger.reserve(instrument, buy);
        ledger.reserve(instrument, sell);
        ledger.settle(new OrderExecution(new Trade(7, "DEMO", new BigDecimal("100.00"), 1), 42, 43));

        assertEquals(new BigDecimal("10000.00"), ledger.findAccount(1).availableBalance());
        assertEquals(BigInteger.TEN, ledger.findAccount(1).positions().getFirst().availableQuantity());
    }
}
