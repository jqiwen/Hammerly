package com.hammerly.backend.repository;

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
            auctionId, bidderId, amount);
    }

    public void insert(long auctionId, long bidderId, double amount, String bidTime) {
        jdbc.update("INSERT INTO bids (auction_id, bidder_id, amount, bidTime) VALUES (?, ?, ?, ?)",
            auctionId, bidderId, amount, bidTime);
    }

    public List<BidHistoryRow> history(long auctionId) {
        return jdbc.query("""
            SELECT b.amount, b.bidTime,
                   COALESCE(u.firstName || '***' || SUBSTR(u.lastName, 1, 1), 'User***') AS bidder
            FROM bids b LEFT JOIN users u ON u.id = b.bidder_id
            WHERE b.auction_id = ? ORDER BY b.bidTime DESC LIMIT 12
            """, (rs, row) -> new BidHistoryRow(rs.getString("bidder"), rs.getDouble("amount"),
            rs.getString("bidTime")), auctionId);
    }

    public List<UserBidRow> findByBidder(long bidderId) {
        return jdbc.query("""
            SELECT a.id AS auctionId, a.title, a.description, a.startPrice, a.currentBid,
                   a.image, a.endTime,
                   COALESCE((SELECT COUNT(*) FROM bids all_bids WHERE all_bids.auction_id = a.id), 0) AS totalBids,
                   MAX(b.amount) AS yourBid,
                   COALESCE(s.firstName || ' ' || s.lastName, NULL) AS sellerName,
                   COALESCE(s.avatarImage, NULL) AS sellerAvatar
            FROM bids b JOIN auctions a ON b.auction_id = a.id
            LEFT JOIN users s ON s.id = a.seller_id
            WHERE b.bidder_id = ? GROUP BY a.id ORDER BY a.endTime DESC
            """, (rs, row) -> new UserBidRow(rs.getLong("auctionId"), rs.getString("title"),
            rs.getString("description"), rs.getDouble("startPrice"), rs.getDouble("currentBid"),
            rs.getString("image"), rs.getString("endTime"), rs.getLong("totalBids"),
            rs.getDouble("yourBid"), rs.getString("sellerName"), rs.getString("sellerAvatar")), bidderId);
    }

    public boolean seedBidExists(long auctionId, long bidderId, double amount, String bidTime) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM bids WHERE auction_id = ? AND bidder_id = ? " +
            "AND amount = ? AND bidTime = ?", Integer.class, auctionId, bidderId, amount, bidTime);
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
