package com.example.tradingengine.order.service;

import com.example.tradingengine.matching.OrderBook;
import com.example.tradingengine.matching.OrderExecution;
import com.example.tradingengine.model.Order;
import com.example.tradingengine.model.Side;
import com.example.tradingengine.model.Trade;
import com.example.tradingengine.order.OrderView;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
class TradingOrderStore {
    private static final RowMapper<StoredOrder> ORDER_ROW = (rs, rowNum) -> new StoredOrder(
            new Order(rs.getLong("id"), rs.getLong("account_id"), rs.getString("seccode"),
                    Side.valueOf(rs.getString("side")), rs.getBigDecimal("price"), rs.getLong("quantity")),
            rs.getLong("remaining_quantity"), rs.getBoolean("cancelled"));

    private final JdbcClient jdbc;

    TradingOrderStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void lockWrites() {
        jdbc.sql("SELECT id FROM trading_write_lock WHERE id = 1 FOR UPDATE")
                .query(Integer.class).single();
    }

    long nextOrderId() {
        return jdbc.sql("SELECT nextval('trading_order_id_seq')").query(Long.class).single();
    }

    List<StoredOrder> findOrders(String seccode) {
        return jdbc.sql("SELECT * FROM trading_orders WHERE seccode = :code ORDER BY id")
                .param("code", seccode).query(ORDER_ROW).list();
    }

    List<StoredOrder> openForInstrument(String seccode) {
        return jdbc.sql("""
                SELECT * FROM trading_orders
                WHERE seccode = :code AND remaining_quantity > 0
                ORDER BY queue_priority, id
                """).param("code", seccode).query(ORDER_ROW).list();
    }

    List<StoredOrder> openForAccount(long accountId) {
        return jdbc.sql("SELECT * FROM trading_orders WHERE account_id = :id AND remaining_quantity > 0")
                .param("id", accountId).query(ORDER_ROW).list();
    }

    StoredOrder requireOpen(String seccode, long id) {
        StoredOrder stored = jdbc.sql("SELECT * FROM trading_orders WHERE id = :id AND seccode = :code")
                .param("id", id).param("code", seccode).query(ORDER_ROW).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Заявка не найдена"));
        if (stored.remaining() == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "У заявки нет открытого остатка");
        }
        return stored;
    }

    OrderBook restoreBook(String seccode, List<StoredOrder> open) {
        OrderBook book = new OrderBook(seccode,
                () -> jdbc.sql("SELECT nextval('trade_id_seq')").query(Long.class).single());
        open.forEach(stored -> book.restoreOpen(stored.openOrder()));
        return book;
    }

    void insert(Order order) {
        jdbc.sql("""
                INSERT INTO trading_orders (id, account_id, seccode, side, price, quantity, remaining_quantity)
                VALUES (:id, :account, :code, :side, :price, :lots, :lots)
                """).param("id", order.id()).param("account", order.accountId())
                .param("code", order.seccode()).param("side", order.side().name())
                .param("price", order.price()).param("lots", order.quantity()).update();
    }

    void changePrice(long id, BigDecimal newPrice) {
        jdbc.sql("""
                UPDATE trading_orders
                SET price = :price, queue_priority = nextval('trading_queue_priority_seq')
                WHERE id = :id
                """).param("price", newPrice).param("id", id).update();
    }

    void cancel(long id) {
        jdbc.sql("UPDATE trading_orders SET cancelled = TRUE, remaining_quantity = 0 WHERE id = :id")
                .param("id", id).update();
    }

    void saveExecution(OrderExecution execution) {
        Trade trade = execution.trade();
        int updated = jdbc.sql("""
                UPDATE trading_orders SET remaining_quantity = remaining_quantity - :lots
                WHERE id IN (:buy, :sell) AND remaining_quantity >= :lots AND NOT cancelled
                """).param("lots", trade.quantity()).param("buy", execution.buyOrderId())
                .param("sell", execution.sellOrderId()).update();

        if (updated != 2) {
            throw new IllegalStateException("Стакан и остатки заявок в БД расходятся");
        }

        jdbc.sql("""
                INSERT INTO trades (id, seccode, buy_order_id, sell_order_id, price, quantity)
                VALUES (:id, :code, :buy, :sell, :price, :lots)
                """).param("id", trade.id()).param("code", trade.seccode())
                .param("buy", execution.buyOrderId()).param("sell", execution.sellOrderId())
                .param("price", trade.price()).param("lots", trade.quantity()).update();
    }

    List<Long> accountIds(Set<Long> orderIds) {
        return jdbc.sql("SELECT DISTINCT account_id FROM trading_orders WHERE id IN (:ids)")
                .param("ids", orderIds).query(Long.class).list();
    }

    List<Trade> findTrades(String seccode) {
        return jdbc.sql("SELECT id, seccode, price, quantity FROM trades WHERE seccode = :code ORDER BY id")
                .param("code", seccode)
                .query((rs, row) -> new Trade(rs.getLong("id"), rs.getString("seccode"),
                        rs.getBigDecimal("price"), rs.getLong("quantity")))
                .list();
    }

    record StoredOrder(Order order, long remaining, boolean cancelled) {
        Order openOrder() {
            return order.withQuantity(remaining);
        }

        OrderView view() {
            return OrderView.from(order, remaining, cancelled);
        }
    }
}
