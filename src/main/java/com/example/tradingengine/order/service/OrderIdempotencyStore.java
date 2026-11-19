package com.example.tradingengine.order.service;

import com.example.tradingengine.model.Side;
import com.example.tradingengine.model.Trade;
import com.example.tradingengine.order.CreateOrderRequest;
import com.example.tradingengine.order.OrderSubmission;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class OrderIdempotencyStore {

    private static final TypeReference<List<Trade>> TRADE_LIST = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    OrderIdempotencyStore(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    Optional<SavedSubmission> find(long accountId, String key) {
        return jdbc.sql("""
                SELECT seccode, side, price, quantity, order_id, trades_json
                FROM order_idempotency
                WHERE account_id = :accountId AND idempotency_key = :key
                """)
                .param("accountId", accountId)
                .param("key", key)
                .query((rs, rowNum) -> new SavedSubmission(
                        rs.getString("seccode"),
                        Side.valueOf(rs.getString("side")),
                        rs.getBigDecimal("price"),
                        rs.getLong("quantity"),
                        new OrderSubmission(rs.getLong("order_id"), readTrades(rs.getString("trades_json")))))
                .optional();
    }

    void save(String key, CreateOrderRequest request, OrderSubmission result) {
        jdbc.sql("""
                INSERT INTO order_idempotency
                    (account_id, idempotency_key, seccode, side, price, quantity, order_id, trades_json)
                VALUES (:accountId, :key, :seccode, :side, :price, :quantity, :orderId, :trades)
                """)
                .param("accountId", request.accountId())
                .param("key", key)
                .param("seccode", request.seccode())
                .param("side", request.side().name())
                .param("price", request.price())
                .param("quantity", request.quantity())
                .param("orderId", result.orderId())
                .param("trades", writeTrades(result.trades()))
                .update();
    }

    private List<Trade> readTrades(String value) {
        try {
            return json.readValue(value, TRADE_LIST);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Не удалось прочитать сохранённый результат заявки", exception);
        }
    }

    private String writeTrades(List<Trade> trades) {
        try {
            return json.writeValueAsString(trades);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Не удалось сохранить результат заявки", exception);
        }
    }

    record SavedSubmission(String seccode, Side side, BigDecimal price, long quantity, OrderSubmission result) {
        boolean matches(CreateOrderRequest request) {
            return seccode.equals(request.seccode())
                    && side == request.side()
                    && price.compareTo(request.price()) == 0
                    && quantity == request.quantity();
        }
    }
}
