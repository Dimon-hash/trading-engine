package com.example.tradingengine.order;

import com.example.tradingengine.TradingEngineApplication;
import com.example.tradingengine.account.AccountView;
import com.example.tradingengine.instrument.InstrumentCatalog;
import com.example.tradingengine.model.Instrument;
import com.example.tradingengine.model.InstrumentType;
import com.example.tradingengine.model.QuoteSnapshot;
import com.example.tradingengine.model.Side;
import com.example.tradingengine.model.Trade;
import com.example.tradingengine.order.service.OrderService;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradingPersistenceTest {

    private static final String DEMO = "DEMO";
    private static final long BUYER_ID = 1L;
    private static final long SELLER_ID = 2L;

    @TempDir
    Path databaseDirectory;

    private TestDatabase database;

    @BeforeEach
    void configureDatabase() {
        if (Boolean.getBoolean("trading.test.postgres")) {
            String url = requiredEnvironmentVariable("TRADING_TEST_POSTGRES_URL");
            if (!url.startsWith("jdbc:postgresql:")) {
                throw new IllegalArgumentException("TRADING_TEST_POSTGRES_URL must be a PostgreSQL JDBC URL");
            }
            String schema = "trading_test_" + UUID.randomUUID().toString().replace("-", "");
            database = new TestDatabase(url, requiredEnvironmentVariable("TRADING_TEST_POSTGRES_USER"),
                    requiredEnvironmentVariable("TRADING_TEST_POSTGRES_PASSWORD"), schema);
        } else {
            String url = "jdbc:h2:file:" + databaseDirectory.resolve("trading").toAbsolutePath()
                    + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
            database = new TestDatabase(url, "sa", "", null);
        }
    }

    @AfterEach
    void dropPostgresTestSchema() throws SQLException {
        if (database == null || database.schema() == null) {
            return;
        }
        try (var connection = DriverManager.getConnection(database.url(), database.user(), database.password());
             var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + database.schema() + " CASCADE");
        }
    }

    @Test
    @Tag("postgresql")
    @DisplayName("Перезапуск сохраняет сделки, резервы и приоритет частично исполненной заявки")
    void restoresTradingStateAndQueuePriorityAfterRestart() {
        TradingSnapshot beforeRestart;
        long repricedSell;
        long earlierAtPrice;
        long reservedBuy;

        try (var app = startApplication()) {
            var trading = app.getBean(OrderService.class);
            trading.submit(SELLER_ID, DEMO, Side.SELL, price("99"), 1);
            trading.submit(BUYER_ID, DEMO, Side.BUY, price("99"), 1);

            repricedSell = trading.submit(SELLER_ID, DEMO, Side.SELL, price("100"), 2).orderId();
            earlierAtPrice = trading.submit(SELLER_ID, DEMO, Side.SELL, price("100"), 2).orderId();
            trading.changePrice(DEMO, repricedSell, price("101"));
            trading.changePrice(DEMO, repricedSell, price("100"));
            trading.submit(BUYER_ID, DEMO, Side.BUY, price("100"), 1);
            reservedBuy = trading.submit(BUYER_ID, DEMO, Side.BUY, price("95"), 2).orderId();

            beforeRestart = snapshot(trading);
            assertThat(beforeRestart.trades()).hasSize(2);
            assertThat(beforeRestart.buyer().reservedBalance()).isEqualByComparingTo("1900");
            assertThat(beforeRestart.seller().positions().getFirst().reservedQuantity())
                    .isEqualTo(BigInteger.valueOf(30));
            assertThat(findOrder(trading, earlierAtPrice).remainingQuantity()).isEqualTo(1);
        }

        try (var app = startApplication()) {
            var trading = app.getBean(OrderService.class);
            assertThat(snapshot(trading)).isEqualTo(beforeRestart);

            var purchase = trading.submit(BUYER_ID, DEMO, Side.BUY, price("100"), 1);
            assertThat(purchase.orderId()).isGreaterThan(reservedBuy);
            assertThat(purchase.trades()).singleElement().satisfies(trade ->
                    assertThat(trade.id()).isGreaterThan(beforeRestart.trades().getLast().id()));
            assertThat(findOrder(trading, earlierAtPrice).status()).isEqualTo(OrderView.Status.FILLED);
            assertThat(findOrder(trading, repricedSell).remainingQuantity()).isEqualTo(2);

            trading.cancel(DEMO, reservedBuy);
            assertThat(trading.findAccount(BUYER_ID).reservedBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Test
    @DisplayName("Ошибка записи позиции откатывает заявку, сделку и изменения баланса")
    void rollsBackAllChangesWhenPositionWriteFails() {
        try (var app = startApplication()) {
            var trading = app.getBean(OrderService.class);
            var jdbc = app.getBean(JdbcClient.class);
            trading.submit(SELLER_ID, DEMO, Side.SELL, price("100"), 1);
            TradingSnapshot before = snapshot(trading);

            jdbc.sql("""
                    ALTER TABLE positions ADD CONSTRAINT reject_buyer_position CHECK (account_id <> 1)
                    """).update();

            assertThatThrownBy(() -> trading.submit(BUYER_ID, DEMO, Side.BUY, price("100"), 1))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(snapshot(trading)).isEqualTo(before);

            jdbc.sql("ALTER TABLE positions DROP CONSTRAINT reject_buyer_position").update();
            assertThat(trading.submit(BUYER_ID, DEMO, Side.BUY, price("100"), 1).trades()).hasSize(1);
            assertThat(trading.findTrades(DEMO)).hasSize(1);
        }
    }

    @Test
    @DisplayName("Два экземпляра приложения не могут одновременно потратить один баланс")
    void preventsOverspendingAcrossApplicationInstances() throws Exception {
        try (var first = startApplication(); var second = startApplication();
             var executor = Executors.newFixedThreadPool(2)) {
            String otherInstrument = "SECOND";
            first.getBean(InstrumentCatalog.class).register(
                    new Instrument(otherInstrument, InstrumentType.SECURITY, 10, price("0.05")));

            var firstTrading = first.getBean(OrderService.class);
            var secondTrading = second.getBean(OrderService.class);
            var start = new CountDownLatch(1);
            var firstOrder = executor.submit(() -> {
                start.await();
                return submitAgainstSharedBalance(firstTrading, DEMO);
            });
            var secondOrder = executor.submit(() -> {
                start.await();
                return submitAgainstSharedBalance(secondTrading, otherInstrument);
            });

            start.countDown();
            assertThat(List.of(firstOrder.get(5, TimeUnit.SECONDS), secondOrder.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
            assertThat(firstTrading.findAccount(BUYER_ID).availableBalance()).isEqualByComparingTo("4000");
            assertThat(secondTrading.findAccount(BUYER_ID).reservedBalance()).isEqualByComparingTo("6000");
            assertThat(firstTrading.findOrders(DEMO).size() + secondTrading.findOrders(otherInstrument).size())
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Повтор после перезапуска возвращает сделки из первого ответа")
    void replaysOriginalTradesAfterRestart() {
        String key = "match-once";
        var request = new CreateOrderRequest(BUYER_ID, DEMO, Side.BUY, price("101"), 2);
        OrderSubmission original;
        TradingSnapshot beforeRestart;

        try (var app = startApplication()) {
            var trading = app.getBean(OrderService.class);
            trading.submit(SELLER_ID, DEMO, Side.SELL, price("100"), 1);
            original = trading.submit(request, key);
            assertThat(original.trades()).hasSize(1);

            trading.submit(SELLER_ID, DEMO, Side.SELL, price("101"), 1);
            beforeRestart = snapshot(trading);
            assertThat(beforeRestart.trades()).hasSize(2);
        }

        try (var app = startApplication()) {
            var trading = app.getBean(OrderService.class);
            assertThat(trading.submit(request, key)).isEqualTo(original);
            assertThat(snapshot(trading)).isEqualTo(beforeRestart);
        }
    }

    @Test
    @Tag("postgresql")
    @DisplayName("Параллельные POST с одним ключом создают одну заявку и один резерв")
    void serializesConcurrentRetriesAcrossInstances() throws Exception {
        try (var first = startApplication(WebApplicationType.SERVLET);
             var second = startApplication(WebApplicationType.SERVLET);
             var executor = Executors.newFixedThreadPool(2)) {
            var request = new CreateOrderRequest(BUYER_ID, DEMO, Side.BUY, price("95"), 1);
            var start = new CountDownLatch(1);
            var firstOrder = executor.submit(() -> {
                start.await();
                return postOrder(first, request);
            });
            var secondOrder = executor.submit(() -> {
                start.await();
                return postOrder(second, request);
            });

            start.countDown();
            var firstResponse = firstOrder.get(10, TimeUnit.SECONDS);
            var secondResponse = secondOrder.get(10, TimeUnit.SECONDS);
            assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(firstResponse.getBody()).isNotNull().isEqualTo(secondResponse.getBody());

            var trading = first.getBean(OrderService.class);
            assertThat(trading.findOrders(DEMO)).singleElement().satisfies(order ->
                    assertThat(order.id()).isEqualTo(firstResponse.getBody().orderId()));
            assertThat(second.getBean(OrderService.class).findAccount(BUYER_ID).reservedBalance())
                    .isEqualByComparingTo("950");
        }
    }

    @Test
    @Tag("postgresql")
    @DisplayName("Ошибка записи сделки откатывает изменения заявок и счетов")
    void rollsBackTradingStateWhenTradeWriteFails() {
        try (var app = startApplication()) {
            var trading = app.getBean(OrderService.class);
            var jdbc = app.getBean(JdbcClient.class);
            trading.submit(SELLER_ID, DEMO, Side.SELL, price("100"), 1);
            TradingSnapshot before = snapshot(trading);

            jdbc.sql("ALTER TABLE trades ADD CONSTRAINT reject_test_trade CHECK (price <> 100)").update();

            assertThatThrownBy(() -> trading.submit(BUYER_ID, DEMO, Side.BUY, price("100"), 2))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(snapshot(trading)).isEqualTo(before);

            jdbc.sql("ALTER TABLE trades DROP CONSTRAINT reject_test_trade").update();
            assertThat(trading.submit(BUYER_ID, DEMO, Side.BUY, price("100"), 2).trades()).hasSize(1);
            assertThat(trading.findAccount(BUYER_ID).reservedBalance()).isEqualByComparingTo("1000");
        }
    }

    @Test
    @DisplayName("Ошибка записи ключа откатывает заявку, сделку и изменения счетов")
    void rollsBackOrderWhenIdempotencyWriteFails() {
        try (var app = startApplication()) {
            var trading = app.getBean(OrderService.class);
            var jdbc = app.getBean(JdbcClient.class);
            trading.submit(SELLER_ID, DEMO, Side.SELL, price("100"), 1);
            TradingSnapshot before = snapshot(trading);

            jdbc.sql("""
                    ALTER TABLE order_idempotency ADD CONSTRAINT reject_test_key
                    CHECK (idempotency_key <> 'forced-rollback')
                    """).update();

            var request = new CreateOrderRequest(BUYER_ID, DEMO, Side.BUY, price("100"), 2);
            assertThatThrownBy(() -> trading.submit(request, "forced-rollback"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(snapshot(trading)).isEqualTo(before);

            jdbc.sql("ALTER TABLE order_idempotency DROP CONSTRAINT reject_test_key").update();
            OrderSubmission retry = trading.submit(request, "forced-rollback");
            assertThat(retry.trades()).hasSize(1);
            assertThat(trading.findAccount(BUYER_ID).reservedBalance()).isEqualByComparingTo("1000");
            assertThat(trading.submit(request, "forced-rollback")).isEqualTo(retry);
        }
    }

    private ConfigurableApplicationContext startApplication() {
        return startApplication(WebApplicationType.NONE);
    }

    private ConfigurableApplicationContext startApplication(WebApplicationType webType) {
        var arguments = new ArrayList<>(List.of(
                "--spring.datasource.url=" + database.connectionUrl(),
                "--spring.datasource.username=" + database.user(),
                "--spring.datasource.password=" + database.password(),
                "--spring.main.banner-mode=off",
                "--server.address=127.0.0.1",
                "--server.port=0"));
        if (database.schema() != null) {
            arguments.add("--spring.flyway.default-schema=" + database.schema());
        }
        return new SpringApplicationBuilder(TradingEngineApplication.class)
                .web(webType)
                .run(arguments.toArray(String[]::new));
    }

    private static ResponseEntity<OrderSubmission> postOrder(
            ConfigurableApplicationContext app, CreateOrderRequest request
    ) {
        int port = ((ServletWebServerApplicationContext) app).getWebServer().getPort();
        return RestClient.create("http://127.0.0.1:" + port)
                .post()
                .uri("/orders")
                .header("Idempotency-Key", "parallel")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(OrderSubmission.class);
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing PostgreSQL test setting " + name);
        }
        return value;
    }

    private static int submitAgainstSharedBalance(OrderService trading, String seccode) {
        try {
            trading.submit(BUYER_ID, seccode, Side.BUY, price("600"), 1);
            return 201;
        } catch (ResponseStatusException exception) {
            return exception.getStatusCode().value();
        }
    }

    private static TradingSnapshot snapshot(OrderService trading) {
        return new TradingSnapshot(trading.findOrders(DEMO), trading.findTrades(DEMO), trading.findQuotes(DEMO),
                trading.findAccount(BUYER_ID), trading.findAccount(SELLER_ID));
    }

    private static OrderView findOrder(OrderService trading, long id) {
        return trading.findOrders(DEMO).stream().filter(order -> order.id() == id).findFirst().orElseThrow();
    }

    private static BigDecimal price(String amount) {
        return new BigDecimal(amount);
    }

    private record TestDatabase(String url, String user, String password, String schema) {
        String connectionUrl() {
            if (schema == null) {
                return url;
            }
            return url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
        }
    }

    private record TradingSnapshot(
            List<OrderView> orders, List<Trade> trades, QuoteSnapshot quotes, AccountView buyer, AccountView seller
    ) {}
}
