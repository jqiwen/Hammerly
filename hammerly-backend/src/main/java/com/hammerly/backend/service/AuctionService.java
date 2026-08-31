package com.hammerly.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hammerly.backend.dto.AuctionDtos.CreateAuctionRequest;
import com.hammerly.backend.cache.MarketplaceCache;
import com.hammerly.backend.exception.ApiException;
import com.hammerly.backend.model.Auction;
import com.hammerly.backend.model.AuctionSummary;
import com.hammerly.backend.repository.AuctionRepository;
import com.hammerly.backend.repository.AuctionRepository.OwnerRow;
import com.hammerly.backend.repository.BidRepository;
import com.hammerly.backend.repository.UserRepository;
import com.hammerly.backend.repository.WatchlistRepository;
import com.hammerly.backend.util.TimeUtils;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuctionService {
    private static final Logger log = LoggerFactory.getLogger(AuctionService.class);
    private static final int TOP_LIMIT = 12;
    private static final int DEFAULT_PAGE_SIZE = 12;
    private static final int MAX_PAGE_SIZE = 50;
    private final AuctionRepository auctions;
    private final BidRepository bids;
    private final WatchlistRepository watchlist;
    private final UserRepository users;
    private final MarketplaceCache cache;

    public AuctionService(AuctionRepository auctions, BidRepository bids,
                          WatchlistRepository watchlist, UserRepository users,
                          MarketplaceCache cache) {
        this.auctions = auctions;
        this.bids = bids;
        this.watchlist = watchlist;
        this.users = users;
        this.cache = cache;
    }

    public Map<String, Object> top() {
        long requestStartedAt = System.nanoTime();
        long redisStartedAt = System.nanoTime();
        var cached = cache.getTop();
        long redisMillis = elapsedMillis(redisStartedAt);
        if (cached.isPresent()) {
            Map<String, Object> response = cached.orElseThrow();
            logListLatency("get-top", true, redisMillis, 0, requestStartedAt,
                listResultCount(response));
            return response;
        }
        long databaseStartedAt = System.nanoTime();
        Map<String, Object> rawStats = auctions.activeStats();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("activeLots", number(rawStats.get("activeLots")).longValue());
        stats.put("totalValue", number(rawStats.get("totalValue")).doubleValue());
        stats.put("averageBid", Math.round(number(rawStats.get("averageBid")).doubleValue()));
        stats.put("completedToday", 32);
        Map<String, Object> response = responseWithData(
            auctions.findTop(TOP_LIMIT).stream().map(this::mapSummary).toList(), stats);
        long databaseMillis = elapsedMillis(databaseStartedAt);
        cache.putTop(response);
        logListLatency("get-top", false, redisMillis, databaseMillis, requestStartedAt,
            listResultCount(response));
        return response;
    }

    public Map<String, Object> get(String rawId) {
        long id = parseId(rawId);
        var cached = cache.getAuction(id);
        if (cached.isPresent()) return cached.orElseThrow();
        Auction auction = auctions.findById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Auction not found"));
        Map<String, Object> data = mapAuction(auction);
        data.put("bidHistory", bids.history(id).stream().map(row -> {
            Map<String, Object> bid = new LinkedHashMap<>();
            bid.put("bidder", row.bidder());
            bid.put("amount", row.amount());
            bid.put("time", row.time());
            return bid;
        }).toList());
        Map<String, Object> response = successData(data);
        cache.putAuction(id, response);
        return response;
    }

    public Map<String, Object> related(String rawId) {
        long id = parseId(rawId);
        String category = auctions.findCategory(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Auction not found"));
        return successData(auctions.findRelated(id, category).stream().map(this::mapSummary).toList());
    }

    public Map<String, Object> search(String query, String rawPage, String rawSize) {
        long requestStartedAt = System.nanoTime();
        String resolvedQuery = query == null ? "" : query.trim();
        int page = parsePage(rawPage);
        int limit = parsePageSize(rawSize);
        int offset = Math.max(0, (page - 1) * limit);
        boolean cacheable = resolvedQuery.isBlank() && page == 1 && limit == DEFAULT_PAGE_SIZE;
        long redisMillis = 0;
        if (cacheable) {
            long redisStartedAt = System.nanoTime();
            var cached = cache.getFirstPage();
            redisMillis = elapsedMillis(redisStartedAt);
            if (cached.isPresent()) {
                Map<String, Object> response = cached.orElseThrow();
                logListLatency("search", true, redisMillis, 0, requestStartedAt,
                    listResultCount(response));
                return response;
            }
        }
        long databaseStartedAt = System.nanoTime();
        long total = auctions.countSearch(resolvedQuery);
        Map<String, Object> response = successData(
            auctions.search(resolvedQuery, limit, offset).stream().map(this::mapSummary).toList());
        response.put("total", total);
        response.put("page", page);
        response.put("totalPages", (int) Math.ceil((double) total / limit));
        response.put("limit", limit);
        long databaseMillis = elapsedMillis(databaseStartedAt);
        if (cacheable) cache.putFirstPage(response);
        logListLatency("search", false, redisMillis, databaseMillis, requestStartedAt,
            listResultCount(response));
        return response;
    }

    @Transactional
    public Map<String, Object> placeBid(String rawId, String rawBidAmount, long userId) {
        double amount;
        try {
            amount = Double.parseDouble(rawBidAmount == null ? "" : rawBidAmount);
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid bid amount");
        }
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid bid amount");
        }
        long id = parseLongOrNotFound(rawId);
        if (!auctions.lockById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Auction not found");
        }
        Auction auction = auctions.findById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Auction not found"));
        if (!"active".equals(auction.status())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Auction is not active");
        }
        if (TimeUtils.parse(auction.startTime()).isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Auction has not started");
        }
        if (!TimeUtils.parse(auction.endTime()).isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Auction has ended");
        }
        if (auction.sellerId() == userId) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Sellers cannot bid on their own auctions");
        }
        if (amount <= auction.currentBid()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bid amount must be higher than the current bid");
        }
        auctions.updateCurrentBid(id, amount);
        bids.insert(id, userId, amount);
        cache.invalidateAuctionAfterCommit(id);
        cache.invalidateListingsAfterCommit();
        Map<String, Object> response = successMessage("Bid placed successfully");
        response.put("data", auctions.findById(id).map(this::mapAuction).orElse(null));
        return response;
    }

    public Map<String, Object> watch(String rawId, long userId) {
        long id = parseId(rawId);
        if (users.findById(userId).isEmpty()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "User not found. Please log in again.");
        }
        if (!auctions.exists(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                "Auction is not stored in database. This item cannot be watched yet.");
        }
        if (watchlist.isWatched(userId, id)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Item already in watchlist");
        }
        watchlist.add(userId, id);
        return successMessage("Item added to watchlist");
    }

    public Map<String, Object> unwatch(String rawId, long userId) {
        long id = parseId(rawId);
        if (!watchlist.isWatched(userId, id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Item not in watchlist");
        }
        watchlist.remove(userId, id);
        return successMessage("Item removed from watchlist");
    }

    public Map<String, Object> watchlist(long userId) {
        return successData(watchlist.findByUser(userId).stream().map(this::mapAuction).toList());
    }

    public Map<String, Object> isWatched(String rawId, long userId) {
        long id;
        try {
            id = Long.parseLong(rawId);
        } catch (NumberFormatException exception) {
            id = -1;
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("isWatched", id > 0 && watchlist.isWatched(userId, id));
        return response;
    }

    @Transactional
    public Map<String, Object> create(CreateAuctionRequest request, long userId) {
        JsonNode sellerNode = firstNonNull(request.sellerId(), request.seller_id());
        if (sellerNode != null) {
            double sellerNumber = parseNumber(sellerNode);
            if (!Double.isFinite(sellerNumber) || sellerNumber <= 0 || sellerNumber != Math.rint(sellerNumber)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "sellerId/seller_id must be a valid positive number");
            }
            if ((long) sellerNumber != userId) {
                throw new ApiException(HttpStatus.FORBIDDEN, "sellerId does not match authenticated user");
            }
        }

        double startPrice = parseNumber(firstNonNull(request.startPrice(), request.startingPrice()));
        String endTime = request.endTime();
        if (endTime == null || endTime.isEmpty()) {
            double duration = parseNumber(request.duration());
            if (Double.isFinite(duration) && duration > 0) {
                endTime = Instant.now().plus((long) (duration * 86_400_000), ChronoUnit.MILLIS).toString();
            }
        }
        if (missing(request.title()) || missing(request.category()) || !Double.isFinite(startPrice) || missing(endTime)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                "Missing required fields: title, category, startingPrice/startPrice, and duration/endTime");
        }
        if (startPrice <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "startingPrice/startPrice must be a positive number");
        }

        Instant parsedEnd;
        try {
            parsedEnd = TimeUtils.parse(endTime);
        } catch (DateTimeParseException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                "Invalid endTime format. Use ISO 8601 format (e.g., 2026-03-15T18:30:00Z)");
        }
        if (!parsedEnd.isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Auction end time must be in the future");
        }

        String image = truthy(request.image()) ? request.image() : firstImage(request.images());
        if (!truthy(image)) image = "/images/picture.jpg";
        String description = composeDescription(request);
        Instant startTime = Instant.now();
        long id = auctions.insert(request.title(), request.category(), description, startPrice, image,
            truthy(request.condition()) ? request.condition() : null, userId, startTime, parsedEnd);
        cache.invalidateListingsAfterCommit();
        Map<String, Object> response = successMessage("Auction created successfully");
        response.put("data", auctions.findById(id).map(this::mapAuction).orElse(null));
        return response;
    }

    @Transactional
    public Map<String, Object> end(String rawId, long userId) {
        long id = parsePositiveId(rawId);
        OwnerRow auction = auctions.findOwner(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Auction not found"));
        if (auction.sellerId() != userId) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only end your own auctions");
        }
        if ("ended".equals(auction.status())) {
            return successMessage("Auction is already ended");
        }
        auctions.end(id);
        cache.invalidateAuctionAfterCommit(id);
        cache.invalidateListingsAfterCommit();
        return successMessage("Auction ended successfully");
    }

    @Transactional
    public Map<String, Object> delete(String rawId, long userId) {
        long id = parsePositiveId(rawId);
        OwnerRow auction = auctions.findOwner(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Auction not found"));
        if (auction.sellerId() != userId) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only delete your own auctions");
        }
        bids.deleteByAuction(id);
        watchlist.deleteByAuction(id);
        auctions.delete(id);
        cache.invalidateAuctionAfterCommit(id);
        cache.invalidateListingsAfterCommit();
        return successMessage("Auction deleted successfully");
    }

    private Map<String, Object> mapAuction(Auction auction) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", auction.id());
        result.put("title", auction.title());
        result.put("category", auction.category());
        result.put("description", auction.description());
        result.put("startPrice", auction.startPrice());
        result.put("currentBid", auction.currentBid());
        result.put("image", auction.image());
        result.put("condition", auction.condition());
        result.put("seller_id", auction.sellerId());
        result.put("sellerId", auction.sellerId());
        result.put("status", auction.status());
        result.put("startTime", auction.startTime());
        result.put("endTime", auction.endTime());
        result.put("createdAt", auction.createdAt());
        result.put("seller", truthy(auction.seller()) ? auction.seller() : "Seller " + auction.sellerId());
        result.put("totalBids", auction.totalBids());
        result.put("timeRemaining", TimeUtils.timeRemaining(auction.endTime()));
        result.put("progress", TimeUtils.progress(auction.startTime(), auction.endTime()));
        return result;
    }

    private Map<String, Object> mapSummary(AuctionSummary auction) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", auction.id());
        result.put("title", auction.title());
        result.put("category", auction.category());
        result.put("currentBid", auction.currentBid());
        result.put("image", auction.image());
        result.put("condition", auction.condition());
        result.put("status", auction.status());
        result.put("startTime", auction.startTime());
        result.put("endTime", auction.endTime());
        result.put("createdAt", auction.createdAt());
        result.put("seller", truthy(auction.seller()) ? auction.seller() : "Seller");
        result.put("totalBids", auction.totalBids());
        result.put("timeRemaining", TimeUtils.timeRemaining(auction.endTime()));
        result.put("progress", TimeUtils.progress(auction.startTime(), auction.endTime()));
        return result;
    }

    private String composeDescription(CreateAuctionRequest request) {
        StringBuilder value = new StringBuilder(truthy(request.description()) ? request.description() : "");
        appendIfTruthy(value, "\nReserve Price: ", request.reservePrice());
        if (truthy(request.shippingOption())) value.append("\nShipping Option: ").append(request.shippingOption());
        appendIfTruthy(value, "\nShipping Cost: ", request.shippingCost());
        String result = value.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private void appendIfTruthy(StringBuilder target, String label, JsonNode value) {
        if (value != null && !value.isNull() && !(value.isNumber() && value.asDouble() == 0) &&
            !(value.isTextual() && value.asText().isEmpty())) {
            target.append(label).append(value.isTextual() ? value.asText() : value.toString());
        }
    }

    private Map<String, Object> responseWithData(Object data, Object stats) {
        Map<String, Object> response = successData(data);
        response.put("stats", stats);
        return response;
    }

    private Map<String, Object> successData(Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", data);
        return response;
    }

    private Map<String, Object> successMessage(String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", message);
        return response;
    }

    private Number number(Object value) {
        return value instanceof Number number ? number : 0;
    }

    private int parsePage(String rawPage) {
        if (rawPage == null || rawPage.isEmpty()) return 1;
        try {
            int page = Integer.parseInt(rawPage);
            return page == 0 ? 1 : page;
        } catch (NumberFormatException exception) {
            return 1;
        }
    }

    private int parsePageSize(String rawSize) {
        if (rawSize == null || rawSize.isBlank()) return DEFAULT_PAGE_SIZE;
        try {
            return Math.clamp(Integer.parseInt(rawSize), 1, MAX_PAGE_SIZE);
        } catch (NumberFormatException exception) {
            return DEFAULT_PAGE_SIZE;
        }
    }

    private long elapsedMillis(long startedAtNanos) {
        return Math.round((System.nanoTime() - startedAtNanos) / 1_000_000.0);
    }

    private int listResultCount(Map<String, Object> response) {
        Object data = response.get("data");
        return data instanceof List<?> list ? list.size() : 0;
    }

    private void logListLatency(String endpoint, boolean cacheHit, long redisMillis,
                                long databaseMillis, long requestStartedAt, int resultCount) {
        log.info("auction_list_latency endpoint={} cacheHit={} redisMs={} dbMs={} totalMs={} resultCount={}",
            endpoint, cacheHit, redisMillis, databaseMillis, elapsedMillis(requestStartedAt), resultCount);
    }

    private long parseId(String rawId) {
        try {
            return Long.parseLong(rawId);
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid auction id");
        }
    }

    private long parsePositiveId(String rawId) {
        long id = parseId(rawId);
        if (id <= 0) throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid auction id");
        return id;
    }

    private long parseLongOrNotFound(String rawId) {
        try {
            return Long.parseLong(rawId);
        } catch (NumberFormatException exception) {
            return Long.MIN_VALUE;
        }
    }

    private double parseNumber(JsonNode node) {
        if (node == null || node.isNull()) return Double.NaN;
        if (node.isNumber()) return node.asDouble();
        if (node.isBoolean()) return node.asBoolean() ? 1 : 0;
        try {
            String text = node.asText();
            return text.isBlank() ? 0 : Double.parseDouble(text);
        } catch (NumberFormatException exception) {
            return Double.NaN;
        }
    }

    private JsonNode firstNonNull(JsonNode first, JsonNode second) {
        return first != null && !first.isNull() ? first : second != null && !second.isNull() ? second : null;
    }

    private String firstImage(List<String> images) {
        return images == null || images.isEmpty() ? null : images.getFirst();
    }

    private boolean missing(String value) {
        return value == null || value.isEmpty();
    }

    private boolean truthy(String value) {
        return value != null && !value.isEmpty();
    }
}
