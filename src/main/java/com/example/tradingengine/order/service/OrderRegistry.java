package com.example.tradingengine.order.service;

import com.example.tradingengine.model.QuoteSnapshot;
import com.example.tradingengine.model.Trade;
import com.example.tradingengine.order.OrderView;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class OrderRegistry {

    private final ConcurrentMap<String, InstrumentOrders> byInstrument = new ConcurrentHashMap<>();

    InstrumentOrders getOrCreate(String seccode) {
        return byInstrument.computeIfAbsent(seccode, InstrumentOrders::new);
    }

    InstrumentOrders require(String seccode) {
        InstrumentOrders instrumentOrders = byInstrument.get(seccode);
        if (instrumentOrders == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Заявка не найдена");
        }
        return instrumentOrders;
    }

    List<OrderView> findOrders(String seccode) {
        InstrumentOrders instrumentOrders = byInstrument.get(seccode);
        return instrumentOrders == null ? List.of() : instrumentOrders.orders();
    }

    QuoteSnapshot findQuotes(String seccode) {
        InstrumentOrders instrumentOrders = byInstrument.get(seccode);
        if (instrumentOrders == null) {
            return new QuoteSnapshot(seccode, List.of(), List.of());
        }
        return instrumentOrders.book().quotes();
    }

    List<Trade> findTrades(String seccode) {
        InstrumentOrders instrumentOrders = byInstrument.get(seccode);
        return instrumentOrders == null ? List.of() : instrumentOrders.trades();
    }
}
