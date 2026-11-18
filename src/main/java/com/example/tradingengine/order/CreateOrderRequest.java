package com.example.tradingengine.order;

import com.example.tradingengine.model.Side;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CreateOrderRequest(
        @NotBlank String seccode,
        @NotNull Side side,
        @NotNull @Positive BigDecimal price,
        @Positive long quantity
) {}
