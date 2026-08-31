-- Supports the active-list WHERE clause and created_at ordering. start_time and
-- end_time are included so PostgreSQL can reject scheduled/expired rows from the
-- index without fetching every heap tuple.
CREATE INDEX auctions_active_created_at_idx
    ON hammerly.auctions (status, created_at DESC)
    INCLUDE (start_time, end_time);
