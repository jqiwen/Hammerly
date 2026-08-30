package com.hammerly.backend.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hammerly.marketplace-cache")
public record MarketplaceCacheProperties(
    boolean enabled,
    Duration auctionTtl,
    Duration listingTtl
) {
}
