package com.example.tradingengine.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:idempotency-${random.uuid};"
        + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrderIdempotencyTest {

    private final MockMvc mvc;

    @Autowired
    OrderIdempotencyTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    @DisplayName("Повтор при равной цене возвращает исходный ответ и сохраняет один резерв")
    void replaysSameOrderAndConflictsOnChangedQuantity() throws Exception {
        String key = "buy-one";
        String first = submit(key, 1, "BUY", "95", 1)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String retry = submit(key, 1, "BUY", "95.00", 1)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(retry).isEqualTo(first);

        submit(key, 1, "BUY", "95", 2).andExpect(status().isConflict());
        expectOrderCount(1);
        mvc.perform(get("/accounts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalance").value(9050))
                .andExpect(jsonPath("$.reservedBalance").value(950));
    }

    @Test
    @DisplayName("Одинаковый ключ на разных счетах создаёт две независимые заявки")
    void scopesKeysToAccount() throws Exception {
        submit("shared-key", 1, "BUY", "95", 1).andExpect(status().isCreated());
        submit("shared-key", 2, "SELL", "100", 1).andExpect(status().isCreated());

        expectOrderCount(2);
    }

    @Test
    @DisplayName("Некорректный ключ возвращает HTTP 400 до создания заявки")
    void rejectsInvalidKeyBeforeCreatingOrder() throws Exception {
        submit("invalid key", 1, "BUY", "95", 1).andExpect(status().isBadRequest());
        expectOrderCount(0);
    }

    private ResultActions submit(String key, long account, String side, String price, int quantity) throws Exception {
        return mvc.perform(post("/orders")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"accountId":%d,"seccode":"DEMO","side":"%s","price":%s,"quantity":%d}
                        """.formatted(account, side, price, quantity)));
    }

    private void expectOrderCount(int count) throws Exception {
        mvc.perform(get("/orders").param("seccode", "DEMO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(count));
    }
}
