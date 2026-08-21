package com.hammerly.backend.repository;

import com.hammerly.backend.util.TimeUtils;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BidRepository {
    private final JdbcTemplate jdbc;

    public BidRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(long auctionId, long bidderId, double amount) {
        jdbc.update("INSERT INTO bids (auction_id, bidder_id, amount) VALUES (?, ?, ?)",
            auctionId, bidderId, BigDecimal.valueOf(amount));
    }

    public void insert(long auctionId, long bidderId, double amount, Instant bidTime) {
        jdbc.update("INSERT INTO bids (auction_id, bidder_id, amount, bid_time) VALUES (?, ?, ?, ?)",
            auctionId, bidderId, BigDecimal.valueOf(amount), bidTime.atOffset(ZoneOffset.UTC));
    }

    public List<BidHistoryRow> history(long auctionId) {
        return jdbc.query("""
            SELECT b.amount, b.bid_time,
                   COALESCE(u.first_name || '***' || SUBSTR(u.last_name, 1, 1), 'User***') AS bidder
            FROM bids b LEFT JOIN users u ON u.id = b.bidder_id
            WHERE b.auction_id = ? ORDER BY b.bid_time DESC LIMIT 12
            """, (rs, row) -> new BidHistoryRow(rs.getString("bidder"), rs.getDouble("amount"),
            TimeUtils.readInstant(rs, "bid_time")), auctionId);
    }

    public List<UserBidRow> findByBidder(long bidderId) {
        return jdbc.query("""
            SELECT a.id AS auction_id, a.title, a.description, a.start_price, a.current_bid,
                   a.image, a.end_time,
                   COALESCE((SELECT COUNT(*) FROM bids all_bids WHERE all_bids.auction_id = a.id), 0) AS total_bids,
                   MAX(b.amount) AS your_bid,
                   COALESCE(s.first_name || ' ' || s.last_name, NULL) AS seller_name,
                   COALESCE(s.avatar_image, NULL) AS seller_avatar
            FROM bids b JOIN auctions a ON b.auction_id = a.id
            LEFT JOIN users s ON s.id = a.seller_id
            WHERE b.bidder_id = ? GROUP BY a.id, s.id ORDER BY a.end_time DESC
            """, (rs, row) -> new UserBidRow(rs.getLong("auction_id"), rs.getString("title"),
            rs.getString("description"), rs.getDouble("start_price"), rs.getDouble("current_bid"),
            rs.getString("image"), TimeUtils.readInstant(rs, "end_time"), rs.getLong("total_bids"),
            rs.getDouble("your_bid"), rs.getString("seller_name"), rs.getString("seller_avatar")), bidderId);
    }

    public boolean seedBidExists(long auctionId, long bidderId, double amount, Instant bidTime) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM bids WHERE auction_id = ? AND bidder_id = ? " +
            "AND amount = ? AND bid_time = ?", Integer.class, auctionId, bidderId,
            BigDecimal.valueOf(amount), bidTime.atOffset(ZoneOffset.UTC));
        return count != null && count > 0;
    }

    public void deleteByAuction(long auctionId) {
        jdbc.update("DELETE FROM bids WHERE auction_id = ?", auctionId);
    }

    public record BidHistoryRow(String bidder, double amount, String time) {
    }

    public record UserBidRow(long auctionId, String title, String description, double startPrice,
                             double currentBid, String image, String endTime, long totalBids,
                             double yourBid, String sellerName, String sellerAvatar) {
    }
}
