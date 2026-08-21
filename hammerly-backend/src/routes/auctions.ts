import { Router, Request, Response } from 'express';
import { authMiddleware } from '../middleware/auth.js';
import { runQuery, getOne, getAll } from '../db/database.js';

const router = Router();

const auctionStats = {
  activeLots: 10,
  totalValue: 1200000,
  averageBid: 850,
  completedToday: 32
};

type AuctionRow = {
  id: number;
  title: string;
  category: string;
  description: string | null;
  startPrice: number;
  currentBid: number;
  image: string | null;
  condition: string | null;
  sellerId: number;
  status: string;
  startTime: string;
  endTime: string;
  seller?: string | null;
  totalBids?: number;
};

const toTimeRemaining = (endTime: string): string => {
  const diffMs = new Date(endTime).getTime() - Date.now();
  if (diffMs <= 0) return 'Ended';

  const totalMinutes = Math.floor(diffMs / (1000 * 60));
  const days = Math.floor(totalMinutes / (24 * 60));
  const hours = Math.floor((totalMinutes % (24 * 60)) / 60);
  const minutes = totalMinutes % 60;

  if (days > 0) return `${days}d ${hours}h`;
  if (hours > 0) return `${hours}h ${minutes}m`;
  return `${minutes}m`;
};

// Progress is time-based: 0% at start, 100% when auction reaches endTime.
const toProgress = (startTime: string, endTime: string): number => {
  const startMs = new Date(startTime).getTime();
  const endMs = new Date(endTime).getTime();
  const nowMs = Date.now();

  if (Number.isNaN(startMs) || Number.isNaN(endMs) || endMs <= startMs) {
    return 0;
  }

  if (nowMs <= startMs) return 0;
  if (nowMs >= endMs) return 100;

  const elapsed = nowMs - startMs;
  const total = endMs - startMs;
  return Math.round((elapsed / total) * 100);
};

const mapAuctionForClient = (row: AuctionRow) => ({
  ...row,
  totalBids: Number(row.totalBids || 0),
  seller: row.seller || `Seller ${row.sellerId}`,
  timeRemaining: toTimeRemaining(row.endTime),
  progress: toProgress(row.startTime, row.endTime),
});


// /**
//  * @swagger
//  * /api/auctions/get-all:
//  *   get:
//  *     tags:
//  *       - Auctions
//  *     summary: Get all auctions with pagination
//  *     parameters:
//  *       - in: query
//  *         name: page
//  *         schema:
//  *           type: integer
//  *         description: Page number for pagination
//  *     responses:
//  *       200:
//  *         description: A list of auctions with pagination info
//  */

// // GET all auctions (pagination: fixed 9 per page)
// router.get('/get-all', (req: Request, res: Response) => {
//   const page = Number(req.query.page) || 1;
//   const limit = 9;  

//   const start = (page - 1) * limit;

//   res.json({
//     success: true,
//     data: auctionListings.slice(start, start + limit),
//     total: auctionListings.length,
//     page,
//     totalPages: Math.ceil(auctionListings.length / limit),
//     stats: auctionStats
//   });
// });

/**
 * @swagger
 * /api/auctions/get-top:
 *   get:
 *     tags:
 *       - Auctions
 *     summary: Get top 4 auctions for the homepage
 *     responses:
 *       200:
 *         description: Top 4 auctions
 */

