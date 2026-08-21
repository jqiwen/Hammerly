package com.hammerly.backend.repository;

import com.hammerly.backend.model.Auction;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class AuctionRepository {
    private static final String SELECT_AUCTION = """
        SELECT a.id, a.title, a.category, a.description, a.startPrice, a.currentBid,
               a.image, a.condition, a.seller_id AS sellerId, a.status, a.startTime,
               a.endTime, a.createdAt,
               COALESCE(u.firstName || ' ' || u.lastName, NULL) AS seller,
               COUNT(b.id) AS totalBids
        FROM auctions a
        LEFT JOIN users u ON u.id = a.seller_id
        LEFT JOIN bids b ON b.auction_id = a.id
        """;
    private final JdbcTemplate jdbc;

    public AuctionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Auction> findTop() {
        return jdbc.query(SELECT_AUCTION + """
            WHERE a.status = 'active' AND a.endTime > CURRENT_TIMESTAMP
            GROUP BY a.id ORDER BY a.createdAt DESC LIMIT 6
            """, this::mapAuction);
    }

    public Map<String, Object> activeStats() {
        return jdbc.queryForMap("""
            SELECT COUNT(*) AS activeLots,
                   COALESCE(SUM(currentBid), 0) AS totalValue,
                   COALESCE(AVG(currentBid), 0) AS averageBid
            FROM auctions WHERE status = 'active' AND endTime > CURRENT_TIMESTAMP
            """);
    }

    public Optional<Auction> findById(long id) {
        return jdbc.query(SELECT_AUCTION + " WHERE a.id = ? GROUP BY a.id", this::mapAuction, id)
            .stream().findFirst();
    }

    public Optional<String> findCategory(long id) {
        return jdbc.query("SELECT category FROM auctions WHERE id = ?", (rs, row) -> rs.getString(1), id)
            .stream().findFirst();
    }

    public List<Auction> findRelated(long id, String category) {
        return jdbc.query(SELECT_AUCTION + """
            WHERE a.category = ? AND a.id != ? AND a.status = 'active' AND a.endTime > CURRENT_TIMESTAMP
            GROUP BY a.id ORDER BY a.createdAt DESC LIMIT 4
            """, this::mapAuction, category, id);
    }

    public long countSearch(String query) {
        if (query.isBlank()) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM auctions a " +
                "WHERE a.status = 'active' AND a.endTime > CURRENT_TIMESTAMP", Long.class);
            return count == null ? 0 : count;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM auctions a " +
                "WHERE a.status = 'active' AND a.endTime > CURRENT_TIMESTAMP " +
                "AND LOWER(a.title) LIKE LOWER(?)", Long.class, "%" + query + "%");
        return count == null ? 0 : count;
    }

    public List<Auction> search(String query, int limit, int offset) {
        String sql = SELECT_AUCTION + " WHERE a.status = 'active' AND a.endTime > CURRENT_TIMESTAMP ";
        List<Object> parameters = new ArrayList<>();
        if (!query.isBlank()) {
            sql += "AND LOWER(a.title) LIKE LOWER(?) ";
            parameters.add("%" + query + "%");
        }
        sql += "GROUP BY a.id ORDER BY a.createdAt DESC LIMIT ? OFFSET ?";
        parameters.add(limit);
        parameters.add(offset);
        return jdbc.query(sql, this::mapAuction, parameters.toArray());
    }

    public long insert(String title, String category, String description, double startPrice,
                       String image, String condition, long sellerId, String startTime, String endTime) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO auctions
                    (title, category, description, startPrice, currentBid, image, condition,
                     seller_id, startTime, endTime, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'active')
                """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, title);
            statement.setString(2, category);
            statement.setString(3, description);
            statement.setDouble(4, startPrice);
            statement.setDouble(5, startPrice);
            statement.setString(6, image);
            statement.setString(7, condition);
            statement.setLong(8, sellerId);
            statement.setString(9, startTime);
            statement.setString(10, endTime);
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void updateCurrentBid(long id, double amount) {
        jdbc.update("UPDATE auctions SET currentBid = ? WHERE id = ?", amount, id);
    }

    public Optional<OwnerRow> findOwner(long id) {
        return jdbc.query("SELECT id, seller_id, status FROM auctions WHERE id = ?",
            (rs, row) -> new OwnerRow(rs.getLong("id"), rs.getLong("seller_id"), rs.getString("status")), id)
            .stream().findFirst();
    }

    public boolean exists(long id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM auctions WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    public void end(long id) {
        jdbc.update("UPDATE auctions SET status = 'ended', endTime = CURRENT_TIMESTAMP WHERE id = ?", id);
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM auctions WHERE id = ?", id);
    }

    public List<ListingRow> findBySeller(long sellerId) {
        return jdbc.query("""
            SELECT a.id, a.title, a.description, a.startPrice, a.currentBid, a.image, a.status,
                   a.startTime, a.endTime, a.createdAt,
                   COALESCE((SELECT COUNT(*) FROM bids b WHERE b.auction_id = a.id), 0) AS totalBids,
                   COALESCE((SELECT COUNT(*) FROM watchlist w WHERE w.auction_id = a.id), 0) AS totalWatchers
            FROM auctions a WHERE a.seller_id = ? ORDER BY a.createdAt DESC
            """, (rs, row) -> new ListingRow(rs.getLong("id"), rs.getString("title"),
            rs.getString("description"), rs.getDouble("startPrice"), rs.getDouble("currentBid"),
            rs.getString("image"), rs.getString("status"), rs.getString("startTime"),
            rs.getString("endTime"), rs.getString("createdAt"), rs.getLong("totalBids"),
            rs.getLong("totalWatchers")), sellerId);
    }

    public Optional<Long> findSeedAuction(String title, long sellerId) {
        return jdbc.query("SELECT id FROM auctions WHERE title = ? AND seller_id = ?",
            (rs, row) -> rs.getLong(1), title, sellerId).stream().findFirst();
    }

    public void updateSeedAuction(long id, String category, String description, double startPrice,
                                  double currentBid, String image, String condition, String startTime,
                                  String endTime) {
        jdbc.update("""
            UPDATE auctions SET category = ?, description = ?, startPrice = ?, currentBid = ?,
                image = ?, condition = ?, status = 'active', startTime = ?, endTime = ? WHERE id = ?
            """, category, description, startPrice, currentBid, image, condition, startTime, endTime, id);
    }

    private Auction mapAuction(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Auction(rs.getLong("id"), rs.getString("title"), rs.getString("category"),
            rs.getString("description"), rs.getDouble("startPrice"), rs.getDouble("currentBid"),
            rs.getString("image"), rs.getString("condition"), rs.getLong("sellerId"),
            rs.getString("status"), rs.getString("startTime"), rs.getString("endTime"),
            rs.getString("createdAt"), rs.getString("seller"), rs.getLong("totalBids"));
    }

    public record OwnerRow(long id, long sellerId, String status) {
    }

    public record ListingRow(long id, String title, String description, double startPrice,
                             double currentBid, String image, String status, String startTime,
                             String endTime, String createdAt, long totalBids, long totalWatchers) {
    }
}
