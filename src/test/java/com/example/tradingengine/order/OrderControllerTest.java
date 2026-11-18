package com.example.tradingengine.order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

    private final MockMvc mockMvc;

    @Autowired
    OrderControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void returnsTradeForCrossingOrders() throws Exception {
        postOrder("MATCH", "SELL", 100, "10")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").isNumber())
                .andExpect(jsonPath("$.trades").isEmpty());

        postOrder("MATCH", "BUY", 101, "4")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").isNumber())
                .andExpect(jsonPath("$.trades.length()").value(1))
                .andExpect(jsonPath("$.trades[0].id").isNumber())
                .andExpect(jsonPath("$.trades[0].seccode").value("MATCH"))
                .andExpect(jsonPath("$.trades[0].price").value(100))
                .andExpect(jsonPath("$.trades[0].quantity").value(4));
    }

    @Test
    void keepsOrdersForDifferentInstrumentsApart() throws Exception {
        postOrder("FIRST", "SELL", 100, "10")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trades").isEmpty());

        postOrder("SECOND", "BUY", 101, "4")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trades").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.5", "null"})
    void rejectsInvalidQuantities(String quantity) throws Exception {
        postOrder("INVALID", "BUY", 100, quantity)
                .andExpect(status().isBadRequest());
    }

    private ResultActions postOrder(String seccode, String side, int price, String quantity) throws Exception {
        String body = """
                {"seccode":"%s","side":"%s","price":%d,"quantity":%s}
                """.formatted(seccode, side, price, quantity);
        return mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
