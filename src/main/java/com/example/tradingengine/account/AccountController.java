package com.example.tradingengine.account;

import com.example.tradingengine.account.service.CashLedger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountController {

    private final CashLedger cashLedger;

    public AccountController(CashLedger cashLedger) {
        this.cashLedger = cashLedger;
    }

    @GetMapping("/accounts/{id}")
    public AccountView findAccount(@PathVariable("id") long id) {
        return cashLedger.findAccount(id);
    }
}
