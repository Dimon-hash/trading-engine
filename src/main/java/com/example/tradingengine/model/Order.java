package com.example.tradingengine.model;


import java.math.BigDecimal;
import java.util.Objects;

public record Order(
        long id,
        String seccode,
        Side side,
        BigDecimal price,
        long quantity
) {
    public Order {
        Objects.requireNonNull(seccode, "seccode");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(price, "price");

        if (seccode.isBlank()) {
            throw new IllegalArgumentException("Укажи код инструмента");
        }
        if (price.signum() <= 0 || quantity <= 0) {
            throw new IllegalArgumentException("Цена и количество должны быть положительными");
        }
    }
    public Order withQuantity(long quantity) {
        return new Order(id, seccode, side, price, quantity);
    }
}
