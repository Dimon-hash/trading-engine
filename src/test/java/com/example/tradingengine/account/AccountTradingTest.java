package com.example.tradingengine.account;

import com.example.tradingengine.account.service.AccountStore;
import com.example.tradingengine.instrument.InstrumentCatalog;
import com.example.tradingengine.model.Account;
import com.example.tradingengine.model.Instrument;
import com.example.tradingengine.model.InstrumentType;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Currency;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountTradingTest {

    private final MockMvc mockMvc;
    private final AccountStore ledger;
    private final InstrumentCatalog catalog;

    @Autowired
    AccountTradingTest(MockMvc mockMvc, AccountStore ledger, InstrumentCatalog catalog) {
        this.mockMvc = mockMvc;
        this.ledger = ledger;
        this.catalog = catalog;
    }

    @Test
    @DisplayName("Частичная сделка переводит деньги и бумаги, отмена освобождает резервы")
    void settlesTradeInUnitsAndReleasesBothKindsOfReserveOnCancellation() throws Exception {
        submit(2L, "DEMO", "SELL", 100, 1).andExpect(status().isCreated());
        mockMvc.perform(get("/accounts/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions[0].availableQuantity").value(990))
                .andExpect(jsonPath("$.positions[0].reservedQuantity").value(10));

        long buyId = orderId(submit(1L, "DEMO", "BUY", 101, 2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trades[0].price").value(100))
                .andExpect(jsonPath("$.trades[0].quantity").value(1)));

        mockMvc.perform(get("/accounts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalance").value(7990))
                .andExpect(jsonPath("$.reservedBalance").value(1010))
                .andExpect(jsonPath("$.positions[0].availableQuantity").value(10));
        mockMvc.perform(get("/accounts/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalance").value(1000))
                .andExpect(jsonPath("$.positions[0].reservedQuantity").value(0));

        changePrice(buyId, "DEMO", 102).andExpect(status().isOk())
                .andExpect(jsonPath("$.trades").isEmpty());
        mockMvc.perform(get("/accounts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalance").value(7980))
                .andExpect(jsonPath("$.reservedBalance").value(1020));

        mockMvc.perform(post("/orders/{id}/cancel", buyId).param("seccode", "DEMO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(1))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(get("/accounts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalance").value(9000))
                .andExpect(jsonPath("$.reservedBalance").value(0));

        long sellId = orderId(submit(2L, "DEMO", "SELL", 100, 2).andExpect(status().isCreated()));
        mockMvc.perform(get("/accounts/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions[0].availableQuantity").value(970))
                .andExpect(jsonPath("$.positions[0].reservedQuantity").value(20));
        mockMvc.perform(post("/orders/{id}/cancel", sellId).param("seccode", "DEMO"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/accounts/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions[0].availableQuantity").value(990))
                .andExpect(jsonPath("$.positions[0].reservedQuantity").value(0));
    }

    @Test
    @DisplayName("Продажа исполняется по цене заявки на покупку, остаток бумаг остаётся в резерве")
    void incomingSellSettlesAtRestingBuyPriceAndKeepsItsRemainingStockReserved() throws Exception {
        String seccode = "ASK-FILL";
        registerInstrument(seccode);
        registerAccount(51L, "RUB", "220");
        registerAccount(52L, "RUB", "0");
        ledger.registerPosition(52L, seccode, BigInteger.valueOf(3));

        submit(51L, seccode, "BUY", 110, 1).andExpect(status().isCreated());
        long sellId = orderId(submit(52L, seccode, "SELL", 100, 2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trades[0].price").value(110))
                .andExpect(jsonPath("$.trades[0].quantity").value(1)));

        mockMvc.perform(get("/accounts/51"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalance").value(110))
                .andExpect(jsonPath("$.reservedBalance").value(0))
                .andExpect(jsonPath("$.positions[0].availableQuantity").value(1));
        mockMvc.perform(get("/accounts/52"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalance").value(110))
                .andExpect(jsonPath("$.positions[0].availableQuantity").value(1))
                .andExpect(jsonPath("$.positions[0].reservedQuantity").value(1));

        mockMvc.perform(post("/orders/{id}/cancel", sellId).param("seccode", seccode))
                .andExpect(status().isOk());
        mockMvc.perform(get("/accounts/52"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions[0].availableQuantity").value(2))
                .andExpect(jsonPath("$.positions[0].reservedQuantity").value(0));
    }

    @Test
    @DisplayName("Недостаток денег при изменении цены даёт HTTP 409, допустимая цена приводит к сделке")
    void repricingWithInsufficientCashLeavesOrderIntactAndValidRepricingCanSettle() throws Exception {
        String seccode = "REPRICE-FUNDS";
        registerInstrument(seccode);
        registerAccount(21L, "RUB", "110");
        registerAccount(22L, "RUB", "0");
        ledger.registerPosition(22L, seccode, BigInteger.ONE);

        long buy = orderId(submit(21L, seccode, "BUY", 100, 1).andExpect(status().isCreated()));
        submit(22L, seccode, "SELL", 105, 1).andExpect(status().isCreated());

        changePrice(buy, seccode, 120).andExpect(status().isConflict());
        mockMvc.perform(get("/quotes").param("seccode", seccode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bids[0].price").value(100))
                .andExpect(jsonPath("$.asks[0].price").value(105));
        mockMvc.perform(get("/accounts/21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalance").value(10))
                .andExpect(jsonPath("$.reservedBalance").value(100));

        changePrice(buy, seccode, 105).andExpect(status().isOk())
                .andExpect(jsonPath("$.trades[0].price").value(105));
        mockMvc.perform(get("/accounts/21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalance").value(5))
                .andExpect(jsonPath("$.reservedBalance").value(0))
                .andExpect(jsonPath("$.positions[0].availableQuantity").value(1));
        mockMvc.perform(get("/accounts/22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalance").value(105));
    }

    @Test
    @DisplayName("Заявки без нужных активов, подходящей валюты или счёта отклоняются")
    void rejectsOrdersWithoutRequiredAssetsAccountOrCurrency() throws Exception {
        String seccode = "ASSET-CHECK";
        registerInstrument(seccode);
        registerAccount(31L, "RUB", "0");
        registerAccount(32L, "CNY", "10000");

        submit(31L, seccode, "BUY", 100, 1).andExpect(status().isConflict());
        submit(31L, seccode, "SELL", 100, 1).andExpect(status().isConflict());
        submit(32L, seccode, "BUY", 100, 1).andExpect(status().isConflict());
        submit(999L, seccode, "BUY", 100, 1).andExpect(status().isNotFound());
        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seccode":"ASSET-CHECK","side":"BUY","price":100,"quantity":1}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/quotes").param("seccode", seccode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bids").isEmpty())
                .andExpect(jsonPath("$.asks").isEmpty());
    }

    @Test
    @DisplayName("Одновременные заявки по разным инструментам расходуют общий баланс")
    void concurrentOrdersForDifferentInstrumentsShareAccountBalance() throws Exception {
        registerInstrument("CASH-FIRST");
        registerInstrument("CASH-SECOND");
        registerAccount(41L, "RUB", "100");
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return submit(41L, "CASH-FIRST", "BUY", 100, 1).andReturn().getResponse().getStatus();
            });
            var second = executor.submit(() -> {
                start.await();
                return submit(41L, "CASH-SECOND", "BUY", 100, 1).andReturn().getResponse().getStatus();
            });

            start.countDown();
            List<Integer> statuses = List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
            assertEquals(List.of(201, 409), statuses.stream().sorted().toList());
        }

        mockMvc.perform(get("/accounts/41"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalance").value(0))
                .andExpect(jsonPath("$.reservedBalance").value(100));
    }

    private void registerInstrument(String seccode) {
        catalog.register(new Instrument(seccode, InstrumentType.SECURITY, 1, new BigDecimal("0.05")));
    }

    private void registerAccount(long id, String currency, String balance) {
        ledger.register(new Account(id, id, new BigDecimal(balance), Currency.getInstance(currency)));
    }

    private ResultActions submit(long accountId, String seccode, String side, int price, long quantity)
            throws Exception {
        String body = """
                {"accountId":%d,"seccode":"%s","side":"%s","price":%d,"quantity":%d}
                """.formatted(accountId, seccode, side, price, quantity);
        return mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions changePrice(long orderId, String seccode, int price) throws Exception {
        return mockMvc.perform(patch("/orders/{id}", orderId).param("seccode", seccode)
                .contentType(MediaType.APPLICATION_JSON).content("{\"price\":%d}".formatted(price)));
    }

    private static long orderId(ResultActions result) throws Exception {
        Number id = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.orderId");
        return id.longValue();
    }
}
