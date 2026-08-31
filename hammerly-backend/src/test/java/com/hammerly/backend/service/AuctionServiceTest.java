package com.hammerly.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hammerly.backend.cache.MarketplaceCache;
import com.hammerly.backend.model.Auction;
import com.hammerly.backend.repository.AuctionRepository;
import com.hammerly.backend.repository.BidRepository;
import com.hammerly.backend.repository.UserRepository;
import com.hammerly.backend.repository.WatchlistRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AuctionServiceTest {
    @Test
    void firstPageCacheHitSkipsPostgres() {
        AuctionRepository auctions = mock(AuctionRepository.class);
        MarketplaceCache cache = mock(MarketplaceCache.class);
        Map<String, Object> cached = Map.of(
            "success", true, "data", List.of(Map.of("id", 7)), "total", 1,
            "page", 1, "totalPages", 1, "limit", 12);
        when(cache.getFirstPage()).thenReturn(Optional.of(cached));
        AuctionService service = service(auctions, mock(BidRepository.class), cache);

        assertThat(service.search("", "1", "12")).isSameAs(cached);

        verifyNoInteractions(auctions);
    }

    @Test
    void emptyFirstPageFallsBackToPostgresAndIsCached() {
        AuctionRepository auctions = mock(AuctionRepository.class);
        MarketplaceCache cache = mock(MarketplaceCache.class);
        when(cache.getFirstPage()).thenReturn(Optional.empty());
        when(auctions.countSearch("")).thenReturn(0L);
        when(auctions.search("", 12, 0)).thenReturn(List.of());
        AuctionService service = service(auctions, mock(BidRepository.class), cache);

        Map<String, Object> response = service.search("", "1", "12");

        assertThat(response).containsEntry("total", 0L).containsEntry("limit", 12);
        assertThat((List<?>) response.get("data")).isEmpty();
        verify(cache).putFirstPage(response);
    }

    @Test
    void successfulBidInvalidatesDetailAndListingCaches() {
        AuctionRepository auctions = mock(AuctionRepository.class);
        BidRepository bids = mock(BidRepository.class);
        MarketplaceCache cache = mock(MarketplaceCache.class);
        Instant now = Instant.now();
        Auction auction = new Auction(9, "Camera", "Photography", "Test", 100, 120,
            "/images/picture.jpg", "Excellent", 2, "active",
            now.minus(1, ChronoUnit.DAYS).toString(), now.plus(1, ChronoUnit.DAYS).toString(),
            now.toString(), "Demo Seller", 0);
        when(auctions.lockById(9)).thenReturn(true);
        when(auctions.findById(9)).thenReturn(Optional.of(auction));
        AuctionService service = service(auctions, bids, cache);

        service.placeBid("9", "130", 3);

        verify(auctions).updateCurrentBid(9, 130);
        verify(bids).insert(9, 3, 130);
        verify(cache).invalidateAuctionAfterCommit(9);
        verify(cache).invalidateListingsAfterCommit();
    }

    @Test
    void statusTransitionInvalidatesDetailAndListingCaches() {
        AuctionRepository auctions = mock(AuctionRepository.class);
        MarketplaceCache cache = mock(MarketplaceCache.class);
        when(auctions.findOwner(11)).thenReturn(Optional.of(
            new AuctionRepository.OwnerRow(11, 4, "active")));
        AuctionService service = service(auctions, mock(BidRepository.class), cache);

        service.end("11", 4);

        verify(auctions).end(11);
        verify(cache).invalidateAuctionAfterCommit(11);
        verify(cache).invalidateListingsAfterCommit();
    }

    private AuctionService service(AuctionRepository auctions, BidRepository bids,
                                   MarketplaceCache cache) {
        return new AuctionService(auctions, bids, mock(WatchlistRepository.class),
            mock(UserRepository.class), cache);
    }
}
