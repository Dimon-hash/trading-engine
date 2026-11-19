package com.example.tradingengine.testsupport;

import com.example.tradingengine.account.service.CashLedger;
import com.example.tradingengine.instrument.InstrumentCatalog;
import com.example.tradingengine.model.Account;
import com.example.tradingengine.model.Instrument;
import com.example.tradingengine.model.InstrumentType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Currency;
import java.util.List;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class TestInstruments {
    private static final long BUYER_ID = 11L;
    private static final long SELLER_ID = 12L;

    public static long accountForSide(String side) {
        return "BUY".equals(side) ? BUYER_ID : SELLER_ID;
    }

    @Bean
    ApplicationRunner registerTestInstruments(InstrumentCatalog catalog, CashLedger ledger) {
        return args -> {
            Currency rub = Currency.getInstance("RUB");
            ledger.register(new Account(BUYER_ID, 111L, new BigDecimal("1000000"), rub));
            ledger.register(new Account(SELLER_ID, 112L, BigDecimal.ZERO, rub));
            ledger.registerPosition(SELLER_ID, "DEMO", BigInteger.valueOf(1000));
            List.of(
                    "MATCH", "FIRST", "SECOND", "INVALID", "ORDER-REGISTRY",
                    "REPRICE", "REPRICE-CROSS", "REPRICE-MISSING", "REPRICE-NULL",
                    "REPRICE-ZERO", "REPRICE-NEGATIVE", "REPRICE-RIGHT", "REPRICE-OTHER",
                    "REPRICE-TWICE", "CANCEL-PARTIAL",
                    "QUOTES-LEVELS", "QUOTES-CHANGES", "QUOTES-OTHER",
                    "TRADE-TEST", "OTHER-TRADE"
            ).forEach(seccode -> {
                catalog.register(new Instrument(seccode, InstrumentType.SECURITY, 1, new BigDecimal("0.05")));
                ledger.registerPosition(SELLER_ID, seccode, BigInteger.valueOf(1000));
            });
        };
    }
}
