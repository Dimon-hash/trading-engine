package com.example.tradingengine.model;

import java.math.BigDecimal;

public record Trade(
        long id,
        String seccode,
        BigDecimal price,
        long quantity
) {}