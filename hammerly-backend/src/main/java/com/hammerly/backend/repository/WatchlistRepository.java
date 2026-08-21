package com.hammerly.backend.repository;

import com.hammerly.backend.model.Auction;
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
            SELECT a.id, a.title, a.category, a.description, a.startPrice, a.currentBid,
                   a.image, a.condition, a.seller_id AS sellerId, a.status, a.startTime,
                   a.endTime, a.createdAt,
                   COALESCE(u.firstName || ' ' || u.lastName, NULL) AS seller,
                   COUNT(b.id) AS totalBids
            FROM auctions a INNER JOIN watchlist w ON a.id = w.auction_id
            LEFT JOIN users u ON u.id = a.seller_id
            LEFT JOIN bids b ON b.auction_id = a.id
            WHERE w.user_id = ? GROUP BY a.id ORDER BY w.createdAt DESC
            """, (rs, row) -> new Auction(rs.getLong("id"), rs.getString("title"),
            rs.getString("category"), rs.getString("description"), rs.getDouble("startPrice"),
            rs.getDouble("currentBid"), rs.getString("image"), rs.getString("condition"),
            rs.getLong("sellerId"), rs.getString("status"), rs.getString("startTime"),
            rs.getString("endTime"), rs.getString("createdAt"), rs.getString("seller"),
            rs.getLong("totalBids")), userId);
    }
}
