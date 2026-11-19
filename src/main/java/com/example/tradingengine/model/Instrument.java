package com.example.tradingengine.model;

import com.example.tradingengine.model.validation.ModelValidation;
import java.math.BigDecimal;
import java.util.Currency;

public record Instrument(
        String seccode,
        InstrumentType type,
        long lotSize,
        BigDecimal minStep,
        Currency settlementCurrency
) {
    public Instrument(String seccode, InstrumentType type, long lotSize, BigDecimal minStep) {
        this(seccode, type, lotSize, minStep, Currency.getInstance("RUB"));
    }

    public Instrument {
        ModelValidation.validateInstrument(seccode, type, lotSize, minStep, settlementCurrency);
    }
}
