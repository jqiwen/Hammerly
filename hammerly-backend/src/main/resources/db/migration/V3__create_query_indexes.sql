CREATE INDEX auctions_seller_id_idx
    ON hammerly.auctions (seller_id);

CREATE INDEX auctions_status_end_time_idx
    ON hammerly.auctions (status, end_time);

CREATE INDEX auctions_category_status_end_time_idx
    ON hammerly.auctions (category, status, end_time);

CREATE INDEX bids_auction_id_bid_time_idx
    ON hammerly.bids (auction_id, bid_time DESC);

CREATE INDEX bids_bidder_id_idx
    ON hammerly.bids (bidder_id);

CREATE INDEX watchlist_auction_id_idx
    ON hammerly.watchlist (auction_id);

CREATE INDEX payment_methods_user_order_idx
    ON hammerly.payment_methods (user_id, is_default DESC, created_at DESC);
