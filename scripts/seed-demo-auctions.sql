BEGIN;

SET LOCAL search_path TO hammerly, public;

-- These reserved .example addresses are the durable marker for this seed. If
-- one already belongs to an unexpected user, abort instead of adopting it.
-- A conflict deliberately produces division by zero and rolls back the whole
-- transaction. This pure-SQL assertion also works in JDBC script runners.
SELECT 1 / CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END AS demo_seller_identity_check
FROM users
WHERE email IN (
    'demo-seller-01@hammerly.example',
    'demo-seller-02@hammerly.example',
    'demo-seller-03@hammerly.example',
    'demo-seller-04@hammerly.example'
)
  AND (first_name <> 'Hammerly Demo' OR last_name NOT LIKE 'Seller %');

INSERT INTO users (first_name, last_name, email, password, phone, avatar_image)
VALUES
    ('Hammerly Demo', 'Seller 01', 'demo-seller-01@hammerly.example', '$2a$10$I8c3IFAfwOmMZUIufnXwfOQ/p1dDZiULV49ss8xCzKFu7ps.Hxj3.', '', '/images/user.jpg'),
    ('Hammerly Demo', 'Seller 02', 'demo-seller-02@hammerly.example', '$2a$10$I8c3IFAfwOmMZUIufnXwfOQ/p1dDZiULV49ss8xCzKFu7ps.Hxj3.', '', '/images/user.jpg'),
    ('Hammerly Demo', 'Seller 03', 'demo-seller-03@hammerly.example', '$2a$10$I8c3IFAfwOmMZUIufnXwfOQ/p1dDZiULV49ss8xCzKFu7ps.Hxj3.', '', '/images/user.jpg'),
    ('Hammerly Demo', 'Seller 04', 'demo-seller-04@hammerly.example', '$2a$10$I8c3IFAfwOmMZUIufnXwfOQ/p1dDZiULV49ss8xCzKFu7ps.Hxj3.', '', '/images/user.jpg')
ON CONFLICT (email) DO NOTHING;

-- The plaintext for this generated BCrypt value was discarded. Demo sellers
-- remain auth-model compatible but cannot be used as shared login accounts.
UPDATE users
SET password = '$2a$10$I8c3IFAfwOmMZUIufnXwfOQ/p1dDZiULV49ss8xCzKFu7ps.Hxj3.'
WHERE email IN (
    'demo-seller-01@hammerly.example',
    'demo-seller-02@hammerly.example',
    'demo-seller-03@hammerly.example',
    'demo-seller-04@hammerly.example'
)
  AND first_name = 'Hammerly Demo'
  AND last_name LIKE 'Seller %';

CREATE TEMP TABLE hammerly_demo_auction_catalog (
    ordinal INTEGER PRIMARY KEY,
    title TEXT NOT NULL,
    category TEXT NOT NULL,
    description TEXT NOT NULL,
    start_price NUMERIC(12, 2) NOT NULL,
    current_bid NUMERIC(12, 2) NOT NULL,
    image TEXT NOT NULL,
    condition TEXT NOT NULL,
    seller_email TEXT NOT NULL,
    status TEXT NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
) ON COMMIT DROP;

