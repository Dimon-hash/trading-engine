package com.example.tradingengine.matching;

import com.example.tradingengine.model.Order;
import com.example.tradingengine.model.QuoteSnapshot.PriceLevel;
import com.example.tradingengine.model.Side;
import com.example.tradingengine.model.Trade;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderBookTest {

    @Test
    @DisplayName("Покупка исполняется по цене заявки в стакане, остаток продажи сохраняется")
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
    @DisplayName("Обе заявки остаются в стакане, пока цены не пересекаются")
    void keepsBothOrdersWhenPricesDoNotCross() {
        var book = new OrderBook("TEST");
        var sell = new Order(1L, "TEST", Side.SELL, new BigDecimal("100"), 10L);
        var buy = new Order(2L, "TEST", Side.BUY, new BigDecimal("99"), 4L);

        book.submit(sell);

        assertTrue(book.submit(buy).isEmpty());
        assertEquals(List.of(sell), book.openOrders(Side.SELL));
        assertEquals(List.of(buy), book.openOrders(Side.BUY));
    }

    @Test
    @DisplayName("Одинаковая цена с разным числом знаков сохраняет место в очереди")
    void keepsQueuePriorityWhenPriceIsNumericallyUnchanged() {
        var book = new OrderBook("TEST");
        var firstSell = new Order(1L, "TEST", Side.SELL, new BigDecimal("100"), 3L);
        var secondSell = new Order(2L, "TEST", Side.SELL, new BigDecimal("100"), 5L);
        book.submit(firstSell);
        book.submit(secondSell);

        var trades = book.changePrice(Side.SELL, firstSell.id(), firstSell.price(), new BigDecimal("100.00"))
                .orElseThrow();

        assertTrue(trades.isEmpty());
        assertEquals(List.of(firstSell, secondSell), book.openOrders(Side.SELL));
    }

    @Test
    @DisplayName("Заявка с новой ценой встаёт в конец очереди на новом уровне")
    void movesOrderBehindExistingOrdersAtNewPrice() {
        var book = new OrderBook("TEST");
        var oldPrice = new BigDecimal("100");
        var newPrice = new BigDecimal("99");
        var repricedSell = new Order(1L, "TEST", Side.SELL, oldPrice, 4L);
        var otherSellAtOldPrice = new Order(2L, "TEST", Side.SELL, oldPrice, 5L);
        var sellAtNewPrice = new Order(3L, "TEST", Side.SELL, newPrice, 2L);
        book.submit(repricedSell);
        book.submit(otherSellAtOldPrice);
        book.submit(sellAtNewPrice);

        var trades = book.changePrice(Side.SELL, repricedSell.id(), oldPrice, newPrice).orElseThrow();

        assertTrue(trades.isEmpty());
        var expectedSell = repricedSell.withPrice(newPrice);
        assertEquals(List.of(sellAtNewPrice, expectedSell, otherSellAtOldPrice), book.openOrders(Side.SELL));

        var buy = new Order(4L, "TEST", Side.BUY, newPrice, 2L);
        assertEquals(1, book.submit(buy).size());
        assertEquals(List.of(expectedSell, otherSellAtOldPrice), book.openOrders(Side.SELL));
    }

    @Test
    @DisplayName("Покупка после изменения цены сначала исполняется по самым дешёвым уровням")
    void repricedBuyMatchesCheapestSellsFirstAcrossPriceLevels() {
        var book = new OrderBook("TEST");
        var cheaperSell = new Order(1L, "TEST", Side.SELL, new BigDecimal("100"), 3L);
        var dearerSell = new Order(2L, "TEST", Side.SELL, new BigDecimal("102"), 5L);
        var buy = new Order(3L, "TEST", Side.BUY, new BigDecimal("95"), 7L);
        book.submit(dearerSell);
        book.submit(cheaperSell);
        book.submit(buy);

        var trades = book.changePrice(Side.BUY, buy.id(), buy.price(), dearerSell.price()).orElseThrow();

        assertEquals(List.of(cheaperSell.price(), dearerSell.price()), trades.stream().map(Trade::price).toList());
        assertEquals(List.of(3L, 4L), trades.stream().map(Trade::quantity).toList());
        assertTrue(book.openOrders(Side.BUY).isEmpty());
        assertEquals(List.of(dearerSell.withQuantity(1L)), book.openOrders(Side.SELL));
    }

    @Test
    @DisplayName("Объём на ценовом уровне суммируется без переполнения long")
    void sumsPriceLevelQuantityBeyondLongRange() {
        var book = new OrderBook("TEST");
        var price = new BigDecimal("100");
        book.submit(new Order(1L, "TEST", Side.SELL, price, Long.MAX_VALUE));
        book.submit(new Order(2L, "TEST", Side.SELL, price, 1L));

        var total = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        assertEquals(List.of(new PriceLevel(price, total)), book.quotes().asks());
    }
}
