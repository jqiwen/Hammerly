package com.hammerly.backend.model;

public record AuctionSummary(
    long id,
    String title,
    String category,
    double currentBid,
    String image,
    String condition,
    String status,
    String startTime,
    String endTime,
    String createdAt,
    String seller,
    long totalBids
) {
}
