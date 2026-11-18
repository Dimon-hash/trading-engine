package com.example.tradingengine.order;

import com.example.tradingengine.model.Trade;
import java.util.List;

public record OrderSubmission(long orderId, List<Trade> trades) {}
