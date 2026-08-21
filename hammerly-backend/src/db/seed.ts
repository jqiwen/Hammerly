import db, { getOne, runInsert, runQuery } from './database.js';
import { initializeDatabase } from './init.js';
import { hashPassword } from '../utils/auth.js';
import { seedAuctions, seedBids, seedUsers, type SeedAuction, type SeedBid, type SeedUser } from './seed-data.js';

type IdRow = { id: number };
type AuctionIdentityRow = { id: number; startTime: string; currentBid: number };

const resolveAuctionTimes = (auction: SeedAuction) => {
  const startTime = auction.startTime ? new Date(auction.startTime) : new Date();
  const endTime = auction.endTime
    ? new Date(auction.endTime)
    : new Date(startTime.getTime() + (auction.durationHours ?? 24) * 60 * 60 * 1000);

  if (Number.isNaN(startTime.getTime()) || Number.isNaN(endTime.getTime())) {
    throw new Error(`Invalid date on seed auction "${auction.title}"`);
  }

  if (endTime <= startTime) {
    throw new Error(`Seed auction "${auction.title}" must end after it starts`);
  }

  return {
    startTime: startTime.toISOString(),
    endTime: endTime.toISOString(),
  };
};

const upsertUser = async (user: SeedUser) => {
  const existing = await getOne<IdRow>('SELECT id FROM users WHERE email = ?', [user.email]);
  const hashedPassword = await hashPassword(user.password);

  if (existing) {
    await runQuery(
      `UPDATE users
       SET firstName = ?, lastName = ?, password = ?, phone = ?, avatarImage = ?, updatedAt = CURRENT_TIMESTAMP
       WHERE id = ?`,
      [
        user.firstName,
        user.lastName,
        hashedPassword,
        user.phone || '',
        user.avatarImage || '',
        existing.id,
      ]
    );
    return existing.id;
  }

  return runInsert(
    `INSERT INTO users (firstName, lastName, email, password, phone, avatarImage)
     VALUES (?, ?, ?, ?, ?, ?)`,
    [
      user.firstName,
      user.lastName,
      user.email,
      hashedPassword,
      user.phone || '',
      user.avatarImage || '',
    ]
  );
};

const upsertAuction = async (auction: SeedAuction, sellerId: number) => {
  const existing = await getOne<AuctionIdentityRow>(
    'SELECT id, startTime, currentBid FROM auctions WHERE title = ? AND seller_id = ?',
    [auction.title, sellerId]
  );
  const { startTime, endTime } = resolveAuctionTimes(auction);
  const currentBid = auction.currentBid ?? auction.startPrice;

  if (existing) {
    await runQuery(
      `UPDATE auctions
       SET category = ?, description = ?, startPrice = ?, currentBid = ?, image = ?, condition = ?, status = ?, startTime = ?, endTime = ?
       WHERE id = ?`,
      [
        auction.category,
        auction.description || '',
        auction.startPrice,
        currentBid,
        auction.image || '',
        auction.condition || '',
        auction.status || 'active',
        startTime,
        endTime,
        existing.id,
      ]
    );
    return existing.id;
  }

  return runInsert(
    `INSERT INTO auctions (title, category, description, startPrice, currentBid, image, condition, seller_id, status, startTime, endTime)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    [
      auction.title,
      auction.category,
      auction.description || '',
      auction.startPrice,
      currentBid,
      auction.image || '',
      auction.condition || '',
      sellerId,
      auction.status || 'active',
      startTime,
      endTime,
    ]
  );
};

const ensureBid = async (bid: SeedBid, auctionId: number, bidderId: number, fallbackBidTime: string) => {
  const bidTime = bid.bidTime || fallbackBidTime;
  const existing = await getOne<IdRow>(
    `SELECT id
     FROM bids
     WHERE auction_id = ? AND bidder_id = ? AND amount = ? AND bidTime = ?`,
    [auctionId, bidderId, bid.amount, bidTime]
  );

  if (!existing) {
    await runQuery(
      'INSERT INTO bids (auction_id, bidder_id, amount, bidTime) VALUES (?, ?, ?, ?)',
      [auctionId, bidderId, bid.amount, bidTime]
    );
  }
};

const seedDatabase = async () => {
  const userIdsByEmail = new Map<string, number>();
  const auctionKeys = new Map<string, { id: number; startTime: string; currentBid: number }>();

  for (const user of seedUsers) {
    const userId = await upsertUser(user);
    userIdsByEmail.set(user.email, userId);
  }

  for (const auction of seedAuctions) {
    const sellerId = userIdsByEmail.get(auction.sellerEmail);

    if (!sellerId) {
      throw new Error(`Seed auction "${auction.title}" references missing seller "${auction.sellerEmail}"`);
    }

    const auctionId = await upsertAuction(auction, sellerId);
    const current = await getOne<AuctionIdentityRow>(
      'SELECT id, startTime, currentBid FROM auctions WHERE id = ?',
      [auctionId]
    );

    if (current) {
      auctionKeys.set(`${auction.sellerEmail}::${auction.title}`, current);
    }
  }

  for (const bid of seedBids) {
    const bidderId = userIdsByEmail.get(bid.bidderEmail);
    const auction = auctionKeys.get(`${bid.sellerEmail}::${bid.auctionTitle}`);

    if (!bidderId) {
      throw new Error(`Seed bid for "${bid.auctionTitle}" references missing bidder "${bid.bidderEmail}"`);
    }

    if (!auction) {
      throw new Error(`Seed bid references missing auction "${bid.auctionTitle}"`);
    }

    await ensureBid(bid, auction.id, bidderId, auction.startTime);

    if (bid.amount > auction.currentBid) {
      await runQuery('UPDATE auctions SET currentBid = ? WHERE id = ?', [bid.amount, auction.id]);
      auction.currentBid = bid.amount;
    }
  }
};

try {
  await initializeDatabase();
  await seedDatabase();
  console.log('Seed data loaded into hammerly.db. Restarting the backend will keep this data.');
} catch (error) {
  console.error('Failed to seed database:', error);
  process.exitCode = 1;
} finally {
  db.close();
}
