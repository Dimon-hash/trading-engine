package com.example.tradingengine.order.service;

import com.example.tradingengine.account.service.CashLedger;
import com.example.tradingengine.instrument.InstrumentCatalog;
import com.example.tradingengine.model.QuoteSnapshot;
import com.example.tradingengine.model.Side;
import com.example.tradingengine.model.Trade;
import com.example.tradingengine.order.OrderSubmission;
import com.example.tradingengine.order.OrderView;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final OrderRegistry registry = new OrderRegistry();
    private final OrderOperations operations;

    public OrderService(InstrumentCatalog instrumentCatalog, CashLedger cashLedger) {
        this.operations = new OrderOperations(instrumentCatalog, cashLedger, registry);
    }

    public OrderSubmission submit(long accountId, String seccode, Side side, BigDecimal price, long quantity) {
        return operations.submit(accountId, seccode, side, price, quantity);
    }

    public OrderSubmission changePrice(String seccode, long id, BigDecimal newPrice) {
        return operations.changePrice(seccode, id, newPrice);
    }

    public OrderView cancel(String seccode, long id) {
        return operations.cancel(seccode, id);
    }

    public List<OrderView> findOrders(String seccode) {
        return registry.findOrders(seccode);
    }

    public QuoteSnapshot findQuotes(String seccode) {
        return registry.findQuotes(seccode);
    }

    public List<Trade> findTrades(String seccode) {
        return registry.findTrades(seccode);
    }
}
