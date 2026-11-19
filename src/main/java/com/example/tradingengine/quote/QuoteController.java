package com.example.tradingengine.quote;

import com.example.tradingengine.instrument.validation.InstrumentValidation.RequiredSeccode;
import com.example.tradingengine.model.QuoteSnapshot;
import com.example.tradingengine.order.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QuoteController {

    private final OrderService orderService;

    public QuoteController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/quotes")
    public QuoteSnapshot findQuotes(@RequestParam("seccode") @RequiredSeccode String seccode) {
        return orderService.findQuotes(seccode);
    }
}
