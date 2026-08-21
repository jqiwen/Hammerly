package com.hammerly.backend.repository;

import com.hammerly.backend.model.Auction;
import com.hammerly.backend.util.TimeUtils;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.ZoneOffset;
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
        SELECT a.id, a.title, a.category, a.description, a.start_price, a.current_bid,
               a.image, a.condition, a.seller_id, a.status, a.start_time,
               a.end_time, a.created_at,
               COALESCE(u.first_name || ' ' || u.last_name, NULL) AS seller,
               COUNT(b.id) AS total_bids
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
            WHERE a.status = 'active' AND a.end_time > CURRENT_TIMESTAMP
            GROUP BY a.id, u.id ORDER BY a.created_at DESC LIMIT 6
            """, this::mapAuction);
    }

    public Map<String, Object> activeStats() {
        return jdbc.queryForMap("""
            SELECT COUNT(*) AS "activeLots",
                   COALESCE(SUM(current_bid), 0) AS "totalValue",
                   COALESCE(AVG(current_bid), 0) AS "averageBid"
            FROM auctions WHERE status = 'active' AND end_time > CURRENT_TIMESTAMP
            """);
    }

    public Optional<Auction> findById(long id) {
        return jdbc.query(SELECT_AUCTION + " WHERE a.id = ? GROUP BY a.id, u.id", this::mapAuction, id)
            .stream().findFirst();
    }

    public Optional<String> findCategory(long id) {
        return jdbc.query("SELECT category FROM auctions WHERE id = ?", (rs, row) -> rs.getString(1), id)
            .stream().findFirst();
    }

    public List<Auction> findRelated(long id, String category) {
        return jdbc.query(SELECT_AUCTION + """
            WHERE a.category = ? AND a.id != ? AND a.status = 'active' AND a.end_time > CURRENT_TIMESTAMP
            GROUP BY a.id, u.id ORDER BY a.created_at DESC LIMIT 4
            """, this::mapAuction, category, id);
    }

    public long countSearch(String query) {
        if (query.isBlank()) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM auctions a " +
                "WHERE a.status = 'active' AND a.end_time > CURRENT_TIMESTAMP", Long.class);
            return count == null ? 0 : count;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM auctions a " +
                "WHERE a.status = 'active' AND a.end_time > CURRENT_TIMESTAMP " +
                "AND a.title ILIKE ?", Long.class, "%" + query + "%");
        return count == null ? 0 : count;
    }

    public List<Auction> search(String query, int limit, int offset) {
        String sql = SELECT_AUCTION + " WHERE a.status = 'active' AND a.end_time > CURRENT_TIMESTAMP ";
        List<Object> parameters = new ArrayList<>();
        if (!query.isBlank()) {
            sql += "AND a.title ILIKE ? ";
            parameters.add("%" + query + "%");
        }
        sql += "GROUP BY a.id, u.id ORDER BY a.created_at DESC LIMIT ? OFFSET ?";
        parameters.add(limit);
        parameters.add(offset);
        return jdbc.query(sql, this::mapAuction, parameters.toArray());
    }

    public long insert(String title, String category, String description, double startPrice,
                       String image, String condition, long sellerId, Instant startTime, Instant endTime) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO auctions
                    (title, category, description, start_price, current_bid, image, condition,
                     seller_id, start_time, end_time, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'active')
                """, new String[] {"id"});
            statement.setString(1, title);
            statement.setString(2, category);
            statement.setString(3, description);
            statement.setBigDecimal(4, BigDecimal.valueOf(startPrice));
            statement.setBigDecimal(5, BigDecimal.valueOf(startPrice));
            statement.setString(6, image);
            statement.setString(7, condition);
            statement.setLong(8, sellerId);
            statement.setObject(9, startTime.atOffset(ZoneOffset.UTC));
            statement.setObject(10, endTime.atOffset(ZoneOffset.UTC));
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void updateCurrentBid(long id, double amount) {
        jdbc.update("UPDATE auctions SET current_bid = ? WHERE id = ?", BigDecimal.valueOf(amount), id);
    }

    public boolean lockById(long id) {
        return !jdbc.query("SELECT id FROM auctions WHERE id = ? FOR UPDATE",
            (rs, row) -> rs.getLong(1), id).isEmpty();
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
        jdbc.update("UPDATE auctions SET status = 'ended', end_time = CURRENT_TIMESTAMP WHERE id = ?", id);
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM auctions WHERE id = ?", id);
    }

    public List<ListingRow> findBySeller(long sellerId) {
        return jdbc.query("""
            SELECT a.id, a.title, a.description, a.start_price, a.current_bid, a.image, a.status,
                   a.start_time, a.end_time, a.created_at,
                   COALESCE((SELECT COUNT(*) FROM bids b WHERE b.auction_id = a.id), 0) AS total_bids,
                   COALESCE((SELECT COUNT(*) FROM watchlist w WHERE w.auction_id = a.id), 0) AS total_watchers
            FROM auctions a WHERE a.seller_id = ? ORDER BY a.created_at DESC
            """, (rs, row) -> new ListingRow(rs.getLong("id"), rs.getString("title"),
            rs.getString("description"), rs.getDouble("start_price"), rs.getDouble("current_bid"),
            rs.getString("image"), rs.getString("status"), TimeUtils.readInstant(rs, "start_time"),
            TimeUtils.readInstant(rs, "end_time"), TimeUtils.readInstant(rs, "created_at"),
            rs.getLong("total_bids"), rs.getLong("total_watchers")), sellerId);
    }

    public Optional<Long> findSeedAuction(String title, long sellerId) {
        return jdbc.query("SELECT id FROM auctions WHERE title = ? AND seller_id = ?",
            (rs, row) -> rs.getLong(1), title, sellerId).stream().findFirst();
    }

    public void updateSeedAuction(long id, String category, String description, double startPrice,
                                  double currentBid, String image, String condition, Instant startTime,
                                  Instant endTime) {
        jdbc.update("""
            UPDATE auctions SET category = ?, description = ?, start_price = ?, current_bid = ?,
                image = ?, condition = ?, status = 'active', start_time = ?, end_time = ? WHERE id = ?
            """, category, description, BigDecimal.valueOf(startPrice), BigDecimal.valueOf(currentBid),
            image, condition, startTime.atOffset(ZoneOffset.UTC), endTime.atOffset(ZoneOffset.UTC), id);
    }

    private Auction mapAuction(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Auction(rs.getLong("id"), rs.getString("title"), rs.getString("category"),
            rs.getString("description"), rs.getDouble("start_price"), rs.getDouble("current_bid"),
            rs.getString("image"), rs.getString("condition"), rs.getLong("seller_id"),
            rs.getString("status"), TimeUtils.readInstant(rs, "start_time"),
            TimeUtils.readInstant(rs, "end_time"), TimeUtils.readInstant(rs, "created_at"),
            rs.getString("seller"), rs.getLong("total_bids"));
    }

    public record OwnerRow(long id, long sellerId, String status) {
    }

    public record ListingRow(long id, String title, String description, double startPrice,
                             double currentBid, String image, String status, String startTime,
                             String endTime, String createdAt, long totalBids, long totalWatchers) {
    }
}
