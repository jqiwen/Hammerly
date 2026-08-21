package com.hammerly.backend.config;

import com.hammerly.backend.model.User;
import com.hammerly.backend.repository.AuctionRepository;
import com.hammerly.backend.repository.BidRepository;
import com.hammerly.backend.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
    public void run(ApplicationArguments args) {
        initializeSchema();
        if (seedEnabled) {
            seedDatabase();
        }
        log.info("SQLite schema is ready; existing local data was preserved");
    }

    public void initializeSchema() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS users (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              firstName TEXT NOT NULL,
              lastName TEXT NOT NULL,
              email TEXT UNIQUE NOT NULL,
              password TEXT NOT NULL,
              phone TEXT DEFAULT '',
              avatarImage TEXT DEFAULT '/images/user.jpg',
              createdAt DATETIME DEFAULT CURRENT_TIMESTAMP,
              updatedAt DATETIME DEFAULT CURRENT_TIMESTAMP
            )
            """);
        addColumn("users", "phone", "TEXT DEFAULT ''");
        addColumn("users", "avatarImage", "TEXT DEFAULT '/images/user.jpg'");
        addTimestampColumn("users", "updatedAt");

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS auctions (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              title TEXT NOT NULL,
              category TEXT NOT NULL,
              description TEXT,
              startPrice REAL NOT NULL,
              currentBid REAL NOT NULL,
              image TEXT,
              condition TEXT,
              seller_id INTEGER NOT NULL,
              status TEXT DEFAULT 'active',
              startTime DATETIME DEFAULT CURRENT_TIMESTAMP,
              endTime DATETIME NOT NULL,
              createdAt DATETIME DEFAULT CURRENT_TIMESTAMP,
              FOREIGN KEY (seller_id) REFERENCES users(id)
            )
            """);
        addColumn("auctions", "status", "TEXT DEFAULT 'active'");
        addTimestampColumn("auctions", "startTime");
        addTimestampColumn("auctions", "createdAt");

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS bids (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              auction_id INTEGER NOT NULL,
              bidder_id INTEGER NOT NULL,
              amount REAL NOT NULL,
              bidTime DATETIME DEFAULT CURRENT_TIMESTAMP,
              createdAt DATETIME DEFAULT CURRENT_TIMESTAMP,
              FOREIGN KEY (auction_id) REFERENCES auctions(id),
              FOREIGN KEY (bidder_id) REFERENCES users(id)
            )
            """);
        addTimestampColumn("bids", "bidTime");
        addTimestampColumn("bids", "createdAt");

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS watchlist (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              user_id INTEGER NOT NULL,
              auction_id INTEGER NOT NULL,
              createdAt DATETIME DEFAULT CURRENT_TIMESTAMP,
              UNIQUE(user_id, auction_id),
              FOREIGN KEY (user_id) REFERENCES users(id),
              FOREIGN KEY (auction_id) REFERENCES auctions(id)
            )
            """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS payment_methods (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              user_id INTEGER NOT NULL,
              cardType TEXT NOT NULL,
              cardNumber TEXT DEFAULT '',
              lastFour TEXT NOT NULL,
              expiryMonth INTEGER NOT NULL,
              expiryYear INTEGER NOT NULL,
              cardholderName TEXT NOT NULL,
              isDefault INTEGER DEFAULT 0,
              billingAddress TEXT DEFAULT '',
              billingCity TEXT DEFAULT '',
              billingProvince TEXT DEFAULT '',
              billingPostalCode TEXT DEFAULT '',
              billingCountry TEXT DEFAULT '',
              createdAt DATETIME DEFAULT CURRENT_TIMESTAMP,
              FOREIGN KEY (user_id) REFERENCES users(id)
            )
            """);
        addColumn("payment_methods", "cardNumber", "TEXT DEFAULT ''");
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
        String bidTime = "2026-03-17T12:00:00.000Z";
        if (!bids.seedBidExists(watchId, bidderId, 225, bidTime)) {
            bids.insert(watchId, bidderId, 225, bidTime);
        }
    }

    public void clearAllDataAndReseed() {
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
                condition, sellerId, startTime.toString(), endTime.toString());
        }
        auctions.updateSeedAuction(existing, category, description, startPrice, currentBid,
            "/images/picture.jpg", condition, startTime.toString(), endTime.toString());
        return existing;
    }

    private void addTimestampColumn(String table, String column) {
        if (columnExists(table, column)) return;
        jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " DATETIME");
        jdbc.update("UPDATE " + table + " SET " + column + " = CURRENT_TIMESTAMP WHERE " + column + " IS NULL");
    }

    private void addColumn(String table, String column, String definition) {
        if (!columnExists(table, column)) {
            jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private boolean columnExists(String table, String column) {
        List<String> names = jdbc.query("PRAGMA table_info(" + table + ")",
            (rs, row) -> rs.getString("name"));
        return names.stream().anyMatch(column::equalsIgnoreCase);
    }
}
