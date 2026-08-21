package com.hammerly.backend.repository;

import com.hammerly.backend.model.Auction;
import com.hammerly.backend.util.TimeUtils;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WatchlistRepository {
    private final JdbcTemplate jdbc;

    public WatchlistRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isWatched(long userId, long auctionId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM watchlist WHERE user_id = ? AND auction_id = ?",
            Integer.class, userId, auctionId);
        return count != null && count > 0;
    }

    public void add(long userId, long auctionId) {
        jdbc.update("INSERT INTO watchlist (user_id, auction_id) VALUES (?, ?)", userId, auctionId);
    }

    public void remove(long userId, long auctionId) {
        jdbc.update("DELETE FROM watchlist WHERE user_id = ? AND auction_id = ?", userId, auctionId);
    }

    public void deleteByAuction(long auctionId) {
        jdbc.update("DELETE FROM watchlist WHERE auction_id = ?", auctionId);
    }

    public List<Auction> findByUser(long userId) {
        return jdbc.query("""
            SELECT a.id, a.title, a.category, a.description, a.start_price, a.current_bid,
                   a.image, a.condition, a.seller_id, a.status, a.start_time,
                   a.end_time, a.created_at,
                   COALESCE(u.first_name || ' ' || u.last_name, NULL) AS seller,
                   COUNT(b.id) AS total_bids
            FROM auctions a INNER JOIN watchlist w ON a.id = w.auction_id
            LEFT JOIN users u ON u.id = a.seller_id
            LEFT JOIN bids b ON b.auction_id = a.id
            WHERE w.user_id = ? GROUP BY a.id, u.id, w.created_at ORDER BY w.created_at DESC
            """, (rs, row) -> new Auction(rs.getLong("id"), rs.getString("title"),
            rs.getString("category"), rs.getString("description"), rs.getDouble("start_price"),
            rs.getDouble("current_bid"), rs.getString("image"), rs.getString("condition"),
            rs.getLong("seller_id"), rs.getString("status"), TimeUtils.readInstant(rs, "start_time"),
            TimeUtils.readInstant(rs, "end_time"), TimeUtils.readInstant(rs, "created_at"),
            rs.getString("seller"), rs.getLong("total_bids")), userId);
    }
}
