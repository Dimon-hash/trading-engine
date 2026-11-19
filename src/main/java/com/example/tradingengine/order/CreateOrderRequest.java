package com.example.tradingengine.order;

import com.example.tradingengine.model.Side;
import java.math.BigDecimal;

public record CreateOrderRequest(
        long accountId,
        String seccode,
        Side side,
        BigDecimal price,
        long quantity
) {}
