package com.hammerly.backend;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hammerly.backend.config.DatabaseInitializer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
    "hammerly.database.seed=true",
    "hammerly.debug-endpoint.enabled=true",
    "spring.datasource.hikari.data-source-properties.sslmode=disable",
    "hammerly.marketplace-cache.enabled=false",
    "hammerly.kafka.enabled=false",
    "hammerly.ai.internal-token=test-internal-token",
    "hammerly.internal-token-required=true",
    "jwt.secret=test-secret-compatible-with-node-jsonwebtoken"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class HammerlyApiIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        org.testcontainers.utility.DockerImageName.parse("pgvector/pgvector:pg16")
            .asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired DatabaseInitializer databaseInitializer;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void resetDatabase() {
        databaseInitializer.clearAllDataAndReseed();
    }

    @Test
    void healthAndPublicAuctionQueriesPreserveResponseShapes() throws Exception {
        long auctionId = auctionId("Vintage Pocket Watch");
        mvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("Backend is running"));
        mvc.perform(get("/api/auctions/get-top"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(2))))
            .andExpect(jsonPath("$.data[0].timeRemaining", not(blankOrNullString())))
            .andExpect(jsonPath("$.stats.activeLots").value(greaterThanOrEqualTo(2)));
        mvc.perform(get("/api/auctions/get/{id}", auctionId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(auctionId))
            .andExpect(jsonPath("$.data.sellerId").isNumber())
            .andExpect(jsonPath("$.data.bidHistory[0].amount").value(225));
        mvc.perform(get("/api/auctions/search").param("q", "Pocket").param("page", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void explicitDemoSeedIsIdempotentAndPreservesRealAuctions() throws Exception {
        long realAuctionId = auctionId("Vintage Pocket Watch");

        runDemoSeed();
        runDemoSeed();

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM auctions a JOIN users u ON u.id = a.seller_id
            WHERE u.email LIKE 'demo-seller-%@hammerly.example'
            """, Long.class)).isEqualTo(100L);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForMap("""
            SELECT
              COUNT(*) FILTER (WHERE a.status = 'active' AND a.start_time <= now() AND a.end_time > now()) AS active,
              COUNT(*) FILTER (WHERE a.status = 'active' AND a.start_time > now()) AS upcoming,
              COUNT(*) FILTER (WHERE a.status = 'ended') AS ended
            FROM auctions a JOIN users u ON u.id = a.seller_id
            WHERE u.email LIKE 'demo-seller-%@hammerly.example'
            """)).containsEntry("active", 70L).containsEntry("upcoming", 15L).containsEntry("ended", 15L);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM auctions WHERE id = ?", Long.class, realAuctionId)).isEqualTo(1L);
    }

    @Test
    void auctionSearchPaginatesSummariesAndExcludesUpcomingRows() throws Exception {
        runDemoSeed();

        MvcResult firstPage = mvc.perform(get("/api/auctions/search").param("page", "1").param("size", "12"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data", hasSize(12)))
            .andExpect(jsonPath("$.total").value(72))
            .andExpect(jsonPath("$.limit").value(12))
            .andExpect(jsonPath("$.totalPages").value(6))
            .andExpect(jsonPath("$.data[0].description").doesNotExist())
            .andExpect(jsonPath("$.data[0].sellerId").doesNotExist())
            .andReturn();
        for (JsonNode auction : body(firstPage).path("data")) {
            org.assertj.core.api.Assertions.assertThat(Instant.parse(auction.path("startTime").asText()))
                .isBeforeOrEqualTo(Instant.now());
        }
    }

    @Test
    void auctionListReturnsAValidEmptyPage() throws Exception {
        jdbc.update("UPDATE auctions SET status = 'ended'");

        mvc.perform(get("/api/auctions/search").param("page", "1").param("size", "12"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(0)))
            .andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void registrationLoginAndDuplicateValidationWork() throws Exception {
        JsonNode registered = register("auth-flow@example.com", "password123");
        String token = registered.get("token").asText();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("firstName", "Test", "lastName", "User", "email", "auth-flow@example.com",
                    "password", "password123", "phone", "555-0100"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Email is already in use"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "auth-flow@example.com", "password", "password123"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.token", not(blankOrNullString())))
            .andExpect(jsonPath("$.user.email").value("auth-flow@example.com"));
        mvc.perform(get("/api/users/profile").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.firstName").value("Test"));
    }

    @Test
    void invalidCredentialsAndMissingJwtReturnLegacyErrors() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "seller1@hammerly.com", "password", "wrong"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid email or password"));
        mvc.perform(get("/api/users/profile"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("No token provided"));
        mvc.perform(get("/api/users/profile").header("Authorization", "Bearer invalid-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid or expired token"));
    }

    @Test
    void auctionCanBeCreatedAndAppearsInSellerListings() throws Exception {
        JsonNode account = register("seller-new@example.com", "password123");
        String token = account.get("token").asText();
        long userId = account.path("user").path("id").asLong();
        MvcResult created = mvc.perform(post("/api/auctions/create")
                .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("title", "Integration Camera", "category", "Collectibles",
                    "description", "A tested listing", "sellerId", userId, "startingPrice", 125,
                    "duration", 7, "condition", "Excellent"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.title").value("Integration Camera"))
            .andReturn();
        long auctionId = body(created).path("data").path("id").asLong();
        mvc.perform(get("/api/auctions/get/{id}", auctionId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.currentBid").value(125));
        mvc.perform(get("/api/users/my-auctions").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.auctions[0].title").value("Integration Camera"))
            .andExpect(jsonPath("$.auctions[0].startingPrice").value(125));
    }

    @Test
    void placingBidUpdatesAuctionHistoryAndMyBids() throws Exception {
        String token = login("bidder1@hammerly.com", "password123");
        long auctionId = auctionId("Vintage Pocket Watch");
        mvc.perform(get("/api/auctions/{id}/bid", auctionId).param("bidAmount", "250")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Bid placed successfully"))
            .andExpect(jsonPath("$.data.currentBid").value(250))
            .andExpect(jsonPath("$.data.totalBids").value(2));
        mvc.perform(get("/api/auctions/get/{id}", auctionId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.bidHistory[0].amount").value(250));
        mvc.perform(get("/api/users/my-bids").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bids[0].yourBid").value(250))
            .andExpect(jsonPath("$.bids[0].status").value("winning"));
    }

    @Test
    void watchUnwatchAndWatchlistFlowWorks() throws Exception {
        String token = login("bidder1@hammerly.com", "password123");
        long auctionId = auctionId("Signed First Edition Novel");
        mvc.perform(post("/api/auctions/watch/{id}", auctionId).header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Item added to watchlist"));
        mvc.perform(post("/api/auctions/watch/{id}", auctionId).header("Authorization", bearer(token)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Item already in watchlist"));
        mvc.perform(get("/api/auctions/is-watched/{id}", auctionId).header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.isWatched").value(true));
        mvc.perform(get("/api/auctions/get-watchlist").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value(auctionId));
        mvc.perform(delete("/api/auctions/unwatch/{id}", auctionId).header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Item removed from watchlist"));
        mvc.perform(get("/api/auctions/is-watched/{id}", auctionId).header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.isWatched").value(false));
    }

    @Test
    void profilePasswordAndAvatarFlowsWork() throws Exception {
        JsonNode account = register("profile@example.com", "password123");
        String token = account.get("token").asText();
        mvc.perform(put("/api/users/profile").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("firstName", "Updated", "lastName", "Person",
                    "email", "profile-updated@example.com", "phone", "555-0199"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.firstName").value("Updated"))
            .andExpect(jsonPath("$.user.phone").value("555-0199"));
        mvc.perform(put("/api/users/profile/avatar").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("avatarImage", "data:image/png;base64,abc"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.avatarImage").value("data:image/png;base64,abc"));
        mvc.perform(delete("/api/users/profile/avatar").header("Authorization", bearer(token)))
            .andExpect(status().isOk());
        mvc.perform(put("/api/users/profile/password").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("currentPassword", "password123",
                    "newPassword", "new-password", "confirmPassword", "new-password"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Password updated successfully"));
        login("profile-updated@example.com", "new-password");
    }

    @Test
    void paymentMethodAddListDefaultAndDeleteFlowWorks() throws Exception {
        String token = register("payments@example.com", "password123").get("token").asText();
        MvcResult first = addCard(token, "Visa", "4111 1111 1111 1111", false);
        long firstId = body(first).path("paymentMethod").path("id").asLong();
        MvcResult second = addCard(token, "Mastercard", "5555 5555 5555 4444", false);
        long secondId = body(second).path("paymentMethod").path("id").asLong();
        mvc.perform(put("/api/users/profile/payment-methods/{id}/default", secondId)
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Default payment method updated"));
        mvc.perform(get("/api/users/profile/payment-methods").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paymentMethods", hasSize(2)))
            .andExpect(jsonPath("$.paymentMethods[0].id").value(secondId))
            .andExpect(jsonPath("$.paymentMethods[0].cardNumber").value("5555555555554444"));
        mvc.perform(delete("/api/users/profile/payment-methods/{id}", firstId)
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Payment method deleted"));
    }

    @Test
    void sellerCanEndAndDeleteOwnedAuction() throws Exception {
        String token = login("seller2@hammerly.com", "password123");
        long auctionId = auctionId("Signed First Edition Novel");
        mvc.perform(patch("/api/auctions/end/{id}", auctionId).header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Auction ended successfully"));
        mvc.perform(delete("/api/auctions/delete/{id}", auctionId).header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Auction deleted successfully"));
        mvc.perform(get("/api/auctions/get/{id}", auctionId))
            .andExpect(status().isNotFound());
    }

    @Test
    void debugAndPublicUserEndpointsRemainAvailableLocally() throws Exception {
        long sellerId = jdbc.queryForObject("SELECT id FROM users WHERE email = 'seller1@hammerly.com'", Long.class);
        mvc.perform(get("/api/auth/"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.totalTables").value(5));
        mvc.perform(get("/api/users/{id}", sellerId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.firstName").value("John"))
            .andExpect(jsonPath("$.user.email").doesNotExist());
    }

    @Test
    void protectedKnowledgeIngestionCreatesPendingDocumentAndOutboxAtomically() throws Exception {
        String payload = json(Map.of(
            "title", "Hammerly Support Guide",
            "source", "docs/knowledge-base/hammerly-support.md",
            "content", "Bids must be higher than the current bid on an active auction."));

        mvc.perform(post("/internal/knowledge/documents")
                .contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isUnauthorized());

        MvcResult created = mvc.perform(post("/internal/knowledge/documents")
                .header("X-Hammerly-Internal-Token", "test-internal-token")
                .contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andReturn();
        String documentId = body(created).path("id").asText();

        mvc.perform(get("/internal/knowledge/documents/{id}", documentId)
                .header("X-Hammerly-Internal-Token", "test-internal-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(documentId));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM outbox_events WHERE aggregate_id = ?::uuid AND published_at IS NULL",
            Long.class, documentId)).isEqualTo(1L);

        mvc.perform(post("/internal/knowledge/documents")
                .header("X-Hammerly-Internal-Token", "test-internal-token")
                .contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.id").value(documentId));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM outbox_events WHERE aggregate_id = ?::uuid", Long.class, documentId))
            .isEqualTo(1L);
    }

    private MvcResult addCard(String token, String cardType, String number, boolean isDefault) throws Exception {
        return mvc.perform(post("/api/users/profile/payment-methods").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("cardType", cardType,
                    "cardNumber", number, "expiryMonth", 12, "expiryYear", 2030,
                    "cardholderName", "Test User", "isDefault", isDefault))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.paymentMethod.cardType").value(cardType))
            .andReturn();
    }

    private JsonNode register(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("firstName", "Test", "lastName", "User", "email", email,
                    "password", password, "phone", "555-0100"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn();
        return body(result);
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", password))))
            .andExpect(status().isOk())
            .andReturn();
        return body(result).path("token").asText();
    }

    private long auctionId(String title) {
        return jdbc.queryForObject("SELECT id FROM auctions WHERE title = ?", Long.class, title);
    }

    private void runDemoSeed() throws Exception {
        Path current = Path.of("").toAbsolutePath();
        Path seedFile = null;
        for (int level = 0; level < 4 && current != null; level++) {
            Path candidate = current.resolve("scripts").resolve("seed-demo-auctions.sql");
            if (Files.isRegularFile(candidate)) {
                seedFile = candidate;
                break;
            }
            current = current.getParent();
        }
        if (seedFile == null) throw new IllegalStateException("scripts/seed-demo-auctions.sql not found");
        try (Connection connection = java.util.Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                new EncodedResource(new FileSystemResource(seedFile), java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
