package com.example.tradingengine.order;

import com.example.tradingengine.testsupport.TestInstruments;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestInstruments.class)
class OrderControllerTest {

    private final MockMvc mockMvc;

    @Autowired
    OrderControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    @DisplayName("Встречные заявки исполняются по цене заявки в стакане")
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
    @DisplayName("Заявки по разным инструментам попадают в отдельные стаканы")
    void keepsOrdersForDifferentInstrumentsApart() throws Exception {
        postOrder("FIRST", "SELL", 100, "10")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trades").isEmpty());

        postOrder("SECOND", "BUY", 101, "4")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trades").isEmpty());
    }

    @Test
    @DisplayName("Реестр показывает исходное количество, открытый остаток и статус заявки")
    void returnsOriginalQuantitiesAndCurrentStatuses() throws Exception {
        postOrder("ORDER-REGISTRY", "SELL", 100, "10")
                .andExpect(status().isCreated());

        mockMvc.perform(get("/orders").param("seccode", "ORDER-REGISTRY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].remainingQuantity").value(10))
                .andExpect(jsonPath("$[0].status").value("OPEN"));

        postOrder("ORDER-REGISTRY", "BUY", 101, "4")
                .andExpect(status().isCreated());

        mockMvc.perform(get("/orders").param("seccode", "ORDER-REGISTRY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].side").value("SELL"))
                .andExpect(jsonPath("$[0].quantity").value(10))
                .andExpect(jsonPath("$[0].remainingQuantity").value(6))
                .andExpect(jsonPath("$[0].status").value("PARTIALLY_FILLED"))
                .andExpect(jsonPath("$[1].side").value("BUY"))
                .andExpect(jsonPath("$[1].quantity").value(4))
                .andExpect(jsonPath("$[1].remainingQuantity").value(0))
                .andExpect(jsonPath("$[1].status").value("FILLED"));
    }