// Updated to only show auctions that are not ended
router.get('/get-top', async (req: Request, res: Response) => {
  try {
    const rows = await getAll<AuctionRow>(
      `SELECT a.*, COALESCE(u.firstName || ' ' || u.lastName, NULL) AS seller,
              COUNT(b.id) AS totalBids
       FROM auctions a
       LEFT JOIN users u ON u.id = a.seller_id
       LEFT JOIN bids b ON b.auction_id = a.id
       WHERE a.status = 'active' AND a.endTime > CURRENT_TIMESTAMP
       GROUP BY a.id
       ORDER BY a.createdAt DESC
       LIMIT 6`
    );

    const stats = await getOne<{ activeLots: number; totalValue: number; averageBid: number }>(
      `SELECT COUNT(*) AS activeLots,
              COALESCE(SUM(currentBid), 0) AS totalValue,
              COALESCE(AVG(currentBid), 0) AS averageBid
       FROM auctions
       WHERE status = 'active' AND endTime > CURRENT_TIMESTAMP`
    );

    res.json({
      success: true,
      data: rows.map(mapAuctionForClient),
      stats: {
        activeLots: Number(stats?.activeLots || 0),
        totalValue: Number(stats?.totalValue || 0),
        averageBid: Math.round(Number(stats?.averageBid || 0)),
        completedToday: auctionStats.completedToday,
      },
    });
  } catch (error) {
    console.error('Error fetching top auctions:', error);
    res.status(500).json({
      success: false,
      message: 'Internal server error',
    });
  }
});

/**
 * @swagger
 * /api/auctions/get/{id}:
 *   get:
 *     tags:
 *       - Auctions
 *     summary: Get auction details by ID
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: integer
 *         description: ID of the auction
 *     responses:
 *       200: 
 *         description: detail of specific auction
 *       404:
 *         description: Auction not found
 */
// GET auction by ID
router.get('/get/:id', async (req: Request, res: Response) => {
  try {
    const auctionId = parseInt(req.params.id);
    if (Number.isNaN(auctionId)) {
      return res.status(400).json({ success: false, message: 'Invalid auction id' });
    }

    const auction = await getOne<AuctionRow>(
      `SELECT a.*, COALESCE(u.firstName || ' ' || u.lastName, NULL) AS seller,
              COUNT(b.id) AS totalBids
       FROM auctions a
       LEFT JOIN users u ON u.id = a.seller_id
       LEFT JOIN bids b ON b.auction_id = a.id
       WHERE a.id = ?
       GROUP BY a.id`,
      [auctionId]
    );

    if (!auction) {
      return res.status(404).json({
        success: false,
        message: 'Auction not found'
      });
    }

    const bidRows = await getAll<{ amount: number; bidTime: string; bidder: string }>(
      `SELECT b.amount, b.bidTime,
              COALESCE(u.firstName || '***' || SUBSTR(u.lastName, 1, 1), 'User***') AS bidder
       FROM bids b
       LEFT JOIN users u ON u.id = b.bidder_id
       WHERE b.auction_id = ?
       ORDER BY b.bidTime DESC
       LIMIT 12`,
      [auctionId]
    );

    res.json({
      success: true,
      data: {
        ...mapAuctionForClient(auction),
        bidHistory: bidRows.map((b) => ({
          bidder: b.bidder,
          amount: Number(b.amount),
          time: b.bidTime,
        })),
      }
    });
  } catch (error) {
    console.error('Error fetching auction:', error);
    res.status(500).json({ success: false, message: 'Internal server error' });
  }
});

/**
 * @swagger
 * /api/auctions/get-related/{id}:
 *   get:
 *     tags:
 *       - Auctions
 *     summary: Get related auctions by item ID
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: integer
 *         description: ID of the auction
 *     responses:
 *       200: 
 *         description: top 4 related auctions based on category
 *       404:
 *         description: Auction not found
 */

