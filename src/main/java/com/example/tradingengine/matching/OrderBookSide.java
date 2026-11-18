package com.example.tradingengine.matching;

import com.example.tradingengine.model.Order;
import java.math.BigDecimal;
import java.util.ArrayDeque;
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

    /**
     * Списывает исполненные лоты у первой заявки по лучшей цене.
     * При частичном исполнении остаток сохраняет место заявки в очереди.
     * При полном исполнении заявка удаляется. Если очередь опустела,
     * ценовой уровень тоже удаляется.
     *
     * @param filledQuantity количество исполненных лотов
     */
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

    List<Order> snapshot() {
        return ordersByPrice.values().stream().flatMap(Deque::stream).toList();
    }
}