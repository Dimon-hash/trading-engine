package com.example.tradingengine.matching.validation;

import com.example.tradingengine.model.Order;
import com.example.tradingengine.model.Side;
import java.util.Objects;

public final class MatchingValidation {

    private MatchingValidation() {}

    public static String requireSeccode(String seccode) {
        return Objects.requireNonNull(seccode, "seccode");
    }

    public static void requireSide(Side side) {
        Objects.requireNonNull(side, "side");
    }

    public static void requireMatchingInstrument(String seccode, Order order) {
        if (!seccode.equals(order.seccode())) {
            throw new IllegalArgumentException("Заявка относится к другому инструменту");
        }
    }
}
