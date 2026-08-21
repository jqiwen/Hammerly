package com.hammerly.backend.model;

public record Auction(
    long id,
    String title,
    String category,
    String description,
    double startPrice,
    double currentBid,
    String image,
    String condition,
    long sellerId,
    String status,
    String startTime,
    String endTime,
    String createdAt,
    String seller,
    long totalBids
) {
}