WITH item_names AS (
    SELECT item_name, ordinal::INTEGER
    FROM unnest(ARRAY[
        'Apple MacBook Pro 14-inch M3', 'Apple iPhone 15 Pro 256GB',
        'Custom RTX 4080 Gaming PC', 'Nintendo Switch OLED Console',
        'Sony WH-1000XM5 Headphones', 'Apple iPad Pro 12.9-inch',
        'Keychron Q1 Pro Mechanical Keyboard', 'Valve Steam Deck OLED 1TB',
        'Samsung Odyssey OLED Monitor', 'DJI Mini 4 Pro Fly More Combo',
        '1968 Omega Seamaster Automatic', 'First Edition Fantasy Novel Set',
        '1999 Holographic Trading Card Collection', 'Signed Championship Basketball',
        'Mid-Century Travel Poster Archive', 'Limited Pressing Jazz Vinyl Box Set',
        'Antique Sterling Silver Tea Service', 'Vintage Fountain Pen Collection',
        'Commemorative Gold Coin Set', 'Restored 1930s Tabletop Radio',
        'Louis Vuitton Neverfull MM Tote', 'Nike Air Jordan 1 Retro High',
        'Burberry Heritage Trench Coat', 'Tiffany-Style Sterling Bracelet',
        'Gucci Horsebit Leather Loafers', 'Canada Goose Expedition Parka',
        'Vintage Levi''s Type III Jacket', 'Handmade Italian Leather Briefcase',
        'Silk Evening Scarf Collection', '18K Gold Sapphire Pendant',
        'Breville Dual Boiler Espresso Machine', 'Herman Miller Aeron Chair',
        'Walnut Mid-Century Credenza', 'Bowers & Wilkins Speaker Pair',
        'KitchenAid Professional Stand Mixer', 'Dyson V15 Detect Vacuum',
        'Hand-Knotted Persian Wool Rug', 'Le Creuset Cast Iron Cookware Set',
        'Solid Oak Writing Desk', 'Japanese Chef Knife Collection',
        'Trek Fuel EX Mountain Bike', 'Old Town Sportsman Fishing Kayak',
        'Big Agnes Copper Spur Tent', 'Osprey Atmos Hiking Backpack',
        'Weber Summit Kamado Grill', 'Yeti Tundra Haul Cooler',
        'Garmin inReach Mini 2', 'Black Diamond Climbing Rack',
        'Patagonia Fly Fishing Kit', 'Thule Motion Roof Cargo Box',
        'Sony A7 IV Mirrorless Camera', 'Fujifilm X-T5 Camera Body',
        'Canon RF 70-200mm F2.8 Lens', 'Leica M6 35mm Film Camera',
        'Manfrotto Carbon Fiber Tripod', 'Profoto B10 Lighting Duo Kit',
        'Nikon Z 14-24mm F2.8 Lens', 'Epson SureColor Photo Printer',
        'Sekonic Professional Light Meter', 'Pelican Air Camera Case Kit',
        'Fender American Professional Stratocaster', 'Gibson Les Paul Standard 60s',
        'Roland JUNO-X Synthesizer', 'Yamaha Stage Custom Drum Kit',
        'Martin D-18 Acoustic Guitar', 'Universal Audio Apollo Twin X',
        'Technics SL-1200GR Turntable', 'Shure Super 55 Microphone',
        'Moog Subsequent 37 Synthesizer', 'Orange Rockerverb Guitar Amplifier',
        'Scotty Cameron Newport Putter', 'Wilson Pro Staff Tennis Racquets',
        'Specialized S-Works Road Frameset', 'Bauer Vapor Hyperlite Skates',
        'Rawlings Heart of the Hide Glove', 'Titleist T200 Iron Set',
        'Peloton Bike Plus Package', 'Burton Custom Snowboard Setup',
        'Concept2 RowErg with PM5', 'Garmin Forerunner 965 Watch',
        'Original Abstract Coastal Painting', 'Numbered Contemporary Art Print',
        'Hand-Thrown Ceramic Vessel Set', 'Bronze Wildlife Sculpture',
        'Framed Botanical Watercolor Series', 'Studio Blown Glass Centerpiece',
        'Signed Urban Photography Triptych', 'Japanese Woodblock Print',
        'Handwoven Textile Wall Hanging', 'Limited Edition Pop Art Screenprint',
        'Festool Track Saw Package', 'DeWalt 20V MAX Tool Collection',
        'Lie-Nielsen Hand Plane Set', 'Miller Multimatic Welder',
        'Jet Benchtop Wood Lathe', 'Starrett Precision Measuring Set',
        'Milwaukee Packout Workshop Kit', 'Makita Brushless Landscaping Set',
        'Veritas Woodworking Chisel Set', 'Bosch Professional Router Table'
    ]::TEXT[]) WITH ORDINALITY AS names(item_name, ordinal)
), catalog_base AS (
    SELECT
        ordinal,
        item_name AS title,
        (ARRAY[
            'Electronics', 'Collectibles', 'Fashion', 'Home & Living', 'Outdoor',
            'Photography', 'Music', 'Sports', 'Art', 'Tools & Workshop'
        ]::TEXT[])[((ordinal - 1) / 10) + 1] AS category,
        (20 + ((ordinal * 233) % 4981))::NUMERIC(12, 2) AS price,
        (ARRAY['Like New', 'Excellent', 'Very Good', 'Good']::TEXT[])[((ordinal - 1) % 4) + 1] AS item_condition,
        (ARRAY[
            'https://images.unsplash.com/photo-1517336714731-489689fd1ca8?auto=format&fit=crop&w=900&q=80',
            'https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=900&q=80',
            'https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=900&q=80',
            'https://images.unsplash.com/photo-1555041469-a586c61ea9bc?auto=format&fit=crop&w=900&q=80',
            'https://images.unsplash.com/photo-1551632811-561732d1e306?auto=format&fit=crop&w=900&q=80',
            'https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?auto=format&fit=crop&w=900&q=80',
            'https://images.unsplash.com/photo-1511379938547-c1f69419868d?auto=format&fit=crop&w=900&q=80',
            'https://images.unsplash.com/photo-1461896836934-ffe607ba8211?auto=format&fit=crop&w=900&q=80',
            'https://images.unsplash.com/photo-1549490349-8643362247b5?auto=format&fit=crop&w=900&q=80',
            'https://images.unsplash.com/photo-1504148455328-c376907d081c?auto=format&fit=crop&w=900&q=80'
        ]::TEXT[])[((ordinal - 1) / 10) + 1] AS image_url,
        'demo-seller-' || lpad((((ordinal - 1) % 4) + 1)::TEXT, 2, '0') || '@hammerly.example' AS seller_email
    FROM item_names
), timed_catalog AS (
    SELECT *,
        CASE
            WHEN ordinal <= 70 THEN CURRENT_TIMESTAMP - (((ordinal - 1) % 14) + 1) * INTERVAL '1 day'
            WHEN ordinal <= 85 THEN CURRENT_TIMESTAMP + (((ordinal - 71) % 14) + 1) * INTERVAL '1 day'
            ELSE CURRENT_TIMESTAMP - (((ordinal - 85) % 30) + 1) * INTERVAL '1 day'
                 - (((ordinal - 1) % 14) + 1) * INTERVAL '1 day'
        END AS auction_start,
        CASE
            WHEN ordinal <= 70 THEN CURRENT_TIMESTAMP + (1 + ((ordinal * 17) % 720)) * INTERVAL '1 hour'
            WHEN ordinal <= 85 THEN CURRENT_TIMESTAMP + (((ordinal - 71) % 14) + 1) * INTERVAL '1 day'
                 + (2 + ((ordinal * 7) % 25)) * INTERVAL '1 day'
            ELSE CURRENT_TIMESTAMP - (((ordinal - 85) % 30) + 1) * INTERVAL '1 day'
        END AS auction_end
    FROM catalog_base
)
INSERT INTO hammerly_demo_auction_catalog (
    ordinal, title, category, description, start_price, current_bid, image,
    condition, seller_email, status, start_time, end_time, created_at
)
SELECT
    ordinal,
    title,
    category,
    'Professionally presented ' || title || '. The item has been inspected, photographed, and described accurately for the Hammerly demo marketplace.',
    price,
    price,
    CASE title
        WHEN 'Big Agnes Copper Spur Tent' THEN '/demo-auctions/big-agnes-copper-spur-tent.webp'
        WHEN 'Apple MacBook Pro 14-inch M3' THEN '/demo-auctions/macbook-pro-m3.webp'
        WHEN 'Osprey Atmos Hiking Backpack' THEN '/demo-auctions/osprey-atmos-backpack.webp'
        WHEN 'Apple iPhone 15 Pro 256GB' THEN '/demo-auctions/iphone-15-pro.webp'
        WHEN 'Custom RTX 4080 Gaming PC' THEN '/demo-auctions/rtx-4080-gaming-pc.webp'
        ELSE image_url || '&sig=' || ordinal
    END,
    item_condition,
    seller_email,
    CASE WHEN ordinal <= 85 THEN 'active' ELSE 'ended' END,
    auction_start,
    auction_end,
    LEAST(CURRENT_TIMESTAMP - (ordinal % 48) * INTERVAL '1 hour', auction_start - INTERVAL '1 hour')
