package com.example.tradingengine.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

public record QuoteSnapshot(String seccode, List<PriceLevel> bids, List<PriceLevel> asks) {
    public QuoteSnapshot {
        bids = List.copyOf(bids);
        asks = List.copyOf(asks);
    }

    public record PriceLevel(BigDecimal price, BigInteger quantity) {}
}
