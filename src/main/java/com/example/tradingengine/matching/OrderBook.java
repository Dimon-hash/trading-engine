package com.example.tradingengine.matching;

import com.example.tradingengine.matching.validation.MatchingValidation;
import com.example.tradingengine.model.Order;
import com.example.tradingengine.model.QuoteSnapshot;
import com.example.tradingengine.model.Side;
import com.example.tradingengine.model.Trade;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public final class OrderBook {

    private static final AtomicLong NEXT_TRADE_ID = new AtomicLong(1);

    private final String seccode;
    private final OrderBookSide buys = new OrderBookSide(Comparator.reverseOrder());
    private final OrderBookSide sells = new OrderBookSide(Comparator.naturalOrder());

    private final LongSupplier nextTradeId;

    public OrderBook(String seccode) {
        this(seccode, NEXT_TRADE_ID::getAndIncrement);
    }

    public OrderBook(String seccode, LongSupplier nextTradeId) {
        this.seccode = MatchingValidation.requireSeccode(seccode);
        this.nextTradeId = nextTradeId;
    }

    public synchronized void restoreOpen(Order order) {
        MatchingValidation.requireMatchingInstrument(seccode, order);
        (order.side() == Side.BUY ? buys : sells).add(order);
    }

    public List<Trade> submit(Order incoming) {
        return execute(incoming).stream().map(OrderExecution::trade).toList();
    }

    public synchronized List<OrderExecution> execute(Order incoming) {
        MatchingValidation.requireMatchingInstrument(seccode, incoming);

        boolean isBuy = incoming.side() == Side.BUY;
        OrderBookSide ownSide = isBuy ? buys : sells;
        OrderBookSide oppositeSide = isBuy ? sells : buys;
        List<OrderExecution> executions = new ArrayList<>();
        long remaining = incoming.quantity();

        while (remaining > 0) {
            Order resting = oppositeSide.bestOrder();
            if (resting == null || !pricesCross(incoming, resting)) {
                break;
            }

            long filled = Math.min(remaining, resting.quantity());
            long tradeId = nextTradeId.getAsLong();
            Trade trade = new Trade(tradeId, seccode, resting.price(), filled);
            long buyOrderId = isBuy ? incoming.id() : resting.id();
            long sellOrderId = isBuy ? resting.id() : incoming.id();
            executions.add(new OrderExecution(trade, buyOrderId, sellOrderId));

            oppositeSide.fillBestOrder(filled);
            remaining -= filled;
        }

        if (remaining > 0) {
            ownSide.add(incoming.withQuantity(remaining));
        }

        return List.copyOf(executions);
    }

    public Optional<List<Trade>> changePrice(
            Side side, long id, BigDecimal oldPrice, BigDecimal newPrice
    ) {
        return reprice(side, id, oldPrice, newPrice)
                .map(executions -> executions.stream().map(OrderExecution::trade).toList());
    }

    public synchronized Optional<List<OrderExecution>> reprice(
            Side side, long id, BigDecimal oldPrice, BigDecimal newPrice
    ) {
        MatchingValidation.requireSide(side);
        OrderBookSide bookSide = side == Side.BUY ? buys : sells;
        Order openOrder = bookSide.find(id, oldPrice);
        if (openOrder == null) {
            return Optional.empty();
        }
        if (openOrder.price().compareTo(newPrice) == 0) {
            return Optional.of(List.of());
        }

        Order replacement = openOrder.withPrice(newPrice);
        bookSide.remove(openOrder);
        return Optional.of(execute(replacement));
    }

    public synchronized boolean cancel(Side side, long id, BigDecimal price) {
        MatchingValidation.requireSide(side);
        OrderBookSide bookSide = side == Side.BUY ? buys : sells;
        Order openOrder = bookSide.find(id, price);
        if (openOrder == null) {
            return false;
        }

        bookSide.remove(openOrder);
        return true;
    }

    public synchronized List<Order> openOrders(Side side) {
        MatchingValidation.requireSide(side);
        return (side == Side.BUY ? buys : sells).snapshot();
    }

    public synchronized QuoteSnapshot quotes() {
        return new QuoteSnapshot(seccode, buys.priceLevels(), sells.priceLevels());
    }

    private static boolean pricesCross(Order incoming, Order resting) {
        int comparison = incoming.price().compareTo(resting.price());
        return incoming.side() == Side.BUY ? comparison >= 0 : comparison <= 0;
    }
}
