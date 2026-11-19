package com.example.tradingengine.account;

import com.example.tradingengine.order.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountController {

    private final OrderService orderService;

    public AccountController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/accounts/{id}")
    public AccountView findAccount(@PathVariable("id") long id) {
        return orderService.findAccount(id);
    }
}
