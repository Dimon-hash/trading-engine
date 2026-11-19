package com.example.tradingengine.order;

import com.example.tradingengine.model.Order;
import com.example.tradingengine.model.Side;

import java.math.BigDecimal;

public record OrderView(
        long id,
        long accountId,
        String seccode,
        Side side,
        BigDecimal price,
        long quantity,
        long remainingQuantity,
        Status status
) {

    public static OrderView from(Order order, long remainingQuantity, boolean cancelled) {
        Status status;
        if (cancelled) {
            status = Status.CANCELLED;
        } else if (remainingQuantity == 0) {
            status = Status.FILLED;
        } else if (remainingQuantity < order.quantity()) {
            status = Status.PARTIALLY_FILLED;
        } else {
            status = Status.OPEN;
        }

        return new OrderView(
                order.id(),
                order.accountId(),
                order.seccode(),
                order.side(),
                order.price(),
                order.quantity(),
                remainingQuantity,
                status
        );
    }

    public enum Status {
        OPEN,
        PARTIALLY_FILLED,
        FILLED,
        CANCELLED
    }
}