// Updated to only show auctions that are not ended
router.get('/get-related/:id', async (req: Request, res: Response) => {
  try {
    const auctionId = parseInt(req.params.id);
    if (Number.isNaN(auctionId)) {
      return res.status(400).json({ success: false, message: 'Invalid auction id' });
    }

    const target = await getOne<{ category: string }>('SELECT category FROM auctions WHERE id = ?', [auctionId]);
    if (!target) {
      return res.status(404).json({ success: false, message: 'Auction not found' });
    }

    const related = await getAll<AuctionRow>(
      `SELECT a.*, COALESCE(u.firstName || ' ' || u.lastName, NULL) AS seller,
              COUNT(b.id) AS totalBids
       FROM auctions a
       LEFT JOIN users u ON u.id = a.seller_id
       LEFT JOIN bids b ON b.auction_id = a.id
       WHERE a.category = ? AND a.id != ? AND a.status = 'active' AND a.endTime > CURRENT_TIMESTAMP
       GROUP BY a.id
       ORDER BY a.createdAt DESC
       LIMIT 4`,
      [target.category, auctionId]
    );

    res.json({
      success: true,
      data: related.map(mapAuctionForClient),
    });
  } catch (error) {
    console.error('Error fetching related auctions:', error);
    res.status(500).json({ success: false, message: 'Internal server error' });
  }
});


/**
 * @swagger
 * /api/auctions/search:
 *   get:
 *     tags:
 *       - Auctions
 *     summary: Search auctions by title substring with pagination
 *     parameters:
 *       - in: query
 *         name: q
 *         required: true
 *         schema:
 *           type: string
 *         description: Search query string
 *       - in: query
 *         name: page
 *         schema:
 *           type: integer
 *         description: Page number for pagination
 *     responses:
 *       200:
 *         description: A list of matching auctions
 *       400:
 *         description: Missing search query
 */

// Updated to only show auctions that are not ended
router.get('/search', async (req: Request, res: Response) => {
  try {
    const q = (req.query.q as string) || '';
    const page = Number(req.query.page) || 1;
    const limit = 9;
    const offset = (page - 1) * limit;
    const hasQuery = q.trim().length > 0;

    const whereClause = hasQuery
      ? "WHERE a.status = 'active' AND a.endTime > CURRENT_TIMESTAMP AND LOWER(a.title) LIKE LOWER(?)"
      : "WHERE a.status = 'active' AND a.endTime > CURRENT_TIMESTAMP";
    const whereParams = hasQuery ? [`%${q.trim()}%`] : [];

    const totalRow = await getOne<{ total: number }>(
      `SELECT COUNT(*) as total FROM auctions a ${whereClause}`,
      whereParams
    );

    const rows = await getAll<AuctionRow>(
      `SELECT a.*, COALESCE(u.firstName || ' ' || u.lastName, NULL) AS seller,
              COUNT(b.id) AS totalBids
       FROM auctions a
       LEFT JOIN users u ON u.id = a.seller_id
       LEFT JOIN bids b ON b.auction_id = a.id
       ${whereClause}
       GROUP BY a.id
       ORDER BY a.createdAt DESC
       LIMIT ? OFFSET ?`,
      [...whereParams, limit, offset]
    );

    res.json({
      success: true,
      data: rows.map(mapAuctionForClient),
      total: Number(totalRow?.total || 0),
      page,
      totalPages: Math.ceil(Number(totalRow?.total || 0) / limit),
      limit,
    });
  } catch (error) {
    console.error('Error searching auctions:', error);
    res.status(500).json({ success: false, message: 'Internal server error' });
  }
});


/**
 * @swagger
 * /api/auctions/bid/{id}:
 *   get:
 *     tags:
 *       - Auctions
 *     summary: Place a bid on an auction
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: integer
 *         description: ID of the auction
 *       - in: query
 *         name: bidAmount
 *         required: true
 *         schema:
 *           type: number
 *         description: The amount of the bid
 *     responses:
 *       200:
 *         description: Bid placed successfully
 *       400:
 *         description: Invalid bid amount
 *       404:
 *         description: Auction not found
 */

