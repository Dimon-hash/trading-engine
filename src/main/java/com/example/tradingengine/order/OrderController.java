package com.example.tradingengine.order;

import com.example.tradingengine.instrument.validation.InstrumentValidation.RequiredSeccode;
import com.example.tradingengine.order.service.OrderService;
import com.example.tradingengine.order.validation.OrderValidation;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderValidation orderValidation = new OrderValidation();

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @InitBinder
    void configureValidation(WebDataBinder binder) {
        Object target = binder.getTarget();
        if (target != null && orderValidation.supports(target.getClass())) {
            binder.addValidators(orderValidation);
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderSubmission submit(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.submit(
                request.accountId(), request.seccode(), request.side(), request.price(), request.quantity());
    }

    @PatchMapping("/{id}")
    public OrderSubmission changePrice(
            @PathVariable("id") long id,
            @RequestParam("seccode") @RequiredSeccode String seccode,
            @Valid @RequestBody ChangeOrderPriceRequest request
    ) {
        return orderService.changePrice(seccode, id, request.price());
    }

    @PostMapping("/{id}/cancel")
    public OrderView cancel(
            @PathVariable("id") long id,
            @RequestParam("seccode") @RequiredSeccode String seccode
    ) {
        return orderService.cancel(seccode, id);
    }

    @GetMapping
    public List<OrderView> findOrders(@RequestParam("seccode") @RequiredSeccode String seccode) {
        return orderService.findOrders(seccode);
    }
}
