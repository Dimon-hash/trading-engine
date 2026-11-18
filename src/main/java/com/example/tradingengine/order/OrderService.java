package com.example.tradingengine.order;

import com.example.tradingengine.matching.OrderBook;
import com.example.tradingengine.model.Order;
import com.example.tradingengine.model.Side;
import com.example.tradingengine.model.Trade;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final AtomicLong nextOrderId = new AtomicLong(1);
    private final ConcurrentMap<String, OrderBook> books = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<Trade>> tradesBySeccode = new ConcurrentHashMap<>();

    public OrderSubmission submit(String seccode, Side side, BigDecimal price, long quantity) {
        Order order = new Order(nextOrderId.getAndIncrement(), seccode, side, price, quantity);
        OrderBook book = books.computeIfAbsent(order.seccode(), OrderBook::new);

        synchronized (book) {
            List<Trade> trades = book.submit(order);
            tradesBySeccode.computeIfAbsent(seccode, key -> new ArrayList<>())
                    .addAll(trades);
            return new OrderSubmission(order.id(), trades);
        }
    }

    public List<Trade> findTrades(String seccode) {
        OrderBook book = books.get(seccode);
        if (book == null) {
            return List.of();
        }

        synchronized (book) {
            return List.copyOf(tradesBySeccode.getOrDefault(seccode, List.of()));
        }
    }
}
