package com.example.tradingengine.order.service;

import com.example.tradingengine.account.AccountView;
import com.example.tradingengine.account.service.AccountStore;
import com.example.tradingengine.account.service.CashLedger;
import com.example.tradingengine.instrument.InstrumentCatalog;
import com.example.tradingengine.model.QuoteSnapshot;
import com.example.tradingengine.model.Side;
import com.example.tradingengine.model.Trade;
import com.example.tradingengine.order.CreateOrderRequest;
import com.example.tradingengine.order.OrderSubmission;
import com.example.tradingengine.order.OrderView;
import com.example.tradingengine.order.validation.OrderValidation;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final InstrumentCatalog catalog;
    private final AccountStore accounts;
    private final TradingOrderStore orders;
    private final OrderOperations operations;

    public OrderService(
            InstrumentCatalog catalog, AccountStore accounts, TradingOrderStore orders,
            OrderIdempotencyStore idempotency
    ) {
        this.catalog = catalog;
        this.accounts = accounts;
        this.orders = orders;
        this.operations = new OrderOperations(catalog, accounts, orders, idempotency);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderSubmission submit(long accountId, String seccode, Side side, BigDecimal price, long quantity) {
        return operations.submit(new CreateOrderRequest(accountId, seccode, side, price, quantity), null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderSubmission submit(CreateOrderRequest request, String key) {
        OrderValidation.validateIdempotencyKey(key);
        return operations.submit(request, key);
    }

    @Transactional
    public OrderSubmission changePrice(String seccode, long id, BigDecimal price) {
        return operations.changePrice(seccode, id, price);
    }

    @Transactional
    public OrderView cancel(String seccode, long id) {
        return operations.cancel(seccode, id);
    }

    public List<OrderView> findOrders(String seccode) {
        return orders.findOrders(seccode).stream().map(TradingOrderStore.StoredOrder::view).toList();
    }

    public QuoteSnapshot findQuotes(String seccode) {
        return orders.restoreBook(seccode, orders.openForInstrument(seccode)).quotes();
    }

    public List<Trade> findTrades(String seccode) {
        return orders.findTrades(seccode);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AccountView findAccount(long id) {
        CashLedger ledger = accounts.loadAvailable();
        for (var row : orders.openForAccount(id)) {
            ledger.restoreReservation(catalog.require(row.order().seccode()), row.openOrder());
        }
        return ledger.findAccount(id);
    }
}
