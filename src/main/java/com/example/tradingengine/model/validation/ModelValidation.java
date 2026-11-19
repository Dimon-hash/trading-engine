package com.example.tradingengine.model.validation;

import com.example.tradingengine.model.InstrumentType;
import com.example.tradingengine.model.Side;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

public final class ModelValidation {

    private ModelValidation() {}

    public static void validateOrder(String seccode, Side side, BigDecimal price, long quantity) {
        Objects.requireNonNull(seccode, "seccode");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(price, "price");
        requireNonBlankSeccode(seccode);
        if (price.signum() <= 0 || quantity <= 0) {
            throw new IllegalArgumentException("Цена и количество должны быть положительными");
        }
    }

    public static void validateInstrument(String seccode, InstrumentType type, long lotSize,
                                          BigDecimal minStep, Currency settlementCurrency) {
        Objects.requireNonNull(seccode, "seccode");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(minStep, "minStep");
        Objects.requireNonNull(settlementCurrency, "settlementCurrency");
        requireNonBlankSeccode(seccode);
        if (lotSize <= 0 || minStep.signum() <= 0) {
            throw new IllegalArgumentException("Размер лота и шаг цены должны быть положительными");
        }
    }

    public static void validateAccount(BigDecimal balance, Currency currency) {
        Objects.requireNonNull(balance, "balance");
        Objects.requireNonNull(currency, "currency");
    }

    private static void requireNonBlankSeccode(String seccode) {
        if (seccode.isBlank()) {
            throw new IllegalArgumentException("Укажи код инструмента");
        }
    }
}
