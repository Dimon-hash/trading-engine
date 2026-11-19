package com.example.tradingengine.quote;

import com.example.tradingengine.testsupport.TestInstruments;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestInstruments.class)
class QuoteControllerTest {

    private final MockMvc mockMvc;

    @Autowired
    QuoteControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    @DisplayName("Котировки суммируют лоты по цене и сортируют обе стороны по приоритету")
    void groupsOrdersAtEqualPricesAndSortsBothSidesByBestPrice() throws Exception {
        String seccode = "QUOTES-LEVELS";
        submitOrder(seccode, "SELL", "101", 2);
        submitOrder(seccode, "SELL", "100", 3);
        submitOrder(seccode, "SELL", "100.00", 7);
        submitOrder(seccode, "BUY", "98", 4);
        submitOrder(seccode, "BUY", "99", 5);
        submitOrder("QUOTES-OTHER", "SELL", "97", 8);

        mockMvc.perform(get("/quotes").param("seccode", seccode))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "seccode": "QUOTES-LEVELS",
                          "bids": [{"price": 99, "quantity": 5}, {"price": 98, "quantity": 4}],
                          "asks": [{"price": 100, "quantity": 10}, {"price": 101, "quantity": 2}]
                        }
                        """, JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("Котировки обновляются после сделки, изменения цены и отмены заявки")
    void updatesQuotesAfterExecutionPriceChangeAndCancellation() throws Exception {
        String seccode = "QUOTES-CHANGES";
        submitOrder(seccode, "SELL", "100", 3);
        long id = submitOrder(seccode, "SELL", "100", 7);
        submitOrder(seccode, "BUY", "100", 4);

        mockMvc.perform(get("/quotes").param("seccode", seccode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bids").isEmpty())
                .andExpect(jsonPath("$.asks.length()").value(1))
                .andExpect(jsonPath("$.asks[0].price").value(100))
                .andExpect(jsonPath("$.asks[0].quantity").value(6));

        mockMvc.perform(patch("/orders/{id}", id).param("seccode", seccode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":98}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/quotes").param("seccode", seccode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asks.length()").value(1))
                .andExpect(jsonPath("$.asks[0].price").value(98))
                .andExpect(jsonPath("$.asks[0].quantity").value(6));

        mockMvc.perform(post("/orders/{id}/cancel", id).param("seccode", seccode))
                .andExpect(status().isOk());

        mockMvc.perform(get("/quotes").param("seccode", seccode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asks").isEmpty());
    }

    @Test
    @DisplayName("Котировки инструмента без стакана возвращают пустые списки покупки и продажи")
    void returnsEmptySidesWhenThereIsNoBookForInstrument() throws Exception {
        mockMvc.perform(get("/quotes").param("seccode", "QUOTES-UNKNOWN"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"seccode": "QUOTES-UNKNOWN", "bids": [], "asks": []}
                        """, JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("Запрос котировок без кода инструмента или с пробелами даёт HTTP 400")
    void rejectsMissingOrBlankInstrumentCode() throws Exception {
        mockMvc.perform(get("/quotes"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/quotes").param("seccode", " "))
                .andExpect(status().isBadRequest());
    }

    private long submitOrder(String seccode, String side, String price, long quantity) throws Exception {
        String body = """
                {"accountId":%d,"seccode":"%s","side":"%s","price":%s,"quantity":%d}
                """.formatted(TestInstruments.accountForSide(side), seccode, side, price, quantity);
        String response = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(response, "$.orderId");
        return id.longValue();
    }
}
