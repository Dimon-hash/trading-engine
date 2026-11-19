package com.example.tradingengine.order.service;

import com.example.tradingengine.matching.OrderBook;
import com.example.tradingengine.model.Order;
import com.example.tradingengine.model.Side;
import com.example.tradingengine.model.Trade;
import com.example.tradingengine.order.OrderView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class InstrumentOrders {

    private final OrderBook book;
    private final Map<Long, Order> ordersById = new LinkedHashMap<>();
    private final Set<Long> cancelledOrderIds = new HashSet<>();
    private final List<Trade> trades = new ArrayList<>();

    InstrumentOrders(String seccode) {
        this.book = new OrderBook(seccode);
    }

    OrderBook book() {
        return book;
    }

    Order requireOrder(long id) {
        Order order = ordersById.get(id);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Заявка не найдена");
        }
        return order;
    }

    void saveOrder(Order order) {
        ordersById.put(order.id(), order);
    }

    void markCancelled(long id) {
        cancelledOrderIds.add(id);
    }

    void recordTrades(List<Trade> executions) {
        trades.addAll(executions);
    }

    List<OrderView> orders() {
        synchronized (book) {
            Map<Long, Long> remainingById = new HashMap<>();
            for (Side side : Side.values()) {
                for (Order openOrder : book.openOrders(side)) {
                    remainingById.put(openOrder.id(), openOrder.quantity());
                }
            }

            return ordersById.values().stream()
                    .map(order -> OrderView.from(
                            order,
                            remainingById.getOrDefault(order.id(), 0L),
                            cancelledOrderIds.contains(order.id())))
                    .toList();
        }
    }

    List<Trade> trades() {
        synchronized (book) {
            return List.copyOf(trades);
        }
    }
}
