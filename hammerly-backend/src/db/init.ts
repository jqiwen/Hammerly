import { runQuery } from './database.js';

const addColumnIfNotExists = async (table: string, column: string, definition: string) => {
  try {
    await runQuery(`ALTER TABLE ${table} ADD COLUMN ${column} ${definition}`);
  } catch {
    // Ignore duplicate-column errors so existing local databases migrate safely.
  }
};

export const initializeDatabase = async () => {
  try {
    await runQuery(`
      CREATE TABLE IF NOT EXISTS users (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        firstName TEXT NOT NULL,
        lastName TEXT NOT NULL,
        email TEXT UNIQUE NOT NULL,
        password TEXT NOT NULL,
        phone TEXT DEFAULT '',
        avatarImage TEXT DEFAULT '/images/user.jpg',
        createdAt DATETIME DEFAULT CURRENT_TIMESTAMP,
        updatedAt DATETIME DEFAULT CURRENT_TIMESTAMP
      )
    `);

    await addColumnIfNotExists('users', 'phone', "TEXT DEFAULT ''");
    await addColumnIfNotExists('users', 'avatarImage', "TEXT DEFAULT '/images/user.jpg'");
    await addColumnIfNotExists('users', 'updatedAt', 'DATETIME DEFAULT CURRENT_TIMESTAMP');

    await runQuery(`
      CREATE TABLE IF NOT EXISTS auctions (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        title TEXT NOT NULL,
        category TEXT NOT NULL,
        description TEXT,
        startPrice REAL NOT NULL,
        currentBid REAL NOT NULL,
        image TEXT,
        condition TEXT,
        seller_id INTEGER NOT NULL,
        status TEXT DEFAULT 'active',
        startTime DATETIME DEFAULT CURRENT_TIMESTAMP,
        endTime DATETIME NOT NULL,
        createdAt DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (seller_id) REFERENCES users(id)
      )
    `);

    await addColumnIfNotExists('auctions', 'status', "TEXT DEFAULT 'active'");
    await addColumnIfNotExists('auctions', 'startTime', 'DATETIME DEFAULT CURRENT_TIMESTAMP');
    await addColumnIfNotExists('auctions', 'createdAt', 'DATETIME DEFAULT CURRENT_TIMESTAMP');

    await runQuery(`
      CREATE TABLE IF NOT EXISTS bids (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        auction_id INTEGER NOT NULL,
        bidder_id INTEGER NOT NULL,
        amount REAL NOT NULL,
        bidTime DATETIME DEFAULT CURRENT_TIMESTAMP,
        createdAt DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (auction_id) REFERENCES auctions(id),
        FOREIGN KEY (bidder_id) REFERENCES users(id)
      )
    `);

    await addColumnIfNotExists('bids', 'bidTime', 'DATETIME DEFAULT CURRENT_TIMESTAMP');
    await addColumnIfNotExists('bids', 'createdAt', 'DATETIME DEFAULT CURRENT_TIMESTAMP');

    await runQuery(`
      CREATE TABLE IF NOT EXISTS watchlist (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id INTEGER NOT NULL,
        auction_id INTEGER NOT NULL,
        createdAt DATETIME DEFAULT CURRENT_TIMESTAMP,
        UNIQUE(user_id, auction_id),
        FOREIGN KEY (user_id) REFERENCES users(id),
        FOREIGN KEY (auction_id) REFERENCES auctions(id)
      )
    `);

    await runQuery(`
      CREATE TABLE IF NOT EXISTS payment_methods (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id INTEGER NOT NULL,
        cardType TEXT NOT NULL,
        cardNumber TEXT DEFAULT '',
        lastFour TEXT NOT NULL,
        expiryMonth INTEGER NOT NULL,
        expiryYear INTEGER NOT NULL,
        cardholderName TEXT NOT NULL,
        isDefault INTEGER DEFAULT 0,
        billingAddress TEXT DEFAULT '',
        billingCity TEXT DEFAULT '',
        billingProvince TEXT DEFAULT '',
        billingPostalCode TEXT DEFAULT '',
        billingCountry TEXT DEFAULT '',
        createdAt DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (user_id) REFERENCES users(id)
      )
    `);

    await addColumnIfNotExists('payment_methods', 'cardNumber', "TEXT DEFAULT ''");

    console.log('Database schema is ready. Existing local data was preserved.');
  } catch (error) {
    console.error('Database error:', error);
    throw error;
  }
};
