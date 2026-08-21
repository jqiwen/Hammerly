export type SeedUser = {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  phone?: string;
  avatarImage?: string;
};

export type SeedAuction = {
  title: string;
  category: string;
  description?: string;
  startPrice: number;
  currentBid?: number;
  image?: string;
  condition?: string;
  sellerEmail: string;
  status?: 'active' | 'closed';
  startTime?: string;
  endTime?: string;
  durationHours?: number;
};

export type SeedBid = {
  auctionTitle: string;
  sellerEmail: string;
  bidderEmail: string;
  amount: number;
  bidTime?: string;
};

export const seedUsers: SeedUser[] = [
  {
    firstName: 'John',
    lastName: 'Seller',
    email: 'seller1@hammerly.com',
    password: 'password123',
    phone: '+1 647 000 0001',
  },
  {
    firstName: 'Jane',
    lastName: 'Dealer',
    email: 'seller2@hammerly.com',
    password: 'password123',
    phone: '+1 647 000 0002',
  },
  {
    firstName: 'Taylor',
    lastName: 'Bidder',
    email: 'bidder1@hammerly.com',
    password: 'password123',
  },
];

export const seedAuctions: SeedAuction[] = [
  {
    title: 'Vintage Pocket Watch',
    category: 'Collectibles',
    description: 'Working mechanical pocket watch with original chain.',
    startPrice: 150,
    currentBid: 225,
    image: '/images/picture.jpg',
    condition: 'Very Good',
    sellerEmail: 'seller1@hammerly.com',
    durationHours: 72,
  },
  {
    title: 'Signed First Edition Novel',
    category: 'Books',
    description: 'Signed first edition with protective sleeve.',
    startPrice: 80,
    currentBid: 120,
    image: '/images/picture.jpg',
    condition: 'Good',
    sellerEmail: 'seller2@hammerly.com',
    durationHours: 48,
  },
];

export const seedBids: SeedBid[] = [
  {
    auctionTitle: 'Vintage Pocket Watch',
    sellerEmail: 'seller1@hammerly.com',
    bidderEmail: 'bidder1@hammerly.com',
    amount: 225,
    bidTime: '2026-03-17T12:00:00.000Z',
  },
];
