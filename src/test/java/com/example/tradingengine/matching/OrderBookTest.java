package com.example.tradingengine.matching;

import com.example.tradingengine.model.Order;
import com.example.tradingengine.model.Side;
import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderBookTest {

    @Test
    void buyExecutesAtRestingPriceAndLeavesSellRemainder() {
        var book = new OrderBook("TEST");
        var sell = new Order(1L, "TEST", Side.SELL, new BigDecimal("100"), 10L);
        var buy = new Order(2L, "TEST", Side.BUY, new BigDecimal("101"), 4L);

        assertTrue(book.submit(sell).isEmpty());

        var trades = book.submit(buy);
        assertEquals(1, trades.size());
        assertEquals(new BigDecimal("100"), trades.getFirst().price());
        assertEquals(4L, trades.getFirst().quantity());
        assertTrue(book.openOrders(Side.BUY).isEmpty());

        var openSells = book.openOrders(Side.SELL);
        assertEquals(1, openSells.size());
        assertEquals(sell.id(), openSells.getFirst().id());
        assertEquals(6L, openSells.getFirst().quantity());
    }

    @Test
    void keepsBothOrdersWhenPricesDoNotCross() {
        var book = new OrderBook("TEST");
        var sell = new Order(1L, "TEST", Side.SELL, new BigDecimal("100"), 10L);
        var buy = new Order(2L, "TEST", Side.BUY, new BigDecimal("99"), 4L);

        book.submit(sell);

        assertTrue(book.submit(buy).isEmpty());
        assertEquals(List.of(sell), book.openOrders(Side.SELL));
        assertEquals(List.of(buy), book.openOrders(Side.BUY));
    }
}
