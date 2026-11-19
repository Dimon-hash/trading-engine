package com.example.tradingengine.model;

import com.example.tradingengine.model.validation.ModelValidation;
import java.math.BigDecimal;
import java.util.Currency;

public record Account(
        long id,
        long userId,
        BigDecimal balance,
        Currency currency
) {
    public Account {
        ModelValidation.validateAccount(balance, currency);
    }
}