FROM timed_catalog;

-- Refresh only rows owned by the reserved demo sellers and present in this
-- deterministic catalog. No real user or auction can match this scope.
UPDATE auctions AS auction
SET category = catalog.category,
    description = catalog.description,
    start_price = catalog.start_price,
    current_bid = catalog.current_bid,
    image = catalog.image,
    condition = catalog.condition,
    status = catalog.status,
    start_time = catalog.start_time,
    end_time = catalog.end_time,
    created_at = catalog.created_at
FROM hammerly_demo_auction_catalog AS catalog
JOIN users AS seller ON seller.email = catalog.seller_email
WHERE auction.seller_id = seller.id
  AND auction.title = catalog.title;

INSERT INTO auctions (
    title, category, description, start_price, current_bid, image, condition,
    seller_id, status, start_time, end_time, created_at
)
SELECT
    catalog.title, catalog.category, catalog.description, catalog.start_price,
    catalog.current_bid, catalog.image, catalog.condition, seller.id,
    catalog.status, catalog.start_time, catalog.end_time, catalog.created_at
FROM hammerly_demo_auction_catalog AS catalog
JOIN users AS seller ON seller.email = catalog.seller_email
WHERE NOT EXISTS (
    SELECT 1
    FROM auctions AS existing
    WHERE existing.seller_id = seller.id
      AND existing.title = catalog.title
);

