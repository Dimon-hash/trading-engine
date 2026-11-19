package com.example.tradingengine.order.service;

import com.example.tradingengine.account.service.CashLedger;
import com.example.tradingengine.instrument.InstrumentCatalog;
import com.example.tradingengine.matching.OrderBook;
import com.example.tradingengine.matching.OrderExecution;
import com.example.tradingengine.model.Order;
import com.example.tradingengine.model.Side;
import com.example.tradingengine.model.Trade;
import com.example.tradingengine.order.OrderSubmission;
import com.example.tradingengine.order.OrderView;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class OrderOperations {

    private final AtomicLong nextOrderId = new AtomicLong(1);
    private final InstrumentCatalog instrumentCatalog;
    private final CashLedger cashLedger;
    private final OrderRegistry registry;

    OrderOperations(InstrumentCatalog instrumentCatalog, CashLedger cashLedger, OrderRegistry registry) {
        this.instrumentCatalog = instrumentCatalog;
        this.cashLedger = cashLedger;
        this.registry = registry;
    }

    OrderSubmission submit(long accountId, String seccode, Side side, BigDecimal price, long quantity) {
        var instrument = instrumentCatalog.validatePrice(seccode, price);
        Order order = new Order(nextOrderId.getAndIncrement(), accountId, seccode, side, price, quantity);

        synchronized (cashLedger) {
            cashLedger.reserve(instrument, order);
            InstrumentOrders instrumentOrders = registry.getOrCreate(seccode);
            OrderBook book = instrumentOrders.book();
            synchronized (book) {
                List<Trade> trades = settleExecutions(instrumentOrders, book.execute(order));
                instrumentOrders.saveOrder(order);
                return new OrderSubmission(order.id(), trades);
            }
        }
    }

    OrderSubmission changePrice(String seccode, long id, BigDecimal newPrice) {
        instrumentCatalog.validatePrice(seccode, newPrice);
        InstrumentOrders instrumentOrders = registry.require(seccode);
        OrderBook book = instrumentOrders.book();

        synchronized (cashLedger) {
            synchronized (book) {
                Order order = instrumentOrders.requireOrder(id);
                Order updated = order.withPrice(newPrice);
                cashLedger.changePrice(id, newPrice);
                List<OrderExecution> executions = book.reprice(order.side(), id, order.price(), newPrice)
                        .orElseThrow(() -> new IllegalStateException("Заявка с резервом отсутствует в стакане"));

                List<Trade> trades = settleExecutions(instrumentOrders, executions);
                if (order.price().compareTo(newPrice) != 0) {
                    instrumentOrders.saveOrder(updated);
                }
                return new OrderSubmission(id, trades);
            }
        }
    }

    OrderView cancel(String seccode, long id) {
        InstrumentOrders instrumentOrders = registry.require(seccode);
        OrderBook book = instrumentOrders.book();

        synchronized (cashLedger) {
            synchronized (book) {
                Order order = instrumentOrders.requireOrder(id);
                if (!book.cancel(order.side(), id, order.price())) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT, "У заявки нет открытого остатка");
                }

                cashLedger.release(id);
                instrumentOrders.markCancelled(id);
                return OrderView.from(order, 0L, true);
            }
        }
    }

    private List<Trade> settleExecutions(InstrumentOrders instrumentOrders, List<OrderExecution> executions) {
        List<Trade> trades = new ArrayList<>(executions.size());
        for (OrderExecution execution : executions) {
            cashLedger.settle(execution);
            trades.add(execution.trade());
        }
        instrumentOrders.recordTrades(trades);
        return List.copyOf(trades);
    }
}
