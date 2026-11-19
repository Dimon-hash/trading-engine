package com.example.tradingengine.instrument;

import com.example.tradingengine.instrument.validation.InstrumentValidation;
import com.example.tradingengine.model.Instrument;
import com.example.tradingengine.model.InstrumentType;
import java.math.BigDecimal;
import java.util.Currency;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InstrumentCatalog {
    private final JdbcClient jdbc;

    public InstrumentCatalog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void register(Instrument instrument) {
        jdbc.sql("""
                INSERT INTO instruments (seccode, instrument_type, lot_size, min_step, settlement_currency)
                VALUES (:seccode, :type, :lotSize, :minStep, :currency)
                """)
                .param("seccode", instrument.seccode())
                .param("type", instrument.type().name())
                .param("lotSize", instrument.lotSize())
                .param("minStep", instrument.minStep())
                .param("currency", instrument.settlementCurrency().getCurrencyCode())
                .update();
    }

    public Instrument require(String seccode) {
        return jdbc.sql("""
                SELECT seccode, instrument_type, lot_size, min_step, settlement_currency
                FROM instruments WHERE seccode = :seccode
                """)
                .param("seccode", seccode)
                .query((rs, row) -> new Instrument(rs.getString("seccode"),
                        InstrumentType.valueOf(rs.getString("instrument_type")), rs.getLong("lot_size"),
                        rs.getBigDecimal("min_step"), Currency.getInstance(rs.getString("settlement_currency"))))
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Инструмент не найден"));
    }

    public Instrument validatePrice(String seccode, BigDecimal price) {
        Instrument instrument = require(seccode);
        InstrumentValidation.validatePrice(instrument, price);
        return instrument;
    }
}