-- Any incorrect total deliberately fails and rolls the seed back. The final
-- SELECT below prints the human-readable distribution after a successful run.
SELECT 1 / CASE
    WHEN COUNT(*) = 100
     AND COUNT(*) FILTER (WHERE auction.status = 'active' AND auction.start_time <= CURRENT_TIMESTAMP AND auction.end_time > CURRENT_TIMESTAMP) = 70
     AND COUNT(*) FILTER (WHERE auction.status = 'active' AND auction.start_time > CURRENT_TIMESTAMP) = 15
     AND COUNT(*) FILTER (WHERE auction.status = 'ended') = 15
    THEN 1 ELSE 0 END AS demo_distribution_check
FROM auctions AS auction
JOIN users AS seller ON seller.id = auction.seller_id
WHERE seller.email LIKE 'demo-seller-%@hammerly.example';

COMMIT;

SELECT
    COUNT(*) AS demo_total,
    COUNT(*) FILTER (WHERE auction.status = 'active' AND auction.start_time <= CURRENT_TIMESTAMP AND auction.end_time > CURRENT_TIMESTAMP) AS active,
    COUNT(*) FILTER (WHERE auction.status = 'active' AND auction.start_time > CURRENT_TIMESTAMP) AS upcoming,
    COUNT(*) FILTER (WHERE auction.status = 'ended') AS ended
FROM hammerly.auctions AS auction
JOIN hammerly.users AS seller ON seller.id = auction.seller_id
WHERE seller.email LIKE 'demo-seller-%@hammerly.example';
