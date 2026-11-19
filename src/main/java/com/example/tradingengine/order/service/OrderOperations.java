package com.example.tradingengine.order.service;

import com.example.tradingengine.account.service.AccountStore;
import com.example.tradingengine.account.service.CashLedger;
import com.example.tradingengine.instrument.InstrumentCatalog;
import com.example.tradingengine.matching.OrderBook;
import com.example.tradingengine.matching.OrderExecution;
import com.example.tradingengine.model.Instrument;
import com.example.tradingengine.model.Order;
import com.example.tradingengine.order.CreateOrderRequest;
import com.example.tradingengine.order.OrderSubmission;
import com.example.tradingengine.order.OrderView;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class OrderOperations {
    private final InstrumentCatalog catalog;
    private final AccountStore accounts;
    private final TradingOrderStore orders;
    private final OrderIdempotencyStore idempotency;

    OrderOperations(
            InstrumentCatalog catalog, AccountStore accounts, TradingOrderStore orders,
            OrderIdempotencyStore idempotency
    ) {
        this.catalog = catalog;
        this.accounts = accounts;
        this.orders = orders;
        this.idempotency = idempotency;
    }

    OrderSubmission submit(CreateOrderRequest request, String key) {
        orders.lockWrites();

        if (key == null) {
            return createOrder(request);
        }

        var previous = idempotency.find(request.accountId(), key);
        if (previous.isPresent()) {
            var saved = previous.get();
            if (!saved.matches(request)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Idempotency-Key уже использован для другой заявки");
            }
            return saved.result();
        }

        OrderSubmission result = createOrder(request);
        idempotency.save(key, request, result);
        return result;
    }

    private OrderSubmission createOrder(CreateOrderRequest request) {
        Instrument instrument = catalog.validatePrice(request.seccode(), request.price());
        WorkingBook state = loadBook(instrument);
        Order order = new Order(orders.nextOrderId(), request.accountId(), request.seccode(),
                request.side(), request.price(), request.quantity());
        state.ledger().reserve(instrument, order);
        orders.insert(order);
        return settle(state.ledger(), order, state.book().execute(order));
    }

    OrderSubmission changePrice(String seccode, long id, BigDecimal price) {
        orders.lockWrites();
        Instrument instrument = catalog.validatePrice(seccode, price);
        Order order = orders.requireOpen(seccode, id).order();
        if (order.price().compareTo(price) == 0) {
            return new OrderSubmission(id, List.of());
        }

        WorkingBook state = loadBook(instrument);
        state.ledger().changePrice(id, price);
        List<OrderExecution> executions = state.book().reprice(order.side(), id, order.price(), price)
                .orElseThrow(() -> new IllegalStateException("Заявка отсутствует в восстановленном стакане"));

        orders.changePrice(id, price);
        return settle(state.ledger(), order, executions);
    }

    OrderView cancel(String seccode, long id) {
        orders.lockWrites();
        var stored = orders.requireOpen(seccode, id);
        CashLedger ledger = accounts.loadAvailable();
        ledger.restoreReservation(catalog.require(seccode), stored.openOrder());
        ledger.release(id);
        orders.cancel(id);
        accounts.saveAvailable(ledger.findAccount(stored.order().accountId()), seccode);
        return OrderView.from(stored.order(), 0L, true);
    }

    private WorkingBook loadBook(Instrument instrument) {
        var open = orders.openForInstrument(instrument.seccode());
        CashLedger ledger = accounts.loadAvailable();
        open.forEach(row -> ledger.restoreReservation(instrument, row.openOrder()));
        return new WorkingBook(orders.restoreBook(instrument.seccode(), open), ledger);
    }

    private OrderSubmission settle(CashLedger ledger, Order order, List<OrderExecution> executions) {
        Set<Long> affectedOrders = new HashSet<>();
        affectedOrders.add(order.id());
        for (OrderExecution execution : executions) {
            ledger.settle(execution);
            orders.saveExecution(execution);
            affectedOrders.add(execution.buyOrderId());
            affectedOrders.add(execution.sellOrderId());
        }
        for (long accountId : orders.accountIds(affectedOrders)) {
            accounts.saveAvailable(ledger.findAccount(accountId), order.seccode());
        }
        return new OrderSubmission(order.id(), executions.stream().map(OrderExecution::trade).toList());
    }

    private record WorkingBook(OrderBook book, CashLedger ledger) {}
}
