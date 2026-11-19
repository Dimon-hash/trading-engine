package com.example.tradingengine.matching;

import com.example.tradingengine.model.Order;
import com.example.tradingengine.model.QuoteSnapshot.PriceLevel;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

final class OrderBookSide {

    private final NavigableMap<BigDecimal, Deque<Order>> ordersByPrice;

    OrderBookSide(Comparator<BigDecimal> pricePriority) {
        ordersByPrice = new TreeMap<>(pricePriority);
    }

    void add(Order order) {
        ordersByPrice.computeIfAbsent(order.price(), price -> new ArrayDeque<>())
                .addLast(order);
    }

    Order bestOrder() {
        var level = ordersByPrice.firstEntry();
        return level == null ? null : level.getValue().getFirst();
    }

    void fillBestOrder(long filledQuantity) {
        var queue = ordersByPrice.firstEntry().getValue();
        Order order = queue.removeFirst();
        long remaining = order.quantity() - filledQuantity;

        if (remaining > 0) {
            queue.addFirst(order.withQuantity(remaining));
        }
        if (queue.isEmpty()) {
            ordersByPrice.pollFirstEntry();
        }
    }

    Order find(long id, BigDecimal price) {
        Deque<Order> queue = ordersByPrice.get(price);
        if (queue == null) {
            return null;
        }

        for (Order order : queue) {
            if (order.id() == id) {
                return order;
            }
        }
        return null;
    }

    void remove(Order order) {
        Deque<Order> queue = ordersByPrice.get(order.price());
        if (queue != null && queue.remove(order) && queue.isEmpty()) {
            ordersByPrice.remove(order.price());
        }
    }

    List<Order> snapshot() {
        return ordersByPrice.values().stream().flatMap(Deque::stream).toList();
    }

    List<PriceLevel> priceLevels() {
        List<PriceLevel> levels = new ArrayList<>(ordersByPrice.size());
        for (var entry : ordersByPrice.entrySet()) {
            BigInteger quantity = BigInteger.ZERO;
            for (Order order : entry.getValue()) {
                quantity = quantity.add(BigInteger.valueOf(order.quantity()));
            }
            levels.add(new PriceLevel(entry.getKey(), quantity));
        }
        return levels;
    }
}