    @Test
    @DisplayName("Изменение цены применяется к открытому остатку и сохраняет прежнюю сделку")
    void changesPriceOfOpenRemainderAndKeepsEarlierTrade() throws Exception {
        long id = submitSellOrder("REPRICE", 100, 10);

        postOrder("REPRICE", "BUY", 101, "4").andExpect(status().isCreated());

        patchPrice(id, "REPRICE", "{\"price\":98}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(id))
                .andExpect(jsonPath("$.trades").isEmpty());

        mockMvc.perform(get("/orders").param("seccode", "REPRICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].price").value(98))
                .andExpect(jsonPath("$[0].quantity").value(10))
                .andExpect(jsonPath("$[0].remainingQuantity").value(6))
                .andExpect(jsonPath("$[0].status").value("PARTIALLY_FILLED"));

        mockMvc.perform(get("/trades").param("seccode", "REPRICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].price").value(100))
                .andExpect(jsonPath("$[0].quantity").value(4));
    }

    @Test
    @DisplayName("Новая цена приводит к сделке, повторное изменение исполненной заявки даёт HTTP 409")
    void matchesNewPriceAndRejectsChangeOfFilledOrder() throws Exception {
        long id = submitSellOrder("REPRICE-CROSS", 100, 4);
        postOrder("REPRICE-CROSS", "BUY", 99, "4").andExpect(status().isCreated());

        patchPrice(id, "REPRICE-CROSS", "{\"price\":98}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trades.length()").value(1))
                .andExpect(jsonPath("$.trades[0].price").value(99))
                .andExpect(jsonPath("$.trades[0].quantity").value(4));
        patchPrice(id, "REPRICE-CROSS", "{\"price\":98}").andExpect(status().isConflict());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "REPRICE-MISSING|{}",
            "REPRICE-NULL|{\"price\":null}",
            "REPRICE-ZERO|{\"price\":0}",
            "REPRICE-NEGATIVE|{\"price\":-1}"
    })
    @DisplayName("Некорректная новая цена даёт HTTP 400, заявка остаётся открытой")
    void rejectsInvalidNewPriceAndKeepsOrderOpen(String seccode, String body) throws Exception {
        long id = submitSellOrder(seccode, 100, 10);

        patchPrice(id, seccode, body).andExpect(status().isBadRequest());

        mockMvc.perform(get("/orders").param("seccode", seccode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].price").value(100))
                .andExpect(jsonPath("$[0].remainingQuantity").value(10))
                .andExpect(jsonPath("$[0].status").value("OPEN"));
    }

    @Test
    @DisplayName("Неизвестная заявка или неверный код инструмента дают HTTP 404")
    void returnsNotFoundForUnknownOrderOrWrongInstrument() throws Exception {
        long id = submitSellOrder("REPRICE-RIGHT", 100, 10);
        submitSellOrder("REPRICE-OTHER", 100, 10);
        String body = "{\"price\":98}";

        patchPrice(Long.MAX_VALUE, "REPRICE-RIGHT", body).andExpect(status().isNotFound());
        patchPrice(id, "REPRICE-OTHER", body).andExpect(status().isNotFound());
        patchPrice(id, "REPRICE-UNKNOWN", body).andExpect(status().isNotFound());

        mockMvc.perform(get("/orders").param("seccode", "REPRICE-RIGHT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].price").value(100));
        mockMvc.perform(get("/orders").param("seccode", "REPRICE-OTHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].price").value(100));
    }

    @Test
    @DisplayName("Повторное изменение находит заявку по её текущей цене")
    void looksUpOrderAtItsLatestPriceOnRepeatedChanges() throws Exception {
        String seccode = "REPRICE-TWICE";
        long id = submitSellOrder(seccode, 100, 10);

        patchPrice(id, seccode, "{\"price\":98}").andExpect(status().isOk());
        patchPrice(id, seccode, "{\"price\":97}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(id))
                .andExpect(jsonPath("$.trades").isEmpty());

        postOrder(seccode, "BUY", 97, "4")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trades.length()").value(1))
                .andExpect(jsonPath("$.trades[0].price").value(97))
                .andExpect(jsonPath("$.trades[0].quantity").value(4));

        mockMvc.perform(get("/orders").param("seccode", seccode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].price").value(97))
                .andExpect(jsonPath("$[0].remainingQuantity").value(6));
    }

    @Test
    @DisplayName("Отмена снимает открытый остаток и сохраняет прежнюю сделку")
    void cancelsOpenRemainderAndKeepsEarlierTrade() throws Exception {
        String seccode = "CANCEL-PARTIAL";
        long id = submitSellOrder(seccode, 100, 10);

        postOrder(seccode, "BUY", 101, "4").andExpect(status().isCreated());

        mockMvc.perform(post("/orders/{id}/cancel", id).param("seccode", seccode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/orders").param("seccode", seccode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].quantity").value(10))
                .andExpect(jsonPath("$[0].remainingQuantity").value(0))
                .andExpect(jsonPath("$[0].status").value("CANCELLED"));

        postOrder(seccode, "BUY", 101, "6")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trades").isEmpty());

        mockMvc.perform(get("/trades").param("seccode", seccode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].price").value(100))
                .andExpect(jsonPath("$[0].quantity").value(4));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.5", "null"})
    @DisplayName("Некорректное количество лотов даёт HTTP 400")
    void rejectsInvalidQuantities(String quantity) throws Exception {
        postOrder("INVALID", "BUY", 100, quantity)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Создание заявки по неизвестному инструменту даёт HTTP 404")
    void rejectsOrdersForUnknownInstrument() throws Exception {
        postOrder("UNKNOWN-INSTRUMENT", "BUY", 100, "1")
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Создание заявки и изменение цены проверяют шаг цены")
    void checksPriceStepOnSubmissionAndPriceChange() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":%d,"seccode":"DEMO","side":"SELL","price":100.12,"quantity":2}
                                """.formatted(TestInstruments.accountForSide("SELL"))))
                .andExpect(status().isBadRequest());

        long id = submitSellOrder("DEMO", 100, 2);
        patchPrice(id, "DEMO", "{\"price\":100.12}").andExpect(status().isBadRequest());
        patchPrice(id, "DEMO", "{\"price\":100.15}").andExpect(status().isOk());

        mockMvc.perform(get("/orders").param("seccode", "DEMO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].price").value(100.15));
    }

    private long submitSellOrder(String seccode, int price, long quantity) throws Exception {
        String response = postOrder(seccode, "SELL", price, Long.toString(quantity))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(response, "$.orderId");
        return id.longValue();
    }

    private ResultActions patchPrice(long id, String seccode, String body) throws Exception {
        return mockMvc.perform(patch("/orders/{id}", id)
                .param("seccode", seccode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions postOrder(String seccode, String side, int price, String quantity) throws Exception {
        String body = """
                {"accountId":%d,"seccode":"%s","side":"%s","price":%d,"quantity":%s}
                """.formatted(TestInstruments.accountForSide(side), seccode, side, price, quantity);
        return mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
