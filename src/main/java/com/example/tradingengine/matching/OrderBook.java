package com.example.tradingengine.matching;

import com.example.tradingengine.model.Order;
import com.example.tradingengine.model.Side;
import com.example.tradingengine.model.Trade;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class OrderBook {

    private static final AtomicLong NEXT_TRADE_ID = new AtomicLong(1);

    private final String seccode;
    private final OrderBookSide buys = new OrderBookSide(Comparator.reverseOrder());
    private final OrderBookSide sells = new OrderBookSide(Comparator.naturalOrder());

    public OrderBook(String seccode) {
        this.seccode = Objects.requireNonNull(seccode, "seccode");
    }

    /**
     * Принимает заявку и исполняет её против лучших встречных заявок,
     * пока их цены укладываются в лимит входящей заявки.
     * Каждая сделка проходит по цене встречной заявки из книги.
     * Неисполненный остаток добавляется в книгу по исходной цене.
     *
     * @param incoming входящая заявка на покупку или продажу
     * @return сделки в порядке исполнения. Список пуст, если сделок не было
     * @throws IllegalArgumentException если заявка относится к другому инструменту
     */
    public synchronized List<Trade> submit(Order incoming) {
        if (!seccode.equals(incoming.seccode())) {
            throw new IllegalArgumentException("Заявка относится к другому инструменту");
        }

        boolean isBuy = incoming.side() == Side.BUY;
        OrderBookSide ownSide = isBuy ? buys : sells;
        OrderBookSide oppositeSide = isBuy ? sells : buys;
        List<Trade> trades = new ArrayList<>();
        long remaining = incoming.quantity();

        while (remaining > 0) {
            Order resting = oppositeSide.bestOrder();
            if (resting == null || !pricesCross(incoming, resting)) {
                break;
            }

            long filled = Math.min(remaining, resting.quantity());
            long tradeId = NEXT_TRADE_ID.getAndIncrement();
            trades.add(new Trade(tradeId, seccode, resting.price(), filled));

            oppositeSide.fillBestOrder(filled);
            remaining -= filled;
        }

        if (remaining > 0) {
            ownSide.add(incoming.withQuantity(remaining));
        }

        return List.copyOf(trades);
    }

    public synchronized List<Order> openOrders(Side side) {
        Objects.requireNonNull(side, "side");
        return (side == Side.BUY ? buys : sells).snapshot();
    }

    private static boolean pricesCross(Order incoming, Order resting) {
        int comparison = incoming.price().compareTo(resting.price());
        return incoming.side() == Side.BUY ? comparison >= 0 : comparison <= 0;
    }
}