package com.example.tradingengine.trade;

import com.example.tradingengine.testsupport.TestInstruments;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestInstruments.class)
class TradeControllerTest {

    private final MockMvc mockMvc;

    @Autowired
    TradeControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    @DisplayName("Реестр возвращает сделки выбранного инструмента в порядке исполнения")
    void returnsEarlierTradesForRequestedInstrumentInExecutionOrder() throws Exception {
        postOrder("TRADE-TEST", "SELL", 100, 10);
        postOrder("TRADE-TEST", "BUY", 101, 4);
        postOrder("OTHER-TRADE", "SELL", 200, 8);
        postOrder("OTHER-TRADE", "BUY", 201, 8);
        postOrder("TRADE-TEST", "BUY", 102, 6);

        mockMvc.perform(get("/trades").param("seccode", "TRADE-TEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].seccode").value("TRADE-TEST"))
                .andExpect(jsonPath("$[0].price").value(100))
                .andExpect(jsonPath("$[0].quantity").value(4))
                .andExpect(jsonPath("$[1].seccode").value("TRADE-TEST"))
                .andExpect(jsonPath("$[1].price").value(100))
                .andExpect(jsonPath("$[1].quantity").value(6));
    }

    @Test
    @DisplayName("Реестр возвращает пустой список сделок для неизвестного инструмента")
    void returnsEmptyListForUnknownInstrument() throws Exception {
        mockMvc.perform(get("/trades").param("seccode", "UNKNOWN-TRADE"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("Запрос сделок без кода инструмента или с пробелами даёт HTTP 400")
    void rejectsMissingOrBlankInstrumentCode() throws Exception {
        mockMvc.perform(get("/trades"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/trades").param("seccode", " "))
                .andExpect(status().isBadRequest());
    }

    private void postOrder(String seccode, String side, int price, long quantity) throws Exception {
        String body = """
                {"accountId":%d,"seccode":"%s","side":"%s","price":%d,"quantity":%d}
                """.formatted(TestInstruments.accountForSide(side), seccode, side, price, quantity);
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }
}
