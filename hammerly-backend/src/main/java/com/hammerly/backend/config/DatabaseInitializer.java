package com.hammerly.backend.config;

import com.hammerly.backend.model.User;
import com.hammerly.backend.repository.AuctionRepository;
import com.hammerly.backend.repository.BidRepository;
import com.hammerly.backend.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DatabaseInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final AuctionRepository auctions;
    private final BidRepository bids;
    private final PasswordEncoder passwordEncoder;
    private final boolean seedEnabled;

    public DatabaseInitializer(JdbcTemplate jdbc, UserRepository users, AuctionRepository auctions,
                               BidRepository bids, PasswordEncoder passwordEncoder,
                               @Value("${hammerly.database.seed}") boolean seedEnabled) {
        this.jdbc = jdbc;
        this.users = users;
        this.auctions = auctions;
        this.bids = bids;
        this.passwordEncoder = passwordEncoder;
        this.seedEnabled = seedEnabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (seedEnabled) {
            seedDatabase();
        }
        log.info("PostgreSQL schema is ready; Flyway migrations have been applied");
    }

    @Transactional
    public void seedDatabase() {
        Map<String, Long> userIds = Map.of(
            "seller1@hammerly.com", upsertSeedUser("John", "Seller", "seller1@hammerly.com", "+1 647 000 0001"),
            "seller2@hammerly.com", upsertSeedUser("Jane", "Dealer", "seller2@hammerly.com", "+1 647 000 0002"),
            "bidder1@hammerly.com", upsertSeedUser("Taylor", "Bidder", "bidder1@hammerly.com", "")
        );

        Instant now = Instant.now();
        long watchId = upsertSeedAuction("Vintage Pocket Watch", "Collectibles",
            "Working mechanical pocket watch with original chain.", 150, 225,
            "Very Good", userIds.get("seller1@hammerly.com"), now, now.plus(72, ChronoUnit.HOURS));
        upsertSeedAuction("Signed First Edition Novel", "Books",
            "Signed first edition with protective sleeve.", 80, 120,
            "Good", userIds.get("seller2@hammerly.com"), now, now.plus(48, ChronoUnit.HOURS));

        long bidderId = userIds.get("bidder1@hammerly.com");
        Instant bidTime = Instant.parse("2026-03-17T12:00:00.000Z");
        if (!bids.seedBidExists(watchId, bidderId, 225, bidTime)) {
            bids.insert(watchId, bidderId, 225, bidTime);
        }
    }

    @Transactional
    public void clearAllDataAndReseed() {
        jdbc.update("DELETE FROM outbox_events");
        jdbc.update("DELETE FROM knowledge_chunks");
        jdbc.update("DELETE FROM knowledge_documents");
        jdbc.update("UPDATE knowledge_base_state SET version = 0, updated_at = now() WHERE id = 1");
        jdbc.update("DELETE FROM watchlist");
        jdbc.update("DELETE FROM payment_methods");
        jdbc.update("DELETE FROM bids");
        jdbc.update("DELETE FROM auctions");
        jdbc.update("DELETE FROM users");
        seedDatabase();
    }

    private long upsertSeedUser(String firstName, String lastName, String email, String phone) {
        String password = passwordEncoder.encode("password123");
        User existing = users.findByEmail(email).orElse(null);
        if (existing != null) {
            users.updateSeedUser(existing.id(), firstName, lastName, password, phone, "");
            return existing.id();
        }
        return users.insert(email, password, firstName, lastName, phone, "");
    }

    private long upsertSeedAuction(String title, String category, String description,
                                   double startPrice, double currentBid, String condition,
                                   long sellerId, Instant startTime, Instant endTime) {
        Long existing = auctions.findSeedAuction(title, sellerId).orElse(null);
        if (existing == null) {
            existing = auctions.insert(title, category, description, startPrice, "/images/picture.jpg",
                condition, sellerId, startTime, endTime);
        }
        auctions.updateSeedAuction(existing, category, description, startPrice, currentBid,
            "/images/picture.jpg", condition, startTime, endTime);
        return existing;
    }
}
