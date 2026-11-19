package com.example.tradingengine.trade;

import com.example.tradingengine.instrument.validation.InstrumentValidation.RequiredSeccode;
import com.example.tradingengine.model.Trade;
import com.example.tradingengine.order.service.OrderService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TradeController {

    private final OrderService orderService;

    public TradeController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/trades")
    public List<Trade> findTrades(@RequestParam("seccode") @RequiredSeccode String seccode) {
        return orderService.findTrades(seccode);
    }
}