// PLACE a bid (changed to GET method)
const handlePlaceBid = async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    const userId = req.user?.userId;
    const bidAmount = Number(req.query.bidAmount);

    if (!userId) {
      return res.status(401).json({ success: false, message: 'Unauthorized' });
    }

    if (Number.isNaN(bidAmount) || bidAmount <= 0) {
      return res.status(400).json({ success: false, message: 'Invalid bid amount' });
    }

    const auctionId = parseInt(id);
    const auction = await getOne<{ id: number; currentBid: number; status: string }>(
      'SELECT id, currentBid, status FROM auctions WHERE id = ?',
      [auctionId]
    );

    if (!auction) {
      return res.status(404).json({ success: false, message: 'Auction not found' });
    }

    if (auction.status !== 'active') {
      return res.status(400).json({ success: false, message: 'Auction is not active' });
    }

    if (bidAmount <= Number(auction.currentBid)) {
      return res.status(400).json({ success: false, message: 'Bid amount must be higher than the current bid' });
    }

    await runQuery('UPDATE auctions SET currentBid = ? WHERE id = ?', [bidAmount, auctionId]);
    await runQuery('INSERT INTO bids (auction_id, bidder_id, amount) VALUES (?, ?, ?)', [auctionId, userId, bidAmount]);

    const updated = await getOne<AuctionRow>(
      `SELECT a.*, COALESCE(u.firstName || ' ' || u.lastName, NULL) AS seller,
              COUNT(b.id) AS totalBids
       FROM auctions a
       LEFT JOIN users u ON u.id = a.seller_id
       LEFT JOIN bids b ON b.auction_id = a.id
       WHERE a.id = ?
       GROUP BY a.id`,
      [auctionId]
    );

    res.json({
      success: true,
      message: 'Bid placed successfully',
      data: updated ? mapAuctionForClient(updated) : null,
    });
  } catch (error) {
    console.error('Error placing bid:', error);
    res.status(500).json({ success: false, message: 'Internal server error' });
  }
};

router.get('/bid/:id', authMiddleware, handlePlaceBid);
router.get('/:id/bid', authMiddleware, handlePlaceBid);

/**
 * @swagger
 * /api/auctions/watch/{id}:
 *   post:
 *     tags:
 *       - Auctions
 *     summary: Add an auction to user's watchlist
 *     security:
 *       - BearerAuth: []
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: integer
 *         description: ID of the auction
 *     responses:
 *       200:
 *         description: Item added to watchlist successfully
 *       400:
 *         description: Item already in watchlist
 *       401:
 *         description: Unauthorized - no token provided
 */
router.post('/watch/:id', authMiddleware, async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    const userId = req.user?.userId;

    if (!userId) {
      return res.status(401).json({
        success: false,
        message: 'Unauthorized'
      });
    }

    const auctionId = parseInt(id);
    if (Number.isNaN(auctionId)) {
      return res.status(400).json({
        success: false,
        message: 'Invalid auction id'
      });
    }

    // Validate user still exists (handles stale tokens after DB resets).
    const userExists = await getOne<{ id: number }>(
      'SELECT id FROM users WHERE id = ?',
      [userId]
    );
    if (!userExists) {
      return res.status(401).json({
        success: false,
        message: 'User not found. Please log in again.'
      });
    }

    // Watchlist table references auctions(id), so this auction must exist in DB.
    const auctionExists = await getOne<{ id: number }>(
      'SELECT id FROM auctions WHERE id = ?',
      [auctionId]
    );
    if (!auctionExists) {
      return res.status(404).json({
        success: false,
        message: 'Auction is not stored in database. This item cannot be watched yet.'
      });
    }

    // Check if already in watchlist
    const existing = await getOne(
      'SELECT id FROM watchlist WHERE user_id = ? AND auction_id = ?',
      [userId, auctionId]
    );

    if (existing) {
      return res.status(400).json({
        success: false,
        message: 'Item already in watchlist'
      });
    }

    // Add to watchlist
    await runQuery(
      'INSERT INTO watchlist (user_id, auction_id) VALUES (?, ?)',
      [userId, auctionId]
    );

    res.json({
      success: true,
      message: 'Item added to watchlist'
    });
  } catch (error) {
    console.error('Error adding to watchlist:', error);

    if (error instanceof Error && error.message.includes('SQLITE_CONSTRAINT')) {
      return res.status(400).json({
        success: false,
        message: 'Cannot add to watchlist because related user or auction record is missing.'
      });
    }

    res.status(500).json({
      success: false,
      message: error instanceof Error ? error.message : 'Internal server error'
    });
  }
});

