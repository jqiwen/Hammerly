package com.hammerly.backend.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public final class AuctionDtos {
    private AuctionDtos() {
    }

    public record CreateAuctionRequest(
        String title,
        String category,
        String description,
        JsonNode sellerId,
        JsonNode seller_id,
        JsonNode startPrice,
        JsonNode startingPrice,
        JsonNode reservePrice,
        JsonNode duration,
        List<String> images,
        String condition,
        String image,
        String shippingOption,
        JsonNode shippingCost,
        String endTime
    ) {
    }
}
