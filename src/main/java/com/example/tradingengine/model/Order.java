package com.example.tradingengine.model;

import com.example.tradingengine.model.validation.ModelValidation;
import java.math.BigDecimal;

public record Order(
        long id,
        long accountId,
        String seccode,
        Side side,
        BigDecimal price,
        long quantity
) {
    public Order(long id, String seccode, Side side, BigDecimal price, long quantity) {
        this(id, 0L, seccode, side, price, quantity);
    }

    public Order {
        ModelValidation.validateOrder(seccode, side, price, quantity);
    }
    public Order withQuantity(long quantity) {
        return new Order(id, accountId, seccode, side, price, quantity);
    }

    public Order withPrice(BigDecimal newPrice) {
        return new Order(id, accountId, seccode, side, newPrice, quantity);
    }
}