/**
 * @swagger
 * /api/auctions/unwatch/{id}:
 *   delete:
 *     tags:
 *       - Auctions
 *     summary: Remove an auction from user's watchlist
 *     security:
 *       - BearerAuth: []
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: integer
 *         description: ID of the auction
 *     responses:
 *       200:
 *         description: Item removed from watchlist successfully
 *       404:
 *         description: Item not in watchlist
 *       401:
 *         description: Unauthorized - no token provided
 */
router.delete('/unwatch/:id', authMiddleware, async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    const userId = req.user?.userId;

    if (!userId) {
      return res.status(401).json({
        success: false,
        message: 'Unauthorized'
      });
    }

    const auctionId = parseInt(id);
    if (Number.isNaN(auctionId)) {
      return res.status(400).json({
        success: false,
        message: 'Invalid auction id'
      });
    }

    const existing = await getOne<{ id: number }>(
      'SELECT id FROM watchlist WHERE user_id = ? AND auction_id = ?',
      [userId, auctionId]
    );

    if (!existing) {
      return res.status(404).json({
        success: false,
        message: 'Item not in watchlist'
      });
    }

    await runQuery('DELETE FROM watchlist WHERE user_id = ? AND auction_id = ?', [userId, auctionId]);

    res.json({
      success: true,
      message: 'Item removed from watchlist'
    });
  } catch (error) {
    console.error('Error removing from watchlist:', error);
    res.status(500).json({
      success: false,
      message: 'Internal server error'
    });
  }
});

/**
 * @swagger
 * /api/auctions/get-watchlist:
 *   get:
 *     tags:
 *       - Auctions
 *     summary: Get user's watchlist
 *     security:
 *       - BearerAuth: []
 *     responses:
 *       200:
 *         description: User's watchlist items
 *       401:
 *         description: Unauthorized - no token provided
 */
router.get('/get-watchlist', authMiddleware, async (req: Request, res: Response) => {
  try {
    const userId = req.user?.userId;

    if (!userId) {
      return res.status(401).json({
        success: false,
        message: 'Unauthorized'
      });
    }

    const watchlist = await getAll<AuctionRow>(
      `SELECT a.*, COALESCE(u.firstName || ' ' || u.lastName, NULL) AS seller,
              COUNT(b.id) AS totalBids
       FROM auctions a
       INNER JOIN watchlist w ON a.id = w.auction_id
       LEFT JOIN users u ON u.id = a.seller_id
       LEFT JOIN bids b ON b.auction_id = a.id
       WHERE w.user_id = ?
       GROUP BY a.id
       ORDER BY w.createdAt DESC`,
      [userId]
    );

    res.json({
      success: true,
      data: watchlist.map(mapAuctionForClient)
    });
  } catch (error) {
    console.error('Error fetching watchlist:', error);
    res.status(500).json({
      success: false,
      message: 'Internal server error'
    });
  }
});

/**
 * @swagger
 * /api/auctions/is-watched/{id}:
 *   get:
 *     tags:
 *       - Auctions
 *     summary: Check if auction is in user's watchlist
 *     security:
 *       - BearerAuth: []
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: integer
 *         description: ID of the auction
 *     responses:
 *       200:
 *         description: Watch status of the auction
 *       401:
 *         description: Unauthorized - no token provided
 */
