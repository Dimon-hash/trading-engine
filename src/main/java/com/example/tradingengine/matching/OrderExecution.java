package com.example.tradingengine.matching;

import com.example.tradingengine.model.Trade;

public record OrderExecution(Trade trade, long buyOrderId, long sellOrderId) {}
