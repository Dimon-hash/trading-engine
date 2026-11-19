package com.example.tradingengine.account;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Currency;
import java.util.List;

public record AccountView(
        long id,
        long userId,
        Currency currency,
        BigDecimal availableBalance,
        BigDecimal reservedBalance,
        List<Position> positions
) {
    public record Position(String seccode, BigInteger availableQuantity, BigInteger reservedQuantity) {}
}