router.get('/is-watched/:id', authMiddleware, async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    const userId = req.user?.userId;

    if (!userId) {
      return res.status(401).json({
        success: false,
        message: 'Unauthorized'
      });
    }

    const auctionId = parseInt(id);
    const watchItem = await getOne(
      'SELECT id FROM watchlist WHERE user_id = ? AND auction_id = ?',
      [userId, auctionId]
    );

    res.json({
      success: true,
      isWatched: !!watchItem
    });
  } catch (error) {
    console.error('Error checking watchlist:', error);
    res.status(500).json({
      success: false,
      message: 'Internal server error'
    });
  }
});

/**
 * @swagger
 * /api/auctions/create:
 *   post:
 *     tags:
 *       - Auctions
 *     summary: Create a new auction
 *     description: Creates an active auction owned by the authenticated user.
 *     security:
 *       - BearerAuth: []
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             properties:
 *               title:
 *                 type: string
 *                 minLength: 1
 *                 description: Auction title (required)
 *                 example: Vintage Camera Lens
 *               category:
 *                 type: string
 *                 minLength: 1
 *                 description: Category of the item (required)
 *                 example: Collectibles
 *               description:
 *                 type: string
 *                 nullable: true
 *                 description: Detailed description of the item (optional)
 *                 example: Well-maintained lens with minor cosmetic wear.
 *               sellerId:
 *                 type: integer
 *                 description: Seller user ID. Must match the authenticated user when provided.
 *                 example: 2
 *               startingPrice:
 *                 type: number
 *                 minimum: 0.01
 *                 description: Starting bid price (preferred input field)
 *                 example: 120
 *               startPrice:
 *                 type: number
 *                 minimum: 0.01
 *                 description: Starting bid price (legacy-compatible field)
 *                 example: 120
 *               reservePrice:
 *                 type: number
 *                 nullable: true
 *                 description: Reserve price (optional)
 *                 example: 200
 *               duration:
 *                 type: integer
 *                 minimum: 1
 *                 description: Auction duration in days; used when endTime is not provided
 *                 example: 7
 *               condition:
 *                 type: string
 *                 nullable: true
 *                 description: Item condition (optional)
 *                 example: Excellent
 *               images:
 *                 type: array
 *                 items:
 *                   type: string
 *                 description: Image list; first image is used as main image when image is not provided
 *               image:
 *                 type: string
 *                 nullable: true
 *                 description: URL or path/base64 of item image (optional)
 *                 example: /images/camera-lens.jpg
 *               shippingOption:
 *                 type: string
 *                 enum: [seller, buyer]
 *                 description: Shipping payer option (optional)
 *               shippingCost:
 *                 type: number
 *                 nullable: true
 *                 description: Shipping cost if buyer pays (optional)
 *               endTime:
 *                 type: string
 *                 format: date-time
 *                 description: Auction end time in the future (ISO 8601). If omitted, duration is used.
 *                 example: 2026-03-15T18:30:00Z
 *             required:
 *               - title
 *               - category
 *               - startingPrice
 *               - duration
 *     responses:
 *       201:
 *         description: Auction created successfully
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 success:
 *                   type: boolean
 *                   example: true
 *                 message:
 *                   type: string
 *                   example: Auction created successfully
 *                 data:
 *                   type: object
 *       400:
 *         description: Invalid input data or validation failed
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 success:
 *                   type: boolean
 *                   example: false
 *                 message:
 *                   type: string
 *       401:
 *         description: Unauthorized - no token provided
 *       500:
 *         description: Internal server error
 */
router.post('/create', authMiddleware, async (req: Request, res: Response) => {
  try {
    const {
      title,
      category,
      description,
      sellerId,
      seller_id,
      startPrice,
      startingPrice,
      reservePrice,
      duration,
      images,
      condition,
      image,
      shippingOption,
      shippingCost,
      endTime,
    } = req.body;
    const authSellerId = req.user?.userId;
    const payloadSellerIdRaw = sellerId ?? seller_id;
    const payloadSellerId = payloadSellerIdRaw !== undefined ? Number(payloadSellerIdRaw) : undefined;

    if (!authSellerId) {
      return res.status(401).json({
        success: false,
        message: 'Unauthorized'
      });
    }

    if (
      payloadSellerIdRaw !== undefined &&
      (payloadSellerId === undefined || Number.isNaN(payloadSellerId) || payloadSellerId <= 0)
    ) {
      return res.status(400).json({
        success: false,
        message: 'sellerId/seller_id must be a valid positive number'
      });
    }

    if (payloadSellerId !== undefined && payloadSellerId !== authSellerId) {
      return res.status(403).json({
        success: false,
        message: 'sellerId does not match authenticated user'
      });
    }

    const resolvedSellerId = authSellerId;

    const resolvedStartPrice = Number(startPrice ?? startingPrice);

    let resolvedEndTime = endTime as string | undefined;
    if (!resolvedEndTime) {
      const durationDays = Number(duration);
      if (!Number.isNaN(durationDays) && durationDays > 0) {
        resolvedEndTime = new Date(Date.now() + durationDays * 24 * 60 * 60 * 1000).toISOString();
      }
    }

    const resolvedImage = image || (Array.isArray(images) ? images[0] : '/images/picture.jpg');

    // Validation
    if (!title || !category || Number.isNaN(resolvedStartPrice) || !resolvedEndTime) {
      return res.status(400).json({
        success: false,
        message: 'Missing required fields: title, category, startingPrice/startPrice, and duration/endTime'
      });
    }

    if (resolvedStartPrice <= 0) {
      return res.status(400).json({
        success: false,
        message: 'startingPrice/startPrice must be a positive number'
      });
    }

    // Validate endTime
    const endTimeDate = new Date(resolvedEndTime);
    if (isNaN(endTimeDate.getTime())) {
      return res.status(400).json({
        success: false,
        message: 'Invalid endTime format. Use ISO 8601 format (e.g., 2026-03-15T18:30:00Z)'
      });
    }

    if (endTimeDate <= new Date()) {
      return res.status(400).json({
        success: false,
        message: 'Auction end time must be in the future'
      });
    }

    const resolvedStartTime = new Date().toISOString();

    // Create auction
    await runQuery(
      `INSERT INTO auctions (title, category, description, startPrice, currentBid, image, condition, seller_id, startTime, endTime, status)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        title,
        category,
        [
          description || '',
          reservePrice ? `\nReserve Price: ${reservePrice}` : '',
          shippingOption ? `\nShipping Option: ${shippingOption}` : '',
          shippingCost ? `\nShipping Cost: ${shippingCost}` : '',
        ].join('').trim() || null,
        resolvedStartPrice,
        resolvedStartPrice,
        resolvedImage || '/images/picture.jpg',
        condition || null,
        resolvedSellerId,
        resolvedStartTime,
        resolvedEndTime,
        'active',
      ]
    );

    // Get the created auction
    const newAuction = await getOne<AuctionRow>(
      `SELECT a.*, COALESCE(u.firstName || ' ' || u.lastName, NULL) AS seller,
              COUNT(b.id) AS totalBids
       FROM auctions a
       LEFT JOIN users u ON u.id = a.seller_id
       LEFT JOIN bids b ON b.auction_id = a.id
       WHERE a.seller_id = ?
       GROUP BY a.id
       ORDER BY a.id DESC
       LIMIT 1`,
      [resolvedSellerId]
    );

    res.status(201).json({
      success: true,
      message: 'Auction created successfully',
      data: newAuction ? mapAuctionForClient(newAuction) : null
    });
  } catch (error) {
    console.error('Error creating auction:', error);
    res.status(500).json({
      success: false,
      message: 'Internal server error'
    });
  }
});

/**
 * @swagger
 * /api/auctions/end/{id}:
 *   patch:
 *     tags:
 *       - Auctions
 *     summary: End an auction
 *     description: Marks an auction as ended. Only the owner can end their auction.
 *     security:
 *       - BearerAuth: []
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: integer
 *         description: ID of the auction to end
 *     responses:
 *       200:
 *         description: Auction ended successfully
 *       400:
 *         description: Invalid auction id
 *       401:
 *         description: Unauthorized - no token provided
 *       403:
 *         description: Forbidden - auction does not belong to the user
 *       404:
 *         description: Auction not found
 *       500:
 *         description: Internal server error
 */
router.patch('/end/:id', authMiddleware, async (req: Request, res: Response) => {
  try {
    const authSellerId = req.user?.userId;
    if (!authSellerId) {
      return res.status(401).json({
        success: false,
        message: 'Unauthorized'
      });
    }

    const auctionId = Number(req.params.id);
    if (Number.isNaN(auctionId) || auctionId <= 0) {
      return res.status(400).json({
        success: false,
        message: 'Invalid auction id'
      });
    }

    const auction = await getOne<{ id: number; seller_id: number; status: string }>(
      'SELECT id, seller_id, status FROM auctions WHERE id = ?',
      [auctionId]
    );

    if (!auction) {
      return res.status(404).json({
        success: false,
        message: 'Auction not found'
      });
    }

    if (auction.seller_id !== authSellerId) {
      return res.status(403).json({
        success: false,
        message: 'You can only end your own auctions'
      });
    }

    if (auction.status === 'ended') {
      return res.json({
        success: true,
        message: 'Auction is already ended'
      });
    }

    await runQuery(
      "UPDATE auctions SET status = 'ended', endTime = CURRENT_TIMESTAMP WHERE id = ?",
      [auctionId]
    );

    res.json({
      success: true,
      message: 'Auction ended successfully'
    });
  } catch (error) {
    console.error('Error ending auction:', error);
    res.status(500).json({
      success: false,
      message: 'Internal server error'
    });
  }
});

/**
 * @swagger
 * /api/auctions/delete/{id}:
 *   delete:
 *     tags:
 *       - Auctions
 *     summary: Delete an auction
 *     description: Deletes an auction owned by the authenticated user.
 *     security:
 *       - BearerAuth: []
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: integer
 *         description: ID of the auction to delete
 *     responses:
 *       200:
 *         description: Auction deleted successfully
 *       400:
 *         description: Invalid auction id
 *       401:
 *         description: Unauthorized - no token provided
 *       403:
 *         description: Forbidden - auction does not belong to the user
 *       404:
 *         description: Auction not found
 *       500:
 *         description: Internal server error
 */
router.delete('/delete/:id', authMiddleware, async (req: Request, res: Response) => {
  try {
    const authSellerId = req.user?.userId;
    if (!authSellerId) {
      return res.status(401).json({
        success: false,
        message: 'Unauthorized'
      });
    }

    const auctionId = Number(req.params.id);
    if (Number.isNaN(auctionId) || auctionId <= 0) {
      return res.status(400).json({
        success: false,
        message: 'Invalid auction id'
      });
    }

    const auction = await getOne<{ id: number; seller_id: number }>(
      'SELECT id, seller_id FROM auctions WHERE id = ?',
      [auctionId]
    );

    if (!auction) {
      return res.status(404).json({
        success: false,
        message: 'Auction not found'
      });
    }

    if (auction.seller_id !== authSellerId) {
      return res.status(403).json({
        success: false,
        message: 'You can only delete your own auctions'
      });
    }

    // Remove dependent records first because FK constraints do not use ON DELETE CASCADE.
    await runQuery('DELETE FROM bids WHERE auction_id = ?', [auctionId]);
    await runQuery('DELETE FROM watchlist WHERE auction_id = ?', [auctionId]);
    await runQuery('DELETE FROM auctions WHERE id = ?', [auctionId]);

    res.json({
      success: true,
      message: 'Auction deleted successfully'
    });
  } catch (error) {
    console.error('Error deleting auction:', error);
    res.status(500).json({
      success: false,
      message: 'Internal server error'
    });
  }
});

export default router;
